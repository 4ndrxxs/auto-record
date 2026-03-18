package com.jw.autorecord.util

import android.content.Context
import java.io.File

/**
 * 앱 전용 외부 저장소 경로 유틸리티.
 * context.getExternalFilesDir()를 사용하여 별도 권한 없이 접근 가능.
 * 경로: /storage/emulated/0/Android/data/com.jw.autorecord/files/AutoRecord/
 */
object StoragePaths {

    fun getBaseDir(context: Context): File {
        return File(context.getExternalFilesDir(null), "AutoRecord")
    }

    fun getDateDir(context: Context, dateStr: String): File {
        return File(getBaseDir(context), dateStr).also { it.mkdirs() }
    }

    /**
     * 파일명에서 특수문자 제거 (경로 구분자, 널 등)
     */
    fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[/\\\\:*?\"<>|\\x00]"), "_").trim()
    }
}
