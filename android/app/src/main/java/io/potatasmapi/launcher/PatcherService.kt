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
 * The VirtualExtractor (Ultra).
 * Strips the vanilla C# code from the APK to force SMAPI loading.
 */
class PatcherService(private val context: Context) {
    private val TAG = "PotataVirtual"

    private fun log(msg: String) {
        PotataApp.addLog(msg)
    }

    fun importGame(originalApkPaths: List<String>) {
        log("Surgical Workspace Initialization...")

        val virtualRoot = File(context.filesDir, "virtual/stardew")
        if (virtualRoot.exists()) virtualRoot.deleteRecursively()
        virtualRoot.mkdirs()

        val libDir = File(virtualRoot, "lib").apply { mkdirs() }
        val assetsDir = File(virtualRoot, "assets").apply { mkdirs() }
        
        log("Importing ${originalApkPaths.size} segments...")
        
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

            if (index == 0) {
                log("Cleaning Engine: ${sourceFile.name}")
                cleanAndCopyApk(sourceFile, virtualApk, libDir)
            } else {
                log("Copying Segment: ${sourceFile.name}")
                sourceFile.copyTo(virtualApk, overwrite = true)
            }
            
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
        
        log("Import Successful!")
        log("Size Optimized. SMAPI Ready.")
        File(virtualRoot, "virtual.ready").createNewFile()
    }

    /**
     * Copies the APK but REMOVES the vanilla DLLs.
     * This forces Mono to look at our MONO_PATH for the modified DLLs.
     */
    private fun cleanAndCopyApk(source: File, target: File, libDir: File) {
        val preferredAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "armeabi-v7a"
        
        ZipFile(source).use { zip ->
            ZipOutputStream(FileOutputStream(target)).use { out ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    
                    // Skip the vanilla DLL (the core of the redirection)
                    if (entry.name.endsWith("Stardew Valley.dll", ignoreCase = true)) {
                        continue 
                    }

                    // Extract Native Libs to filesystem while copying others
                    if (entry.name.startsWith("lib/$preferredAbi/") && entry.name.endsWith(".so")) {
                        val libFile = File(libDir, File(entry.name).name)
                        zip.getInputStream(entry).use { input ->
                            libFile.outputStream().use { output -> input.copyTo(output) }
                        }
                    }

                    // Copy entry to new APK
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
