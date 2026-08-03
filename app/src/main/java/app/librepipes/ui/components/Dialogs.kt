package app.librepipes.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.librepipes.LibrePipeApp
import app.librepipes.data.model.StreamRef
import app.librepipes.ui.components.kit.LpFilledButton
import app.librepipes.ui.components.kit.LpOutlinedTextField
import app.librepipes.ui.components.kit.LpSheet
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

    LpSheet(
        title = "Add to playlist",
        onDismiss = onDismiss,
    ) {
        if (playlistsList.isEmpty()) {
            Text(
                text = "No playlists yet — create one below.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        } else {
            playlistsList.forEach { playlist ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clickable {
                            scope.launch {
                                playlists.addItem(playlist.id, ref)
                                Toast.makeText(context, "Added to ${playlist.name}", Toast.LENGTH_SHORT).show()
                            }
                            onDismiss()
                        }
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.PlaylistAdd,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
            LpOutlinedTextField(
                value = newName,
                onValueChange = { if (it.length <= 60) newName = it },
                label = "New playlist",
                placeholder = "Name",
            )
            Spacer(Modifier.height(8.dp))
            LpFilledButton(
                text = "Create",
                enabled = newName.isNotBlank(),
                onClick = {
                    scope.launch {
                        val id = playlists.create(newName.trim())
                        playlists.addItem(id, ref)
                        Toast.makeText(context, "Created ${newName.trim()}", Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}