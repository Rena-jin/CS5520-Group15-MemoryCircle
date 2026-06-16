package com.cs5520group15.memorycircle.ui.scrapbook

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.cs5520group15.memorycircle.R
import com.cs5520group15.memorycircle.ui.common.AvatarCircle
import com.cs5520group15.memorycircle.ui.common.MemoryCircleTopBar
import com.cs5520group15.memorycircle.ui.theme.*

/**
 * What: Displays one group's collaborative timeline. Each time point sits on a
 *       continuous left-hand line marked by a Brown dot and its date, with a card
 *       on the right showing every member's photo + avatar + name + their own
 *       description, an editable title, group comments, and an "Add my photo" CTA.
 *       A FAB adds a brand-new time point.
 * Who: Called by MemoryCircleNavigation for the ScrapbookViewer route.
 * When: Opened from a Home group card or the Memories tab.
 */
@Composable
fun ScrapbookViewerScreen(
    groupId:           String,
    onBack:            () -> Unit,
    onOpenGroupDetail: () -> Unit,
    onAddTimePoint:    () -> Unit,
    onJoinEntry:       (String) -> Unit,
    viewModel:         ScrapbookViewerViewModel = viewModel()
) {
    LaunchedEffect(groupId) { viewModel.bind(groupId) }

    // Collect the shared repository flow directly so created/joined contributions
    // and posted comments show up immediately.
    val entriesFlow = remember(groupId) { ScrapbookRepository.entriesFor(groupId) }
    val entries by entriesFlow.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Cream,
        topBar = {
            MemoryCircleTopBar(
                title    = "June 2025",
                showBack = true,
                onBack   = onBack,
                actions  = {
                    IconButton(onClick = onOpenGroupDetail) {
                        Icon(
                            painter            = painterResource(R.drawable.ic_menu),
                            contentDescription = "Open group details"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick        = onAddTimePoint,
                containerColor = Ink,
                contentColor   = Cream
            ) {
                Text("+", style = MaterialTheme.typography.headlineSmall)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier       = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
        ) {
            // No verticalArrangement spacing here — each row carries its own
            // bottom padding so the timeline line stays continuous between entries.
            items(entries, key = { it.id }) { entry ->
                TimelineEntry(
                    entry         = entry,
                    onSaveTitle   = { title -> viewModel.updateEntryTitle(entry.id, title) },
                    onPostComment = { text -> viewModel.addComment(entry.id, author = "", text = text) },
                    onJoin        = { onJoinEntry(entry.id) }
                )
            }
        }
    }
}

/**
 * What: One timeline row — a continuous vertical line + dot + date on the left,
 *       and the memory card on the right.
 * Who: Called by ScrapbookViewerScreen for each entry.
 * When: Rendered for every time point in the month.
 */
@Composable
private fun TimelineEntry(
    entry:         ScrapbookEntry,
    onSaveTitle:   (String) -> Unit,
    onPostComment: (String) -> Unit,
    onJoin:        () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {

        // --- Background: continuous vertical line in the 72dp left gutter ---
        Box(modifier = Modifier.matchParentSize()) {
            Box(
                modifier = Modifier
                    .width(72.dp)
                    .fillMaxHeight(),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(Brown)
                )
            }
        }

        // --- Foreground: left gutter (dot + date) + memory card ---
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier            = Modifier
                    .width(72.dp)
                    .align(Alignment.CenterVertically)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Brown)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text      = entry.date,
                    style     = MaterialTheme.typography.labelSmall,
                    color     = Brown,
                    textAlign = TextAlign.Center
                )
            }

            MemoryCard(
                entry         = entry,
                onSaveTitle   = onSaveTitle,
                onPostComment = onPostComment,
                onJoin        = onJoin,
                modifier      = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, bottom = 20.dp)
            )
        }
    }
}

/**
 * What: The card for a single time point — an editable title, each member's
 *       contribution (photo + avatar + name + their own description), an
 *       "Add my photo" CTA, and the group comment thread.
 * Who: Called by TimelineEntry.
 * When: Rendered for every time point.
 */
