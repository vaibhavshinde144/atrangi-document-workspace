package com.atrangi.documentworkspace

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class UpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {
    override fun doWork(): Result = try {
        val info = UpdateManager.fetchRemoteUpdate() ?: return Result.retry()
        UpdateManager.notifyIfNeeded(applicationContext, info)
        Result.success()
    } catch (_: Exception) {
        Result.retry()
    }
}
