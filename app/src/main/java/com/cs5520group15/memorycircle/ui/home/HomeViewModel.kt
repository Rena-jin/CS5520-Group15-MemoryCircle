package com.cs5520group15.memorycircle.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cs5520group15.memorycircle.common.AuthRepository
import com.cs5520group15.memorycircle.common.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * What: Holds all UI state for the Home screen.
 * Who: Used by HomeScreen.
 * When: Created when HomeScreen is first displayed, survives configuration changes.
 */
class HomeViewModel : ViewModel() {

    // --- Data Models ---

    /**
     * What: Represents a single memory group (a group of people) on the Home screen.
     *       A group is NOT an event — it's a circle of people who share memories.
     * Who: Used by HomeViewModel and HomeScreen.
     * When: Instantiated when loading the list of groups.
     */
    data class Group(
        val id:          String,
        val name:        String,  // e.g. "Group 1" — the circle's name, not an event
        val date:        String,  // subtitle line, e.g. member summary / created date
        val memoryCount: Int,
        val colorType:   String  // "brown" or "sage" — controls card gradient color
    )

    // Private mutable state — only ViewModel can change
    private val _groups = MutableStateFlow<List<Group>>(emptyList())
    private val _userName = MutableStateFlow("")

    // Public read-only state — UI observes these
    val groups:    StateFlow<List<Group>> = _groups.asStateFlow()
    val userName:  StateFlow<String>      = _userName.asStateFlow()

    // Load dummy data when ViewModel is created
    // Firebase will replace this in a later phase
    init {
        loadDummyGroups()
        loadUserName()
    }

    /**
     * What: Reads the current user's name from Firebase via AuthRepository
     *       and publishes it to userName.
     * Who: Called automatically in the init block.
     * When: Once when the ViewModel is first created.
     */
    private fun loadUserName() = viewModelScope.launch {
        when (val result = AuthRepository.getCurrentUserName()) {
            is Result.Loading -> { /* repository returns terminal states; nothing to do */ }
            is Result.Success -> _userName.value = result.data
            is Result.Error   -> { /* keep blank on failure */ }
        }
    }

    /**
     * What: Loads a hardcoded list of groups for UI skeleton demonstration.
     * Who: Called automatically in the init block.
     * When: Once when the ViewModel is first created.
     */
    private fun loadDummyGroups() {
        _groups.value = listOf(
            Group("1", "Group 1", "4 members", 12, "brown"),
            Group("2", "Group 2", "3 members",  5, "sage"),
            Group("3", "Group 3", "6 members",  8, "brown")
        )
    }
}