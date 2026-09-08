# Distribution

GitHub Releases is LibrePipe's canonical distribution channel. Each public release contains a signed universal APK, its SHA-256 checksum, release notes, and `update.json` for the in-app updater. The same release URL can be used by Obtainium and the future project website.

## Repository setup

Create `develop` from `main` after the current milestone is merged. Protect both branches in GitHub:

- Require pull requests and block direct pushes.
- Require the `Android verification` status check.
- Require branches to be up to date before merging.
- Allow the release workflow to write repository contents.

Create a protected GitHub Actions environment named `release`, restricted to `main`, with these secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Back up the original keystore and credentials offline. Every production APK must retain package name `app.librepipes` and use this signing key.

## Releasing

Every pull request into `main` must increase `VERSION_CODE` and change semantic `VERSION_NAME` in `gradle.properties`. Merging the pull request runs full verification, builds the signed APK, creates tag `vVERSION_NAME`, uploads a draft release, then publishes it only after all assets exist.

Pull requests into `develop` run the same Android checks but do not require a version bump.

Register `app.librepipes` and the production signing certificate in Android Developer Console before global developer-verification enforcement.
