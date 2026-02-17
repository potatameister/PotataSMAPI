package io.potatasmapi.launcher

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

/**
 * The VirtualExtractor (Clean Slate Edition).
 */
class PatcherService(private val context: Context) {
    private val TAG = "PotataVirtual"

    private fun log(msg: String) {
        PotataApp.addLog(msg)
    }

    fun importGame(originalApkPaths: List<String>) {
        log("--- STARTING CLEAN IMPORT ---")

        val virtualRoot = File(context.filesDir, "virtual/stardew")
        if (virtualRoot.exists()) virtualRoot.deleteRecursively()
        virtualRoot.mkdirs()

        val libDir = File(virtualRoot, "lib").apply { mkdirs() }
        
        // Dynamic SD Card Path
        val sdcardRoot = File(Environment.getExternalStorageDirectory(), "PotataSMAPI")
        if (!sdcardRoot.exists()) sdcardRoot.mkdirs()
        
        // CLEAN SLATE: Wipe previous attempt files
        log("Cleaning old redirection files...")
        File(sdcardRoot, "assets").deleteRecursively()
        File(sdcardRoot, "assemblies").deleteRecursively()
        
        val assetsDir = File(sdcardRoot, "assets").apply { mkdirs() }
        val assemblyDir = File(sdcardRoot, "assemblies").apply { mkdirs() }
        
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

            log("Cloning segment: $targetName")
            sourceFile.copyTo(virtualApk, overwrite = true)
            
            extractEssentialFiles(virtualApk, libDir, assemblyDir, assetsDir)
            
            virtualApk.setReadOnly()
            if (path.startsWith("content://")) sourceFile.delete()
        }

        // Inject SMAPI
        log("Injecting SMAPI core to SD Card...")
        context.assets.open("StardewModdingAPI.dll").use { input ->
            val target = File(assemblyDir, "Stardew Valley.dll")
            target.outputStream().use { input.copyTo(it) }
        }
        
        log("--- IMPORT SUCCESSFUL ---")
        File(virtualRoot, "virtual.ready").createNewFile()
    }

    private fun extractEssentialFiles(apk: File, libDir: File, assemblyDir: File, assetsDir: File) {
        val preferredAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "armeabi-v7a"
        ZipFile(apk).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val name = entry.name
                
                // 1. Assemblies to SD Card
                if (name.contains("assemblies/") && name.endsWith(".dll")) {
                    val fileName = name.substringAfterLast("/")
                    val target = File(assemblyDir, fileName)
                    
                    zip.getInputStream(entry).use { input ->
                        val bytes = input.readBytes()
                        target.writeBytes(patchBinaryPaths(bytes))
                    }
                    
                    if (fileName.equals("Stardew Valley.dll", ignoreCase = true)) {
                        target.renameTo(File(assemblyDir, "StardewValley.Vanilla.dll"))
                    }
                }
                
                // 2. Content to SD Card
                if (name.startsWith("assets/Content/")) {
                    val target = File(assetsDir, name.removePrefix("assets/"))
                    target.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { it.copyTo(target.outputStream()) }
                }
                
                // 3. Native Libs to Internal
                if (name.startsWith("lib/$preferredAbi/") && name.endsWith(".so")) {
                    val target = File(libDir, name.substringAfterLast("/"))
                    if (!target.exists()) {
                        zip.getInputStream(entry).use { it.copyTo(target.outputStream()) }
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
                for (j in replace.indices) patched[i + j] = replace[j]
                i += search.size
            } else i++
        }
        return patched
    }

    private fun copyUriToFile(uri: android.net.Uri, outFile: File) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        } ?: throw Exception("URI Access Failed")
    }
}
