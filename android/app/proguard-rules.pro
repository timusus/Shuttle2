# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /Users/tim/Library/Android/sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile

# Custom Exceptions
-keep public class * extends java.lang.Exception

# Keep all Shuttle model classes
-keep class com.simplecityapps.shuttle.model.** { *; }
-keep class com.simplecityapps.shuttle.query.** { *; }
-keep class com.simplecityapps.shuttle.sorting.** { *; }


# KotlinX Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt # core serialization annotations

# kotlinx-serialization-json specific. Add this if you have java.lang.NoClassDefFoundError kotlinx.serialization.json.JsonObjectSerializer
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Change here com.yourcompany.yourpackage
-keep,includedescriptorclasses class com.simplecityapps.shuttle.**$$serializer { *; }
-keepclassmembers class com.simplecityapps.shuttle.** {
    *** Companion;
}
-keepclasseswithmembers class com.simplecityapps.shuttle.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# AppInitializers names each initializer's trace section ("S2 init <Name>") after its class, which the startup
# benchmark measures (docs/performance/android-startup.md)
-keepnames class * implements com.simplecityapps.shuttle.appinitializers.AppInitializer
