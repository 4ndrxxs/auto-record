package com.jw.autorecord.ui.recordings

import android.app.Application
import android.content.Intent
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import com.jw.autorecord.util.StoragePaths
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

data class RecordingDate(val dirName: String, val displayName: String, val fileCount: Int)
data class RecordingFile(val file: File, val name: String, val sizeFormatted: String)

data class PlayerState(
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val fileName: String = "",
    val filePath: String = "",
    val currentMs: Int = 0,
    val totalMs: Int = 0,
    val speed: Float = 1.0f
) {
    val progressPercent: Float get() = if (totalMs > 0) currentMs.toFloat() / totalMs else 0f

    fun currentFormatted(): String = formatMs(currentMs)
    fun totalFormatted(): String = formatMs(totalMs)

    private fun formatMs(ms: Int): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%02d:%02d".format(min, sec)
    }
}

class RecordingsViewModel(app: Application) : AndroidViewModel(app) {
    private val baseDir = StoragePaths.getBaseDir(app)

    private val _dates = MutableStateFlow<List<RecordingDate>>(emptyList())
    val dates: StateFlow<List<RecordingDate>> = _dates

    private val _selectedDate = MutableStateFlow<String?>(null)
    val selectedDate: StateFlow<String?> = _selectedDate

    private val _files = MutableStateFlow<List<RecordingFile>>(emptyList())
    val files: StateFlow<List<RecordingFile>> = _files

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState

    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val progressUpdater = object : Runnable {
        override fun run() {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) {
                    _playerState.value = _playerState.value.copy(currentMs = mp.currentPosition)
                }
            }
            handler.postDelayed(this, 200)
        }
    }

    init {
        loadDates()
    }

    fun loadDates() {
        val dirs = baseDir.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedByDescending { it.name }
            ?.map { dir ->
                val count = dir.listFiles()?.count { it.extension == "m4a" } ?: 0
                RecordingDate(dir.name, dir.name, count)
            }
            ?: emptyList()
        _dates.value = dirs
    }

    fun selectDate(date: String) {
        _selectedDate.value = date
        loadFiles(date)
    }

    fun goBack() {
        stopPlayback()
        _selectedDate.value = null
        _files.value = emptyList()
        loadDates()
    }

    private fun loadFiles(date: String) {
        val dir = File(baseDir, date)
        val fileList = dir.listFiles()
            ?.filter { it.extension == "m4a" }
            ?.sortedBy { it.name }
            ?.map { file ->
                val size = when {
                    file.length() < 1024 -> "${file.length()} B"
                    file.length() < 1024 * 1024 -> "%.1f KB".format(file.length() / 1024.0)
                    else -> "%.1f MB".format(file.length() / (1024.0 * 1024.0))
                }
                RecordingFile(file, file.nameWithoutExtension, size)
            }
            ?: emptyList()
        _files.value = fileList
    }

    // ═══ 뮤직 플레이어 컨트롤 ═══

    fun playFile(file: File) {
        stopPlayback()
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
                setOnCompletionListener {
                    _playerState.value = _playerState.value.copy(isPlaying = false, isPaused = false, currentMs = 0)
                    handler.removeCallbacks(progressUpdater)
                }
            }
            _playerState.value = PlayerState(
                isPlaying = true,
                isPaused = false,
                fileName = file.nameWithoutExtension,
                filePath = file.absolutePath,
                currentMs = 0,
                totalMs = mediaPlayer!!.duration,
                speed = _playerState.value.speed // 속도 유지
            )
            applySpeed(_playerState.value.speed)
            handler.post(progressUpdater)
        } catch (e: Exception) {
            _playerState.value = PlayerState()
        }
    }

    fun togglePlayPause() {
        val mp = mediaPlayer ?: return
        if (mp.isPlaying) {
            mp.pause()
            _playerState.value = _playerState.value.copy(isPlaying = true, isPaused = true)
        } else {
            mp.start()
            applySpeed(_playerState.value.speed)
            _playerState.value = _playerState.value.copy(isPlaying = true, isPaused = false)
        }
    }

    fun seekTo(positionMs: Int) {
        mediaPlayer?.seekTo(positionMs)
        _playerState.value = _playerState.value.copy(currentMs = positionMs)
    }

    fun seekForward(seconds: Int = 10) {
        val mp = mediaPlayer ?: return
        val newPos = (mp.currentPosition + seconds * 1000).coerceAtMost(mp.duration)
        mp.seekTo(newPos)
        _playerState.value = _playerState.value.copy(currentMs = newPos)
    }

    fun seekBackward(seconds: Int = 10) {
        val mp = mediaPlayer ?: return
        val newPos = (mp.currentPosition - seconds * 1000).coerceAtLeast(0)
        mp.seekTo(newPos)
        _playerState.value = _playerState.value.copy(currentMs = newPos)
    }

    fun setSpeed(speed: Float) {
        _playerState.value = _playerState.value.copy(speed = speed)
        applySpeed(speed)
    }

    private fun applySpeed(speed: Float) {
        try {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) {
                    mp.playbackParams = PlaybackParams().setSpeed(speed)
                }
            }
        } catch (_: Exception) {}
    }

    fun stopPlayback() {
        handler.removeCallbacks(progressUpdater)
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
        _playerState.value = PlayerState()
    }

    fun deleteFile(file: File) {
        if (_playerState.value.filePath == file.absolutePath) {
            stopPlayback()
        }
        file.delete()
        _selectedDate.value?.let { loadFiles(it) }
    }

    /** 녹음 파일 공유 */
    fun shareFile(file: File) {
        val app = getApplication<Application>()
        try {
            val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            app.startActivity(Intent.createChooser(intent, "녹음 파일 공유").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Exception) { }
    }

    /** 달력용: 녹음이 있는 날짜 목록 반환 */
    fun getRecordedDates(): Set<String> {
        return baseDir.listFiles()
            ?.filter { it.isDirectory && (it.listFiles()?.any { f -> f.extension == "m4a" } == true) }
            ?.map { it.name }
            ?.toSet()
            ?: emptySet()
    }

    override fun onCleared() {
        stopPlayback()
        super.onCleared()
    }
}
