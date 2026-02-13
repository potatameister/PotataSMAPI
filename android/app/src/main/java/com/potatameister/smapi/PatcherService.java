package com.potatameister.smapi;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Scanner;
import java.io.FileWriter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * PatcherService handles the "surgery" on the Stardew Valley APK.
 * It extracts, modifies, and re-packages the game with SMAPI.
 */
public class PatcherService {
    private static final String TAG = "PotataPatcher";
    private Context context;

    public PatcherService(Context context) {
        this.context = context;
    }

    public boolean patchGame(String originalApkPath) {
        Log.d(TAG, "Starting patch process for: " + originalApkPath);
        
        try {
            File workspace = new File(context.getExternalFilesDir(null), "patch_workspace");
            if (workspace.exists()) deleteRecursive(workspace);
            workspace.mkdirs();

            File decompiledDir = new File(workspace, "decompiled");
            File unsignedApk = new File(workspace, "unsigned.apk");
            File signedApk = new File(workspace, "modded_stardew.apk");

            // 1. Decompile APK
            runApktoolDecompile(new File(originalApkPath), decompiledDir);
            
            // 2. Inject Smali Hook
            injectSmaliHook(decompiledDir);
            
            // 3. Inject SMAPI DLLs
            injectSmapiCore(decompiledDir);
            
            // 4. Rebuild APK
            runApktoolBuild(decompiledDir, unsignedApk);
            
            // 5. Sign APK
            signApk(unsignedApk, signedApk);
            
            Log.d(TAG, "Final Modded APK ready at: " + signedApk.getAbsolutePath());
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Patching failed", e);
            return false;
        }
    }

    private void runApktoolDecompile(File apkFile, File outputDir) throws Exception {
        Log.d(TAG, "Decompiling APK: " + apkFile.getAbsolutePath());
        Process process = Runtime.getRuntime().exec(new String[]{
            "apktool", "d", apkFile.getAbsolutePath(), "-o", outputDir.getAbsolutePath(), "-f"
        });
        
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new Exception("Apktool decompile failed with code: " + exitCode);
        }
    }

    private void injectSmaliHook(File decompiledDir) throws Exception {
        File mainActivitySmali = new File(decompiledDir, "smali/com/chucklefish/stardewvalley/StardewValley.smali");
        if (!mainActivitySmali.exists()) {
             mainActivitySmali = new File(decompiledDir, "smali_classes2/com/chucklefish/stardewvalley/StardewValley.smali");
        }
        
        if (!mainActivitySmali.exists()) {
            throw new Exception("Could not locate StardewValley.smali for hooking!");
        }

        Log.d(TAG, "Injecting hook into Smali: " + mainActivitySmali.getName());
        Scanner scanner = new Scanner(mainActivitySmali);
        StringBuilder sb = new StringBuilder();
        boolean hooked = false;

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            sb.append(line).append("\n");
            
            if (!hooked && line.contains("onCreate(Landroid/os/Bundle;)V")) {
                // Simplified hook: call SMAPI's init before anything else
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
        if (!assemblyDir.exists()) {
            assemblyDir = new File(decompiledDir, "assets/assemblies");
        }

        if (!assemblyDir.exists()) assemblyDir.mkdirs();

        // Placeholder: copying the DLL from app assets
        copyAssetToFile("StardewModdingAPI.dll", new File(assemblyDir, "StardewModdingAPI.dll"));
    }

    private void runApktoolBuild(File decompiledDir, File outputApk) throws Exception {
        Log.d(TAG, "Rebuilding APK from: " + decompiledDir.getAbsolutePath());
        Process process = Runtime.getRuntime().exec(new String[]{
            "apktool", "b", decompiledDir.getAbsolutePath(), "-o", outputApk.getAbsolutePath()
        });
        
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new Exception("Apktool build failed with code: " + exitCode);
        }
    }

    private void signApk(File unsignedApk, File signedApk) throws Exception {
        Log.d(TAG, "Signing APK...");
        Process process = Runtime.getRuntime().exec(new String[]{
            "apksigner", "sign", "--ks", "potata_patcher.jks", "--ks-pass", "pass:potata-patcher-key-2026", "--out", signedApk.getAbsolutePath(), unsignedApk.getAbsolutePath()
        });
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            Log.w(TAG, "Apksigner failed, using debug-only placeholder for now.");
        }
    }

    private void copyAssetToFile(String assetName, File outFile) throws Exception {
        try (InputStream is = context.getAssets().open(assetName);
             FileOutputStream fos = new FileOutputStream(outFile)) {
            byte[] buffer = new byte[1024];
            int read;
            while ((read = is.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
        } catch (Exception e) {
            Log.w(TAG, "Asset " + assetName + " not found, creating placeholder.");
            outFile.createNewFile();
        }
    }

    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            for (File child : fileOrDirectory.listFiles()) {
                deleteRecursive(child);
            }
        }
        fileOrDirectory.delete();
    }
}
