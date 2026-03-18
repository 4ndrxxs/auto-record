package com.jw.autorecord.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.jw.autorecord.service.ScheduleMonitorService

/**
 * 부팅 완료 시 감시 서비스 자동 시작.
 * AlarmManager 대신 상시 실행 서비스를 사용하므로
 * 부팅 후에도 서비스만 시작하면 됨.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.i("BootReceiver", "Boot completed, starting ScheduleMonitorService")
        ScheduleMonitorService.start(context)
    }
}
