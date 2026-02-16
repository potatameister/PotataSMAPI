package io.potatasmapi.launcher

import android.app.Application
import android.util.Log
import androidx.compose.runtime.mutableStateListOf

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
        addLog("Launcher core initialized.")
    }
}
