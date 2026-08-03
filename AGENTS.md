# AGENTS.md

LibrePipe — single-module Android app (`:app`), Kotlin + Jetpack Compose (Material3), package/namespace `app.librepipes`, minSdk 26 / compileSdk 36 / Java 17. Privacy-focused YouTube client: no login, no ads, no Google Play Services.

## Build & verify

- Typecheck (fast, use after any code change): `./gradlew :app:compileDebugKotlin`
- Full build: `./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
- Lint: `./gradlew :app:lintDebug` — `abortOnError = false`, never blocks; still worth reviewing
- Unit tests (Parsers only): `./gradlew :app:testDebugUnitTest` — JUnit 4 against **live API fixtures** in `app/src/test/resources/fixtures/` (captured 2026-08-02, one per response shape: legacy `videoRenderer`, `lockupViewModel`, `playlistHeaderRenderer` classic + `pageHeaderRenderer` new, ANDROID player, WEB_REMIX music, empty anonymous home feed, plus `watch_chapters.html` for chapter-marker extraction). Expected values are hardcoded from the fixtures at capture time; re-capture a fixture and update the assertions when YouTube drifts. There are no instrumented tests and no CI.
- Tests need `org.json:json` on the test classpath (`testImplementation(libs.orgjson)`) — android.jar's org.json stubs throw in host JVM tests. Do NOT add it to main; `org.json` is Android's built-in.
- JDK 21 is installed; Gradle toolchain targets Java 17. Core library desugaring is enabled — keep it for `java.time`/`java.util.function` on minSdk 26.
- Add dependencies via `gradle/libs.versions.toml` (version catalog), never inline.
- JDK versions: AGP 8.13.2, Kotlin 2.3.10, KSP 2.3.10 (Room codegen).

## Architecture (hard-earned, not obvious)

- **There is no NewPipeExtractor.** Extraction is fully native in `app/src/main/java/app/librepipes/data/youtube/`:
  - `Innertube.kt` — HTTP client for YouTube's InnerTube JSON API (WEB client for search/browse, ANDROID client 20.05.42 for the player endpoint, WEB_REMIX for Music search). Client version is bootstrapped from `sw.js` with a fallback constant.
  - `Parsers.kt` — JSON→model parsing for legacy renderers (`videoRenderer`, `channelRenderer`) and the 2025+ `lockupViewModel` layout, continuation tokens, subtitles, format classification by codec strings, plus watch-page `ytInitialPlayerResponse` extraction + chapter markers.
  - `YoutubeModels.kt` — `StreamInfo`/`StreamFormat`/`SubtitleTrack`/`StreamType`/`Chapter` (playback & download types).
- **`Extractor` object (`data/extractor/Extractor.kt`) is the public facade** every ViewModel and worker calls (`search`, `suggestions`, `stream`, `channel`, `playlist`, `trending`, `chapters`, feeds with `loadInitial()`/`loadMore()`). Keep its API stable when touching the engine — callers: `MainViewModels.kt`, `SearchScreen.kt`, `Playback.kt`, `NowPlayingActivity.kt`, `DownloadWorker.kt`, `UploadRefreshWorker.kt`.
- **Chapter markers ship in the watch page HTML, not the player API**: `Extractor.chapters(url)` fetches `https://www.youtube.com/watch?v=…` (WEB UA) and pulls `ytInitialPlayerResponse.playerOverlays → playerOverlayRenderer → decoratedPlayerBarRenderer → playerBar → multiMarkersPlayerBarRenderer → markersMap` under the `DESCRIPTION_CHAPTERS` key (was `CHAPTER_MARKERS`). `Innertube.chapters()` computes nothing else but returns start-seconds; NowPlaying converts them to seek-bar tick fractions (`duration` from the player). Watch pages are heavy, so results are cached (bounded LRU) in `InnertubeClient`.
- `Extractor.init(container.okHttpClient)` must run once at startup (`LibrePipeApp.onCreate`); any call before init throws `IllegalStateException`.
- ANDROID-client player responses return **plain pre-signed stream URLs — no JS signature/nsig deobfuscation is implemented or needed**. If YouTube starts returning `signatureCipher`/PoToken errors, the WEB-client fallback (plus deobfuscation) is the known escalation path.
- App-owned serializable models are `StreamRef`/`ChannelRef`/`PlaylistRef` in `data/model/Models.kt` (used by Room, history, notifications). They are distinct from `data/youtube` types — don't merge them.
- DI is a hand-rolled `AppContainer` (no Hilt): `(applicationContext as LibrePipeApp).container`.
- Room: `AppDatabase` has `exportSchema = false` and `fallbackToDestructiveMigration()` — schema changes silently wipe user data; bump `version` and add a real migration for anything user-facing.
- Channel URLs may be `@handle` style; `Innertube.resolveChannelId()` resolves via HTML as fallback.
- **Design tokens live in `ui/theme/`** (source of truth: `design/Librepipe 01 Foundations.dc.html`, gitignored): `color/Light.kt`+`Dark.kt` (board hexes — everything not on the board was derived per M3 conventions), `color/ExtendedColors.kt` (success + player scrim, outside M3's ColorScheme), `Typography.kt` (IBM Plex Sans all roles, Plex Mono `numeric` with `tnum`), `Shape.kt`, `Spacing.kt` (nine values only), `Motion.kt` (≤500ms, board easings). Fonts are bundled in `res/font/` (OFL-1.1, license in `res/raw/plex_ofl.txt`) — never swap to runtime font loading.
- **Dynamic color**: `LibrePipeTheme` defaults `dynamicColor = true`; after `dynamic{Light,Dark}ColorScheme(context)` it copies the **fixed** error family back (board 02: "semantic roles stay fixed"). success/playerScrim come from `LocalExtendedColors` and are always fixed. Board rule: at most one filled accent object per viewport.
- Launcher icon is the brand mark (sky-blue circle `#4AA8E8` + white triangle) as pure vectors (`ic_launcher_foreground.xml`), `ic_launcher_monochrome.xml` for themed icons, `mipmap-anydpi-v33/` adds the monochrome layer. The mark never adopts dynamic color.
- **Component kit lives in `ui/components/kit/`** (source of truth: `design/Librepipe 02 Components.dc.html`, gitignored): `Buttons.kt` (40dp shape-full labelLarge), `Inputs.kt` (56dp search bar pill / text field shape-sm, switch 52×32, chips radius 8dp NOT pill), `Rows.kt` (no card container — thumbnail is the card; video row thumb 160dp; skeleton 1200ms ±4% luminance), `Navigation.kt` (80dp bottom bar, selected = 64×32 primaryContainer pill + filled icon + 6dp primary unread dot), `Overlays.kt` (sheet 28dp top corners + 32×4 handle + 32% scrim + max 60% height + 400ms; destructive dialog action = error text button, never filled red; context menu corner 8, rows 48, containerHigh, destructive last after divider; snackbar inverseSurface with always-Undo), `Player.kt` (seek played = brand `#4AA8E8` — theme-independent overlay, thumb 12dp / 6×16 dragging, chapter ticks 2×8 white 90%; mini player 72dp surfaceContainer 96×54 thumb; status pills white 8% bg, LIVE `#FF4B3E`).
- **Player kit is wired (Phase 4)**: `NowPlayingActivity.kt` uses `PlayerView` with `useController = false` and Compose chrome — top scrim + metadata/actions, bottom `LpSeekBar` (brand played, chapter tick fractions from `Extractor.chapters`), time labels, `LpStatusPill` LIVE/`Buffering…`/`{height}p`, transport + action rows; tap on the video toggles play/pause. Audio-only mode swaps the old M3 `Slider` for `LpSeekBar`. The queue sheet is a kit `LpSheet` ("Up next") with an "Autoplay next" `LpSwitch` (persisted in `SettingsRepository.AUTOPLAY`; implemented by rolling back `MEDIA_ITEM_TRANSITION_REASON_AUTO` transitions) and "Clear queue" (error text button after divider).
- **Mini player**: `MiniPlayerViewModel` (in `ui/viewmodels/`) observes the playback session and drives `LpMiniPlayer` docked **above** `LpBottomBar` in `MainActivity` (narrow layout: inside the Scaffold `bottomBar` column; wide: under the NavHost). Tap opens `NowPlayingActivity` with `EXTRA_STREAM_JSON` only (no session restart), `×` calls `stop()` + `clearMediaItems()`. Hidden when the session is idle or has no current item.
- Kit is previewed in `ui/debug/ComponentGalleryActivity.kt` (light/dark toggle in the top bar; declared in `src/debug/AndroidManifest.xml` so only debug builds can launch it — do NOT move it to the main manifest).

## Behavior that looks like bugs (don't "fix")

- `Extractor.trending()` returns an **empty list for anonymous users** — YouTube removed the global trending kiosk; it now maps to the personalized `FEwhat_to_watch` feed. HomeViewModel handles empties.
- Channel "Videos" tab items have `uploaderName = null` — the new `lockupViewModel` layout omits the uploader row on a channel's own videos.
- `parseMusicItem` uploaderName is the type label ("Song") — WEB_REMIX search lists prepend the type to the artist column; recognized as cosmetic.
- `lint { abortOnError = false }` in `app/build.gradle.kts` is intentional.
- Search filter chips are rendered whenever the search query is non-blank (suggestions + results share the chip row); tapping a chip runs `vm.search(filter)`. That is what the Maestro flows rely on (they never submit via keyboard).
- `LpSearchBar`'s `BasicTextField` is `singleLine = true` and routes `KeyboardActions.onSearch` — Enter submits the query instead of inserting a newline. Removing `singleLine` breaks IME-search flows and newline-pollutes queries.
- Library is a playlists grid; **History and Downloads are `LpIconAction`s in the Library top bar**, not tabs. They navigate to pushed `Routes.HISTORY`/`Routes.DOWNLOADS`.
- Channel page has Videos/About tabs only — no Playlists tab (`ChannelFeed` has no playlist section).
- Dynamic color is opt-out: `SettingsRepository.DYNAMIC_COLOR` defaults to `true`. With the emulator's wallpaper the rendered palette won't match board hexes until "Dynamic colours" is toggled off in Settings (verified: fixed palette = `#FBFCFE` surface, `#EFF1F4` surfaceContainer, `#D2E7F9` selected pill).
- Unread badge model: `subscriptions.lastCheckedAt > lastVisitedAt` (room COUNT in `SubscriptionDao.observeUnreadCount`). Marked seen = `markAllSeen`/`markChannelSeen`.
- "Autoplay next" (queue sheet) can't use `setPauseAtEndOfMediaItems` — that's ExoPlayer-only and not exposed by `Player`/`MediaController`. It's implemented in `NowPlayingActivity`'s listener: an `onMediaItemTransition` with reason `MEDIA_ITEM_TRANSITION_REASON_AUTO` is rolled back to the previous item and paused when autoplay is off. Manual seeks keep the item.

## If extraction breaks (API drift)

YouTube's InnerTube API and client versions rotate frequently. When search/player responses change shape:
1. Re-validate with curl: `sw.js` (client version), `youtubei/v1/{search,browse,player}` POSTs — see `Innertube.kt` for exact bodies/headers that were verified working.
2. Check for new renderer keys in the JSON (renderers have migrated before: `playlistRenderer` → `lockupViewModel`, `continuationItemRenderer` → `continuationItemViewModel`).
3. Verify with `./gradlew :app:compileDebugKotlin` after parser changes.

## Notes

- `docs/SECURITY_FLOWS.md` documents the app's security flows (kept current).
- `.freebuff/`, `.kotlin`, `.gradle`, `local.properties`, `app/build` are gitignored — don't commit them.
- Intents: `MainActivity` declares VIEW filters for youtube.com / youtu.be / music.youtube.com domains; the app opens YouTube links by design.
