package com.example.systemservice

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class RecordingWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "RecordingWorker"
        private const val WORK_NAME = "RecordingWorkManager"
        
        /**
         * Schedule periodic work to manage recordings
         * 
         * @param context The application context
         */
        fun scheduleWork(context: Context) {
            val recordingWorkRequest = PeriodicWorkRequestBuilder<RecordingWorker>(
                15, TimeUnit.MINUTES
            ).build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                recordingWorkRequest
            )
            
            Log.d(TAG, "Recording work scheduled")
        }
        
        /**
         * Cancel any scheduled work
         * 
         * @param context The application context
         */
        fun cancelWork(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Recording work cancelled")
        }
    }
    
    override suspend fun doWork(): Result {
        Log.d(TAG, "Executing recording work")
        
        try {
            // DO NOT try to start the service directly, as it requires MediaProjection permission
            // which can only be obtained through user interaction

            // Just manage storage
            val recordingsDir = FileEncryptionUtil.getRecordingsDirectory(applicationContext)
            FileEncryptionUtil.manageStorage(applicationContext, recordingsDir)
            
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Work failed: ${e.message}")
            return Result.retry()
        }
    }
} 