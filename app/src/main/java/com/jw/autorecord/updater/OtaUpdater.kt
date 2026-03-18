package com.jw.autorecord.updater

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val changelog: String
)

object OtaUpdater {
    private const val TAG = "OtaUpdater"
    private const val VERSION_URL = "https://raw.githubusercontent.com/4ndrxxs/auto-record/master/version.json"

    private val client = OkHttpClient()

    // 현재 다운로드 ID 추적 (자동 설치에 사용)
    private var currentDownloadId: Long = -1L
    private var pendingInstallFileName: String? = null
    private var downloadReceiver: BroadcastReceiver? = null

    suspend fun checkForUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(getVersionUrl(context)).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) return@withContext null

            val json = JSONObject(response.body?.string() ?: return@withContext null)
            val remoteVersionCode = json.getInt("versionCode")
            val currentVersionCode = context.packageManager
                .getPackageInfo(context.packageName, 0).longVersionCode.toInt()

            if (remoteVersionCode > currentVersionCode) {
                UpdateInfo(
                    versionCode = remoteVersionCode,
                    versionName = json.getString("versionName"),
                    apkUrl = json.getString("apkUrl"),
                    changelog = json.optString("changelog", "")
                )
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Update check failed", e)
            null
        }
    }

    /**
     * APK 다운로드 + 완료 시 자동 설치.
     * DownloadManager로 다운로드하고, BroadcastReceiver로 완료를 감지해 installApk() 호출.
     */
    fun downloadAndInstall(context: Context, updateInfo: UpdateInfo) {
        val fileName = "AutoRecord-${updateInfo.versionName}.apk"

        // 기존 파일 삭제 (재다운로드 대비)
        val existingFile = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            fileName
        )
        if (existingFile.exists()) existingFile.delete()

        val downloadManager = context.getSystemService(DownloadManager::class.java)

        val request = DownloadManager.Request(Uri.parse(updateInfo.apkUrl))
            .setTitle("자동녹음 업데이트")
            .setDescription("v${updateInfo.versionName} 다운로드 중...")
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setMimeType("application/vnd.android.package-archive")

        currentDownloadId = downloadManager.enqueue(request)
        pendingInstallFileName = fileName
        Log.i(TAG, "Download started: id=$currentDownloadId, file=$fileName")

        // ★ 다운로드 완료 리시버 등록
        registerDownloadReceiver(context)
    }

    /**
     * 다운로드 완료 브로드캐스트를 감지해 자동으로 APK 설치 화면을 띄움.
     */
    private fun registerDownloadReceiver(context: Context) {
        // 이전 리시버가 있으면 해제
        unregisterDownloadReceiver(context)

        downloadReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (downloadId != currentDownloadId) return

                Log.i(TAG, "Download completed: id=$downloadId")

                val fileName = pendingInstallFileName ?: return
                val file = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    fileName
                )

                if (file.exists() && file.length() > 0) {
                    Log.i(TAG, "Installing APK: ${file.absolutePath} (${file.length()} bytes)")
                    installApk(ctx, file)
                } else {
                    Log.e(TAG, "Downloaded file not found or empty: ${file.absolutePath}")
                }

                // 정리
                unregisterDownloadReceiver(ctx)
                currentDownloadId = -1L
                pendingInstallFileName = null
            }
        }

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(downloadReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(downloadReceiver, filter)
        }
    }

    private fun unregisterDownloadReceiver(context: Context) {
        downloadReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (_: Exception) { }
        }
        downloadReceiver = null
    }

    /**
     * APK 설치 화면 실행.
     * FileProvider URI를 사용해 Android 7+ 호환.
     */
    fun installApk(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch installer", e)
            // 폴백: 직접 파일 URI로 시도
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(
                        Uri.fromFile(file),
                        "application/vnd.android.package-archive"
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                Log.e(TAG, "Fallback install also failed", e2)
            }
        }
    }

    private fun getVersionUrl(context: Context): String {
        val prefs = context.getSharedPreferences("prefs", 0)
        return prefs.getString("ota_url", VERSION_URL) ?: VERSION_URL
    }

    fun setVersionUrl(context: Context, url: String) {
        context.getSharedPreferences("prefs", 0)
            .edit().putString("ota_url", url).apply()
    }
}
