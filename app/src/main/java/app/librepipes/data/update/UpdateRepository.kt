package app.librepipes.data.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.security.MessageDigest

data class UpdateRelease(
    val versionCode: Int,
    val versionName: String,
    val minimumSdk: Int,
    val apkAsset: String,
    val apkUrl: String,
    val apkSize: Long,
    val sha256: String,
    val releaseNotes: String,
) {
    fun toCacheJson(): String = JSONObject()
        .put("versionCode", versionCode)
        .put("versionName", versionName)
        .put("minimumSdk", minimumSdk)
        .put("apkAsset", apkAsset)
        .put("apkUrl", apkUrl)
        .put("apkSize", apkSize)
        .put("sha256", sha256)
        .put("releaseNotes", releaseNotes)
        .toString()

    companion object {
        fun fromCacheJson(value: String?): UpdateRelease? = runCatching {
            val json = JSONObject(value ?: return null)
            UpdateRelease(
                versionCode = json.getInt("versionCode"),
                versionName = json.getString("versionName"),
                minimumSdk = json.getInt("minimumSdk"),
                apkAsset = json.getString("apkAsset"),
                apkUrl = json.getString("apkUrl"),
                apkSize = json.optLong("apkSize", -1L),
                sha256 = json.getString("sha256"),
                releaseNotes = json.optString("releaseNotes"),
            )
        }.getOrNull()
    }
}

sealed interface UpdateCheckResult {
    data class Found(val release: UpdateRelease, val etag: String?) : UpdateCheckResult
    data object NotModified : UpdateCheckResult
    data object NoRelease : UpdateCheckResult
}

class UpdateRepository(
    private val context: Context,
    private val client: OkHttpClient,
) {
    suspend fun checkLatest(etag: String?): UpdateCheckResult {
        val request = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2026-03-10")
            .header("User-Agent", "LibrePipe/${app.librepipes.BuildConfig.VERSION_NAME}")
            .apply { if (!etag.isNullOrBlank()) header("If-None-Match", etag) }
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 304) return UpdateCheckResult.NotModified
            if (response.code == 404) return UpdateCheckResult.NoRelease
            if (!response.isSuccessful) throw IOException("GitHub update check failed (${response.code})")

            val releaseJson = JSONObject(response.body?.string() ?: throw IOException("Empty GitHub release response"))
            if (!isStableGitHubRelease(releaseJson)) {
                return UpdateCheckResult.NoRelease
            }
            val assets = releaseJson.optJSONArray("assets") ?: throw IOException("Release has no assets")
            val manifestAsset = findReleaseAsset(assets, UPDATE_MANIFEST)
                ?: throw IOException("Release is missing $UPDATE_MANIFEST")
            val manifest = fetchManifest(manifestAsset.first)
            val apkName = manifest.getString("apkAsset")
            val apk = findReleaseAsset(assets, apkName) ?: throw IOException("Release is missing $apkName")
            val sha256 = manifest.getString("sha256").lowercase()
            if (!SHA256.matches(sha256)) throw IOException("Update checksum is invalid")

            return UpdateCheckResult.Found(
                release = UpdateRelease(
                    versionCode = manifest.getInt("versionCode"),
                    versionName = manifest.getString("versionName"),
                    minimumSdk = manifest.getInt("minimumSdk"),
                    apkAsset = apkName,
                    apkUrl = apk.first,
                    apkSize = apk.second,
                    sha256 = sha256,
                    releaseNotes = releaseJson.optString("body"),
                ),
                etag = response.header("ETag"),
            )
        }
    }

    fun downloadAndVerify(release: UpdateRelease, onProgress: (Int) -> Unit): File {
        if (!isAllowedReleaseUrl(release.apkUrl)) throw IOException("Untrusted update URL")
        val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
        updateDir.listFiles()?.forEach { it.delete() }
        val partial = File(updateDir, "librepipe-${release.versionCode}.apk.part")
        val target = File(updateDir, "librepipe-${release.versionCode}.apk")
        var completed = false
        try {
            val request = Request.Builder()
                .url(release.apkUrl)
                .header("User-Agent", "LibrePipe/${app.librepipes.BuildConfig.VERSION_NAME}")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Update download failed (${response.code})")
                val body = response.body ?: throw IOException("Empty update download")
                val contentLength = body.contentLength()
                if (contentLength > MAX_APK_BYTES || release.apkSize > MAX_APK_BYTES) {
                    throw IOException("Update APK is too large")
                }
                if (release.apkSize > 0 && contentLength > 0 && release.apkSize != contentLength) {
                    throw IOException("Update APK size does not match release")
                }
                body.byteStream().use { input ->
                    partial.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var copied = 0L
                        var lastProgress = -1
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            val total = when {
                                contentLength > 0 -> contentLength
                                release.apkSize > 0 -> release.apkSize
                                else -> -1L
                            }
                            val progress = if (total > 0) ((copied * 100) / total).toInt().coerceIn(0, 100) else 0
                            if (progress != lastProgress) {
                                lastProgress = progress
                                onProgress(progress)
                            }
                        }
                    }
                }
            }

            val digest = sha256(partial)
            if (!digest.equals(release.sha256, ignoreCase = true)) throw IOException("Update checksum does not match")
            validateApk(partial, release)
            if (target.exists() && !target.delete()) throw IOException("Could not replace cached update")
            if (!partial.renameTo(target)) throw IOException("Could not finalize update download")
            completed = true
            onProgress(100)
            return target
        } finally {
            if (!completed) partial.delete()
        }
    }

    private fun fetchManifest(url: String): JSONObject {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "LibrePipe/${app.librepipes.BuildConfig.VERSION_NAME}")
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Update manifest download failed (${response.code})")
            val json = JSONObject(response.body?.string() ?: throw IOException("Empty update manifest"))
            if (json.optInt("schema") != 1) throw IOException("Unsupported update manifest")
            json
        }
    }

    private fun validateApk(file: File, release: UpdateRelease) {
        val packageManager = context.packageManager
        val archive = packageInfo(packageManager, file.absolutePath)
            ?: throw IOException("Downloaded file is not a valid APK")
        val installed = packageInfo(packageManager, context.packageName)
            ?: throw IOException("Could not verify installed app signature")
        validateUpdateIdentity(
            expectedPackage = context.packageName,
            currentVersionCode = app.librepipes.BuildConfig.VERSION_CODE,
            installedCertificates = certificateDigests(installed),
            archive = ApkIdentity(
                packageName = archive.packageName,
                versionCode = PackageInfoCompat.getLongVersionCode(archive),
                versionName = archive.versionName.orEmpty(),
                minimumSdk = archive.applicationInfo?.minSdkVersion ?: release.minimumSdk,
                certificates = certificateDigests(archive),
            ),
            release = release,
            deviceSdk = Build.VERSION.SDK_INT,
        )
    }

    @Suppress("DEPRECATION")
    private fun packageInfo(packageManager: PackageManager, value: String): PackageInfo? {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        return if (Build.VERSION.SDK_INT >= 33) {
            if (value.endsWith(".apk")) {
                packageManager.getPackageArchiveInfo(value, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                packageManager.getPackageInfo(value, PackageManager.PackageInfoFlags.of(flags.toLong()))
            }
        } else if (value.endsWith(".apk")) {
            packageManager.getPackageArchiveInfo(value, flags)
        } else {
            packageManager.getPackageInfo(value, flags)
        }
    }

    @Suppress("DEPRECATION")
    private fun certificateDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners.orEmpty()
        } else {
            info.signatures.orEmpty()
        }
        return signatures.mapTo(mutableSetOf()) { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).toHex()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/ishrafislam/librepipe/releases/latest"
        const val UPDATE_MANIFEST = "update.json"
        private const val MAX_APK_BYTES = 250L * 1024L * 1024L
        private val SHA256 = Regex("^[0-9a-f]{64}$")

        fun isAllowedReleaseUrl(value: String): Boolean = runCatching {
            val url = java.net.URI(value)
            url.scheme == "https" && url.host.equals("github.com", ignoreCase = true)
        }.getOrDefault(false)
    }
}

