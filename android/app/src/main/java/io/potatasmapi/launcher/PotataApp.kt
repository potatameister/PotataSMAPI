package io.potatasmapi.launcher

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.res.AssetManager
import android.os.Bundle
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import java.io.File

class PotataApp : Application() {
    companion object {
        val logs = mutableStateListOf<String>()
        private var logFile: File? = null
        var virtualLibPath: String? = null
        var assembliesPath: String? = null
        var baseDir: String? = null

        fun addLog(msg: String) {
            Log.d("Potata", msg)
            logs.add(0, msg)
            saveLogToFile(msg)
        }

        private fun saveLogToFile(msg: String) {
            try {
                logFile?.appendText("[${java.util.Date()}] $msg\n")
            } catch (e: Exception) {}
        }
        
        fun setupEnvironment() {
            try {
                val ctx = getAppContext() ?: return
                val sdcardRoot = File(android.os.Environment.getExternalStorageDirectory(), "PotataSMAPI")
                baseDir = sdcardRoot.absolutePath
                assembliesPath = File(sdcardRoot, "assemblies").absolutePath
                val virtualRoot = File(ctx.filesDir, "virtual/stardew")
                virtualLibPath = File(virtualRoot, "lib").absolutePath
                
                android.system.Os.setenv("MONO_PATH", assembliesPath!!, true)
                android.system.Os.setenv("SMAPI_ANDROID_BASE_DIR", baseDir!!, true)
                android.system.Os.setenv("HOME", baseDir!!, true)
                android.system.Os.setenv("EXTERNAL_STORAGE", baseDir!!, true)
                android.system.Os.setenv("LD_LIBRARY_PATH", virtualLibPath!!, true)
                
                addLog("Environment configured at app start")
            } catch (e: Exception) {
                Log.e("PotataApp", "Environment setup failed", e)
            }
        }
        
        private var appContext: Context? = null
        fun getAppContext(): Context? = appContext
    }
    
    init {
        appContext = this
        try {
            System.setProperty("os.name", "linux")
            System.setProperty("java.vm.vendor", "The Android Project")
            System.setProperty("java.vm.name", "Dalvik")
        } catch (e: Exception) {
            Log.e("PotataApp", "Failed to set early system properties", e)
        }
    }

    override fun onCreate() {
        super.onCreate()
        bypassHiddenApi()
        
        // Initialize environment as early as possible
        setupEnvironment()
        
        System.setProperty("user.home", filesDir.absolutePath)
        
        // Initialize persistent log
        val logDir = File("/sdcard/PotataSMAPI")
        if (!logDir.exists()) logDir.mkdirs()
        logFile = File(logDir, "launcher_log.txt")
        if (logFile?.exists() == true && logFile!!.length() > 1024 * 1024) {
            logFile?.delete()
        }
        addLog("--- NEW SESSION ---")

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                mountAssets(activity)
                mountNativeLibs(activity)
            }
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle?) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

        addLog("Launcher core initialized.")
    }

    private fun bypassHiddenApi() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.P) return
        try {
            val forName = Class::class.java.getDeclaredMethod("forName", String::class.java)
            val getDeclaredMethod = Class::class.java.getDeclaredMethod("getDeclaredMethod", String::class.java, arrayOf<Class<*>>().javaClass)

            val vmRuntimeClass = forName.invoke(null, "dalvik.system.VMRuntime") as Class<*>
            val getRuntime = getDeclaredMethod.invoke(vmRuntimeClass, "getRuntime", null) as java.lang.reflect.Method
            val setHiddenApiExemptions = getDeclaredMethod.invoke(vmRuntimeClass, "setHiddenApiExemptions", arrayOf(arrayOf<String>().javaClass)) as java.lang.reflect.Method

            val vmRuntime = getRuntime.invoke(null)
            setHiddenApiExemptions.invoke(vmRuntime, arrayOf("L"))
            Log.d("Potata", "Hidden API Bypass: SUCCESS")
        } catch (e: Exception) {
            Log.e("Potata", "Hidden API Bypass: FAILED", e)
        }
    }

    private fun mountAssets(activity: Activity) {
        try {
            val virtualRoot = File(filesDir, "virtual/stardew")
            if (virtualRoot.exists()) {
                val apkFiles = virtualRoot.listFiles()?.filter { it.name.endsWith(".apk") } ?: return
                val addAssetPathMethod = AssetManager::class.java.getDeclaredMethod("addAssetPath", String::class.java)
                addAssetPathMethod.isAccessible = true
                for (apk in apkFiles) {
                    addAssetPathMethod.invoke(activity.assets, apk.absolutePath)
                    Log.d("PotataApp", "Mounted assets from ${apk.name} for ${activity.javaClass.name}")
                }
                Log.d("PotataApp", "Mounted ${apkFiles.size} APKs for ${activity.javaClass.name}")
            }
        } catch (e: Exception) {
            Log.e("PotataApp", "Asset mount failed", e)
        }
    }
    
    private fun mountNativeLibs(activity: Activity) {
        try {
            val virtualLibPath = PotataApp.virtualLibPath ?: return
            val libDir = File(virtualLibPath)
            if (!libDir.exists()) return
            
            // Preload native libraries needed by Mono/Xamarin
            val nativeLibs = listOf("libmonosgen-2.0.so", "libmonodroid.so", "libxamarin-app.so")
            for (libName in nativeLibs) {
                val libFile = File(libDir, libName)
                if (libFile.exists()) {
                    try {
                        System.load(libFile.absolutePath)
                        Log.d("PotataApp", "Preloaded $libName for ${activity.javaClass.name}")
                    } catch (e: Throwable) {
                        Log.e("PotataApp", "Failed to load $libName: ${e.message}")
                    }
                }
            }
            
            // Update LD_LIBRARY_PATH for this activity
            val currentLdLib = android.system.Os.getenv("LD_LIBRARY_PATH") ?: ""
            if (!currentLdLib.contains(virtualLibPath)) {
                android.system.Os.setenv("LD_LIBRARY_PATH", "$virtualLibPath:$currentLdLib", true)
            }
            
            addLog("Native libs ready for ${activity.javaClass.simpleName}")
        } catch (e: Exception) {
            Log.e("PotataApp", "Native lib mount failed", e)
        }
    }
}
