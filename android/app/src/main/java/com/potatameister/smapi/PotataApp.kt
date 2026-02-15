package com.potatameister.smapi

import android.app.Application

class PotataApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Absolute earliest point to fix the Apktool OSDetection crash
        if (System.getProperty("os.name").isNullOrBlank()) {
            System.setProperty("os.name", "linux")
        }
        if (System.getProperty("user.home").isNullOrBlank()) {
            System.setProperty("user.home", filesDir.absolutePath)
        }
    }
}
