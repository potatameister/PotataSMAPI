package io.potatasmapi.launcher

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

/**
 * The VirtualExtractor (Stable Patcher).
 * Combines Mono redirection with length-safe binary patching for isolated saves.
 */
class PatcherService(private val context: Context) {
    private val TAG = "PotataVirtual"

    private fun log(msg: String) {
        PotataApp.addLog(msg)
    }

    fun importGame(originalApkPaths: List<String>) {
        log("Initializing Stable Patcher Workspace...")

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

            // CRITICAL: Copy APK UNTOUCHED to keep signature valid (No more black screen)
            log("Mounting Segment: ${sourceFile.name}")
            sourceFile.copyTo(virtualApk, overwrite = true)
            
            // Extract and Patch redirection targets
            extractAndPatchRedirectionTargets(virtualApk, libDir, assetsDir)
            
            virtualApk.setReadOnly()
            if (path.startsWith("content://")) sourceFile.delete()
        }

        // 3. Inject SMAPI into the extracted filesystem
        log("Injecting SMAPI into redirection path...")
        val assemblyDir = File(assetsDir, "assemblies").apply { mkdirs() }
        
        // Replace Stardew Valley.dll with SMAPI (Our version is already length-safe)
        context.assets.open("StardewModdingAPI.dll").use { input ->
            File(assemblyDir, "Stardew Valley.dll").outputStream().use { output ->
                input.copyTo(output)
            }
        }
        
        log("Import Successful! Signature valid + Paths patched.")
        File(virtualRoot, "virtual.ready").createNewFile()
    }

    private fun extractAndPatchRedirectionTargets(apk: File, libDir: File, assetsDir: File) {
        val preferredAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "armeabi-v7a"
        ZipFile(apk).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                // 1. Extract and Patch DLLs (Assemblies)
                if (entry.name.contains("assemblies/") && entry.name.endsWith(".dll")) {
                    val target = File(assetsDir, entry.name.removePrefix("assets/"))
                    target.parentFile?.mkdirs()
                    
                    zip.getInputStream(entry).use { input ->
                        val bytes = input.readBytes()
                        // Patch hardcoded paths in the DLL bytes
                        val patchedBytes = patchBinaryPaths(bytes)
                        target.writeBytes(patchedBytes)
                    }
                    
                    // If it's the vanilla DLL, rename it so SMAPI can load it
                    if (entry.name.endsWith("Stardew Valley.dll", ignoreCase = true)) {
                        val vanilla = File(target.parentFile, "StardewValley.Vanilla.dll")
                        target.renameTo(vanilla)
                    }
                }
                
                // 2. Extract and Patch Native Libs
                if (entry.name.startsWith("lib/$preferredAbi/") && entry.name.endsWith(".so")) {
                    val target = File(libDir, File(entry.name).name)
                    if (!target.exists()) {
                        zip.getInputStream(entry).use { input ->
                            val bytes = input.readBytes()
                            val patchedBytes = patchBinaryPaths(bytes)
                            target.writeBytes(patchedBytes)
                        }
                    }
                }
            }
        }
    }

    /**
     * Replaces hardcoded 'StardewValley' strings with 'PotataSMAPI' in binary data.
     * CRITICAL: Preserves length (13 bytes) using null padding to avoid corruption.
     */
    private fun patchBinaryPaths(data: ByteArray): ByteArray {
        val search = "StardewValley".toByteArray()
        // 'PotataSMAPI' (11) + 2 null bytes = 13 bytes. File structure remains perfect.
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
