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
 * VirtualLauncher: The Native Engine Hijacker (Diagnostic Edition).
 */
class VirtualLauncher(private val context: Context) {
    private val TAG = "PotataLauncher"

    fun launch(activityName: String?, onComplete: () -> Unit) {
        try {
            val hostPackageName = "io.potatasmapi.launcher" 
            val virtualRoot = File(context.filesDir, "virtual/stardew")
            val libDir = File(virtualRoot, "lib")
            val sdcardRoot = File(Environment.getExternalStorageDirectory(), "PotataSMAPI")
            
            if (!File(virtualRoot, "virtual.ready").exists()) {
                throw Exception("Environment not ready. Please import the game first.")
            }

            PotataApp.addLog("--- VIRTUAL BOOT SEQUENCE ---")
            
            // Fix: Copy APKs to a read-only location for DexClassLoader
            val dexRoot = File(context.filesDir, "dex").apply { mkdirs() }
            val allApks = virtualRoot.listFiles()?.filter { it.name.endsWith(".apk") } ?: emptyList()
            
            // Copy each APK to dex folder (making them read-only)
            val dexPathList = mutableListOf<String>()
            for (apk in allApks) {
                val destFile = File(dexRoot, apk.name)
                apk.inputStream().use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                }
                destFile.setReadOnly()
                dexPathList.add(destFile.absolutePath)
            }
            
            val dexPath = dexPathList.joinToString(File.pathSeparator)
            val optimizedDexPath = File(context.codeCacheDir, "opt_dex").apply { mkdirs() }.absolutePath
            val nativeLibPath = libDir.absolutePath

            // 1. Verify Core
            val assembliesDir = File(sdcardRoot, "assemblies")
            val smapiDll = File(assembliesDir, "Stardew Valley.dll")
            if (!smapiDll.exists()) {
                throw Exception("SMAPI Core missing from assemblies folder.")
            }

            // 2. Prepare Code Loader
            PotataApp.addLog("Initializing Virtual ClassLoader...")
            val classLoader = DexClassLoader(dexPath, optimizedDexPath, nativeLibPath, context.classLoader)

            // 3. Environment Redirection (Mono/Xamarin Hooks)
            try {
                val baseDir = sdcardRoot.absolutePath
                
                // Critical Mono Environment Variables
                android.system.Os.setenv("MONO_PATH", assembliesDir.absolutePath, true)
                android.system.Os.setenv("SMAPI_ANDROID_BASE_DIR", baseDir, true)
                android.system.Os.setenv("HOME", baseDir, true)
                android.system.Os.setenv("EXTERNAL_STORAGE", baseDir, true)
                android.system.Os.setenv("LD_LIBRARY_PATH", nativeLibPath, true)
                
                // .NET 6+ Startup Hooks (Potential future-proofing)
                android.system.Os.setenv("DOTNET_STARTUP_HOOKS", File(assembliesDir, "Stardew Valley.dll").absolutePath, true)
                
                // Pre-load native engines to ensure they use our redirected paths
                try {
                    System.load(File(libDir, "libxamarin-app.so").absolutePath)
                    System.load(File(libDir, "libmonosgen-2.0.so").absolutePath)
                    System.load(File(libDir, "libmonodroid.so").absolutePath)
                    PotataApp.addLog("Native Engines: INITIALIZED")
                } catch (e: Throwable) {
                    PotataApp.addLog("Engine Warning: ${e.message}")
                }
                
                PotataApp.addLog("Redirection: $baseDir")
            } catch (e: Exception) { PotataApp.addLog("Env Hijack Error: ${e.message}") }

            // 4. System Records Hijacking
            val baseApk = allApks.find { it.name == "base.apk" }?.absolutePath ?: allApks[0].absolutePath
            injectSystemRecords(classLoader, baseApk, nativeLibPath, virtualRoot.absolutePath, hostPackageName)
            injectInstrumentation(classLoader, baseApk, nativeLibPath)
            injectVirtualResources(dexPath, hostPackageName)

            // 5. Fire Launch via Proxy
            val targetActivity = detectEntryPoint(classLoader, activityName)
            (context as Activity).runOnUiThread {
                try {
                    val intent = Intent().apply {
                        setClassName(hostPackageName, ProxyActivity::class.java.name)
                        putExtra("TARGET_ACTIVITY", targetActivity)
                        putExtra("DEX_PATH", dexPath)
                        putExtra("LIB_PATH", nativeLibPath)
                        putExtra("VIRTUAL_MODE", true)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    onComplete()
                    PotataApp.addLog("Handover: $targetActivity")
                } catch (e: Exception) {
                    PotataApp.addLog("Launch Failed: ${e.message}")
                    onComplete()
                }
            }

        } catch (e: Exception) {
            PotataApp.addLog("FATAL: ${e.message}")
            (context as Activity).runOnUiThread { onComplete() }
        }
    }

