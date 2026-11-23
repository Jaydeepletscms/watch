# Consumer ProGuard rules for VideoHub Android SDK.
# LiveKit is used internally — keep its classes from being stripped.

-keep public class dev.videohub.** { *; }
-keep class io.livekit.** { *; }
-dontwarn io.livekit.**
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**
-keep class kotlinx.serialization.** { *; }
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
