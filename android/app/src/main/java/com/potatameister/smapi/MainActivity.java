package com.potatameister.smapi;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;

import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends BridgeActivity {
    private static final int PICK_FOLDER_REQUEST = 1001;
    private static final int PICK_APK_REQUEST = 1002;
    private static final int PERMISSION_REQUEST_CODE = 200;
    private static final String PREFS_NAME = "PotataPrefs";
    private static final String KEY_FOLDER_URI = "folder_uri";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(PotataBridge.class);
        super.onCreate(savedInstanceState);
        requestAllPermissions();
    }

    private void requestAllPermissions() {
        String[] permissions = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        };
        
        ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
    }

    public void openFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, PICK_FOLDER_REQUEST);
    }

    public void openApkPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/vnd.android.package-archive");
        startActivityForResult(intent, PICK_APK_REQUEST);
    }

    public String getSavedFolderUri() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_FOLDER_URI, null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (requestCode == PICK_FOLDER_REQUEST) {
                getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
                editor.putString(KEY_FOLDER_URI, uri.toString());
                editor.apply();

                resolveBridgeCall("PotataBridge", uri.toString());
            } else if (requestCode == PICK_APK_REQUEST) {
                resolveBridgeCall("PotataBridge", uri.toString());
            }
        }
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
