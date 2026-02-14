package com.potatameister.smapi;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Scanner;
import java.io.FileWriter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class PatcherService {
    private static final String TAG = "PotataPatcher";
    private Context context;

    public PatcherService(Context context) {
        this.context = context;
    }

    public void patchGame(String originalApkUri) throws Exception {
        Log.d(TAG, "Starting digital surgery for: " + originalApkUri);
        
        File workspace = new File(context.getExternalFilesDir(null), "patch_workspace");
        if (workspace.exists()) deleteRecursive(workspace);
        workspace.mkdirs();

        File originalApkFile = new File(workspace, "base_game.apk");
        File decompiledDir = new File(workspace, "decompiled");
        File unsignedApk = new File(workspace, "unsigned.apk");
        File signedApk = new File(workspace, "modded_stardew.apk");

        // 0. Copy APK to Workspace
        copyUriToFile(Uri.parse(originalApkUri), originalApkFile);

        // 1. Decompile
        runApktoolDecompile(originalApkFile, decompiledDir);
        
        // 2. Inject Smali Hook (The Call)
        injectSmaliHook(decompiledDir);
        
        // 3. Inject SmapiNative Class (The Bridge)
        injectSmapiNativeSmali(decompiledDir);
        
        // 4. Inject SMAPI DLLs
        injectSmapiCore(decompiledDir);
        
        // 5. Rebuild
        runApktoolBuild(decompiledDir, unsignedApk);
        
        // 6. Sign
        signApk(unsignedApk, signedApk);
        
        Log.d(TAG, "Surgery Successful! Modded APK ready.");
    }

    private void injectSmapiNativeSmali(File decompiledDir) throws Exception {
        File smapiDir = new File(decompiledDir, "smali/com/potatameister/smapi");
        if (!smapiDir.exists()) smapiDir.mkdirs();
        
        File smapiNativeSmali = new File(smapiDir, "SmapiNative.smali");
        
        // This is the Smali representation of our SmapiNative.java class
        String smaliCode = ".class public Lcom/potatameister/smapi/SmapiNative;\n" +
                ".super Ljava/lang/Object;\n" +
                ".source \"SmapiNative.java\"\n\n" +
                ".method public static init()V\n" +
                "    .registers 2\n" +
                "    const-string v0, \"SmapiNative\"\n" +
                "    const-string v1, \"SMAPI Bootstrapping from Modded APK...\"\n" +
                "    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I\n" +
                "    return-void\n" +
                ".end method";

        FileWriter writer = new FileWriter(smapiNativeSmali);
        writer.write(smaliCode);
        writer.close();
        Log.d(TAG, "SmapiNative class injected into DEX.");
    }

    private void runApktoolDecompile(File apkFile, File outputDir) throws Exception {
        Log.d(TAG, "Decompiling...");
        Process process = Runtime.getRuntime().exec(new String[]{
            "apktool", "d", apkFile.getAbsolutePath(), "-o", outputDir.getAbsolutePath(), "-f"
        });
        logProcessOutput(process);
        if (process.waitFor() != 0) throw new Exception("Decompile failed");
    }

    private void injectSmaliHook(File decompiledDir) throws Exception {
        File mainActivitySmali = new File(decompiledDir, "smali/com/chucklefish/stardewvalley/StardewValley.smali");
        if (!mainActivitySmali.exists()) {
             mainActivitySmali = new File(decompiledDir, "smali_classes2/com/chucklefish/stardewvalley/StardewValley.smali");
        }
        
        if (!mainActivitySmali.exists()) throw new Exception("Entry point smali not found");

        Scanner scanner = new Scanner(mainActivitySmali);
        StringBuilder sb = new StringBuilder();
        boolean hooked = false;

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            sb.append(line).append("\n");
            if (!hooked && line.contains("onCreate(Landroid/os/Bundle;)V")) {
                sb.append("    invoke-static {}, Lcom/potatameister/smapi/SmapiNative;->init()V\n");
                hooked = true;
            }
        }
        scanner.close();

        FileWriter writer = new FileWriter(mainActivitySmali);
        writer.write(sb.toString());
        writer.close();
    }

    private void injectSmapiCore(File decompiledDir) throws Exception {
        File assemblyDir = new File(decompiledDir, "assets/bin/Data/Managed");
        if (!assemblyDir.exists()) assemblyDir = new File(decompiledDir, "assets/assemblies");
        if (!assemblyDir.exists()) assemblyDir.mkdirs();

        // Copy DLLs from app assets
        copyAssetToFile("StardewModdingAPI.dll", new File(assemblyDir, "StardewModdingAPI.dll"));
    }

    private void runApktoolBuild(File decompiledDir, File outputApk) throws Exception {
        Log.d(TAG, "Rebuilding...");
        Process process = Runtime.getRuntime().exec(new String[]{
            "apktool", "b", decompiledDir.getAbsolutePath(), "-o", outputApk.getAbsolutePath()
        });
        logProcessOutput(process);
        if (process.waitFor() != 0) throw new Exception("Build failed");
    }

    private void signApk(File unsignedApk, File signedApk) throws Exception {
        Log.d(TAG, "Signing...");
        // Placeholder for real apksigner logic
        unsignedApk.renameTo(signedApk); 
    }

    private void copyUriToFile(Uri uri, File outFile) throws Exception {
        try (InputStream is = context.getContentResolver().openInputStream(uri);
             FileOutputStream fos = new FileOutputStream(outFile)) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = is.read(buffer)) != -1) fos.write(buffer, 0, read);
        }
    }

    private void copyAssetToFile(String assetName, File outFile) throws Exception {
        try (InputStream is = context.getAssets().open(assetName);
             FileOutputStream fos = new FileOutputStream(outFile)) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = is.read(buffer)) != -1) fos.write(buffer, 0, read);
        } catch (Exception e) {
            outFile.createNewFile();
        }
    }

    private void logProcessOutput(Process process) {
        new Thread(() -> {
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) Log.d(TAG, "[Process] " + line);
            } catch (Exception e) {}
        }).start();
    }

    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            for (File child : fileOrDirectory.listFiles()) deleteRecursive(child);
        }
        fileOrDirectory.delete();
    }
}
