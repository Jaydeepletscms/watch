# ProGuard / R8 rules for the :videohub-android library release build.
# Consumer rules are shipped via consumer-rules.pro; this file applies to the
# library itself when it is compiled with minification enabled.

-keep class com.videohub.client.** { public *; }

-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# kotlinx.serialization companion serializers
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
