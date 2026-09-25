package com.ruben.spotitube.sync.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ruben.spotitube.sync.SyncManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncManager: SyncManager
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Timber.d("SyncWorker: Starting background sync...")
        return try {
            syncManager.syncLibrary()
            Timber.d("SyncWorker: Background sync completed successfully")
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "SyncWorker: Background sync failed")
            Result.retry()
        }
    }
}
