package app.librepipes.player

import androidx.media3.common.C
import androidx.media3.common.Player

fun Player.playOrPause() {
    if (isPlaying) pause() else play()
}

/** Toggles text tracks (subtitles) on and off. */
fun Player.toggleSubtitles() {
    val params = trackSelectionParameters
    val disabled = params.disabledTrackTypes
    trackSelectionParameters = if (C.TRACK_TYPE_TEXT in disabled) {
        params.buildUpon().setDisabledTrackTypes(emptySet()).build()
    } else {
        params.buildUpon().setDisabledTrackTypes(setOf(C.TRACK_TYPE_TEXT)).build()
    }
}