@Composable
private fun MemoryCard(
    entry:         ScrapbookEntry,
    onSaveTitle:   (String) -> Unit,
    onPostComment: (String) -> Unit,
    onJoin:        () -> Unit,
    modifier:      Modifier = Modifier
) {
    var isEditing    by remember(entry.id) { mutableStateOf(false) }
    var titleInput   by remember(entry.id) { mutableStateOf(entry.title) }
    var commentInput by remember(entry.id) { mutableStateOf("") }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor   = Sage,
        unfocusedBorderColor = Beige
    )

    Card(
        modifier  = modifier,
        shape     = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors    = CardDefaults.cardColors(containerColor = Cream)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            // Header: member count + Edit/Done toggle for the title
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text  = "👥 ${entry.contributions.size} members",
                    style = MaterialTheme.typography.labelSmall,
                    color = Brown
                )
                TextButton(
                    onClick = {
                        if (isEditing) {
                            onSaveTitle(titleInput)
                            isEditing = false
                        } else {
                            titleInput = entry.title
                            isEditing  = true
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text(
                        text  = if (isEditing) "✓ Done" else "✏️ Edit",
                        style = MaterialTheme.typography.labelMedium,
                        color = AccentGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Title: read-only, or editable in edit mode
            if (isEditing) {
                OutlinedTextField(
                    value         = titleInput,
                    onValueChange = { titleInput = it },
                    modifier      = Modifier.fillMaxWidth(),
                    label         = { Text("Title") },
                    singleLine    = true,
                    shape         = RoundedCornerShape(12.dp),
                    colors        = fieldColors
                )
            } else {
                Text(
                    text  = entry.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Ink
                )
            }

            // Tags
            if (entry.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text  = entry.tags.joinToString("  "),
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentGreen
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Each member's contribution: photo + avatar + name + their own description
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                entry.contributions.forEach { contribution ->
                    ContributionBlock(contribution)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // CTA: join this time point with your own photo + description
            OutlinedButton(
                onClick  = onJoin,
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(20.dp)
            ) {
                Text("➕  Add my photo", color = AccentGreen)
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Beige.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(8.dp))

            // --- Comments (any group member can share their mood) ---
            Text(
                text  = "COMMENTS",
                style = MaterialTheme.typography.labelSmall,
                color = InkSecondary
            )
            Spacer(modifier = Modifier.height(6.dp))

            if (entry.comments.isEmpty()) {
                Text(
                    text  = "No comments yet — be the first to share how you felt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkTertiary
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    entry.comments.forEach { comment -> CommentRow(comment) }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Comment input
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value         = commentInput,
                    onValueChange = { commentInput = it },
                    modifier      = Modifier.weight(1f),
                    placeholder   = { Text("Share your mood…", style = MaterialTheme.typography.bodyMedium) },
                    singleLine    = true,
                    shape         = RoundedCornerShape(20.dp),
                    colors        = fieldColors
                )
                TextButton(onClick = {
                    onPostComment(commentInput)
                    commentInput = ""
                }) {
                    Text("Post", color = AccentGreen)
                }
            }
        }
    }
}

/**
 * What: One member's contribution — their photo above a row of their avatar, name,
 *       and their own description.
 * Who: Called by MemoryCard for each contribution on a time point.
 * When: Rendered for every member who has joined the time point.
 */
@Composable
private fun ContributionBlock(contribution: MemberContribution) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AsyncImage(
            model              = contribution.photoUri,
            contentDescription = "${contribution.memberName}'s photo",
            contentScale       = ContentScale.Crop,
            modifier           = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Row(verticalAlignment = Alignment.Top) {
            AvatarCircle(name = contribution.memberName, size = 28.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text  = contribution.memberName,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Brown
                )
                if (contribution.description.isNotBlank()) {
                    Text(
                        text  = contribution.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ink
                    )
                }
            }
        }
    }
}

/**
 * What: A single comment line — author in bold followed by their text.
 * Who: Called by MemoryCard for each comment.
 * When: Rendered for every comment on an entry.
 */
@Composable
private fun CommentRow(comment: Comment) {
    Row {
        Text(
            text  = comment.author,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = Brown
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text  = comment.text,
            style = MaterialTheme.typography.bodySmall,
            color = Ink
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ScrapbookViewerScreenPreview() {
    MemoryCircleTheme {
        ScrapbookViewerScreen(
            groupId           = "test",
            onBack            = {},
            onOpenGroupDetail = {},
            onAddTimePoint    = {},
            onJoinEntry       = {}
        )
    }
}
