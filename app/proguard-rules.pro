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
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# --- Gson rules ---
# Keep @SerializedName annotations and the fields they apply to
-keepattributes *Annotation*
-keepclassmembers public class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
# Keep constructors of classes that are serialized/deserialized
-keepclassmembers class * {
    public <init>(...);
}
# Keep fields in data/model classes (adjust package name if necessary)
# If your GSON PlaRin Old Java Objects (POJOs) are in a specific package:
-keep class com.starmaurya.whiteboard.data.** { *; }
-keep class com.starmaurya.whiteboard.models.** { *; }
# If you use @Expose, you might need to keep fields annotated with it:
#-keepclassmembers class * {
#    @com.google.gson.annotations.Expose <fields>;
#}

# --- Kotlin Coroutines rules ---
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.coroutines.flow.internal.AbstractSharedFlowKt {
    java.lang.Object[] getBuffer();
}
-keepclassmembernames class kotlinx.coroutines.flow.internal.ChannelFlow {
    kotlinx.coroutines.channels.ReceiveChannel getChannel();
}

# --- Kotlinx Serialization rules (if you were to use it) ---
# -keepclassmembers class **$$serializer { *; }
# -keep class **$$serializer
# -keepnames class kotlinx.serialization.SerializersKt

# --- AndroidX Lifecycle / ViewModel rules ---
-keep class androidx.lifecycle.ViewModel { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Add any other custom rules below
