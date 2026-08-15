package app.librepipes.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.librepipes.LibrePipeApp
import app.librepipes.ui.LocalSessionActive
import app.librepipes.data.model.DownloadMode
import app.librepipes.data.model.StreamRef
import app.librepipes.player.QueueOps
import app.librepipes.ui.components.kit.LpSheet
import app.librepipes.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * The overflow menu behind the 3-dot on any video row or card.
 *
 * "Add to queue" appears only while something is playing — with no session there is no
 * queue to append to, and [QueueOps.addToQueue] would have nothing to talk to.
 */
@Composable
fun VideoMenuSheet(
    context: Context,
    ref: StreamRef,
    sessionActive: Boolean,
    onDismiss: () -> Unit,
) {
    val app = context.applicationContext as LibrePipeApp
    val container = app.container
    // Not rememberCoroutineScope: every item dismisses the sheet, which would cancel a
    // composition-scoped coroutine before the work ran.
    val scope = app.appScope

    LpSheet(title = ref.title.ifBlank { "Video" }, onDismiss = onDismiss) {
        MenuRow(icon = Icons.Rounded.Download, label = "Download") {
            scope.launch { container.downloadManager.enqueue(ref, DownloadMode.VIDEO) }
            Toast.makeText(context, "Download started", Toast.LENGTH_SHORT).show()
            onDismiss()
        }
        MenuRow(icon = Icons.Rounded.PlaylistAdd, label = "Save for later") {
            scope.launch { container.playlists.addToWatchLater(ref) }
            Toast.makeText(context, "Saved to Watch later", Toast.LENGTH_SHORT).show()
            onDismiss()
        }
        if (sessionActive) {
            MenuRow(icon = Icons.Rounded.QueueMusic, label = "Add to queue") {
                scope.launch {
                    val added = QueueOps.addToQueue(context, ref)
                    Toast.makeText(
                        context,
                        if (added) "Added to queue" else "Couldn't add to queue",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                onDismiss()
            }
        }
    }
}

/** Holds which row's menu is open, so screens don't each re-declare the same state. */
class VideoMenuController {
    var target by mutableStateOf<StreamRef?>(null)
        private set

    fun open(ref: StreamRef) {
        target = ref
    }

    fun dismiss() {
        target = null
    }
}

@Composable
fun rememberVideoMenuController(): VideoMenuController = remember { VideoMenuController() }

/** Renders the sheet for [controller] when a row has asked for it. */
@Composable
fun VideoMenuHost(controller: VideoMenuController) {
    val ref = controller.target ?: return
    VideoMenuSheet(
        context = LocalContext.current,
        ref = ref,
        sessionActive = LocalSessionActive.current,
        onDismiss = controller::dismiss,
    )
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.space6, vertical = Spacing.space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(Spacing.space4))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}
