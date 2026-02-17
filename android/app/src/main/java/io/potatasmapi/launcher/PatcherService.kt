package io.potatasmapi.launcher

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * The VirtualExtractor (Final Form).
 * Completely sterilizes the game APKs by removing vanilla code segments.
 */
class PatcherService(private val context: Context) {
    private val TAG = "PotataVirtual"

    private fun log(msg: String) {
        PotataApp.addLog(msg)
    }

    fun importGame(originalApkPaths: List<String>) {
        log("Surgical Sterilization Starting...")

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

            log("Sterilizing Segment: ${sourceFile.name}")
            sterilizeApk(sourceFile, virtualApk, libDir)
            
            virtualApk.setReadOnly()
            if (path.startsWith("content://")) sourceFile.delete()
        }

        // 3. Inject SMAPI Engine
        log("Injecting SMAPI Core...")
        context.assets.open("StardewModdingAPI.dll").use { input ->
            File(assetsDir, "Stardew Valley.dll").outputStream().use { output ->
                input.copyTo(output)
            }
        }
        
        log("Import Successful! ALL segments sterilized.")
        File(virtualRoot, "virtual.ready").createNewFile()
    }

    /**
     * Removes ANY file named 'Stardew Valley.dll' from the APK segment.
     */
    private fun sterilizeApk(source: File, target: File, libDir: File) {
        val preferredAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "armeabi-v7a"
        
        ZipFile(source).use { zip ->
            ZipOutputStream(FileOutputStream(target)).use { out ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    
                    // The "Nuclear" Rule: Delete the vanilla engine from the archive
                    if (entry.name.contains("Stardew Valley.dll", ignoreCase = true)) {
                        log("Destroyed Vanilla Ghost: ${entry.name}")
                        continue 
                    }

                    // Extract Native Libs while we are here
                    if (entry.name.startsWith("lib/$preferredAbi/") && entry.name.endsWith(".so")) {
                        val libFile = File(libDir, File(entry.name).name)
                        if (!libFile.exists()) {
                            zip.getInputStream(entry).use { input ->
                                libFile.outputStream().use { output -> input.copyTo(output) }
                            }
                        }
                    }

                    // Copy everything else
                    val newEntry = ZipEntry(entry.name)
                    out.putNextEntry(newEntry)
                    zip.getInputStream(entry).use { input -> input.copyTo(out) }
                    out.closeEntry()
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
