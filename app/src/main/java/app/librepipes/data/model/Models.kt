package app.librepipes.data.model

import org.json.JSONObject

/**
 * Lightweight, serializable description of a video/audio stream.
 * Used everywhere a stream needs to be stored or passed around (history,
 * playlists, downloads, queues, notifications).
 */
data class StreamRef(
    val id: String,
    val title: String,
    val url: String,
    val thumbnailUrl: String? = null,
    val uploaderName: String? = null,
    val uploaderUrl: String? = null,
    val duration: Long = 0L,
    val viewCount: Long = 0L,
    val textualDate: String? = null,
    val isLive: Boolean = false,
    val isAudio: Boolean = false,
) {
    fun toJson(): String = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("url", url)
        put("thumbnailUrl", thumbnailUrl ?: "")
        put("uploaderName", uploaderName ?: "")
        put("uploaderUrl", uploaderUrl ?: "")
        put("duration", duration)
        put("viewCount", viewCount)
        put("textualDate", textualDate ?: "")
        put("isLive", isLive)
        put("isAudio", isAudio)
    }.toString()

    companion object {
        fun fromJson(json: String?): StreamRef? {
            if (json.isNullOrBlank()) return null
            return try {
                val o = JSONObject(json)
                StreamRef(
                    id = o.getString("id"),
                    title = o.getString("title"),
                    url = o.getString("url"),
                    thumbnailUrl = o.optString("thumbnailUrl").ifEmpty { null },
                    uploaderName = o.optString("uploaderName").ifEmpty { null },
                    uploaderUrl = o.optString("uploaderUrl").ifEmpty { null },
                    duration = o.optLong("duration"),
                    viewCount = o.optLong("viewCount"),
                    textualDate = o.optString("textualDate").ifEmpty { null },
                    isLive = o.optBoolean("isLive"),
                    isAudio = o.optBoolean("isAudio"),
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

data class ChannelRef(
    val id: String,
    val name: String,
    val url: String,
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val subscriberCount: Long = 0L,
    val description: String? = null,
)

data class PlaylistRef(
    val id: String,
    val name: String,
    val url: String,
    val thumbnailUrl: String? = null,
    val uploaderName: String? = null,
    val streamCount: Long = 0L,
)

enum class DownloadMode { VIDEO, AUDIO }

enum class DownloadState { QUEUED, RUNNING, DONE, ERROR, CANCELLED }

enum class PlayerMode { FULL, POPUP, BACKGROUND }
