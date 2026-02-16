package io.potatasmapi.launcher

import android.app.Application
import android.util.Log

class PotataApp : Application() {
    
    init {
        // Absolute earliest injection using multiple methods to ensure os.name is NEVER null
        try {
            // Method 1: Standard
            System.setProperty("os.name", "linux")
            System.setProperty("java.vm.vendor", "The Android Project")
            System.setProperty("java.vm.name", "Dalvik")
            
            // Method 2: Direct Properties access
            val props = System.getProperties()
            props.setProperty("os.name", "linux")
            
            // Method 3: Reflection (Force load OSDetection after setting)
            try {
                Class.forName("brut.util.OSDetection")
            } catch (e: Throwable) {
                // Ignore, we just want to trigger its clinit while our properties are set
            }
            
            Log.d("PotataApp", "System properties primed. os.name=${System.getProperty("os.name")}")
        } catch (e: Exception) {
            Log.e("PotataApp", "Failed to prime system properties", e)
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Ensure user.home is set for Apktool framework
        System.setProperty("user.home", filesDir.absolutePath)
    }
}
