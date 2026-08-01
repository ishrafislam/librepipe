package app.librepipes.di

import android.content.Context
import app.librepipes.data.db.AppDatabase
import app.librepipes.data.prefs.SettingsRepository
import app.librepipes.data.repo.DownloadRepository
import app.librepipes.data.repo.GroupRepository
import app.librepipes.data.repo.HistoryRepository
import app.librepipes.data.repo.PlaylistRepository
import app.librepipes.data.repo.SubscriptionRepository
import app.librepipes.download.DownloadManager
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Hand-rolled dependency container (keeps the app light — no DI framework).
 */
class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    val database: AppDatabase by lazy {
        AppDatabase.build(appContext)
    }

    val settings: SettingsRepository by lazy {
        SettingsRepository(appContext)
    }

    val subscriptions: SubscriptionRepository by lazy {
        SubscriptionRepository(database.subscriptionDao())
    }

    val groups: GroupRepository by lazy {
        GroupRepository(database.groupDao(), database.groupChannelDao(), database.subscriptionDao())
    }

    val history: HistoryRepository by lazy {
        HistoryRepository(database.historyDao())
    }

    val playlists: PlaylistRepository by lazy {
        PlaylistRepository(database.playlistDao(), database.playlistItemDao())
    }

    val downloads: DownloadRepository by lazy {
        DownloadRepository(database.downloadDao())
    }

    val downloadManager: DownloadManager by lazy {
        DownloadManager(appContext, downloads, settings)
    }
}
