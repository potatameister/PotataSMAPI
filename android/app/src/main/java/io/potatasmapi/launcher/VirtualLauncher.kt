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

    fun launch() {
        try {
            val virtualRoot = File(context.filesDir, "virtual/stardew")
            val libDir = File(virtualRoot, "lib")
            val baseApk = File(virtualRoot, "base.apk")
            
            if (!File(virtualRoot, "virtual.ready").exists()) {
                throw Exception("Virtual environment not ready. Please import first.")
            }

            // Enforce read-only for security (Android restriction)
            if (baseApk.canWrite()) {
                baseApk.setReadOnly()
            }

            PotataApp.addLog("Initializing Virtual Engine...")

            // 1. Prepare Paths
            val optimizedDexPath = File(context.codeCacheDir, "opt_dex").apply { mkdirs() }.absolutePath
            val nativeLibPath = libDir.absolutePath

            // 2. Create the Virtual ClassLoader
            // We point to base.apk directly so Android handles multidex automatically
            val classLoader = DexClassLoader(
                baseApk.absolutePath,
                optimizedDexPath,
                nativeLibPath,
                context.classLoader
            )

            // 3. Hook the Activity Thread (The "Brain" of the App)
            PotataApp.addLog("Redirecting System Context...")
            injectVirtualLoader(classLoader, baseApk.absolutePath)

            // 3.5 Hook Resources globally
            injectVirtualResources(baseApk.absolutePath)

            // 4. Launch the Game Activity
            PotataApp.addLog("Executing Virtual Core...")
            
            val intent = Intent().apply {
                setClassName(context.packageName, "com.chucklefish.stardewvalley.StardewValley")
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
     * custom ClassLoader when it tries to start the game's activities.
     */
    @SuppressLint("DiscouragedPrivateApi")
    private fun injectVirtualLoader(classLoader: ClassLoader, apkPath: String) {
        try {
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

            // Force our custom ClassLoader into the system record
            val loadedApkClass = Class.forName("android.app.LoadedApk")
            val mClassLoaderField = loadedApkClass.getDeclaredField("mClassLoader")
            mClassLoaderField.isAccessible = true
            mClassLoaderField.set(loadedApk, classLoader)
            
            PotataApp.addLog("System ClassLoader Swapped.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inject classloader", e)
            throw e
        }
    }

    /**
     * Injects the game APK into the resource search path.
     */
    @SuppressLint("DiscouragedPrivateApi")
    private fun injectVirtualResources(apkPath: String) {
        try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentActivityThreadMethod = activityThreadClass.getDeclaredMethod("currentActivityThread")
            currentActivityThreadMethod.isAccessible = true
            val currentActivityThread = currentActivityThreadMethod.invoke(null)

            val mPackagesField = activityThreadClass.getDeclaredField("mPackages")
            mPackagesField.isAccessible = true
            val mPackages = mPackagesField.get(currentActivityThread) as MutableMap<String, *>

            val loadedApkWeakRef = mPackages[context.packageName] as java.lang.ref.WeakReference<*>
            val loadedApk = loadedApkWeakRef.get() ?: return

            val loadedApkClass = Class.forName("android.app.LoadedApk")
            
            // Add to mSplitResDirs
            val mSplitResDirsField = loadedApkClass.getDeclaredField("mSplitResDirs")
            mSplitResDirsField.isAccessible = true
            val currentDirs = mSplitResDirsField.get(loadedApk) as? Array<String>
            val newDirs = if (currentDirs == null) {
                arrayOf(apkPath)
            } else {
                if (currentDirs.contains(apkPath)) return
                currentDirs + apkPath
            }
            mSplitResDirsField.set(loadedApk, newDirs)

            // Force resources refresh
            val mResourcesField = loadedApkClass.getDeclaredField("mResources")
            mResourcesField.isAccessible = true
            mResourcesField.set(loadedApk, null)

            PotataApp.addLog("System Resources Augmented.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inject resources", e)
            PotataApp.addLog("Warning: Resource injection failed.")
        }
    }
}
