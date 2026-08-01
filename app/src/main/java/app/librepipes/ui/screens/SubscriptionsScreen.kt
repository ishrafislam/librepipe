package app.librepipes.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.librepipes.data.model.ChannelRef
import app.librepipes.ui.components.ChannelRow
import app.librepipes.ui.components.EmptyState
import app.librepipes.ui.components.GroupPickerDialog
import app.librepipes.ui.viewmodels.SubscriptionsViewModel
import app.librepipes.util.Format

@Composable
fun SubscriptionsScreen(
    vm: SubscriptionsViewModel,
    onOpenChannel: (String) -> Unit,
) {
    // These VM fields are backed by mutableStateOf — Compose tracks them directly.
    val channels = vm.channels
    val groups = vm.groups
    val selectedGroupId = vm.selectedGroupId

    var showCreateGroup by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    var showManageGroups by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<app.librepipes.data.db.GroupEntity?>(null) }
    var editName by remember { mutableStateOf("") }
    var movingChannel by remember { mutableStateOf<SubscriptionsViewModel.ChannelItem?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Group filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selectedGroupId == null,
                onClick = { vm.selectGroup(null) },
                label = { Text("All") },
            )
            groups.forEach { group ->
                FilterChip(
                    selected = selectedGroupId == group.id,
                    onClick = { vm.selectGroup(group.id) },
                    label = { Text(group.name) },
                )
            }
        }

        val visible = channels.filter { item ->
            selectedGroupId == null || selectedGroupId in item.groupIds
        }

        if (channels.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.Subscriptions,
                title = "No subscriptions",
                subtitle = "Subscribe to channels to see them here and get notified about new uploads.",
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(visible, key = { it.subscription.channelUrl }) { item ->
                    val sub = item.subscription
                    val groupNames = groups.filter { it.id in item.groupIds }.joinToString(", ") { it.name }
                    ChannelRow(
                        channel = ChannelRef(
                            id = sub.channelId,
                            name = sub.name,
                            url = sub.channelUrl,
                            avatarUrl = sub.avatarUrl,
                        ),
                        onClick = { onOpenChannel(sub.channelUrl) },
                        subtitle = groupNames.ifEmpty { "${Format.count(sub.subscriberCount)} subscribers" },
                        trailing = {
                            IconButton(onClick = { movingChannel = item }) {
                                Icon(Icons.Rounded.Add, contentDescription = "Move to group")
                            }
                        },
                    )
                }
            }
        }
    }

    FloatingActionButton(
        onClick = { showCreateGroup = true },
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(16.dp),
    ) {
        Icon(Icons.Rounded.Add, contentDescription = "New group")
    }
    }

    // ---- Create group dialog
    if (showCreateGroup) {
        AlertDialog(
            onDismissRequest = { showCreateGroup = false },
            title = { Text("New group") },
            text = {
                OutlinedTextField(
                    value = newGroupName,
                    onValueChange = { newGroupName = it },
                    singleLine = true,
                    placeholder = { Text("Group name") },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newGroupName.isNotBlank(),
                    onClick = {
                        vm.createGroup(newGroupName.trim())
                        newGroupName = ""
                        showCreateGroup = false
                    },
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateGroup = false }) { Text("Cancel") }
            },
        )
    }

    // ---- Rename / delete group dialog
    editingGroup?.let { group ->
        AlertDialog(
            onDismissRequest = { editingGroup = null },
            title = { Text("Edit group") },
            text = {
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = editName.isNotBlank(),
                    onClick = {
                        vm.renameGroup(group.id, editName.trim())
                        editingGroup = null
                    },
                ) { Text("Rename") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        vm.deleteGroup(group.id)
                        editingGroup = null
                    }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { editingGroup = null }) { Text("Cancel") }
                }
            },
        )
    }

    // ---- Manage groups dialog (long-press FAB)
    if (showManageGroups) {
        AlertDialog(
            onDismissRequest = { showManageGroups = false },
            title = { Text("Manage groups") },
            text = {
                if (groups.isEmpty()) {
                    Text("No groups yet. Tap + to create one.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    LazyColumn {
                        items(groups, key = { it.id }) { group ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.Group, contentDescription = null)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    group.name,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(vertical = 10.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                IconButton(onClick = {
                                    editName = group.name
                                    editingGroup = group
                                    showManageGroups = false
                                }) {
                                    Icon(Icons.Rounded.Edit, contentDescription = "Rename")
                                }
                                IconButton(onClick = { vm.deleteGroup(group.id) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showManageGroups = false }) { Text("Close") }
            },
        )
    }

    // ---- Move channel to group
    movingChannel?.let { item ->
        GroupPickerDialog(
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
