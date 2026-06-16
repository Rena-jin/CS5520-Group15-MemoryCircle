package com.cs5520group15.memorycircle.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cs5520group15.memorycircle.common.AuthRepository
import com.cs5520group15.memorycircle.common.Result
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * What: Holds all UI state and business logic for Login and Register screens.
 * Who: Used by LoginScreen and RegisterScreen.
 * When: Created once and survives configuration changes (e.g. screen rotation).
 */
class AuthViewModel : ViewModel() {

    // --- UI State (StateFlow) ---
    // These are persistent states that survive rotation
    // Private mutable version — only ViewModel can change it
    private val _email    = MutableStateFlow("")
    private val _password = MutableStateFlow("")
    private val _name     = MutableStateFlow("")
    private val _isLoading = MutableStateFlow(false)

    // Public read-only version — UI can only observe, not change directly
    val email:     StateFlow<String>  = _email.asStateFlow()
    val password:  StateFlow<String>  = _password.asStateFlow()
    val name:      StateFlow<String>  = _name.asStateFlow()
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // --- One-shot Events (Channel) ---
    // Used for things that should only happen once (e.g. show a Snackbar)
    sealed class AuthEvent {
        data class ShowSnackbar(val message: String) : AuthEvent()
        object NavigateToHome     : AuthEvent()
        object NavigateToRegister : AuthEvent()
    }

    private val _events = Channel<AuthEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // --- Event handlers called by the UI ---

    /**
     * What: Updates the email state when the user types in the email field.
     * Who: Called by LoginScreen and RegisterScreen's email TextField.
     * When: Every time the user types a character.
     */
    fun onEmailChange(value: String) {
        _email.value = value
    }

    /**
     * What: Updates the password state when the user types in the password field.
     * Who: Called by LoginScreen and RegisterScreen's password TextField.
     * When: Every time the user types a character.
     */
    fun onPasswordChange(value: String) {
        _password.value = value
    }

    /**
     * What: Updates the name state when the user types in the name field.
     * Who: Called by RegisterScreen's name TextField.
     * When: Every time the user types a character.
     */
    fun onNameChange(value: String) {
        _name.value = value
    }

    /**
     * What: Validates login input and triggers navigation to Home on success.
     *       Shows a Snackbar if validation fails.
     * Who: Called by LoginScreen when the user taps "Sign In".
     * When: On Sign In button click.
     */
    fun onLoginClick() = viewModelScope.launch {
        // Basic validation
        if (_email.value.isBlank() || _password.value.isBlank()) {
            _events.send(AuthEvent.ShowSnackbar("Please fill in all fields"))
            return@launch
        }
        // Real Firebase login via AuthRepository
        _isLoading.value = true
        when (val result = AuthRepository.login(_email.value.trim(), _password.value)) {
            is Result.Loading -> { /* repository returns terminal states; nothing to do */ }
            is Result.Success -> {
                _isLoading.value = false
                _events.send(AuthEvent.NavigateToHome)
            }
            is Result.Error -> {
                _isLoading.value = false
                _events.send(AuthEvent.ShowSnackbar(result.message))
            }
        }
    }

    /**
     * What: Sends a real Firebase password reset link to the given email.
     *       Shows a Snackbar confirming success or reporting the error.
     * Who: Called by LoginScreen's "Forgot password?" dialog.
     * When: On tapping "Send reset link".
     */
    fun onForgotPassword(email: String) = viewModelScope.launch {
        if (email.isBlank()) {
            _events.send(AuthEvent.ShowSnackbar("Please enter your email"))
            return@launch
        }
        sendPasswordReset(email.trim())
    }

    /**
     * What: Calls AuthRepository.sendPasswordReset() and reports the outcome
     *       via a Snackbar.
     * Who: Used by onForgotPassword (and any caller needing a password reset).
     * When: On request to send a password reset email.
     */
    fun sendPasswordReset(email: String) = viewModelScope.launch {
        _isLoading.value = true
        when (val result = AuthRepository.sendPasswordReset(email)) {
            is Result.Loading -> { /* repository returns terminal states; nothing to do */ }
            is Result.Success -> {
                _isLoading.value = false
                _events.send(AuthEvent.ShowSnackbar("Password reset link sent to $email"))
            }
            is Result.Error -> {
                _isLoading.value = false
                _events.send(AuthEvent.ShowSnackbar(result.message))
            }
        }
    }

    /**
     * What: Validates register input and triggers navigation to Home on success.
     *       Shows a Snackbar if validation fails.
     * Who: Called by RegisterScreen when the user taps "Create Account".
     * When: On Create Account button click.
     */
    fun onRegisterClick() = viewModelScope.launch {
        if (_name.value.isBlank() || _email.value.isBlank() || _password.value.isBlank()) {
            _events.send(AuthEvent.ShowSnackbar("Please fill in all fields"))
            return@launch
        }
        // Real Firebase registration via AuthRepository
        _isLoading.value = true
        when (val result = AuthRepository.register(
            _name.value.trim(),
            _email.value.trim(),
            _password.value
        )) {
            is Result.Loading -> { /* repository returns terminal states; nothing to do */ }
            is Result.Success -> {
                _isLoading.value = false
                _events.send(AuthEvent.NavigateToHome)
            }
            is Result.Error -> {
                _isLoading.value = false
                _events.send(AuthEvent.ShowSnackbar(result.message))
            }
        }
    }
}