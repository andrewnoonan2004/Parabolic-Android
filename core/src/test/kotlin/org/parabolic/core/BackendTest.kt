package org.parabolic.core

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class BackendTest {
    private val url = MediaUrl.parse("https://example.com/watch?v=123&list=abc")
    private fun args(options: DownloadOptions = DownloadOptions()) = RequestFactory.download(DownloadSpec(url, options), "/private/job with space", null).arguments
    private fun List<String>.value(flag: String) = get(indexOf(flag) + 1)

    @Test fun `web url keeps query and trims whitespace`() {
        assertEquals(url, MediaUrl.parse("  ${url.value}  "))
    }
    @Test fun `unsafe urls are rejected`() {
        listOf("file:///etc/passwd", "javascript:alert(1)", "--exec=evil", "https://", "https://user:secret@example.com", "https://example.com/\n").forEach { value ->
            if (value.endsWith("\n")) assertThrows(IllegalArgumentException::class.java) { MediaUrl.parse("https://exam\nple.com") }
            else assertThrows(IllegalArgumentException::class.java) { MediaUrl.parse(value) }
        }
    }
    @Test fun `arguments retain spaces without shell quoting`() {
        assertEquals("/private/job with space", args().value("--paths"))
        assertEquals("%(title).120B [%(id)s].%(ext)s", args().value("--output"))
    }
    @Test fun `default video selector has a combined-stream fallback`() {
        assertEquals("bestvideo+bestaudio/best", args().value("--format"))
        assertEquals("mkv", args().value("--merge-output-format"))
        assertEquals("mp4", args().value("--recode-video"))
    }
    @Test fun `height cap applies to fallback too`() {
        assertEquals("bestvideo[height<=720]+bestaudio/best[height<=720]", args(DownloadOptions(maxHeight = 720)).value("--format"))
    }
    @Test fun `every audio format extracts and converts`() {
        Container.entries.filter { it.audioOnly }.forEach { format ->
            val command = args(DownloadOptions(container = format))
            assertTrue(command.contains("--extract-audio"))
            assertEquals(format.extension, command.value("--audio-format"))
            assertFalse(command.contains("--recode-video"))
        }
    }
    @Test fun `all video containers recode`() {
        Container.entries.filterNot { it.audioOnly }.forEach { format ->
            assertEquals(format.extension, args(DownloadOptions(container = format)).value("--recode-video"))
        }
    }
    @Test fun `explicit format and language selection never falls back silently`() {
        assertEquals("137+140", args(DownloadOptions(videoFormatId = "137", audioFormatId = "140")).value("--format"))
        assertEquals("bestvideo+bestaudio[language=fr]", args(DownloadOptions(audioLanguage = "fr")).value("--format"))
    }
    @Test fun `reject format-expression injection`() {
        assertThrows(IllegalArgumentException::class.java) { DownloadOptions(videoFormatId = "137/best") }
        assertThrows(IllegalArgumentException::class.java) { DownloadOptions(audioLanguage = "en]+best") }
    }
    @Test fun `clip is a locale-independent numeric interval`() {
        assertEquals("*1.5-20.0", args(DownloadOptions(clip = Clip(1.5, 20.0))).value("--download-sections"))
    }
    @Test fun `reject invalid time ranges`() {
        listOf(-1.0 to 3.0, 3.0 to 2.0, 0.0 to Double.NaN, 0.0 to Double.POSITIVE_INFINITY).forEach { (start, end) ->
            assertThrows(IllegalArgumentException::class.java) { Clip(start, end) }
        }
    }
    @Test fun `subtitles preserve requested languages and conversion`() {
        val command = args(DownloadOptions(subtitles = Subtitles(setOf("fr", "en"), SubtitleFormat.ASS, true, true)))
        assertEquals("en,fr", command.value("--sub-langs"))
        assertEquals("ass", command.value("--convert-subs"))
        assertTrue(command.containsAll(listOf("--write-subs", "--embed-subs", "--write-auto-subs")))
    }
    @Test fun `incompatible combinations fail before spawning process`() {
        assertThrows(IllegalArgumentException::class.java) { DownloadOptions(container = Container.WAV, embedThumbnail = true) }
        assertThrows(IllegalArgumentException::class.java) { DownloadOptions(container = Container.WEBM, subtitles = Subtitles()) }
        assertThrows(IllegalArgumentException::class.java) { DownloadOptions(splitChapters = true, clip = Clip(0.0, 1.0)) }
        assertThrows(IllegalArgumentException::class.java) { DownloadOptions(container = Container.MP3, videoFormatId = "137") }
    }
    @Test fun `sidecar subtitles work with webm`() {
        assertTrue(args(DownloadOptions(container = Container.WEBM, subtitles = Subtitles(embed = false))).contains("--write-subs"))
    }
    @Test fun `sponsorblock and chapters have stable arguments`() {
        val command = args(DownloadOptions(splitChapters = true, sponsorCategories = setOf(SponsorCategory.SPONSOR, SponsorCategory.INTRO)))
        assertEquals("intro,sponsor", command.value("--sponsorblock-remove"))
        assertTrue(command.contains("--split-chapters"))
    }
    @Test fun `rate limits and proxies remain separate argument tokens`() {
        val command = args(DownloadOptions(network = NetworkOptions(bytesPerSecond = 1024, proxy = "socks5://localhost:1080")))
        assertEquals("1024", command.value("--limit-rate"))
        assertEquals("socks5://localhost:1080", command.value("--proxy"))
    }
    @Test fun `aria2 is explicit and cannot defeat a speed cap`() {
        assertEquals("libaria2c.so", args(DownloadOptions(network = NetworkOptions(useAria2 = true))).value("--downloader"))
        assertThrows(IllegalArgumentException::class.java) { NetworkOptions(bytesPerSecond = 1000, useAria2 = true) }
        assertThrows(IllegalArgumentException::class.java) { NetworkOptions(proxy = "https://secret:password@host") }
    }
    @Test fun `cookies require a private snapshot`() {
        assertThrows(IllegalArgumentException::class.java) { args(DownloadOptions(useCookies = true)) }
        val command = RequestFactory.download(DownloadSpec(url, DownloadOptions(useCookies = true)), "/private/job", "/private/cookies.txt")
        assertEquals("/private/cookies.txt", command.arguments.value("--cookies"))
    }
    @Test fun `inspection is bounded and does not download`() {
        val command = RequestFactory.inspection(url, InspectionOptions(playlistLimit = 50), null)
        assertTrue(command.arguments.containsAll(listOf("--dump-single-json", "--flat-playlist", "--skip-download")))
        assertEquals("50", command.arguments.value("--playlist-end"))
        assertFalse(command.arguments.contains("--no-playlist"))
    }
    @Test fun `download never accidentally expands playlist`() { assertTrue(args().contains("--no-playlist")) }
    @Test fun `spec roundtrips persisted data`() {
        val spec = DownloadSpec(url, DownloadOptions(container = Container.OPUS), Destination.Tree("content://provider/tree/media"))
        assertEquals(spec, Json.decodeFromString<DownloadSpec>(Json.encodeToString(spec)))
    }
    @Test fun `structured progress handles estimated sizes and decimal ETA`() {
        val p = ProgressParser.parse("""PARABOLIC_PROGRESS:{"downloaded_bytes":25,"total_bytes_estimate":100,"speed":1024.5,"eta":2.9,"status":"downloading"}""")!!
        assertEquals(25f, p.percent)
        assertEquals(1024.5, p.bytesPerSecond)
        assertEquals(2L, p.etaSeconds)
    }
    @Test fun `unknown totals stay indeterminate`() {
        assertNull(ProgressParser.parse("""PARABOLIC_PROGRESS:{"downloaded_bytes":5,"total_bytes":0,"speed":null}""")!!.percent)
    }
    @Test fun `malformed progress becomes log instead of crashing`() {
        assertNull(ProgressParser.parse("PARABOLIC_PROGRESS:not-json"))
        assertNull(ProgressParser.parse("[Merger] Merging formats"))
    }
    @Test fun `progress never exceeds one hundred`() {
        assertEquals(100f, ProgressParser.parse("""PARABOLIC_PROGRESS:{"downloaded_bytes":120,"total_bytes":100}""")!!.percent)
    }
    @Test fun `logs redact signed urls credentials and terminal escapes`() {
        val line = LogSanitizer.clean("\u001B[31mhttps://example.com/?token=secret cookie=secret authorization=abc")
        assertFalse(line.contains("secret")); assertFalse(line.contains("abc")); assertFalse(line.contains("\u001B"))
        assertTrue(line.contains("[URL]"))
    }
    @Test fun `logs redact full bearer and cookie headers`() {
        assertFalse(LogSanitizer.clean("Authorization: Bearer topsecret").contains("topsecret"))
        assertFalse(LogSanitizer.clean("Cookie: session=secret; other=private").contains("private"))
    }
    @Test fun `logs are bounded`() { assertEquals(4096, LogSanitizer.clean("x".repeat(10000)).length) }
    @Test fun `metadata preserves fractional values language and chapters`() {
        val info = MetadataParser().parse("""{
            "id":"abc","title":"Video","duration":12.5,"future_field":true,
            "formats":[{"format_id":"251","ext":"webm","fps":29.97,"acodec":"opus","vcodec":"none","language":"de","filesize_approx":1234,"audio_channels":2}],
            "chapters":[{"title":"Start","start_time":0,"end_time":12.5}],
            "subtitles":{"de":[{"ext":"srt"},{"ext":"vtt"}]},
            "automatic_captions":{"en":[{"ext":"vtt"}]}
        }""")
        assertEquals(12.5, info.durationSeconds)
        assertEquals(29.97, info.formats.single().fps)
        assertNull(info.formats.single().videoCodec)
        assertEquals("de", info.formats.single().audioLanguage)
        assertTrue(info.formats.single().approximateSize)
        assertEquals("Start", info.chapters.single().title)
        assertEquals(2, info.subtitles.size)
        assertTrue(info.subtitles.last().automatic)
    }
    @Test fun `collection handles unavailable entries and count`() {
        val info = MetadataParser().parse("""{"_type":"playlist","title":"Collection","playlist_count":500,"entries":[null,{"id":"one","url":"https://example.com/one"}]}""")
        assertTrue(info.isCollection)
        assertEquals(500, info.entryCount)
        assertEquals(1, info.unavailableEntries)
        assertEquals("https://example.com/one", info.entries.single().webpageUrl)
    }
    @Test fun `sparse live metadata remains usable`() {
        val info = MetadataParser().parse("""{"id":"live","duration":null,"formats":null,"chapters":null}""")
        assertNull(info.durationSeconds)
        assertEquals("live", info.title)
        assertTrue(info.formats.isEmpty())
    }
}
