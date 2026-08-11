package app.librepipes.player

import app.librepipes.data.youtube.StreamFormat
import app.librepipes.data.youtube.StreamInfo
import app.librepipes.data.youtube.StreamType
import java.util.Locale

/**
 * Builds an on-demand DASH manifest out of YouTube's adaptive formats.
 *
 * YouTube ships one progressive format (itag 18, 360p); everything above it is a
 * video-only stream paired with a separate audio stream. Those are DASH segment
 * streams — googlevideo 403s the range-less whole-file request ExoPlayer issues for a
 * progressive source, but serves the bounded segment requests a manifest produces.
 *
 * Handing ExoPlayer a manifest also lets it merge A/V and switch quality by track
 * selection instead of reloading the item.
 */
object DashManifest {

    /** MIME type to declare on the MediaItem; the data: URI has no extension to infer from. */
    const val MIME = "application/dash+xml"

    /**
     * Returns a manifest covering every H.264 representation up to [maxHeight], or null
     * when this video can't be expressed as one (live, audio-only, missing byte ranges).
     * Callers fall back to the progressive stream on null.
     */
    fun build(info: StreamInfo, maxHeight: Int, exactHeight: Boolean = false): String? {
        if (info.streamType != StreamType.NORMAL) return null

        val cap = if (maxHeight > 0) maxHeight else Int.MAX_VALUE
        // One codec family only: mixing avc1 and vp9 in an AdaptationSet tells the
        // adaptive selector they're interchangeable. avc1 decodes on the widest range
        // of devices.
        val usableVideos = info.videoOnlyStreams
            .filter { it.isUsable() && it.codecs?.startsWith("avc1") == true && it.height in 1..cap }
            .sortedBy { it.height }
        // Pinning matters: emitting everything up to the cap lets ExoPlayer adapt down
        // by bandwidth, so choosing 1080p on a slow link silently keeps playing 240p and
        // the choice looks ignored. One representation leaves nothing to adapt to.
        val videos = if (exactHeight) {
            usableVideos.lastOrNull()?.let { top -> usableVideos.filter { it.height == top.height } }
                ?: usableVideos
        } else {
            usableVideos
        }
        // The original track is listed twice with an identical id, so dedupe.
        val audios = info.audioStreams
            .filter { it.isUsable() && it.codecs?.startsWith("mp4a") == true }
            .distinctBy { it.audioTrackId to it.itag }
            .sortedBy { it.bitrate }

        if (videos.isEmpty() || audios.isEmpty()) return null

        val durationSeconds = videos.firstNotNullOfOrNull { it.durationSeconds() }
            ?: audios.firstNotNullOfOrNull { it.durationSeconds() }
            ?: return null

        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""")
            append(
                """<MPD xmlns="urn:mpeg:dash:schema:mpd:2011" """ +
                    """profiles="urn:mpeg:dash:profile:isoff-on-demand:2011" type="static" """ +
                    """minBufferTime="PT1.5S" mediaPresentationDuration="${duration(durationSeconds)}">""",
            )
            append("<Period>")

            append("""<AdaptationSet mimeType="video/mp4" subsegmentAlignment="true">""")
            videos.forEach { f ->
                append(
                    """<Representation id="${f.itag}" codecs="${f.codecs}" bandwidth="${f.bitrate}" """ +
                        """width="${f.width}" height="${f.height}"""",
                )
                if (f.fps > 0) append(""" frameRate="${f.fps}"""")
                append(""" startWithSAP="1">""")
                append(segments(f))
                append("</Representation>")
            }
            append("</AdaptationSet>")

            // Exactly one audio AdaptationSet, always the video's original track.
            // Emitting every dub made ExoPlayer treat them as interchangeable bitrate
            // variants and swap language mid-playback.
            val chosenId = audios.firstOrNull { it.audioIsDefault }?.audioTrackId
            audios.groupBy { it.audioTrackId }
                .filterKeys { chosenId == null || it == chosenId }
                .forEach { (trackId, formats) ->
                    val best = formats.maxByOrNull { it.bitrate } ?: return@forEach
                    val lang = best.audioLanguage
                    append("""<AdaptationSet mimeType="audio/mp4" subsegmentAlignment="true"""")
                    if (lang != null) append(""" lang="${escape(lang)}"""")
                    append(">")
                    // Marks the original track, which ExoPlayer falls back to when no
                    // language preference matches.
                    if (best.audioIsDefault) {
                        append("""<Role schemeIdUri="urn:mpeg:dash:role:2011" value="main"/>""")
                    }
                    val id = listOfNotNull(best.itag.toString(), trackId).joinToString("-")
                    append("""<Representation id="${escape(id)}" codecs="${best.codecs}" bandwidth="${best.bitrate}"""")
                    if (best.audioSampleRate > 0) append(""" audioSamplingRate="${best.audioSampleRate}"""")
                    append(">")
                    if (best.audioChannels > 0) {
                        append(
                            """<AudioChannelConfiguration """ +
                                """schemeIdUri="urn:mpeg:dash:23003:3:audio_channel_configuration:2011" """ +
                                """value="${best.audioChannels}"/>""",
                        )
                    }
                    append(segments(best))
                    append("</Representation>")
                    append("</AdaptationSet>")
                }

            append("</Period></MPD>")
        }
    }

    /** Heights a manifest built from [info] would actually offer. */
    fun availableHeights(info: StreamInfo): List<Int> {
        if (info.streamType != StreamType.NORMAL) return emptyList()
        if (info.audioStreams.none { it.isUsable() && it.codecs?.startsWith("mp4a") == true }) {
            return emptyList()
        }
        return info.videoOnlyStreams
            .filter { it.isUsable() && it.codecs?.startsWith("avc1") == true && it.height > 0 }
            .map { it.height }
            .distinct()
            .sortedDescending()
    }

    /** A SegmentBase representation is invalid without both byte ranges. */
    private fun StreamFormat.isUsable(): Boolean =
        initRange != null && indexRange != null && codecs != null && url.isNotBlank()

    private fun StreamFormat.durationSeconds(): Double? =
        approxDurationMs?.toLongOrNull()?.takeIf { it > 0 }?.let { it / 1000.0 }

    private fun segments(f: StreamFormat): String =
        """<BaseURL>${escape(f.url)}</BaseURL>""" +
            """<SegmentBase indexRange="${f.indexRange}"><Initialization range="${f.initRange}"/></SegmentBase>"""

    private fun duration(seconds: Double): String =
        "PT" + String.format(Locale.ROOT, "%.3f", seconds) + "S"

    /**
     * Stream URLs are full of `&`; a single unescaped one makes the document
     * unparseable and ExoPlayer fails with an opaque manifest error.
     */
    private fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
