package com.potatameister.smapi;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.File;
import java.util.List;

@CapacitorPlugin(name = "PotataBridge")
public class PotataBridge extends Plugin {

    @PluginMethod
    public void initFolder(PluginCall call) {
        MainActivity activity = (MainActivity) getActivity();
        activity.checkAndInitFolder();
        
        JSObject ret = new JSObject();
        ret.put("path", activity.getDefaultPath());
        call.resolve(ret);
    }

    @PluginMethod
    public void getMods(PluginCall call) {
        String path = call.getString("uri");
        File modsFolder = new File(path, "Mods");
        
        JSArray modNames = new JSArray();
        File[] files = modsFolder.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) modNames.put(f.getName());
            }
        }

        JSObject ret = new JSObject();
        ret.put("mods", modNames);
        call.resolve(ret);
    }

    @PluginMethod
    public void pickApk(PluginCall call) {
        saveCall(call);
        MainActivity activity = (MainActivity) getActivity();
        activity.openApkPicker();
    }

    @PluginMethod
    public void autoLocateGame(PluginCall call) {
        MainActivity activity = (MainActivity) getActivity();
        String path = activity.locateStardewApk();
        JSObject ret = new JSObject();
        ret.put("path", path);
        call.resolve(ret);
    }

    @PluginMethod
    public void startPatching(PluginCall call) {
        String apkPath = call.getString("path");
        PatcherService patcher = new PatcherService(getContext());
        try {
            patcher.patchGame(apkPath);
            JSObject ret = new JSObject();
            ret.put("success", true);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }
}
