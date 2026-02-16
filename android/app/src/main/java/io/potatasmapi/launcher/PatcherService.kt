package io.potatasmapi.launcher

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

/**
 * The VirtualExtractor (Refined).
 * Surgically prepares the "Virtual Cartridge" for the loader.
 */
class PatcherService(private val context: Context) {
    private val TAG = "PotataVirtual"

    private fun log(msg: String) {
        PotataApp.addLog(msg)
    }

    fun importGame(originalApkPath: String) {
        log("Mounting Virtual Workspace...")

        val virtualRoot = File(context.filesDir, "virtual/stardew")
        if (virtualRoot.exists()) virtualRoot.deleteRecursively()
        virtualRoot.mkdirs()

        val libDir = File(virtualRoot, "lib").apply { mkdirs() }
        
        log("Scanning Cartridge: ${File(originalApkPath).name}")
        
        val sourceFile = if (originalApkPath.startsWith("content://")) {
            val tmp = File(context.cacheDir, "temp_source.apk")
            copyUriToFile(android.net.Uri.parse(originalApkPath), tmp)
            tmp
        } else {
            File(originalApkPath)
        }

        // Keep the APK for resource mapping and code loading
        val virtualApk = File(virtualRoot, "base.apk")
        sourceFile.copyTo(virtualApk, overwrite = true)
        virtualApk.setReadOnly() // Required by Android to load DEX code safely

        try {
            log("Architecture: ${Build.SUPPORTED_ABIS.joinToString()}")
            val preferredAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "armeabi-v7a"
            log("Selecting Engine: $preferredAbi")

            ZipFile(sourceFile).use { zip ->
                val entries = zip.entries().asSequence().toList()
                
                // 1. Extract Native Engine (.so files)
                // We extract them to a separate dir for the ClassLoader to find them easily
                log("Extracting Native Engine...")
                val libEntries = entries.filter { it.name.startsWith("lib/$preferredAbi/") && it.name.endsWith(".so") }
                if (libEntries.isEmpty() && preferredAbi == "arm64-v8a") {
                    log("Warning: arm64 engine missing, falling back to 32-bit...")
                    entries.filter { it.name.startsWith("lib/armeabi-v7a/") && it.name.endsWith(".so") }.forEach { entry ->
                        val target = File(libDir, File(entry.name).name)
                        zip.getInputStream(entry).use { input -> target.outputStream().use { output -> input.copyTo(output) } }
                    }
                } else {
                    libEntries.forEach { entry ->
                        val target = File(libDir, File(entry.name).name)
                        zip.getInputStream(entry).use { input -> target.outputStream().use { output -> input.copyTo(output) } }
                    }
                }

                // 2. Extract Assets (Content)
                log("Unpacking Game Assets...")
                entries.filter { it.name.startsWith("assets/") && !it.isDirectory }.forEach { entry ->
                    val target = File(virtualRoot, entry.name)
                    target.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input -> target.outputStream().use { output -> input.copyTo(output) } }
                }

                // 3. Inject SMAPI Engine
                log("Injecting SMAPI Core...")
                context.assets.open("StardewModdingAPI.dll").use { input ->
                    File(virtualRoot, "StardewModdingAPI.dll").outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            
            log("Import Successful!")
            log("Cartridge ready for Virtual Launch.")
            File(virtualRoot, "virtual.ready").createNewFile()

        } catch (e: Exception) {
            log("Import Error: ${e.localizedMessage}")
            throw e
        } finally {
            if (originalApkPath.startsWith("content://")) sourceFile.delete()
        }
    }

    private fun copyUriToFile(uri: android.net.Uri, outFile: File) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        } ?: throw Exception("URI Access Failed")
    }
}
