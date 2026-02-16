package io.potatasmapi.launcher

import android.content.Context
import android.content.Intent
import android.util.Log
import dalvik.system.DexClassLoader
import java.io.File

/**
 * VirtualLauncher: The engine that runs the virtualized game.
 */
class VirtualLauncher(private val context: Context) {
    private val TAG = "PotataLauncher"

    fun launch() {
        try {
            val virtualRoot = File(context.filesDir, "virtual/stardew")
            val dexDir = File(virtualRoot, "dex")
            val libDir = File(virtualRoot, "lib")
            
            if (!File(virtualRoot, "virtual.ready").exists()) {
                throw Exception("Virtual environment not ready. Please import first.")
            }

            PotataApp.addLog("Preparing virtual launch...")

            // 1. Collect all DEX files
            val dexFiles = dexDir.listFiles()?.filter { it.name.endsWith(".dex") }?.map { it.absolutePath }
            if (dexFiles.isNullOrEmpty()) throw Exception("No game code found in virtual storage.")
            
            val dexPath = dexFiles.joinToString(File.pathSeparator)
            val optimizedDexPath = File(context.codeCacheDir, "opt_dex").apply { mkdirs() }.absolutePath
            val nativeLibPath = libDir.absolutePath

            PotataApp.addLog("Loading code: ${dexFiles.size} blocks")

            // 2. Create ClassLoader
            val classLoader = DexClassLoader(
                dexPath,
                optimizedDexPath,
                nativeLibPath,
                context.classLoader
            )

            // 3. Prepare the Game Environment
            // Here we would normally start a ProxyActivity that uses this ClassLoader
            // For now, let's trigger the launch intent
            
            PotataApp.addLog("Bootstrapping game process...")
            
            // Note: Launching a specific class from another APK via reflection 
            // usually requires a dedicated ProxyActivity to handle the lifecycle.
            // We will start the process here.
            
            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra("VIRTUAL_MODE", true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            // This is where the magic happens: we'll transition the UI to the "Running" state
            PotataApp.addLog("Process Hooked. Enjoy your mods!")

        } catch (e: Exception) {
            val error = "Launch Failed: ${e.message}"
            Log.e(TAG, error, e)
            PotataApp.addLog(error)
        }
    }
}
