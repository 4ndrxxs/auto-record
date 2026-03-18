package com.jw.autorecord

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.jw.autorecord.ui.navigation.AppNavigation
import com.jw.autorecord.ui.theme.AutoRecordTheme
import com.jw.autorecord.updater.OtaUpdater
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requiredPermissions = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results.values.all { it }
    }

    private var permissionsGranted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        permissionsGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (!permissionsGranted) {
            permissionLauncher.launch(requiredPermissions.toTypedArray())
        }

        requestBatteryOptimizationExemption()

        setContent {
            AutoRecordTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (permissionsGranted) {
                        var updateInfo by remember { mutableStateOf<com.jw.autorecord.updater.UpdateInfo?>(null) }
                        val scope = rememberCoroutineScope()

                        LaunchedEffect(Unit) {
                            scope.launch {
                                updateInfo = OtaUpdater.checkForUpdate(this@MainActivity)
                            }
                        }

                        Box {
                            AppNavigation()

                            // Update dialog
                            updateInfo?.let { info ->
                                AlertDialog(
                                    onDismissRequest = { updateInfo = null },
                                    title = { Text("업데이트 가능") },
                                    text = {
                                        Column {
                                            Text("v${info.versionName} 버전이 있습니다.")
                                            if (info.changelog.isNotBlank()) {
                                                Spacer(Modifier.height(8.dp))
                                                Text(info.changelog)
                                            }
                                        }
                                    },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            OtaUpdater.downloadAndInstall(this@MainActivity, info)
                                            updateInfo = null
                                        }) {
                                            Text("다운로드")
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { updateInfo = null }) {
                                            Text("나중에")
                                        }
                                    }
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("녹음 권한이 필요합니다")
                                Spacer(Modifier.height(16.dp))
                                Button(onClick = {
                                    permissionLauncher.launch(requiredPermissions.toTypedArray())
                                }) {
                                    Text("권한 허용")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun requestBatteryOptimizationExemption() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }
}
