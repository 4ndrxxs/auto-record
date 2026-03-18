package com.jw.autorecord.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val recordingDuration by viewModel.recordingDuration.collectAsState()
    val audioBitrate by viewModel.audioBitrate.collectAsState()
    val audioSampleRate by viewModel.audioSampleRate.collectAsState()
    val storageInfo by viewModel.storageInfo.collectAsState()
    val backupResult by viewModel.backupResult.collectAsState()

    // 백업 파일 생성 런처
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.backupSchedules(it) }
    }

    // 복원 파일 선택 런처
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.restoreSchedules(it) }
    }

    // 삭제 확인
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDurationPicker by remember { mutableStateOf(false) }
    var showBitratePicker by remember { mutableStateOf(false) }
    var showSampleRatePicker by remember { mutableStateOf(false) }

    // 백업 결과 스낵바
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(backupResult) {
        backupResult?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearBackupResult()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    "설정",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }

            // ═══ 시간표 백업/복원 ═══
            item { SectionHeader("시간표 백업 / 복원") }

            item {
                SettingsCard {
                    SettingsItem(
                        icon = Icons.Default.Upload,
                        title = "시간표 백업",
                        subtitle = "시간표와 변경 내역을 JSON 파일로 저장",
                        onClick = {
                            val fileName = "autorecord_backup_${
                                java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.KOREA)
                                    .format(java.util.Date())
                            }.json"
                            backupLauncher.launch(fileName)
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsItem(
                        icon = Icons.Default.Download,
                        title = "시간표 복원",
                        subtitle = "백업 파일에서 시간표 가져오기",
                        onClick = { restoreLauncher.launch(arrayOf("application/json")) }
                    )
                }
            }

            // ═══ 녹음 설정 ═══
            item { SectionHeader("녹음 설정") }

            item {
                SettingsCard {
                    SettingsItem(
                        icon = Icons.Default.Timer,
                        title = "녹음 시간",
                        subtitle = "${recordingDuration}분",
                        onClick = { showDurationPicker = true }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsItem(
                        icon = Icons.Default.HighQuality,
                        title = "오디오 비트레이트",
                        subtitle = bitrateLabel(audioBitrate),
                        onClick = { showBitratePicker = true }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsItem(
                        icon = Icons.Default.GraphicEq,
                        title = "샘플레이트",
                        subtitle = sampleRateLabel(audioSampleRate),
                        onClick = { showSampleRatePicker = true }
                    )
                }
            }

            // ═══ 저장소 ═══
            item { SectionHeader("저장소") }

            item {
                SettingsCard {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("경로", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            storageInfo.basePath.ifBlank { "로딩 중..." },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 2.dp)
                        )

                        Spacer(Modifier.height(12.dp))

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StorageStat("녹음 파일", "${storageInfo.fileCount}개")
                            StorageStat("폴더", "${storageInfo.folderCount}개")
                            StorageStat("총 용량", storageInfo.totalSizeFormatted())
                        }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.DeleteForever, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("모든 녹음 파일 삭제")
                }
            }

            // ═══ 앱 정보 ═══
            item { SectionHeader("앱 정보") }

            item {
                SettingsCard {
                    Column(Modifier.padding(16.dp)) {
                        InfoRow("버전", "v${viewModel.appVersion} (${viewModel.appVersionCode})")
                        InfoRow("패키지", "com.jw.autorecord")
                        InfoRow("개발", "자동녹음 - 개인용")
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    // ═══ 다이얼로그들 ═══

    if (showDurationPicker) {
        OptionPickerDialog(
            title = "녹음 시간 (분)",
            options = listOf(30, 40, 45, 50, 55, 60, 70, 80, 90),
            current = recordingDuration,
            labelFn = { "${it}분" },
            onSelect = { viewModel.setRecordingDuration(it); showDurationPicker = false },
            onDismiss = { showDurationPicker = false }
        )
    }

    if (showBitratePicker) {
        OptionPickerDialog(
            title = "오디오 비트레이트",
            options = listOf(64000, 96000, 128000, 192000, 256000),
            current = audioBitrate,
            labelFn = { bitrateLabel(it) },
            onSelect = { viewModel.setAudioBitrate(it); showBitratePicker = false },
            onDismiss = { showBitratePicker = false }
        )
    }

    if (showSampleRatePicker) {
        OptionPickerDialog(
            title = "샘플레이트",
            options = listOf(22050, 44100, 48000),
            current = audioSampleRate,
            labelFn = { sampleRateLabel(it) },
            onSelect = { viewModel.setAudioSampleRate(it); showSampleRatePicker = false },
            onDismiss = { showSampleRatePicker = false }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("모든 녹음 삭제") },
            text = { Text("저장된 모든 녹음 파일(${storageInfo.fileCount}개, ${storageInfo.totalSizeFormatted()})이 영구 삭제됩니다.\n\n이 작업은 되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAllRecordings()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("전체 삭제") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("취소") }
            }
        )
    }
}

// ═══ 컴포넌트 ═══

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                Icons.Default.ChevronRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun StorageStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun <T> OptionPickerDialog(
    title: String,
    options: List<T>,
    current: T,
    labelFn: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    Surface(
                        onClick = { onSelect(option) },
                        color = if (option == current)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = option == current,
                                onClick = { onSelect(option) }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(labelFn(option), fontSize = 15.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}

private fun bitrateLabel(bitrate: Int): String = when (bitrate) {
    64000 -> "64 kbps (저품질)"
    96000 -> "96 kbps (보통)"
    128000 -> "128 kbps (표준)"
    192000 -> "192 kbps (고품질)"
    256000 -> "256 kbps (최고)"
    else -> "${bitrate / 1000} kbps"
}

private fun sampleRateLabel(rate: Int): String = when (rate) {
    22050 -> "22.05 kHz (저음질)"
    44100 -> "44.1 kHz (CD 품질)"
    48000 -> "48 kHz (스튜디오)"
    else -> "${rate / 1000} kHz"
}
