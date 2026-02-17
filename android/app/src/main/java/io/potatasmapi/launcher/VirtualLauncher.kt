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
import android.os.Environment
import android.util.Log
import dalvik.system.DexClassLoader
import java.io.File
import java.lang.reflect.Field

/**
 * VirtualLauncher: The Native Engine Hijacker.
 */
class VirtualLauncher(private val context: Context) {
    private val TAG = "PotataLauncher"

    fun launch(activityName: String?, onComplete: () -> Unit) {
        try {
            val virtualRoot = File(context.filesDir, "virtual/stardew")
            val libDir = File(virtualRoot, "lib")
            val sdcardRoot = File(Environment.getExternalStorageDirectory(), "PotataSMAPI")
            
            if (!File(virtualRoot, "virtual.ready").exists()) {
                throw Exception("Environment not ready.")
            }

            PotataApp.addLog("--- ENGINE HIJACK SEQUENCE ---")
            
            val allApks = virtualRoot.listFiles()?.filter { it.name.endsWith(".apk") } ?: emptyList()
            val dexPath = allApks.joinToString(File.pathSeparator) { it.absolutePath }
            val optimizedDexPath = File(context.codeCacheDir, "opt_dex").apply { mkdirs() }.absolutePath
            val nativeLibPath = libDir.absolutePath

            // 1. Create ClassLoader
            val classLoader = DexClassLoader(dexPath, optimizedDexPath, nativeLibPath, context.classLoader)

            // 2. Setup Environment (Aggressive)
            try {
                android.system.Os.setenv("MONO_PATH", sdcardRoot.absolutePath, true)
                android.system.Os.setenv("SMAPI_ANDROID_BASE_DIR", sdcardRoot.absolutePath, true)
                android.system.Os.setenv("HOME", sdcardRoot.absolutePath, true)
                android.system.Os.setenv("EXTERNAL_STORAGE", sdcardRoot.absolutePath, true)
                android.system.Os.setenv("LD_LIBRARY_PATH", nativeLibPath, true)
                PotataApp.addLog("Redirection Env: ACTIVE")
            } catch (e: Exception) {}

            // 3. Manual Native Hijack
            loadPatchedEngines(libDir)

            // 4. System Hooks
            injectSystemRecords(classLoader, allApks[0].absolutePath, nativeLibPath, virtualRoot.absolutePath)
            injectInstrumentation(classLoader, allApks[0].absolutePath)
            injectVirtualResources(dexPath)

            // 5. Entry Discovery
            val targetActivity = detectEntryPoint(classLoader, activityName)

            // 6. Launch
            (context as Activity).runOnUiThread {
                try {
                    val intent = Intent().apply {
                        setClassName(context.packageName, targetActivity)
                        putExtra("VIRTUAL_MODE", true)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    onComplete()
                    PotataApp.addLog("Hijacked Intent Delivered.")
                } catch (e: Exception) {
                    PotataApp.addLog("Intent Failure: ${e.message}")
                    onComplete()
                }
            }

        } catch (e: Exception) {
            PotataApp.addLog("HIJACK FAIL: ${e.message}")
            (context as Activity).runOnUiThread { onComplete() }
        }
    }

    private fun loadPatchedEngines(libDir: File) {
        val engines = listOf("libmonosgen-2.0.so", "libmonodroid.so", "libxamarin-app.so")
        engines.forEach { name ->
            val file = File(libDir, name)
            if (file.exists()) {
                try {
                    System.load(file.absolutePath)
                    PotataApp.addLog("Hijacked $name: LOADED")
                } catch (e: Throwable) {
                    PotataApp.addLog("Hijacked $name: FAIL (${e.message})")
                }
            }
        }
    }

    private fun detectEntryPoint(cl: ClassLoader, preferred: String?): String {
        val options = listOf("com.chucklefish.stardewvalley.StardewValley", "com.chucklefish.stardewvalley.MainActivity")
        for (opt in options) {
            try {
                cl.loadClass(opt)
                PotataApp.addLog("Entry Point: $opt")
                return opt
            } catch (e: Exception) {}
        }
        return preferred ?: options[0]
    }

    @SuppressLint("DiscouragedPrivateApi")
    private fun injectSystemRecords(classLoader: ClassLoader, baseApk: String, libDir: String, dataDir: String) {
        try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentActivityThread = activityThreadClass.getDeclaredMethod("currentActivityThread").invoke(null)
            
            // 1. Process Identity Hijack
            try {
                val mBoundApplicationField = activityThreadClass.getDeclaredField("mBoundApplication")
                mBoundApplicationField.isAccessible = true
                val mBoundApplication = mBoundApplicationField.get(currentActivityThread)
                val infoField = mBoundApplication.javaClass.getDeclaredField("appInfo")
                infoField.isAccessible = true
                val appInfo = infoField.get(mBoundApplication) as ApplicationInfo
                appInfo.packageName = "com.chucklefish.stardewvalley"
                appInfo.dataDir = dataDir
                appInfo.sourceDir = baseApk
                appInfo.publicSourceDir = baseApk
                appInfo.nativeLibraryDir = libDir
                PotataApp.addLog("Process Hook: SUCCESS")
            } catch (e: Exception) {}

            // 2. LoadedApk Swap
            val mPackagesField = activityThreadClass.getDeclaredField("mPackages")
            mPackagesField.isAccessible = true
            val mPackages = mPackagesField.get(currentActivityThread) as MutableMap<String, *>
            val loadedApkWeakRef = mPackages[context.packageName] as java.lang.ref.WeakReference<*>
            val loadedApk = loadedApkWeakRef.get() ?: return
            val loadedApkClass = Class.forName("android.app.LoadedApk")
            
            val fieldMap = mapOf(
                "mClassLoader" to classLoader,
                "mAppDir" to baseApk, "mDir" to baseApk, "mResDir" to baseApk,
                "mDataDir" to dataDir,
                "mLibDir" to libDir, "mLibPath" to libDir,
                "mPackageName" to "com.chucklefish.stardewvalley"
            )

            for ((name, value) in fieldMap) {
                try {
                    val field = loadedApkClass.getDeclaredField(name)
                    field.isAccessible = true
                    field.set(loadedApk, value)
                } catch (e: Exception) {}
            }
            PotataApp.addLog("Package Hook: SUCCESS")
        } catch (e: Exception) { PotataApp.addLog("Global Hook Fail") }
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
            
            loadedApkClass.getDeclaredField("mSplitResDirs").apply { isAccessible = true }.set(loadedApk, apkPaths)
            loadedApkClass.getDeclaredField("mResources").apply { isAccessible = true }.set(loadedApk, null)
            PotataApp.addLog("Resource Hook: SUCCESS")
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
                PotataApp.addLog("Instrumentation Hook: SUCCESS")
            }
        } catch (e: Exception) {}
    }

    private class PotataInstrumentation(private val base: Instrumentation, private val classLoader: ClassLoader, private val baseApk: String) : Instrumentation() {
        override fun newActivity(cl: ClassLoader?, className: String?, intent: Intent?): Activity {
            return base.newActivity(classLoader, className, intent)
        }

        override fun callActivityOnCreate(activity: Activity, icicle: Bundle?) {
            if (activity.javaClass.name.contains("chucklefish")) {
                try {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    val spoofedContext = object : ContextWrapper(activity.baseContext) {
                        override fun getPackageName(): String = "com.chucklefish.stardewvalley"
                        override fun getExternalFilesDir(type: String?): File? = File(Environment.getExternalStorageDirectory(), "PotataSMAPI/Files")
                        override fun getFilesDir(): File = File(Environment.getExternalStorageDirectory(), "PotataSMAPI/Internal")
                        override fun getAssets(): AssetManager = activity.assets
                        override fun getApplicationInfo(): ApplicationInfo {
                            val info = super.getApplicationInfo()
                            info.packageName = "com.chucklefish.stardewvalley"
                            info.sourceDir = baseApk
                            info.publicSourceDir = baseApk
                            info.nativeLibraryDir = File(baseApk).parentFile?.absolutePath + "/lib"
                            return info
                        }
                    }
                    val mBaseField = ContextWrapper::class.java.getDeclaredField("mBase")
                    mBaseField.isAccessible = true
                    mBaseField.set(activity, spoofedContext)
                    PotataApp.addLog("Context Hijack: SUCCESS")
                } catch (e: Exception) {}
            }
            base.callActivityOnCreate(activity, icicle)
        }

        override fun onCreate(arguments: Bundle?) { base.onCreate(arguments) }
        override fun onStart() { base.onStart() }
        override fun onDestroy() { base.onDestroy() }
    }
}
