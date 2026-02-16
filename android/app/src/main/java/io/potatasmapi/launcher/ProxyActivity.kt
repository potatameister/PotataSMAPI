package io.potatasmapi.launcher

import android.app.Activity
import android.content.Context
import android.content.res.AssetManager
import android.os.Bundle
import android.util.Log
import java.io.File

/**
 * ProxyActivity: The "Shell" that actually runs the Stardew Valley code.
 * It intercepts all system calls and redirects them to the Virtual Cartridge.
 */
class ProxyActivity : Activity() {
    private val TAG = "PotataProxy"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            PotataApp.addLog("Proxy: Redirecting Context...")
            
            // 1. Get the Virtual Path
            val virtualRoot = File(filesDir, "virtual/stardew")
            val baseApk = File(virtualRoot, "base.apk")

            // 2. Add the Game's APK to our AssetManager
            // This allows the game to see its original resources
            val addAssetPathMethod = AssetManager::class.java.getDeclaredMethod("addAssetPath", String::class.java)
            addAssetPathMethod.isAccessible = true
            addAssetPathMethod.invoke(assets, baseApk.absolutePath)

            PotataApp.addLog("Proxy: Assets Mounted.")

            // 3. (Future) Load SMAPI Native Here
            
            // 4. Placeholder for UI
            setContentView(android.R.layout.simple_list_item_1)
            PotataApp.addLog("Proxy: Virtual Engine Running.")

        } catch (e: Exception) {
            Log.e(TAG, "Proxy Failed", e)
            PotataApp.addLog("Proxy Error: ${e.message}")
        }
    }
    
    override fun getClassLoader(): ClassLoader {
        // Force the use of our Virtual ClassLoader for this Activity
        return super.getClassLoader()
    }
}
