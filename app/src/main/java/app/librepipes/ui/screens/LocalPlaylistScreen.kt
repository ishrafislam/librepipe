package app.librepipes.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.librepipes.data.model.StreamRef
import app.librepipes.ui.components.EmptyState
import app.librepipes.ui.components.VideoRow
import app.librepipes.ui.viewmodels.LocalPlaylistViewModel

@Composable
fun LocalPlaylistScreen(
    vm: LocalPlaylistViewModel,
    onBack: () -> Unit,
    onOpenVideo: (StreamRef, List<StreamRef>) -> Unit,
) {
    // These VM fields are backed by mutableStateOf — Compose tracks them directly.
    val name = vm.name
    val items = vm.items
    var showRename by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf(name) }
    var showDelete by remember { mutableStateOf(false) }

    val refs = items.mapNotNull { StreamRef.fromJson(it.streamJson) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "${items.size} videos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = {
                newName = name
                showRename = true
            }) {
                Icon(Icons.Rounded.Edit, contentDescription = "Rename")
            }
            IconButton(onClick = { showDelete = true }) {
                Icon(Icons.Rounded.Delete, contentDescription = "Delete playlist")
            }
        }

        if (refs.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.PlayArrow,
                title = "Empty playlist",
                subtitle = "Add videos from their context menu.",
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Button(
                        onClick = { onOpenVideo(refs.first(), refs) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Play all")
                    }
                }
                items(items, key = { it.id }) { item ->
                    val ref = StreamRef.fromJson(item.streamJson)
                    if (ref != null) {
                        VideoRow(
                            ref = ref,
                            index = items.indexOf(item),
                            onClick = { onOpenVideo(ref, refs.drop(items.indexOf(item))) },
                            trailing = {
                                IconButton(onClick = { vm.removeItem(item.id) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Remove")
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Rename playlist") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        vm.rename(newName.trim())
                        showRename = false
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text("Cancel") }
            },
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete playlist?") },
            text = { Text("\"$name\" and its items will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete()
                    onBack()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("Cancel") }
            },
        )
    }
}
