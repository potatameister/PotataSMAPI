package io.potatasmapi.launcher

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

/**
 * The VirtualExtractor (Hyper-Diagnostic Edition).
 */
class PatcherService(private val context: Context) {
    private val TAG = "PotataVirtual"

    private fun log(msg: String) {
        PotataApp.addLog(msg)
    }

    fun importGame(originalApkPaths: List<String>) {
        log("--- HYPER-IMPORT START ---")

        val virtualRoot = File(context.filesDir, "virtual/stardew")
        if (virtualRoot.exists()) virtualRoot.deleteRecursively()
        virtualRoot.mkdirs()

        val libDir = File(virtualRoot, "lib").apply { mkdirs() }
        val sdcardRoot = File(Environment.getExternalStorageDirectory(), "PotataSMAPI")
        
        // PURGE GHOSTS
        log("Purging old redirection folders...")
        File(sdcardRoot, "assets").deleteRecursively()
        File(sdcardRoot, "assemblies").deleteRecursively()
        
        val assetsDir = File(sdcardRoot, "assets").apply { mkdirs() }
        val assemblyDir = File(sdcardRoot, "assemblies").apply { mkdirs() }
        
        var totalPatched = 0
        
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
            
            totalPatched += extractAndPatch(virtualApk, libDir, assemblyDir, assetsDir)
            
            virtualApk.setReadOnly()
            if (path.startsWith("content://")) sourceFile.delete()
        }

        // 3. Inject SMAPI
        log("Injecting SMAPI core...")
        context.assets.open("StardewModdingAPI.dll").use { input ->
            val target = File(assemblyDir, "Stardew Valley.dll")
            target.outputStream().use { input.copyTo(it) }
        }
        
        log("--- IMPORT SUCCESSFUL ---")
        log("Files Patched: $totalPatched")
        log("Engine: SMAPI v4.5.1 Primed.")
        File(virtualRoot, "virtual.ready").createNewFile()
    }

    private fun extractAndPatch(apk: File, libDir: File, assemblyDir: File, assetsDir: File): Int {
        val preferredAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "armeabi-v7a"
        var count = 0
        ZipFile(apk).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val name = entry.name
                
                // 1. Assemblies & Configs
                if (name.contains("assemblies/") && (name.endsWith(".dll") || name.endsWith(".json") || name.endsWith(".config"))) {
                    val target = File(assemblyDir, name.substringAfterLast("/"))
                    zip.getInputStream(entry).use { input ->
                        val bytes = input.readBytes()
                        // val patched = patchBinaryPaths(bytes) // Disabled to prevent corruption
                        // if (patched !== bytes) count++
                        target.writeBytes(bytes)
                    }
                    if (name.endsWith("Stardew Valley.dll", ignoreCase = true)) {
                        target.renameTo(File(assemblyDir, "StardewValley.Vanilla.dll"))
                    }
                }
                
                // 2. Content (SKIP EXTRACTION unless specific files are needed)
                // Note: assets/Content is mounted via addAssetPath in PotataApp.mountAssets
                // We only extract if we need to patch specific path-heavy XMLs or if we want to support overrides easily.
                // For now, let's keep it minimal as per the efficiency plan.
                /*
                if (name.startsWith("assets/Content/")) {
                    // Selective extraction logic could go here
                }
                */
                
                // 3. Native Libs
                if (name.startsWith("lib/$preferredAbi/") && name.endsWith(".so")) {
                    val target = File(libDir, name.substringAfterLast("/"))
                    if (!target.exists()) {
                        zip.getInputStream(entry).use { input ->
                            val bytes = input.readBytes()
                            // val patched = patchBinaryPaths(bytes) // Disabled to prevent corruption
                            // if (patched !== bytes) count++
                            target.writeBytes(bytes)
                        }
                    }
                }
            }
        }
        return count
    }

    private fun patchBinaryPaths(data: ByteArray): ByteArray {
        // ... (Disabled) ...
        return data
    }

    private fun copyUriToFile(uri: android.net.Uri, outFile: File) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        } ?: throw Exception("URI Access Failed")
    }
}
