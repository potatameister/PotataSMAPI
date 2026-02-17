package io.potatasmapi.launcher

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

/**
 * The VirtualExtractor (Final Fix Edition).
 * Extracts both assemblies and game content to ensure MonoGame has all its assets.
 */
class PatcherService(private val context: Context) {
    private val TAG = "PotataVirtual"

    private fun log(msg: String) {
        PotataApp.addLog(msg)
    }

    fun importGame(originalApkPaths: List<String>) {
        log("Surgical Import Starting...")

        val virtualRoot = File(context.filesDir, "virtual/stardew")
        if (virtualRoot.exists()) virtualRoot.deleteRecursively()
        virtualRoot.mkdirs()

        val libDir = File(virtualRoot, "lib").apply { mkdirs() }
        val assetsDir = File(virtualRoot, "assets").apply { mkdirs() }
        val assemblyDir = File(assetsDir, "assemblies").apply { mkdirs() }
        
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

            log("Cloning: ${sourceFile.name}")
            sourceFile.copyTo(virtualApk, overwrite = true)
            
            // Extract code and content
            extractAssets(virtualApk, libDir, assetsDir, assemblyDir)
            
            virtualApk.setReadOnly()
            if (path.startsWith("content://")) sourceFile.delete()
        }

        // Inject SMAPI
        log("Bridging Modded Engine...")
        context.assets.open("StardewModdingAPI.dll").use { input ->
            // In Mono-Redirect, SMAPI must be 'Stardew Valley.dll' to override the internal code
            File(assemblyDir, "Stardew Valley.dll").outputStream().use { output ->
                input.copyTo(output)
            }
        }
        
        log("Import Complete. System Ready.")
        File(virtualRoot, "virtual.ready").createNewFile()
    }

    private fun extractAssets(apk: File, libDir: File, assetsDir: File, assemblyDir: File) {
        val preferredAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "armeabi-v7a"
        ZipFile(apk).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val name = entry.name
                
                // 1. Extract DLLs (Assemblies)
                if (name.contains("assemblies/") && name.endsWith(".dll")) {
                    val target = File(assetsDir, name.removePrefix("assets/"))
                    target.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { it.copyTo(target.outputStream()) }
                    
                    if (name.endsWith("Stardew Valley.dll", ignoreCase = true)) {
                        val vanilla = File(target.parentFile, "StardewValley.Vanilla.dll")
                        target.renameTo(vanilla)
                    }
                }
                
                // 2. Extract Game Content (Essential for textures/audio)
                if (name.startsWith("assets/Content/")) {
                    val target = File(virtualRoot, name)
                    target.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { it.copyTo(target.outputStream()) }
                }
                
                // 3. Extract Native Libs
                if (name.startsWith("lib/$preferredAbi/") && name.endsWith(".so")) {
                    val target = File(libDir, File(name).name)
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
