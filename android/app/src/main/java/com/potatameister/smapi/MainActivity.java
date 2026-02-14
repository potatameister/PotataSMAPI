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
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;

public class MainActivity extends BridgeActivity {
    private static final String PREFS_NAME = "PotataPrefs";
    private static final String KEY_FOLDER_URI = "folder_uri";
    private static final int MANAGE_STORAGE_REQUEST = 1003;

    private ActivityResultLauncher<Intent> folderPickerLauncher;
    private ActivityResultLauncher<Intent> apkPickerLauncher;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(PotataBridge.class);
        super.onCreate(savedInstanceState);

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
    }

    public void requestManualPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Toast.makeText(this, "Redirecting to All Files Access Settings...", Toast.LENGTH_LONG).show();
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, MANAGE_STORAGE_REQUEST);
                } catch (Exception e) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivityForResult(intent, MANAGE_STORAGE_REQUEST);
                }
            } else {
                Toast.makeText(this, "All Files Access already granted!", Toast.LENGTH_SHORT).show();
            }
        } else {
            String[] permissions = {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
            ActivityCompat.requestPermissions(this, permissions, 200);
        }
    }

    public void openFolderPicker() {
        Uri rootUri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3A");
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, rootUri);
        
        // Force "Advanced" Mode for Xiaomi/Buggy Pickers
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
        intent.putExtra("android.content.extra.FANCY_FEATURES", true);
        intent.putExtra("android.content.extra.SHOW_FILESIZE", true);
        
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION 
                      | Intent.FLAG_GRANT_WRITE_URI_PERMISSION 
                      | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

        try {
            folderPickerLauncher.launch(intent);
        } catch (Exception e) {
            Intent fallback = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            folderPickerLauncher.launch(fallback);
        }
    }

    public void openApkPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/vnd.android.package-archive");
        apkPickerLauncher.launch(intent);
    }

    private void handleFolderResult(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
            editor.putString(KEY_FOLDER_URI, uri.toString());
            editor.apply();

            resolveBridgeCall("PotataBridge", uri.toString());
        } catch (Exception e) {
            Toast.makeText(this, "Folder selection failed", Toast.LENGTH_SHORT).show();
        }
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
