package com.potatameister.smapi;

import android.net.Uri;
import androidx.documentfile.provider.DocumentFile;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

@CapacitorPlugin(name = "PotataBridge")
public class PotataBridge extends Plugin {

    @PluginMethod
    public void getMods(PluginCall call) {
        String input = call.getString("uri");
        if (input == null) {
            call.reject("No path/URI provided");
            return;
        }

        List<String> modNames = new java.util.ArrayList<>();

        if (input.startsWith("content://")) {
            Uri uri = Uri.parse(input);
            DocumentFile root = DocumentFile.fromTreeUri(getContext(), uri);
            DocumentFile modsFolder = root.findFile("Mods");
            if (modsFolder != null && modsFolder.isDirectory()) {
                for (DocumentFile file : modsFolder.listFiles()) {
                    if (file.isDirectory()) modNames.add(file.getName());
                }
            }
        } else {
            File modsFolder = new File(input, "Mods");
            File[] files = modsFolder.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isDirectory()) modNames.add(f.getName());
                }
            }
        }

        JSObject ret = new JSObject();
        JSArray jsArray = new JSArray();
        for (String name : modNames) jsArray.put(name);
        ret.put("mods", jsArray);
        call.resolve(ret);
    }

    @PluginMethod
    public void startPatching(PluginCall call) {
        String apkPath = call.getString("path");
        PatcherService patcher = new PatcherService(getContext());
        boolean success = patcher.patchGame(apkPath);
        JSObject ret = new JSObject();
        ret.put("success", success);
        if (success) call.resolve(ret);
        else call.reject("Patching failed.");
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
}
