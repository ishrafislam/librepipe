package app.librepipes.util

import java.util.Locale

object Format {

    /** Formats milliseconds as m:ss or h:mm:ss. */
    fun time(ms: Long): String {
        if (ms <= 0) return "0:00"
        return durationSeconds(ms / 1000)
    }

    /** Formats a duration in seconds as m:ss or h:mm:ss. */
    fun durationSeconds(totalSeconds: Long): String {
        val seconds = totalSeconds.coerceAtLeast(0)
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.ROOT, "%d:%02d", m, s)
        }
    }

    /** Formats a large number compactly: 1234 -> 1.2K, 2300000 -> 2.3M. */
    fun count(value: Long): String {
        if (value < 1_000) return value.toString()
        if (value < 1_000_000) {
            return String.format(Locale.ROOT, "%.1fK", value / 1000.0)
        }
        if (value < 1_000_000_000) {
            return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0)
        }
        return String.format(Locale.ROOT, "%.1fB", value / 1_000_000_000.0)
    }

    private val RELATIVE_DATE = Regex(
        "(\\d+)\\s*(second|minute|hour|day|week|month|year)",
        RegexOption.IGNORE_CASE,
    )

    /**
     * "3 days ago" -> approximate epoch millis; null when unparseable (live streams,
     * premieres, absolute dates). Approximate on purpose — it exists only to order a
     * feed pooled from several channels, where YouTube gives us nothing better than
     * this relative text.
     */
    fun approxPublishedAt(textualDate: String?, now: Long = System.currentTimeMillis()): Long? {
        if (textualDate.isNullOrBlank()) return null
        val match = RELATIVE_DATE.find(textualDate) ?: return null
        val amount = match.groupValues[1].toLongOrNull() ?: return null
        val unitMs = when (match.groupValues[2].lowercase(Locale.ROOT)) {
            "second" -> 1_000L
            "minute" -> 60_000L
            "hour" -> 3_600_000L
            "day" -> 86_400_000L
            "week" -> 604_800_000L
            "month" -> 2_592_000_000L
            "year" -> 31_536_000_000L
            else -> return null
        }
        return now - amount * unitMs
    }

    /** Compact metadata line: "1.2M views • Mar 5, 2026" */
    fun videoMeta(views: Long, date: String?): String {
        val parts = mutableListOf<String>()
        if (views > 0) parts += "${count(views)} views"
        if (!date.isNullOrBlank()) parts += date
        return parts.joinToString(" • ")
    }
}
