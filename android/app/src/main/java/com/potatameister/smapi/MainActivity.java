package com.potatameister.smapi;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;
import java.io.File;

public class MainActivity extends BridgeActivity {
    private static final String DEFAULT_FOLDER_NAME = "PotataSMAPI";
    private ActivityResultLauncher<Intent> apkPickerLauncher;
    private ActivityResultLauncher<Intent> manageStorageLauncher;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(PotataBridge.class);
        super.onCreate(savedInstanceState);

        apkPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    resolveBridgeCall("PotataBridge", result.getData().getData().toString());
                }
            }
        );

        manageStorageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> checkAndInitFolder()
        );

        // Auto-init on startup
        checkAndInitFolder();
    }

    public void checkAndInitFolder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                try {
                    manageStorageLauncher.launch(intent);
                } catch (Exception e) {
                    Intent fallback = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    manageStorageLauncher.launch(fallback);
                }
                return;
            }
        }

        // Create the PotataSMAPI folder automatically
        File root = new File(Environment.getExternalStorageDirectory(), DEFAULT_FOLDER_NAME);
        File mods = new File(root, "Mods");
        
        if (!root.exists()) root.mkdirs();
        if (!mods.exists()) mods.mkdirs();

        Log.d("Potata", "Folder Ready: " + root.getAbsolutePath());
    }

    public void openApkPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/vnd.android.package-archive");
        apkPickerLauncher.launch(intent);
    }

    public String locateStardewApk() {
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo("com.chucklefish.stardewvalley", 0);
            if (ai != null && ai.publicSourceDir != null) {
                Log.d("Potata", "Game found at: " + ai.publicSourceDir);
                return ai.publicSourceDir;
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e("Potata", "Stardew Valley not found. Make sure the official app is installed.");
        } catch (Exception e) {
            Log.e("Potata", "Error locating game: " + e.getMessage());
        }
        return null;
    }

    public String getDefaultPath() {
        return new File(Environment.getExternalStorageDirectory(), DEFAULT_FOLDER_NAME).getAbsolutePath();
    }

    private void resolveBridgeCall(String pluginName, String resultPath) {
        PluginCall call = getBridge().getSavedCall(pluginName);
        if (call != null) {
            JSObject ret = new JSObject();
            ret.put("path", resultPath);
            call.resolve(ret);
        }
    }
}
