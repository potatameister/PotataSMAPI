package io.potatasmapi.launcher

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.res.AssetManager
import android.os.Bundle
import android.util.Log
import dalvik.system.DexClassLoader
import java.io.File
import java.lang.reflect.Field

/**
 * VirtualLauncher: The Final Bridge (Version Agnostic + Deep Trace).
 */
class VirtualLauncher(private val context: Context) {
    private val TAG = "PotataLauncher"

    fun launch(activityName: String?, onComplete: () -> Unit) {
        try {
            val virtualRoot = File(context.filesDir, "virtual/stardew")
            val libDir = File(virtualRoot, "lib")
            val sdcardRoot = File("/sdcard/PotataSMAPI")
            
            if (!File(virtualRoot, "virtual.ready").exists()) {
                throw Exception("Environment not ready.")
            }

            val allApks = virtualRoot.listFiles()?.filter { it.name.endsWith(".apk") } ?: emptyList()
            if (allApks.isEmpty()) throw Exception("No source APKs.")

            PotataApp.addLog("--- LAUNCH SEQUENCE START ---")
            
            val dexPath = allApks.joinToString(File.pathSeparator) { it.absolutePath }
            val optimizedDexPath = File(context.codeCacheDir, "opt_dex").apply { mkdirs() }.absolutePath
            val nativeLibPath = libDir.absolutePath

            // 1. Create ClassLoader
            PotataApp.addLog("Initializing ClassLoader...")
            val classLoader = DexClassLoader(dexPath, optimizedDexPath, nativeLibPath, context.classLoader)

            // 2. Determine Entry Point
            val entries = listOf("com.chucklefish.stardewvalley.StardewValley", "com.chucklefish.stardewvalley.MainActivity")
            var targetActivity = ""
            for (entry in entries) {
                try {
                    classLoader.loadClass(entry)
                    targetActivity = entry
                    PotataApp.addLog("Detected entry point: $targetActivity")
                    break
                } catch (e: Exception) {}
            }
            if (targetActivity.isEmpty()) targetActivity = activityName ?: entries[0]
            
            // 3. Setup Redirection (Environment)
            try {
                android.system.Os.setenv("MONO_PATH", sdcardRoot.absolutePath, true)
                android.system.Os.setenv("SMAPI_ANDROID_BASE_DIR", sdcardRoot.absolutePath, true)
                android.system.Os.setenv("HOME", sdcardRoot.absolutePath, true)
                PotataApp.addLog("Environment: REDIRECTED")
            } catch (e: Exception) { PotataApp.addLog("Env Error: ${e.message}") }

            // 4. Manual Native Library Load (Ordered for dependencies)
            loadNativeEngines(libDir)

            // 5. Inject Hooks
            injectVirtualLoader(classLoader, allApks[0].absolutePath, nativeLibPath, virtualRoot.absolutePath)
            injectInstrumentation(classLoader, allApks[0].absolutePath)
            injectVirtualResources(dexPath)

            // 6. Start Activity
            (context as Activity).runOnUiThread {
                try {
                    val intent = Intent().apply {
                        setClassName(context.packageName, targetActivity)
                        putExtra("VIRTUAL_MODE", true)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    onComplete()
                    PotataApp.addLog("--- LAUNCH SIGNAL SENT ---")
                } catch (e: Exception) {
                    PotataApp.addLog("Launch Failed: ${e.message}")
                    onComplete()
                }
            }

        } catch (e: Exception) {
            PotataApp.addLog("CRITICAL ERROR: ${e.message}")
            (context as Activity).runOnUiThread { onComplete() }
        }
    }

    private fun loadNativeEngines(libDir: File) {
        // Load in order of dependency
        val engines = listOf("libmonosgen-2.0.so", "libmonodroid.so", "libxamarin-app.so")
        engines.forEach { name ->
            val file = File(libDir, name)
            if (file.exists()) {
                try {
                    System.load(file.absolutePath)
                    PotataApp.addLog("Native Engine $name: LOADED")
                } catch (e: Throwable) {
                    PotataApp.addLog("Native Engine $name: FAIL (${e.message})")
                }
            }
        }
    }

    @SuppressLint("DiscouragedPrivateApi")
    private fun injectVirtualLoader(classLoader: ClassLoader, baseApk: String, libDir: String, dataDir: String) {
        try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentActivityThread = activityThreadClass.getDeclaredMethod("currentActivityThread").invoke(null)
            val mPackagesField = activityThreadClass.getDeclaredField("mPackages")
            mPackagesField.isAccessible = true
            val mPackages = mPackagesField.get(currentActivityThread) as MutableMap<String, *>
            val loadedApkWeakRef = mPackages[context.packageName] as java.lang.ref.WeakReference<*>
            val loadedApk = loadedApkWeakRef.get() ?: return
            val loadedApkClass = Class.forName("android.app.LoadedApk")
            
            // Comprehensive field scan
            val fields = loadedApkClass.declaredFields
            for (field in fields) {
                field.isAccessible = true
                val name = field.name
                try {
                    when (name) {
                        "mClassLoader" -> field.set(loadedApk, classLoader)
                        "mAppDir", "mDir" -> field.set(loadedApk, baseApk)
                        "mResDir" -> field.set(loadedApk, baseApk)
                        "mDataDir" -> field.set(loadedApk, dataDir)
                        "mLibDir", "mLibPath" -> field.set(loadedApk, libDir)
                        "mPackageName" -> field.set(loadedApk, "com.chucklefish.stardewvalley")
                    }
                    if (name in listOf("mClassLoader", "mAppDir", "mResDir", "mDataDir", "mLibDir", "mPackageName")) {
                        PotataApp.addLog("System Hook: $name -> REDIRECTED")
                    }
                } catch (e: Exception) {}
            }
        } catch (e: Exception) { 
            PotataApp.addLog("System Hook: GLOBAL FAIL (${e.message})")
        }
    }

