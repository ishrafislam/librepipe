# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Media3 resolves these factories by reflection from DefaultMediaSourceFactory, so R8
# sees them as unused and strips them. Playback is entirely DASH-based here, so losing
# the DASH factory breaks every video — and only in the release build.
-keep class androidx.media3.exoplayer.dash.DashMediaSource$Factory { *; }
-keep class androidx.media3.exoplayer.hls.HlsMediaSource$Factory { *; }

# WorkManager persists the worker's class name as a string in its own database, so
# renaming one breaks work that a previous install already scheduled — notably the
# periodic subscription refresh.
-keep class app.librepipes.notify.UploadRefreshWorker { <init>(...); }
-keep class app.librepipes.notify.PremiereReminderWorker { <init>(...); }
-keep class app.librepipes.download.DownloadWorker { <init>(...); }
