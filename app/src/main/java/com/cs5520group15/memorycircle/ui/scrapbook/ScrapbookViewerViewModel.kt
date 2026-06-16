package com.cs5520group15.memorycircle.ui.scrapbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

/**
 * What: Mediates the viewer's edits (title edits, comments) to the shared
 *       ScrapbookRepository, which persists them to Firestore. The screen collects
 *       the repository's StateFlow directly for display, so any mutation here
 *       propagates automatically once Firestore confirms it.
 * Who: Used by ScrapbookViewerScreen.
 * When: Created when the viewer is displayed; bound to a group on first load.
 */
class ScrapbookViewerViewModel : ViewModel() {

    private var groupId: String? = null

    /** Binds this ViewModel to the group whose timeline is being viewed. */
    fun bind(groupId: String) {
        this.groupId = groupId
    }

    /**
     * What: Updates an entry's title (any member can edit).
     * Who: Called by ScrapbookViewerScreen when a member saves a title edit.
     * When: On tapping "Done" in edit mode.
     */
    fun updateEntryTitle(entryId: String, title: String) {
        val gid = groupId ?: return
        viewModelScope.launch {
            try {
                ScrapbookRepository.updateTitle(gid, entryId, title)
            } catch (e: Exception) {
                // Persisted edit failed; keep the UI as-is for now.
            }
        }
    }

    /**
     * What: Appends a member's comment to an entry. Blank comments are ignored.
     *       The author is resolved to the real current user inside the repository,
     *       so the passed-in value is not used.
     * Who: Called by ScrapbookViewerScreen when a member posts a comment.
     * When: On tapping "Post".
     */
    fun addComment(entryId: String, author: String, text: String) {
        val gid = groupId ?: return
        viewModelScope.launch {
            try {
                ScrapbookRepository.addComment(gid, entryId, author, text)
            } catch (e: Exception) {
                // Posting failed; keep the UI as-is for now.
            }
        }
    }

    /** Detaches this group's Firestore snapshot listener when the viewer is destroyed. */
    override fun onCleared() {
        super.onCleared()
        groupId?.let { ScrapbookRepository.detach(it) }
    }
}
