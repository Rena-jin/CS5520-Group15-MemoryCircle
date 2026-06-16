package com.cs5520group15.memorycircle.ui.scrapbook

import com.cs5520group15.memorycircle.common.AuthRepository
import com.cs5520group15.memorycircle.common.FirebaseModule
import com.cs5520group15.memorycircle.common.Result
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * What: Firestore-backed store for every group's scrapbook timeline. The viewer and
 *       the creation screen read and write through this single source of truth, so a
 *       time point created on one screen shows up on the other. A real-time snapshot
 *       listener keeps each group's StateFlow in sync with Firestore.
 * Who: Used by ScrapbookViewerViewModel and ScrapbookViewModel (and the screens,
 *      which collect entriesFor(...) directly).
 * When: Accessed whenever a timeline is shown, created, or edited.
 *
 * Firestore layout:
 *   groups/{groupId}/entries/{entryId}
 *     entryId, date (Timestamp), title, tags (List<String>), createdAt (Timestamp)
 *   groups/{groupId}/entries/{entryId}/contributions/{contributionId}
 *     contributionId, memberName, photoUrl, description, createdAt (Timestamp)
 *   groups/{groupId}/entries/{entryId}/comments/{commentId}
 *     commentId, author, text, createdAt (Timestamp)
 *
 * Note: contributions/comments live in subcollections, so the entries listener does
 *       not fire when they change — writes to those subcollections refresh the group
 *       explicitly. Photos use picsum.photos placeholders until Storage is wired up.
 */
object ScrapbookRepository {

    private val db = FirebaseModule.db

    // Internal scope for assembling nested data off the snapshot listener. The
    // repository is a singleton (no ViewModel), so it owns its own scope here;
    // UI-triggered writes are launched from the ViewModels' viewModelScope instead.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // groupId -> that group's timeline entries (the StateFlow the screens collect).
    private val flows = mutableMapOf<String, MutableStateFlow<List<ScrapbookEntry>>>()

    // groupId -> the active entries snapshot listener for that group.
    private val listeners = mutableMapOf<String, ListenerRegistration>()

    // Formats the stored `date` Timestamp back into the "June 1" day label the UI shows.
    private val dayLabel = SimpleDateFormat("MMMM d", Locale.ENGLISH)

    /**
     * What: Returns the live timeline flow for a group, attaching a Firestore snapshot
     *       listener on first use.
     * Who: Called by the ViewModels and screens to observe entries.
     * When: On screen load.
     */
    fun entriesFor(groupId: String): StateFlow<List<ScrapbookEntry>> = flow(groupId).asStateFlow()

    private fun flow(groupId: String): MutableStateFlow<List<ScrapbookEntry>> {
        flows[groupId]?.let { return it }
        val f = MutableStateFlow<List<ScrapbookEntry>>(emptyList())
        flows[groupId] = f          // store before listening so the callback finds it
        startListening(groupId)
        return f
    }

