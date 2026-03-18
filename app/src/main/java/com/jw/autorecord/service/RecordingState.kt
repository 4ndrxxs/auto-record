package com.jw.autorecord.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 녹음 상태를 앱 전체에서 관찰할 수 있는 싱글톤.
 * RecordingService가 매초 업데이트하고, HomeScreen이 실시간으로 관찰한다.
 */
object RecordingState {

    data class State(
        val isRecording: Boolean = false,
        val period: Int = -1,
        val subject: String = "",
        val teacher: String = "",
        val startTimeMillis: Long = 0L,
        val durationMin: Int = 50,
        val elapsedSeconds: Long = 0L,
        val filePath: String = "",
        val fileSizeBytes: Long = 0L,
        val amplitudeDb: Int = 0          // 마이크 입력 세기 (0~32767)
    ) {
        val totalSeconds: Long get() = durationMin * 60L
        val remainingSeconds: Long get() = (totalSeconds - elapsedSeconds).coerceAtLeast(0)
        val progressPercent: Float get() = if (totalSeconds > 0) {
            (elapsedSeconds.toFloat() / totalSeconds).coerceIn(0f, 1f)
        } else 0f

        fun elapsedFormatted(): String {
            val min = elapsedSeconds / 60
            val sec = elapsedSeconds % 60
            return "%02d:%02d".format(min, sec)
        }

        fun remainingFormatted(): String {
            val min = remainingSeconds / 60
            val sec = remainingSeconds % 60
            return "%02d:%02d".format(min, sec)
        }

        fun fileSizeFormatted(): String {
            return when {
                fileSizeBytes < 1024 -> "${fileSizeBytes} B"
                fileSizeBytes < 1024 * 1024 -> "%.1f KB".format(fileSizeBytes / 1024.0)
                else -> "%.1f MB".format(fileSizeBytes / (1024.0 * 1024.0))
            }
        }
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun update(block: State.() -> State) {
        _state.value = _state.value.block()
    }

    fun reset() {
        _state.value = State()
    }
}
