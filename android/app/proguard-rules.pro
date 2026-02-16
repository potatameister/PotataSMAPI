# Jetpack Compose
-keep class androidx.compose.material3.** { *; }
-keep class androidx.compose.runtime.** { *; }

# General reflection safety
-keepattributes Signature,AnnotationDefault,EnclosingMethod,InnerClasses,SourceFile,LineNumberTable
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# Keep our virtual core classes
-keep class io.potatasmapi.launcher.** { *; }
