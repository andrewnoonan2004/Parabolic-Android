package org.parabolic.core

/** A list of arguments, never a shell command. Paths are supplied only by the app's staging store. */
data class Command(val url: MediaUrl, val arguments: List<String>)

object RequestFactory {
    fun inspection(url: MediaUrl, options: InspectionOptions, cookiePath: String?): Command = Command(url, buildList {
        addAll(common(options.network, cookiePath, options.useCookies))
        addAll(listOf("--dump-single-json", "--skip-download", "--flat-playlist", "--playlist-end", options.playlistLimit.toString()))
    })

    fun download(spec: DownloadSpec, stagingPath: String, cookiePath: String?): Command = Command(spec.url, buildList {
        val o = spec.options
        addAll(common(o.network, cookiePath, o.useCookies))
        addAll(listOf("--no-playlist", "--newline", "--progress", "--progress-delta", "0.5",
            "--progress-template", "download:${ProgressParser.PREFIX}%(progress)j",
            "--continue", "--no-overwrites", "--windows-filenames", "--trim-filenames", "180",
            "--paths", stagingPath, "--output", "%(title).120B [%(id)s].%(ext)s"))
        val height = o.maxHeight?.let { "[height<=$it]" }.orEmpty()
        val language = o.audioLanguage?.let { "[language=$it]" }.orEmpty()
        val audio = o.audioFormatId ?: "bestaudio$language"
        val video = o.videoFormatId ?: "bestvideo$height"
        val selector = if (o.container.audioOnly) {
            if (o.audioFormatId != null || o.audioLanguage != null) audio else "bestaudio/best"
        } else if (o.videoFormatId != null || o.audioFormatId != null || o.audioLanguage != null) {
            // Explicit choices fail if unavailable; never silently substitute a different audio track.
            "$video+$audio"
        } else "$video+$audio/best$height"
        addAll(listOf("--format", selector))
        if (o.container.audioOnly) {
            addAll(listOf("--extract-audio", "--audio-format", o.container.extension, "--audio-quality", "0"))
        } else {
            // Merge to MKV first; recoding then guarantees the selected container even for incompatible codecs.
            addAll(listOf("--merge-output-format", "mkv", "--recode-video", o.container.extension))
        }
        o.clip?.let {
            addAll(listOf("--download-sections", "*${it.startSeconds}-${it.endSeconds}", "--force-keyframes-at-cuts"))
        }
        o.subtitles?.let { subtitles ->
            addAll(listOf("--write-subs", "--sub-langs", subtitles.languages.sorted().joinToString(","),
                "--sub-format", "best", "--convert-subs", subtitles.format.name.lowercase()))
            if (subtitles.includeAutomatic) add("--write-auto-subs")
            if (subtitles.embed) add("--embed-subs")
        }
        if (o.embedMetadata) addAll(listOf("--embed-metadata", "--embed-chapters"))
        if (o.embedThumbnail) addAll(listOf("--embed-thumbnail", "--convert-thumbnails", "jpg"))
        if (o.splitChapters) addAll(listOf("--split-chapters", "--output", "chapter:%(title).100B [%(id)s] - %(section_number)03d %(section_title).60B.%(ext)s"))
        if (o.sponsorCategories.isNotEmpty()) addAll(listOf("--sponsorblock-remove", o.sponsorCategories.map { it.argument }.sorted().joinToString(",")))
        if (o.network.useAria2) addAll(listOf("--downloader", "libaria2c.so"))
    })

    private fun common(network: NetworkOptions, cookiePath: String?, useCookies: Boolean): List<String> = buildList {
        // Ignore external configs and prevent yt-dlp from prompting for stdin in background work.
        addAll(listOf("--ignore-config", "--no-color", "--no-call-home", "--socket-timeout", "30", "--retries", "3", "--fragment-retries", "3"))
        network.bytesPerSecond?.let { addAll(listOf("--limit-rate", it.toString())) }
        network.proxy?.let { addAll(listOf("--proxy", it)) }
        if (useCookies) {
            require(!cookiePath.isNullOrBlank()) { "Import cookies before using authentication" }
            addAll(listOf("--cookies", cookiePath))
        }
    }
}
