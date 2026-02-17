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
 * VirtualLauncher: The Host Engine.
 * Runs Stardew Valley code inside our own process identity.
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

            PotataApp.addLog("Initializing Host Sandbox...")

            val dexPath = allApks.joinToString(File.pathSeparator) { it.absolutePath }
            val optimizedDexPath = File(context.codeCacheDir, "opt_dex").apply { mkdirs() }.absolutePath
            val nativeLibPath = libDir.absolutePath

            // Load game code into OUR process
            val classLoader = DexClassLoader(dexPath, optimizedDexPath, nativeLibPath, context.classLoader)

            val targetActivity = activityName ?: "com.chucklefish.stardewvalley.StardewValley"
            
            // Set paths for the Host
            System.setProperty("user.dir", "/sdcard/PotataSMAPI")
            System.setProperty("user.home", "/sdcard/PotataSMAPI")
            
            injectClassLoaderPaths(classLoader, dexPath, nativeLibPath)
            injectInstrumentation(classLoader)
            injectVirtualResources(dexPath)

            // SMAPI Environment
            try {
                android.system.Os.setenv("MONO_PATH", assetsDir.absolutePath, true)
                android.system.Os.setenv("SMAPI_ANDROID_BASE_DIR", "/sdcard/PotataSMAPI", true)
                android.system.Os.setenv("HOME", "/sdcard/PotataSMAPI", true)
            } catch (e: Exception) {}

            val intent = Intent().apply {
                setClassName(context.packageName, targetActivity)
                putExtra("VIRTUAL_MODE", true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            context.startActivity(intent)
            PotataApp.addLog("Host Sandbox Active.")

        } catch (e: Exception) {
            PotataApp.addLog("Launch Failed: ${e.message}")
        }
    }

    private fun injectClassLoaderPaths(virtualLoader: DexClassLoader, dexPath: String, nativeLibPath: String) {
        try {
            val systemLoader = context.classLoader as BaseDexClassLoader
            val pathListField = findField(BaseDexClassLoader::class.java, "pathList")
            val systemPathList = pathListField.get(systemLoader)
            val virtualPathList = pathListField.get(virtualLoader)

            val dexElementsField = findField(systemPathList.javaClass, "dexElements")
            val systemElements = dexElementsField.get(systemPathList) as Array<*>
            val virtualElements = dexElementsField.get(virtualPathList) as Array<*>
            
            val combinedElements = java.lang.reflect.Array.newInstance(systemElements.javaClass.componentType!!, systemElements.size + virtualElements.size)
            System.arraycopy(virtualElements, 0, combinedElements, 0, virtualElements.size)
            System.arraycopy(systemElements, 0, combinedElements, virtualElements.size, systemElements.size)
            dexElementsField.set(systemPathList, combinedElements)
        } catch (e: Exception) {}
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
        } catch (e: Exception) {}
    }

    private fun findField(clazz: Class<*>, name: String): Field {
        var c: Class<*>? = clazz
        while (c != null) {
            try {
                val field = c.getDeclaredField(name)
                field.isAccessible = true
                return field
            } catch (e: NoSuchFieldException) { c = c.superclass }
        }
        throw NoSuchFieldException(name)
    }

    private class PotataInstrumentation(private val base: Instrumentation, private val classLoader: ClassLoader) : Instrumentation() {
        override fun newActivity(cl: ClassLoader?, className: String?, intent: Intent?): Activity {
            return base.newActivity(classLoader, className, intent)
        }

        override fun callActivityOnCreate(activity: Activity, icicle: Bundle?) {
            try {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                android.widget.Toast.makeText(activity, "POTATA SMAPI: HOSTING FARM...", android.widget.Toast.LENGTH_LONG).show()
            } catch (e: Exception) {}
            base.callActivityOnCreate(activity, icicle)
        }

        override fun onCreate(arguments: Bundle?) { base.onCreate(arguments) }
        override fun onStart() { base.onStart() }
        override fun onDestroy() { base.onDestroy() }
    }
}
