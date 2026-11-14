# Release keep rules (R8 full mode). Most libraries ship consumer rules (Hilt, Room, OkHttp, Coil, Compose);
# the rules below cover what they leave out or what this app reaches by reflection.

# Keep line numbers for readable crash traces, hide source file names.
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# Strip verbose/debug/info logs in release (DlsVerdict and Diagnostics numbers never reach Logcat).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# kotlinx-serialization: keep generated serializers and companions of @Serializable classes.
-keepclassmembers @kotlinx.serialization.Serializable class com.aitherapist.dls.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.aitherapist.dls.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.aitherapist.dls.**$$serializer { *; }
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1>$Companion { kotlinx.serialization.KSerializer serializer(...); }

# Room: entities, DAOs and the generated database implementation.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface *

# SQLCipher: JNI binds Java names from native code.
-keep class net.zetetic.database.** { *; }
-keep class net.sqlcipher.** { *; }
-dontwarn net.zetetic.database.**

# MediaPipe Tasks: JNI, protobuf-lite messages and AutoValue classes are looked up by name.
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn com.google.protobuf.**
-dontwarn com.google.auto.value.**
-dontwarn javax.annotation.processing.**
-dontwarn javax.lang.model.**
-dontwarn com.google.android.datatransport.**

# OkHttp / Okio optional platform integrations.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Coil 3 decoders are found through ServiceLoader.
-keep class * implements coil3.util.DecoderServiceLoaderTarget { *; }
-keep class * implements coil3.util.FetcherServiceLoaderTarget { *; }
-dontwarn coil3.**
