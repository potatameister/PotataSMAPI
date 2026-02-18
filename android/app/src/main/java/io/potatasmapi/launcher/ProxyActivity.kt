package io.potatasmapi.launcher

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.AssetManager
import android.os.Bundle
import android.util.Log
import java.io.File

/**
 * ProxyActivity: The "Shell" that actually runs the Stardew Valley code.
 * It intercepts all system calls and redirects them to the Virtual Cartridge.
 */
class ProxyActivity : Activity() {
    private val TAG = "PotataProxy"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            val targetActivityName = intent.getStringExtra("TARGET_ACTIVITY") ?: return
            val dexPath = intent.getStringExtra("DEX_PATH") ?: return
            val libPath = intent.getStringExtra("LIB_PATH") ?: return

            PotataApp.addLog("Proxy: Initializing Engine...")
            
            // 1. Setup Virtual ClassLoader for this instance
            val classLoader = dalvik.system.DexClassLoader(
                dexPath, 
                File(codeCacheDir, "opt_dex").absolutePath, 
                libPath, 
                this.javaClass.classLoader
            )

            // 2. Hijack the Context for the coming Activity
            // This is critical for Android 15/16 redirection
            val targetClass = classLoader.loadClass(targetActivityName)
            
            PotataApp.addLog("Proxy: Target Class Loaded.")

            // 3. Launch the actual game activity from the virtual loader
            val intent = Intent(this, targetClass).apply {
                putExtras(this@ProxyActivity.intent)
                addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT)
            }
            
            // 4. Final Hook: Force the system to use our ClassLoader for the next activity
            overrideClassLoader(classLoader)
            
            startActivity(intent)
            finish()
            PotataApp.addLog("Proxy: Handover Complete.")

        } catch (e: Exception) {
            Log.e(TAG, "Proxy Failed", e)
            PotataApp.addLog("Proxy Error: ${e.message}")
            finish()
        }
    }

    @SuppressLint("DiscouragedPrivateApi")
    private fun overrideClassLoader(cl: ClassLoader) {
        try {
            val mPackagesField = activityThread().javaClass.getDeclaredField("mPackages")
            mPackagesField.isAccessible = true
            val mPackages = mPackagesField.get(activityThread()) as MutableMap<String, *>
            val loadedApkWeakRef = mPackages[packageName] as java.lang.ref.WeakReference<*>
            val loadedApk = loadedApkWeakRef.get() ?: return
            
            val mClassLoaderField = loadedApk.javaClass.getDeclaredField("mClassLoader")
            mClassLoaderField.isAccessible = true
            mClassLoaderField.set(loadedApk, cl)
        } catch (e: Exception) {
            Log.e(TAG, "ClassLoader Override Fail", e)
        }
    }

    private fun activityThread(): Any {
        return Class.forName("android.app.ActivityThread")
            .getDeclaredMethod("currentActivityThread")
            .invoke(null)
    }
}
