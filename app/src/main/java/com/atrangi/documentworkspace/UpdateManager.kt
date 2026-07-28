package com.atrangi.documentworkspace

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

internal data class RemoteUpdateInfo(
    val webVersion: String,
    val nativeVersionCode: Int,
    val nativeVersionName: String,
    val mandatory: Boolean,
    val title: String,
    val notes: String
)

internal object UpdateManager {
    const val VERSION_URL = "https://vaibhavshinde144.github.io/atrangi-document-workspace/version.json"
    const val EXTRA_WEB_VERSION = "atrangi.extra.WEB_VERSION"
    const val EXTRA_APPLY_WEB_UPDATE = "atrangi.extra.APPLY_WEB_UPDATE"

    private const val PREFS = "atrangi_updates"
    private const val KEY_APPLIED_WEB_VERSION = "applied_web_version"
    private const val CHANNEL_ID = "atrangi_updates"
    private const val NOTIFICATION_ID = 71012
    private const val PERIODIC_WORK = "atrangi_periodic_update_check"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Atrangi updates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "New Atrangi Document Workspace versions and update actions"
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val periodic = PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodic
        )
    }

    fun checkNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<UpdateWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    fun appliedWebVersion(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_APPLIED_WEB_VERSION, BuildConfig.VERSION_NAME)
            ?: BuildConfig.VERSION_NAME

    fun markWebVersionApplied(context: Context, version: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_APPLIED_WEB_VERSION, version)
            .apply()
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    fun isNewer(candidate: String, current: String): Boolean {
        fun pieces(v: String): List<Int> = v
            .trim()
            .removePrefix("v")
            .split('.', '-', '_')
            .map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
        val a = pieces(candidate)
        val b = pieces(current)
        val max = maxOf(a.size, b.size)
        for (i in 0 until max) {
            val av = a.getOrElse(i) { 0 }
            val bv = b.getOrElse(i) { 0 }
            if (av != bv) return av > bv
        }
        return false
    }

    fun fetchRemoteUpdate(): RemoteUpdateInfo? {
        val connection = (URL(VERSION_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-cache")
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            RemoteUpdateInfo(
                webVersion = json.optString("webVersion", BuildConfig.VERSION_NAME),
                nativeVersionCode = json.optInt("nativeVersionCode", BuildConfig.VERSION_CODE),
                nativeVersionName = json.optString("nativeVersionName", BuildConfig.VERSION_NAME),
                mandatory = json.optBoolean("mandatory", false),
                title = json.optString("title", "Atrangi update available"),
                notes = json.optString("notes", "A newer Atrangi workspace is ready.")
            )
        } finally {
            connection.disconnect()
        }
    }

    fun notifyIfNeeded(context: Context, info: RemoteUpdateInfo) {
        val currentWeb = appliedWebVersion(context)
        val hasWebUpdate = isNewer(info.webVersion, currentWeb)
        val hasNativeUpdate = info.nativeVersionCode > BuildConfig.VERSION_CODE
        if (!hasWebUpdate && !hasNativeUpdate) return

        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val intent = Intent(context, UpdateActivity::class.java).apply {
            putExtra(EXTRA_WEB_VERSION, info.webVersion)
            putExtra("native_update", hasNativeUpdate)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pending = PendingIntent.getActivity(
            context,
            71012,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val detail = if (hasNativeUpdate) {
            "A native Android update is available. Atrangi will apply the workspace update now; Android may still require its installer flow for a native package update."
        } else {
            info.notes
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(info.title)
            .setContentText("Version ${info.webVersion} is ready")
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(!info.mandatory)
            .setOngoing(info.mandatory)
            .setContentIntent(pending)
            .addAction(0, "Update", pending)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
