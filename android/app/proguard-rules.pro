# Apktool / brut dependencies
-keep class brut.androlib.Config {
    public *;
}
-keep class brut.androlib.** { *; }
-keep class brut.common.ExtFile { *; }
-keep class brut.androlib.res.data.** { *; }
-keep class brut.j.dir.** { *; }
-keep class brut.common.** { *; }
-keep class brut.util.** { *; }
-keep class brut.directory.** { *; }

# Smali / Dexlib2
-keep class com.android.tools.smali.** { *; }
-keep class org.jf.dexlib2.** { *; }
-keep class org.jf.util.** { *; }

# Antlr - Allow obfuscation but keep structure
-keep,allowobfuscation class org.antlr.** { *; }

# Guava - Be more aggressive, only keep what's referenced
-keep,allowobfuscation class com.google.common.** { *; }
-dontwarn com.google.common.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**

# Apache Commons - Allow obfuscation
-keep,allowobfuscation class org.apache.commons.** { *; }
-dontwarn org.apache.commons.**

# YAML
-keep class org.yaml.snakeyaml.** { *; }
-dontwarn org.yaml.snakeyaml.**

# Ignore missing desktop GUI classes (Swing/AWT/ImageIO)
-dontwarn javax.swing.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.xml.stream.**
-dontwarn java.beans.**
-dontwarn com.sun.source.**
-dontwarn javax.annotation.processing.**
-dontwarn org.stringtemplate.v4.gui.**
-dontwarn brut.androlib.res.decoder.**

# General reflection safety
-keepattributes Signature,AnnotationDefault,EnclosingMethod,InnerClasses,SourceFile,LineNumberTable
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
