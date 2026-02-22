package io.potatasmapi.launcher

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.AssetManager
import android.os.Bundle
import android.util.Log
import java.io.File
import dalvik.system.DexClassLoader

/**
 * ProxyActivity: The "Shell" that actually runs the Stardew Valley code.
 * It intercepts all system calls and redirects them to the Virtual Cartridge.
 */
class ProxyActivity : Activity() {
    private val TAG = "PotataProxy"
    
    private var virtualClassLoader: DexClassLoader? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            val targetActivityName = intent.getStringExtra("TARGET_ACTIVITY") ?: return
            val dexPath = intent.getStringExtra("DEX_PATH") ?: return
            val libPath = intent.getStringExtra("LIB_PATH") ?: return

            PotataApp.addLog("Proxy: Initializing Engine...")
            
            // Fix: Copy APKs to read-only location for DexClassLoader
            val dexRoot = File(filesDir, "dex").apply { mkdirs() }
            val sourceApks = dexPath.split(File.pathSeparator).filter { it.endsWith(".apk") }
            val dexPathList = mutableListOf<String>()
            
            for (apkPath in sourceApks) {
                val sourceFile = File(apkPath)
                val destFile = File(dexRoot, sourceFile.name)
                sourceFile.inputStream().use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                }
                destFile.setReadOnly()
                dexPathList.add(destFile.absolutePath)
            }
            
            val fixedDexPath = dexPathList.joinToString(File.pathSeparator)
            
            // 1. Setup Virtual ClassLoader for this instance
            virtualClassLoader = DexClassLoader(
                fixedDexPath, 
                File(codeCacheDir, "opt_dex").absolutePath, 
                libPath, 
                this.javaClass.classLoader
            )

            // 2. CRITICAL: Apply system hooks BEFORE starting the game
            applySystemHooks(virtualClassLoader!!, dexPath, libPath)
            
            // 3. Set up environment variables for Mono
            setupEnvironment(libPath)
            
            // 4. Hijack the Context for the coming Activity
            val targetClass = virtualClassLoader!!.loadClass(targetActivityName)
            
            PotataApp.addLog("Proxy: Target Class Loaded.")

            // 4. Launch the actual game activity from the virtual loader
            val gameIntent = Intent(this, targetClass).apply {
                putExtras(this@ProxyActivity.intent)
                addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT)
            }
            
            startActivity(gameIntent)
            finish()
            PotataApp.addLog("Proxy: Handover Complete.")

        } catch (e: Exception) {
            Log.e(TAG, "Proxy Failed", e)
            PotataApp.addLog("Proxy Error: ${e.message}")
            finish()
        }
    }

    @SuppressLint("DiscouragedPrivateApi")
    private fun applySystemHooks(classLoader: DexClassLoader, dexPath: String, libPath: String) {
        try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentActivityThread = activityThreadClass.getDeclaredMethod("currentActivityThread").invoke(null)
            
            // Get mBoundApplication
            val mBoundApplicationField = activityThreadClass.getDeclaredField("mBoundApplication")
            mBoundApplicationField.isAccessible = true
            val mBoundApplication = mBoundApplicationField.get(currentActivityThread)
            
            // Update appInfo
            val infoField = mBoundApplication.javaClass.getDeclaredField("appInfo")
            infoField.isAccessible = true
            val appInfo = infoField.get(mBoundApplication) as ApplicationInfo
            
            val virtualRoot = File(filesDir, "virtual/stardew")
            val dataDir = virtualRoot.absolutePath
            val baseApk = File(virtualRoot, "base.apk").absolutePath
            
            appInfo.dataDir = dataDir
            appInfo.sourceDir = baseApk
            appInfo.publicSourceDir = baseApk
            appInfo.nativeLibraryDir = libPath
            
            PotataApp.addLog("Proxy: System Records Updated")
            
            // Update mPackages classloader
            val mPackagesField = activityThreadClass.getDeclaredField("mPackages")
            mPackagesField.isAccessible = true
            val mPackages = mPackagesField.get(currentActivityThread) as MutableMap<String, *>
            
            val hostPkg = packageName
            val loadedApkWeakRef = mPackages[hostPkg] as? java.lang.ref.WeakReference<*>
            val loadedApk = loadedApkWeakRef?.get()
            
            if (loadedApk != null) {
                val loadedApkClass = loadedApk.javaClass
                loadedApkClass.getDeclaredField("mClassLoader").apply { 
                    isAccessible = true 
                }.set(loadedApk, classLoader)
                
                loadedApkClass.getDeclaredField("mAppDir").apply { 
                    isAccessible = true 
                }.set(loadedApk, baseApk)
                
                loadedApkClass.getDeclaredField("mDataDir").apply { 
                    isAccessible = true 
                }.set(loadedApk, dataDir)
                
                loadedApkClass.getDeclaredField("mLibDir").apply { 
                    isAccessible = true 
                }.set(loadedApk, libPath)
                
                PotataApp.addLog("Proxy: Package Registry Updated")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "System Hooks Failed", e)
            PotataApp.addLog("Proxy Hook Error: ${e.message}")
        }
    }

    private fun setupEnvironment(libPath: String) {
        try {
            val sdcardRoot = File(android.os.Environment.getExternalStorageDirectory(), "PotataSMAPI")
            val assembliesDir = File(sdcardRoot, "assemblies")
            val baseDir = sdcardRoot.absolutePath
            
            android.system.Os.setenv("MONO_PATH", assembliesDir.absolutePath, true)
            android.system.Os.setenv("SMAPI_ANDROID_BASE_DIR", baseDir, true)
            android.system.Os.setenv("HOME", baseDir, true)
            android.system.Os.setenv("EXTERNAL_STORAGE", baseDir, true)
            android.system.Os.setenv("LD_LIBRARY_PATH", libPath, true)
            android.system.Os.setenv("DOTNET_STARTUP_HOOKS", File(assembliesDir, "Stardew Valley.dll").absolutePath, true)
            
            // Pre-load native libraries
            val libDir = File(filesDir, "virtual/stardew/lib")
            try {
                System.load(File(libDir, "libxamarin-app.so").absolutePath)
                System.load(File(libDir, "libmonosgen-2.0.so").absolutePath)
                System.load(File(libDir, "libmonodroid.so").absolutePath)
                PotataApp.addLog("Proxy: Native Engines Loaded")
            } catch (e: Throwable) {
                PotataApp.addLog("Proxy: Engine Load Warning: ${e.message}")
            }
            
            PotataApp.addLog("Proxy: Environment Ready")
        } catch (e: Exception) {
            PotataApp.addLog("Proxy: Env Setup Error: ${e.message}")
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
