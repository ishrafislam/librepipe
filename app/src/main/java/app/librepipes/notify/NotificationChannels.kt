package app.librepipes.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import app.librepipes.R

object NotificationChannels {
    const val UPLOADS = "uploads"
    const val DOWNLOADS = "downloads"
    const val POPUP = "popup"
    const val PREMIERE = "premiere"

    fun create(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    UPLOADS,
                    context.getString(R.string.channel_uploads_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = context.getString(R.string.channel_uploads_desc)
                },
                NotificationChannel(
                    DOWNLOADS,
                    context.getString(R.string.channel_downloads_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = context.getString(R.string.channel_downloads_desc)
                },
                NotificationChannel(
                    POPUP,
                    "Popup player",
                    NotificationManager.IMPORTANCE_LOW,
                ),
                NotificationChannel(
                    PREMIERE,
                    context.getString(R.string.channel_premiere_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = context.getString(R.string.channel_premiere_desc)
                },
            )
        )
    }
}
