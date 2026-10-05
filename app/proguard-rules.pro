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
# Ed25519 signs the device identity, and the encoders handle Base64. Nothing else in this
# ~8 MB provider is reachable from that.
#
# Only the specific classes used are kept. An earlier rule kept crypto.util.** and
# BouncyCastleProvider, which dragged in the JCE provider machinery the app never touches:
# nothing in the source calls Security.addProvider, so provider registration is dead weight.
#
# The JCE provider also ships post-quantum (org.bouncycastle.pqc) and elliptic-curve
# (org.bouncycastle.math.ec) implementations. Shrinking measured 188 KB of dex code in those
# two packages alone, none of it used. They are removed explicitly rather than left to R8,
# because the Ed25519 key classes reach them reflectively.
-dontwarn org.bouncycastle.**
-keep class org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters { *; }
-keep class org.bouncycastle.crypto.params.Ed25519PublicKeyParameters { *; }
-keep class org.bouncycastle.crypto.signers.Ed25519Signer { *; }
-keep class org.bouncycastle.util.encoders.Base64 { *; }

# Nothing below is referenced by this app, and no keep rule above preserves it, so R8 removes
# the post-quantum, elliptic-curve, ASN.1 and X.509 implementations. The unit test
# "encryption engine round-trips ... Ed25519 signatures" fails if this is over-trimmed, so the
# reduction is checked rather than assumed.

# --- Android Auto -------------------------------------------------------
# The car app host instantiates these by name from the manifest.
-keep class app.luogo.app.auto.** { *; }

# --- Android components -------------------------------------------------
-keep class app.luogo.app.service.** { *; }
-keep class app.luogo.app.LuogoApplication { *; }
-keep class app.luogo.app.MainActivity { *; }

# --- ZXing (QR invite codes) --------------------------------------------
# Only QR codes are generated, for invite links. The earlier rule kept all of com.google.zxing,
# which retained the PDF417 and Code128 encoders: about 106 KB of dex code for barcode formats
# this app never produces.
-dontwarn com.google.zxing.**
-keep class com.google.zxing.qrcode.QRCodeWriter { *; }
-keep class com.google.zxing.qrcode.decoder.Decoder { *; }
-keep class com.google.zxing.BarcodeFormat { *; }
-keep class com.google.zxing.EncodeHintType { *; }

# --- OkHttp -------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
# OkHttp references optional platform providers that are absent on Android.
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.openjsse.**