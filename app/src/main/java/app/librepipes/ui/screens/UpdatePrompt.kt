package app.librepipes.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.librepipes.ui.components.kit.LpFilledButton
import app.librepipes.ui.components.kit.LpSheet
import app.librepipes.ui.components.kit.LpTextButton
import app.librepipes.ui.viewmodels.UpdateViewModel

@Composable
fun UpdatePrompt(
    vm: UpdateViewModel,
    onInstall: (String) -> Unit,
) {
    val state by vm.uiState.collectAsState()
    if (!state.showPrompt) return

    val release = state.release
    LpSheet(
        title = if (release == null) "Update check failed" else "Update available",
        onDismiss = vm::dismiss,
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            if (release != null) {
                Text(
                    text = "LibrePipe ${release.versionName}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (release.releaseNotes.isNotBlank()) {
                    Text(
                        text = release.releaseNotes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }

            state.error?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            if (state.downloading) {
                LinearProgressIndicator(
                    progress = { state.progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                )
                Text(
                    text = "Downloading ${state.progress}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                LpTextButton(text = "Later", onClick = vm::dismiss)
                when {
                    release == null -> LpFilledButton(text = "Retry", onClick = { vm.check(manual = true) })
                    state.installReadyPath != null -> LpFilledButton(
                        text = "Install",
                        leadingIcon = Icons.Rounded.InstallMobile,
                        onClick = {
                            onInstall(state.installReadyPath!!)
                            vm.installerStarted()
                        },
                    )
                    else -> LpFilledButton(
                        text = if (state.downloading) "Downloading…" else "Download",
                        leadingIcon = Icons.Rounded.Download,
                        enabled = !state.downloading,
                        onClick = vm::download,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
