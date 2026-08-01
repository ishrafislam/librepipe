# NewPipe Extractor keeps a minified copy of Mozilla Rhino and needs these rules
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**

# NewPipe Extractor models
-keep class org.schabi.newpipe.extractor.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
