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
 * VirtualLauncher: The Final Bridge (Enhanced Logging).
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
            PotataApp.addLog("Target Activity: ${activityName ?: "Auto-detect"}")
            
            val dexPath = allApks.joinToString(File.pathSeparator) { it.absolutePath }
            val optimizedDexPath = File(context.codeCacheDir, "opt_dex").apply { mkdirs() }.absolutePath
            val nativeLibPath = libDir.absolutePath

            // 1. Create ClassLoader
            PotataApp.addLog("Initializing ClassLoader with ${allApks.size} segments...")
            val classLoader = DexClassLoader(dexPath, optimizedDexPath, nativeLibPath, context.classLoader)

            val targetActivity = activityName ?: "com.chucklefish.stardewvalley.StardewValley"
            
            // 2. Setup Redirection (Environment)
            try {
                // Point MONO_PATH to the SD card where assets/Content and assemblies live
                val assemblyDir = File(sdcardRoot, "assemblies")
                android.system.Os.setenv("MONO_PATH", sdcardRoot.absolutePath, true)
                android.system.Os.setenv("MONO_GAC_PREFIX", sdcardRoot.absolutePath, true)
                android.system.Os.setenv("SMAPI_ANDROID_BASE_DIR", sdcardRoot.absolutePath, true)
                android.system.Os.setenv("HOME", sdcardRoot.absolutePath, true)
                PotataApp.addLog("Native environment redirected to SD Card.")
            } catch (e: Exception) {
                PotataApp.addLog("Env Error: ${e.message}")
            }

            // 3. Inject Hooks
            PotataApp.addLog("Injecting system hooks...")
            injectVirtualLoader(classLoader, allApks[0].absolutePath, nativeLibPath, virtualRoot.absolutePath)
            injectInstrumentation(classLoader)
            injectVirtualResources(dexPath)

            // 4. Start Activity
            (context as Activity).runOnUiThread {
                try {
                    val intent = Intent().apply {
                        setClassName(context.packageName, targetActivity)
                        putExtra("VIRTUAL_MODE", true)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    PotataApp.addLog("Firing Intent for $targetActivity...")
                    context.startActivity(intent)
                    onComplete()
                    PotataApp.addLog("Intent delivered to Android System.")
                } catch (e: Exception) {
                    PotataApp.addLog("FATAL: Intent delivery failed: ${e.message}")
                    onComplete()
                }
            }

        } catch (e: Exception) {
            PotataApp.addLog("CRITICAL LAUNCH ERROR: ${e.message}")
            Log.e(TAG, "Launch error", e)
            (context as Activity).runOnUiThread { onComplete() }
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
            
            loadedApkClass.getDeclaredField("mClassLoader").apply { isAccessible = true }.set(loadedApk, classLoader)
            loadedApkClass.getDeclaredField("mAppDir").apply { isAccessible = true }.set(loadedApk, baseApk)
            loadedApkClass.getDeclaredField("mResDir").apply { isAccessible = true }.set(loadedApk, baseApk)
            loadedApkClass.getDeclaredField("mLibDir").apply { isAccessible = true }.set(loadedApk, libDir)
            loadedApkClass.getDeclaredField("mDataDir").apply { isAccessible = true }.set(loadedApk, dataDir)
            
            PotataApp.addLog("LoadedApk Hook: SUCCESS")
        } catch (e: Exception) { 
            PotataApp.addLog("LoadedApk Hook: FAILED (${e.message})")
        }
    }

    @SuppressLint("DiscouragedPrivateApi")
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
            
            loadedApkClass.getDeclaredField("mSplitResDirs").apply { isAccessible = true }.set(loadedApk, apkPaths)
            loadedApkClass.getDeclaredField("mResources").apply { isAccessible = true }.set(loadedApk, null)
            PotataApp.addLog("Resource Hook: SUCCESS")
        } catch (e: Exception) {
            PotataApp.addLog("Resource Hook: FAILED")
        }
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
                PotataApp.addLog("Instrumentation Hook: SUCCESS")
            }
        } catch (e: Exception) {
            PotataApp.addLog("Instrumentation Hook: FAILED")
        }
    }

    private class PotataInstrumentation(private val base: Instrumentation, private val classLoader: ClassLoader) : Instrumentation() {
        override fun newActivity(cl: ClassLoader?, className: String?, intent: Intent?): Activity {
            PotataApp.addLog("System creating activity: $className")
            return base.newActivity(classLoader, className, intent)
        }

        override fun callActivityOnCreate(activity: Activity, icicle: Bundle?) {
            val name = activity.javaClass.name
            PotataApp.addLog("Activity.onCreate hit: $name")
            
            if (name.contains("chucklefish")) {
                try {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    
                    val spoofedContext = object : ContextWrapper(activity.baseContext) {
                        override fun getPackageName(): String = "com.chucklefish.stardewvalley"
                        override fun getExternalFilesDir(type: String?): File? = File("/sdcard/PotataSMAPI/Files")
                        override fun getFilesDir(): File = File("/sdcard/PotataSMAPI/Internal")
                        override fun getAssets(): AssetManager = activity.assets
                    }
                    
                    val mBaseField = ContextWrapper::class.java.getDeclaredField("mBase")
                    mBaseField.isAccessible = true
                    mBaseField.set(activity, spoofedContext)
                    
                    PotataApp.addLog("Identity Hijack: SUCCESS for $name")
                    android.widget.Toast.makeText(activity, "POTATA SMAPI: FARM LOADING", android.widget.Toast.LENGTH_SHORT).show()
                } catch (e: Exception) { 
                    PotataApp.addLog("Identity Hijack: FAILED for $name")
                }
            }
            base.callActivityOnCreate(activity, icicle)
        }

        override fun onCreate(arguments: Bundle?) { base.onCreate(arguments) }
        override fun onStart() { base.onStart() }
        override fun onDestroy() { base.onDestroy() }
    }
}
