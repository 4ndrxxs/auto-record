package com.jw.autorecord.ui.schedule

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jw.autorecord.data.Schedule

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(viewModel: ScheduleViewModel = viewModel()) {
    val selectedDay by viewModel.selectedDay.collectAsState()
    val schedules by viewModel.schedules.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var editingPeriod by remember { mutableIntStateOf(0) }
    var editingSchedule by remember { mutableStateOf<Schedule?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "시간표 설정",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(16.dp))

        // Day tabs
        ScrollableTabRow(
            selectedTabIndex = selectedDay - 1,
            containerColor = MaterialTheme.colorScheme.surface,
            edgePadding = 0.dp
        ) {
            (1..5).forEach { day ->
                Tab(
                    selected = selectedDay == day,
                    onClick = { viewModel.selectDay(day) },
                    text = {
                        Text(
                            Schedule.dayName(day),
                            fontWeight = if (selectedDay == day) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            val existingPeriods = schedules.map { it.period }.toSet()

            items(schedules) { schedule ->
                ScheduleItemCard(
                    schedule = schedule,
                    onEdit = {
                        editingSchedule = schedule
                        editingPeriod = schedule.period
                        showDialog = true
                    },
                    onDelete = {
                        viewModel.deleteSchedule(schedule.dayOfWeek, schedule.period)
                    }
                )
            }

            // Add button for unset periods
            val unsetPeriods = (1..7).filter { it !in existingPeriods }
            if (unsetPeriods.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    unsetPeriods.forEach { period ->
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            shape = RoundedCornerShape(12.dp),
                            onClick = {
                                editingSchedule = null
                                editingPeriod = period
                                showDialog = true
                            }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("${period}교시 추가")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        ScheduleEditDialog(
            period = editingPeriod,
            existing = editingSchedule,
            onDismiss = { showDialog = false },
            onSave = { startTime, subject, teacher ->
                viewModel.saveSchedule(selectedDay, editingPeriod, startTime, subject, teacher)
                showDialog = false
            }
        )
    }
}

@Composable
private fun ScheduleItemCard(schedule: Schedule, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${schedule.period}교시",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.width(50.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(schedule.subject, fontWeight = FontWeight.SemiBold)
                Text(
                    "${schedule.teacher} | ${schedule.startTime}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, "수정")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "삭제", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ScheduleEditDialog(
    period: Int,
    existing: Schedule?,
    onDismiss: () -> Unit,
    onSave: (startTime: String, subject: String, teacher: String) -> Unit
) {
    var startTime by remember { mutableStateOf(existing?.startTime ?: "") }
    var subject by remember { mutableStateOf(existing?.subject ?: "") }
    var teacher by remember { mutableStateOf(existing?.teacher ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${period}교시 설정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = startTime,
                    onValueChange = { startTime = it },
                    label = { Text("시작 시간 (예: 08:30)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("과목") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = teacher,
                    onValueChange = { teacher = it },
                    label = { Text("선생님") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(startTime, subject, teacher) },
                enabled = startTime.isNotBlank() && subject.isNotBlank() && teacher.isNotBlank()
            ) {
                Text("저장")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}
