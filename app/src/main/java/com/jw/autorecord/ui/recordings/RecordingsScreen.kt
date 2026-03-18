package com.jw.autorecord.ui.recordings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordingsScreen(viewModel: RecordingsViewModel = viewModel()) {
    val dates by viewModel.dates.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val files by viewModel.files.collectAsState()
    val playerState by viewModel.playerState.collectAsState()

    var deleteTarget by remember { mutableStateOf<RecordingFile?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Content area
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectedDate != null) {
                    IconButton(onClick = { viewModel.goBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로")
                    }
                }
                Text(
                    selectedDate ?: "녹음 파일",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(Modifier.height(16.dp))

            if (selectedDate == null) {
                // Date list
                if (dates.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("녹음 파일이 없습니다", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(dates) { date ->
                            Card(
                                onClick = { viewModel.selectDate(date.dirName) },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            date.displayName,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    // 파일 수 뱃지
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            "${date.fileCount}개",
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // File list
                if (files.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("녹음 파일이 없습니다", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(files) { recording ->
                            val isActive = playerState.filePath == recording.file.absolutePath
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isActive)
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surface
                                ),
                                modifier = Modifier.combinedClickable(
                                    onClick = { viewModel.playFile(recording.file) },
                                    onLongClick = { deleteTarget = recording }
                                )
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Play icon
                                    Icon(
                                        if (isActive && playerState.isPlaying && !playerState.isPaused)
                                            Icons.Default.GraphicEq
                                        else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = if (isActive) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            recording.name,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            recording.sizeFormatted,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    // 삭제 버튼
                                    IconButton(
                                        onClick = { deleteTarget = recording },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "삭제",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ═══ 하단 뮤직 플레이어 바 ═══
        if (playerState.isPlaying || playerState.isPaused) {
            PlayerBar(
                state = playerState,
                onPlayPause = { viewModel.togglePlayPause() },
                onSeek = { viewModel.seekTo(it) },
                onSeekForward = { viewModel.seekForward(10) },
                onSeekBackward = { viewModel.seekBackward(10) },
                onSpeedChange = { viewModel.setSpeed(it) },
                onStop = { viewModel.stopPlayback() }
            )
        }
    }

    // Delete dialog
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("삭제") },
            text = { Text("${target.name}\n이 파일을 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteFile(target.file)
                    deleteTarget = null
                }) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("취소") }
            }
        )
    }
}

@Composable
private fun PlayerBar(
    state: PlayerState,
    onPlayPause: () -> Unit,
    onSeek: (Int) -> Unit,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onStop: () -> Unit
) {
    var showSpeedMenu by remember { mutableStateOf(false) }
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            // 파일명
            Text(
                state.fileName,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(4.dp))

            // 시크바
            Slider(
                value = state.currentMs.toFloat(),
                onValueChange = { onSeek(it.toInt()) },
                valueRange = 0f..state.totalMs.toFloat().coerceAtLeast(1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )

            // 시간 표시
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(state.currentFormatted(), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(state.totalFormatted(), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // 컨트롤 버튼
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 배속
                Box {
                    TextButton(onClick = { showSpeedMenu = true }) {
                        Text(
                            "${state.speed}x",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (state.speed != 1.0f) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = showSpeedMenu,
                        onDismissRequest = { showSpeedMenu = false }
                    ) {
                        speeds.forEach { speed ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "${speed}x",
                                        fontWeight = if (speed == state.speed) FontWeight.Bold else FontWeight.Normal,
                                        color = if (speed == state.speed) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    onSpeedChange(speed)
                                    showSpeedMenu = false
                                }
                            )
                        }
                    }
                }

                // 10초 뒤로
                IconButton(onClick = onSeekBackward) {
                    Icon(Icons.Default.Replay10, "10초 뒤로", modifier = Modifier.size(28.dp))
                }

                // 재생/일시정지
                FilledIconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = "재생/일시정지",
                        modifier = Modifier.size(28.dp),
                        tint = Color.White
                    )
                }

                // 10초 앞으로
                IconButton(onClick = onSeekForward) {
                    Icon(Icons.Default.Forward10, "10초 앞으로", modifier = Modifier.size(28.dp))
                }

                // 정지
                IconButton(onClick = onStop) {
                    Icon(
                        Icons.Default.Stop,
                        "정지",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
