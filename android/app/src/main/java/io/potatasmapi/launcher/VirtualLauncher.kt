package io.potatasmapi.launcher

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.AssetManager
import android.os.Bundle
import android.util.Log
import dalvik.system.DexClassLoader
import java.io.File
import java.lang.reflect.Field

/**
 * VirtualLauncher: The Final Bridge (Version Agnostic).
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
            val classLoader = DexClassLoader(dexPath, optimizedDexPath, nativeLibPath, context.classLoader)

            // 2. Determine Entry Point (Prefer StardewValley over MainActivity)
            var targetActivity = "com.chucklefish.stardewvalley.StardewValley"
            try {
                classLoader.loadClass(targetActivity)
                PotataApp.addLog("Using primary entry: $targetActivity")
            } catch (e: Exception) {
                targetActivity = activityName ?: "com.chucklefish.stardewvalley.MainActivity"
                PotataApp.addLog("Primary entry not found, using fallback: $targetActivity")
            }
            
            // 3. Setup Redirection (Environment)
            try {
                android.system.Os.setenv("MONO_PATH", sdcardRoot.absolutePath, true)
                android.system.Os.setenv("SMAPI_ANDROID_BASE_DIR", sdcardRoot.absolutePath, true)
                android.system.Os.setenv("HOME", sdcardRoot.absolutePath, true)
                PotataApp.addLog("Environment Variables: OK")
            } catch (e: Exception) { PotataApp.addLog("Env Error: ${e.message}") }

            // 4. Manual Native Library Load (Fix for black screen)
            loadNativeEngines(libDir)

            // 5. Inject Hooks
            injectVirtualLoader(classLoader, allApks[0].absolutePath, nativeLibPath, virtualRoot.absolutePath)
            injectInstrumentation(classLoader)
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
                    PotataApp.addLog("Launch Signal Sent.")
                } catch (e: Exception) {
                    PotataApp.addLog("Intent Failed: ${e.message}")
                    onComplete()
                }
            }

        } catch (e: Exception) {
            PotataApp.addLog("CRITICAL ERROR: ${e.message}")
            (context as Activity).runOnUiThread { onComplete() }
        }
    }

    /**
     * Manually loads the native Mono/Xamarin engines from our virtual folder.
     * This fixes the black screen on devices where LoadedApk hook fails.
     */
    private fun loadNativeEngines(libDir: File) {
        val engines = listOf("libmonodroid.so", "libmonosgen-2.0.so", "libxamarin-app.so")
        engines.forEach { name ->
            val file = File(libDir, name)
            if (file.exists()) {
                try {
                    System.load(file.absolutePath)
                    PotataApp.addLog("Engine $name: Loaded")
                } catch (e: Throwable) {
                    PotataApp.addLog("Engine $name: Skip (${e.message})")
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
            
            // Version-agnostic field mapping
            val fieldMap = mapOf(
                "mClassLoader" to classLoader,
                "mAppDir" to baseApk,
                "mResDir" to baseApk,
                "mDataDir" to dataDir,
                "mLibDir" to libDir,
                "mLibPath" to libDir, // Alternate name on some versions
                "mDir" to baseApk      // Alternate name on some versions
            )

            for ((name, value) in fieldMap) {
                try {
                    val field = loadedApkClass.getDeclaredField(name)
                    field.isAccessible = true
                    field.set(loadedApk, value)
                    PotataApp.addLog("Field $name: Redirected")
                } catch (e: Exception) {} // Ignore missing fields
            }
        } catch (e: Exception) { 
            PotataApp.addLog("LoadedApk Hook Failed: ${e.message}")
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
                loadedApkClass.getDeclaredField("mSplitResDirs").apply { isAccessible = true }.set(loadedApk, apkPaths)
                loadedApkClass.getDeclaredField("mResources").apply { isAccessible = true }.set(loadedApk, null)
                PotataApp.addLog("Resource Hook: OK")
            } catch (e: Exception) { PotataApp.addLog("Resource Hook: Skip") }
        } catch (e: Exception) {}
    }

    private fun injectInstrumentation(classLoader: ClassLoader) {
        try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentActivityThread = activityThreadClass.getDeclaredMethod("currentActivityThread").invoke(null)
            val mInstrumentationField = activityThreadClass.getDeclaredField("mInstrumentation")
            mInstrumentationField.isAccessible = true
            val base = mInstrumentationField.get(currentActivityThread) as Instrumentation
            if (base !is PotataInstrumentation) {
                mInstrumentationField.set(currentActivityThread, PotataInstrumentation(base, classLoader))
                PotataApp.addLog("Instrumentation Hook: OK")
            }
        } catch (e: Exception) {}
    }

    private class PotataInstrumentation(private val base: Instrumentation, private val classLoader: ClassLoader) : Instrumentation() {
        override fun newActivity(cl: ClassLoader?, className: String?, intent: Intent?): Activity {
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
                    }
                    ContextWrapper::class.java.getDeclaredField("mBase").apply { isAccessible = true }.set(activity, spoofedContext)
                    PotataApp.addLog("Context Spoofed: OK")
                } catch (e: Exception) {}
            }
            base.callActivityOnCreate(activity, icicle)
        }

        override fun onCreate(arguments: Bundle?) { base.onCreate(arguments) }
        override fun onStart() { base.onStart() }
        override fun onDestroy() { base.onDestroy() }
    }
}
