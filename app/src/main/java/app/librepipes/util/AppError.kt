package app.librepipes.util

import app.librepipes.data.youtube.PlayabilityException
import app.librepipes.data.youtube.PlayabilityKind
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException

/**
 * User-facing, typed error rendered by [app.librepipes.ui.components.kit.LpErrorState].
 * [code] is the copyable mono diagnostic (board 05).
 */
data class AppError(
    val code: String,
    val message: String,
) {
    companion object {
        const val OFFLINE = "OFFLINE"
        const val TIMEOUT = "TIMEOUT"
        const val EXTRACTOR_PARSE = "EXTRACTOR_PARSE"
        const val UNKNOWN = "UNKNOWN"
        const val AGE_RESTRICTED = "AGE_RESTRICTED"
        const val AGE_VERIFICATION = "AGE_VERIFICATION"
        const val PRIVATE = "PRIVATE"
        const val REGION_BLOCKED = "REGION_BLOCKED"
        const val REMOVED = "REMOVED"
        const val LOGIN_REQUIRED = "LOGIN_REQUIRED"
        const val PREMIUM_REQUIRED = "PREMIUM_REQUIRED"
        const val LIVE_NOT_STARTED = "LIVE_NOT_STARTED"
        const val UNPLAYABLE = "UNPLAYABLE"
    }
}

/** Classifies any failure from the extractor/network layer into a typed [AppError]. */
fun Throwable.toAppError(): AppError = when (this) {
    is PlayabilityException -> when (kind) {
        PlayabilityKind.AGE_RESTRICTED -> AppError(
            AppError.AGE_RESTRICTED,
            "This video is age-restricted. Librepipe can't sign in, so it can't be played.",
        )
        PlayabilityKind.AGE_VERIFICATION -> AppError(
            AppError.AGE_VERIFICATION,
            "This video requires age verification, which Librepipe can't provide.",
        )
        PlayabilityKind.PRIVATE -> AppError(
            AppError.PRIVATE,
            "This video is private and can only be watched by its uploader.",
        )
        PlayabilityKind.REGION_BLOCKED -> AppError(
            AppError.REGION_BLOCKED,
            message.orEmpty().ifBlank { "This video isn't available in your region." },
        )
        PlayabilityKind.REMOVED -> AppError(
            AppError.REMOVED,
            message.orEmpty().ifBlank { "This video has been removed by its uploader." },
        )
        PlayabilityKind.LOGIN_REQUIRED -> AppError(
            AppError.LOGIN_REQUIRED,
            "This video requires a YouTube sign-in, which Librepipe doesn't support.",
        )
        PlayabilityKind.PREMIUM_REQUIRED -> AppError(
            AppError.PREMIUM_REQUIRED,
            "This video is for YouTube Premium or rental members only.",
        )
        PlayabilityKind.LIVE_NOT_STARTED -> AppError(
            AppError.LIVE_NOT_STARTED,
            "This live stream hasn't started yet.",
        )
        PlayabilityKind.UNPLAYABLE -> AppError(
            AppError.UNPLAYABLE,
            message.orEmpty().ifBlank { "This video can't be played." },
        )
    }
    is UnknownHostException -> AppError(AppError.OFFLINE, "You're offline. Check your connection and try again.")
    is SocketTimeoutException -> AppError(AppError.TIMEOUT, "The request timed out. Try again.")
    is TimeoutException -> AppError(AppError.TIMEOUT, "The request timed out. Try again.")
    is IOException -> AppError(AppError.EXTRACTOR_PARSE, message.orEmpty().ifBlank { "Couldn't load this. Try again." })
    else -> AppError(AppError.UNKNOWN, message.orEmpty().ifBlank { "Something unexpected went wrong." })
}
