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
 * The VirtualExtractor (Deep Binary Hijacker).
 * Physically patches hardcoded paths and sterilizes the APK.
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

            log("Deep Patching Segment: ${sourceFile.name}")
            sterilizeAndPatchApk(sourceFile, virtualApk, libDir)
            
            virtualApk.setReadOnly()
            if (path.startsWith("content://")) sourceFile.delete()
        }
        
        log("Import Successful! Binary strings hijacked.")
        File(virtualRoot, "virtual.ready").createNewFile()
    }

    private fun sterilizeAndPatchApk(source: File, target: File, libDir: File) {
        val preferredAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "armeabi-v7a"
        
        ZipFile(source).use { zip ->
            ZipOutputStream(FileOutputStream(target)).use { out ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name
                    
                    // 1. Blob Killer
                    if (name.contains("assemblies.blob") || name.contains("assemblies.manifest")) {
                        continue
                    }

                    // 2. DLL Redirection
                    if (name.endsWith("assemblies/Stardew Valley.dll", ignoreCase = true)) {
                        out.putNextEntry(ZipEntry("assemblies/StardewValley.Vanilla.dll"))
                        zip.getInputStream(entry).use { it.copyTo(out) }
                        out.closeEntry()
                        continue
                    }
                    
                    // 3. Native Lib Patching & Extraction
                    if (name.startsWith("lib/$preferredAbi/") && name.endsWith(".so")) {
                        val libFile = File(libDir, File(name).name)
                        zip.getInputStream(entry).use { input ->
                            val bytes = input.readBytes()
                            val patchedBytes = patchBinaryPaths(bytes)
                            libFile.writeBytes(patchedBytes)
                        }
                    }

                    // 4. Content Path Hijacking
                    val newEntry = ZipEntry(name)
                    out.putNextEntry(newEntry)
                    zip.getInputStream(entry).use { input ->
                        if (name.endsWith(".dll") || name.endsWith(".xml") || name.endsWith(".json")) {
                            val bytes = input.readBytes()
                            out.write(patchBinaryPaths(bytes))
                        } else {
                            input.copyTo(out)
                        }
                    }
                    out.closeEntry()
                }

                // 5. Inject SMAPI
                out.putNextEntry(ZipEntry("assemblies/Stardew Valley.dll"))
                context.assets.open("StardewModdingAPI.dll").use { it.copyTo(out) }
                out.closeEntry()
            }
        }
    }

    /**
     * Replaces hardcoded 'StardewValley' strings with 'PotataSMAPI' in binary data.
     * This forces the game to use our folder for saves and data.
     */
    private fun patchBinaryPaths(data: ByteArray): ByteArray {
        val search = "StardewValley".toByteArray()
        val replace = "PotataSMAPI".toByteArray()
        var patched = data
        
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
