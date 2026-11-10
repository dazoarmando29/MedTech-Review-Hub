# MedTech Review Hub — ProGuard / R8 rules
# Keep everything that is looked up reflectively (Retrofit, Moshi, kotlinx.serialization).

# ---- Kotlin ----
-dontwarn kotlin.**
-dontwarn kotlinx.**
-keepclassmembers class **$WhenMappings { <fields>; }
-keepclassmembers class kotlin.Metadata { public <methods>; }

# ---- kotlinx.serialization ----
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.medtech.reviewhub.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---- Moshi (reflection via KotlinJsonAdapterFactory) ----
-keep class com.medtech.reviewhub.data.network.dto.** { *; }
-keep class com.medtech.reviewhub.data.content.** { *; }
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keep class kotlin.reflect.jvm.internal.impl.** { *; }

# ---- Retrofit ----
-keepattributes Signature, Exceptions
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# ---- Room ----
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

# ---- Enum safety ----
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ---- App models kept for export/JSON round-trips ----
-keep class com.medtech.reviewhub.domain.model.** { *; }
