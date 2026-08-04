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

    /**
     * Audio track URL for adaptive playback. YouTube only ships one progressive
     * (audio+video) format — itag 18 at 360p — so anything above that is a video-only
     * stream that has to be merged with a separate audio stream. `PlaybackService`
     * reads this and builds a `MergingMediaSource`.
     */
    const val EXTRA_AUDIO_URL = "stream_audio_url"

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
        )
    }

    /** Video URL plus, for adaptive playback, the audio URL to merge with it. */
    data class Selection(val videoUrl: String, val audioUrl: String?, val height: Int)

    fun buildItem(
        info: StreamInfo,
        ref: StreamRef,
        audioOnly: Boolean,
        maxHeight: Int,
    ): MediaItem {
        val selection = selectStreams(info, audioOnly, maxHeight)

        val metadata = MediaMetadata.Builder()
            .setTitle(ref.title)
            .setArtist(ref.uploaderName)
            .setArtworkUri(ref.thumbnailUrl?.let { Uri.parse(it) })
            .setExtras(
                Bundle().apply {
                    putString(EXTRA_REF_JSON, ref.toJson())
                    selection.audioUrl?.let { putString(EXTRA_AUDIO_URL, it) }
                },
            )
            .build()

        val builder = MediaItem.Builder()
            .setMediaId(ref.id)
            .setUri(selection.videoUrl)
            .setMediaMetadata(metadata)

        // Always attach the tracks; visibility is controlled by disabledTrackTypes.
        // Gating attachment on the setting made the in-player toggle a no-op.
        if (!ref.isLive) {
            val subtitles = buildSubtitles(info)
            if (subtitles.isNotEmpty()) builder.setSubtitleConfigurations(subtitles)
        }
        return builder.build()
    }

    /**
     * Resolutions offered for [info], highest first. Only progressive formats for now —
     * listing the adaptive heights would offer resolutions that 403 on playback.
     */
    fun availableHeights(info: StreamInfo): List<Int> {
        if (info.streamType != StreamType.NORMAL) return emptyList()
        return info.videoStreams.map { heightOf(it) }.filter { it > 0 }.distinct().sortedDescending()
    }

    fun selectStreams(info: StreamInfo, audioOnly: Boolean, maxHeight: Int): Selection {
        if (audioOnly || info.streamType == StreamType.AUDIO) {
            val audio = info.audioStreams.maxByOrNull { it.bitrate }
            val url = audio?.url
                ?: info.videoStreams.firstOrNull()?.url
                ?: info.hlsUrl
                ?: info.url
            return Selection(url, null, 0)
        }

        if (info.streamType == StreamType.LIVE) {
            // Live is a single manifest carrying both tracks — never merge.
            val url = info.hlsUrl ?: bestProgressive(info) ?: info.url
            return Selection(url, null, 0)
        }

        val cap = if (maxHeight > 0) maxHeight else Int.MAX_VALUE

        // Progressive only, deliberately. The video-only adaptive streams are DASH
        // segment streams: googlevideo 403s a range-less request for them (which is
        // ExoPlayer's first read) and also 403s any deep byte offset, so they cannot
        // be played as flat files no matter how the requests are chunked. Reaching
        // them needs a generated DASH manifest — tracked separately.
        val progressive = info.videoStreams
        val best = progressive.filter { heightOf(it) in 1..cap }.maxByOrNull { heightOf(it) }
            ?: progressive.maxByOrNull { heightOf(it) }
        if (best != null) return Selection(best.url, null, heightOf(best))

        if (!info.dashMpdUrl.isNullOrBlank()) return Selection(info.dashMpdUrl, null, 0)

        return Selection(info.videoOnlyStreams.firstOrNull()?.url ?: info.url, null, 0)
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
