package com.jw.autorecord.ui.recordings

import android.app.Application
import android.media.MediaPlayer
import androidx.lifecycle.AndroidViewModel
import com.jw.autorecord.util.StoragePaths
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

data class RecordingDate(val dirName: String, val displayName: String)
data class RecordingFile(val file: File, val name: String)

class RecordingsViewModel(app: Application) : AndroidViewModel(app) {
    private val baseDir = StoragePaths.getBaseDir(app)

    private val _dates = MutableStateFlow<List<RecordingDate>>(emptyList())
    val dates: StateFlow<List<RecordingDate>> = _dates

    private val _selectedDate = MutableStateFlow<String?>(null)
    val selectedDate: StateFlow<String?> = _selectedDate

    private val _files = MutableStateFlow<List<RecordingFile>>(emptyList())
    val files: StateFlow<List<RecordingFile>> = _files

    private val _playingFile = MutableStateFlow<String?>(null)
    val playingFile: StateFlow<String?> = _playingFile

    private var mediaPlayer: MediaPlayer? = null

    init {
        loadDates()
    }

    fun loadDates() {
        val dirs = baseDir.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedByDescending { it.name }
            ?.map { RecordingDate(it.name, it.name) }
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
            ?.map { RecordingFile(it, it.nameWithoutExtension) }
            ?: emptyList()
        _files.value = fileList
    }

    fun togglePlayback(file: File) {
        if (_playingFile.value == file.name) {
            stopPlayback()
        } else {
            stopPlayback()
            try {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    prepare()
                    start()
                    setOnCompletionListener {
                        _playingFile.value = null
                    }
                }
                _playingFile.value = file.name
            } catch (_: Exception) {}
        }
    }

    fun stopPlayback() {
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
        _playingFile.value = null
    }

    fun deleteFile(file: File) {
        file.delete()
        _selectedDate.value?.let { loadFiles(it) }
    }

    override fun onCleared() {
        stopPlayback()
        super.onCleared()
    }
}
