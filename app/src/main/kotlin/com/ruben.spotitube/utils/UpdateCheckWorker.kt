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
import com.ruben.spotitube.constants.EnableUpdateNotificationKey
import com.ruben.spotitube.constants.UpdateChannel
import com.ruben.spotitube.constants.UpdateChannelKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val dataStore = applicationContext.dataStore

            val isEnabled = dataStore.data.map { it[EnableUpdateNotificationKey] ?: false }.first()
            if (!isEnabled) return Result.success()

            val updateChannel = dataStore.data.map {
                it[UpdateChannelKey]?.let { value ->
                    try { UpdateChannel.valueOf(value) } catch (e: Exception) { UpdateChannel.STABLE }
                } ?: UpdateChannel.STABLE
            }.first()

            if (updateChannel == UpdateChannel.NIGHTLY) return Result.success()

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