internal fun isStableGitHubRelease(json: JSONObject): Boolean =
    !json.optBoolean("draft") && !json.optBoolean("prerelease")

internal fun findReleaseAsset(assets: JSONArray, exactName: String): Pair<String, Long>? {
    for (index in 0 until assets.length()) {
        val asset = assets.optJSONObject(index) ?: continue
        if (asset.optString("name") != exactName) continue
        val url = asset.optString("browser_download_url")
        if (!UpdateRepository.isAllowedReleaseUrl(url)) return null
        return url to asset.optLong("size", -1L)
    }
    return null
}

internal data class ApkIdentity(
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
    val minimumSdk: Int,
    val certificates: Set<String>,
)

internal fun validateUpdateIdentity(
    expectedPackage: String,
    currentVersionCode: Int,
    installedCertificates: Set<String>,
    archive: ApkIdentity,
    release: UpdateRelease,
    deviceSdk: Int,
) {
    if (archive.packageName != expectedPackage) throw IOException("Update package name does not match")
    if (archive.versionCode != release.versionCode.toLong()) throw IOException("Update version does not match manifest")
    if (archive.versionCode <= currentVersionCode) throw IOException("Update version is not newer")
    if (archive.versionName != release.versionName) throw IOException("Update name does not match manifest")
    if (archive.minimumSdk != release.minimumSdk) throw IOException("Update minimum SDK does not match manifest")
    if (archive.minimumSdk > deviceSdk) throw IOException("Update requires newer Android version")
    if (archive.certificates.isEmpty() || archive.certificates != installedCertificates) {
        throw IOException("Update signing certificate does not match")
    }
}
