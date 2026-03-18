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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jw.autorecord.data.Schedule
import com.jw.autorecord.service.AlarmScheduler
import com.jw.autorecord.service.RecordingState

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

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (masterEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                    contentDescription = null,
                    tint = if (masterEnabled) MaterialTheme.colorScheme.primary else Color.Gray
                )
                Spacer(Modifier.width(4.dp))
                Switch(
                    checked = masterEnabled,
                    onCheckedChange = { viewModel.setMasterEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // 현재 녹음 중이면 상세 상태 카드
        if (recState.isRecording) {
            RecordingDetailCard(recState)
            Spacer(Modifier.height(16.dp))
        }

        if (schedules.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "시간표를 먼저 설정해주세요",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 16.sp
                )
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

/**
 * 녹음 중일 때 화면 상단에 표시되는 상세 상태 카드
 */
@Composable
private fun RecordingDetailCard(state: RecordingState.State) {
    // 깜빡이는 빨간 점 애니메이션
    val infiniteTransition = rememberInfiniteTransition(label = "blink")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blinkAlpha"
    )

    // 마이크 세기 바 레벨 (0~1 정규화)
    val amplitudeLevel = (state.amplitudeDb.toFloat() / 32767f).coerceIn(0f, 1f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // 상단: 빨간 점 + "녹음 중" + 과목
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color.Red.copy(alpha = alpha))
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "녹음 중",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${state.period}교시 ${state.subject}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                state.teacher,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            // 프로그레스 바
            LinearProgressIndicator(
                progress = { state.progressPercent },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.error,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            // 경과시간 / 남은시간
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    state.elapsedFormatted(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "-${state.remainingFormatted()} 남음",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(16.dp))

            // 상세 정보 그리드
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DetailItem(
                    icon = Icons.Default.Timer,
                    label = "녹음 시간",
                    value = state.elapsedFormatted()
                )
                DetailItem(
                    icon = Icons.Default.Storage,
                    label = "파일 크기",
                    value = state.fileSizeFormatted()
                )
                DetailItem(
                    icon = Icons.Default.GraphicEq,
                    label = "입력 세기",
                    value = "${(amplitudeLevel * 100).toInt()}%"
                )
            }

            Spacer(Modifier.height(12.dp))

            // 마이크 입력 세기 바
            Text(
                "마이크 입력",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))

            // 세기 시각화 바
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(amplitudeLevel)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            when {
                                amplitudeLevel > 0.7f -> Color(0xFFE53935) // 빨강
                                amplitudeLevel > 0.4f -> Color(0xFFFFA726) // 주황
                                else -> Color(0xFF66BB6A) // 초록
                            }
                        )
                )
            }

            Spacer(Modifier.height(12.dp))

            // 파일 경로
            Text(
                state.filePath.substringAfterLast("AutoRecord/"),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun DetailItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ScheduleCard(
    schedule: Schedule,
    isRecording: Boolean,
    isRecorded: Boolean,
    recState: RecordingState.State
) {
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 교시 뱃지
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isRecording -> MaterialTheme.colorScheme.error
                            isRecorded -> Color(0xFF4CAF50)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isRecording) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Text(
                        "${schedule.period}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = if (isRecorded) Color.White
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    schedule.subject,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "${schedule.teacher} | ${schedule.startTime}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isRecording) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${recState.elapsedFormatted()} 경과",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // 상태 아이콘 + 텍스트
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                when {
                    isRecording -> {
                        Icon(
                            Icons.Default.FiberManualRecord,
                            contentDescription = "녹음 중",
                            tint = Color.Red,
                            modifier = Modifier.size(20.dp)
                        )
                        Text("녹음 중", fontSize = 10.sp, color = Color.Red)
                    }
                    isRecorded -> {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "완료",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(20.dp)
                        )
                        Text("완료", fontSize = 10.sp, color = Color(0xFF4CAF50))
                    }
                    else -> {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = "대기",
                            tint = Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                        Text("대기", fontSize = 10.sp, color = Color.Gray)
                    }
                }
            }
        }
    }
}
