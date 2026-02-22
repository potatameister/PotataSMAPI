package io.potatasmapi.launcher

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * PatcherService: The Virtual Environment Architect.
 * It prepares the game for modded execution by extracting assemblies and neutralizing original assets.
 */
class PatcherService(private val context: Context) {
    private val TAG = "PotataVirtual"

    private fun log(msg: String) {
        PotataApp.addLog(msg)
    }

    fun importGame(originalApkPaths: List<String>) {
        log("--- VIRTUAL IMPORT START ---")

        val virtualRoot = File(context.filesDir, "virtual/stardew")
        if (virtualRoot.exists()) virtualRoot.deleteRecursively()
        virtualRoot.mkdirs()

        val libDir = File(virtualRoot, "lib").apply { mkdirs() }
        val sdcardRoot = File(Environment.getExternalStorageDirectory(), "PotataSMAPI")
        
        // Purge old files
        log("Clearing environment...")
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

            log("Processing: $targetName")
            
            // Extract and neutralize in one pass
            extractAndNeutralize(sourceFile, virtualApk, libDir, assemblyDir)
            
            if (path.startsWith("content://")) sourceFile.delete()
        }

        // 3. Inject SMAPI
        log("Deploying SMAPI Engine...")
        try {
            context.assets.open("StardewModdingAPI.dll").use { input ->
                val target = File(assemblyDir, "Stardew Valley.dll")
                target.outputStream().use { input.copyTo(it) }
            }
            log("SMAPI v4.5.1 Primed.")
        } catch (e: Exception) {
            log("SMAPI Injection Failed: ${e.message}")
        }
        
        log("--- VIRTUAL READY ---")
        File(virtualRoot, "virtual.ready").createNewFile()
    }

    private fun extractAndNeutralize(source: File, targetApk: File, libDir: File, assemblyDir: File) {
        val preferredAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "armeabi-v7a"
        
        ZipInputStream(source.inputStream()).use { zis ->
            ZipOutputStream(targetApk.outputStream()).use { zos ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    var shouldCopy = true

                    // 1. Native Libs Extraction
                    if (name.contains("lib/") && name.contains(preferredAbi) && name.endsWith(".so")) {
                        val libFile = File(libDir, name.substringAfterLast("/"))
                        if (!libFile.exists()) {
                            libFile.outputStream().use { zis.copyTo(it) }
                        }
                    }

                    // 2. Assemblies Extraction & Neutralization
                    // We check for "assemblies/" anywhere in the path to be safe (Xamarin split APKs can be weird)
                    if (name.contains("assemblies/") && (name.endsWith(".dll") || name.endsWith(".json") || name.endsWith(".config") || name.endsWith(".dll.so"))) {
                        val fileName = name.substringAfterLast("/")
                        val targetDll = File(assemblyDir, fileName)
                        
                        // Extract to SD card for redirection
                        targetDll.outputStream().use { zis.copyTo(it) }
                        
                        // Special handling for the main game DLL
                        if (fileName.equals("Stardew Valley.dll", ignoreCase = true) || fileName.equals("StardewValley.dll", ignoreCase = true)) {
                            targetDll.renameTo(File(assemblyDir, "StardewValley.Vanilla.dll"))
                            log("Hijacked: $fileName")
                        }

                        // NEUTRALIZE: We skip copying assemblies to the virtual APK 
                        // to force Mono to look at MONO_PATH (SD Card).
                        shouldCopy = false 
                    }

                    if (shouldCopy) {
                        try {
                            zos.putNextEntry(ZipEntry(name))
                            zis.copyTo(zos)
                            zos.closeEntry()
                        } catch (e: Exception) {
                            // Ignore duplicates or errors
                        }
                    }

                    zis.closeEntry()
                    entry = zis.nextEntry
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
