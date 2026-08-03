# AGENTS.md

LibrePipe — single-module Android app (`:app`), Kotlin + Jetpack Compose (Material3), package/namespace `app.librepipes`, minSdk 26 / compileSdk 36 / Java 17. Privacy-focused YouTube client: no login, no ads, no Google Play Services.

## Build & verify

- Typecheck (fast, use after any code change): `./gradlew :app:compileDebugKotlin`
- Full build: `./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
- Lint: `./gradlew :app:lintDebug` — `abortOnError = false`, never blocks; still worth reviewing
- Unit tests (Parsers only): `./gradlew :app:testDebugUnitTest` — JUnit 4 against **live API fixtures** in `app/src/test/resources/fixtures/` (captured 2026-08-02, one per response shape: legacy `videoRenderer`, `lockupViewModel`, `playlistHeaderRenderer` classic + `pageHeaderRenderer` new, ANDROID player, WEB_REMIX music, empty anonymous home feed). Expected values are hardcoded from the fixtures at capture time; re-capture a fixture and update the assertions when YouTube drifts. There are no instrumented tests and no CI.
- Tests need `org.json:json` on the test classpath (`testImplementation(libs.orgjson)`) — android.jar's org.json stubs throw in host JVM tests. Do NOT add it to main; `org.json` is Android's built-in.
- JDK 21 is installed; Gradle toolchain targets Java 17. Core library desugaring is enabled — keep it for `java.time`/`java.util.function` on minSdk 26.
- Add dependencies via `gradle/libs.versions.toml` (version catalog), never inline.
- JDK versions: AGP 8.13.2, Kotlin 2.3.10, KSP 2.3.10 (Room codegen).

## Architecture (hard-earned, not obvious)

- **There is no NewPipeExtractor.** Extraction is fully native in `app/src/main/java/app/librepipes/data/youtube/`:
  - `Innertube.kt` — HTTP client for YouTube's InnerTube JSON API (WEB client for search/browse, ANDROID client 20.05.42 for the player endpoint, WEB_REMIX for Music search). Client version is bootstrapped from `sw.js` with a fallback constant.
  - `Parsers.kt` — JSON→model parsing for legacy renderers (`videoRenderer`, `channelRenderer`) and the 2025+ `lockupViewModel` layout, continuation tokens, subtitles, format classification by codec strings.
  - `YoutubeModels.kt` — `StreamInfo`/`StreamFormat`/`SubtitleTrack`/`StreamType` (playback & download types).
- **`Extractor` object (`data/extractor/Extractor.kt`) is the public facade** every ViewModel and worker calls (`search`, `suggestions`, `stream`, `channel`, `playlist`, `trending`, feeds with `loadInitial()`/`loadMore()`). Keep its API stable when touching the engine — callers: `MainViewModels.kt`, `SearchScreen.kt`, `Playback.kt`, `DownloadWorker.kt`, `UploadRefreshWorker.kt`.
- `Extractor.init(container.okHttpClient)` must run once at startup (`LibrePipeApp.onCreate`); any call before init throws `IllegalStateException`.
- ANDROID-client player responses return **plain pre-signed stream URLs — no JS signature/nsig deobfuscation is implemented or needed**. If YouTube starts returning `signatureCipher`/PoToken errors, the WEB-client fallback (plus deobfuscation) is the known escalation path.
- App-owned serializable models are `StreamRef`/`ChannelRef`/`PlaylistRef` in `data/model/Models.kt` (used by Room, history, notifications). They are distinct from `data/youtube` types — don't merge them.
- DI is a hand-rolled `AppContainer` (no Hilt): `(applicationContext as LibrePipeApp).container`.
- Room: `AppDatabase` has `exportSchema = false` and `fallbackToDestructiveMigration()` — schema changes silently wipe user data; bump `version` and add a real migration for anything user-facing.
- Channel URLs may be `@handle` style; `Innertube.resolveChannelId()` resolves via HTML as fallback.
- **Design tokens live in `ui/theme/`** (source of truth: `design/Librepipe 01 Foundations.dc.html`, gitignored): `color/Light.kt`+`Dark.kt` (board hexes — everything not on the board was derived per M3 conventions), `color/ExtendedColors.kt` (success + player scrim, outside M3's ColorScheme), `Typography.kt` (IBM Plex Sans all roles, Plex Mono `numeric` with `tnum`), `Shape.kt`, `Spacing.kt` (nine values only), `Motion.kt` (≤500ms, board easings). Fonts are bundled in `res/font/` (OFL-1.1, license in `res/raw/plex_ofl.txt`) — never swap to runtime font loading.
- **Dynamic color**: `LibrePipeTheme` defaults `dynamicColor = true`; after `dynamic{Light,Dark}ColorScheme(context)` it copies the **fixed** error family back (board 02: "semantic roles stay fixed"). success/playerScrim come from `LocalExtendedColors` and are always fixed. Board rule: at most one filled accent object per viewport.
- Launcher icon is the brand mark (sky-blue circle `#4AA8E8` + white triangle) as pure vectors (`ic_launcher_foreground.xml`), `ic_launcher_monochrome.xml` for themed icons, `mipmap-anydpi-v33/` adds the monochrome layer. The mark never adopts dynamic color.

## Behavior that looks like bugs (don't "fix")

- `Extractor.trending()` returns an **empty list for anonymous users** — YouTube removed the global trending kiosk; it now maps to the personalized `FEwhat_to_watch` feed. HomeViewModel handles empties.
- Channel "Videos" tab items have `uploaderName = null` — the new `lockupViewModel` layout omits the uploader row on a channel's own videos.
- `parseMusicItem` uploaderName is the type label ("Song") — WEB_REMIX search lists prepend the type to the artist column; recognized as cosmetic.
- `lint { abortOnError = false }` in `app/build.gradle.kts` is intentional.

## If extraction breaks (API drift)

YouTube's InnerTube API and client versions rotate frequently. When search/player responses change shape:
1. Re-validate with curl: `sw.js` (client version), `youtubei/v1/{search,browse,player}` POSTs — see `Innertube.kt` for exact bodies/headers that were verified working.
2. Check for new renderer keys in the JSON (renderers have migrated before: `playlistRenderer` → `lockupViewModel`, `continuationItemRenderer` → `continuationItemViewModel`).
3. Verify with `./gradlew :app:compileDebugKotlin` after parser changes.

## Notes

- `docs/SECURITY_FLOWS.md` documents the app's security flows (kept current).
- `.freebuff/`, `.kotlin`, `.gradle`, `local.properties`, `app/build` are gitignored — don't commit them.
- Intents: `MainActivity` declares VIEW filters for youtube.com / youtu.be / music.youtube.com domains; the app opens YouTube links by design.
