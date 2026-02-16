package io.potatasmapi.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry
import brut.androlib.ApkDecoder
import brut.directory.ExtFile
import com.android.apksig.ApkSigner
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate

class PatcherService(private val context: Context) {
    companion object {
        init {
            if (System.getProperty("os.name").isNullOrBlank()) {
                System.setProperty("os.name", "linux")
            }
        }
    }
    private val TAG = "PotataPatcher"
    private val KEYSTORE_PASS = "potata-patcher-key-2026"
    private val ALIAS = "potata_patcher"

    fun patchGame(originalApkPath: String) {
        Log.d(TAG, "Starting Zip-Inject Surgery for: $originalApkPath")

        val workspace = File(context.externalCacheDir, "patch_workspace")
        if (workspace.exists()) workspace.deleteRecursively()
        workspace.mkdirs()

        val originalApkFile = File(workspace, "base_game.apk")
        val decompiledDir = File(workspace, "decompiled")
        val classesOnlyApk = File(workspace, "classes_only.apk")
        val finalUnsigned = File(workspace, "final_unsigned.apk")
        val signedApk = File(workspace, "modded_stardew.apk")

        // 0. Copy APK
        if (originalApkPath.startsWith("content://")) {
            copyUriToFile(Uri.parse(originalApkPath), originalApkFile)
        } else {
            File(originalApkPath).copyTo(originalApkFile, true)
        }

        // 1. Decompile Smali (Fast mode)
        val configConstructor = brut.androlib.Config::class.java.getDeclaredConstructor()
        configConstructor.isAccessible = true
        val config = configConstructor.newInstance() as brut.androlib.Config
        config.frameworkDirectory = File(context.cacheDir, "apktool_framework").apply { mkdirs() }.absolutePath
        config.decodeResources = brut.androlib.Config.DECODE_RESOURCES_NONE

        val decoder = ApkDecoder(config, brut.directory.ExtFile(originalApkFile))
        decoder.decode(decompiledDir)
        
        // 2. Patch Smali & Manifest
        injectSmaliHook(decompiledDir)
        injectSmapiNativeSmali(decompiledDir)
        injectSmapiCore(decompiledDir)
        
        // 3. Build only the new DEX
        val builder = brut.androlib.ApkBuilder(config, brut.directory.ExtFile(decompiledDir))
        builder.build(classesOnlyApk)
        
        // 4. Perform Direct Injection (Preserves original alignment and libs)
        performInjection(originalApkFile, classesOnlyApk, decompiledDir, finalUnsigned)

        // 5. Sign
        signApk(finalUnsigned, signedApk)
        
        if (signedApk.exists()) {
            Log.d(TAG, "Surgery Complete. Final APK: ${signedApk.absolutePath}")
            installApk(signedApk)
        }
    }

