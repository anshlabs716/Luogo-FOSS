# Luogo-FOSS R8 / ProGuard rules
-keepattributes *Annotation*,InnerClasses,Signature,EnclosingMethod
-dontwarn org.bouncycastle.**
-keep class app.luogo.app.data.db.** { *; }
-keep class app.luogo.app.domain.model.** { *; }
-keep class app.luogo.app.auto.** { *; }
