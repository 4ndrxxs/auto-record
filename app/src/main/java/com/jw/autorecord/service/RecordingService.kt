package com.jw.autorecord.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaRecorder
import android.os.Environment
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.jw.autorecord.AutoRecordApp
import com.jw.autorecord.MainActivity
import com.jw.autorecord.R
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class RecordingService : Service() {

    private var mediaRecorder: MediaRecorder? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val subject = intent?.getStringExtra(EXTRA_SUBJECT) ?: "Unknown"
        val teacher = intent?.getStringExtra(EXTRA_TEACHER) ?: "Unknown"
        val period = intent?.getIntExtra(EXTRA_PERIOD, 0) ?: 0
        val durationMin = intent?.getIntExtra(EXTRA_DURATION_MIN, 50) ?: 50

        startForeground(NOTIFICATION_ID, createNotification(subject, period))
        startRecording(subject, teacher, period, durationMin)

        return START_NOT_STICKY
    }

    private fun createNotification(subject: String, period: Int): Notification {
        val tapIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, AutoRecordApp.RECORDING_CHANNEL_ID)
            .setContentTitle("🔴 녹음 중")
            .setContentText("${period}교시 - $subject")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun startRecording(subject: String, teacher: String, period: Int, durationMin: Int) {
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.KOREA)
        val dateDirFormat = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
        val now = Date()

        val dateStr = dateFormat.format(now)
        val dateDirStr = dateDirFormat.format(now)

        val baseDir = File(
            Environment.getExternalStorageDirectory(),
            "AutoRecord/$dateDirStr"
        )
        baseDir.mkdirs()

        val fileName = "${dateStr}_${period}교시_${subject}_${teacher}.m4a"
        val outputFile = File(baseDir, fileName)

        try {
            mediaRecorder = MediaRecorder(this).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
            Log.i(TAG, "Recording started: $fileName")

            recordingJob = scope.launch {
                delay(durationMin * 60 * 1000L)
                stopRecordingAndService()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            stopSelf()
        }
    }

    private fun stopRecordingAndService() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            Log.i(TAG, "Recording stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
        }
        mediaRecorder = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        recordingJob?.cancel()
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {}
        mediaRecorder = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val TAG = "RecordingService"
        const val NOTIFICATION_ID = 1001
        const val EXTRA_SUBJECT = "extra_subject"
        const val EXTRA_TEACHER = "extra_teacher"
        const val EXTRA_PERIOD = "extra_period"
        const val EXTRA_DURATION_MIN = "extra_duration_min"
    }
}
