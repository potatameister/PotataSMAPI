package com.potatameister.smapi

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
    private val TAG = "PotataPatcher"
    private val KEYSTORE_PASS = "potata-patcher-key-2026"
    private val ALIAS = "potata_patcher"

    fun patchGame(originalApkPath: String) {
        Log.d(TAG, "Starting stable digital surgery for: $originalApkPath")

        val workspace = File(context.externalCacheDir, "patch_workspace")
        if (workspace.exists()) workspace.deleteRecursively()
        if (!workspace.mkdirs()) throw Exception("Failed to create workspace directory")

        // Fix: Use reflection to bypass private constructor and avoid buggy getDefaultConfig() 
        // which triggers a crash on some Android devices due to OSDetection.
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

        // 1. Decompile using Apktool Lib
        try {
            val decoder = ApkDecoder(config, brut.directory.ExtFile(originalApkFile))
            decoder.decode(decompiledDir)
        } catch (e: Exception) {
            throw Exception("Decompile failed: ${e.message}")
        }
        
        // 2. Hook & Inject
        try {
            renamePackage(decompiledDir)
            injectSmaliHook(decompiledDir)
            injectSmapiNativeSmali(decompiledDir)
            injectSmapiCore(decompiledDir)
        } catch (e: Exception) {
            throw Exception("Injection failed: ${e.message}")
        }
        
        // 3. Rebuild (Using ApkBuilder)
        try {
            Log.d(TAG, "Rebuilding APK into: ${unsignedApk.absolutePath}")
            val builder = brut.androlib.ApkBuilder(config, brut.directory.ExtFile(decompiledDir))
            builder.build(unsignedApk)
            Log.d(TAG, "ApkBuilder finished. Unsigned APK exists: ${unsignedApk.exists()}")
        } catch (e: Exception) {
            Log.e(TAG, "Rebuild CRASHED: ${Log.getStackTraceString(e)}")
            throw Exception("Rebuild failed: ${e.message}")
        }
        
        // 4. Sign
        try {
            Log.d(TAG, "Signing APK...")
            signApk(unsignedApk, signedApk)
            Log.d(TAG, "Signing successful!")
        } catch (e: Exception) {
            Log.e(TAG, "Signing FAILED: ${Log.getStackTraceString(e)}")
            throw Exception("Signing failed: ${e.message}")
        }
        
        // Finalize
        if (signedApk.exists()) {
            Log.d(TAG, "Surgery Complete. Final APK: ${signedApk.absolutePath} (${signedApk.length()} bytes)")
            installApk(signedApk)
        } else {
            throw Exception("Failed to generate final signed APK")
        }
    }

    private fun signApk(inputApk: File, outputApk: File) {
        val ksFile = File(context.cacheDir, "patcher.p12")
        if (!ksFile.exists()) {
            context.assets.open("potata_patcher.p12").use { input ->
                ksFile.outputStream().use { output -> input.copyTo(output) }
            }
        }

        // Android prefers PKCS12 over JKS
        val keystore = KeyStore.getInstance("PKCS12")
        ksFile.inputStream().use { input ->
            keystore.load(input, KEYSTORE_PASS.toCharArray())
        }
        
        val privateKey = keystore.getKey(ALIAS, KEYSTORE_PASS.toCharArray()) as PrivateKey
        val cert = keystore.getCertificate(ALIAS) as X509Certificate
        
        val signerConfig = ApkSigner.SignerConfig.Builder(ALIAS, privateKey, listOf(cert)).build()
        
        val apkSigner = ApkSigner.Builder(listOf(signerConfig))
            .setInputApk(inputApk)
            .setOutputApk(outputApk)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setMinSdkVersion(24) // Match our project's minSdk to bypass manifest parsing bug
            .build()
            
        apkSigner.sign()
    }

    private fun renamePackage(decompiledDir: File) {
        val manifestFile = File(decompiledDir, "AndroidManifest.xml")
        if (!manifestFile.exists()) return

        val originalPackage = "com.chucklefish.stardewvalley"
        val newPackage = "com.potatameister.smapi.stardew"
        
        var content = manifestFile.readText()
        
        // 1. Rename the main package attribute
        content = content.replace("package=\"$originalPackage\"", "package=\"$newPackage\"")
        
        // 2. Rename authorities to avoid conflict
        // Stardew uses several providers, we'll append .smapi to them
        content = content.replace("android:authorities=\"$originalPackage", "android:authorities=\"$newPackage")
        
        manifestFile.writeText(content)
        Log.d(TAG, "Package renamed to $newPackage")
    }

    private fun injectSmapiNativeSmali(decompiledDir: File) {
        val smapiDir = File(decompiledDir, "smali/com/potatameister/smapi")
        if (!smapiDir.exists()) smapiDir.mkdirs()
        
        val smaliCode = ".class public Lcom/potatameister/smapi/SmapiNative;\n" +
                ".super Ljava/lang/Object;\n" +
                ".source \"SmapiNative.java\"\n\n" +
                ".method public static init()V\n" +
                "    .registers 2\n" +
                "    const-string v0, \"SmapiNative\"\n" +
                "    const-string v1, \"SMAPI Bootstrapping (Modded)...\"\n" +
                "    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I\n" +
                "    \n" +
                "    # Set EarlyConstants.AndroidBaseDirPath = /sdcard/PotataSMAPI\n" +
                "    const-string v0, \"/sdcard/PotataSMAPI\"\n" +
                "    invoke-static {v0}, LStardewModdingAPI/EarlyConstants;->set_AndroidBaseDirPath(Ljava/lang/String;)V\n" +
                "    return-void\n" +
                ".end method"

        File(smapiDir, "SmapiNative.smali").writeText(smaliCode)
    }

    private fun injectSmaliHook(decompiledDir: File) {
        var entrySmali = File(decompiledDir, "smali/com/chucklefish/stardewvalley/StardewValley.smali")
        if (!entrySmali.exists()) {
            entrySmali = File(decompiledDir, "smali_classes2/com/chucklefish/stardewvalley/StardewValley.smali")
        }
        
        if (entrySmali.exists()) {
            val lines = entrySmali.readLines()
            val output = StringBuilder()
            var hooked = false
            for (line in lines) {
                output.append(line).append("\n")
                if (!hooked && line.contains("onCreate(Landroid/os/Bundle;)V")) {
                    output.append("    invoke-static {}, Lcom/potatameister/smapi/SmapiNative;->init()V\n")
                    hooked = true
                }
            }
            entrySmali.writeText(output.toString())
        }
    }

    private fun injectSmapiCore(decompiledDir: File) {
        var assemblyDir = File(decompiledDir, "assets/bin/Data/Managed")
        if (!assemblyDir.exists()) {
            assemblyDir = File(decompiledDir, "assets/assemblies")
        }
        if (!assemblyDir.exists()) assemblyDir.mkdirs()
            
        copyAssetToFile("StardewModdingAPI.dll", File(assemblyDir, "StardewModdingAPI.dll"))
    }

    private fun installApk(file: File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
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
