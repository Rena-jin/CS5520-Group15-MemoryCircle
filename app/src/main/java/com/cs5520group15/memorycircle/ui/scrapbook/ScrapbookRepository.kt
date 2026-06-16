package com.cs5520group15.memorycircle.ui.scrapbook

import com.cs5520group15.memorycircle.common.AuthRepository
import com.cs5520group15.memorycircle.common.FirebaseModule
import com.cs5520group15.memorycircle.common.Result
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
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
import java.time.YearMonth
import java.util.Locale
import java.util.UUID

/**
 * What: Firestore-backed store for every group's scrapbook timeline. The viewer and
 *       creation screens read and write through this single source of truth, so a
 *       post created on one screen shows up on the other. A real-time snapshot
 *       listener keeps each group's StateFlow in sync with Firestore.
 * Who: Used by ScrapbookViewerViewModel and ScrapbookViewModel (and the screens,
 *      which collect entriesFor(...) directly).
 * When: Accessed whenever a timeline is shown, created, or edited.
 *
 * Firestore layout (one scrapbook per group per month, id = "YYYY-MM"):
 *   groups/{groupId}/scrapbooks/{scrapbookId}/posts/{postId}
 *     postId, authorId, authorName, title, date (Timestamp), tags (List<String>),
 *     photos (List<Map> of { photoId, url, storagePath, description, uploaderId,
 *     uploadedAt }), commentCount (Int), createdAt (Timestamp)
 *   groups/{groupId}/scrapbooks/{scrapbookId}/posts/{postId}/comments/{commentId}
 *     commentId, author, text, createdAt (Timestamp)
 *
 * Note: the scrapbookId is computed from the current month internally, so the UI
 *       (which only knows the groupId) needs no changes. Photos use picsum.photos
 *       placeholders until Firebase Storage is wired up.
 */
object ScrapbookRepository {

    private val db = FirebaseModule.db

    // Internal scope for assembling nested data off the snapshot listener. The
    // repository is a singleton (no ViewModel), so it owns its own scope here;
    // UI-triggered writes are launched from the ViewModels' viewModelScope instead.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // groupId -> that group's timeline posts (the StateFlow the screens collect).
    private val flows = mutableMapOf<String, MutableStateFlow<List<ScrapbookEntry>>>()

    // groupId -> the active posts snapshot listener for that group.
    private val listeners = mutableMapOf<String, ListenerRegistration>()

    // Formats the stored `date` Timestamp back into the "June 1" day label the UI shows.
    private val dayLabel = SimpleDateFormat("MMMM d", Locale.ENGLISH)

    /** The current month's scrapbook id, e.g. "2026-06". */
    private fun currentScrapbookId(): String = YearMonth.now().toString()

    /** posts collection for a group's scrapbook. */
    private fun postsRef(groupId: String, scrapbookId: String): CollectionReference =
        db.collection("groups").document(groupId)
            .collection("scrapbooks").document(scrapbookId)
            .collection("posts")

    /**
     * What: Returns the live timeline flow for a group, attaching a Firestore snapshot
     *       listener on the current month's posts on first use.
     * Who: Called by the ViewModels and screens to observe posts.
     * When: On screen load.
     */
    fun entriesFor(groupId: String): StateFlow<List<ScrapbookEntry>> = flow(groupId).asStateFlow()

    private fun flow(groupId: String): MutableStateFlow<List<ScrapbookEntry>> {
        flows[groupId]?.let { return it }
        val f = MutableStateFlow<List<ScrapbookEntry>>(emptyList())
        flows[groupId] = f          // store before listening so the callback finds it
        startListening(groupId, currentScrapbookId())
        return f
    }

