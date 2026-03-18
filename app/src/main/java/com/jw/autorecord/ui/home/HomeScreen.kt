package com.jw.autorecord.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel = viewModel()) {
    val schedules by viewModel.todaySchedules.collectAsState()
    val masterEnabled by viewModel.masterEnabled.collectAsState()
    val recordingPeriod by viewModel.recordingPeriod.collectAsState()
    val recordedFiles = remember { viewModel.getRecordedFiles() }

    val today = remember {
        val cal = java.util.Calendar.getInstance()
        val day = com.jw.autorecord.service.AlarmScheduler.calendarDayToOurDay(
            cal.get(java.util.Calendar.DAY_OF_WEEK)
        )
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

            // Master toggle
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

        Spacer(Modifier.height(20.dp))

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
                        isRecording = recordingPeriod == schedule.period,
                        isRecorded = recordedFiles.any { it.contains("${schedule.period}교시") }
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleCard(schedule: Schedule, isRecording: Boolean, isRecorded: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isRecording)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Period badge
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (isRecording) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${schedule.period}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = if (isRecording) Color.White
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
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
            }

            // Status icon
            when {
                isRecording -> Icon(
                    Icons.Default.Mic,
                    contentDescription = "녹음 중",
                    tint = MaterialTheme.colorScheme.primary
                )
                isRecorded -> Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "완료",
                    tint = Color(0xFF4CAF50)
                )
                else -> Icon(
                    Icons.Default.Schedule,
                    contentDescription = "대기",
                    tint = Color.Gray
                )
            }
        }
    }
}
