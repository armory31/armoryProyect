/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ruben.spotitube.utils

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ruben.spotitube.BuildConfig

class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            Updater.getLatestVersionName(forceRefresh = true)
                .onSuccess { latestVersion ->
                    if (Updater.isNewerVersion(latestVersion, BuildConfig.VERSION_NAME)) {
                        UpdateNotificationManager.notifyIfNewVersion(applicationContext, latestVersion)
                    }
                }
                .onFailure {
                    return Result.retry()
                }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
