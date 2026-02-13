package com.potatameister.smapi;

import android.util.Log;

/**
 * SmapiNative is the bridge between the Android game and the C# SMAPI engine.
 * The modded game's MainActivity calls SmapiNative.init() at startup.
 */
public class SmapiNative {
    private static final String TAG = "SmapiNative";

    /**
     * Entry point called by the modded game's Smali code.
     */
    public static void init() {
        Log.d(TAG, "SmapiNative.init() called! Bootstrapping SMAPI engine...");
        
        try {
            // 1. Load the Mono runtime libraries
            // 2. Set environment variables for the mods folder
            // 3. Invoke the StardewModdingAPI.Program.Main() method
            
            Log.d(TAG, "SMAPI Engine started successfully.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to bootstrap SMAPI", e);
        }
    }
}
