package app.librepipes.player

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import app.librepipes.LibrePipeApp
import app.librepipes.data.extractor.Extractor
import app.librepipes.data.model.StreamRef
import app.librepipes.data.youtube.StreamFormat
import app.librepipes.data.youtube.StreamInfo
import app.librepipes.data.youtube.StreamType
import java.util.Locale

/**
 * Builds [MediaItem]s from extractor stream info: picks the right format
 * (progressive MP4, DASH manifest, HLS for live, or audio-only), attaches
 * metadata and subtitle tracks.
 */
object Playback {

    const val EXTRA_REF_JSON = "stream_ref_json"
    const val MAX_QUEUE = 40

    data class Resolved(
        val items: List<MediaItem>,
        val startIndex: Int,
        val startPosition: Long,
        val audioOnly: Boolean,
        val subtitlesAvailable: Boolean,
        /** Epoch millis when the first item goes live (premiere/countdown). */
        val premiereAt: Long? = null,
    )

    /** Resolves the first stream plus the queue into playable media items. */
    suspend fun resolve(context: Context, first: StreamRef, queue: List<StreamRef> = listOf(first)): Resolved {
        val container = (context.applicationContext as LibrePipeApp).container
        val settings = container.settings.snapshot()
        val audioOnly = settings.audioOnly || first.isAudio
        val captionsOn = settings.captionsEnabled && !audioOnly

        val streamInfo = Extractor.stream(first.url)

        // A premiere that hasn't gone live yet: nothing to play — the caller
        // renders the countdown state instead.
        if (streamInfo.premiereAt != null) {
            return Resolved(
                items = emptyList(),
                startIndex = 0,
                startPosition = 0L,
                audioOnly = audioOnly,
                subtitlesAvailable = false,
                premiereAt = streamInfo.premiereAt,
            )
        }

        val startPosition = if (settings.recordHistory) {
            container.history.getByStreamId(first.id)?.let {
                if (it.positionMs in 15_000..(it.durationMs * 90 / 100)) it.positionMs else 0L
            } ?: 0L
        } else 0L

        val items = mutableListOf(buildItem(streamInfo, first, audioOnly, captionsOn, settings.maxQuality))

        val remaining = queue.filter { it.id != first.id }.take(MAX_QUEUE)
        for (ref in remaining) {
            val item = runCatching {
                val info = Extractor.stream(ref.url)
                if (info.premiereAt != null) null else buildItem(info, ref, audioOnly, captionsOn, settings.maxQuality)
            }.getOrNull()
            if (item != null) items += item
        }

        return Resolved(
            items = items,
            startIndex = 0,
            startPosition = startPosition,
            audioOnly = audioOnly,
            subtitlesAvailable = streamInfo.subtitles.isNotEmpty(),
        )
    }

    private fun buildItem(
        info: StreamInfo,
        ref: StreamRef,
        audioOnly: Boolean,
        captionsOn: Boolean,
        maxHeight: Int,
    ): MediaItem {
        val url = selectUrl(info, audioOnly, maxHeight)

        val metadata = MediaMetadata.Builder()
            .setTitle(ref.title)
            .setArtist(ref.uploaderName)
            .setArtworkUri(ref.thumbnailUrl?.let { Uri.parse(it) })
            .setExtras(Bundle().apply { putString(EXTRA_REF_JSON, ref.toJson()) })
            .build()

        val builder = MediaItem.Builder()
            .setMediaId(ref.id)
            .setUri(url)
            .setMediaMetadata(metadata)

        if (captionsOn && !ref.isLive) {
            val subtitles = buildSubtitles(info)
            if (subtitles.isNotEmpty()) builder.setSubtitleConfigurations(subtitles)
        }
        return builder.build()
    }

    private fun selectUrl(info: StreamInfo, audioOnly: Boolean, maxHeight: Int): String {
        val isLive = info.streamType == StreamType.LIVE

        if (audioOnly || info.streamType == StreamType.AUDIO) {
            val audio = info.audioStreams.maxByOrNull { it.bitrate }
            return audio?.url
                ?: info.videoStreams.firstOrNull()?.url
                ?: info.hlsUrl
                ?: info.url
        }

        if (isLive) {
            return info.hlsUrl ?: bestProgressive(info) ?: info.url
        }

        // Progressive formats carry audio+video combined — ideal for playback.
        val progressive = info.videoStreams
        val withinLimit = progressive.filter { heightOf(it) in 1..maxHeight }
        val mp4WithinLimit = withinLimit
            .filter { it.suffix.equals("mp4", ignoreCase = true) }
            .maxByOrNull { heightOf(it) }
        val best = mp4WithinLimit
            ?: withinLimit.maxByOrNull { heightOf(it) }
            ?: progressive.maxByOrNull { heightOf(it) }
        if (best != null) return best.url

        // No combined format: fall back to the DASH manifest (handles A/V sync).
        if (!info.dashMpdUrl.isNullOrBlank()) return info.dashMpdUrl

        return info.videoOnlyStreams.firstOrNull()?.url ?: info.url
    }

    private fun bestProgressive(info: StreamInfo): String? =
        info.videoStreams.maxByOrNull { heightOf(it) }?.url

    private fun heightOf(stream: StreamFormat): Int {
        if (stream.height > 0) return stream.height
        val digits = stream.resolution.filter { it.isDigit() }
        return digits.take(4).toIntOrNull() ?: 0
    }

    private fun buildSubtitles(info: StreamInfo): List<MediaItem.SubtitleConfiguration> {
        val deviceLang = Locale.getDefault().language
        val configs = mutableListOf<MediaItem.SubtitleConfiguration>()
        info.subtitles.forEachIndexed { index, sub ->
            val isManual = !sub.isAutoGenerated
            val flags = when {
                isManual && sub.languageTag?.startsWith(deviceLang, ignoreCase = true) == true ->
                    C.SELECTION_FLAG_DEFAULT
                isManual && index == 0 -> C.SELECTION_FLAG_DEFAULT
                else -> 0
            }
            val builder = MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                .setMimeType(MimeTypes.TEXT_VTT)
                .setLanguage(sub.languageTag)
                .setSelectionFlags(flags)
            configs += builder.build()
        }
        return configs
    }
}
