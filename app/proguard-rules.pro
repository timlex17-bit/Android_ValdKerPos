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
# ---------------------------------------------------------------------------
# Aturan Valora. Sebelum ini file hanya berisi komentar dan R8 dimatikan.
# ---------------------------------------------------------------------------

# Buang seluruh log verbose/debug/info dari build release. Sebelumnya semua ikut
# terbawa, termasuk payload checkout lengkap dan respons order dari server.
# Warning dan error tetap dipertahankan supaya kegagalan nyata masih terlihat.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Model diparse manual dari org.json (bukan lewat refleksi Gson), jadi tidak
# perlu -keep field. Yang tetap perlu dipertahankan hanya yang disentuh
# framework atau Parcelable.
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Room menghasilkan implementasi turunan dari kelas @Database.
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# Volley memakai refleksi minimal; simpan kelas request bawaannya.
-dontwarn com.android.volley.**

# Glide (modul di-generate lewat annotation processor).
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }

# OkHttp / Okio hanya dipakai Owner Chat.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**

# Enum dipakai lewat valueOf pada beberapa tempat (PrintResult, dsb.)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
