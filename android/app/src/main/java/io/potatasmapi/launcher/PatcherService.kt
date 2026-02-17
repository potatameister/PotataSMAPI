package io.potatasmapi.launcher

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

/**
 * The VirtualExtractor (Mono-Redirect Edition).
 * Extracts game assemblies to allow filesystem-level injection.
 */
class PatcherService(private val context: Context) {
    private val TAG = "PotataVirtual"

    private fun log(msg: String) {
        PotataApp.addLog(msg)
    }

    fun importGame(originalApkPaths: List<String>) {
        log("Initializing Mono-Redirect Workspace...")

        val virtualRoot = File(context.filesDir, "virtual/stardew")
        if (virtualRoot.exists()) virtualRoot.deleteRecursively()
        virtualRoot.mkdirs()

        val libDir = File(virtualRoot, "lib").apply { mkdirs() }
        val assetsDir = File(virtualRoot, "assets").apply { mkdirs() }
        
        originalApkPaths.forEachIndexed { index, path ->
            val sourceFile = if (path.startsWith("content://")) {
                val tmp = File(context.cacheDir, "temp_source_$index.apk")
                copyUriToFile(android.net.Uri.parse(path), tmp)
                tmp
            } else {
                File(path)
            }
            
            val targetName = if (index == 0) "base.apk" else "split_$index.apk"
            val virtualApk = File(virtualRoot, targetName)

            // CRITICAL: We copy the APK UNTOUCHED to keep the signature valid
            log("Mounting Segment: ${sourceFile.name}")
            sourceFile.copyTo(virtualApk, overwrite = true)
            
            // Extract assemblies and libs for redirection
            extractRedirectionTargets(virtualApk, libDir, assetsDir)
            
            virtualApk.setReadOnly()
            if (path.startsWith("content://")) sourceFile.delete()
        }

        // 3. Inject SMAPI into the extracted filesystem
        log("Injecting SMAPI into redirection path...")
        val assemblyDir = File(assetsDir, "assemblies").apply { mkdirs() }
        
        // Replace Stardew Valley.dll with SMAPI
        context.assets.open("StardewModdingAPI.dll").use { input ->
            File(assemblyDir, "Stardew Valley.dll").outputStream().use { output ->
                input.copyTo(output)
            }
        }
        
        log("Import Successful! Mono-Redirect Ready.")
        File(virtualRoot, "virtual.ready").createNewFile()
    }

    private fun extractRedirectionTargets(apk: File, libDir: File, assetsDir: File) {
        val preferredAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "armeabi-v7a"
        ZipFile(apk).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                // Extract DLLs (Assemblies)
                if (entry.name.contains("assemblies/") && entry.name.endsWith(".dll")) {
                    val target = File(assetsDir, entry.name.removePrefix("assets/"))
                    target.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { it.copyTo(target.outputStream()) }
                    
                    // If it's the vanilla DLL, rename it so SMAPI can load it
                    if (entry.name.endsWith("Stardew Valley.dll", ignoreCase = true)) {
                        val vanilla = File(target.parentFile, "StardewValley.Vanilla.dll")
                        target.renameTo(vanilla)
                    }
                }
                
                // Extract Native Libs
                if (entry.name.startsWith("lib/$preferredAbi/") && entry.name.endsWith(".so")) {
                    val target = File(libDir, File(entry.name).name)
                    if (!target.exists()) {
                        zip.getInputStream(entry).use { it.copyTo(target.outputStream()) }
                    }
                }
            }
        }
    }

    private fun copyUriToFile(uri: android.net.Uri, outFile: File) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        } ?: throw Exception("URI Access Failed")
    }
}
