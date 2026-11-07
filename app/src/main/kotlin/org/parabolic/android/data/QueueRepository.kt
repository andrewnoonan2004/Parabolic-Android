package org.parabolic.android.data

import android.content.Context
import android.os.Build
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.parabolic.android.work.DownloadWorker
import org.parabolic.android.work.DownloadGate
import org.parabolic.android.storage.StagingStore
import org.parabolic.core.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QueueRepository @Inject constructor(
    @param:ApplicationContext private val context: Context, private val dao: DownloadDao, private val json: Json,
    private val gate: DownloadGate, private val staging: StagingStore,
) {
    private val controls = Mutex()
    private val work get() = WorkManager.getInstance(context)
    val downloads = dao.observe()
    fun logs(id: String) = dao.logs(id)
    fun exports(id: String) = dao.observeExports(id)

    suspend fun enqueue(spec: DownloadSpec): String {
        require(Build.VERSION.SDK_INT >= 29 || spec.destination is Destination.Tree) { "Choose a destination folder on Android 8 or 9" }
        val id = UUID.randomUUID().toString()
        dao.insert(DownloadEntity(id = id, specification = json.encodeToString(spec)))
        schedule(id)
        return id
    }
    suspend fun pause(id: String) = controls.withLock { stop(id, QueueState.PAUSED) }
    suspend fun cancel(id: String) = controls.withLock { stop(id, QueueState.CANCELED) }
    private suspend fun stop(id: String, state: QueueState) {
        val item = dao.get(id) ?: return
        if (item.state == QueueState.COMPLETED) return
        dao.state(id, state)
        withContext(Dispatchers.IO) { work.cancelUniqueWork(DownloadWorker.name(id)).result.get() }
    }
    suspend fun resume(id: String) = controls.withLock {
        val item = dao.get(id) ?: return@withLock
        require(item.state in setOf(QueueState.PAUSED, QueueState.CANCELED, QueueState.FAILED))
        // Await cancellation before enqueuing the replacement; .part files are retained for yt-dlp --continue.
        withContext(Dispatchers.IO) { work.cancelUniqueWork(DownloadWorker.name(id)).result.get() }
        dao.state(id, QueueState.QUEUED)
        schedule(id, ExistingWorkPolicy.REPLACE)
    }
    /** Removing a queue/history item never deletes its published media. */
    suspend fun remove(id: String) = controls.withLock {
        stop(id, QueueState.CANCELED)
        gate.withJob(id) {
            dao.delete(id)
            withContext(Dispatchers.IO) { staging.clear(id) }
        }
    }
    suspend fun clearCompleted() = controls.withLock {
        for (item in dao.completed()) {
            gate.withJob(item.id) {
                dao.delete(item.id)
                withContext(Dispatchers.IO) { staging.clear(item.id) }
            }
        }
    }
    /** Call at shell startup: repairs the small non-transactional gap between Room and WorkManager. */
    suspend fun reconcile() {
        for (row in dao.unfinished()) {
            val workers = withContext(Dispatchers.IO) { work.getWorkInfosForUniqueWork(DownloadWorker.name(row.id)).get() }
            if (workers.none { !it.state.isFinished }) {
                if (workers.any { it.state == WorkInfo.State.CANCELLED }) dao.state(row.id, QueueState.CANCELED)
                else { dao.state(row.id, QueueState.QUEUED); schedule(row.id) }
            }
        }
    }
    private suspend fun schedule(id: String, policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.JOB_ID to id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag("downloads").build()
        withContext(Dispatchers.IO) { work.enqueueUniqueWork(DownloadWorker.name(id), policy, request).result.get() }
    }
}
