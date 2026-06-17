package com.cs5520group15.memorycircle.ui.group

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cs5520group15.memorycircle.R
import com.cs5520group15.memorycircle.ui.common.AvatarCircle
import com.cs5520group15.memorycircle.ui.common.MemoryCircleTopBar
import com.cs5520group15.memorycircle.ui.theme.*

/**
 * Muted warm red used for the destructive "leave group" affordances on this
 * screen — the top-bar ic_leave icon, the inline Leave-group button, and the
 * "Yes, leave" dialog confirm. Same hex as AllFriendRequestsScreen's DeleteRed
 * so destructive UI across the app reads consistently. Kept private here
 * until the brand palette formally adopts a danger color.
 */
private val DeleteRed = Color(0xFFC25B5B)

/**
 * What: A group's "settings/details" page reached from the menu icon on the
 *       timeline top bar. Three stacked sections — group name hero, members
 *       thumbnail card (with "View all members" and an invite "+" tile), and a
 *       per-month scrapbook list for this group. The top bar carries a
 *       confirmation-gated "leave group" action on the right.
 * Who: Called by MemoryCircleNavigation for the GroupDetail route.
 * When: Displayed when the user taps the menu icon on the timeline screen.
 *
 * @param groupId              the group whose details are shown
 * @param onOpenAllMembers     navigates to the flat members list
 * @param onOpenMemberProfile  navigates to a member's profile (placeholder route)
 * @param onInviteMember       opens the invite-friend flow (placeholder route)
 * @param onOpenScrapbook      opens a month's scrapbook viewer
 */
@Composable
fun GroupDetailScreen(
    groupId:              String,
    onBack:               () -> Unit,
    onOpenAllMembers:     () -> Unit,
    onOpenMemberProfile:  (String) -> Unit,
    onInviteMember:       () -> Unit,
    onOpenScrapbook:      (groupId: String, month: String, year: String) -> Unit,
    onLeaveGroup:         () -> Unit = {},
    viewModel:            GroupDetailViewModel = viewModel()
) {
    LaunchedEffect(groupId) { viewModel.bind(groupId) }

    val groupName by viewModel.groupName.collectAsStateWithLifecycle()
    val members   by viewModel.members.collectAsStateWithLifecycle()
    val months    by viewModel.months.collectAsStateWithLifecycle()

    var showLeaveDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Cream,
        topBar = {
            MemoryCircleTopBar(
                title    = "",
                showBack = true,
                onBack   = onBack,
                actions  = {
                    IconButton(onClick = { showLeaveDialog = true }) {
                        Icon(
                            painter            = painterResource(R.drawable.ic_leave),
                            contentDescription = "Leave group",
                            tint               = DeleteRed
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier            = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding      = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                GroupNameHero(groupName = groupName, memberCount = members.size)
            }

            item {
                MembersCard(
                    members             = members,
                    onMemberClick       = onOpenMemberProfile,
                    onSeeAllClick       = onOpenAllMembers,
                    onInviteClick       = onInviteMember
                )
            }

            item {
                // Secondary entry point for the same flow the top-bar ic_leave
                // action triggers — both routes feed the same showLeaveDialog
                // state so the confirmation behaviour stays single-sourced.
                LeaveGroupButton(onClick = { showLeaveDialog = true })
            }

            item {
                Text(
                    text  = "SCRAPBOOKS",
                    style = MaterialTheme.typography.labelSmall,
                    color = Ink
                )
            }

            items(months, key = { it.id }) { month ->
                MonthScrapbookRow(
                    month   = month,
                    onClick = { onOpenScrapbook(groupId, month.month, month.year) }
                )
            }
        }
    }

    if (showLeaveDialog) {
        LeaveGroupDialog(
            groupName = groupName,
            onConfirm = {
                showLeaveDialog = false
                // Remove the user from the group in Firestore, then navigate away.
                // Home's live query drops the group once memberIds no longer has the uid.
                viewModel.leaveGroup(onLeft = onLeaveGroup)
            },
            onDismiss = { showLeaveDialog = false }
        )
    }
}

/**
 * What: Large serif group name + "N members" subtitle, used as the screen's hero.
 * Who: Called by GroupDetailScreen.
 * When: Rendered once at the top of the page.
 */
@Composable
private fun GroupNameHero(groupName: String, memberCount: Int) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text  = groupName.ifBlank { "Group" },
            style = MaterialTheme.typography.headlineMedium,
            color = Ink
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text  = "$memberCount ${if (memberCount == 1) "member" else "members"}",
            style = MaterialTheme.typography.bodyMedium,
            color = InkSecondary
        )
    }
}

/**
 * What: White-card section holding the members thumbnail grid. A section header
 *       sits above the grid with a "View all members" affordance on the right;
 *       the trailing tile in the grid is an "Invite" "+".
 * Who: Called by GroupDetailScreen.
 * When: Rendered once between the hero and the scrapbook list.
 */
@Composable
private fun MembersCard(
    members:       List<Member>,
    onMemberClick: (String) -> Unit,
    onSeeAllClick: () -> Unit,
    onInviteClick: () -> Unit
) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = WhiteCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Section header: title (left) + "View all members" (right)
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text  = "MEMBERS",
                    style = MaterialTheme.typography.labelSmall,
                    color = Ink
                )
                TextButton(
                    onClick        = onSeeAllClick,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text(
                        text  = "View all ${members.size} members  ›",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Brown
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            ThumbnailGrid(
                members       = members,
                onMemberClick = onMemberClick,
                onInviteClick = onInviteClick
            )
        }
    }
}

