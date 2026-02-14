package com.potatameister.smapi;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.app.ActivityCompat;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;

public class MainActivity extends BridgeActivity {
    private static final String PREFS_NAME = "PotataPrefs";
    private static final String KEY_FOLDER_URI = "folder_uri";

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

    public void openFolderPicker() {
        // This is the "Magic URI" for the primary storage root
        Uri rootUri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3A");
        
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, rootUri);
        
        // We only use the absolute essential flags
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION 
                      | Intent.FLAG_GRANT_WRITE_URI_PERMISSION 
                      | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

        try {
            folderPickerLauncher.launch(intent);
        } catch (Exception e) {
            // Fallback if the magic URI fails
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

    public void requestManualPermissions() {
        String[] permissions = {
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        };
        ActivityCompat.requestPermissions(this, permissions, 200);
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
