package com.cs5520group15.memorycircle.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cs5520group15.memorycircle.common.AuthRepository
import com.cs5520group15.memorycircle.common.FirebaseModule
import com.cs5520group15.memorycircle.common.Result
import com.google.firebase.firestore.ListenerRegistration
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

    // Holds the active Firestore snapshot listener so we can detach it
    // in onCleared() and avoid leaking it past the ViewModel's lifecycle.
    private var groupsListener: ListenerRegistration? = null

    // Load real data when ViewModel is created
    init {
        loadGroups()
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
     * What: Subscribes to all Firestore groups whose memberIds array contains the
     *       current user's uid, and republishes them to the groups StateFlow.
     *       Uses a real-time snapshot listener, so the list updates automatically
     *       whenever a group the user belongs to is added, changed, or removed.
     * Who: Called automatically in the init block.
     * When: Once when the ViewModel is first created; the listener then fires
     *       on every server-side change until onCleared() detaches it.
     */
    private fun loadGroups() {
        val uid = AuthRepository.currentUid ?: ""
        groupsListener = FirebaseModule.db.collection("groups")
            .whereArrayContains("memberIds", uid)
            .addSnapshotListener { snapshot, error ->
                // On error or no data, leave the current list untouched.
                if (error != null || snapshot == null) return@addSnapshotListener

                _groups.value = snapshot.documents.map { doc ->
                    val memberCount = (doc.getLong("memberCount") ?: 0L).toInt()
                    Group(
                        id          = doc.id,
                        name        = doc.getString("name") ?: "",
                        // Subtitle line: member summary (createdAt isn't reliably
                        // available until the server timestamp resolves).
                        date        = "$memberCount members",
                        memoryCount = (doc.getLong("memoryCount") ?: 0L).toInt(),
                        colorType   = doc.getString("colorType") ?: "brown"
                    )
                }
            }
    }

    /**
     * What: Detaches the Firestore snapshot listener when the ViewModel is destroyed.
     * Who: Called by the framework when HomeScreen leaves the composition for good.
     * When: On ViewModel teardown — prevents the listener from leaking.
     */
    override fun onCleared() {
        super.onCleared()
        groupsListener?.remove()
        groupsListener = null
    }
}