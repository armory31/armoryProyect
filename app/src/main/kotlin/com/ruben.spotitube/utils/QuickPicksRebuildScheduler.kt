/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.utils

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object QuickPicksRebuildScheduler {
    fun schedule(context: Context) {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        var next4am = now.toLocalDate().atTime(4, 0).atZone(zone)
        if (!now.isBefore(next4am)) {
            next4am = next4am.plusDays(1)
        }

        val initialDelayMs = Duration.between(now, next4am).toMillis()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<QuickPicksRebuildWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "quick_picks_rebuild",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
