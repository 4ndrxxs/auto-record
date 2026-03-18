package com.jw.autorecord.updater

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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

    // GitHub raw URL for version.json - user should set their own repo
    private const val VERSION_URL = "https://raw.githubusercontent.com/YOUR_GITHUB/auto-record/main/version.json"

    private val client = OkHttpClient()

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

    fun downloadAndInstall(context: Context, updateInfo: UpdateInfo) {
        val downloadManager = context.getSystemService(DownloadManager::class.java)

        val request = DownloadManager.Request(Uri.parse(updateInfo.apkUrl))
            .setTitle("자동녹음 업데이트")
            .setDescription("v${updateInfo.versionName} 다운로드 중")
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "AutoRecord-${updateInfo.versionName}.apk"
            )
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

        downloadManager.enqueue(request)
    }

    fun installApk(context: Context, file: File) {
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
