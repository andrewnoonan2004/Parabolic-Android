package org.parabolic.android.engine

import android.content.Context
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.parabolic.android.storage.CookieStore
import org.parabolic.android.storage.StagingStore
import org.parabolic.core.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidYtDlpRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val staging: StagingStore,
    private val cookies: CookieStore,
) : YtDlpRepository {
    private val sdk = YoutubeDL.getInstance()
    private val lifecycle = Mutex()
    private var initialized = false
    private var active = 0

    private fun initialize() {
        if (initialized) return
        sdk.init(context)
        FFmpeg.getInstance().init(context)
        Aria2c.getInstance().init(context)
        initialized = true
    }

    private suspend fun <T> session(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        lifecycle.withLock { initialize(); active++ }
        try { block() } finally { withContext(NonCancellable) { lifecycle.withLock { active-- } } }
    }

    override suspend fun getInfo(url: MediaUrl, options: InspectionOptions): MediaInfo = session {
        cookies.withSnapshot(options.useCookies) { cookie ->
            val command = RequestFactory.inspection(url, options, cookie?.absolutePath)
            // getInfo(request) in upstream drops chapters, subtitles and collection entries, and has no process ID.
            // Execute the equivalent full JSON command so cancellation and all metadata remain available.
            val raw = execute(UUID.randomUUID().toString(), command, mergeErrors = false).out
            withContext(Dispatchers.Default) { MetadataParser().parse(raw) }
        }
    }

    override fun download(jobId: String, spec: DownloadSpec): Flow<DownloadEvent> = channelFlow {
        session {
            val directory = staging.directory(jobId)
            // Separate snapshots prevent concurrent yt-dlp processes rewriting the same cookie jar.
            cookies.withSnapshot(spec.options.useCookies) { cookie ->
                val command = RequestFactory.download(spec, directory.absolutePath, cookie?.absolutePath)
                send(DownloadEvent.Started)
                execute(UUID.randomUUID().toString(), command, mergeErrors = true) { percent, eta, line ->
                    if (line.isNotBlank()) {
                        val progress = ProgressParser.parse(line) ?: if (line.startsWith("[#") && percent >= 0) {
                            DownloadProgress(percent, null, null, null, eta.takeIf { it >= 0 }, "downloading")
                        } else null
                        // Bounded backpressure preserves logs. A canceled channel unblocks the native reader.
                        val event = progress?.let { DownloadEvent.Progress(it) } ?: DownloadEvent.Log(LogSanitizer.clean(line))
                        trySendBlocking(event)
                    }
                }
                val files = staging.completedFiles(jobId)
                check(files.isNotEmpty()) { "yt-dlp completed without producing a media file" }
                send(DownloadEvent.Completed(files.map { it.absolutePath }))
            }
        }
    }.buffer(64).flowOn(Dispatchers.IO)

    private suspend fun execute(
        id: String, command: Command, mergeErrors: Boolean,
        callback: ((Float, Long, String) -> Unit)? = null,
    ): com.yausername.youtubedl_android.YoutubeDLResponse {
        // Upstream uses IDs in its child-process cleanup. Accept only UUIDs, never external text.
        require(UUID.fromString(id).toString() == id)
        val request = YoutubeDLRequest(command.url.value).addCommands(command.arguments + "--")
        try {
            return runInterruptible(Dispatchers.IO) {
                sdk.execute(request, processId = id, redirectErrorStream = mergeErrors, callback = callback)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (cancelled: YoutubeDL.CanceledException) {
            throw CancellationException("Download canceled", cancelled)
        } catch (error: Exception) {
            // Upstream exceptions may contain cookies, signed URLs or an entire command line.
            throw EngineException(LogSanitizer.clean(error.message?.takeIf { it.isNotBlank() } ?: "Media extraction failed; inspect the job log"))
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { sdk.destroyProcessById(id) }
        }
    }

    override suspend fun update(): UpdateResult = withContext(Dispatchers.IO) {
        lifecycle.withLock {
            check(active == 0) { "Wait for active downloads and inspections before updating yt-dlp" }
            initialize()
            // Updating rewrites the executable: hold the lifecycle lock until the operation actually finishes.
            when (sdk.updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)) {
                YoutubeDL.UpdateStatus.DONE -> UpdateResult.UPDATED
                YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> UpdateResult.CURRENT
                null -> throw EngineException("yt-dlp returned no update status")
            }
        }
    }

    override suspend fun version(): String? = withContext(Dispatchers.IO) {
        lifecycle.withLock { initialize(); sdk.versionName(context) ?: sdk.version(context) }
    }
}
class EngineException(message: String) : Exception(message)
