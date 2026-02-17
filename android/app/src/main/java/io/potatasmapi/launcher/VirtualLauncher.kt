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
import dalvik.system.BaseDexClassLoader
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

            // Enforce read-only for security
            allApks.forEach { apk -> if (apk.canWrite()) apk.setReadOnly() }

            PotataApp.addLog("Initializing Virtual Engine...")

            // 2. Prepare Paths
            val dexPath = allApks.joinToString(File.pathSeparator) { it.absolutePath }
            val optimizedDexPath = File(context.codeCacheDir, "opt_dex").apply { mkdirs() }.absolutePath
            val nativeLibPath = libDir.absolutePath

            // 3. Create the Virtual ClassLoader
            // Parent is the original ClassLoader to ensure system classes are reachable
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
                PotataApp.addLog("Error: Class $targetActivity NOT found!")
                throw e
            }

            // 4.5 Set Working Directory
            System.setProperty("user.dir", virtualRoot.absolutePath)

            // 5. Hook the System (Ultimate Mode)
            PotataApp.addLog("Redirecting System Context...")
            injectClassLoaderPaths(classLoader, dexPath, nativeLibPath)
            injectVirtualLoader(classLoader, dexPath, nativeLibPath, virtualRoot.absolutePath)
            injectInstrumentation(classLoader)

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
     * Augments the existing system ClassLoader by prepending our virtual paths.
     * This ensures that activity instantiation by the system finds our classes.
     */
    private fun injectClassLoaderPaths(virtualLoader: DexClassLoader, dexPath: String, nativeLibPath: String) {
        try {
            val systemLoader = context.classLoader as BaseDexClassLoader
            
            val pathListField = findField(BaseDexClassLoader::class.java, "pathList")
            val systemPathList = pathListField.get(systemLoader)
            val virtualPathList = pathListField.get(virtualLoader)

            // 1. Merge dexElements
            val dexElementsField = findField(systemPathList.javaClass, "dexElements")
            val systemElements = dexElementsField.get(systemPathList) as Array<*>
            val virtualElements = dexElementsField.get(virtualPathList) as Array<*>
            
            val combinedElements = java.lang.reflect.Array.newInstance(
                systemElements.javaClass.componentType!!,
                systemElements.size + virtualElements.size
            )
            System.arraycopy(virtualElements, 0, combinedElements, 0, virtualElements.size)
            System.arraycopy(systemElements, 0, combinedElements, virtualElements.size, systemElements.size)
            dexElementsField.set(systemPathList, combinedElements)

            // 2. Merge nativeLibraryDirectories
            try {
                val nativeDirsField = findField(systemPathList.javaClass, "nativeLibraryDirectories")
                val systemDirs = nativeDirsField.get(systemPathList) as? MutableList<File> ?: mutableListOf()
                val virtualLib = File(nativeLibPath)
                if (!systemDirs.contains(virtualLib)) {
                    systemDirs.add(0, virtualLib)
                }
                nativeDirsField.set(systemPathList, systemDirs)
            } catch (e: Exception) { Log.w(TAG, "Native dir injection failed") }

            PotataApp.addLog("System ClassLoader Augmented.")
        } catch (e: Exception) {
            Log.e(TAG, "ClassLoader Path Injection Failed", e)
        }
    }

    @SuppressLint("DiscouragedPrivateApi")
    private fun injectVirtualLoader(classLoader: ClassLoader, dexPath: String, libDir: String, dataDir: String) {
        try {
            val apkPaths = dexPath.split(File.pathSeparator).toTypedArray()
            val baseApk = apkPaths[0]

            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentActivityThread = activityThreadClass.getDeclaredMethod("currentActivityThread").invoke(null)
            val mPackagesField = activityThreadClass.getDeclaredField("mPackages")
            mPackagesField.isAccessible = true
            val mPackages = mPackagesField.get(currentActivityThread) as MutableMap<String, *>

            val loadedApkWeakRef = mPackages[context.packageName] as java.lang.ref.WeakReference<*>
            val loadedApk = loadedApkWeakRef.get() ?: return
            val loadedApkClass = Class.forName("android.app.LoadedApk")
            
            // Swap ClassLoader in LoadedApk
            val mClassLoaderField = loadedApkClass.getDeclaredField("mClassLoader")
            mClassLoaderField.isAccessible = true
            mClassLoaderField.set(loadedApk, classLoader)

            // Swap Paths
            val fields = mapOf("mAppDir" to baseApk, "mResDir" to baseApk, "mLibDir" to libDir, "mDataDir" to dataDir)
            for ((name, value) in fields) {
                try {
                    val field = loadedApkClass.getDeclaredField(name)
                    field.isAccessible = true
                    field.set(loadedApk, value)
                } catch (e: Exception) {}
            }

            PotataApp.addLog("LoadedApk Swapped.")
        } catch (e: Exception) { Log.e(TAG, "LoadedApk injection failed", e) }
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
            
            val mSplitResDirsField = loadedApkClass.getDeclaredField("mSplitResDirs")
            mSplitResDirsField.isAccessible = true
            val currentDirs = mSplitResDirsField.get(loadedApk) as? Array<String>
            val newDirs = (currentDirs?.toList() ?: emptyList()) + apkPaths.toList()
            mSplitResDirsField.set(loadedApk, newDirs.distinct().toTypedArray())

            val mResourcesField = loadedApkClass.getDeclaredField("mResources")
            mResourcesField.isAccessible = true
            mResourcesField.set(loadedApk, null)
            PotataApp.addLog("Global Resources Refreshed.")
        } catch (e: Exception) { Log.e(TAG, "Resource injection failed", e) }
    }

    @SuppressLint("DiscouragedPrivateApi")
    private fun injectInstrumentation(classLoader: ClassLoader) {
        try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentActivityThread = activityThreadClass.getDeclaredMethod("currentActivityThread").invoke(null)
            val mInstrumentationField = activityThreadClass.getDeclaredField("mInstrumentation")
            mInstrumentationField.isAccessible = true
            val base = mInstrumentationField.get(currentActivityThread) as Instrumentation
            mInstrumentationField.set(currentActivityThread, PotataInstrumentation(base, classLoader))
            PotataApp.addLog("Instrumentation Hooked.")
        } catch (e: Exception) { Log.e(TAG, "Instrumentation hook failed", e) }
    }

    private fun findField(clazz: Class<*>, name: String): Field {
        var c: Class<*>? = clazz
        while (c != null) {
            try {
                val field = c.getDeclaredField(name)
                field.isAccessible = true
                return field
            } catch (e: NoSuchFieldException) {
                c = c.superclass
            }
        }
        throw NoSuchFieldException("Field $name not found in $clazz")
    }

    private class PotataInstrumentation(private val base: Instrumentation, private val classLoader: ClassLoader) : Instrumentation() {
        override fun newActivity(cl: ClassLoader?, className: String?, intent: Intent?): Activity {
            // Force use our virtual classloader
            val activity = base.newActivity(classLoader, className, intent)
            
            // Package Name Spoofing Wrapper
            val baseContext = activity.baseContext
            val spoofedContext = object : ContextWrapper(baseContext) {
                override fun getPackageName(): String = "com.chucklefish.stardewvalley"
                override fun getApplicationInfo(): android.content.pm.ApplicationInfo {
                    val info = super.getApplicationInfo()
                    info.packageName = "com.chucklefish.stardewvalley"
                    return info
                }
            }
            
            // Swap the base context with our spoofed one
            try {
                val mBaseField = ContextWrapper::class.java.getDeclaredField("mBase")
                mBaseField.isAccessible = true
                mBaseField.set(activity, spoofedContext)
            } catch (e: Exception) { Log.e("Potata", "Context Spoof Failed", e) }

            try {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } catch (e: Exception) {}
            return activity
        }

        override fun onCreate(arguments: Bundle?) { base.onCreate(arguments) }
        override fun onStart() { base.onStart() }
        override fun onDestroy() { base.onDestroy() }
    }
}
