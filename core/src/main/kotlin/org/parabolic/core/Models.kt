package org.parabolic.core

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import java.net.URI

/** Only web URLs enter yt-dlp. Local files, pseudo-protocols and embedded credentials are rejected. */
@Serializable
@JvmInline
value class MediaUrl private constructor(val value: String) {
    companion object {
        fun parse(text: String): MediaUrl {
            val value = text.trim()
            require(value.length in 1..8192 && value.none { it.isISOControl() }) { "Invalid URL" }
            val uri = try { URI(value) } catch (_: Exception) { throw IllegalArgumentException("Invalid URL") }
            require(uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank()) {
                "Enter a complete HTTP or HTTPS URL"
            }
            require(uri.rawUserInfo == null) { "Use cookies instead of credentials in URLs" }
            return MediaUrl(value)
        }
    }
}

@Serializable enum class Container(val extension: String, val audioOnly: Boolean) {
    MP4("mp4", false), WEBM("webm", false), MKV("mkv", false),
    MP3("mp3", true), OPUS("opus", true), FLAC("flac", true), WAV("wav", true), M4A("m4a", true)
}
@Serializable enum class SubtitleFormat { SRT, VTT, ASS }
@Serializable enum class SponsorCategory(val argument: String) {
    SPONSOR("sponsor"), INTRO("intro"), OUTRO("outro"), SELFPROMO("selfpromo"),
    INTERACTION("interaction"), PREVIEW("preview"), FILLER("filler"), MUSIC_OFFTOPIC("music_offtopic")
}
@Serializable data class Clip(val startSeconds: Double, val endSeconds: Double) {
    init { require(startSeconds.isFinite() && endSeconds.isFinite() && startSeconds >= 0 && endSeconds > startSeconds) }
}
@Serializable data class Subtitles(
    val languages: Set<String> = setOf("en"),
    val format: SubtitleFormat = SubtitleFormat.SRT,
    val embed: Boolean = true,
    val includeAutomatic: Boolean = false,
) {
    init {
        require(languages.isNotEmpty() && languages.all { it.matches(Regex("[A-Za-z0-9_-]{1,35}")) })
    }
}
@Serializable data class NetworkOptions(
    val bytesPerSecond: Long? = null,
    val proxy: String? = null,
    val useAria2: Boolean = false,
) {
    init {
        require(bytesPerSecond == null || bytesPerSecond > 0)
        proxy?.let {
            val uri = URI(it)
            require(uri.scheme in setOf("http", "https", "socks4", "socks5", "socks5h") && !uri.host.isNullOrBlank())
            require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
                uri.path.orEmpty() in setOf("", "/") && uri.port in -1..65535) {
                "Proxy credentials and paths are not supported"
            }
        }
        // aria2's separate connection pools cannot guarantee a whole-job rate cap.
        require(!useAria2 || bytesPerSecond == null) { "Disable aria2 to apply a speed limit" }
    }
}
@Serializable data class DownloadOptions(
    val container: Container = Container.MP4,
    val maxHeight: Int? = null,
    val videoFormatId: String? = null,
    val audioFormatId: String? = null,
    val audioLanguage: String? = null,
    val clip: Clip? = null,
    val subtitles: Subtitles? = null,
    val embedMetadata: Boolean = true,
    val embedThumbnail: Boolean = false,
    val splitChapters: Boolean = false,
    val sponsorCategories: Set<SponsorCategory> = emptySet(),
    val network: NetworkOptions = NetworkOptions(),
    val useCookies: Boolean = false,
) {
    init {
        require(maxHeight == null || maxHeight in 144..16384)
        listOfNotNull(videoFormatId, audioFormatId).forEach { require(it.matches(Regex("[A-Za-z0-9_.-]{1,100}"))) }
        audioLanguage?.let { require(it.matches(Regex("[A-Za-z0-9_-]{1,35}"))) }
        require(!container.audioOnly || videoFormatId == null)
        require(!splitChapters || clip == null) { "Choose clipping or chapter splitting, not both" }
        require(subtitles?.embed != true || container in setOf(Container.MP4, Container.MKV)) {
            "Embedded subtitles require MP4 or MKV; use sidecar subtitles for other containers"
        }
        require(!embedThumbnail || container in setOf(Container.MP3, Container.MP4, Container.M4A, Container.FLAC, Container.MKV)) {
            "This container does not support cover art"
        }
    }
}
@Serializable sealed interface Destination {
    @Serializable data object Downloads : Destination
    @Serializable data class Tree(val uri: String) : Destination {
        init { require(URI(uri).scheme == "content") }
    }
}
@Serializable data class DownloadSpec(
    val url: MediaUrl,
    val options: DownloadOptions = DownloadOptions(),
    val destination: Destination = Destination.Downloads,
)

data class MediaFormat(
    val id: String, val extension: String?, val width: Int?, val height: Int?, val fps: Double?,
    val videoCodec: String?, val audioCodec: String?, val audioLanguage: String?,
    val note: String?, val bytes: Long?, val approximateSize: Boolean,
    val bitrateKbps: Double?, val audioChannels: Int?,
)
data class Chapter(val title: String, val startSeconds: Double, val endSeconds: Double?)
data class SubtitleTrack(val language: String, val extensions: Set<String>, val automatic: Boolean)
data class MediaInfo(
    val id: String?, val title: String, val webpageUrl: String?, val thumbnailUrl: String?,
    val uploader: String?, val durationSeconds: Double?, val formats: List<MediaFormat>,
    val chapters: List<Chapter>, val subtitles: List<SubtitleTrack>, val entries: List<MediaInfo>,
    val isCollection: Boolean, val entryCount: Int?, val unavailableEntries: Int,
)
data class DownloadProgress(
    val percent: Float?, val downloadedBytes: Long?, val totalBytes: Long?,
    val bytesPerSecond: Double?, val etaSeconds: Long?, val status: String?,
)
sealed interface DownloadEvent {
    data object Started : DownloadEvent
    data class Progress(val value: DownloadProgress) : DownloadEvent
    /** Sanitized merged stdout/stderr, never raw credentials or signed media URLs. */
    data class Log(val line: String) : DownloadEvent
    data class Completed(val files: List<String>) : DownloadEvent
}
data class InspectionOptions(val network: NetworkOptions = NetworkOptions(), val useCookies: Boolean = false, val playlistLimit: Int = 200) {
    init { require(playlistLimit in 1..1000) }
}
enum class UpdateResult { UPDATED, CURRENT }
interface YtDlpRepository {
    /** Flat collections are bounded; inspect an entry's webpage URL to get its formats before enqueueing. */
    suspend fun getInfo(url: MediaUrl, options: InspectionOptions = InspectionOptions()): MediaInfo
    /** Cold flow. Collect exactly once per job. Cancellation terminates the native process. */
    fun download(jobId: String, spec: DownloadSpec): Flow<DownloadEvent>
    suspend fun update(): UpdateResult
    suspend fun version(): String?
}
