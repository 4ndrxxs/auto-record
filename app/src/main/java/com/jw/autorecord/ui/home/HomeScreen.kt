package com.jw.autorecord.ui.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jw.autorecord.data.Schedule
import com.jw.autorecord.service.AlarmScheduler
import com.jw.autorecord.service.RecordingState
import com.jw.autorecord.service.ScheduleMonitorService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel = viewModel()) {
    val schedules by viewModel.todaySchedules.collectAsState()
    val masterEnabled by viewModel.masterEnabled.collectAsState()
    val recState by viewModel.recordingState.collectAsState()
    val recordedFiles by viewModel.recordedFiles.collectAsState()

    val today = remember {
        val cal = java.util.Calendar.getInstance()
        val day = AlarmScheduler.calendarDayToOurDay(cal.get(java.util.Calendar.DAY_OF_WEEK))
        Schedule.dayName(day) + "요일"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "오늘의 녹음",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    today,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ★ ON/OFF 토글 — 확실히 구분되는 색상
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (masterEnabled) "ON" else "OFF",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (masterEnabled) Color(0xFF4CAF50) else Color(0xFF9E9E9E)
                )
                Spacer(Modifier.width(6.dp))
                Switch(
                    checked = masterEnabled,
                    onCheckedChange = { viewModel.setMasterEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF4CAF50),
                        checkedBorderColor = Color(0xFF388E3C),
                        uncheckedThumbColor = Color(0xFF9E9E9E),
                        uncheckedTrackColor = Color(0xFF424242),
                        uncheckedBorderColor = Color(0xFF616161)
                    )
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // 녹음 중이면 상세 카드 + 조작 버튼
        if (recState.isRecording) {
            RecordingDetailCard(recState)
            Spacer(Modifier.height(16.dp))
        }

        if (schedules.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("시간표를 먼저 설정해주세요", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(schedules) { schedule ->
                    ScheduleCard(
                        schedule = schedule,
                        isRecording = recState.isRecording && recState.period == schedule.period,
                        isRecorded = recordedFiles.any { it.contains("${schedule.period}교시") },
                        recState = recState
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordingDetailCard(state: RecordingState.State) {
    val context = LocalContext.current

    val infiniteTransition = rememberInfiniteTransition(label = "blink")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (state.isPaused) 1f else 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blinkAlpha"
    )

    val amplitudeLevel = (state.amplitudeDb.toFloat() / 32767f).coerceIn(0f, 1f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (state.isPaused)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            // 상태 헤더
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(12.dp).clip(CircleShape)
                        .background(
                            if (state.isPaused) Color(0xFFFFA726).copy(alpha = alpha)
                            else Color.Red.copy(alpha = alpha)
                        )
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (state.isPaused) "일시정지" else "녹음 중",
                    fontWeight = FontWeight.Bold, fontSize = 18.sp,
                    color = if (state.isPaused) Color(0xFFFFA726) else MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${state.period}교시 ${state.subject}",
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(Modifier.height(4.dp))
            Text(state.teacher, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(16.dp))

            // 프로그레스 바
            LinearProgressIndicator(
                progress = { state.progressPercent },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = if (state.isPaused) Color(0xFFFFA726) else MaterialTheme.colorScheme.error,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(state.elapsedFormatted(), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                Text("-${state.remainingFormatted()} 남음", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(16.dp))

            // 상세 정보
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                DetailItem(Icons.Default.Timer, "녹음 시간", state.elapsedFormatted())
                DetailItem(Icons.Default.Storage, "파일 크기", state.fileSizeFormatted())
                DetailItem(Icons.Default.GraphicEq, "입력 세기", "${(amplitudeLevel * 100).toInt()}%")
            }

            Spacer(Modifier.height(12.dp))

            // 마이크 입력 바
            Text("마이크 입력", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier.fillMaxHeight().fillMaxWidth(amplitudeLevel).clip(RoundedCornerShape(3.dp))
                        .background(
                            when {
                                amplitudeLevel > 0.7f -> Color(0xFFE53935)
                                amplitudeLevel > 0.4f -> Color(0xFFFFA726)
                                else -> Color(0xFF66BB6A)
                            }
                        )
                )
            }

            Spacer(Modifier.height(16.dp))

            // ★ 녹음 조작 버튼
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 일시정지 / 재개
                FilledTonalButton(
                    onClick = {
                        if (state.isPaused) {
                            ScheduleMonitorService.sendAction(context, ScheduleMonitorService.ACTION_RESUME)
                        } else {
                            ScheduleMonitorService.sendAction(context, ScheduleMonitorService.ACTION_PAUSE)
                        }
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (state.isPaused) Color(0xFF4CAF50).copy(alpha = 0.2f)
                        else Color(0xFFFFA726).copy(alpha = 0.2f)
                    )
                ) {
                    Icon(
                        if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        null, Modifier.size(20.dp),
                        tint = if (state.isPaused) Color(0xFF4CAF50) else Color(0xFFFFA726)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (state.isPaused) "녹음 재개" else "일시정지",
                        color = if (state.isPaused) Color(0xFF4CAF50) else Color(0xFFFFA726)
                    )
                }

                Spacer(Modifier.width(12.dp))

                // 강제 중지
                var showStopDialog by remember { mutableStateOf(false) }
                FilledTonalButton(
                    onClick = { showStopDialog = true },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                    )
                ) {
                    Icon(Icons.Default.Stop, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text("중지", color = MaterialTheme.colorScheme.error)
                }

                if (showStopDialog) {
                    AlertDialog(
                        onDismissRequest = { showStopDialog = false },
                        title = { Text("녹음 중지") },
                        text = { Text("${state.period}교시 ${state.subject} 녹음을 중지하시겠습니까?\n${state.elapsedFormatted()} 분량이 저장됩니다.") },
                        confirmButton = {
                            TextButton(onClick = {
                                ScheduleMonitorService.sendAction(context, ScheduleMonitorService.ACTION_STOP)
                                showStopDialog = false
                            }) { Text("중지", color = MaterialTheme.colorScheme.error) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showStopDialog = false }) { Text("취소") }
                        }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                state.filePath.substringAfterLast("AutoRecord/"),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun DetailItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ScheduleCard(schedule: Schedule, isRecording: Boolean, isRecorded: Boolean, recState: RecordingState.State) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isRecording -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                isRecorded -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(
                    when {
                        isRecording -> MaterialTheme.colorScheme.error
                        isRecorded -> Color(0xFF4CAF50)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
                contentAlignment = Alignment.Center
            ) {
                if (isRecording) {
                    Icon(Icons.Default.Mic, null, tint = Color.White, modifier = Modifier.size(22.dp))
                } else {
                    Text("${schedule.period}", fontWeight = FontWeight.Bold, fontSize = 18.sp,
                        color = if (isRecorded) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(1f)) {
                Text(schedule.subject, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                Text("${schedule.teacher} | ${schedule.startTime}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (isRecording) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (recState.isPaused) "일시정지 | ${recState.elapsedFormatted()}" else "${recState.elapsedFormatted()} 경과",
                        fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        color = if (recState.isPaused) Color(0xFFFFA726) else MaterialTheme.colorScheme.error
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                when {
                    isRecording && recState.isPaused -> {
                        Icon(Icons.Default.PauseCircle, "일시정지", tint = Color(0xFFFFA726), modifier = Modifier.size(20.dp))
                        Text("일시정지", fontSize = 10.sp, color = Color(0xFFFFA726))
                    }
                    isRecording -> {
                        Icon(Icons.Default.FiberManualRecord, "녹음 중", tint = Color.Red, modifier = Modifier.size(20.dp))
                        Text("녹음 중", fontSize = 10.sp, color = Color.Red)
                    }
                    isRecorded -> {
                        Icon(Icons.Default.CheckCircle, "완료", tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                        Text("완료", fontSize = 10.sp, color = Color(0xFF4CAF50))
                    }
                    else -> {
                        Icon(Icons.Default.Schedule, "대기", tint = Color.Gray, modifier = Modifier.size(20.dp))
                        Text("대기", fontSize = 10.sp, color = Color.Gray)
                    }
                }
            }
        }
    }
}