    /**
     * What: Attaches a real-time snapshot listener on groups/{groupId}/entries ordered
     *       by date descending, re-assembling each entry with its contributions and
     *       comments subcollections and publishing to the group's StateFlow.
     * Who: Called by flow() the first time a group is observed.
     * When: Once per group, until detach()/detachAll() removes the listener.
     */
    private fun startListening(groupId: String) {
        if (listeners.containsKey(groupId)) return
        val registration = db.collection("groups").document(groupId)
            .collection("entries")
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val docs = snapshot.documents
                scope.launch {
                    try {
                        flows[groupId]?.value = assemble(docs)
                    } catch (e: Exception) {
                        // Leave the current list untouched on assembly failure.
                    }
                }
            }
        listeners[groupId] = registration
    }

    /**
     * What: Builds the full ScrapbookEntry list for a set of entry documents, fetching
     *       each entry's contributions and comments subcollections.
     * Who: Used by the snapshot listener and by refreshGroup().
     */
    private suspend fun assemble(entryDocs: List<DocumentSnapshot>): List<ScrapbookEntry> {
        return entryDocs.map { doc ->
            val entryRef = doc.reference

            val contributionsSnap = entryRef.collection("contributions")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .get().await()
            val contributions = contributionsSnap.documents.map { c ->
                MemberContribution(
                    memberName  = c.getString("memberName") ?: "",
                    photoUri    = c.getString("photoUrl") ?: "",
                    description = c.getString("description") ?: ""
                )
            }

            val commentsSnap = entryRef.collection("comments")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .get().await()
            val comments = commentsSnap.documents.map { cm ->
                Comment(
                    id     = cm.id,
                    author = cm.getString("author") ?: "",
                    text   = cm.getString("text") ?: ""
                )
            }

            val dateLabel = doc.getTimestamp("date")?.toDate()?.let { dayLabel.format(it) } ?: ""

            ScrapbookEntry(
                id            = doc.id,
                date          = dateLabel,
                title         = doc.getString("title") ?: "",
                tags          = (doc.get("tags") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                contributions = contributions,
                comments      = comments
            )
        }
    }

    /**
     * What: One-shot re-fetch + re-assemble of a group's entries. Used after writes to
     *       a subcollection (contributions/comments), which do not trigger the
     *       entries-collection snapshot listener.
     */
    private suspend fun refreshGroup(groupId: String) {
        val snap = db.collection("groups").document(groupId)
            .collection("entries")
            .orderBy("date", Query.Direction.DESCENDING)
            .get().await()
        flows[groupId]?.value = assemble(snap.documents)
    }

    /**
     * What: Creates a brand-new time point in Firestore (title + tags, dated now) and
     *       writes the creator's first contribution.
     * Who: Called by ScrapbookViewModel when the "+" (new time point) flow saves.
     * When: On save in new-entry mode.
     */
    suspend fun addEntry(
        groupId:           String,
        date:              String,
        title:             String,
        tags:              List<String>,
        firstContribution: MemberContribution
    ) {
        val entryRef = db.collection("groups").document(groupId)
            .collection("entries").document()
        val entryDoc = mapOf(
            "entryId"   to entryRef.id,
            "date"      to FieldValue.serverTimestamp(),
            "title"     to title,
            "tags"      to tags,
            "createdAt" to FieldValue.serverTimestamp()
        )
        entryRef.set(entryDoc).await()
        writeContribution(entryRef, firstContribution)
        refreshGroup(groupId)
    }

    /**
     * What: Appends a member's contribution (photo + description) to an existing time
     *       point — the "join an existing card" flow. The author name comes from the
     *       real current user; the photo is a picsum placeholder for now.
     * Who: Called by ScrapbookViewModel when the join flow saves.
     * When: On save in join mode.
     */
    suspend fun addContribution(groupId: String, entryId: String, contribution: MemberContribution) {
        val entryRef = db.collection("groups").document(groupId)
            .collection("entries").document(entryId)
        writeContribution(entryRef, contribution)
        refreshGroup(groupId)
    }

    /**
     * What: Writes a single contribution under an entry. memberName is the real
     *       current user's name; photoUrl is a stable picsum placeholder (no Storage yet).
     */
    private suspend fun writeContribution(entryRef: DocumentReference, contribution: MemberContribution) {
        val contribRef = entryRef.collection("contributions").document()
        val doc = mapOf(
            "contributionId" to contribRef.id,
            "memberName"     to currentUserName(),
            "photoUrl"       to placeholderPhoto(contribRef.id),
            "description"    to contribution.description,
            "createdAt"      to FieldValue.serverTimestamp()
        )
        contribRef.set(doc).await()
    }

    /**
     * What: Looks up a single entry from the in-memory cache (used by the join flow to
     *       pre-fill title/tags). Reads the latest value published by the listener.
     * Who: Called by ScrapbookViewModel.loadIfNeeded.
     * When: When opening the creation screen to join an existing time point.
     */
    fun entry(groupId: String, entryId: String): ScrapbookEntry? =
        flows[groupId]?.value?.firstOrNull { it.id == entryId }

    /**
     * What: Updates an entry's title (any member can edit). Blank input is a no-op,
     *       leaving the existing title in place.
     * Who: Called by ScrapbookViewerViewModel on inline title edit.
     * When: On tapping "Done" in edit mode.
     */
    suspend fun updateTitle(groupId: String, entryId: String, title: String) {
        val newTitle = title.ifBlank { return }
        db.collection("groups").document(groupId)
            .collection("entries").document(entryId)
            .update("title", newTitle).await()
        // The entries listener re-fires on this update and republishes automatically.
    }

    /**
     * What: Appends a member's comment to an entry. Blank comments are ignored; the
     *       author is the real current user, not a hardcoded value.
     * Who: Called by ScrapbookViewerViewModel when a member posts a comment.
     * When: On tapping "Post".
     */
    suspend fun addComment(groupId: String, entryId: String, author: String, text: String) {
        if (text.isBlank()) return
        val commentRef = db.collection("groups").document(groupId)
            .collection("entries").document(entryId)
            .collection("comments").document()
        val doc = mapOf(
            "commentId" to commentRef.id,
            "author"    to currentUserName(),
            "text"      to text.trim(),
            "createdAt" to FieldValue.serverTimestamp()
        )
        commentRef.set(doc).await()
        refreshGroup(groupId)
    }

    /**
     * What: Detaches the snapshot listener for a group and drops its cached flow.
     * Who: Called by ScrapbookViewerViewModel.onCleared().
     * When: When the viewer leaves the composition for good.
     */
    fun detach(groupId: String) {
        listeners.remove(groupId)?.remove()
        flows.remove(groupId)
    }

    /** Detaches every active snapshot listener and clears all cached flows. */
    fun detachAll() {
        listeners.values.forEach { it.remove() }
        listeners.clear()
        flows.clear()
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /** The real current user's display name, falling back to "User". */
    private suspend fun currentUserName(): String =
        when (val result = AuthRepository.getCurrentUserName()) {
            is Result.Success -> result.data
            else              -> "User"
        }

    /** A stable picsum placeholder photo URL until Firebase Storage is wired up. */
    private fun placeholderPhoto(seed: String): String =
        "https://picsum.photos/seed/$seed/400/300"
}
