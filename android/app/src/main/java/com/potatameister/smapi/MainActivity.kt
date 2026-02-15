package com.potatameister.smapi

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    private val DEFAULT_FOLDER = "PotataSMAPI"
    
    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { checkPermissionStatus() }

    private val apkPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.getData() != null) {
            manualApkPath = result.data?.data?.toString()
        }
    }

    private var hasPermission by mutableStateOf(false)
    private var manualApkPath by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkPermissionStatus()
        
        setContent {
            if (!hasPermission) {
                WelcomeScreen()
            } else {
                PotataDashboard()
            }
        }
    }

    private fun checkPermissionStatus() {
        hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
        if (hasPermission) initFolders()
    }

    private fun initFolders() {
        val root = File(Environment.getExternalStorageDirectory(), DEFAULT_FOLDER)
        if (!root.exists()) root.mkdirs()
        File(root, "Mods").mkdirs()
    }

    private fun openPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:$packageName")
            try { manageStorageLauncher.launch(intent) }
            catch (e: Exception) {
                val fallback = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                manageStorageLauncher.launch(fallback)
            }
        }
    }

    private fun openApkPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/vnd.android.package-archive"
        }
        apkPickerLauncher.launch(intent)
    }

    private fun locateStardew(): String? {
        return try {
            packageManager.getApplicationInfo("com.chucklefish.stardewvalley", 0).publicSourceDir
        } catch (e: Exception) { null }
    }

    @Composable
    fun WelcomeScreen() {
        Column(
            modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A)).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("🥔", fontSize = 64.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text("PotataSMAPI Setup", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(12.dp))
            Text("Permission required to manage mods and patch the game files locally on your device.", color = Color.Gray, fontSize = 14.sp, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = { openPermissionSettings() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("ALLOW STORAGE ACCESS", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }

    @Composable
    fun PotataDashboard() {
        val autoPath = remember { locateStardew() }
        val activePath = manualApkPath ?: autoPath
        
        var modCount by remember { mutableStateOf(0) }
        var isPatching by remember { mutableStateOf(false) }
        var isPatched by remember { mutableStateOf(false) }
        var statusText by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        
        LaunchedEffect(Unit) {
            val modsFolder = File(Environment.getExternalStorageDirectory(), "$DEFAULT_FOLDER/Mods")
            modCount = modsFolder.listFiles()?.count { it.isDirectory } ?: 0
            val patchedFile = File(externalCacheDir, "patch_workspace/modded_stardew.apk")
            if (patchedFile.exists()) isPatched = true
        }

        Column(
            modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A)).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("PotataSMAPI v1.0", color = Color(0xFF4CAF50), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141414))
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = statusText ?: if (isPatched) "Patch Ready" else if (activePath != null) "Game Detected" else "Action Required",
                        color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold
                    )
                    
                    if (!isPatched) {
                        Button(
                            onClick = { 
                                activePath?.let { path ->
                                    isPatching = true
                                    statusText = "Initializing Patcher..."
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            PatcherService(this@MainActivity).patchGame(path)
                                            isPatched = true
                                            statusText = "Success!"
                                        } catch (e: Exception) {
                                            statusText = "Failed: ${e.message}"
                                            Log.e("Potata", "Error", e)
                                        } finally { isPatching = false }
                                    }
                                }
                            },
                            enabled = activePath != null && !isPatching,
                            modifier = Modifier.padding(top = 20.dp).fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                        ) {
                            Text(if (isPatching) "PROCESSING..." else "PATCH GAME", color = Color.Black, fontWeight = FontWeight.Bold)
                        }

                        if (!isPatching && autoPath != null) {
                            OutlinedButton(
                                onClick = { openApkPicker() },
                                modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF222222))
                            ) {
                                Text("SELECT APK MANUALLY", color = Color.Gray, fontSize = 12.sp)
                            }
                        } else if (activePath == null) {
                            Button(
                                onClick = { openApkPicker() },
                                modifier = Modifier.padding(top = 20.dp).fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222))
                            ) {
                                Text("SELECT APK MANUALLY", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Button(
                            onClick = { 
                                scope.launch(Dispatchers.IO) { PatcherService(this@MainActivity).patchGame(activePath!!) }
                            },
                            modifier = Modifier.padding(top = 20.dp).fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                        ) {
                            Text("INSTALL APK", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                        
                        TextButton(onClick = { 
                            File(externalCacheDir, "patch_workspace").deleteRecursively()
                            isPatched = false
                            statusText = null
                        }) {
                            Text("RESET WORKSPACE", color = Color.Red.copy(alpha = 0.6f), fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("MODS DETECTED", modCount.toString(), Modifier.weight(1f))
                StatCard("SMAPI VERSION", "4.5.1", Modifier.weight(1f))
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F0F))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("FOLDER: /sdcard/$DEFAULT_FOLDER", color = Color.DarkGray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    @Composable
    fun StatCard(title: String, value: String, modifier: Modifier) {
        Card(modifier = modifier.height(80.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF141414))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(title, color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
