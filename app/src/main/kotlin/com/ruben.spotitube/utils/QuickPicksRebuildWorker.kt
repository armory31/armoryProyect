/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.utils

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ruben.spotitube.db.MusicDatabase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class QuickPicksRebuildWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val database: MusicDatabase,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val success = runCatching {
            val built = QuickPicksPoolBuilder.build(applicationContext, database)
            if (built.isNotEmpty()) {
                QuickPicksPoolStore.save(applicationContext, built)
                true
            } else {
                false
            }
        }.getOrDefault(false)

        return if (success) {
            Result.success()
        } else {
            Result.retry()
        }
    }
}
