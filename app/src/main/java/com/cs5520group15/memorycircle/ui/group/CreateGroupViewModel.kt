package com.cs5520group15.memorycircle.ui.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cs5520group15.memorycircle.common.AuthRepository
import com.cs5520group15.memorycircle.common.FirebaseModule
import com.cs5520group15.memorycircle.common.Result
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * What: Holds UI state and business logic for the "create a new group" flow.
 * Who: Used by CreateGroupScreen.
 * When: Created when CreateGroupScreen is shown, survives configuration changes.
 */
class CreateGroupViewModel : ViewModel() {

    // --- UI State (StateFlow) ---
    private val _name      = MutableStateFlow("")
    private val _colorType = MutableStateFlow("brown")  // "brown" or "sage"
    private val _isLoading = MutableStateFlow(false)

    // Public read-only state — UI observes these
    val name:      StateFlow<String>  = _name.asStateFlow()
    val colorType: StateFlow<String>  = _colorType.asStateFlow()
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // --- One-shot Events (Channel) ---
    sealed class CreateGroupEvent {
        data class ShowSnackbar(val message: String) : CreateGroupEvent()
        object NavigateBack : CreateGroupEvent()
    }

    private val _events = Channel<CreateGroupEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // --- Event handlers called by the UI ---

    /** Updates the group name as the user types. */
    fun onNameChange(value: String) {
        _name.value = value
    }

    /** Updates the chosen card color ("brown" or "sage"). */
    fun onColorChange(value: String) {
        _colorType.value = value
    }

    /**
     * What: Validates input, then creates a new group document in Firestore plus
     *       an owner member sub-document, and navigates back to Home on success.
     * Who: Called by CreateGroupScreen when the user taps "Create".
     * When: On Create button click.
     */
    fun onCreateClick() = viewModelScope.launch {
        val groupName = _name.value.trim()
        if (groupName.isBlank()) {
            _events.send(CreateGroupEvent.ShowSnackbar("Please enter a group name"))
            return@launch
        }

        val uid = AuthRepository.currentUid
        if (uid == null) {
            _events.send(CreateGroupEvent.ShowSnackbar("You must be logged in to create a group"))
            return@launch
        }

        _isLoading.value = true
        try {
            val db = FirebaseModule.db

            // Step 1: create the group document with an auto-generated id.
            val groupRef = db.collection("groups").document()
            val groupId  = groupRef.id
            val groupDoc = mapOf(
                "groupId"     to groupId,
                "name"        to groupName,
                "colorType"   to _colorType.value,
                "createdAt"   to FieldValue.serverTimestamp(),
                "ownerId"     to uid,
                "memberIds"   to listOf(uid),
                "memberCount" to 1,
                "memoryCount" to 0
            )
            groupRef.set(groupDoc).await()

            // Step 2: add the creator as the owner member.
            val ownerName = when (val result = AuthRepository.getCurrentUserName()) {
                is Result.Success -> result.data
                else              -> "User"
            }
            val memberDoc = mapOf(
                "uid"      to uid,
                "name"     to ownerName,
                "role"     to "owner",
                "joinedAt" to FieldValue.serverTimestamp()
            )
            groupRef.collection("members").document(uid).set(memberDoc).await()

            _isLoading.value = false
            _events.send(CreateGroupEvent.NavigateBack)
        } catch (e: Exception) {
            _isLoading.value = false
            _events.send(CreateGroupEvent.ShowSnackbar(e.message ?: "Failed to create group"))
        }
    }
}
