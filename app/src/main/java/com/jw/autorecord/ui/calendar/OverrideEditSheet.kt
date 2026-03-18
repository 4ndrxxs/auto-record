package com.jw.autorecord.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jw.autorecord.data.Schedule
import com.jw.autorecord.data.ScheduleOverride

/**
 * 특정 날짜의 시간표 변경 바텀시트.
 *
 * 기본 시간표를 보여주고, 각 교시별로 변경/취소 가능.
 * 새 교시 추가도 지원.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverrideEditSheet(
    dateStr: String,
    baseSchedules: List<Schedule>,
    existingOverrides: List<ScheduleOverride>,
    onSave: (ScheduleOverride) -> Unit,
    onDelete: (String, Int) -> Unit,  // (date, period)
    onDismiss: () -> Unit
) {
    val overrideMap = remember(existingOverrides) {
        existingOverrides.associateBy { it.period }.toMutableMap()
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingPeriod by remember { mutableStateOf<Int?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // 헤더
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "$dateStr 시간표 변경",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "교시를 눌러서 변경하거나 취소할 수 있습니다",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalIconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, "교시 추가")
                }
            }

            Spacer(Modifier.height(16.dp))

            // 교시 목록
            val allPeriods = buildList {
                // 기본 시간표 교시
                addAll(baseSchedules.map { it.period })
                // ADD override 교시 (기본에 없는 것)
                val basePeriods = baseSchedules.map { it.period }.toSet()
                existingOverrides
                    .filter { it.type == ScheduleOverride.TYPE_ADD && it.period !in basePeriods }
                    .forEach { add(it.period) }
            }.distinct().sorted()

            if (allPeriods.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "이 요일에 등록된 시간표가 없습니다",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(allPeriods) { period ->
                        val base = baseSchedules.find { it.period == period }
                        val override = existingOverrides.find { it.period == period }

                        OverridePeriodCard(
                            period = period,
                            baseSchedule = base,
                            override = override,
                            onEdit = { editingPeriod = period },
                            onCancel = {
                                // 토글: 이미 CANCEL이면 삭제, 아니면 CANCEL 설정
                                if (override?.type == ScheduleOverride.TYPE_CANCEL) {
                                    onDelete(dateStr, period)
                                } else {
                                    onSave(
                                        ScheduleOverride(
                                            date = dateStr,
                                            period = period,
                                            type = ScheduleOverride.TYPE_CANCEL
                                        )
                                    )
                                }
                            },
                            onRestore = {
                                // override 삭제 → 기본 시간표로 복원
                                onDelete(dateStr, period)
                            }
                        )
                    }
                }
            }
        }
    }

    // 교시 편집 다이얼로그
    if (editingPeriod != null) {
        val period = editingPeriod!!
        val base = baseSchedules.find { it.period == period }
        val existing = existingOverrides.find { it.period == period }

        EditOverrideDialog(
            dateStr = dateStr,
            period = period,
            currentSubject = existing?.subject ?: base?.subject ?: "",
            currentTeacher = existing?.teacher ?: base?.teacher ?: "",
            currentStartTime = existing?.startTime ?: base?.startTime ?: "",
            isAdd = base == null,
            onSave = { override ->
                onSave(override)
                editingPeriod = null
            },
            onDismiss = { editingPeriod = null }
        )
    }

    // 새 교시 추가 다이얼로그
    if (showAddDialog) {
        AddPeriodDialog(
            dateStr = dateStr,
            existingPeriods = baseSchedules.map { it.period }.toSet() +
                    existingOverrides.map { it.period }.toSet(),
            onSave = { override ->
                onSave(override)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }
}

@Composable
private fun OverridePeriodCard(
    period: Int,
    baseSchedule: Schedule?,
    override: ScheduleOverride?,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
    onRestore: () -> Unit
) {
    val isCancelled = override?.type == ScheduleOverride.TYPE_CANCEL
    val isChanged = override != null && override.type != ScheduleOverride.TYPE_CANCEL
    val isAdded = override?.type == ScheduleOverride.TYPE_ADD

    val displaySubject = when {
        isCancelled -> baseSchedule?.subject ?: "취소됨"
        isChanged || isAdded -> override!!.subject
        else -> baseSchedule?.subject ?: ""
    }
    val displayTeacher = when {
        isCancelled -> baseSchedule?.teacher ?: ""
        isChanged || isAdded -> override!!.teacher
        else -> baseSchedule?.teacher ?: ""
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (!isCancelled) onEdit() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isCancelled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                isChanged -> Color(0xFFFFA726).copy(alpha = 0.1f)
                isAdded -> Color(0xFF4CAF50).copy(alpha = 0.1f)
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 교시 번호
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(
                    when {
                        isCancelled -> Color.Gray.copy(alpha = 0.3f)
                        isChanged -> Color(0xFFFFA726)
                        isAdded -> Color(0xFF4CAF50)
                        else -> MaterialTheme.colorScheme.primaryContainer
                    }
                ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$period",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = when {
                        isCancelled -> Color.Gray
                        isChanged || isAdded -> Color.White
                        else -> MaterialTheme.colorScheme.onPrimaryContainer
                    }
                )
            }

            Spacer(Modifier.width(12.dp))

            // 과목/선생님 정보
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        displaySubject,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = if (isCancelled) Color.Gray
                        else MaterialTheme.colorScheme.onSurface
                    )
                    if (isChanged) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "변경",
                            fontSize = 10.sp,
                            color = Color(0xFFFFA726),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFFFFA726).copy(alpha = 0.15f))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                    if (isAdded) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "추가",
                            fontSize = 10.sp,
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFF4CAF50).copy(alpha = 0.15f))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                    if (isCancelled) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "취소",
                            fontSize = 10.sp,
                            color = Color(0xFFE57373),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFFE57373).copy(alpha = 0.15f))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
                if (displayTeacher.isNotBlank()) {
                    Text(
                        displayTeacher,
                        fontSize = 13.sp,
                        color = if (isCancelled) Color.Gray
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // 변경 전 원본 표시
                if (isChanged && baseSchedule != null) {
                    Text(
                        "원래: ${baseSchedule.subject} (${baseSchedule.teacher})",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            // 액션 버튼들
            if (override != null) {
                // 원래대로 복원 버튼
                IconButton(onClick = onRestore, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Restore, "복원",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (!isCancelled && baseSchedule != null) {
                // 취소 버튼
                IconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Cancel, "취소",
                        tint = Color(0xFFE57373),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EditOverrideDialog(
    dateStr: String,
    period: Int,
    currentSubject: String,
    currentTeacher: String,
    currentStartTime: String,
    isAdd: Boolean,
    onSave: (ScheduleOverride) -> Unit,
    onDismiss: () -> Unit
) {
    var subject by remember { mutableStateOf(currentSubject) }
    var teacher by remember { mutableStateOf(currentTeacher) }
    var startTime by remember { mutableStateOf(currentStartTime) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${period}교시 ${if (isAdd) "추가" else "변경"}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                OutlinedTextField(
                    value = startTime,
                    onValueChange = { startTime = it },
                    label = { Text("시작 시간 (HH:mm)") },
                    placeholder = { Text("비워두면 기본 시간 사용") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (subject.isNotBlank()) {
                        onSave(
                            ScheduleOverride(
                                date = dateStr,
                                period = period,
                                type = if (isAdd) ScheduleOverride.TYPE_ADD else ScheduleOverride.TYPE_CHANGE,
                                subject = subject.trim(),
                                teacher = teacher.trim(),
                                startTime = startTime.trim()
                            )
                        )
                    }
                },
                enabled = subject.isNotBlank()
            ) { Text("저장") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}

@Composable
private fun AddPeriodDialog(
    dateStr: String,
    existingPeriods: Set<Int>,
    onSave: (ScheduleOverride) -> Unit,
    onDismiss: () -> Unit
) {
    var periodText by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var teacher by remember { mutableStateOf("") }
    var startTime by remember { mutableStateOf("") }

    val periodNum = periodText.toIntOrNull()
    val isValid = periodNum != null && periodNum in 1..10 &&
            periodNum !in existingPeriods && subject.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("교시 추가") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = periodText,
                    onValueChange = { periodText = it },
                    label = { Text("교시 번호") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    isError = periodNum != null && periodNum in existingPeriods,
                    supportingText = if (periodNum != null && periodNum in existingPeriods) {
                        { Text("이미 존재하는 교시입니다") }
                    } else null
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
                OutlinedTextField(
                    value = startTime,
                    onValueChange = { startTime = it },
                    label = { Text("시작 시간 (HH:mm)") },
                    placeholder = { Text("예: 14:00") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (isValid) {
                        onSave(
                            ScheduleOverride(
                                date = dateStr,
                                period = periodNum!!,
                                type = ScheduleOverride.TYPE_ADD,
                                subject = subject.trim(),
                                teacher = teacher.trim(),
                                startTime = startTime.trim()
                            )
                        )
                    }
                },
                enabled = isValid
            ) { Text("추가") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}
