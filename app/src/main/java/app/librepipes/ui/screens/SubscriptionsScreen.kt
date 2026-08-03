package app.librepipes.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.librepipes.data.model.ChannelRef
import app.librepipes.ui.components.EmptyState
import app.librepipes.ui.components.kit.LpChannelRow
import app.librepipes.ui.components.kit.LpDialog
import app.librepipes.ui.components.kit.LpFilterChip
import app.librepipes.ui.components.kit.LpIconAction
import app.librepipes.ui.components.kit.LpOutlinedTextField
import app.librepipes.ui.components.kit.LpSheet
import app.librepipes.ui.components.kit.LpSkeletonBox
import app.librepipes.ui.components.kit.LpTopBar
import app.librepipes.ui.theme.ShapeTokens
import app.librepipes.ui.viewmodels.SubscriptionsViewModel
import app.librepipes.util.Format
import coil3.compose.AsyncImage

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SubscriptionsScreen(
    vm: SubscriptionsViewModel,
    onOpenChannel: (String) -> Unit,
    onOpenSearch: () -> Unit,
) {
    val channels = vm.channels
    val groups = vm.groups
    val selectedGroupId = vm.selectedGroupId
    val uploads = vm.uploads
    val uploadsLoading = vm.uploadsLoading

    var gridMode by rememberSaveable { mutableStateOf(true) }
    var showCreateGroup by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    var editingGroup by remember { mutableStateOf<app.librepipes.data.db.GroupEntity?>(null) }
    var editName by remember { mutableStateOf("") }
    var showManageGroups by remember { mutableStateOf(false) }
    var movingChannel by remember { mutableStateOf<SubscriptionsViewModel.ChannelItem?>(null) }

    val visible = channels.filter { item ->
        selectedGroupId == null || selectedGroupId in item.groupIds
    }
    val openChannel = { url: String ->
        vm.markChannelSeen(url)
        onOpenChannel(url)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LpTopBar(
            title = "Subscriptions",
            actions = listOf(
                LpIconAction(Icons.Rounded.Search, "Search", onClick = onOpenSearch),
                LpIconAction(
                    icon = if (gridMode) Icons.Rounded.ViewList else Icons.Rounded.GridView,
                    contentDescription = if (gridMode) "List view" else "Grid view",
                    onClick = { gridMode = !gridMode },
                ),
            ),
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                LpFilterChip(
                    text = "All",
                    selected = selectedGroupId == null,
                    onClick = { vm.selectGroup(null) },
                )
            }
            items(groups, key = { it.id }) { group ->
                LpFilterChip(
                    text = group.name,
                    selected = selectedGroupId == group.id,
                    onClick = { vm.selectGroup(group.id) },
                )
            }
        }

        when {
            channels.isEmpty() -> EmptyState(
                icon = Icons.Rounded.Subscriptions,
                title = "No subscriptions",
                subtitle = "Subscribe to channels to see their latest uploads here.",
            )

            uploadsLoading && uploads.isEmpty() -> UploadsSkeleton()

            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (visible.isNotEmpty() && uploads.isNotEmpty()) {
                    item(key = "uploads_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 8.dp, top = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Latest uploads",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            if (uploads.any { it.isNew }) {
                                TextButton(onClick = vm::markAllSeen) {
                                    Text("Mark all seen")
                                }
                            }
                        }
                    }
                    items(uploads, key = { it.ref.id }) { item ->
                        UploadRow(
                            item = item,
                            onClick = { onOpenChannel(item.channel.channelUrl) },
                        )
                    }
                }

                item(key = "channels_header") {
                    Text(
                        text = if (gridMode) "Channels (${visible.size})" else "All channels",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
                    )
                }

                if (gridMode) {
                    visible.chunked(3).forEachIndexed { chunkIndex, chunk ->
                        item(key = "chunk_$chunkIndex") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                            ) {
                                chunk.forEach { item ->
                                    ChannelCell(
                                        item = item,
                                        modifier = Modifier.weight(1f),
                                        onClick = { openChannel(item.subscription.channelUrl) },
                                        onLongClick = { movingChannel = item },
                                    )
                                }
                                repeat(3 - chunk.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                } else {
                    items(visible, key = { it.subscription.channelUrl }) { item ->
                        val sub = item.subscription
                        val groupNames = groups.filter { it.id in item.groupIds }.joinToString(", ") { it.name }
                        LpChannelRow(
                            channel = ChannelRef(
                                id = sub.channelId,
                                name = sub.name,
                                url = sub.channelUrl,
                                avatarUrl = sub.avatarUrl,
                            ),
                            onClick = { openChannel(sub.channelUrl) },
                            subtitle = groupNames.ifEmpty { "${Format.count(sub.subscriberCount)} subscribers" },
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                }
            }
        }
    }

    FloatingActionButtonRow(
        onNewGroup = { showCreateGroup = true },
        onManageGroups = { showManageGroups = true },
    )

    if (showCreateGroup) {
        LpDialog(
            title = "New group",
            text = null,
            confirmLabel = "Create",
            confirmEnabled = newGroupName.isNotBlank(),
            onConfirm = {
                vm.createGroup(newGroupName.trim())
                newGroupName = ""
                showCreateGroup = false
            },
            onDismiss = { showCreateGroup = false },
        ) {
            LpOutlinedTextField(
                value = newGroupName,
                onValueChange = { if (it.length <= 30) newGroupName = it },
                label = "Group name",
                placeholder = "e.g. Music",
            )
        }
    }

    editingGroup?.let { group ->
        LpDialog(
            title = "Edit group",
            text = null,
            confirmLabel = "Rename",
            confirmEnabled = editName.isNotBlank(),
            onConfirm = {
                vm.renameGroup(group.id, editName.trim())
                editingGroup = null
            },
            onDismiss = { editingGroup = null },
            content = {
                Column {
                    LpOutlinedTextField(
                        value = editName,
                        onValueChange = { if (it.length <= 30) editName = it },
                        label = "Group name",
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            vm.deleteGroup(group.id)
                            editingGroup = null
                        },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text("Delete group", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
        )
    }

    if (showManageGroups) {
        LpSheet(
            title = "Manage groups",
            onDismiss = { showManageGroups = false },
        ) {
            if (groups.isEmpty()) {
                Text(
                    text = "No groups yet. Tap + to create one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
            } else {
                groups.forEach { group ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = group.name,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = "Rename",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(48.dp)
                                .padding(12.dp)
                                .clickable {
                                    editName = group.name
                                    editingGroup = group
                                    showManageGroups = false
                                },
                        )
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .size(48.dp)
                                .padding(12.dp)
                                .clickable { vm.deleteGroup(group.id) },
                        )
                    }
                }
            }
        }
    }

    movingChannel?.let { item ->
        GroupPickerSheet(
            groups = groups,
            currentGroupId = item.groupIds.firstOrNull(),
            channelName = item.subscription.name,
            onSelect = { groupId ->
                vm.assignChannel(item.subscription.channelUrl, groupId)
            },
            onDismiss = { movingChannel = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelCell(
    item: SubscriptionsViewModel.ChannelItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val sub = item.subscription
    val unread = sub.lastCheckedAt > sub.lastVisitedAt
    Column(
        modifier = modifier
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(colors.surfaceContainerHigh)
                .then(
                    if (unread) Modifier.border(3.dp, colors.primary, CircleShape) else Modifier
                ),
        ) {
            AsyncImage(
                model = sub.avatarUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        }
        Text(
            text = sub.name,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp, start = 6.dp, end = 6.dp),
        )
    }
}

@Composable
private fun UploadRow(
    item: SubscriptionsViewModel.UploadItem,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = item.ref.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .width(96.dp)
                .height(54.dp)
                .clip(ShapeTokens.md),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.ref.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (item.isNew) {
                    Text(
                        text = "NEW",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onPrimaryContainer,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clip(ShapeTokens.xs)
                            .background(colors.primaryContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                text = item.channel.name,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun FloatingActionButtonRow(onNewGroup: () -> Unit, onManageGroups: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.material3.FloatingActionButton(
            onClick = onNewGroup,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "New group")
        }
    }
}

@Composable
private fun GroupPickerSheet(
    groups: List<app.librepipes.data.db.GroupEntity>,
    currentGroupId: Long?,
    channelName: String,
    onSelect: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    LpSheet(
        title = "Move $channelName to group",
        onDismiss = onDismiss,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clickable {
                    onSelect(null)
                    onDismiss()
                }
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "No group",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            if (currentGroupId == null) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        groups.forEach { group ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable {
                        onSelect(group.id)
                        onDismiss()
                    }
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                if (currentGroupId == group.id) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun UploadsSkeleton() {
    Column(modifier = Modifier.fillMaxSize().padding(top = 12.dp)) {
        LpSkeletonBox(shape = ShapeTokens.xs, modifier = Modifier.padding(start = 16.dp).size(width = 130.dp, height = 20.dp))
        repeat(4) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LpSkeletonBox(modifier = Modifier.size(width = 96.dp, height = 54.dp))
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LpSkeletonBox(shape = ShapeTokens.xs, modifier = Modifier.size(width = 180.dp, height = 12.dp))
                    LpSkeletonBox(shape = ShapeTokens.xs, modifier = Modifier.size(width = 100.dp, height = 10.dp))
                }
            }
        }
    }
}
