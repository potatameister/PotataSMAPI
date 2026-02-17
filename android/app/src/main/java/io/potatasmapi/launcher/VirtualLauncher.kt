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
            
            if (!File(virtualRoot, "virtual.ready").exists()) {
                throw Exception("Virtual environment not ready. Please import first.")
            }

            // 1. Find all APKs
            val allApks = virtualRoot.listFiles()?.filter { it.name.endsWith(".apk") } ?: emptyList()
            if (allApks.isEmpty()) throw Exception("No game APKs found.")
            allApks.forEach { apk -> if (apk.canWrite()) apk.setReadOnly() }

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
                PotataApp.addLog("Verified: $targetActivity")
            } catch (e: ClassNotFoundException) {
                PotataApp.addLog("Error: $targetActivity not found")
                throw e
            }

            // 5. System Hooks
            System.setProperty("user.dir", virtualRoot.absolutePath)
            
            injectClassLoaderPaths(classLoader, dexPath, nativeLibPath)
            injectVirtualLoader(classLoader, dexPath, nativeLibPath, virtualRoot.absolutePath)
            injectInstrumentation(classLoader)
            injectVirtualResources(dexPath)

            // 6. Bootstrap SMAPI
            // We set the MONO_PATH to include our virtual assets folder so our redirected Stardew Valley.dll is seen
            try {
                val assetsDir = File(virtualRoot, "assets")
                android.system.Os.setenv("MONO_PATH", assetsDir.absolutePath, true)
                android.system.Os.setenv("SMAPI_ANDROID_BASE_DIR", "/sdcard/PotataSMAPI", true)
                android.system.Os.setenv("SMAPI_AUTO_LOAD", "1", true)
                PotataApp.addLog("SMAPI Bootstrapper Primed.")
            } catch (e: Exception) { Log.w(TAG, "SMAPI env failed") }

            // 7. Launch
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

    private fun injectClassLoaderPaths(virtualLoader: DexClassLoader, dexPath: String, nativeLibPath: String) {
        try {
            val systemLoader = context.classLoader as BaseDexClassLoader
            val pathListField = findField(BaseDexClassLoader::class.java, "pathList")
            val systemPathList = pathListField.get(systemLoader)
            val virtualPathList = pathListField.get(virtualLoader)

            // Merge dexElements
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

            PotataApp.addLog("ClassLoader Hooked.")
        } catch (e: Exception) { Log.e(TAG, "ClassLoader hook failed", e) }
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
            
            // Swap fields
            val fields = mapOf(
                "mClassLoader" to classLoader,
                "mAppDir" to baseApk,
                "mResDir" to baseApk,
                "mLibDir" to libDir,
                "mDataDir" to dataDir,
                "mPackageName" to "com.chucklefish.stardewvalley"
            )

            for ((name, value) in fields) {
                try {
                    val field = loadedApkClass.getDeclaredField(name)
                    field.isAccessible = true
                    field.set(loadedApk, value)
                } catch (e: Exception) {}
            }
            
            // Set isolated environment
            try {
                android.system.Os.setenv("ANDROID_DATA", dataDir, true)
                android.system.Os.setenv("HOME", dataDir, true)
            } catch (e: Exception) {}

        } catch (e: Exception) { Log.e(TAG, "LoadedApk hook failed", e) }
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
            
            try {
                val mSplitResDirsField = loadedApkClass.getDeclaredField("mSplitResDirs")
                mSplitResDirsField.isAccessible = true
                mSplitResDirsField.set(loadedApk, apkPaths)
            } catch (e: Exception) {}

            try {
                val mResourcesField = loadedApkClass.getDeclaredField("mResources")
                mResourcesField.isAccessible = true
                mResourcesField.set(loadedApk, null)
            } catch (e: Exception) {}
        } catch (e: Exception) { Log.e(TAG, "Resource hook failed", e) }
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
        throw NoSuchFieldException(name)
    }

    private class PotataInstrumentation(private val base: Instrumentation, private val classLoader: ClassLoader) : Instrumentation() {
        override fun newActivity(cl: ClassLoader?, className: String?, intent: Intent?): Activity {
            val activity = base.newActivity(classLoader, className, intent)
            
            // Context Spoofing (Package & Paths)
            try {
                val baseContext = activity.baseContext
                val spoofedContext = object : ContextWrapper(baseContext) {
                    override fun getPackageName(): String = "com.chucklefish.stardewvalley"
                    
                    // Redirect SD Card / Hardcoded paths
                    override fun getExternalFilesDir(type: String?): File? = File("/sdcard/PotataSMAPI/Files")
                    override fun getFilesDir(): File = File("/sdcard/PotataSMAPI/Internal")
                    
                    override fun getSystemService(name: String): Any? {
                        // Potential hook for storage manager if needed
                        return super.getSystemService(name)
                    }
                }
                val mBaseField = ContextWrapper::class.java.getDeclaredField("mBase")
                mBaseField.isAccessible = true
                mBaseField.set(activity, spoofedContext)
            } catch (e: Exception) { Log.e("Potata", "Context Spoof Failed", e) }

            return activity
        }

        override fun callActivityOnCreate(activity: Activity, icicle: Bundle?) {
            try {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } catch (e: Exception) {}
            base.callActivityOnCreate(activity, icicle)
        }

        override fun onCreate(arguments: Bundle?) { base.onCreate(arguments) }
        override fun onStart() { base.onStart() }
        override fun onDestroy() { base.onDestroy() }
    }
}
