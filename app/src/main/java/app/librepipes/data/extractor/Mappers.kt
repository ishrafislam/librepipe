package app.librepipes.data.extractor

/**
 * Derives a stable id from a YouTube URL: v=, youtu.be, /shorts/, /live/,
 * /channel/, list=, ...
 */
fun idFromUrl(url: String): String {
    val u = url.substringBefore("?")
    return when {
        "youtu.be" in url || "/shorts/" in u || "/live/" in u -> u.substringAfterLast("/")
        "/watch" in u -> url.substringAfter("v=").substringBefore("&")
        "/channel/" in u -> u.substringAfter("/channel/")
        "/playlist" in u || "list=" in url -> url.substringAfter("list=").substringBefore("&")
        else -> url
    }
}
