package app.librepipes.data.youtube

import java.io.IOException

/** Why a video refused to play (from the player's `playabilityStatus`). */
enum class PlayabilityKind {
    UNPLAYABLE,
    AGE_RESTRICTED,
    AGE_VERIFICATION,
    PRIVATE,
    REGION_BLOCKED,
    REMOVED,
    LOGIN_REQUIRED,
    PREMIUM_REQUIRED,
    LIVE_NOT_STARTED,
}

/**
 * Thrown by [app.librepipes.data.youtube.Parsers.parseStreamInfo] when the
 * InnerTube player response reports a status other than `OK`. Carries the raw
 * status/reason/errorCode so screens can render a typed, copyable error.
 */
class PlayabilityException(
    val status: String,
    val kind: PlayabilityKind,
    val errorCode: String?,
    message: String,
) : IOException(message) {

    companion object {
        fun classify(status: String, reason: String?, errorCode: String?): PlayabilityKind {
            val r = reason.orEmpty().lowercase()
            return when {
                status == "LIVE_STREAM_OFFLINE" -> PlayabilityKind.LIVE_NOT_STARTED
                errorCode == "AGE_CHECK_REQUIRED" ||
                    errorCode == "AGE_VERIFICATION_REQUIRED" ||
                    errorCode == "CONTENT_CHECK_REQUIRED" ||
                    r.contains("confirm your age") -> PlayabilityKind.AGE_VERIFICATION
                (r.contains("age") && r.contains("restricted")) ||
                    (r.contains("log in") && r.contains("age")) -> PlayabilityKind.AGE_RESTRICTED
                errorCode == "VIDEO_PLAYBACK_ERROR_REQUIRES_RENTAL" ||
                    r.contains("available to rent or purchase") ||
                    r.contains("members only") || r.contains("members-only") ||
                    r.contains("with premium") ||
                    r.contains("premium") -> PlayabilityKind.PREMIUM_REQUIRED
                r.contains("private video") || r.contains("video is private") -> PlayabilityKind.PRIVATE
                r.contains("has been removed") || r.contains("video unavailable") ||
                    r.contains("no longer available") -> PlayabilityKind.REMOVED
                r.contains("available in your country") ||
                    r.contains("not available in your country") ||
                    r.contains("in your country") -> PlayabilityKind.REGION_BLOCKED
                errorCode == "LOGIN_REQUIRED" || status == "LOGIN_REQUIRED" ->
                    PlayabilityKind.LOGIN_REQUIRED
                else -> PlayabilityKind.UNPLAYABLE
            }
        }
    }
}