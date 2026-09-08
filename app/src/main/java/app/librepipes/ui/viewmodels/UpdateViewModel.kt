package app.librepipes.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.librepipes.BuildConfig
import app.librepipes.data.update.UpdateCheckResult
import app.librepipes.data.update.UpdateRelease
import app.librepipes.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UpdateUiState(
    val checking: Boolean = false,
    val downloading: Boolean = false,
    val progress: Int = 0,
    val release: UpdateRelease? = null,
    val installReadyPath: String? = null,
    val showPrompt: Boolean = false,
    val upToDate: Boolean = false,
    val debugDisabled: Boolean = false,
    val error: String? = null,
)

class UpdateViewModel(private val container: AppContainer) : ViewModel() {
    private val _uiState = MutableStateFlow(UpdateUiState(debugDisabled = BuildConfig.DEBUG))
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()
    private var activeJob: Job? = null

    fun check(manual: Boolean) {
        if (BuildConfig.DEBUG) {
            if (manual) _uiState.update { it.copy(debugDisabled = true, error = null, upToDate = false) }
            return
        }
        if (activeJob?.isActive == true || _uiState.value.downloading) return
        activeJob = viewModelScope.launch {
            val metadata = container.settings.updateMetadata()
            val now = System.currentTimeMillis()
            if (!manual && !shouldAutomaticCheck(now, metadata.lastCheckAt)) return@launch
            _uiState.update { it.copy(checking = true, error = null, upToDate = false) }
            runCatching {
                withContext(Dispatchers.IO) { container.updates.checkLatest(metadata.etag) }
            }.onSuccess { result ->
                var effectiveResult = result
                if (result == UpdateCheckResult.NotModified &&
                    UpdateRelease.fromCacheJson(metadata.cachedReleaseJson) == null
                ) {
                    effectiveResult = withContext(Dispatchers.IO) { container.updates.checkLatest(null) }
                }
                val release = when (effectiveResult) {
                    is UpdateCheckResult.Found -> effectiveResult.release
                    UpdateCheckResult.NotModified -> UpdateRelease.fromCacheJson(metadata.cachedReleaseJson)
                    UpdateCheckResult.NoRelease -> null
                }
                val etag = (effectiveResult as? UpdateCheckResult.Found)?.etag ?: metadata.etag
                container.settings.saveUpdateCheck(now, etag, release?.toCacheJson())
                val available = release?.takeIf {
                    isUpdateAvailable(BuildConfig.VERSION_CODE, it.versionCode) &&
                        it.minimumSdk <= android.os.Build.VERSION.SDK_INT
                }
                _uiState.update {
                    it.copy(
                        checking = false,
                        release = available,
                        installReadyPath = null,
                        showPrompt = available != null && (manual || metadata.dismissedVersionCode != available.versionCode),
                        upToDate = available == null,
                        error = null,
                    )
                }
            }.onFailure { failure ->
                if (failure is CancellationException) throw failure
                _uiState.update {
                    it.copy(
                        checking = false,
                        showPrompt = manual,
                        error = failure.message ?: "Could not check for updates",
                    )
                }
            }
        }
    }

    fun download() {
        val release = _uiState.value.release ?: return
        if (activeJob?.isActive == true || _uiState.value.downloading) return
        activeJob = viewModelScope.launch {
            _uiState.update { it.copy(downloading = true, progress = 0, error = null) }
            runCatching {
                withContext(Dispatchers.IO) {
                    container.updates.downloadAndVerify(release) { progress ->
                        _uiState.update { it.copy(progress = progress) }
                    }
                }
            }.onSuccess { apk ->
                _uiState.update {
                    it.copy(downloading = false, progress = 100, installReadyPath = apk.absolutePath)
                }
            }.onFailure { failure ->
                if (failure is CancellationException) throw failure
                _uiState.update {
                    it.copy(
                        downloading = false,
                        progress = 0,
                        installReadyPath = null,
                        error = failure.message ?: "Could not download update",
                    )
                }
            }
        }
    }

    fun dismiss() {
        val versionCode = _uiState.value.release?.versionCode
        _uiState.update { it.copy(showPrompt = false, error = null) }
        if (versionCode != null) viewModelScope.launch { container.settings.dismissUpdate(versionCode) }
    }

    fun showAvailableUpdate() {
        if (_uiState.value.release != null || _uiState.value.error != null) {
            _uiState.update { it.copy(showPrompt = true) }
        } else {
            check(manual = true)
        }
    }

    fun installerStarted() {
        _uiState.update { it.copy(showPrompt = false) }
    }
}

internal const val UPDATE_CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L

internal fun shouldAutomaticCheck(now: Long, lastCheckAt: Long): Boolean =
    lastCheckAt <= 0L || now - lastCheckAt >= UPDATE_CHECK_INTERVAL_MS || now < lastCheckAt

internal fun isUpdateAvailable(currentVersionCode: Int, releaseVersionCode: Int): Boolean =
    releaseVersionCode > currentVersionCode
