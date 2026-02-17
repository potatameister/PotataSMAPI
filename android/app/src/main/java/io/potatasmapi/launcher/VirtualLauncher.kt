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
 * VirtualLauncher: The Stable Bridge.
 * Runs the game in a controlled sandbox without freezing the launcher.
 */
class VirtualLauncher(private val context: Context) {
    private val TAG = "PotataLauncher"

    fun launch(activityName: String?) {
        try {
            val virtualRoot = File(context.filesDir, "virtual/stardew")
            val libDir = File(virtualRoot, "lib")
            val assetsDir = File(virtualRoot, "assets")
            
            if (!File(virtualRoot, "virtual.ready").exists()) {
                throw Exception("Virtual environment not ready.")
            }

            val allApks = virtualRoot.listFiles()?.filter { it.name.endsWith(".apk") } ?: emptyList()
            if (allApks.isEmpty()) throw Exception("No sterilized APKs found.")
            allApks.forEach { if (it.canWrite()) it.setReadOnly() }

            PotataApp.addLog("Preparing Stable Bridge...")

            val dexPath = allApks.joinToString(File.pathSeparator) { it.absolutePath }
            val optimizedDexPath = File(context.codeCacheDir, "opt_dex").apply { mkdirs() }.absolutePath
            val nativeLibPath = libDir.absolutePath

            // 1. Create the Virtual ClassLoader (Independent)
            val classLoader = DexClassLoader(dexPath, optimizedDexPath, nativeLibPath, context.classLoader)

            val targetActivity = activityName ?: "com.chucklefish.stardewvalley.StardewValley"
            
            // 2. Setup System Hooks (SAFE VERSION)
            System.setProperty("user.dir", "/sdcard/PotataSMAPI")
            System.setProperty("user.home", "/sdcard/PotataSMAPI")
            
            injectInstrumentation(classLoader)
            injectVirtualResources(dexPath)

            // 3. SMAPI Environment
            try {
                android.system.Os.setenv("MONO_PATH", assetsDir.absolutePath, true)
                android.system.Os.setenv("SMAPI_ANDROID_BASE_DIR", "/sdcard/PotataSMAPI", true)
                android.system.Os.setenv("EXTERNAL_STORAGE", "/sdcard/PotataSMAPI", true)
            } catch (e: Exception) {}

            // 4. Launch with Explicit Identity
            val intent = Intent().apply {
                setClassName(context.packageName, targetActivity)
                putExtra("VIRTUAL_MODE", true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            context.startActivity(intent)
            PotataApp.addLog("Bridge Established. Launching Farm...")

        } catch (e: Exception) {
            PotataApp.addLog("Launch Failed: ${e.message}")
            Log.e(TAG, "Launch error", e)
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
            }
        } catch (e: Exception) { Log.e(TAG, "Instrumentation hook failed", e) }
    }

    private class PotataInstrumentation(private val base: Instrumentation, private val classLoader: ClassLoader) : Instrumentation() {
        override fun newActivity(cl: ClassLoader?, className: String?, intent: Intent?): Activity {
            // THE BRIDGE: When Android asks for a game activity, we give it our Virtual Loader
            val loaderToUse = if (className?.contains("chucklefish") == true) classLoader else cl
            val activity = base.newActivity(loaderToUse, className, intent)
            
            try {
                val spoofedContext = object : ContextWrapper(activity.baseContext) {
                    override fun getPackageName(): String = "com.chucklefish.stardewvalley"
                    override fun getExternalFilesDir(type: String?): File? = File("/sdcard/PotataSMAPI/Files")
                    override fun getFilesDir(): File = File("/sdcard/PotataSMAPI/Internal")
                }
                val mBaseField = ContextWrapper::class.java.getDeclaredField("mBase")
                mBaseField.isAccessible = true
                mBaseField.set(activity, spoofedContext)
            } catch (e: Exception) {}
            
            return activity
        }

        override fun callActivityOnCreate(activity: Activity, icicle: Bundle?) {
            try {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                if (activity.javaClass.name.contains("chucklefish")) {
                    android.widget.Toast.makeText(activity, "POTATA: STARTING ENGINE...", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {}
            base.callActivityOnCreate(activity, icicle)
        }

        override fun onCreate(arguments: Bundle?) { base.onCreate(arguments) }
        override fun onStart() { base.onStart() }
        override fun onDestroy() { base.onDestroy() }
    }
}
