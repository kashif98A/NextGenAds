# Google Mobile Ads / AdMob SDK ProGuard configuration
-keep public class com.google.android.gms.ads.** {
    public *;
}
-keep public class com.google.ads.** {
    public *;
}
-keep class com.google.android.gms.ads.mediation.** { *; }

# For Mediation Adapters and SDK internal hooks mapping javascript interfaces safely
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Firebase Analytics ProGuard rules
-keep class com.google.firebase.analytics.** { *; }
-dontwarn com.google.firebase.analytics.**

# Firebase Remote Config ProGuard rules
-keep class com.google.firebase.remoteconfig.** { *; }
-dontwarn com.google.firebase.remoteconfig.**

# Hilt & Dagger ProGuard rules
-keep class dagger.hilt.** { *; }
-keep class dagger.internal.codegen.** { *; }
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
}
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep serializable model names preserved if JSON serialization mapping is used
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keep class * implements com.admodule.next.genads.adsImp.data.** { *; }
