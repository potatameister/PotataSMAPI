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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

class MainActivity : ComponentActivity() {
    private val DEFAULT_FOLDER = "PotataSMAPI"
    
    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { checkAndInit() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndInit()
        
        setContent {
            PotataDashboard()
        }
    }

    private fun checkAndInit() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                manageStorageLauncher.launch(intent)
            }
        }
        
        val root = File(Environment.getExternalStorageDirectory(), DEFAULT_FOLDER)
        if (!root.exists()) root.mkdirs()
        File(root, "Mods").mkdirs()
    }

    private fun locateStardew(): String? {
        return try {
            packageManager.getApplicationInfo("com.chucklefish.stardewvalley", 0).publicSourceDir
        } catch (e: Exception) {
            null
        }
    }

    @Composable
    fun PotataDashboard() {
        var apkPath by remember { mutableStateOf(locateStardew()) }
        var modCount by remember { mutableStateOf(0) }
        
        LaunchedEffect(Unit) {
            val modsFolder = File(Environment.getExternalStorageDirectory(), "$DEFAULT_FOLDER/Mods")
            modCount = modsFolder.listFiles()?.count { it.isDirectory } ?: 0
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0A0A))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "PotataSMAPI Native",
                color = Color(0xFF4CAF50),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            Card(
                modifier = Modifier.fillMaxWidth().height(180.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141414))
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (apkPath != null) "Ready to Farm" else "Game Not Found",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    
                    Button(
                        onClick = { /* TODO: Launch Patcher */ },
                        enabled = apkPath != null,
                        modifier = Modifier.padding(top = 20.dp).fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Text("PATCH & LAUNCH", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                StatCard("MODS", modCount.toString(), Modifier.weight(1f))
                Spacer(modifier = Modifier.width(12.dp))
                StatCard("ENGINE", "4.5.1", Modifier.weight(1f))
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F0F))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("WORKSPACE", color = Color.Gray, fontSize = 10.sp)
                    Text(
                        text = "/sdcard/$DEFAULT_FOLDER",
                        color = Color.DarkGray,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }

    @Composable
    fun StatCard(title: String, value: String, modifier: Modifier) {
        Card(
            modifier = modifier.height(100.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141414))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(title, color = Color.Gray, fontSize = 10.sp)
                Text(value, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
