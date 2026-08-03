package app.librepipes.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.librepipes.data.model.StreamRef
import app.librepipes.ui.components.EmptyState
import app.librepipes.ui.components.kit.LpDialog
import app.librepipes.ui.components.kit.LpIconAction
import app.librepipes.ui.components.kit.LpOutlinedTextField
import app.librepipes.ui.components.kit.LpTopBar
import app.librepipes.ui.components.kit.LpVideoRow
import app.librepipes.ui.viewmodels.LocalPlaylistViewModel

@Composable
fun LocalPlaylistScreen(
    vm: LocalPlaylistViewModel,
    onBack: () -> Unit,
    onOpenVideo: (StreamRef, List<StreamRef>) -> Unit,
) {
    val name = vm.name
    val items = vm.items
    var showRename by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf(name) }
    var showDelete by remember { mutableStateOf(false) }

    val refs = items.mapNotNull { StreamRef.fromJson(it.streamJson) }

    Column(modifier = Modifier.fillMaxSize()) {
        LpTopBar(
            title = name,
            onNavigationClick = onBack,
            navigationIcon = Icons.Rounded.ArrowBack,
            actions = listOf(
                LpIconAction(
                    icon = Icons.Rounded.Edit,
                    contentDescription = "Rename",
                    onClick = {
                        newName = name
                        showRename = true
                    },
                ),
                LpIconAction(
                    icon = Icons.Rounded.Delete,
                    contentDescription = "Delete playlist",
                    onClick = { showDelete = true },
                ),
            ),
        )

        if (refs.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.PlayArrow,
                title = "Empty playlist",
                subtitle = "Add videos from their context menu.",
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Text(
                        text = if (items.size == 1) "1 video" else "${items.size} videos",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                item {
                    TextButton(
                        onClick = { onOpenVideo(refs.first(), refs) },
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Play all", modifier = Modifier.padding(start = 4.dp))
                    }
                }
                items(items, key = { it.id }) { item ->
                    val ref = StreamRef.fromJson(item.streamJson)
                    if (ref != null) {
                        val index = items.indexOf(item)
                        LpVideoRow(
                            ref = ref,
                            onClick = { onOpenVideo(ref, refs.drop(index)) },
                            onLongPress = { vm.removeItem(item.id) },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }

    if (showRename) {
        LpDialog(
            title = "Rename playlist",
            text = null,
            confirmLabel = "Save",
            confirmEnabled = newName.isNotBlank(),
            onConfirm = {
                vm.rename(newName.trim())
                showRename = false
            },
            onDismiss = { showRename = false },
        ) {
            LpOutlinedTextField(
                value = newName,
                onValueChange = { if (it.length <= 60) newName = it },
                label = "Name",
            )
        }
    }

    if (showDelete) {
        LpDialog(
            title = "Delete playlist?",
            text = "\"$name\" and its videos will be removed from Librepipe.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                vm.delete()
                onBack()
            },
            onDismiss = { showDelete = false },
        )
    }
}
