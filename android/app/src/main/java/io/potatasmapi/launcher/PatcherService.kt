package io.potatasmapi.launcher

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

/**
 * The VirtualExtractor (Stable & Transparent).
 */
class PatcherService(private val context: Context) {
    private val TAG = "PotataVirtual"

    private fun log(msg: String) {
        PotataApp.addLog(msg)
    }

    fun importGame(originalApkPaths: List<String>) {
        log("--- STARTING IMPORT ---")

        val virtualRoot = File(context.filesDir, "virtual/stardew")
        if (virtualRoot.exists()) virtualRoot.deleteRecursively()
        virtualRoot.mkdirs()

        val libDir = File(virtualRoot, "lib").apply { mkdirs() }
        val sdcardRoot = File("/sdcard/PotataSMAPI")
        if (!sdcardRoot.exists()) sdcardRoot.mkdirs()
        
        val assemblyDir = File(sdcardRoot, "assemblies").apply { if (exists()) deleteRecursively(); mkdirs() }
        
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

            log("Processing: ${sourceFile.name} -> $targetName")
            sourceFile.copyTo(virtualApk, overwrite = true)
            
            extractEssentialFiles(virtualApk, libDir, assemblyDir)
            
            virtualApk.setReadOnly()
            if (path.startsWith("content://")) sourceFile.delete()
        }

        // Inject SMAPI
        log("Injecting SMAPI into SD Card...")
        context.assets.open("StardewModdingAPI.dll").use { input ->
            File(assemblyDir, "Stardew Valley.dll").outputStream().use { output ->
                input.copyTo(output)
            }
        }
        
        log("--- IMPORT COMPLETE ---")
        File(virtualRoot, "virtual.ready").createNewFile()
    }

    private fun extractEssentialFiles(apk: File, libDir: File, assemblyDir: File) {
        val preferredAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "armeabi-v7a"
        log("Extracting for ABI: $preferredAbi")
        
        ZipFile(apk).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val name = entry.name
                
                // 1. DLLs to SD Card
                if (name.contains("assemblies/") && name.endsWith(".dll")) {
                    val target = File(assemblyDir, name.substringAfter("assemblies/"))
                    target.parentFile?.mkdirs()
                    
                    zip.getInputStream(entry).use { input ->
                        val bytes = input.readBytes()
                        target.writeBytes(patchBinaryPaths(bytes))
                    }
                    
                    if (name.endsWith("Stardew Valley.dll", ignoreCase = true)) {
                        target.renameTo(File(target.parentFile, "StardewValley.Vanilla.dll"))
                    }
                }
                
                // 2. Native Engine Libs (The most important for fixing black screen)
                if (name.startsWith("lib/$preferredAbi/") && name.endsWith(".so")) {
                    val target = File(libDir, File(name).name)
                    if (!target.exists()) {
                        log("Extracting Native: ${target.name}")
                        zip.getInputStream(entry).use { input ->
                            val bytes = input.readBytes()
                            target.writeBytes(patchBinaryPaths(bytes))
                        }
                    }
                }
            }
        }
    }

    private fun patchBinaryPaths(data: ByteArray): ByteArray {
        val search = "StardewValley".toByteArray()
        val replace = "PotataSMAPI\u0000\u0000".toByteArray()
        val patched = data.copyOf()
        
        var i = 0
        while (i <= patched.size - search.size) {
            var match = true
            for (j in search.indices) {
                if (patched[i + j] != search[j]) {
                    match = false
                    break
                }
            }
            if (match) {
                for (j in replace.indices) {
                    patched[i + j] = replace[j]
                }
                i += search.size
            } else {
                i++
            }
        }
        return patched
    }

    private fun copyUriToFile(uri: android.net.Uri, outFile: File) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        } ?: throw Exception("URI Access Failed")
    }
}
