package com.potatameister.smapi;

import android.net.Uri;
import androidx.documentfile.provider.DocumentFile;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.util.ArrayList;
import java.util.List;

@CapacitorPlugin(name = "PotataBridge")
public class PotataBridge extends Plugin {

    @PluginMethod
    public void getMods(PluginCall call) {
        String folderUri = call.getString("uri");
        if (folderUri == null) {
            call.reject("No folder URI provided");
            return;
        }

        Uri uri = Uri.parse(folderUri);
        DocumentFile root = DocumentFile.fromTreeUri(getContext(), uri);
        DocumentFile modsFolder = root.findFile("Mods");

        JSArray modNames = new JSArray();
        if (modsFolder != null && modsFolder.isDirectory()) {
            for (DocumentFile file : modsFolder.listFiles()) {
                if (file.isDirectory()) {
                    modNames.put(file.getName());
                }
            }
        } else if (modsFolder == null) {
            // Create the Mods folder if it doesn't exist
            root.createDirectory("Mods");
        }

        JSObject ret = new JSObject();
        ret.put("mods", modNames);
        call.resolve(ret);
    }

    @PluginMethod
    public void startPatching(PluginCall call) {
        String apkPath = call.getString("path");
        
        PatcherService patcher = new PatcherService(getContext());
        boolean success = patcher.patchGame(apkPath);

        JSObject ret = new JSObject();
        ret.put("success", success);
        
        if (success) {
            call.resolve(ret);
        } else {
            call.reject("Patching failed. Check logs.");
        }
    }

    @PluginMethod
    public void pickFolder(PluginCall call) {
        saveCall(call);
        MainActivity activity = (MainActivity) getActivity();
        activity.openFolderPicker();
    }

    @PluginMethod
    public void getSavedFolder(PluginCall call) {
        MainActivity activity = (MainActivity) getActivity();
        String path = activity.getSavedFolderUri();
        JSObject ret = new JSObject();
        ret.put("path", path);
        call.resolve(ret);
    }

    @PluginMethod
    public void pickApk(PluginCall call) {
        saveCall(call);
        MainActivity activity = (MainActivity) getActivity();
        activity.openApkPicker();
    }

    @PluginMethod
    public void requestManualPermissions(PluginCall call) {
        MainActivity activity = (MainActivity) getActivity();
        activity.requestManualPermissions();
        call.resolve();
    }
}
