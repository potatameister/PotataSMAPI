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
import org.json.JSONObject

data class ModInfo(
    val folderName: String,
    val name: String,
    val author: String,
    val version: String,
    val isEnabled: Boolean
)

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
    private var modList by mutableStateOf<List<ModInfo>>(emptyList())

    // Stardew-inspired Palette
    val StardewGreen = Color(0xFF4CAF50)
    val StardewBrown = Color(0xFF5D4037)
    val StardewGold = Color(0xFFFBC02D)
    val StardewDark = Color(0xFF1B1B1B)

    private fun loadMods() {
        val modsFolder = File(Environment.getExternalStorageDirectory(), "$DEFAULT_FOLDER/Mods")
        if (!modsFolder.exists()) return
        
        val foundMods = mutableListOf<ModInfo>()
        modsFolder.listFiles()?.filter { it.isDirectory }?.forEach { folder ->
            val isEnabled = !folder.name.startsWith(".")
            val manifestFile = File(folder, "manifest.json")
            
            if (manifestFile.exists()) {
                try {
                    val json = JSONObject(manifestFile.readText())
                    foundMods.add(ModInfo(
                        folderName = folder.name,
                        name = json.optString("Name", folder.name),
                        author = json.optString("Author", "Unknown"),
                        version = json.optString("Version", "1.0.0"),
                        isEnabled = isEnabled
                    ))
                } catch (e: Exception) {
                    foundMods.add(ModInfo(folder.name, folder.name, "Error", "0.0", isEnabled))
                }
            }
        }
        modList = foundMods.sortedBy { it.name }
    }

    private fun toggleMod(mod: ModInfo) {
        val modsFolder = File(Environment.getExternalStorageDirectory(), "$DEFAULT_FOLDER/Mods")
        val oldFile = File(modsFolder, mod.folderName)
        val newName = if (mod.isEnabled) ".${mod.folderName}" else mod.folderName.removePrefix(".")
        val newFile = File(modsFolder, newName)
        
        if (oldFile.renameTo(newFile)) {
            loadMods()
        }
    }

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
            modifier = Modifier.fillMaxSize().background(StardewDark).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("🥔", fontSize = 80.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text("PotataSMAPI", color = StardewGold, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(12.dp))
            Text("A modern, stable launcher for Stardew Valley mods on Android.", color = Color.Gray, fontSize = 14.sp, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(48.dp))
            Button(
                onClick = { openPermissionSettings() },
                colors = ButtonDefaults.buttonColors(containerColor = StardewGreen),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(64.dp)
            ) {
                Text("GET STARTED", color = Color.Black, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }
    }

    @Composable
    fun PotataDashboard() {
        val autoPath = remember { locateStardew() }
        val activePath = manualApkPath ?: autoPath
        
        var isPatching by remember { mutableStateOf(false) }
        var isPatched by remember { mutableStateOf(false) }
        var statusText by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        
        LaunchedEffect(Unit) {
            loadMods()
            val patchedFile = File(externalCacheDir, "patch_workspace/modded_stardew.apk")
            if (patchedFile.exists()) isPatched = true
        }

        Column(
            modifier = Modifier.fillMaxSize().background(StardewDark).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("PotataSMAPI v1.0", color = StardewGold, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
            
            // Patch Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF252525))
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = statusText ?: if (isPatched) "Surgery Complete" else if (activePath != null) "Game Detected" else "Action Required",
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
                                            val errorMsg = android.util.Log.getStackTraceString(e)
                                            statusText = "Failed: ${e.localizedMessage}"
                                            Log.e("Potata", "Error: $errorMsg")
                                        } finally { isPatching = false }
                                    }
                                }
                            },
                            enabled = activePath != null && !isPatching,
                            modifier = Modifier.padding(top = 20.dp).fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = StardewGreen)
                        ) {
                            Text(if (isPatching) "PROCESSING..." else "PERFORM SURGERY", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = { 
                                // Re-trigger installer
                                scope.launch(Dispatchers.IO) { PatcherService(this@MainActivity).patchGame(activePath!!) }
                            },
                            modifier = Modifier.padding(top = 20.dp).fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = StardewGreen)
                        ) {
                            Text("INSTALL MODDED APK", color = Color.Black, fontWeight = FontWeight.Bold)
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

            Spacer(modifier = Modifier.height(16.dp))

            // Stats Row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("MODS LOADED", modList.size.toString(), Modifier.weight(1f))
                StatCard("ENGINE", "4.5.1", Modifier.weight(1f))
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            // Mod List Header
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("MOD MANAGER", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { loadMods() }) {
                    Text("REFRESH", color = StardewGold, fontSize = 12.sp)
                }
            }

            // Mod List
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(modList.size) { index ->
                    val mod = modList[index]
                    ModItem(mod)
                }
            }
        }
    }

    @Composable
    fun ModItem(mod: ModInfo) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = if (mod.isEnabled) Color(0xFF2D2D2D) else Color(0xFF1A1A1A))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(mod.name, color = if (mod.isEnabled) Color.White else Color.Gray, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("by ${mod.author} • v${mod.version}", color = Color.Gray, fontSize = 12.sp)
                }
                Switch(
                    checked = mod.isEnabled,
                    onCheckedChange = { toggleMod(mod) },
                    colors = SwitchDefaults.colors(checkedThumbColor = StardewGreen)
                )
            }
        }
    }

    @Composable
    fun StatCard(title: String, value: String, modifier: Modifier) {
        Card(modifier = modifier.height(80.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = StardewBrown)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(title, color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Text(value, color = StardewGold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
