package com.jw.autorecord.ui.settings

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jw.autorecord.AutoRecordApp
import com.jw.autorecord.data.Schedule
import com.jw.autorecord.data.ScheduleOverride
import com.jw.autorecord.util.StoragePaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("prefs", 0)
    private val db = (app as AutoRecordApp).database

    // ── 녹음 설정 ──
    private val _recordingDuration = MutableStateFlow(prefs.getInt("recording_duration", 50))
    val recordingDuration: StateFlow<Int> = _recordingDuration

    private val _audioBitrate = MutableStateFlow(prefs.getInt("audio_bitrate", 128000))
    val audioBitrate: StateFlow<Int> = _audioBitrate

    private val _audioSampleRate = MutableStateFlow(prefs.getInt("audio_sample_rate", 44100))
    val audioSampleRate: StateFlow<Int> = _audioSampleRate

    // ── 저장소 정보 ──
    private val _storageInfo = MutableStateFlow(StorageInfo())
    val storageInfo: StateFlow<StorageInfo> = _storageInfo.asStateFlow()

    // ── 백업/복원 상태 ──
    private val _backupResult = MutableStateFlow<String?>(null)
    val backupResult: StateFlow<String?> = _backupResult.asStateFlow()

    // ── 앱 버전 ──
    val appVersion: String = try {
        app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "unknown"
    } catch (_: Exception) { "unknown" }

    val appVersionCode: Int = try {
        app.packageManager.getPackageInfo(app.packageName, 0).longVersionCode.toInt()
    } catch (_: Exception) { 0 }

    init {
        loadStorageInfo()
    }

    // ═══════════════════════════════════════
    // 녹음 설정
    // ═══════════════════════════════════════

    fun setRecordingDuration(minutes: Int) {
        _recordingDuration.value = minutes
        prefs.edit().putInt("recording_duration", minutes).apply()
    }

    fun setAudioBitrate(bitrate: Int) {
        _audioBitrate.value = bitrate
        prefs.edit().putInt("audio_bitrate", bitrate).apply()
    }

    fun setAudioSampleRate(rate: Int) {
        _audioSampleRate.value = rate
        prefs.edit().putInt("audio_sample_rate", rate).apply()
    }

    // ═══════════════════════════════════════
    // 저장소 정보
    // ═══════════════════════════════════════

    fun loadStorageInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            val baseDir = StoragePaths.getBaseDir(getApplication())
            val totalSize = calculateDirSize(baseDir)
            val fileCount = countFiles(baseDir)
            val folderCount = baseDir.listFiles()?.count { it.isDirectory } ?: 0

            _storageInfo.value = StorageInfo(
                basePath = baseDir.absolutePath,
                totalSizeBytes = totalSize,
                fileCount = fileCount,
                folderCount = folderCount
            )
        }
    }

    private fun calculateDirSize(dir: File): Long {
        if (!dir.exists()) return 0
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    private fun countFiles(dir: File): Int {
        if (!dir.exists()) return 0
        return dir.walkTopDown().filter { it.isFile && it.extension == "m4a" }.count()
    }

    // ═══════════════════════════════════════
    // 시간표 백업 (JSON → URI)
    // ═══════════════════════════════════════

    fun backupSchedules(uri: Uri) {
        viewModelScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    val schedules = db.scheduleDao().getAllSchedulesOnce()
                    val overrides = db.scheduleOverrideDao().getAllOverridesOnce()
                    buildBackupJson(schedules, overrides)
                }

                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                        it.write(json.toString(2).toByteArray())
                    }
                }

                _backupResult.value = "백업 완료! (시간표 ${json.getJSONArray("schedules").length()}개, 변경 ${json.getJSONArray("overrides").length()}개)"
            } catch (e: Exception) {
                Log.e("Settings", "Backup failed", e)
                _backupResult.value = "백업 실패: ${e.message}"
            }
        }
    }

    fun restoreSchedules(uri: Uri) {
        viewModelScope.launch {
            try {
                val jsonStr = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use {
                        it.bufferedReader().readText()
                    } ?: throw Exception("파일을 읽을 수 없습니다")
                }

                val json = JSONObject(jsonStr)
                val schedulesArray = json.getJSONArray("schedules")
                val overridesArray = json.optJSONArray("overrides") ?: JSONArray()

                withContext(Dispatchers.IO) {
                    // 시간표 복원
                    for (i in 0 until schedulesArray.length()) {
                        val obj = schedulesArray.getJSONObject(i)
                        db.scheduleDao().upsert(
                            Schedule(
                                dayOfWeek = obj.getInt("dayOfWeek"),
                                period = obj.getInt("period"),
                                startTime = obj.getString("startTime"),
                                subject = obj.getString("subject"),
                                teacher = obj.getString("teacher")
                            )
                        )
                    }

                    // Override 복원
                    for (i in 0 until overridesArray.length()) {
                        val obj = overridesArray.getJSONObject(i)
                        db.scheduleOverrideDao().upsert(
                            ScheduleOverride(
                                date = obj.getString("date"),
                                period = obj.getInt("period"),
                                type = obj.getString("type"),
                                subject = obj.optString("subject", ""),
                                teacher = obj.optString("teacher", ""),
                                startTime = obj.optString("startTime", "")
                            )
                        )
                    }
                }

                _backupResult.value = "복원 완료! (시간표 ${schedulesArray.length()}개, 변경 ${overridesArray.length()}개)"
            } catch (e: Exception) {
                Log.e("Settings", "Restore failed", e)
                _backupResult.value = "복원 실패: ${e.message}"
            }
        }
    }

    private fun buildBackupJson(schedules: List<Schedule>, overrides: List<ScheduleOverride>): JSONObject {
        val root = JSONObject()
        root.put("appVersion", appVersion)
        root.put("backupDate", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.KOREA).format(java.util.Date()))

        val schedulesArray = JSONArray()
        for (s in schedules) {
            schedulesArray.put(JSONObject().apply {
                put("dayOfWeek", s.dayOfWeek)
                put("period", s.period)
                put("startTime", s.startTime)
                put("subject", s.subject)
                put("teacher", s.teacher)
            })
        }
        root.put("schedules", schedulesArray)

        val overridesArray = JSONArray()
        for (o in overrides) {
            overridesArray.put(JSONObject().apply {
                put("date", o.date)
                put("period", o.period)
                put("type", o.type)
                put("subject", o.subject)
                put("teacher", o.teacher)
                put("startTime", o.startTime)
            })
        }
        root.put("overrides", overridesArray)

        return root
    }

    fun clearBackupResult() {
        _backupResult.value = null
    }

    // ═══════════════════════════════════════
    // 전체 데이터 삭제
    // ═══════════════════════════════════════

    fun deleteAllRecordings() {
        viewModelScope.launch(Dispatchers.IO) {
            val baseDir = StoragePaths.getBaseDir(getApplication())
            baseDir.deleteRecursively()
            baseDir.mkdirs()
            loadStorageInfo()
        }
    }
}

data class StorageInfo(
    val basePath: String = "",
    val totalSizeBytes: Long = 0,
    val fileCount: Int = 0,
    val folderCount: Int = 0
) {
    fun totalSizeFormatted(): String {
        return when {
            totalSizeBytes < 1024 -> "${totalSizeBytes} B"
            totalSizeBytes < 1024 * 1024 -> "%.1f KB".format(totalSizeBytes / 1024.0)
            totalSizeBytes < 1024 * 1024 * 1024 -> "%.1f MB".format(totalSizeBytes / (1024.0 * 1024))
            else -> "%.2f GB".format(totalSizeBytes / (1024.0 * 1024 * 1024))
        }
    }
}
