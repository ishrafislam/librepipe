package app.librepipes

import android.app.Application
import app.librepipes.data.extractor.Extractor
import app.librepipes.di.AppContainer
import app.librepipes.notify.NotificationChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import app.librepipes.notify.UploadScheduler

/**
 * LibrePipe — a privacy-friendly, ad-free YouTube & YouTube Music client.
 * No login, no Google Play Services required.
 */
class LibrePipeApp : Application() {

    lateinit var container: AppContainer
        private set

    /**
     * For work started from a composable that is about to leave the composition — a
     * menu sheet dismissing itself would cancel `rememberCoroutineScope` mid-flight.
     */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Extractor.init(container.okHttpClient)
        NotificationChannels.create(this)
        UploadScheduler.reschedule(this, container.settings)
    }
}
