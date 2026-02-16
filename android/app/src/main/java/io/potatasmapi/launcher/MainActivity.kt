package io.potatasmapi.launcher

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
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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

// Global Theme Colors
val StardewGreen = Color(0xFF7BB661)
val StardewBrown = Color(0xFF4E342E)
val StardewLightBrown = Color(0xFF8D6E63)
val StardewGold = Color(0xFFF4B41A)
val StardewDark = Color(0xFF1A1A1A)
val StardewSurface = Color(0xFF252525)

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkPermissionStatus()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = StardewGreen,
                    secondary = StardewGold,
                    background = StardewDark,
                    surface = StardewSurface
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = StardewDark) {
                    if (!hasPermission) {
                        WelcomeScreen()
                    } else {
                        PotataDashboard()
                    }
                }
            }
        }
    }

    private fun checkPermissionStatus() {
        hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
        if (hasPermission) {
            initFolders()
            loadMods()
        }
    }

    private fun initFolders() {
        val root = File(Environment.getExternalStorageDirectory(), DEFAULT_FOLDER)
        if (!root.exists()) root.mkdirs()
        File(root, "Mods").mkdirs()
    }

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
                    foundMods.add(ModInfo(folder.name, folder.name, "Incomplete", "0.0", isEnabled))
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
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(StardewBrown)
                    .border(4.dp, StardewGold, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("🥔", fontSize = 60.sp)
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text("PotataSMAPI", color = StardewGold, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
            Text("Virtual Farm Loader", color = StardewGreen, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Run Stardew Valley mods without modifying the original game. Safe, root-free, and side-by-side compatible.",
                color = Color.Gray,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
            Spacer(modifier = Modifier.height(48.dp))
            Button(
                onClick = { openPermissionSettings() },
                colors = ButtonDefaults.buttonColors(containerColor = StardewGreen),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(64.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
            ) {
                Text("INITIALIZE VIRTUAL CORE", color = Color.Black, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
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
            val virtualMarker = File(filesDir, "virtual/stardew/virtual.ready")
            if (virtualMarker.exists()) isPatched = true
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Farm Manager", color = Color.Gray, fontSize = 14.sp)
                    Text("Potata Virtual", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(StardewBrown),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = StardewGold, modifier = Modifier.size(24.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            // Main Action Card (Bento Style)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = StardewSurface),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isPatched) StardewGreen.copy(0.2f) else StardewGold.copy(0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isPatched) Icons.Default.CheckCircle else Icons.Default.Info,
                                contentDescription = null,
                                tint = if (isPatched) StardewGreen else StardewGold
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                if (isPatched) "Virtual Farm Ready" else "Setup Required",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                statusText ?: if (isPatched) "Game loaded in virtual memory" else if (activePath != null) "Game APK detected" else "Locate original APK",
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    if (!isPatched) {
                        Button(
                            onClick = { 
                                activePath?.let { path ->
                                    isPatching = true
                                    statusText = "Importing game resources..."
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            PatcherService(this@MainActivity).importGame(path)
                                            isPatched = true
                                            statusText = "Success!"
                                        } catch (e: Exception) {
                                            statusText = "Import Failed: ${e.message}"
                                            Log.e("Potata", "Error: ${Log.getStackTraceString(e)}")
                                        } finally { isPatching = false }
                                    }
                                }
                            },
                            enabled = activePath != null && !isPatching,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = StardewGreen)
                        ) {
                            if (isPatching) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("IMPORTING...", color = Color.Black, fontWeight = FontWeight.Bold)
                            } else {
                                Text("IMPORT GAME RESOURCES", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { 
                                    statusText = "Launching Virtual Game... (WIP)" 
                                },
                                modifier = Modifier.weight(1f).height(56.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = StardewGreen)
                            ) {
                                Text("LAUNCH GAME", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = { 
                                    File(filesDir, "virtual/stardew").deleteRecursively()
                                    isPatched = false
                                    statusText = null
                                },
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.Red.copy(0.1f))
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Stats Bento Grid
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatCard("MODS LOADED", modList.size.toString(), Icons.Default.List, Modifier.weight(1f))
                StatCard("ENGINE VER", "4.5.1", Icons.Default.Build, Modifier.weight(1f))
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            // Mod Manager Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Mod Library", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { loadMods() }) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Refresh", color = StardewGold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Mod List
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(modList.size) { index ->
                    ModItem(modList[index])
                }
            }

            // --- DEV CONSOLE ---
            var consoleExpanded by remember { mutableStateOf(false) }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .animateContentSize()
                    .clickable { consoleExpanded = !consoleExpanded },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.4f)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.List, contentDescription = null, tint = StardewGold, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("VIRTUAL LOGS", color = StardewGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(if (consoleExpanded) "CLOSE" else "VIEW LOGS", color = Color.Gray, fontSize = 9.sp)
                    }
                    if (consoleExpanded) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(modifier = Modifier.height(150.dp)) {
                            items(PotataApp.logs.size) { index ->
                                Text(
                                    PotataApp.logs[index],
                                    color = if (PotataApp.logs[index].contains("Failed")) Color.Red else Color.LightGray,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun ModItem(mod: ModInfo) {
        val backgroundColor = if (mod.isEnabled) StardewSurface else StardewSurface.copy(0.5f)
        val contentAlpha = if (mod.isEnabled) 1f else 0.5f
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            border = if (mod.isEnabled) BorderStroke(1.dp, StardewGreen.copy(0.1f)) else null
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (mod.isEnabled) StardewGreen.copy(0.1f) else Color.Gray.copy(0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📦", fontSize = 20.sp, modifier = Modifier.alpha(contentAlpha))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        mod.name, 
                        color = Color.White.copy(contentAlpha), 
                        fontSize = 16.sp, 
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        "by ${mod.author}", 
                        color = Color.Gray.copy(contentAlpha), 
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = mod.isEnabled,
                    onCheckedChange = { toggleMod(mod) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = StardewGreen,
                        checkedTrackColor = StardewGreen.copy(0.3f)
                    )
                )
            }
        }
    }

    @Composable
    fun StatCard(title: String, value: String, icon: ImageVector, modifier: Modifier) {
        Card(
            modifier = modifier.height(100.dp), 
            shape = RoundedCornerShape(24.dp), 
            colors = CardDefaults.cardColors(containerColor = StardewSurface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(title, color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(value, color = StardewGold, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}
