package com.cs5520group15.memorycircle.ui.group

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cs5520group15.memorycircle.ui.common.MemoryCircleTopBar
import com.cs5520group15.memorycircle.ui.theme.*
import kotlinx.coroutines.flow.collectLatest

/**
 * What: The "create a new group" flow, where a user names a new group (a circle
 *       of people), picks a card color, and creates it. Creating the group writes
 *       it to Firestore via CreateGroupViewModel and returns to Home.
 *       (The richer contact-picker UI is owned by a teammate and lands later;
 *       this screen provides the minimal create flow so the "+" button works.)
 * Who: Called by MemoryCircleNavigation when the user taps the "+" FAB on HomeScreen.
 * When: Displayed when navigating to the CreateGroup route.
 */
@Composable
fun CreateGroupScreen(
    onBack: () -> Unit,
    viewModel: CreateGroupViewModel = viewModel()
) {
    val name      by viewModel.name.collectAsStateWithLifecycle()
    val colorType by viewModel.colorType.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    // Collect one-shot events from the ViewModel.
    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is CreateGroupViewModel.CreateGroupEvent.ShowSnackbar ->
                    snackbarHostState.showSnackbar(event.message)
                is CreateGroupViewModel.CreateGroupEvent.NavigateBack ->
                    onBack()
            }
        }
    }

    Scaffold(
        containerColor = Cream,
        snackbarHost   = { SnackbarHost(snackbarHostState) },
        topBar = {
            MemoryCircleTopBar(
                title    = "New Group",
                showBack = true,
                onBack   = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text      = "Name your new group",
                style     = MaterialTheme.typography.titleLarge,
                color     = Ink,
                textAlign = TextAlign.Center
            )
            Text(
                text      = "Give your circle a name to start sharing memories together.",
                style     = MaterialTheme.typography.bodyMedium,
                color     = InkTertiary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Group name field
            Text(
                text     = "GROUP NAME",
                style    = MaterialTheme.typography.labelSmall,
                color    = InkSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
            OutlinedTextField(
                value         = name,
                onValueChange = viewModel::onNameChange,
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(28.dp),
                placeholder   = { Text("e.g. Weekend Crew", color = Brown.copy(alpha = 0.6f)) },
                singleLine    = true,
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor      = Sage,
                    unfocusedBorderColor    = Beige,
                    focusedContainerColor   = Color.White.copy(alpha = 0.8f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.8f)
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Card color picker
            Text(
                text     = "CARD COLOR",
                style    = MaterialTheme.typography.labelSmall,
                color    = InkSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ColorSwatch(
                    swatchColor = Brown,
                    selected    = colorType == "brown",
                    onClick     = { viewModel.onColorChange("brown") }
                )
                ColorSwatch(
                    swatchColor = Sage,
                    selected    = colorType == "sage",
                    onClick     = { viewModel.onColorChange("sage") }
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Create button
            Button(
                onClick  = viewModel::onCreateClick,
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
                    Text("Create Group", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/**
 * What: A round, tappable color swatch for the card-color picker; shows a ring
 *       when selected.
 * Who: Used by CreateGroupScreen's color picker.
 */
@Composable
private fun ColorSwatch(
    swatchColor: Color,
    selected:    Boolean,
    onClick:     () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(swatchColor, CircleShape)
            .then(
                if (selected) Modifier.border(3.dp, Ink, CircleShape)
                else Modifier
            )
            .clickable(onClick = onClick)
    )
}

@Preview(showBackground = true)
@Composable
fun CreateGroupScreenPreview() {
    MemoryCircleTheme {
        CreateGroupScreen(onBack = {})
    }
}
