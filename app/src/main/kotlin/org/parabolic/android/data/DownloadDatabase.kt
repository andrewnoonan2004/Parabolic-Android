package org.parabolic.android.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

enum class QueueState { QUEUED, DOWNLOADING, EXPORTING, PAUSED, COMPLETED, FAILED, CANCELED }

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String,
    val specification: String,
    val state: QueueState = QueueState.QUEUED,
    val createdAt: Long = System.currentTimeMillis(),
    val percent: Float? = null,
    val speed: Double? = null,
    val eta: Long? = null,
    val error: String? = null,
    val downloaded: Boolean = false,
    val attemptId: String? = null,
)
@Entity(tableName = "download_logs", indices = [Index("downloadId")], foreignKeys = [ForeignKey(
    entity = DownloadEntity::class, parentColumns = ["id"], childColumns = ["downloadId"], onDelete = ForeignKey.CASCADE,
)])
data class DownloadLog(@PrimaryKey(autoGenerate = true) val sequence: Long = 0, val downloadId: String, val line: String)
@Entity(tableName = "exports", primaryKeys = ["downloadId", "fileName"], indices = [Index("downloadId")], foreignKeys = [ForeignKey(
    entity = DownloadEntity::class, parentColumns = ["id"], childColumns = ["downloadId"], onDelete = ForeignKey.CASCADE,
)])
data class ExportEntity(val downloadId: String, val fileName: String, val uri: String, val completed: Boolean = false)

@Dao
abstract class DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC") abstract fun observe(): Flow<List<DownloadEntity>>
    @Query("SELECT * FROM downloads WHERE id = :id") abstract suspend fun get(id: String): DownloadEntity?
    @Query("SELECT * FROM downloads WHERE state IN ('QUEUED', 'DOWNLOADING', 'EXPORTING')") abstract suspend fun unfinished(): List<DownloadEntity>
    @Insert abstract suspend fun insert(item: DownloadEntity)
    @Query("DELETE FROM downloads WHERE id = :id") abstract suspend fun delete(id: String)
    @Query("UPDATE downloads SET state = :state, error = :error WHERE id = :id")
    abstract suspend fun state(id: String, state: QueueState, error: String? = null)
    @Query("UPDATE downloads SET attemptId = :attemptId WHERE id = :id AND state NOT IN ('PAUSED', 'CANCELED', 'COMPLETED')")
    abstract suspend fun claim(id: String, attemptId: String): Int
    @Query("UPDATE downloads SET state = :state, error = :error WHERE id = :id AND attemptId = :attemptId AND state NOT IN ('PAUSED', 'CANCELED')")
    abstract suspend fun workerState(id: String, attemptId: String, state: QueueState, error: String? = null): Int
    @Query("UPDATE downloads SET percent = :percent, speed = :speed, eta = :eta WHERE id = :id")
    abstract suspend fun progress(id: String, percent: Float?, speed: Double?, eta: Long?)
    @Query("UPDATE downloads SET downloaded = 1 WHERE id = :id") abstract suspend fun downloaded(id: String)
    @Query("SELECT * FROM download_logs WHERE downloadId = :id ORDER BY sequence") abstract fun logs(id: String): Flow<List<DownloadLog>>
    @Insert protected abstract suspend fun insertLog(log: DownloadLog)
    @Query("DELETE FROM download_logs WHERE downloadId = :id AND sequence NOT IN (SELECT sequence FROM download_logs WHERE downloadId = :id ORDER BY sequence DESC LIMIT 1000)")
    protected abstract suspend fun trimLogs(id: String)
    @Transaction open suspend fun appendLog(id: String, line: String) { insertLog(DownloadLog(downloadId = id, line = line)); trimLogs(id) }
    @Query("SELECT * FROM exports WHERE downloadId = :id AND fileName = :name") abstract suspend fun export(id: String, name: String): ExportEntity?
    @Query("SELECT * FROM exports WHERE downloadId = :id AND completed = 1") abstract suspend fun exports(id: String): List<ExportEntity>
    @Query("SELECT * FROM exports WHERE downloadId = :id AND completed = 1 ORDER BY fileName") abstract fun observeExports(id: String): Flow<List<ExportEntity>>
    @Query("SELECT * FROM downloads WHERE state = 'COMPLETED'") abstract suspend fun completed(): List<DownloadEntity>
    @Upsert abstract suspend fun saveExport(export: ExportEntity)
    @Query("DELETE FROM exports WHERE downloadId = :id AND fileName = :name") abstract suspend fun removeExport(id: String, name: String)
}
@Database(entities = [DownloadEntity::class, DownloadLog::class, ExportEntity::class], version = 1, exportSchema = true)
abstract class DownloadDatabase : RoomDatabase() { abstract fun downloads(): DownloadDao }
