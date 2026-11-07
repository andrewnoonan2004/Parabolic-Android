package org.parabolic.android.ui

import org.parabolic.core.Clip
import org.parabolic.core.Container
import org.parabolic.core.Destination
import org.parabolic.core.DownloadOptions
import org.parabolic.core.DownloadSpec
import org.parabolic.core.MediaFormat
import org.parabolic.core.MediaInfo
import org.parabolic.core.MediaUrl
import org.parabolic.core.NetworkOptions
import org.parabolic.core.SponsorCategory
import org.parabolic.core.SubtitleFormat
import org.parabolic.core.Subtitles
import java.util.Locale

object TimeParser {
    fun parseSeconds(text: String): Double? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        if (!trimmed.contains(':')) {
            val seconds = trimmed.toDoubleOrNull() ?: return null
            return seconds.takeIf { it.isFinite() && it >= 0.0 }
        }
        val parts = trimmed.split(':')
        if (parts.size !in 2..3) return null
        val secondsPart = parts.last().toDoubleOrNull() ?: return null
        if (!secondsPart.isFinite() || secondsPart < 0.0 || secondsPart >= 60.0) return null
        val minutesPart = parts[parts.size - 2].toLongOrNull() ?: return null
        if (minutesPart < 0L || (parts.size == 3 && minutesPart >= 60L)) return null
        val hoursPart = if (parts.size == 3) {
            val h = parts[0].toLongOrNull() ?: return null
            if (h < 0L) return null
            h
        } else 0L
        return hoursPart * 3600.0 + minutesPart * 60.0 + secondsPart
    }

    fun formatDuration(seconds: Double?): String {
        val total = seconds?.takeIf { it.isFinite() && it >= 0.0 }?.toLong() ?: return "--:--"
        val hours = total / 3600L
        val minutes = (total % 3600L) / 60L
        val secs = total % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, secs)
        }
    }
}

object ByteFormatter {
    fun formatBytes(bytes: Long?): String {
        val value = bytes?.takeIf { it >= 0L } ?: return "Unknown size"
        return when {
            value < 1024L -> "$value B"
            value < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", value / 1024.0)
            value < 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f MB", value / (1024.0 * 1024.0))
            else -> String.format(Locale.US, "%.2f GB", value / (1024.0 * 1024.0 * 1024.0))
        }
    }

    fun formatSpeed(bytesPerSecond: Double?): String {
        val value = bytesPerSecond?.takeIf { it.isFinite() && it > 0.0 } ?: return ""
        return "${formatBytes(value.toLong())}/s"
    }
}

