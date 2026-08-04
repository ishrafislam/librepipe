package app.librepipes.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** User preferences backed by DataStore. */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME = intPreferencesKey("theme")                 // 0 system, 1 light, 2 dark
        val MAX_QUALITY = intPreferencesKey("max_quality")     // video height, 0 = best available
        val AUDIO_ONLY = booleanPreferencesKey("audio_only")
        val CAPTIONS = booleanPreferencesKey("captions")
        val RECORD_HISTORY = booleanPreferencesKey("record_history")
        val NOTIFICATIONS = booleanPreferencesKey("notifications")
        val REFRESH_INTERVAL = intPreferencesKey("refresh_interval_hours")
        val DOWNLOAD_QUALITY = intPreferencesKey("download_quality")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val AUTOPLAY = booleanPreferencesKey("autoplay")
        val VIEW_MODE = intPreferencesKey("view_mode")         // 0 list, 1 grid
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
    }

    private val dataStore = context.settingsDataStore

    val theme: Flow<Int> = dataStore.data.map { it[Keys.THEME] ?: 0 }
    val maxQuality: Flow<Int> = dataStore.data.map { it[Keys.MAX_QUALITY] ?: 1080 }
    val audioOnly: Flow<Boolean> = dataStore.data.map { it[Keys.AUDIO_ONLY] ?: false }
    val captionsEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.CAPTIONS] ?: true }
    val recordHistory: Flow<Boolean> = dataStore.data.map { it[Keys.RECORD_HISTORY] ?: true }
    val notificationsEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.NOTIFICATIONS] ?: true }
    val refreshIntervalHours: Flow<Int> = dataStore.data.map { it[Keys.REFRESH_INTERVAL] ?: 6 }
    val downloadQuality: Flow<Int> = dataStore.data.map { it[Keys.DOWNLOAD_QUALITY] ?: 1080 }
    val dynamicColor: Flow<Boolean> = dataStore.data.map { it[Keys.DYNAMIC_COLOR] ?: true }
    val autoplay: Flow<Boolean> = dataStore.data.map { it[Keys.AUTOPLAY] ?: true }
    val viewMode: Flow<Int> = dataStore.data.map { it[Keys.VIEW_MODE] ?: 1 }
    val playbackSpeed: Flow<Float> = dataStore.data.map { it[Keys.PLAYBACK_SPEED] ?: 1f }

    suspend fun setTheme(value: Int) = dataStore.edit { it[Keys.THEME] = value }
    suspend fun setMaxQuality(value: Int) = dataStore.edit { it[Keys.MAX_QUALITY] = value }
    suspend fun setAudioOnly(value: Boolean) = dataStore.edit { it[Keys.AUDIO_ONLY] = value }
    suspend fun setCaptionsEnabled(value: Boolean) = dataStore.edit { it[Keys.CAPTIONS] = value }
    suspend fun setRecordHistory(value: Boolean) = dataStore.edit { it[Keys.RECORD_HISTORY] = value }
    suspend fun setNotificationsEnabled(value: Boolean) = dataStore.edit { it[Keys.NOTIFICATIONS] = value }
    suspend fun setRefreshInterval(value: Int) = dataStore.edit { it[Keys.REFRESH_INTERVAL] = value }
    suspend fun setDownloadQuality(value: Int) = dataStore.edit { it[Keys.DOWNLOAD_QUALITY] = value }
    suspend fun setDynamicColor(value: Boolean) = dataStore.edit { it[Keys.DYNAMIC_COLOR] = value }
    suspend fun setAutoplay(value: Boolean) = dataStore.edit { it[Keys.AUTOPLAY] = value }
    suspend fun setViewMode(value: Int) = dataStore.edit { it[Keys.VIEW_MODE] = value }
    suspend fun setPlaybackSpeed(value: Float) = dataStore.edit { it[Keys.PLAYBACK_SPEED] = value }

    data class Snapshot(
        val theme: Int,
        val maxQuality: Int,
        val audioOnly: Boolean,
        val captionsEnabled: Boolean,
        val recordHistory: Boolean,
        val notificationsEnabled: Boolean,
        val refreshIntervalHours: Int,
        val downloadQuality: Int,
        val dynamicColor: Boolean,
        val autoplay: Boolean,
        val viewMode: Int,
        val playbackSpeed: Float,
    )

    suspend fun snapshot(): Snapshot {
        val prefs = dataStore.data.first()
        return Snapshot(
            theme = prefs[Keys.THEME] ?: 0,
            maxQuality = prefs[Keys.MAX_QUALITY] ?: 1080,
            audioOnly = prefs[Keys.AUDIO_ONLY] ?: false,
            captionsEnabled = prefs[Keys.CAPTIONS] ?: true,
            recordHistory = prefs[Keys.RECORD_HISTORY] ?: true,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS] ?: true,
            refreshIntervalHours = prefs[Keys.REFRESH_INTERVAL] ?: 6,
            downloadQuality = prefs[Keys.DOWNLOAD_QUALITY] ?: 1080,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
            autoplay = prefs[Keys.AUTOPLAY] ?: true,
            viewMode = prefs[Keys.VIEW_MODE] ?: 1,
            playbackSpeed = prefs[Keys.PLAYBACK_SPEED] ?: 1f,
        )
    }
}