    private fun injectVirtualResources(dexPath: String) {
        try {
            val apkPaths = dexPath.split(File.pathSeparator).toTypedArray()
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentActivityThread = activityThreadClass.getDeclaredMethod("currentActivityThread").invoke(null)
            val mPackagesField = activityThreadClass.getDeclaredField("mPackages")
            mPackagesField.isAccessible = true
            val mPackages = mPackagesField.get(currentActivityThread) as MutableMap<String, *>
            val loadedApkWeakRef = mPackages[context.packageName] as java.lang.ref.WeakReference<*>
            val loadedApk = loadedApkWeakRef.get() ?: return
            val loadedApkClass = Class.forName("android.app.LoadedApk")
            
            try {
                val mSplitResDirsField = loadedApkClass.getDeclaredField("mSplitResDirs")
                mSplitResDirsField.isAccessible = true
                mSplitResDirsField.set(loadedApk, apkPaths)
                
                val mResourcesField = loadedApkClass.getDeclaredField("mResources")
                mResourcesField.isAccessible = true
                mResourcesField.set(loadedApk, null)
                PotataApp.addLog("Resource Hook: ACTIVE")
            } catch (e: Exception) { PotataApp.addLog("Resource Hook: PARTIAL") }
        } catch (e: Exception) {}
    }

    private fun injectInstrumentation(classLoader: ClassLoader, baseApk: String) {
        try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentActivityThread = activityThreadClass.getDeclaredMethod("currentActivityThread").invoke(null)
            val mInstrumentationField = activityThreadClass.getDeclaredField("mInstrumentation")
            mInstrumentationField.isAccessible = true
            val base = mInstrumentationField.get(currentActivityThread) as Instrumentation
            if (base !is PotataInstrumentation) {
                mInstrumentationField.set(currentActivityThread, PotataInstrumentation(base, classLoader, baseApk))
                PotataApp.addLog("Instrumentation: HOOKED")
            }
        } catch (e: Exception) {}
    }

    private class PotataInstrumentation(private val base: Instrumentation, private val classLoader: ClassLoader, private val baseApk: String) : Instrumentation() {
        override fun newActivity(cl: ClassLoader?, className: String?, intent: Intent?): Activity {
            PotataApp.addLog("Activity Factory: $className")
            return base.newActivity(classLoader, className, intent)
        }

        override fun callActivityOnCreate(activity: Activity, icicle: Bundle?) {
            if (activity.javaClass.name.contains("chucklefish")) {
                try {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    val spoofedContext = object : ContextWrapper(activity.baseContext) {
                        override fun getPackageName(): String = "com.chucklefish.stardewvalley"
                        override fun getExternalFilesDir(type: String?): File? = File("/sdcard/PotataSMAPI/Files")
                        override fun getFilesDir(): File = File("/sdcard/PotataSMAPI/Internal")
                        override fun getAssets(): AssetManager = activity.assets
                        
                        override fun getApplicationInfo(): ApplicationInfo {
                            val info = super.getApplicationInfo()
                            info.packageName = "com.chucklefish.stardewvalley"
                            info.publicSourceDir = baseApk
                            info.sourceDir = baseApk
                            return info
                        }
                    }
                    val mBaseField = ContextWrapper::class.java.getDeclaredField("mBase")
                    mBaseField.isAccessible = true
                    mBaseField.set(activity, spoofedContext)
                    PotataApp.addLog("Activity Hijack: SUCCESS")
                } catch (e: Exception) { PotataApp.addLog("Activity Hijack: FAIL") }
            }
            base.callActivityOnCreate(activity, icicle)
        }

        override fun onCreate(arguments: Bundle?) { base.onCreate(arguments) }
        override fun onStart() { base.onStart() }
        override fun onDestroy() { base.onDestroy() }
    }
}
