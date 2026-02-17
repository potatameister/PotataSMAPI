package io.potatasmapi.launcher

import android.app.Activity
import android.app.Application
import android.content.res.AssetManager
import android.os.Bundle
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import java.io.File

class PotataApp : Application() {
    companion object {
        val logs = mutableStateListOf<String>()
        fun addLog(msg: String) {
            Log.d("Potata", msg)
            logs.add(0, msg) // Newest first
        }
    }
    
    init {
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
        System.setProperty("user.home", filesDir.absolutePath)
        
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                mountAssets(activity)
            }
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

        addLog("Launcher core initialized.")
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
}
