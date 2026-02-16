package com.potatameister.smapi

import android.app.Application
import android.util.Log

class PotataApp : Application() {
    
    init {
        // This static-style block runs as soon as the class is loaded by the JVM
        // ensuring properties are set before any other class (like Apktool) can load.
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
        // Secondary safeguard
        if (System.getProperty("user.home").isNullOrBlank()) {
            System.setProperty("user.home", filesDir.absolutePath)
        }
    }
}
