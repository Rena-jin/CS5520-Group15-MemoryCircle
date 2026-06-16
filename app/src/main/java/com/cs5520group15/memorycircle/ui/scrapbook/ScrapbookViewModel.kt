package com.cs5520group15.memorycircle.ui.scrapbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cs5520group15.memorycircle.common.FirebaseModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * What: Holds the UI state for the scrapbook creation screen, which serves two
 *       modes: creating a brand-new time point (sets title + tags + first photo +
 *       description) or joining an existing one (adds only this member's photo +
 *       description; title + tags are inherited and read-only).
 * Who: Used by ScrapbookScreen.
 * When: Created when the creation screen is displayed.
 */
class ScrapbookViewModel : ViewModel() {

    // --- State ---
    private val _title            = MutableStateFlow("")
    private val _description      = MutableStateFlow("")
    private val _tags             = MutableStateFlow<List<String>>(emptyList())
    private val _selectedPhotoUri = MutableStateFlow<String?>(null)
    private val _selectedDate     = MutableStateFlow(LocalDate.now())
    private val _takenDates       = MutableStateFlow<Set<LocalDate>>(emptySet())

    val title:            StateFlow<String>        = _title.asStateFlow()
    val description:      StateFlow<String>        = _description.asStateFlow()
    val tags:             StateFlow<List<String>>  = _tags.asStateFlow()
    val selectedPhotoUri: StateFlow<String?>       = _selectedPhotoUri.asStateFlow()
    val selectedDate:     StateFlow<LocalDate>     = _selectedDate.asStateFlow()
    val takenDates:       StateFlow<Set<LocalDate>> = _takenDates.asStateFlow()

    // In join mode this is set; title + tags are then pre-filled and read-only.
    private var joinEntryId: String? = null
    private var loaded = false

    val isJoinMode: Boolean get() = joinEntryId != null

    /**
     * What: Pre-loads title + tags from an existing entry when joining it, so the
     *       creator's title/tags show (read-only) while the user adds their own
     *       photo + description. No-op when creating a new time point.
     * Who: Called by ScrapbookScreen on first composition.
     * When: Once per ViewModel.
     */
    fun loadIfNeeded(groupId: String, entryId: String?) {
        if (loaded) return
        loaded = true
        joinEntryId = entryId
        if (entryId != null) {
            ScrapbookRepository.entry(groupId, entryId)?.let { existing ->
                _title.value = existing.title
                _tags.value  = existing.tags
            }
        }
        loadTakenDates(groupId)
    }

    /**
     * What: Loads the days in the current month that already have a post for this group,
     *       so the date picker can gray them out. Queries the current month's posts and
     *       maps each post's date Timestamp to a LocalDate.
     * Who: Called by loadIfNeeded.
     * When: Once per ViewModel, on first load.
     */
    private fun loadTakenDates(groupId: String) {
        val scrapbookId = YearMonth.now().toString()   // current month, e.g. "2026-06"
        viewModelScope.launch {
            try {
                val snapshot = FirebaseModule.db.collection("groups").document(groupId)
                    .collection("scrapbooks").document(scrapbookId)
                    .collection("posts")
                    .get().await()
                val zone = ZoneId.systemDefault()
                _takenDates.value = snapshot.documents.mapNotNull { doc ->
                    doc.getTimestamp("date")?.toDate()?.toInstant()?.atZone(zone)?.toLocalDate()
                }.toSet()
            } catch (e: Exception) {
                // Leave taken dates empty on failure.
            }
        }
    }

    fun onTitleChange(value: String)       { _title.value = value }
    fun onDescriptionChange(value: String) { _description.value = value }
    fun onPhotoSelected(uri: String)       { _selectedPhotoUri.value = uri }
    fun onDateSelected(date: LocalDate)    { _selectedDate.value = date }

    /**
     * What: Adds a "#"-prefixed tag (new-entry mode only).
     * Who: Called by ScrapbookScreen's add-tag input.
     */
    fun onAddTag(tag: String) {
        if (tag.isBlank()) return
        _tags.value = _tags.value + "#${tag.removePrefix("#")}"
    }

    fun onRemoveTag(tag: String) {
        _tags.value = _tags.value.filter { it != tag }
    }

    /**
     * What: True once the form has enough to save: always a photo, plus a title
     *       when creating a new time point.
     * Who: Used by ScrapbookScreen to enable/disable the save button.
     */
    val canSave: Boolean
        get() = _selectedPhotoUri.value != null && (isJoinMode || _title.value.isNotBlank())

    /**
     * What: Persists the contribution — creating a new time point or appending to
     *       an existing one — then signals completion.
     * Who: Called by ScrapbookScreen when the user taps save.
     * When: On save, only when canSave is true.
     */
    fun save(groupId: String, today: String) {
        // A photo must have been picked (canSave guards this too).
        if (_selectedPhotoUri.value == null) return
        // Author + photo URL are resolved inside the repository; the picked URI is a
        // placeholder for now (no Storage yet). The post date comes from _selectedDate;
        // `today` (String) is unused but kept so the screen's save(groupId, today) call
        // is unchanged.
        val description = _description.value.trim()
        val joinId = joinEntryId
        viewModelScope.launch {
            try {
                ScrapbookRepository.addPost(
                    groupId     = groupId,
                    title       = _title.value.trim(),
                    tags        = _tags.value,
                    description = description,
                    date        = _selectedDate.value,
                    joinPostId  = joinId
                )
            } catch (e: Exception) {
                // Save failed; the form stays as-is for now.
            }
        }
    }
}
