package app.librepipes.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class UpdateRepositoryTest {
    @Test
    fun cachedRelease_roundTripsAllFields() {
        val release = UpdateRelease(
            versionCode = 42,
            versionName = "1.2.3",
            minimumSdk = 26,
            apkAsset = "librepipe-1.2.3.apk",
            apkUrl = "https://github.com/ishrafislam/librepipe/releases/download/v1.2.3/librepipe-1.2.3.apk",
            apkSize = 1234L,
            sha256 = "a".repeat(64),
            releaseNotes = "Fixes",
        )

        assertEquals(release, UpdateRelease.fromCacheJson(release.toCacheJson()))
    }

    @Test
    fun cachedRelease_rejectsMalformedJson() {
        assertNull(UpdateRelease.fromCacheJson("not-json"))
        assertNull(UpdateRelease.fromCacheJson(null))
    }

    @Test
    fun releaseUrl_acceptsOnlyGitHubHttps() {
        assertTrue(UpdateRepository.isAllowedReleaseUrl("https://github.com/ishrafislam/librepipe/releases/download/v1/app.apk"))
        assertFalse(UpdateRepository.isAllowedReleaseUrl("http://github.com/ishrafislam/librepipe/releases/download/v1/app.apk"))
        assertFalse(UpdateRepository.isAllowedReleaseUrl("https://github.com.evil.example/app.apk"))
        assertFalse(UpdateRepository.isAllowedReleaseUrl("https://example.com/app.apk"))
    }

    @Test
    fun releaseFiltering_rejectsDraftsAndPrereleases() {
        assertTrue(isStableGitHubRelease(JSONObject("""{"draft":false,"prerelease":false}""")))
        assertFalse(isStableGitHubRelease(JSONObject("""{"draft":true,"prerelease":false}""")))
        assertFalse(isStableGitHubRelease(JSONObject("""{"draft":false,"prerelease":true}""")))
    }

    @Test
    fun assetSelection_requiresExactNameAndTrustedUrl() {
        val assets = JSONArray(
            """[
                {"name":"librepipe.apk.sha256","browser_download_url":"https://github.com/a/b/checksum","size":64},
                {"name":"librepipe.apk","browser_download_url":"https://github.com/a/b/apk","size":1234}
            ]""",
        )
        assertEquals("https://github.com/a/b/apk", findReleaseAsset(assets, "librepipe.apk")?.first)
        assertNull(findReleaseAsset(assets, "other.apk"))

        val untrusted = JSONArray(
            """[{"name":"librepipe.apk","browser_download_url":"https://example.com/app.apk","size":1234}]""",
        )
        assertNull(findReleaseAsset(untrusted, "librepipe.apk"))
    }

    @Test
    fun updateIdentity_acceptsMatchingNewerApk() {
        validateUpdateIdentity(
            expectedPackage = "app.librepipes",
            currentVersionCode = 41,
            installedCertificates = setOf("certificate"),
            archive = validIdentity(),
            release = validRelease(),
            deviceSdk = 36,
        )
    }

    @Test
    fun updateIdentity_rejectsPackageVersionSdkAndSignerMismatch() {
        assertIdentityFailure(validIdentity().copy(packageName = "evil.app"), "package name")
        assertIdentityFailure(validIdentity().copy(versionCode = 41), "version")
        assertIdentityFailure(validIdentity().copy(versionName = "9.9.9"), "name")
        assertIdentityFailure(validIdentity().copy(minimumSdk = 27), "minimum SDK")
        assertIdentityFailure(validIdentity().copy(certificates = setOf("other")), "signing certificate")

        val failure = runCatching {
            validateUpdateIdentity(
                expectedPackage = "app.librepipes",
                currentVersionCode = 41,
                installedCertificates = setOf("certificate"),
                archive = validIdentity(),
                release = validRelease(),
                deviceSdk = 25,
            )
        }.exceptionOrNull()
        assertTrue(failure is IOException && failure.message.orEmpty().contains("newer Android"))
    }

    private fun assertIdentityFailure(identity: ApkIdentity, messagePart: String) {
        val failure = runCatching {
            validateUpdateIdentity(
                expectedPackage = "app.librepipes",
                currentVersionCode = 41,
                installedCertificates = setOf("certificate"),
                archive = identity,
                release = validRelease(),
                deviceSdk = 36,
            )
        }.exceptionOrNull()
        assertTrue(failure is IOException && failure.message.orEmpty().contains(messagePart))
    }

    private fun validIdentity() = ApkIdentity(
        packageName = "app.librepipes",
        versionCode = 42,
        versionName = "1.2.3",
        minimumSdk = 26,
        certificates = setOf("certificate"),
    )

    private fun validRelease() = UpdateRelease(
        versionCode = 42,
        versionName = "1.2.3",
        minimumSdk = 26,
        apkAsset = "librepipe-1.2.3.apk",
        apkUrl = "https://github.com/ishrafislam/librepipe/releases/download/v1.2.3/librepipe-1.2.3.apk",
        apkSize = 1234L,
        sha256 = "a".repeat(64),
        releaseNotes = "Fixes",
    )
}
