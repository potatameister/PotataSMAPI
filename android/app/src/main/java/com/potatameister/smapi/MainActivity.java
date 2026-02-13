package com.potatameister.smapi;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.core.app.ActivityCompat;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;

public class MainActivity extends BridgeActivity {
    private static final String PREFS_NAME = "PotataPrefs";
    private static final String KEY_FOLDER_URI = "folder_uri";

    private ActivityResultLauncher<Intent> folderPickerLauncher;
    private ActivityResultLauncher<Intent> apkPickerLauncher;
    private ActivityResultLauncher<Intent> manageStorageLauncher;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(PotataBridge.class);
        super.onCreate(savedInstanceState);

        // Initialize launchers
        folderPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    handleFolderResult(result.getData().getData());
                }
            }
        );

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
            result -> Toast.makeText(this, "Returned from settings", Toast.LENGTH_SHORT).show()
        );
    }

    public void requestAllPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Toast.makeText(this, "Please enable 'All Files Access' for Potata", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                manageStorageLauncher.launch(intent);
            } else {
                Toast.makeText(this, "Permissions already granted!", Toast.LENGTH_SHORT).show();
            }
        } else {
            ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, 200);
        }
    }

    public void openFolderPicker() {
        Toast.makeText(this, "Opening Folder Picker...", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
        folderPickerLauncher.launch(intent);
    }

    public void openApkPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/vnd.android.package-archive");
        apkPickerLauncher.launch(intent);
    }

    private void handleFolderResult(Uri uri) {
        getContentResolver().takePersistableUriPermission(uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.putString(KEY_FOLDER_URI, uri.toString());
        editor.apply();

        resolveBridgeCall("PotataBridge", uri.toString());
    }

    public String getSavedFolderUri() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_FOLDER_URI, null);
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
