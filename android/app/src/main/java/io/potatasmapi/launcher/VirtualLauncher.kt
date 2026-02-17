package io.potatasmapi.launcher

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.AssetManager
import android.util.Log
import dalvik.system.DexClassLoader
import java.io.File
import java.lang.reflect.Field

/**
 * VirtualLauncher: The engine that runs the virtualized game.
 */
class VirtualLauncher(private val context: Context) {
    private val TAG = "PotataLauncher"

    fun launch(activityName: String?) {
        try {
            val virtualRoot = File(context.filesDir, "virtual/stardew")
            val libDir = File(virtualRoot, "lib")
            val baseApk = File(virtualRoot, "base.apk")
            
            if (!File(virtualRoot, "virtual.ready").exists()) {
                throw Exception("Virtual environment not ready. Please import first.")
            }

            // 1. Find all APKs (base + splits)
            val allApks = virtualRoot.listFiles()?.filter { it.name.endsWith(".apk") } ?: emptyList()
            if (allApks.isEmpty()) throw Exception("No game APKs found.")

            // Enforce read-only for security (Android restriction)
            allApks.forEach { apk ->
                if (apk.canWrite()) {
                    apk.setReadOnly()
                }
            }

            PotataApp.addLog("Initializing Virtual Engine...")

            // 2. Prepare Paths
            val dexPath = allApks.joinToString(File.pathSeparator) { it.absolutePath }
            val optimizedDexPath = File(context.codeCacheDir, "opt_dex").apply { mkdirs() }.absolutePath
            val nativeLibPath = libDir.absolutePath

            // 3. Create the Virtual ClassLoader
            val classLoader = DexClassLoader(
                dexPath,
                optimizedDexPath,
                nativeLibPath,
                context.classLoader
            )

            // 4. Verify Activity
            val targetActivity = activityName ?: "com.chucklefish.stardewvalley.StardewValley"
            try {
                classLoader.loadClass(targetActivity)
                PotataApp.addLog("Verified: $targetActivity found.")
            } catch (e: ClassNotFoundException) {
                PotataApp.addLog("Error: Class $targetActivity NOT found in provided APKs!")
                throw e
            }

            // 5. Hook the Activity Thread (The "Brain" of the App)
            PotataApp.addLog("Redirecting System Context...")
            injectVirtualLoader(classLoader, dexPath)
            injectInstrumentation(classLoader)

            // 5.1 Hook the current context's PackageInfo (Nuclear Option)
            try {
                val baseContext = (context as? ContextWrapper)?.baseContext ?: context
                val mPackageInfoField = baseContext.javaClass.getDeclaredField("mPackageInfo")
                mPackageInfoField.isAccessible = true
                val mPackageInfo = mPackageInfoField.get(baseContext)
                
                val mClassLoaderField = mPackageInfo.javaClass.getDeclaredField("mClassLoader")
                mClassLoaderField.isAccessible = true
                mClassLoaderField.set(mPackageInfo, classLoader)
                PotataApp.addLog("Context mPackageInfo Hooked.")
            } catch (e: Exception) { Log.w(TAG, "Context hook failed: ${e.message}") }

            // 5.5 Hook Resources globally
            injectVirtualResources(dexPath)

            // 6. Launch the Game Activity
            PotataApp.addLog("Executing Virtual Core...")
            
            val intent = Intent().apply {
                setClassName(context.packageName, targetActivity)
                putExtra("VIRTUAL_MODE", true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            context.startActivity(intent)
            
            PotataApp.addLog("Virtual Tunnel Established.")

        } catch (e: Exception) {
            val error = "Launch Failed: ${e.message}"
            Log.e(TAG, error, e)
            PotataApp.addLog(error)
        }
    }

    /**
     * This is the "Magic" step. It tells the Android system to use our 
     * custom ClassLoader and paths when it tries to start the game's activities.
     */
    @SuppressLint("DiscouragedPrivateApi")
    private fun injectVirtualLoader(classLoader: ClassLoader, dexPath: String) {
        try {
            val apkPaths = dexPath.split(File.pathSeparator).toTypedArray()
            val baseApk = apkPaths[0]

            // Get ActivityThread
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentActivityThreadMethod = activityThreadClass.getDeclaredMethod("currentActivityThread")
            currentActivityThreadMethod.isAccessible = true
            val currentActivityThread = currentActivityThreadMethod.invoke(null)

            // Get mPackages (the cache of all loaded APKs)
            val mPackagesField = activityThreadClass.getDeclaredField("mPackages")
            mPackagesField.isAccessible = true
            val mPackages = mPackagesField.get(currentActivityThread) as MutableMap<String, *>

            // Find our own package record
            val loadedApkWeakRef = mPackages[context.packageName] as java.lang.ref.WeakReference<*>
            val loadedApk = loadedApkWeakRef.get() ?: return

            // Force our custom ClassLoader and paths into the system record
            val loadedApkClass = Class.forName("android.app.LoadedApk")
            
            // 1. Swap ClassLoader
            val mClassLoaderField = loadedApkClass.getDeclaredField("mClassLoader")
            mClassLoaderField.isAccessible = true
            mClassLoaderField.set(loadedApk, classLoader)

            // 2. Update Paths (important for resources and activity instantiation)
            try {
                val mAppDirField = loadedApkClass.getDeclaredField("mAppDir")
                mAppDirField.isAccessible = true
                mAppDirField.set(loadedApk, baseApk)
            } catch (e: Exception) { Log.w(TAG, "mAppDir not found") }

            try {
                val mResDirField = loadedApkClass.getDeclaredField("mResDir")
                mResDirField.isAccessible = true
                mResDirField.set(loadedApk, baseApk)
            } catch (e: Exception) { Log.w(TAG, "mResDir not found") }

            if (apkPaths.size > 1) {
                try {
                    val mSplitSourceDirsField = loadedApkClass.getDeclaredField("mSplitSourceDirs")
                    mSplitSourceDirsField.isAccessible = true
                    mSplitSourceDirsField.set(loadedApk, apkPaths)
                } catch (e: Exception) { 
                    Log.w(TAG, "mSplitSourceDirs not found, trying mSplitResDirs")
                    try {
                        val mSplitResDirsField = loadedApkClass.getDeclaredField("mSplitResDirs")
                        mSplitResDirsField.isAccessible = true
                        mSplitResDirsField.set(loadedApk, apkPaths)
                    } catch (e2: Exception) { Log.w(TAG, "mSplitResDirs not found") }
                }
            }
            
            PotataApp.addLog("System ClassLoader & Paths Swapped.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inject classloader", e)
            throw e
        }
    }

    /**
     * Injects the game APK(s) into the resource search path.
     */
    @SuppressLint("DiscouragedPrivateApi")
    private fun injectInstrumentation(classLoader: ClassLoader) {
        try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentActivityThreadMethod = activityThreadClass.getDeclaredMethod("currentActivityThread")
            currentActivityThreadMethod.isAccessible = true
            val currentActivityThread = currentActivityThreadMethod.invoke(null)

            val mInstrumentationField = activityThreadClass.getDeclaredField("mInstrumentation")
            mInstrumentationField.isAccessible = true
            val baseInstrumentation = mInstrumentationField.get(currentActivityThread) as Instrumentation
            
            val wrapper = PotataInstrumentation(baseInstrumentation, classLoader)
            mInstrumentationField.set(currentActivityThread, wrapper)
            PotataApp.addLog("Instrumentation Hooked.")
        } catch (e: Exception) {
            Log.e(TAG, "Instrumentation injection failed", e)
        }
    }

    /**
     * A custom Instrumentation that forces the use of our Virtual ClassLoader.
     */
    private class PotataInstrumentation(private val base: Instrumentation, private val classLoader: ClassLoader) : Instrumentation() {
        override fun newActivity(cl: ClassLoader?, className: String?, intent: Intent?): Activity {
            // Force use our virtual classloader instead of the system one
            return base.newActivity(classLoader, className, intent)
        }

        override fun onCreate(arguments: Bundle?) { base.onCreate(arguments) }
        override fun onStart() { base.onStart() }
        override fun onResume() { base.onResume() }
        override fun onPause() { base.onPause() }
        override fun onDestroy() { base.onDestroy() }
        // Pass-through other methods as needed...
    }
}
