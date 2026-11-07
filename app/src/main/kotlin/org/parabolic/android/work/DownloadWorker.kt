package org.parabolic.android.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.parabolic.android.R
import org.parabolic.android.data.*
import org.parabolic.android.storage.OutputPublisher
import org.parabolic.android.storage.StagingStore
import org.parabolic.core.*

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context, @Assisted parameters: WorkerParameters,
    private val repository: YtDlpRepository, private val dao: DownloadDao,
    private val json: Json, private val gate: DownloadGate,
    private val staging: StagingStore, private val publisher: OutputPublisher,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val jobId = inputData.getString(JOB_ID) ?: return Result.failure()
        val row = dao.get(jobId) ?: return Result.failure()
        if (row.state in setOf(QueueState.PAUSED, QueueState.CANCELED, QueueState.COMPLETED)) return Result.success()
        if (dao.claim(jobId, id.toString()) == 0) return Result.success()
        try {
            setForeground(notification(applicationContext.getString(R.string.download_waiting)))
            return gate.withJob(jobId) { gate.withSlot {
                val current = dao.get(jobId) ?: return@withSlot Result.failure()
                if (current.attemptId != id.toString() || current.state in setOf(QueueState.PAUSED, QueueState.CANCELED, QueueState.COMPLETED)) return@withSlot Result.success()
                val spec = json.decodeFromString<DownloadSpec>(current.specification)
                if (!current.downloaded) {
                    dao.workerState(jobId, id.toString(), QueueState.DOWNLOADING)
                    var lastProgressUpdate = 0L
                    repository.download(jobId, spec).collect { event ->
                        when (event) {
                            DownloadEvent.Started -> setForeground(notification(applicationContext.getString(R.string.download_processing)))
                            is DownloadEvent.Log -> dao.appendLog(jobId, event.line)
                            is DownloadEvent.Progress -> {
                                val now = SystemClock.elapsedRealtime()
                                if (now - lastProgressUpdate >= 1000) {
                                    lastProgressUpdate = now
                                    val p = event.value
                                    dao.progress(jobId, p.percent, p.bytesPerSecond, p.etaSeconds)
                                    setForeground(notification(p.percent?.let { "${it.toInt()}%" } ?: applicationContext.getString(R.string.download_processing), p.percent))
                                }
                            }
                            is DownloadEvent.Completed -> dao.downloaded(jobId)
                        }
                    }
                }
                currentCoroutineContext().ensureActive()
                dao.workerState(jobId, id.toString(), QueueState.EXPORTING)
                setForeground(notification(applicationContext.getString(R.string.download_exporting)))
                val files = withContext(Dispatchers.IO) { staging.completedFiles(jobId) }
                check(files.isNotEmpty()) { "Staged files are missing; remove this job and add it again" }
                publisher.publish(jobId, files, spec.destination)
                withContext(NonCancellable) {
                    if (dao.workerState(jobId, id.toString(), QueueState.COMPLETED) > 0) {
                        dao.progress(jobId, 100f, null, null)
                        withContext(Dispatchers.IO) { staging.clear(jobId) }
                    }
                }
                Result.success()
            } }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                // Explicit pause/cancel has already persisted its state. OS interruption remains resumable.
                val canceledByUser = withContext(Dispatchers.IO) {
                    WorkManager.getInstance(applicationContext).getWorkInfoById(id).get()?.state == WorkInfo.State.CANCELLED
                }
                dao.workerState(jobId, id.toString(), if (canceledByUser) QueueState.CANCELED else QueueState.QUEUED)
            }
            throw cancelled
        } catch (error: Exception) {
            dao.workerState(jobId, id.toString(), QueueState.FAILED, LogSanitizer.clean(error.message ?: "Download failed"))
            return Result.failure()
        }
    }

    private fun notification(message: String, percent: Float? = null): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, applicationContext.getString(R.string.download_channel), NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(applicationContext.getString(R.string.download_notification))
            .setContentText(message).setOnlyAlertOnce(true).setOngoing(true)
            .setProgress(100, percent?.toInt() ?: 0, percent == null)
            .addAction(0, applicationContext.getString(R.string.cancel), WorkManager.getInstance(applicationContext).createCancelPendingIntent(id))
            .build()
        val notificationId = id.hashCode() and Int.MAX_VALUE
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(notificationId, notification)
    }
    companion object {
        const val JOB_ID = "download_id"
        private const val CHANNEL = "downloads"
        fun name(id: String) = "download:$id"
    }
}
