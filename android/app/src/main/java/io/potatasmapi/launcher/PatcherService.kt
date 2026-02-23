package io.potatasmapi.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class PatcherService(private val context: Context) {
    private val TAG = "PotataPatcher"
    private val MODDED_PACKAGE = "com.potatasmapi.stardew"

    private fun log(msg: String) {
        PotataApp.addLog(msg)
    }

    fun importAndPatchGame(apkPath: String, onComplete: (Boolean, String) -> Unit) {
        Thread {
            try {
                log("=== APK PATCHING START ===")
                
                val workDir = File(context.cacheDir, "patcher_work")
                if (workDir.exists()) workDir.deleteRecursively()
                workDir.mkdirs()

                val sourceApk = if (apkPath.startsWith("content://")) {
                    val tempFile = File(workDir, "source.apk")
                    copyUriToFile(Uri.parse(apkPath), tempFile)
                    tempFile
                } else {
                    File(apkPath)
                }

                log("Extracting APK...")
                val extractedDir = File(workDir, "extracted")
                extractApk(sourceApk, extractedDir)

                log("Modifying AndroidManifest.xml...")
                modifyManifest(extractedDir)

                log("Injecting SMAPI...")
                injectSmapi(extractedDir)

                log("Repacking APK...")
                val unsignedApk = File(workDir, "unsigned.apk")
                repackApk(extractedDir, unsignedApk)

                log("Signing APK...")
                val outputDir = File(context.getExternalFilesDir(null), "PotataSMAPI")
                outputDir.mkdirs()
                val finalApk = File(outputDir, "StardewValley.Modded.apk")
                signApk(unsignedApk, finalApk)

                workDir.deleteRecursively()

                log("=== PATCH COMPLETE ===")
                log("APK ready at: ${finalApk.absolutePath}")
                
                context.runOnUiThread {
                    promptInstall(finalApk, onComplete)
                }

            } catch (e: Exception) {
                log("FATAL: ${e.message}")
                e.printStackTrace()
                context.runOnUiThread {
                    onComplete(false, e.message ?: "Unknown error")
                }
            }
        }.start()
    }

    private fun promptInstall(apkFile: File, onComplete: (Boolean, String) -> Unit) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            onComplete(true, "Modded APK created! Install it, then launch from PotataSMAPI.")
        } catch (e: Exception) {
            onComplete(false, "Failed to open installer: ${e.message}")
        }
    }

    private fun extractApk(apkFile: File, outputDir: File) {
        outputDir.mkdirs()
        
        ZipInputStream(apkFile.inputStream()).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val outFile = File(outputDir, entry.name)
                
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { output ->
                        zis.copyTo(output)
                    }
                }
                entry = zis.nextEntry
            }
        }
        log("Extracted ${outputDir.listFiles()?.size ?: 0} files")
    }

    private fun modifyManifest(decompiledDir: File) {
        val manifestFile = File(decompiledDir, "AndroidManifest.xml")
        if (!manifestFile.exists()) {
            throw Exception("AndroidManifest.xml not found")
        }

        try {
            var content = manifestFile.readText()
            
            content = content.replace(Regex("""package="[^"]*""""), """package="$MODDED_PACKAGE"""")
            content = content.replace("com.chucklefish.stardewvalley", MODDED_PACKAGE)
            
            manifestFile.writeText(content)
            log("Package name changed to $MODDED_PACKAGE")
        } catch (e: Exception) {
            throw Exception("Failed to modify manifest: ${e.message}")
        }
    }

    private fun injectSmapi(decompiledDir: File) {
        val assetsDir = File(decompiledDir, "assets")
        assetsDir.mkdirs()
        
        context.assets.open("StardewModdingAPI.dll").use { input ->
            File(assetsDir, "StardewModdingAPI.dll").outputStream().use { output ->
                input.copyTo(output)
            }
        }
        log("SMAPI DLL injected")
    }

    private fun repackApk(sourceDir: File, outputApk: File) {
        outputApk.parentFile?.mkdirs()
        
        ZipOutputStream(FileOutputStream(outputApk)).use { zos ->
            sourceDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val relativePath = file.relativeTo(sourceDir).path
                    zos.putNextEntry(ZipEntry(relativePath))
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
        log("APK repacked: ${outputApk.length() / 1024}KB")
    }

    private fun signApk(unsignedApk: File, signedApk: File) {
        try {
            val tempDir = File(context.cacheDir, "signing")
            tempDir.mkdirs()
            
            val keystoreFile = File(tempDir, "signing.keystore")
            context.assets.open("potata_patcher.jks").use { input ->
                keystoreFile.outputStream().use { output -> input.copyTo(output) }
            }
            
            val alignedApk = File(tempDir, "aligned.apk")
            
            try {
                val zipalign = Runtime.getRuntime().exec(
                    arrayOf("zipalign", "-f", "4", unsignedApk.absolutePath, alignedApk.absolutePath)
                )
                zipalign.waitFor()
            } catch (e: Exception) {
                unsignedApk.copyTo(alignedApk)
            }

            val jarsigner = Runtime.getRuntime().exec(
                arrayOf(
                    "jarsigner",
                    "-sigalg", "SHA256withRSA",
                    "-digestalg", "SHA-256",
                    "-keystore", keystoreFile.absolutePath,
                    "-storepass", "potata-patcher-key-2026",
                    "-keypass", "potata-patcher-key-2026",
                    alignedApk.absolutePath,
                    "potata_patcher"
                )
            )
            
            val result = jarsigner.waitFor()
            
            if (result == 0) {
                alignedApk.copyTo(signedApk, overwrite = true)
                log("APK signed successfully: ${signedApk.length() / 1024}KB")
            } else {
                throw Exception("jarsigner failed")
            }
            
            tempDir.deleteRecursively()
            
        } catch (e: Exception) {
            log("Signing note: ${e.message}")
            signedApk.delete()
            unsignedApk.copyTo(signedApk)
            log("APK copied (unsigned - install with 'Allow unknown sources')")
        }
    }

    private fun copyUriToFile(uri: Uri, outFile: File) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        } ?: throw Exception("Failed to read URI: $uri")
    }

    private fun Context.runOnUiThread(action: () -> Unit) {
        val activity = this as? android.app.Activity
        if (activity != null && !activity.isFinishing) {
            activity.runOnUiThread { action() }
        } else {
            android.os.Handler(android.os.Looper.getMainLooper()).post { action() }
        }
    }
}
