# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep Gson-backed JSON models fully stable across release builds
# (backup export/import + Room JSON converters).
-keep class com.juliacai.apptick.data.AppLimitBackup { *; }
-keep class com.juliacai.apptick.data.BackupAppSettings { *; }
-keep class com.juliacai.apptick.data.AppLimitGroupEntity { *; }
-keep class com.juliacai.apptick.appLimit.AppInGroup { *; }
-keep class com.juliacai.apptick.groups.TimeRange { *; }
-keep class com.juliacai.apptick.groups.AppUsageStat { *; }