data class AddDownloadDraft(
    val urlInput: String = "",
    val container: Container = Container.MP4,
    val maxHeight: Int? = 1080,
    val videoFormatId: String? = null,
    val audioFormatId: String? = null,
    val audioLanguage: String? = null,
    val clipEnabled: Boolean = false,
    val clipStartInput: String = "",
    val clipEndInput: String = "",
    val subtitlesEnabled: Boolean = false,
    val subtitleLanguagesInput: String = "en",
    val subtitleFormat: SubtitleFormat = SubtitleFormat.SRT,
    val embedSubtitles: Boolean = true,
    val includeAutoSubtitles: Boolean = false,
    val embedMetadata: Boolean = true,
    val embedThumbnail: Boolean = false,
    val splitChapters: Boolean = false,
    val sponsorCategories: Set<SponsorCategory> = emptySet(),
    val useCookies: Boolean = false,
    val destinationTreeUri: String? = null,
    val inspectedMedia: MediaInfo? = null,
    val selectedEntryUrls: Set<String> = emptySet(),
) {
    val supportsEmbeddedSubtitles: Boolean
        get() = container in setOf(Container.MP4, Container.MKV)

    val supportsCoverArt: Boolean
        get() = container in setOf(Container.MP3, Container.MP4, Container.M4A, Container.FLAC, Container.MKV)

    fun withContainer(newContainer: Container): AddDownloadDraft {
        val canEmbedSubs = newContainer in setOf(Container.MP4, Container.MKV)
        val canEmbedThumb = newContainer in setOf(Container.MP3, Container.MP4, Container.M4A, Container.FLAC, Container.MKV)
        return copy(
            container = newContainer,
            maxHeight = if (newContainer.audioOnly) null else (maxHeight ?: 1080),
            videoFormatId = if (newContainer.audioOnly) null else videoFormatId,
            embedSubtitles = if (canEmbedSubs) embedSubtitles else false,
            embedThumbnail = if (canEmbedThumb) embedThumbnail else false,
        )
    }

    fun withClipEnabled(enabled: Boolean): AddDownloadDraft =
        copy(clipEnabled = enabled, splitChapters = if (enabled) false else splitChapters)

    fun withSplitChapters(enabled: Boolean): AddDownloadDraft =
        copy(splitChapters = enabled, clipEnabled = if (enabled) false else clipEnabled)

    fun withInspectedMedia(info: MediaInfo?): AddDownloadDraft {
        if (info == null) return copy(inspectedMedia = null, selectedEntryUrls = emptySet())
        val entryUrls = if (info.isCollection) {
            info.entries.mapNotNull { it.webpageUrl?.takeIf(String::isNotBlank) }.toSet()
        } else emptySet()
        val defaultLang = info.subtitles.firstOrNull { !it.automatic }?.language
            ?: info.subtitles.firstOrNull()?.language
            ?: subtitleLanguagesInput
        return copy(
            inspectedMedia = info,
            selectedEntryUrls = entryUrls,
            subtitleLanguagesInput = defaultLang,
            videoFormatId = null,
            audioFormatId = null,
        )
    }

    fun togglePlaylistEntry(entryUrl: String): AddDownloadDraft {
        val updated = if (entryUrl in selectedEntryUrls) selectedEntryUrls - entryUrl else selectedEntryUrls + entryUrl
        return copy(selectedEntryUrls = updated)
    }

    fun selectAllPlaylistEntries(): AddDownloadDraft {
        val all = inspectedMedia?.entries?.mapNotNull { it.webpageUrl?.takeIf(String::isNotBlank) }?.toSet().orEmpty()
        return copy(selectedEntryUrls = all)
    }

    fun clearPlaylistEntries(): AddDownloadDraft = copy(selectedEntryUrls = emptySet())

    fun toggleSponsorCategory(category: SponsorCategory): AddDownloadDraft {
        val updated = if (category in sponsorCategories) sponsorCategories - category else sponsorCategories + category
        return copy(sponsorCategories = updated)
    }

    fun videoFormats(): List<MediaFormat> =
        inspectedMedia?.formats?.filter { it.videoCodec != null && it.videoCodec != "none" }.orEmpty()

    fun audioFormats(): List<MediaFormat> =
        inspectedMedia?.formats?.filter { it.audioCodec != null && it.audioCodec != "none" }.orEmpty()

    fun availableAudioLanguages(): List<String> =
        inspectedMedia?.formats
            ?.mapNotNull { it.audioLanguage?.takeIf(String::isNotBlank) }
            ?.distinct()
            ?.sorted()
            .orEmpty()

    fun toDownloadSpecs(
        defaultNetwork: NetworkOptions,
        requiresSafFolder: Boolean,
    ): Result<List<DownloadSpec>> = runCatching {
        val destination = if (!destinationTreeUri.isNullOrBlank()) {
            Destination.Tree(destinationTreeUri)
        } else {
            require(!requiresSafFolder) { "Select a destination folder on Android 8 or 9" }
            Destination.Downloads
        }

        val clip = if (clipEnabled) {
            val start = TimeParser.parseSeconds(clipStartInput)
                ?: throw IllegalArgumentException("Enter a valid clip start time (e.g. 00:30)")
            val end = TimeParser.parseSeconds(clipEndInput)
                ?: throw IllegalArgumentException("Enter a valid clip end time (e.g. 02:15)")
            Clip(startSeconds = start, endSeconds = end)
        } else null

        val subtitles = if (subtitlesEnabled) {
            val langs = subtitleLanguagesInput.split(',', ' ')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
            require(langs.isNotEmpty()) { "Enter at least one subtitle language code (e.g. en)" }
            Subtitles(
                languages = langs,
                format = subtitleFormat,
                embed = embedSubtitles && supportsEmbeddedSubtitles,
                includeAutomatic = includeAutoSubtitles,
            )
        } else null

        val options = DownloadOptions(
            container = container,
            maxHeight = if (container.audioOnly || videoFormatId != null) null else maxHeight,
            videoFormatId = if (container.audioOnly) null else videoFormatId,
            audioFormatId = audioFormatId,
            audioLanguage = audioLanguage?.takeIf { it.isNotBlank() },
            clip = clip,
            subtitles = subtitles,
            embedMetadata = embedMetadata,
            embedThumbnail = embedThumbnail && supportsCoverArt,
            splitChapters = splitChapters && !clipEnabled,
            sponsorCategories = sponsorCategories,
            network = defaultNetwork,
            useCookies = useCookies,
        )

        val info = inspectedMedia
        if (info != null && info.isCollection) {
            val orderedUrls = info.entries
                .mapNotNull { it.webpageUrl?.takeIf(String::isNotBlank) }
                .filter { it in selectedEntryUrls }
            require(orderedUrls.isNotEmpty()) { "Select at least one playlist item to download" }
            orderedUrls.map { entryUrl ->
                DownloadSpec(
                    url = MediaUrl.parse(entryUrl),
                    options = options,
                    destination = destination,
                )
            }
        } else {
            val resolvedUrl = info?.webpageUrl?.takeIf { it.isNotBlank() } ?: urlInput
            listOf(
                DownloadSpec(
                    url = MediaUrl.parse(resolvedUrl),
                    options = options,
                    destination = destination,
                )
            )
        }
    }
}

data class NetworkDraft(
    val speedLimitKbpsInput: String = "",
    val proxyInput: String = "",
    val useAria2: Boolean = false,
) {
    fun withAria2(enabled: Boolean): NetworkDraft =
        copy(useAria2 = enabled, speedLimitKbpsInput = if (enabled) "" else speedLimitKbpsInput)

    fun withSpeedLimit(kbpsText: String): NetworkDraft =
        copy(
            speedLimitKbpsInput = kbpsText,
            useAria2 = if (kbpsText.isNotBlank()) false else useAria2,
        )

    fun toNetworkOptions(): Result<NetworkOptions> = runCatching {
        val kbps = speedLimitKbpsInput.trim().takeIf { it.isNotEmpty() }?.let {
            val parsed = it.toLongOrNull() ?: throw IllegalArgumentException("Speed limit must be a whole number in KB/s")
            require(parsed in 1..1_000_000) { "Speed limit must be between 1 and 1,000,000 KB/s" }
            parsed * 1024L
        }
        val proxy = proxyInput.trim().takeIf { it.isNotEmpty() }
        NetworkOptions(
            bytesPerSecond = kbps,
            proxy = proxy,
            useAria2 = useAria2,
        )
    }

    companion object {
        fun from(options: NetworkOptions): NetworkDraft = NetworkDraft(
            speedLimitKbpsInput = options.bytesPerSecond?.let { (it / 1024L).coerceAtLeast(1L).toString() }.orEmpty(),
            proxyInput = options.proxy.orEmpty(),
            useAria2 = options.useAria2,
        )
    }
}
