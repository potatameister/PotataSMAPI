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
            // Pass libDir to extract native libs during sterilization
            sterilizeAndInject(sourceFile, virtualApk, libDir)
            
            virtualApk.setReadOnly()
            if (path.startsWith("content://")) sourceFile.delete()
        }
        
        log("Import Successful! Blobs destroyed.")
        File(virtualRoot, "virtual.ready").createNewFile()
    }

    /**
     * Removes blobs/vanilla DLLs and INJECTS SMAPI directly into the APK.
     */
    private fun sterilizeAndInject(source: File, target: File, libDir: File) {
        val preferredAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "armeabi-v7a"
        
        ZipFile(source).use { zip ->
            ZipOutputStream(FileOutputStream(target)).use { out ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name
                    
                    // 1. DESTROY THE BLOB (The root cause of vanilla loading)
                    if (name.contains("assemblies.blob") || name.contains("assemblies.manifest")) {
                        continue
                    }

                    // 2. Rename Vanilla DLL (so we can load it later)
                    if (name.endsWith("assemblies/Stardew Valley.dll", ignoreCase = true)) {
                        // Write it as Vanilla.dll
                        out.putNextEntry(ZipEntry("assemblies/StardewValley.Vanilla.dll"))
                        zip.getInputStream(entry).use { input -> input.copyTo(out) }
                        out.closeEntry()
                        continue
                    }
                    
                    // 3. Extract Native Libs
                    if (name.startsWith("lib/$preferredAbi/") && name.endsWith(".so")) {
                        val libFile = File(libDir, File(name).name)
                        if (!libFile.exists()) {
                            zip.getInputStream(entry).use { input ->
                                libFile.outputStream().use { output -> input.copyTo(output) }
                            }
                        }
                    }

                    // Copy everything else normally
                    out.putNextEntry(ZipEntry(name))
                    zip.getInputStream(entry).use { input -> input.copyTo(out) }
                    out.closeEntry()
                }

                // 4. INJECT SMAPI CORE (Masquerading as the game)
                log("Injecting SMAPI into APK...")
                out.putNextEntry(ZipEntry("assemblies/Stardew Valley.dll"))
                context.assets.open("StardewModdingAPI.dll").use { input ->
                    input.copyTo(out)
                }
                out.closeEntry()
            }
        }
    }

    private fun copyUriToFile(uri: android.net.Uri, outFile: File) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        } ?: throw Exception("URI Access Failed")
    }
}
