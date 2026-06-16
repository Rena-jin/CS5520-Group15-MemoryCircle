package com.cs5520group15.memorycircle.ui.scrapbook

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.cs5520group15.memorycircle.ui.common.MemoryCircleTopBar
import com.cs5520group15.memorycircle.ui.theme.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * What: Screen for adding a memory. In new-entry mode the user sets a title, tags,
 *       picks a photo from the album and writes their own description, creating a
 *       new time point dated today. In join mode (entryId != null) the title + tags
 *       are inherited and shown read-only, and the user only adds their own photo +
 *       description to the existing time point.
 * Who: Called by MemoryCircleNavigation for the ScrapbookDetail route.
 * When: Opened from the timeline "+" FAB (new) or a card's "Add my photo" CTA (join).
 */
@Composable
fun ScrapbookScreen(
    groupId:   String,
    entryId:   String? = null,
    onBack:    () -> Unit,
    onSaved:   () -> Unit = {},
    viewModel: ScrapbookViewModel = viewModel()
) {
    LaunchedEffect(groupId, entryId) {
        viewModel.loadIfNeeded(groupId, entryId)
    }

    val title        by viewModel.title.collectAsStateWithLifecycle()
    val description  by viewModel.description.collectAsStateWithLifecycle()
    val tags         by viewModel.tags.collectAsStateWithLifecycle()
    val photoUri     by viewModel.selectedPhotoUri.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val takenDates   by viewModel.takenDates.collectAsStateWithLifecycle()

    val isJoinMode = viewModel.isJoinMode
    val today = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM d", Locale.ENGLISH)) }

    var newTagInput by remember { mutableStateOf("") }
    var showAddTag  by remember { mutableStateOf(false) }

    // Android Photo Picker — no runtime permission needed.
    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) viewModel.onPhotoSelected(uri.toString()) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor   = Sage,
        unfocusedBorderColor = Beige
    )

    Scaffold(
        containerColor = Cream,
        topBar = {
            MemoryCircleTopBar(
                title    = if (isJoinMode) "Add Your Photo" else "New Memory",
                showBack = true,
                onBack   = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {

            // --- Date (new entries pick a day in the current month; join inherits
            //     the existing day). Days that already have a post are disabled. ---
            if (!isJoinMode) {
                val currentMonth = remember { YearMonth.now() }
                val dateListState = rememberLazyListState()
                // On first show, scroll so today's (selected) chip is visible — with the
                // previous day peeking on the left to hint that earlier days exist. The
                // user can then swipe left/right to pick another day.
                LaunchedEffect(Unit) {
                    dateListState.scrollToItem((selectedDate.dayOfMonth - 2).coerceAtLeast(0))
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("DATE")
                    LazyRow(
                        state                 = dateListState,
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(currentMonth.lengthOfMonth()) { index ->
                            val day  = index + 1
                            val date = currentMonth.atDay(day)
                            DayChip(
                                day      = day,
                                selected = date == selectedDate,
                                disabled = takenDates.contains(date),
                                onClick  = { viewModel.onDateSelected(date) }
                            )
                        }
                    }
                }
            }

            // --- Title (editable when creating, read-only when joining) ---
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel("TITLE")
                if (isJoinMode) {
                    Text(
                        text  = title.ifBlank { "Untitled" },
                        style = MaterialTheme.typography.titleLarge,
                        color = Ink
                    )
                } else {
                    OutlinedTextField(
                        value         = title,
                        onValueChange = viewModel::onTitleChange,
                        modifier      = Modifier.fillMaxWidth(),
                        singleLine    = true,
                        shape         = RoundedCornerShape(12.dp),
                        placeholder   = { Text("Name this moment", style = MaterialTheme.typography.bodyMedium) },
                        colors        = fieldColors
                    )
                }
            }

            // --- Tags ---
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel("TAGS")
                FlowRow(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement   = Arrangement.spacedBy(8.dp)
                ) {
                    if (isJoinMode) {
                        // Inherited tags, read-only
                        if (tags.isEmpty()) {
                            Text(
                                text  = "No tags",
                                style = MaterialTheme.typography.bodyMedium,
                                color = InkTertiary
                            )
                        } else {
                            tags.forEach { ReadOnlyTagChip(it) }
                        }
                    } else {
                        tags.forEach { tag ->
                            TagChip(label = tag, onRemove = { viewModel.onRemoveTag(tag) })
                        }
                        if (!showAddTag) {
                            AddTagChip(onClick = { showAddTag = true })
                        }
                    }
                }

                if (!isJoinMode && showAddTag) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value         = newTagInput,
                            onValueChange = { newTagInput = it },
                            modifier      = Modifier.weight(1f),
                            shape         = RoundedCornerShape(20.dp),
                            singleLine    = true,
                            placeholder   = { Text("Enter a tag", style = MaterialTheme.typography.bodyMedium) },
                            colors        = fieldColors
                        )
                        TextButton(onClick = {
                            viewModel.onAddTag(newTagInput)
                            newTagInput = ""
                            showAddTag  = false
                        }) {
                            Text("Add", color = AccentGreen)
                        }
                    }
                }
            }

            // --- Photo from album (one per person) ---
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel("YOUR PHOTO")
                val currentPhoto = photoUri
                if (currentPhoto == null) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.5.dp, Beige, RoundedCornerShape(16.dp))
                            .clickable {
                                pickPhoto.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                    ) {
                        Text("📷  Choose from album", color = Brown, style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    AsyncImage(
                        model              = currentPhoto,
                        contentDescription = "Selected photo",
                        contentScale       = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                pickPhoto.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                    )
                    TextButton(onClick = {
                        pickPhoto.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }) {
                        Text("Change photo", color = AccentGreen)
                    }
                }
            }

            // --- Your description ---
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel("YOUR DESCRIPTION")
                OutlinedTextField(
                    value         = description,
                    onValueChange = viewModel::onDescriptionChange,
                    modifier      = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    shape         = RoundedCornerShape(16.dp),
                    placeholder   = {
                        Text(
                            "Say something about your photo...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Brown.copy(alpha = 0.5f)
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor      = Sage,
                        unfocusedBorderColor    = Beige,
                        focusedContainerColor   = Color.White.copy(alpha = 0.8f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.8f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- Save Button ---
            Button(
                onClick  = {
                    viewModel.save(groupId, today)
                    onSaved()
                },
                enabled  = viewModel.canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape  = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Ink,
                    contentColor   = Cream
                )
            ) {
                Text(
                    text  = if (isJoinMode) "✦  Add to Timeline" else "✦  Create Memory",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * What: A small uppercase section header used throughout the creation form.
 * Who: Called by ScrapbookScreen for each section.
 */
@Composable
private fun SectionLabel(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelSmall, color = InkSecondary)
}

/**
 * What: A selectable day-number chip for the date picker. Highlighted when it is the
 *       selected date; grayed out and untappable when that day already has a post.
 * Who: Called by ScrapbookScreen's date row for each day of the current month.
 * When: Rendered in new-entry mode.
 */
@Composable
private fun DayChip(
    day:      Int,
    selected: Boolean,
    disabled: Boolean,
    onClick:  () -> Unit
) {
    val background = when {
        selected -> Ink
        disabled -> Beige
        else     -> Cream
    }
    val textColor = when {
        selected -> Cream
        disabled -> InkTertiary
        else     -> Ink
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .then(
                if (!selected) Modifier.border(1.dp, Beige, RoundedCornerShape(12.dp))
                else Modifier
            )
            .then(
                if (!disabled) Modifier.clickable { onClick() }
                else Modifier
            )
    ) {
        Text(
            text  = day.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = textColor
        )
    }
}

/**
 * What: Displays a single tag as a removable chip.
 * Who: Called by ScrapbookScreen for each editable tag.
 * When: Rendered for every tag in new-entry mode.
 */
@Composable
fun TagChip(label: String, onRemove: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, Sage, RoundedCornerShape(20.dp))
            .clickable { onRemove() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = AccentGreen)
    }
}

/**
 * What: Displays an inherited tag as a non-removable chip (join mode).
 * Who: Called by ScrapbookScreen when showing a creator's tags read-only.
 */
@Composable
fun ReadOnlyTagChip(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, Beige, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = Brown)
    }
}

/**
 * What: Displays a dashed "+ Add tag" chip button.
 * Who: Called by ScrapbookScreen to let users add new tags.
 * When: Rendered after the existing tag chips in new-entry mode.
 */
@Composable
fun AddTagChip(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, Beige, RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text("+ Add tag", style = MaterialTheme.typography.bodyMedium, color = Brown)
    }
}

@Preview(showBackground = true)
@Composable
fun ScrapbookScreenPreview() {
    MemoryCircleTheme {
        ScrapbookScreen(groupId = "1", onBack = {})
    }
}
