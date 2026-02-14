package com.potatameister.smapi

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.util.Scanner
import brut.androlib.ApkDecoder
import brut.androlib.Androlib
import brut.androlib.options.BuildOptions

class PatcherService(private val context: Context) {
    private val TAG = "PotataPatcher"

    fun patchGame(originalApkPath: String) {
        Log.d(TAG, "Starting library-based digital surgery for: $originalApkPath")
        
        val workspace = File(context.externalCacheDir, "patch_workspace")
        if (workspace.exists()) workspace.deleteRecursively()
        if (!workspace.mkdirs()) throw Exception("Failed to create workspace directory")

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
            val decoder = ApkDecoder()
            decoder.setOutDir(decompiledDir)
            decoder.setApkFile(originalApkFile)
            decoder.decode()
        } catch (e: Exception) {
            throw Exception("Decompile failed: ${e.message}")
        }
        
        // 2. Hook & Inject
        try {
            injectSmaliHook(decompiledDir)
            injectSmapiNativeSmali(decompiledDir)
            injectSmapiCore(decompiledDir)
        } catch (e: Exception) {
            throw Exception("Injection failed: ${e.message}")
        }
        
        // 3. Rebuild using Apktool Lib
        try {
            val builder = Androlib(BuildOptions(), null)
            builder.build(decompiledDir, unsignedApk)
        } catch (e: Exception) {
            throw Exception("Rebuild failed: ${e.message}")
        }
        
        // Finalize
        if (unsignedApk.renameTo(signedApk)) {
            Log.d(TAG, "Surgery Complete. Triggering installation...")
            installApk(signedApk)
        } else {
            throw Exception("Failed to finalize signed APK")
        }
    }

    private fun injectSmapiNativeSmali(decompiledDir: File) {
        val smapiDir = File(decompiledDir, "smali/com/potatameister/smapi")
        if (!smapiDir.exists() && !smapiDir.mkdirs()) throw Exception("Failed to create smapi directory")
        
        val smaliCode = ".class public Lcom/potatameister/smapi/SmapiNative;\n" +
                ".super Ljava/lang/Object;\n" +
                ".source \"SmapiNative.java\"\n\n" +
                ".method public static init()V\n" +
                "    .registers 2\n" +
                "    const-string v0, \"SmapiNative\"\n" +
                "    const-string v1, \"SMAPI Bootstrapping from Lib...\"\n" +
                "    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I\n" +
                "    return-void\n" +
                ".end method"

        File(smapiDir, "SmapiNative.smali").writeText(smaliCode)
    }

    private fun injectSmaliHook(decompiledDir: File) {
        var entrySmali = File(decompiledDir, "smali/com/chucklefish/stardewvalley/StardewValley.smali")
        if (!entrySmali.exists()) {
            entrySmali = File(decompiledDir, "smali_classes2/com/chucklefish/stardewvalley/StardewValley.smali")
        }
        
        if (!entrySmali.exists()) throw Exception("Entry point smali not found")

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

    private fun injectSmapiCore(decompiledDir: File) {
        var assemblyDir = File(decompiledDir, "assets/bin/Data/Managed")
        if (!assemblyDir.exists()) {
            assemblyDir = File(decompiledDir, "assets/assemblies")
        }
        if (!assemblyDir.exists() && !assemblyDir.mkdirs()) throw Exception("Failed to create assembly directory")
            
        copyAssetToFile("StardewModdingAPI.dll", File(assemblyDir, "StardewModdingAPI.dll"))
    }

    private fun installApk(file: File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
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
        context.assets.open(assetName).use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        }
    }
}
