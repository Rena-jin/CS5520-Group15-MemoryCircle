package com.cs5520group15.memorycircle.ui.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cs5520group15.memorycircle.common.AuthRepository
import com.cs5520group15.memorycircle.common.FirebaseModule
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * What: Holds the state shown on the group detail page — the group name, the
 *       flat member list (used both as thumbnails and as the "view all" roster),
 *       and the list of per-month scrapbook entries that belong to this group.
 * Who: Used by GroupDetailScreen.
 * When: Created when the screen is shown for a specific groupId; survives config
 *       changes. The mock sources will be replaced by GroupRepository / Firestore.
 */
class GroupDetailViewModel : ViewModel() {

    /**
     * What: One scrapbook bucket for this group within a single month (e.g.
     *       "Group 1 · January 2025 · 12 memories"). Mirrors the per-row shape
     *       used on the global Memories tab so the visual treatment stays
     *       consistent across both screens.
     * Who: Used by GroupDetailViewModel and GroupDetailScreen.
     * When: Instantiated when loading mock months for a group.
     */
    data class MonthScrapbook(
        val id:          String,
        val month:       String,  // e.g. "January"
        val year:        String,  // e.g. "2025"
        val memoryCount: Int,
        val colorType:   String   // "brown" or "sage" — accent chip color
    )

    private var groupId: String? = null

    private val _groupName = MutableStateFlow("")
    private val _members   = MutableStateFlow<List<Member>>(emptyList())
    private val _months    = MutableStateFlow<List<MonthScrapbook>>(emptyList())

    val groupName: StateFlow<String>              = _groupName.asStateFlow()
    val members:   StateFlow<List<Member>>        = _members.asStateFlow()
    val months:    StateFlow<List<MonthScrapbook>> = _months.asStateFlow()

    /**
     * What: Loads the group name, roster, and monthly scrapbook list for one group.
     *       Mock data for now; reuses the same naming/roster shapes as
     *       GroupMembersViewModel so the two screens stay coherent.
     * Who: Called by GroupDetailScreen in a LaunchedEffect keyed on groupId.
     * When: On screen open, and again if groupId changes.
     */
    fun bind(groupId: String) {
        this.groupId = groupId
        _groupName.value = mockGroupName(groupId)
        _members.value   = mockMembers(groupId)
        _months.value    = mockMonths(groupId)
    }

    /**
     * What: Removes the current user from this group in Firestore — drops their uid
     *       from the group's memberIds array, decrements memberCount, and deletes
     *       their member document. Once memberIds no longer contains the uid, the
     *       Home screen's live query drops the group automatically.
     * Who: Called by GroupDetailScreen when the user confirms "Yes, leave".
     * When: On leave-group confirmation.
     * @param onLeft invoked (on success) so the screen can navigate away.
     */
    fun leaveGroup(onLeft: () -> Unit) {
        val gid = groupId ?: return
        val uid = AuthRepository.currentUid ?: return
        viewModelScope.launch {
            try {
                val groupRef = FirebaseModule.db.collection("groups").document(gid)
                groupRef.update(
                    "memberIds",   FieldValue.arrayRemove(uid),
                    "memberCount", FieldValue.increment(-1)
                ).await()
                groupRef.collection("members").document(uid).delete().await()
                onLeft()
            } catch (e: Exception) {
                // Leaving failed; stay on the screen so the user can retry.
            }
        }
    }

    private fun mockGroupName(groupId: String): String = when (groupId) {
        "1"  -> "Weekend Crew"
        "2"  -> "Family Circle"
        "3"  -> "Travel Buddies"
        else -> "Group $groupId"
    }

    private fun mockMembers(groupId: String): List<Member> = when (groupId) {
        "1" -> listOf(
            Member("u_sarah", "Sarah Chen",  34, isOnline = true),
            Member("u_emma",  "Emma Wilson", 28, isOnline = true),
            Member("u_james", "James Liu",   21),
            Member("u_mia",   "Mia Torres",  18),
            Member("u_lila",  "Lila Nguyen",  9)
        )
        "2" -> listOf(
            Member("u_sarah", "Sarah Chen",  42, isOnline = true),
            Member("u_dad",   "David Chen",  30),
            Member("u_mom",   "Helen Chen",  27)
        )
        else -> listOf(
            Member("u_sarah", "Sarah Chen",   16, isOnline = true),
            Member("u_alex",  "Alex Park",    14),
            Member("u_zoe",   "Zoe Martin",   11),
            Member("u_kai",   "Kai Nakamura",  8),
            Member("u_noah",  "Noah Bennett",  6),
            Member("u_riya",  "Riya Patel",    4)
        )
    }

    private fun mockMonths(groupId: String): List<MonthScrapbook> {
        val color = if (groupId == "2") "sage" else "brown"
        return listOf(
            MonthScrapbook("${groupId}_2025_03", "March",    "2025", 9,  color),
            MonthScrapbook("${groupId}_2025_02", "February", "2025", 7,  color),
            MonthScrapbook("${groupId}_2025_01", "January",  "2025", 12, color),
            MonthScrapbook("${groupId}_2024_12", "December", "2024", 5,  color)
        )
    }
}
