# Apktool / brut dependencies
-keep class brut.androlib.** { *; }
-keep class brut.androlib.res.data.** { *; }
-keep class brut.j.dir.** { *; }
-keep class brut.common.** { *; }
-keep class brut.util.** { *; }

# Smali / Dexlib2
-keep class com.android.tools.smali.** { *; }
-keep class org.jf.dexlib2.** { *; }
-keep class org.jf.util.** { *; }

# Antlr
-keep class org.antlr.** { *; }

# Guava
-keep class com.google.common.** { *; }
-dontwarn com.google.common.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**

# Apache Commons
-keep class org.apache.commons.** { *; }
-dontwarn org.apache.commons.**

# YAML
-keep class org.yaml.snakeyaml.** { *; }
-dontwarn org.yaml.snakeyaml.**

# General reflection safety
-keepattributes Signature,AnnotationDefault,EnclosingMethod,InnerClasses,SourceFile,LineNumberTable
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
