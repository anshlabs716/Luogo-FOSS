# Luogo-FOSS R8 / ProGuard configuration
#
# Principle: R8 removes genuinely unused code and resources. Nothing here disables an
# optimisation to make the APK smaller, and nothing here keeps a feature alive that does
# not work.

# --- Kotlin / coroutines -------------------------------------------------
-keepattributes *Annotation*,InnerClasses,Signature,EnclosingMethod,SourceFile,LineNumberTable
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# --- kotlinx.serialization ----------------------------------------------
# Serializers are generated as companions and looked up reflectively.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class app.luogo.app.** {
    *** Companion;
}
-keepclasseswithmembers class app.luogo.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.luogo.app.**$$serializer { *; }
-keepclassmembers class app.luogo.app.** {
    *** Companion;
}

# --- Room ---------------------------------------------------------------
-keep class app.luogo.app.data.db.** { *; }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# --- Domain models ------------------------------------------------------
# Serialized to JSON for the relay protocol; field names are part of that contract.
-keep class app.luogo.app.domain.model.** { *; }

# --- Bouncy Castle ------------------------------------------------------
# Ed25519 is used for device identity signing. These classes are reached through
# reflection by the JCE provider lookup, so they must survive shrinking.
-dontwarn org.bouncycastle.**
-keep class org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters { *; }
-keep class org.bouncycastle.crypto.params.Ed25519PublicKeyParameters { *; }
-keep class org.bouncycastle.crypto.signers.Ed25519Signer { *; }
-keep class org.bouncycastle.crypto.util.** { *; }
-keep class org.bouncycastle.util.encoders.** { *; }
-keep class org.bouncycastle.jce.provider.BouncyCastleProvider { *; }

# --- Android Auto -------------------------------------------------------
# The car app host instantiates these by name from the manifest.
-keep class app.luogo.app.auto.** { *; }

# --- Android components -------------------------------------------------
-keep class app.luogo.app.service.** { *; }
-keep class app.luogo.app.LuogoApplication { *; }
-keep class app.luogo.app.MainActivity { *; }

# --- ZXing (QR invite codes) --------------------------------------------
-dontwarn com.google.zxing.**
-keep class com.google.zxing.** { *; }

# --- OkHttp -------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
# OkHttp references optional platform providers that are absent on Android.
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.openjsse.**