package app.librepipes.player

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Base64
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
        /** Shared so callers don't issue a second player request for the same video. */
        val streamInfo: StreamInfo? = null,
    )

    /** Resolves the first stream plus the queue into playable media items. */
    suspend fun resolve(context: Context, first: StreamRef, queue: List<StreamRef> = listOf(first)): Resolved {
        val container = (context.applicationContext as LibrePipeApp).container
        val settings = container.settings.snapshot()
        val audioOnly = settings.audioOnly || first.isAudio

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

        val items = mutableListOf(buildItem(streamInfo, first, audioOnly, settings.maxQuality))

        val remaining = queue.filter { it.id != first.id }.take(MAX_QUEUE)
        for (ref in remaining) {
            val item = runCatching {
                val info = Extractor.stream(ref.url)
                if (info.premiereAt != null) null else buildItem(info, ref, audioOnly, settings.maxQuality)
            }.getOrNull()
            if (item != null) items += item
        }

        return Resolved(
            items = items,
            startIndex = 0,
            startPosition = startPosition,
            audioOnly = audioOnly,
            subtitlesAvailable = streamInfo.subtitles.isNotEmpty(),
            streamInfo = streamInfo,
        )
    }

    /**
     * What to play. [isManifest] items are DASH and need an explicit MIME type, since a
     * data: URI carries no extension for Media3 to infer from.
     */
    data class Selection(val uri: String, val isManifest: Boolean, val height: Int)

    fun buildItem(
        info: StreamInfo,
        ref: StreamRef,
        audioOnly: Boolean,
        maxHeight: Int,
        /** Skip the manifest and use the progressive stream — the runtime fallback path. */
        forceProgressive: Boolean = false,
        /** Pin to exactly [maxHeight] rather than allowing anything up to it. */
        exactHeight: Boolean = false,
    ): MediaItem {
        val selection = selectStreams(info, audioOnly, maxHeight, forceProgressive, exactHeight)

        val metadata = MediaMetadata.Builder()
            .setTitle(ref.title)
            .setArtist(ref.uploaderName)
            .setArtworkUri(ref.thumbnailUrl?.let { Uri.parse(it) })
            .setExtras(Bundle().apply { putString(EXTRA_REF_JSON, ref.toJson()) })
            .build()

        val builder = MediaItem.Builder()
            .setMediaId(ref.id)
            .setUri(selection.uri)
            .setMediaMetadata(metadata)

        if (selection.isManifest) builder.setMimeType(DashManifest.MIME)

        // Always attach the tracks; visibility is controlled by disabledTrackTypes.
        // Gating attachment on the setting made the in-player toggle a no-op.
        if (!ref.isLive) {
            val subtitles = buildSubtitles(info)
            if (subtitles.isNotEmpty()) builder.setSubtitleConfigurations(subtitles)
        }
        return builder.build()
    }

    /** Resolutions offered for [info], highest first. */
    fun availableHeights(info: StreamInfo): List<Int> {
        val adaptive = DashManifest.availableHeights(info)
        if (adaptive.isNotEmpty()) return adaptive
        if (info.streamType != StreamType.NORMAL) return emptyList()
        return info.videoStreams.map { heightOf(it) }.filter { it > 0 }.distinct().sortedDescending()
    }

    fun selectStreams(
        info: StreamInfo,
        audioOnly: Boolean,
        maxHeight: Int,
        forceProgressive: Boolean = false,
        exactHeight: Boolean = false,
    ): Selection {
        if (audioOnly || info.streamType == StreamType.AUDIO) {
            // On a dubbed video the highest bitrate is an arbitrary language; prefer
            // the original track first.
            // Always the original track on a dubbed video.
            val audio = info.audioStreams
                .let { all -> all.filter { it.audioIsDefault }.ifEmpty { all } }
                .maxByOrNull { it.bitrate }
            val url = audio?.url
                ?: info.videoStreams.firstOrNull()?.url
                ?: info.hlsUrl
                ?: info.url
            return Selection(url, false, 0)
        }

        if (info.streamType == StreamType.LIVE) {
            // Live is a single manifest carrying both tracks already.
            val url = info.hlsUrl ?: bestProgressive(info) ?: info.url
            return Selection(url, false, 0)
        }

        val cap = if (maxHeight > 0) maxHeight else Int.MAX_VALUE

        // Adaptive via a generated manifest — the only route above 360p, since the
        // video-only streams 403 the range-less whole-file read a progressive source
        // issues. Segment requests are bounded, which googlevideo does serve.
        if (!forceProgressive) {
            val manifest = DashManifest.build(info, cap, exactHeight)
            if (manifest != null) {
                val encoded = Base64.encodeToString(manifest.toByteArray(), Base64.NO_WRAP)
                val height = DashManifest.availableHeights(info).firstOrNull { it <= cap } ?: 0
                return Selection("data:${DashManifest.MIME};base64,$encoded", true, height)
            }
        }

        val progressive = info.videoStreams
        val best = progressive.filter { heightOf(it) in 1..cap }.maxByOrNull { heightOf(it) }
            ?: progressive.maxByOrNull { heightOf(it) }
        if (best != null) return Selection(best.url, false, heightOf(best))

        if (!info.dashMpdUrl.isNullOrBlank()) return Selection(info.dashMpdUrl, true, 0)

        return Selection(info.videoOnlyStreams.firstOrNull()?.url ?: info.url, false, 0)
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
