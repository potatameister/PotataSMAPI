package io.potatasmapi.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.Scanner
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
        Log.d(TAG, "Starting stable digital surgery for: $originalApkPath")

        val workspace = File(context.externalCacheDir, "patch_workspace")
        if (workspace.exists()) workspace.deleteRecursively()
        if (!workspace.mkdirs()) throw Exception("Failed to create workspace directory")

        val configConstructor = brut.androlib.Config::class.java.getDeclaredConstructor()
        configConstructor.isAccessible = true
        val config = configConstructor.newInstance() as brut.androlib.Config
        
        val frameworkDir = File(context.cacheDir, "apktool_framework")
        if (!frameworkDir.exists()) frameworkDir.mkdirs()
        config.frameworkDirectory = frameworkDir.absolutePath
        config.decodeResources = brut.androlib.Config.DECODE_RESOURCES_NONE

        val originalApkFile = File(workspace, "base_game.apk")
        val decompiledDir = File(workspace, "decompiled")
        val unsignedApk = File(workspace, "unsigned.apk")
        val signedApk = File(workspace, "modded_stardew.apk")

        // 0. Copy APK
        try {
            if (originalApkPath.startsWith("content://")) {
                copyUriToFile(Uri.parse(originalApkPath), originalApkFile)
            } else {
                File(originalApkPath).copyTo(originalApkFile, true)
            }
        } catch (e: Exception) {
            throw Exception("Copy failed: ${e.message}")
        }

        // 1. Decompile
        try {
            val decoder = ApkDecoder(config, brut.directory.ExtFile(originalApkFile))
            decoder.decode(decompiledDir)
        } catch (e: Exception) {
            throw Exception("Decompile failed: ${e.message}")
        }
        
        // 2. Surgery
        try {
            patchBinaryManifest(decompiledDir)
            patchGameIcons(decompiledDir)
            injectSmaliHook(decompiledDir)
            injectSmapiNativeSmali(decompiledDir)
            injectSmapiCore(decompiledDir)
        } catch (e: Exception) {
            throw Exception("Injection failed: ${e.message}")
        }
        
        // 3. Rebuild
        try {
            Log.d(TAG, "Rebuilding APK...")
            val builder = brut.androlib.ApkBuilder(config, brut.directory.ExtFile(decompiledDir))
            builder.build(unsignedApk)
        } catch (e: Exception) {
            Log.e(TAG, "Rebuild CRASHED: ${Log.getStackTraceString(e)}")
            throw Exception("Rebuild failed: ${e.message}")
        }
        
        // 4. Sign (Upgraded to V3 for Android 16 compatibility)
        try {
            Log.d(TAG, "Signing APK (V1, V2, V3)...")
            signApk(unsignedApk, signedApk)
        } catch (e: Exception) {
            Log.e(TAG, "Signing FAILED: ${Log.getStackTraceString(e)}")
            throw Exception("Signing failed: ${e.message}")
        }
        
        // Finalize
        if (signedApk.exists()) {
            Log.d(TAG, "Surgery Complete. Final APK: ${signedApk.absolutePath}")
            installApk(signedApk)
        } else {
            throw Exception("Failed to generate final signed APK")
        }
    }

    private fun patchBinaryManifest(decompiledDir: File) {
        val manifestFile = File(decompiledDir, "AndroidManifest.xml")
        if (!manifestFile.exists()) return
        val oldPkg = "com.chucklefish.stardewvalley"
        val newPkg = "io.potatasmapi.launcher.patch"
        val bytes = manifestFile.readBytes()
        var patchedBytes = replaceBytes(bytes, oldPkg.toByteArray(Charsets.UTF_8), newPkg.toByteArray(Charsets.UTF_8))
        patchedBytes = replaceBytes(patchedBytes, oldPkg.toByteArray(Charsets.UTF_16LE), newPkg.toByteArray(Charsets.UTF_16LE))
        manifestFile.writeBytes(patchedBytes)
    }

    private fun patchGameIcons(decompiledDir: File) {
        Log.d(TAG, "Replacing game icons with modded version...")
        val resDir = File(decompiledDir, "res")
        if (!resDir.exists()) return

        // Targeted names for Stardew launcher icons
        val targetNames = listOf("ic_launcher", "icon", "app_icon", "ic_launcher_round", "ic_launcher_foreground")
        
        resDir.walkTopDown().forEach { file ->
            val nameWithoutExt = file.nameWithoutExtension
            if (targetNames.contains(nameWithoutExt)) {
                try {
                    when (file.extension.lowercase()) {
                        "png", "webp" -> {
                            context.assets.open("modded_icon.png").use { input ->
                                file.outputStream().use { output -> input.copyTo(output) }
                            }
                        }
                        "xml" -> {
                            // If it's an adaptive icon XML, we force it to a simple bitmap icon
                            // to ensure our new PNG is used.
                            val simpleXml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                                    "<adaptive-icon xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                                    "    <background android:drawable=\"@android:color/black\"/>\n" +
                                    "    <foreground android:drawable=\"@mipmap/ic_launcher\"/>\n" +
                                    "</adaptive-icon>"
                            // Only overwrite if it looks like a launcher XML
                            if (file.readText().contains("adaptive-icon")) {
                                file.writeText(simpleXml)
                            }
                        }
                    }
                    Log.d(TAG, "Patched resource: ${file.absolutePath}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to patch ${file.name}", e)
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
            for (j in old.indices) {
                if (result[i + j] != old[j]) { match = false; break }
            }
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
            context.assets.open("potata_patcher.p12").use { input ->
                ksFile.outputStream().use { output -> input.copyTo(output) }
            }
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
            .setV3SigningEnabled(true) // Stronger signature for Android 16
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
        if (!assemblyDir.exists()) assemblyDir = File(decompiledDir, "assets/assemblies")
        if (!assemblyDir.exists()) assemblyDir.mkdirs()
        copyAssetToFile("StardewModdingAPI.dll", File(assemblyDir, "StardewModdingAPI.dll"))
    }

    private fun installApk(file: File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        Log.d(TAG, "Triggering install for URI: $uri")
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
