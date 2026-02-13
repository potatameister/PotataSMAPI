package com.potatameister.smapi;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "PotataBridge")
public class PotataBridge extends Plugin {

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
}
