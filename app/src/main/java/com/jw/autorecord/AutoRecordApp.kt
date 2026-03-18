package com.jw.autorecord

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.jw.autorecord.data.AppDatabase

class AutoRecordApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        // 녹음 진행 중 (조용한 ongoing 알림)
        val recordingChannel = NotificationChannel(
            RECORDING_CHANNEL_ID,
            getString(R.string.recording_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.recording_channel_desc)
        }
        manager.createNotificationChannel(recordingChannel)

        // 녹음 시작 알림 (푸시 알림)
        val alertChannel = NotificationChannel(
            ALERT_CHANNEL_ID,
            getString(R.string.alert_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.alert_channel_desc)
        }
        manager.createNotificationChannel(alertChannel)

        // 상시 감시 서비스 (최소 중요도 — 소리/진동 없음)
        val monitorChannel = NotificationChannel(
            MONITOR_CHANNEL_ID,
            getString(R.string.monitor_channel_name),
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = getString(R.string.monitor_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(monitorChannel)
    }

    companion object {
        const val RECORDING_CHANNEL_ID = "recording_channel"
        const val ALERT_CHANNEL_ID = "alert_channel"
        const val MONITOR_CHANNEL_ID = "monitor_channel"
    }
}