    private fun performInjection(baseApk: File, classesApk: File, decompiledDir: File, outputApk: File) {
        Log.d(TAG, "Injecting patches into original APK structure...")
        
        // Prepare patched Manifest
        val manifestFile = File(decompiledDir, "AndroidManifest.xml")
        val oldPkg = "com.chucklefish.stardewvalley"
        val newPkg = "com.chucklefish.stardewvalle_" // Minimal 29-char patch
        val patchedManifestBytes = replaceBytes(manifestFile.readBytes(), oldPkg.toByteArray(Charsets.UTF_8), newPkg.toByteArray(Charsets.UTF_8))

        ZipOutputStream(outputApk.outputStream()).use { zos ->
            // 1. Copy everything from original APK EXCEPT what we want to replace
            ZipFile(baseApk).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    val name = entry.name
                    // Skip files we are replacing
                    if (name == "AndroidManifest.xml" || name.endsWith(".dex") || 
                        name.contains("icon") || name.contains("launcher") || name.contains("logo")) {
                        return@forEach 
                    }
                    
                    zos.putNextEntry(ZipEntry(name))
                    zip.getInputStream(entry).use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }

            // 2. Inject new classes.dex from the builder
            ZipFile(classesApk).use { zip ->
                zip.entries().asSequence().filter { it.name.endsWith(".dex") }.forEach { entry ->
                    zos.putNextEntry(ZipEntry(entry.name))
                    zip.getInputStream(entry).use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }

            // 3. Inject patched Manifest
            zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zos.write(patchedManifestBytes)
            zos.closeEntry()

            // 4. Inject Modded Icons into every icon slot found in the original
            ZipFile(baseApk).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    val name = entry.name
                    if (name.contains("icon") || name.contains("launcher") || name.contains("logo")) {
                        if (name.endsWith(".png") || name.endsWith(".webp")) {
                            zos.putNextEntry(ZipEntry(name))
                            context.assets.open("modded_icon.png").use { it.copyTo(zos) }
                            zos.closeEntry()
                        } else if (name.endsWith(".xml")) {
                            // Simplify XML icons
                            zos.putNextEntry(ZipEntry(name))
                            zos.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<adaptive-icon xmlns:android=\"http://schemas.android.com/apk/res/android\">\n<background android:drawable=\"@android:color/black\"/>\n<foreground android:drawable=\"@mipmap/ic_launcher\"/>\n</adaptive-icon>".toByteArray())
                            zos.closeEntry()
                        }
                    }
                }
            }
        }
    }

    private fun replaceBytes(source: ByteArray, old: ByteArray, new: ByteArray): ByteArray {
        if (old.size != new.size) return source
        val result = source.copyOf()
        var i = 0
        while (i <= result.size - old.size) {
            var match = true
            for (j in old.indices) { if (result[i + j] != old[j]) { match = false; break } }
            if (match) {
                for (j in new.indices) { result[i + j] = new[j] }
                i += old.size
            } else { i++ }
        }
        return result
    }

    private fun signApk(inputApk: File, outputApk: File) {
        val ksFile = File(context.cacheDir, "patcher.p12")
        if (!ksFile.exists()) {
            context.assets.open("potata_patcher.p12").use { input -> ksFile.outputStream().use { output -> input.copyTo(output) } }
        }
        val keystore = KeyStore.getInstance("PKCS12")
        ksFile.inputStream().use { input -> keystore.load(input, KEYSTORE_PASS.toCharArray()) }
        val privateKey = keystore.getKey(ALIAS, KEYSTORE_PASS.toCharArray()) as PrivateKey
        val cert = keystore.getCertificate(ALIAS) as X509Certificate
        val signerConfig = ApkSigner.SignerConfig.Builder(ALIAS, privateKey, listOf(cert)).build()
        
        ApkSigner.Builder(listOf(signerConfig))
            .setInputApk(inputApk)
            .setOutputApk(outputApk)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .setMinSdkVersion(24)
            .build()
            .sign()
    }

    private fun injectSmapiNativeSmali(decompiledDir: File) {
        val smapiDir = File(decompiledDir, "smali/io/potatasmapi/launcher")
        if (!smapiDir.exists()) smapiDir.mkdirs()
        val smaliCode = ".class public Lio/potatasmapi/launcher/SmapiNative;\n" +
                ".super Ljava/lang/Object;\n" +
                ".source \"SmapiNative.java\"\n\n" +
                ".method public static init()V\n" +
                "    .registers 2\n" +
                "    const-string v0, \"SmapiNative\"\n" +
                "    const-string v1, \"SMAPI Bootstrapping (Modded)...\"\n" +
                "    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I\n" +
                "    const-string v0, \"/sdcard/PotataSMAPI\"\n" +
                "    invoke-static {v0}, LStardewModdingAPI/EarlyConstants;->set_AndroidBaseDirPath(Ljava/lang/String;)V\n" +
                "    return-void\n" +
                ".end method"
        File(smapiDir, "SmapiNative.smali").writeText(smaliCode)
    }

    private fun injectSmaliHook(decompiledDir: File) {
        var entrySmali = File(decompiledDir, "smali/com/chucklefish/stardewvalley/StardewValley.smali")
        if (!entrySmali.exists()) entrySmali = File(decompiledDir, "smali_classes2/com/chucklefish/stardewvalley/StardewValley.smali")
        if (entrySmali.exists()) {
            val lines = entrySmali.readLines()
            val output = StringBuilder()
            var hooked = false
            for (line in lines) {
                output.append(line).append("\n")
                if (!hooked && line.contains("onCreate(Landroid/os/Bundle;)V")) {
                    output.append("    invoke-static {}, Lio/potatasmapi/launcher/SmapiNative;->init()V\n")
                    hooked = true
                }
            }
            entrySmali.writeText(output.toString())
        }
    }

    private fun injectSmapiCore(decompiledDir: File) {
        var assemblyDir = File(decompiledDir, "assets/bin/Data/Managed")
        if (!assemblyDir.exists()) assemblyDir.mkdirs()
        copyAssetToFile("StardewModdingAPI.dll", File(assemblyDir, "StardewModdingAPI.dll"))
    }

    private fun installApk(file: File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun copyUriToFile(uri: Uri, outFile: File) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        } ?: throw Exception("Failed to open input stream for URI")
    }

    private fun copyAssetToFile(assetName: String, outFile: File) {
        try {
            context.assets.open(assetName).use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (e: Exception) {
            outFile.createNewFile()
        }
    }
}
