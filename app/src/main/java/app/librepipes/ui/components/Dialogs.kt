package app.librepipes.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.librepipes.LibrePipeApp
import app.librepipes.data.model.StreamRef
import kotlinx.coroutines.launch

@Composable
fun AddToPlaylistDialog(
    context: Context,
    ref: StreamRef,
    onDismiss: () -> Unit,
) {
    val app = context.applicationContext as LibrePipeApp
    val playlists = app.container.playlists
    val scope = rememberCoroutineScope()
    var playlistsList by remember { mutableStateOf<List<app.librepipes.data.db.LocalPlaylistEntity>>(emptyList()) }
    var newName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        playlists.observePlaylists().collect { playlistsList = it }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        text = {
            Column(modifier = Modifier.height(320.dp)) {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(playlistsList) { playlist ->
                        TextButton(
                            onClick = {
                                scope.launch {
                                    playlists.addItem(playlist.id, ref)
                                    Toast.makeText(context, "Added to ${playlist.name}", Toast.LENGTH_SHORT).show()
                                }
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(playlist.name)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("New playlist") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = newName.isNotBlank(),
                        onClick = {
                            scope.launch {
                                val id = playlists.create(newName.trim())
                                playlists.addItem(id, ref)
                                Toast.makeText(context, "Created ${newName.trim()}", Toast.LENGTH_SHORT).show()
                            }
                            onDismiss()
                        },
                    ) {
                        Text("Create")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun GroupPickerDialog(
    groups: List<app.librepipes.data.db.GroupEntity>,
    currentGroupId: Long?,
    channelName: String,
    onSelect: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(currentGroupId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move \"$channelName\" to group") },
        text = {
            LazyColumn {
                item {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(selected = selected == null, onClick = { selected = null })
                        Spacer(Modifier.width(8.dp))
                        Text("No group")
                    }
                }
                items(groups) { group ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(selected = selected == group.id, onClick = { selected = group.id })
                        Spacer(Modifier.width(8.dp))
                        Text(group.name)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSelect(selected)
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
