package app.librepipes.player

import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy

/**
 * Pairs a video-only stream with its audio track.
 *
 * YouTube ships exactly one progressive (audio+video) format — itag 18 at 360p — so
 * every higher resolution is a video-only stream. A [MediaItem] carries a single URI,
 * and a controller in another process cannot build a [MediaSource], so the pairing has
 * to happen here, inside the service that owns the player: the audio URL rides along in
 * [Playback.EXTRA_AUDIO_URL] and is merged in.
 *
 * Items without that extra (live HLS, audio-only, the progressive fallback) pass
 * straight through untouched.
 */
@UnstableApi
class AdaptiveMediaSourceFactory(
    private val delegate: MediaSource.Factory,
) : MediaSource.Factory {

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val video = delegate.createMediaSource(mediaItem)
        val audioUrl = mediaItem.mediaMetadata.extras
            ?.getString(Playback.EXTRA_AUDIO_URL)
            ?.takeIf { it.isNotBlank() }
            ?: return video

        // A failure here would take down playback that could otherwise run video-only,
        // so degrade instead of throwing.
        val audio = runCatching {
            delegate.createMediaSource(MediaItem.fromUri(audioUrl))
        }.getOrNull() ?: return video

        // The two tracks are the same recording, so their durations already agree;
        // clipping to the shorter guards against a truncated fetch.
        return MergingMediaSource(
            /* adjustPeriodTimeOffsets = */ false,
            /* clipDurations = */ true,
            video,
            audio,
        )
    }

    override fun getSupportedTypes(): IntArray = delegate.supportedTypes

    override fun setDrmSessionManagerProvider(
        drmSessionManagerProvider: DrmSessionManagerProvider,
    ): MediaSource.Factory = apply { delegate.setDrmSessionManagerProvider(drmSessionManagerProvider) }

    override fun setLoadErrorHandlingPolicy(
        loadErrorHandlingPolicy: LoadErrorHandlingPolicy,
    ): MediaSource.Factory = apply { delegate.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy) }
}
