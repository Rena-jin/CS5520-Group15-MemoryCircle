package com.cs5520group15.memorycircle.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cs5520group15.memorycircle.R
import com.cs5520group15.memorycircle.ui.theme.*
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.foundation.shape.CircleShape

/**
 * What: Login screen where users enter email and password to sign in.
 * Who: Called by MemoryCircleNavigation as the start destination.
 * When: Shown when the app first launches.
 */
@Composable
fun LoginScreen(
    onLoginSuccess:      () -> Unit,
    onNavigateToRegister: () -> Unit,
    viewModel: AuthViewModel = viewModel()
) {
    val email    by viewModel.email.collectAsStateWithLifecycle()
    val password by viewModel.password.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    var passwordVisible by remember { mutableStateOf(false) }
    var showForgotDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Collect one-shot events from ViewModel
    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is AuthViewModel.AuthEvent.ShowSnackbar  -> snackbarHostState.showSnackbar(event.message)
                is AuthViewModel.AuthEvent.NavigateToHome -> onLoginSuccess()
                else -> {}
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Cream
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Beige, Cream),
                        startY = 0f,
                        endY   = 900f
                    )
                )
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(80.dp))

            // Logo circle
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(72.dp)
                    .background(Color.White, CircleShape)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_favorite),
                    contentDescription = "Logo",
                    tint = Brown,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // App title
            Text(
                text  = "MemoryCircle",
                style = MaterialTheme.typography.displayLarge,
                color = Ink
            )
            Text(
                text  = "cherish every moment",
                style = MaterialTheme.typography.bodyMedium,
                color = InkTertiary
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Welcome text
            Text(
                text      = "Welcome back,\nyour memories await",
                style     = MaterialTheme.typography.titleLarge,
                color     = Ink,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Email field
            Text(
                text     = "EMAIL",
                style    = MaterialTheme.typography.labelSmall,
                color    = InkSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
            OutlinedTextField(
                value         = email,
                onValueChange = viewModel::onEmailChange,
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(28.dp),
                placeholder   = { Text("sarah@example.com", color = Brown.copy(alpha = 0.6f)) },
                leadingIcon   = {
                    Icon(
                        painter = painterResource(R.drawable.ic_email),
                        contentDescription = null,
                        tint = Brown
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine    = true,
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = Sage,
                    unfocusedBorderColor = Beige,
                    focusedContainerColor   = Color.White.copy(alpha = 0.8f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.8f)
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Password field
            Text(
                text     = "PASSWORD",
                style    = MaterialTheme.typography.labelSmall,
                color    = InkSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
            OutlinedTextField(
                value         = password,
                onValueChange = viewModel::onPasswordChange,
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(28.dp),
                placeholder   = { Text("••••••••", color = Brown.copy(alpha = 0.6f)) },
                leadingIcon   = {
                    Icon(
                        painter = painterResource(R.drawable.ic_lock),
                        contentDescription = null,
                        tint = Brown
                    )
                },
                trailingIcon  = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            painter = painterResource(
                                if (passwordVisible) R.drawable.ic_visibility
                                else R.drawable.ic_visibility_off
                            ),
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            tint = Brown
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine    = true,
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = Sage,
                    unfocusedBorderColor = Beige,
                    focusedContainerColor   = Color.White.copy(alpha = 0.8f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.8f)
                )
            )

            // Forgot password
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                TextButton(onClick = { showForgotDialog = true }) {
                    Text("Forgot password?", color = Brown, style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Sign In button
            Button(
                onClick  = viewModel::onLoginClick,
                enabled  = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape  = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor         = Ink,
                    contentColor           = Cream,
                    disabledContainerColor = BrownDisabled
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Cream, modifier = Modifier.size(24.dp))
                } else {
                    Text("Sign In", style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Navigate to Register
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    "New to MemoryCircle? ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkTertiary
                )
                TextButton(onClick = onNavigateToRegister) {
                    Text(
                        "Create Account",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AccentGreen
                    )
                }
            }
        }

        // Forgot-password dialog: enter an email and "receive" a reset link
        if (showForgotDialog) {
            ForgotPasswordDialog(
                initialEmail = email,
                onDismiss    = { showForgotDialog = false },
                onSend       = { resetEmail ->
                    viewModel.onForgotPassword(resetEmail)
                    showForgotDialog = false
                }
            )
        }
    }
}

/**
 * What: Dialog where the user enters an email to receive a password reset link.
 *       The "Send reset link" button is enabled only for a plausible email.
 * Who: Shown by LoginScreen when the user taps "Forgot password?".
 * When: While showForgotDialog is true.
 */
@Composable
private fun ForgotPasswordDialog(
    initialEmail: String,
    onDismiss:    () -> Unit,
    onSend:       (String) -> Unit
) {
    var resetEmail by remember { mutableStateOf(initialEmail) }
    val isValidEmail = resetEmail.isNotBlank() && resetEmail.contains("@") && resetEmail.contains(".")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Cream,
        title = {
            Text("Reset password", style = MaterialTheme.typography.titleLarge, color = Ink)
        },
        text = {
            Column {
                Text(
                    "Enter your email and we'll send you a link to reset your password.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkTertiary
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value         = resetEmail,
                    onValueChange = { resetEmail = it },
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(16.dp),
                    singleLine    = true,
                    placeholder   = { Text("sarah@example.com", color = Brown.copy(alpha = 0.6f)) },
                    leadingIcon   = {
                        Icon(
                            painter = painterResource(R.drawable.ic_email),
                            contentDescription = null,
                            tint = Brown
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = Sage,
                        unfocusedBorderColor = Beige
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSend(resetEmail) },
                enabled = isValidEmail
            ) {
                Text("Send reset link", color = if (isValidEmail) AccentGreen else BrownDisabled)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Brown)
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    MemoryCircleTheme {
        LoginScreen(onLoginSuccess = {}, onNavigateToRegister = {})
    }
}