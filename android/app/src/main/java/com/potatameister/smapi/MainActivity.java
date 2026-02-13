package com.potatameister.smapi;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;

public class MainActivity extends BridgeActivity {
    private static final int PICK_FOLDER_REQUEST = 1001;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(PotataBridge.class);
        super.onCreate(savedInstanceState);
    }

    public void openFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(intent, PICK_FOLDER_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_FOLDER_REQUEST && resultCode == RESULT_OK) {
            if (data != null) {
                Uri uri = data.getData();
                // Persist permissions so we can access it later
                getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                PluginCall call = getBridge().getSavedCall("PotataBridge");
                if (call != null) {
                    JSObject ret = new JSObject();
                    ret.put("path", uri.toString());
                    call.resolve(ret);
                }
            }
        }
    }
}
