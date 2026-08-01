package app.librepipes

import android.app.Application
import app.librepipes.data.extractor.Extractor
import app.librepipes.di.AppContainer
import app.librepipes.notify.NotificationChannels
import app.librepipes.notify.UploadScheduler

/**
 * LibrePipe — a privacy-friendly, ad-free YouTube & YouTube Music client.
 * No login, no Google Play Services required.
 */
class LibrePipeApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Extractor.init(container.okHttpClient)
        NotificationChannels.create(this)
        UploadScheduler.reschedule(this, container.settings)
    }
}
