package io.potatasmapi.launcher

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

/**
 * The VirtualExtractor (Refined).
 * Surgically prepares the "Virtual Cartridge" for the loader.
 */
class PatcherService(private val context: Context) {
    private val TAG = "PotataVirtual"

    private fun log(msg: String) {
        PotataApp.addLog(msg)
    }

    fun importGame(originalApkPaths: List<String>) {
        log("Mounting Virtual Workspace...")

        val virtualRoot = File(context.filesDir, "virtual/stardew")
        if (virtualRoot.exists()) virtualRoot.deleteRecursively()
        virtualRoot.mkdirs()

        val libDir = File(virtualRoot, "lib").apply { mkdirs() }
        
        log("Scanning Cartridge...")
        
        val apkFiles = originalApkPaths.mapIndexed { index, path ->
            val sourceFile = if (path.startsWith("content://")) {
                val tmp = File(context.cacheDir, "temp_source_$index.apk")
                copyUriToFile(android.net.Uri.parse(path), tmp)
                tmp
            } else {
                File(path)
            }
            
            val targetName = if (index == 0) "base.apk" else "split_$index.apk"
            val virtualApk = File(virtualRoot, targetName)
            sourceFile.copyTo(virtualApk, overwrite = true)
            virtualApk.setReadOnly()
            
            if (path.startsWith("content://")) sourceFile.delete()
            virtualApk
        }

        try {
            log("Architecture: ${Build.SUPPORTED_ABIS.joinToString()}")
            val preferredAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "armeabi-v7a"
            log("Selecting Engine: $preferredAbi")

            apkFiles.forEach { apkFile ->
                log("Unpacking: ${apkFile.name}")
                ZipFile(apkFile).use { zip ->
                    val entries = zip.entries().asSequence().toList()
                    
                    // 1. Extract Native Engine (.so files)
                    val libEntries = entries.filter { it.name.startsWith("lib/$preferredAbi/") && it.name.endsWith(".so") }
                    if (libEntries.isEmpty() && preferredAbi == "arm64-v8a") {
                        entries.filter { it.name.startsWith("lib/armeabi-v7a/") && it.name.endsWith(".so") }.forEach { entry ->
                            val target = File(libDir, File(entry.name).name)
                            if (!target.exists()) {
                                zip.getInputStream(entry).use { input -> target.outputStream().use { output -> input.copyTo(output) } }
                            }
                        }
                    } else {
                        libEntries.forEach { entry ->
                            val target = File(libDir, File(entry.name).name)
                            if (!target.exists()) {
                                zip.getInputStream(entry).use { input -> target.outputStream().use { output -> input.copyTo(output) } }
                            }
                        }
                    }

                    // 2. Extract Assets (Content + DLLs)
                    entries.filter { (it.name.startsWith("assets/Content/") || it.name.endsWith(".dll")) && !it.isDirectory }.forEach { entry ->
                        val target = File(virtualRoot, if (entry.name.startsWith("assets/")) entry.name else "assets/${entry.name}")
                        if (!target.exists()) {
                            target.parentFile?.mkdirs()
                            zip.getInputStream(entry).use { input -> target.outputStream().use { output -> input.copyTo(output) } }
                        }
                    }
                }
            }

            // 3. Redirect DLLs
            log("Redirecting assemblies...")
            val vanillaDll = File(virtualRoot, "assets/Stardew Valley.dll")
            if (vanillaDll.exists()) {
                vanillaDll.renameTo(File(virtualRoot, "assets/StardewValley.Vanilla.dll"))
            }

            // 4. Inject SMAPI Engine
            log("Injecting SMAPI Core...")
            context.assets.open("StardewModdingAPI.dll").use { input ->
                File(virtualRoot, "assets/Stardew Valley.dll").outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            log("Import Successful!")
            log("SMAPI Hybrid Injector Ready.")
            File(virtualRoot, "virtual.ready").createNewFile()

        } catch (e: Exception) {
            log("Import Error: ${e.localizedMessage}")
            throw e
        }
    }

    private fun copyUriToFile(uri: android.net.Uri, outFile: File) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        } ?: throw Exception("URI Access Failed")
    }
}