    private fun detectEntryPoint(cl: ClassLoader, preferred: String?): String {
        val options = listOf("com.chucklefish.stardewvalley.StardewValley", "com.chucklefish.stardewvalley.MainActivity")
        for (opt in options) {
            try {
                cl.loadClass(opt)
                PotataApp.addLog("Entry point detected: $opt")
                return opt
            } catch (e: Exception) {}
        }
        return preferred ?: options[0]
    }

    @SuppressLint("DiscouragedPrivateApi")
    private fun injectSystemRecords(classLoader: ClassLoader, baseApk: String, libDir: String, dataDir: String, hostPackageName: String) {
        try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentActivityThread = activityThreadClass.getDeclaredMethod("currentActivityThread").invoke(null)
            
            // Hijack Process ApplicationInfo
            try {
                val mBoundApplicationField = activityThreadClass.getDeclaredField("mBoundApplication")
                mBoundApplicationField.isAccessible = true
                val mBoundApplication = mBoundApplicationField.get(currentActivityThread)
                val infoField = mBoundApplication.javaClass.getDeclaredField("appInfo")
                infoField.isAccessible = true
                val appInfo = infoField.get(mBoundApplication) as ApplicationInfo
                
                appInfo.dataDir = dataDir
                appInfo.sourceDir = baseApk
                appInfo.publicSourceDir = baseApk
                appInfo.nativeLibraryDir = libDir
                PotataApp.addLog("System AppInfo: REDIRECTED")
            } catch (e: Exception) { PotataApp.addLog("Process Hook Skip: ${e.message}") }

            // Hijack LoadedApk (Package Registry)
            val mPackagesField = activityThreadClass.getDeclaredField("mPackages")
            mPackagesField.isAccessible = true
            val mPackages = mPackagesField.get(currentActivityThread) as MutableMap<String, *>
            val loadedApkWeakRef = mPackages[hostPackageName] as? java.lang.ref.WeakReference<*>
            val loadedApk = loadedApkWeakRef?.get() ?: run {
                PotataApp.addLog("LoadedApk missing for $hostPackageName")
                return
            }
            val loadedApkClass = Class.forName("android.app.LoadedApk")
            
            val fields = loadedApkClass.declaredFields
            for (field in fields) {
                field.isAccessible = true
                try {
                    when (field.name) {
                        "mClassLoader" -> field.set(loadedApk, classLoader)
                        "mAppDir", "mDir", "mResDir" -> field.set(loadedApk, baseApk)
                        "mDataDir" -> field.set(loadedApk, dataDir)
                        "mLibDir", "mLibPath" -> field.set(loadedApk, libDir)
                    }
                } catch (e: Exception) {}
            }
            PotataApp.addLog("Package Registry: HIJACKED")
        } catch (e: Exception) { PotataApp.addLog("System Hook Fail: ${e.message}") }
    }

    private fun injectVirtualResources(dexPath: String, hostPackageName: String) {
        try {
            val apkPaths = dexPath.split(File.pathSeparator).toTypedArray()
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentActivityThread = activityThreadClass.getDeclaredMethod("currentActivityThread").invoke(null)
            val mPackagesField = activityThreadClass.getDeclaredField("mPackages")
            mPackagesField.isAccessible = true
            val mPackages = mPackagesField.get(currentActivityThread) as MutableMap<String, *>
            val loadedApkWeakRef = mPackages[hostPackageName] as? java.lang.ref.WeakReference<*>
            val loadedApk = loadedApkWeakRef?.get() ?: return
            val loadedApkClass = Class.forName("android.app.LoadedApk")
            
            loadedApkClass.getDeclaredField("mSplitResDirs").apply { isAccessible = true }.set(loadedApk, apkPaths)
            loadedApkClass.getDeclaredField("mResources").apply { isAccessible = true }.set(loadedApk, null)
            PotataApp.addLog("Virtual Resources: MOUNTED")
        } catch (e: Exception) { PotataApp.addLog("Resource Hook Fail: ${e.message}") }
    }

    private fun injectInstrumentation(classLoader: ClassLoader, baseApk: String, libDir: String) {
        try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentActivityThread = activityThreadClass.getDeclaredMethod("currentActivityThread").invoke(null)
            val mInstrumentationField = activityThreadClass.getDeclaredField("mInstrumentation")
            mInstrumentationField.isAccessible = true
            
            // Store for later use by the game activity
            pendingClassLoader = classLoader
            pendingBaseApk = baseApk
            pendingLibDir = libDir
            
            val base = mInstrumentationField.get(currentActivityThread) as Instrumentation
            if (base !is PotataInstrumentation) {
                mInstrumentationField.set(currentActivityThread, PotataInstrumentation(base, classLoader, baseApk, libDir))
                PotataApp.addLog("Instrumentation: WRAPPED")
            }
        } catch (e: Exception) { PotataApp.addLog("Instrumentation Hook Fail: ${e.message}") }
    }

    private var pendingClassLoader: ClassLoader? = null
    private var pendingBaseApk: String? = null
    private var pendingLibDir: String? = null

    private class PotataInstrumentation(private val base: Instrumentation, private val classLoader: ClassLoader, private val baseApk: String, private val libDir: String) : Instrumentation() {
        
        private fun applyClassLoaderToActivity(activity: Activity) {
            try {
                val activityThreadClass = Class.forName("android.app.ActivityThread")
                val currentActivityThread = activityThreadClass.getDeclaredMethod("currentActivityThread").invoke(null)
                val mPackagesField = activityThreadClass.getDeclaredField("mPackages")
                mPackagesField.isAccessible = true
                val mPackages = mPackagesField.get(currentActivityThread) as MutableMap<String, *>
                
                // Apply to the host package
                val hostPkg = "io.potatasmapi.launcher"
                val loadedApkWeakRef = mPackages[hostPkg] as? java.lang.ref.WeakReference<*>
                val loadedApk = loadedApkWeakRef?.get()
                
                if (loadedApk != null) {
                    val mClassLoaderField = loadedApk.javaClass.getDeclaredField("mClassLoader")
                    mClassLoaderField.isAccessible = true
                    mClassLoaderField.set(loadedApk, classLoader)
                }
            } catch (e: Exception) {
                Log.e("Potata", "ClassLoader apply failed: ${e.message}")
            }
        }
        
        override fun newActivity(cl: ClassLoader?, className: String?, intent: Intent?): Activity {
            // Force use our virtual ClassLoader for ALL activity creations
            return base.newActivity(classLoader, className, intent)
        }

        private fun spoofContext(activity: Activity) {
            if (activity.javaClass.name.contains("chucklefish") || activity.javaClass.name.contains("stardew")) {
                try {
                    val mBaseField = ContextWrapper::class.java.getDeclaredField("mBase")
                    mBaseField.isAccessible = true
                    val currentBase = mBaseField.get(activity) as Context
                    if (currentBase !is PotataContext) {
                        mBaseField.set(activity, PotataContext(currentBase, baseApk, libDir))
                    }
                } catch (e: Exception) { Log.e("Potata", "Context Spoof FAIL: ${e.message}") }
            }
        }
        
        @SuppressLint("DiscouragedPrivateApi")
        private fun hijackPackageIdentity(context: Context) {
            try {
                val activityThreadClass = Class.forName("android.app.ActivityThread")
                val currentActivityThread = activityThreadClass.getDeclaredMethod("currentActivityThread").invoke(null)
                
                val mBoundApplicationField = activityThreadClass.getDeclaredField("mBoundApplication")
                mBoundApplicationField.isAccessible = true
                val mBoundApplication = mBoundApplicationField.get(currentActivityThread)
                val infoField = mBoundApplication.javaClass.getDeclaredField("appInfo")
                infoField.isAccessible = true
                val appInfo = infoField.get(mBoundApplication) as ApplicationInfo
                
                // Deep Identity Spoofing
                appInfo.packageName = "com.chucklefish.stardewvalley"
                
                val mPackagesField = activityThreadClass.getDeclaredField("mPackages")
                mPackagesField.isAccessible = true
                val mPackages = mPackagesField.get(currentActivityThread) as MutableMap<String, *>
                val loadedApkWeakRef = mPackages[context.packageName] as? java.lang.ref.WeakReference<*>
                val loadedApk = loadedApkWeakRef?.get()
                
                if (loadedApk != null) {
                    val mPackageNameField = loadedApk.javaClass.getDeclaredField("mPackageName")
                    mPackageNameField.isAccessible = true
                    mPackageNameField.set(loadedApk, "com.chucklefish.stardewvalley")
                }
            } catch (e: Exception) { Log.e("Potata", "Identity Hijack FAIL: ${e.message}") }
        }

        override fun callActivityOnCreate(activity: Activity, icicle: Bundle?) {
            // Apply classloader to ensure the game uses our virtual environment
            applyClassLoaderToActivity(activity)
            spoofContext(activity)
            if (activity.javaClass.name.contains("chucklefish") || activity.javaClass.name.contains("stardew")) {
                hijackPackageIdentity(activity)
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
            base.callActivityOnCreate(activity, icicle)
        }

        override fun callActivityOnResume(activity: Activity) {
            spoofContext(activity)
            base.callActivityOnResume(activity)
        }

        override fun callActivityOnStart(activity: Activity) {
            spoofContext(activity)
            base.callActivityOnStart(activity)
        }

        override fun onCreate(arguments: Bundle?) { base.onCreate(arguments) }
        override fun onStart() { base.onStart() }
        override fun onDestroy() { base.onDestroy() }
    }

    private class PotataContext(base: Context, private val baseApk: String, private val libDir: String) : ContextWrapper(base) {
        override fun getPackageName(): String = "com.chucklefish.stardewvalley"
        override fun getExternalFilesDir(type: String?): File? = File(Environment.getExternalStorageDirectory(), "PotataSMAPI/Files")
        override fun getFilesDir(): File = File(Environment.getExternalStorageDirectory(), "PotataSMAPI/Internal")
        override fun getApplicationInfo(): ApplicationInfo {
            val info = super.getApplicationInfo()
            info.packageName = "com.chucklefish.stardewvalley"
            info.sourceDir = baseApk
            info.publicSourceDir = baseApk
            info.nativeLibraryDir = libDir
            return info
        }
    }
}