/**
 * What: Lays out member thumbnails in a 5-column grid; the very last tile is
 *       always the "Invite" + button. Partial last rows are padded so the items
 *       stay left-aligned instead of stretching.
 * Who: Called by MembersCard.
 * When: Rendered whenever the members card is shown.
 */
@Composable
private fun ThumbnailGrid(
    members:       List<Member>,
    onMemberClick: (String) -> Unit,
    onInviteClick: () -> Unit
) {
    val columns = 5
    // null acts as the trailing "invite" slot — keeps the grid logic uniform.
    val slots: List<Member?> = members + listOf<Member?>(null)
    val rows = slots.chunked(columns)

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { slot ->
                    Box(modifier = Modifier.weight(1f)) {
                        if (slot != null) {
                            MemberThumbnail(
                                member  = slot,
                                onClick = { onMemberClick(slot.id) }
                            )
                        } else {
                            InviteThumbnail(onClick = onInviteClick)
                        }
                    }
                }
                // Pad partial last row so items don't stretch.
                repeat(columns - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * What: One member tile — avatar with optional online dot above a one-line name.
 *       Tapping the tile opens that member's profile (route TBD).
 * Who: Called by ThumbnailGrid for each member.
 * When: Rendered for every member in the grid.
 */
@Composable
private fun MemberThumbnail(
    member:  Member,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier            = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp)
    ) {
        Box {
            AvatarCircle(name = member.name, size = 48.dp)
            if (member.isOnline) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(AccentGreen)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text       = member.name,
            style      = MaterialTheme.typography.bodyMedium,
            color      = Ink,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
            textAlign  = TextAlign.Center,
            modifier   = Modifier.fillMaxWidth()
        )
    }
}

/**
 * What: Trailing tile that opens the invite-friend flow. Outlined dashed-looking
 *       circle with a "+" so it reads as an "add" affordance rather than a real
 *       avatar.
 * Who: Called by ThumbnailGrid as the last slot.
 * When: Rendered once at the end of the grid.
 */
@Composable
private fun InviteThumbnail(onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier            = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Beige.copy(alpha = 0.4f))
                .border(1.dp, Brown.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(
                painter            = painterResource(R.drawable.ic_add),
                contentDescription = "Invite member",
                tint               = Brown
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text       = "Invite",
            style      = MaterialTheme.typography.bodyMedium,
            color      = InkSecondary,
            maxLines   = 1,
            textAlign  = TextAlign.Center,
            modifier   = Modifier.fillMaxWidth()
        )
    }
}

/**
 * What: One row in the per-month scrapbook list. Visual treatment mirrors the
 *       Memories tab's cards: color chip, month/year + "N memories", chevron.
 * Who: Called by GroupDetailScreen.
 * When: Rendered for every month in this group's history.
 */
@Composable
private fun MonthScrapbookRow(
    month:   GroupDetailViewModel.MonthScrapbook,
    onClick: () -> Unit
) {
    val accent = if (month.colorType == "sage") Sage else Brown

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(WhiteCard)
            .border(1.dp, Beige.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = 0.85f))
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = "${month.month} ${month.year}",
                style = MaterialTheme.typography.titleLarge,
                color = Ink
            )
            Text(
                text  = "📷 ${month.memoryCount} memories",
                style = MaterialTheme.typography.bodyMedium,
                color = InkTertiary
            )
        }
        Text(
            text  = "›",
            style = MaterialTheme.typography.headlineMedium,
            color = Brown
        )
    }
}

/**
 * What: Full-width outlined "Leave group" button placed between the members card
 *       and the scrapbook list. Triggers the SAME confirmation dialog as the
 *       top-bar ic_leave action — the dialog itself owns the destructive flow,
 *       this button is just a more discoverable entry point lower on the page.
 * Who: Called by GroupDetailScreen.
 * When: Rendered once below the members card.
 */
@Composable
private fun LeaveGroupButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick  = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(20.dp),
        border   = BorderStroke(1.dp, DeleteRed.copy(alpha = 0.6f))
    ) {
        Icon(
            painter            = painterResource(R.drawable.ic_leave),
            contentDescription = null,
            tint               = DeleteRed
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text  = "Leave group",
            style = MaterialTheme.typography.labelLarge,
            color = DeleteRed
        )
    }
}

/**
 * What: Confirmation dialog shown before the user actually leaves a group. The
 *       user must tap "Yes, leave" to commit; "Cancel" or tapping outside dismisses.
 * Who: Called by GroupDetailScreen when the top-bar leave icon or the in-body
 *       Leave-group button is tapped.
 * When: While showLeaveDialog is true.
 */
@Composable
private fun LeaveGroupDialog(
    groupName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Cream,
        shape            = RoundedCornerShape(20.dp),
        title = {
            Text(
                text  = "Leave group?",
                style = MaterialTheme.typography.titleLarge,
                color = Ink
            )
        },
        text = {
            Text(
                text  = "You'll stop receiving new memories from " +
                        "${groupName.ifBlank { "this group" }}. " +
                        "Your past photos and comments will stay.",
                style = MaterialTheme.typography.bodyMedium,
                color = InkSecondary
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text  = "Yes, leave",
                    style = MaterialTheme.typography.labelLarge,
                    color = Brown
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text  = "Cancel",
                    style = MaterialTheme.typography.labelLarge,
                    color = AccentGreen
                )
            }
        }
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFF8F4EE)
@Composable
fun GroupDetailScreenPreview() {
    MemoryCircleTheme {
        GroupDetailScreen(
            groupId             = "1",
            onBack              = {},
            onOpenAllMembers    = {},
            onOpenMemberProfile = {},
            onInviteMember      = {},
            onOpenScrapbook     = { _, _, _ -> }
        )
    }
}