    /**
     * What: Attaches a real-time snapshot listener on the current month's posts,
     *       ordered by date descending, re-assembling each post with its comments
     *       subcollection and publishing to the group's StateFlow.
     * Who: Called by flow() the first time a group is observed.
     * When: Once per group, until detach()/detachAll() removes the listener.
     */
    private fun startListening(groupId: String, scrapbookId: String) {
        if (listeners.containsKey(groupId)) return
        val registration = postsRef(groupId, scrapbookId)
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
     * What: Builds the ScrapbookEntry list for a set of post documents, fetching each
     *       post's comments subcollection. Photos are read from the post's photos field.
     * Who: Used by the snapshot listener and by refreshGroup().
     */
    private suspend fun assemble(postDocs: List<DocumentSnapshot>): List<ScrapbookEntry> {
        return postDocs.map { doc ->
            val photos = (doc.get("photos") as? List<*>).orEmpty()
                .filterIsInstance<Map<*, *>>()
                .map { m ->
                    Photo(
                        photoId     = m["photoId"] as? String ?: "",
                        url         = m["url"] as? String ?: "",
                        storagePath = m["storagePath"] as? String ?: "",
                        description = m["description"] as? String ?: "",
                        uploaderId  = m["uploaderId"] as? String ?: ""
                    )
                }

            val commentsSnap = doc.reference.collection("comments")
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
                id           = doc.id,
                authorId     = doc.getString("authorId") ?: "",
                authorName   = doc.getString("authorName") ?: "",
                title        = doc.getString("title") ?: "",
                date         = dateLabel,
                tags         = (doc.get("tags") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                photos       = photos,
                comments     = comments,
                commentCount = (doc.getLong("commentCount") ?: 0L).toInt()
            )
        }
    }

    /**
     * What: One-shot re-fetch + re-assemble of a group's current-month posts. Used
     *       after writes that the posts snapshot listener does not observe (a new
     *       comment in a subcollection, or a photo appended to an existing post while
     *       its server timestamp is still resolving).
     */
    private suspend fun refreshGroup(groupId: String) {
        val snap = postsRef(groupId, currentScrapbookId())
            .orderBy("date", Query.Direction.DESCENDING)
            .get().await()
        flows[groupId]?.value = assemble(snap.documents)
    }

    /**
     * What: Adds a memory post. With joinPostId == null this creates a brand-new post
     *       (title + tags + the author's first photo). With joinPostId set it appends a
     *       photo to that existing post's photos field — the old "join a card" flow,
     *       now that photos are a list on the post rather than a subcollection.
     * Who: Called by ScrapbookViewModel when the creation screen saves.
     * When: On save (new-post or join mode).
     */
    suspend fun addPost(
        groupId:     String,
        title:       String,
        tags:        List<String>,
        description: String,
        joinPostId:  String? = null
    ) {
        val scrapbookId = currentScrapbookId()
        val uid  = AuthRepository.currentUid ?: ""
        val name = currentUserName()

        if (joinPostId == null) {
            // Create a new post with its first photo.
            val postRef = postsRef(groupId, scrapbookId).document()
            val postId  = postRef.id
            val photo = photoMap(
                seed        = postId,
                description = description,
                uploaderId  = uid
            )
            val postDoc = mapOf(
                "postId"       to postId,
                "authorId"     to uid,
                "authorName"   to name,
                "title"        to title,
                "date"         to FieldValue.serverTimestamp(),
                "tags"         to tags,
                "photos"       to listOf(photo),
                "commentCount" to 0,
                "createdAt"    to FieldValue.serverTimestamp()
            )
            postRef.set(postDoc).await()
        } else {
            // Append this member's photo to an existing post.
            val postRef = postsRef(groupId, scrapbookId).document(joinPostId)
            val photo = photoMap(
                seed        = UUID.randomUUID().toString(),
                description = description,
                uploaderId  = uid
            )
            postRef.update("photos", FieldValue.arrayUnion(photo)).await()
        }
        refreshGroup(groupId)
    }

    /**
     * What: Looks up a single post from the in-memory cache (used by the join flow to
     *       pre-fill title/tags). Reads the latest value published by the listener.
     * Who: Called by ScrapbookViewModel.loadIfNeeded.
     * When: When opening the creation screen to join an existing post.
     */
    fun entry(groupId: String, entryId: String): ScrapbookEntry? =
        flows[groupId]?.value?.firstOrNull { it.id == entryId }

    /**
     * What: Updates a post's title (any member can edit). Blank input is a no-op,
     *       leaving the existing title in place.
     * Who: Called by ScrapbookViewerViewModel on inline title edit.
     * When: On tapping "Done" in edit mode.
     */
    suspend fun updateTitle(groupId: String, entryId: String, title: String) {
        val newTitle = title.ifBlank { return }
        postsRef(groupId, currentScrapbookId()).document(entryId)
            .update("title", newTitle).await()
        // The posts listener re-fires on this update and republishes automatically.
    }

    /**
     * What: Appends a member's comment to a post and bumps its commentCount. Blank
     *       comments are ignored; the author is the real current user, not a passed-in
     *       value.
     * Who: Called by ScrapbookViewerViewModel when a member posts a comment.
     * When: On tapping "Post".
     */
    suspend fun addComment(groupId: String, entryId: String, author: String, text: String) {
        if (text.isBlank()) return
        val postRef = postsRef(groupId, currentScrapbookId()).document(entryId)
        val commentRef = postRef.collection("comments").document()
        val doc = mapOf(
            "commentId" to commentRef.id,
            "author"    to currentUserName(),
            "text"      to text.trim(),
            "createdAt" to FieldValue.serverTimestamp()
        )
        commentRef.set(doc).await()
        postRef.update("commentCount", FieldValue.increment(1)).await()
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

    /**
     * Builds a photo map for the post's photos array. Uses a picsum placeholder URL
     * (no Storage yet) and a concrete Timestamp — serverTimestamp() is not allowed
     * inside array elements.
     */
    private fun photoMap(seed: String, description: String, uploaderId: String): Map<String, Any> =
        mapOf(
            "photoId"     to UUID.randomUUID().toString(),
            "url"         to placeholderPhoto(seed),
            "storagePath" to "",
            "description" to description,
            "uploaderId"  to uploaderId,
            "uploadedAt"  to Timestamp.now()
        )

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
