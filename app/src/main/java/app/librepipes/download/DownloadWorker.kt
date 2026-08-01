package app.librepipes.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.librepipes.LibrePipeApp
import app.librepipes.data.extractor.Extractor
import app.librepipes.data.model.DownloadMode
import app.librepipes.data.model.DownloadState
import app.librepipes.data.model.StreamRef
import app.librepipes.data.repo.DownloadRepository
import app.librepipes.data.youtube.StreamFormat
import app.librepipes.data.youtube.StreamInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.util.Locale

class DownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_MODE = "mode"
        const val KEY_REF = "ref"

        private const val NOTIFICATION_ID_BASE = 4000
    }

    private val repository: DownloadRepository
        get() = (applicationContext as LibrePipeApp).container.downloads

    override suspend fun doWork(): Result {
        val downloadId = inputData.getLong(KEY_DOWNLOAD_ID, -1)
        val mode = runCatching { DownloadMode.valueOf(inputData.getString(KEY_MODE) ?: "VIDEO") }
            .getOrDefault(DownloadMode.VIDEO)
        val ref = StreamRef.fromJson(inputData.getString(KEY_REF)) ?: return Result.failure()
        if (downloadId < 0) return Result.failure()

        val notificationManager = androidx.core.app.NotificationManagerCompat.from(applicationContext)
        val channelId = "downloads"
        notificationManager.notify(
            NOTIFICATION_ID_BASE + downloadId.toInt(),
            buildProgressNotification(ref.title, 0, channelId),
        )

        return try {
            val streamInfo = Extractor.stream(ref.url)
            if (isStopped) {
                repository.updateState(downloadId, DownloadState.CANCELLED)
                return Result.failure()
            }
            repository.update(downloadId, DownloadState.RUNNING, 0)

            val result = withContext(Dispatchers.IO) {
                download(ref, streamInfo, mode, downloadId, channelId)
            }

            when (result) {
                is DownloadResult.Success -> {
                    repository.update(
                        downloadId,
                        DownloadState.DONE,
                        100,
                        fileUri = result.uri.toString(),
                        totalBytes = result.size,
                    )
                    Result.success()
                }
                is DownloadResult.Cancelled -> {
                    repository.updateState(downloadId, DownloadState.CANCELLED)
                    Result.failure()
                }
                is DownloadResult.Error -> {
                    repository.update(downloadId, DownloadState.ERROR, 0, error = result.message)
                    Result.failure()
                }
            }
        } catch (e: Exception) {
            if (isStopped) {
                repository.updateState(downloadId, DownloadState.CANCELLED)
            } else {
                repository.update(downloadId, DownloadState.ERROR, 0, error = e.message)
            }
            Result.failure()
        }
    }

    private sealed interface DownloadResult {
        data class Success(val uri: Uri, val size: Long) : DownloadResult
        data class Cancelled(val reason: String) : DownloadResult
        data class Error(val message: String) : DownloadResult
    }

    private suspend fun download(
        ref: StreamRef,
        info: StreamInfo,
        mode: DownloadMode,
        downloadId: Long,
        channelId: String,
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            val maxHeight = (applicationContext as LibrePipeApp).container.settings.snapshot().downloadQuality
            val (primaryStream, secondaryStream, container) = selectStreams(info, mode, maxHeight)
                ?: return@withContext DownloadResult.Error("No suitable stream found")

            val primaryName = streamFileName(ref, primaryStream, container)

            if (secondaryStream == null) {
                // Single stream: stream straight to MediaStore / public downloads
                val uri = writeToStorage(info, primaryStream, primaryName, downloadId, channelId)
                    ?: return@withContext DownloadResult.Error("Could not create output file")
                return@withContext DownloadResult.Success(uri, 0L)
            }

            // Two streams: download to cache, mux, then copy out
            val cacheDir = File(applicationContext.cacheDir, "downloads").apply { mkdirs() }
            val videoFile = File(cacheDir, "${ref.id}.video.tmp")
            val audioFile = File(cacheDir, "${ref.id}.audio.tmp")
            when (val videoResult = fetchToFile(info, primaryStream, videoFile, downloadId, channelId, null)) {
                is DownloadResult.Cancelled -> return@withContext DownloadResult.Cancelled("cancelled")
                is DownloadResult.Error -> return@withContext DownloadResult.Error(videoResult.message)
                else -> {}
            }
            when (val audioResult = fetchToFile(info, secondaryStream, audioFile, downloadId, channelId, 50)) {
                is DownloadResult.Cancelled -> return@withContext DownloadResult.Cancelled("cancelled")
                is DownloadResult.Error -> return@withContext DownloadResult.Error(audioResult.message)
                else -> {}
            }

            val muxed = File(cacheDir, "${ref.id}.muxed.tmp")
            val ok = MediaMuxerKit.mux(videoFile, audioFile, muxed)
            if (!ok) return@withContext DownloadResult.Error("Could not merge audio and video")

            reportProgress(downloadId, channelId, info.title ?: "", 95)
            val uri = copyFileToStorage(muxed, primaryName, container)
            videoFile.delete()
            audioFile.delete()
            muxed.delete()
            if (uri == null) DownloadResult.Error("Could not create output file")
            else DownloadResult.Success(uri, muxed.length())
        } catch (e: Exception) {
            if (isStopped) DownloadResult.Cancelled("cancelled")
            else DownloadResult.Error(e.message ?: "Download failed")
        }
    }

    private data class Selected(
        val primary: StreamFormat,
        val secondary: StreamFormat?,
        val container: String,
    )

    private fun selectStreams(info: StreamInfo, mode: DownloadMode, maxHeight: Int): Selected? {
        if (mode == DownloadMode.AUDIO) {
            val audio = info.audioStreams.maxByOrNull { it.bitrate } ?: return null
            return Selected(audio, null, "audio")
        }

        // Prefer a progressive MP4 (video + audio combined)
        val progressive = info.videoStreams
            .filter { heightOf(it) in 1..maxHeight }
            .maxByOrNull { heightOf(it) }
        if (progressive != null) {
            return Selected(progressive, null, suffixOf(progressive))
        }

        // Video-only + audio-only, muxed later. Prefer an audio stream that
        // shares the video's container so MediaMuxer can merge them.
        val video = info.videoOnlyStreams
            .filter { heightOf(it) in 1..maxHeight }
            .maxByOrNull { heightOf(it) }
            ?: info.videoOnlyStreams.maxByOrNull { heightOf(it) }
            ?: return null

        val videoSuffix = suffixOf(video)
        val audio = when (videoSuffix) {
            "mp4" -> info.audioStreams
                .filter { suffixOf(it) == "m4a" || suffixOf(it) == "mp4" }
                .maxByOrNull { it.bitrate }
                ?: info.audioStreams.maxByOrNull { it.bitrate }
            else -> info.audioStreams
                .filter { suffixOf(it) == "webm" || suffixOf(it) == "opus" }
                .maxByOrNull { it.bitrate }
                ?: info.audioStreams.maxByOrNull { it.bitrate }
        } ?: return null

        val audioSuffix = suffixOf(audio)
        val container = when {
            videoSuffix == "mp4" && (audioSuffix == "m4a" || audioSuffix == "mp4") -> "mp4"
            videoSuffix == "webm" && (audioSuffix == "webm" || audioSuffix == "opus") -> "webm"
            else -> return null
        }
        return Selected(video, audio, container)
    }

    private fun heightOf(stream: StreamFormat): Int {
        if (stream.height > 0) return stream.height
        val digits = stream.resolution.filter { it.isDigit() }
        return digits.take(4).toIntOrNull() ?: 0
    }

    private fun suffixOf(stream: StreamFormat): String =
        stream.suffix.lowercase(Locale.ROOT)

    private fun streamFileName(ref: StreamRef, primary: StreamFormat, container: String): String {
        val safeTitle = ref.title
            .replace(Regex("[^A-Za-z0-9 _.-]"), "_")
            .take(80)
            .ifBlank { ref.id }
        val ext = when {
            container == "audio" -> if (suffixOf(primary) == "webm") "webm" else "m4a"
            container == "webm" -> "webm"
            else -> "mp4"
        }
        return "$safeTitle.$ext"
    }

    /** Downloads [stream] into [outputFile], reporting progress. */
    private suspend fun fetchToFile(
        info: StreamInfo,
        stream: StreamFormat,
        outputFile: File,
        downloadId: Long,
        channelId: String,
        baseProgress: Int?,
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(stream.url)
                .build()
            val app = applicationContext as LibrePipeApp
            val response = app.container.okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return@withContext DownloadResult.Error("HTTP ${response.code}")
            }
            val body = response.body ?: return@withContext DownloadResult.Error("Empty response")
            val contentLength = body.contentLength().coerceAtLeast(0)
            var downloaded = 0L
            outputFile.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(256 * 1024)
                    while (!isStopped) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        downloaded += read
                        if (contentLength > 0) {
                            val percent = (downloaded * 100 / contentLength).toInt()
                            val global = baseProgress?.let { it + percent / 2 } ?: percent
                            reportProgress(downloadId, channelId, info.title ?: "", global.coerceIn(0, 99))
                        }
                    }
                }
            }
            response.close()
            if (isStopped) DownloadResult.Cancelled("stopped")
            else DownloadResult.Success(Uri.fromFile(outputFile), downloaded)
        } catch (e: Exception) {
            if (isStopped) DownloadResult.Cancelled("stopped")
            else DownloadResult.Error(e.message ?: "Download failed")
        }
    }

    private suspend fun reportProgress(downloadId: Long, channelId: String, title: String, percent: Int) {
        repository.updateProgress(downloadId, percent.coerceIn(0, 100))
        if (percent % 10 == 0 || percent >= 95) {
            androidx.core.app.NotificationManagerCompat.from(applicationContext).notify(
                NOTIFICATION_ID_BASE + downloadId.toInt(),
                buildProgressNotification(title, percent, channelId),
            )
        }
    }

    private suspend fun writeToStorage(
        info: StreamInfo,
        stream: StreamFormat,
        fileName: String,
        downloadId: Long,
        channelId: String,
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(stream.url)
                .build()
            val app = applicationContext as LibrePipeApp
            val response = app.container.okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return@withContext null
            }
            val body = response.body ?: return@withContext null
            val contentLength = body.contentLength().coerceAtLeast(0)
            val uri = createOutputUri(fileName, mimeOf(stream)) ?: return@withContext null
            var downloaded = 0L
            applicationContext.contentResolver.openOutputStream(uri)?.use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(256 * 1024)
                    while (!isStopped) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        downloaded += read
                        if (contentLength > 0) {
                            val percent = (downloaded * 100 / contentLength).toInt()
                            reportProgress(downloadId, channelId, info.title ?: "", percent.coerceIn(0, 99))
                        }
                    }
                }
            }
            response.close()
            if (isStopped) null else markPending(uri, false) ?: uri
        } catch (e: Exception) {
            null
        }
    }

    private fun copyFileToStorage(file: File, fileName: String, container: String): Uri? {
        val mime = if (container == "webm") "video/webm" else "video/mp4"
        val uri = createOutputUri(fileName, mime) ?: return null
        try {
            applicationContext.contentResolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { input ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                    }
                }
            }
            return markPending(uri, false) ?: uri
        } catch (e: Exception) {
            return null
        }
    }

    private fun createOutputUri(fileName: String, mime: String): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/LibrePipe",
                    )
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                applicationContext.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values,
                )
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "LibrePipe",
                ).apply { mkdirs() }
                val file = File(dir, fileName)
                file.createNewFile()
                Uri.fromFile(file)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun markPending(uri: Uri, pending: Boolean): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return uri
        return try {
            val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, if (pending) 1 else 0) }
            applicationContext.contentResolver.update(uri, values, null, null)
            uri
        } catch (e: Exception) {
            null
        }
    }

    private fun mimeOf(stream: StreamFormat): String {
        return when (suffixOf(stream)) {
            "webm" -> "video/webm"
            "m4a", "opus", "ogg" -> "audio/mp4"
            else -> "video/mp4"
        }
    }

    private fun buildProgressNotification(title: String, percent: Int, channelId: String): android.app.Notification {
        return androidx.core.app.NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading")
            .setContentText(title)
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }
}
