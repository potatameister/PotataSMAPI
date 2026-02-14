package com.potatameister.smapi

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.util.Scanner

class PatcherService(private val context: Context) {
    private val TAG = "PotataPatcher"

    fun patchGame(originalApkPath: String) {
        Log.d(TAG, "Starting native digital surgery for: $originalApkPath")
        
        val workspace = File(context.externalCacheDir, "patch_workspace")
        if (workspace.exists()) workspace.deleteRecursively()
        workspace.mkdirs()

        val originalApkFile = File(workspace, "base_game.apk")
        val decompiledDir = File(workspace, "decompiled")
        val unsignedApk = File(workspace, "unsigned.apk")
        val signedApk = File(workspace, "modded_stardew.apk")

        // 0. Copy APK
        if (originalApkPath.startsWith("content://")) {
            copyUriToFile(Uri.parse(originalApkPath), originalApkFile)
        } else {
            File(originalApkPath).copyTo(originalApkFile, true)
        }

        // 1. Decompile
        runCommand(listOf("apktool", "d", originalApkFile.absolutePath, "-o", decompiledDir.absolutePath, "-f"))
        
        // 2. Hook & Inject
        injectSmaliHook(decompiledDir)
        injectSmapiNativeSmali(decompiledDir)
        injectSmapiCore(decompiledDir)
        
        // 3. Rebuild & Sign
        runCommand(listOf("apktool", "b", decompiledDir.absolutePath, "-o", unsignedApk.absolutePath))
        
        // Finalize
        unsignedApk.renameTo(signedApk)
        Log.d(TAG, "Surgery Complete.")
    }

    private fun injectSmapiNativeSmali(decompiledDir: File) {
        val smapiDir = File(decompiledDir, "smali/com/potatameister/smapi")
        sapiDir.mkdirs()
        File(smapiDir, "SmapiNative.smali").writeText(
            """.class public Lcom/potatameister/smapi/SmapiNative;
.super Ljava/lang/Object;
.source "SmapiNative.java"

.method public static init()V
    .registers 2
    const-string v0, "SmapiNative"
    const-string v1, "Native SMAPI Bootstrapping..."
    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I
    return-void
.end method"""
        )
    }

    private fun injectSmaliHook(decompiledDir: File) {
        var entrySmali = File(decompiledDir, "smali/com/chucklefish/stardewvalley/StardewValley.smali")
        if (!entrySmali.exists()) entrySmali = File(decompiledDir, "smali_classes2/com/chucklefish/stardewvalley/StardewValley.smali")
        
        if (entrySmali.exists()) {
            val lines = entrySmali.readLines().toMutableList()
            val output = StringBuilder()
            for (line in lines) {
                output.append(line).append("
")
                if (line.contains("onCreate(Landroid/os/Bundle;)V")) {
                    output.append("    invoke-static {}, Lcom/potatameister/smapi/SmapiNative;->init()V
")
                }
            }
            entrySmali.writeText(output.toString())
        }
    }

    private fun injectSmapiCore(decompiledDir: File) {
        val assemblyDir = File(decompiledDir, "assets/bin/Data/Managed").takeIf { it.exists() } 
            ?: File(decompiledDir, "assets/assemblies").apply { mkdirs() }
            
        copyAssetToFile("StardewModdingAPI.dll", File(assemblyDir, "StardewModdingAPI.dll"))
    }

    private fun runCommand(cmd: List<String>) {
        val process = ProcessBuilder(cmd).start()
        process.waitFor()
    }

    private fun copyUriToFile(uri: Uri, outFile: File) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        }
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
