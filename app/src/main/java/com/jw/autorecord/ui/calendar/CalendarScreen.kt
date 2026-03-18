package com.jw.autorecord.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.*

@Composable
fun CalendarScreen(viewModel: CalendarViewModel = viewModel()) {
    val currentMonth by viewModel.currentMonth.collectAsState()
    val recordedDates by viewModel.recordedDates.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val selectedDateFiles by viewModel.selectedDateFiles.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "녹음 달력",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(16.dp))

        // 월 이동 헤더
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.previousMonth() }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "이전 달")
            }

            val cal = Calendar.getInstance().apply {
                set(Calendar.YEAR, currentMonth.first)
                set(Calendar.MONTH, currentMonth.second - 1)
            }
            Text(
                "${currentMonth.first}년 ${currentMonth.second}월",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )

            IconButton(onClick = { viewModel.nextMonth() }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "다음 달")
            }
        }

        Spacer(Modifier.height(8.dp))

        // 요일 헤더
        Row(modifier = Modifier.fillMaxWidth()) {
            val dayNames = listOf("일", "월", "화", "수", "목", "금", "토")
            dayNames.forEach { day ->
                Text(
                    day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = when (day) {
                        "일" -> Color(0xFFE57373)
                        "토" -> Color(0xFF64B5F6)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // 달력 그리드
        val days = viewModel.getMonthDays(currentMonth.first, currentMonth.second)
        val weeks = days.chunked(7)

        weeks.forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .then(
                                if (day != null) {
                                    Modifier.clickable { viewModel.selectDate(day) }
                                } else Modifier
                            )
                            .then(
                                if (day == selectedDate) {
                                    Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (day != null) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val isToday = viewModel.isToday(day)
                                Text(
                                    "${day.third}",
                                    fontSize = 14.sp,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isToday) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                // 녹음 있으면 빨간 점
                                val dateStr = "%04d-%02d-%02d".format(day.first, day.second, day.third)
                                if (dateStr in recordedDates) {
                                    Spacer(Modifier.height(2.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 선택된 날짜의 녹음 파일 목록
        if (selectedDate != null) {
            val dateStr = "%04d-%02d-%02d".format(selectedDate!!.first, selectedDate!!.second, selectedDate!!.third)
            Text(
                "$dateStr 녹음 파일",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(8.dp))

            if (selectedDateFiles.isEmpty()) {
                Text(
                    "이 날짜에 녹음 파일이 없습니다",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(selectedDateFiles) { fileName ->
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.AudioFile,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    fileName,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
