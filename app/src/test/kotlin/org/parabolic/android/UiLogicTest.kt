package org.parabolic.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.parabolic.android.data.DownloadEntity
import org.parabolic.android.data.QueueState
import org.parabolic.android.storage.NetscapeCookieFormatter
import org.parabolic.android.ui.AddDownloadDraft
import org.parabolic.android.ui.ByteFormatter
import org.parabolic.android.ui.MainUiState
import org.parabolic.android.ui.MainViewModel
import org.parabolic.android.ui.NetworkDraft
import org.parabolic.android.ui.QueueFilter
import org.parabolic.android.ui.QueueItemUi
import org.parabolic.android.ui.TimeParser
import org.parabolic.core.Container
import org.parabolic.core.Destination
import org.parabolic.core.MediaInfo
import org.parabolic.core.MediaUrl
import org.parabolic.core.NetworkOptions
import org.parabolic.core.SponsorCategory
import org.parabolic.core.SubtitleFormat

class UiLogicTest {

    @Test
    fun `time parser handles seconds, mm ss, and hh mm ss`() {
        assertEquals(45.5, TimeParser.parseSeconds("45.5") ?: -1.0, 0.001)
        assertEquals(95.0, TimeParser.parseSeconds("01:35") ?: -1.0, 0.001)
        assertEquals(3723.25, TimeParser.parseSeconds("1:02:03.25") ?: -1.0, 0.001)
        assertNull(TimeParser.parseSeconds(""))
        assertNull(TimeParser.parseSeconds("1:99"))
        assertNull(TimeParser.parseSeconds("-5"))
        assertEquals("1:01:05", TimeParser.formatDuration(3665.0))
        assertEquals("02:09", TimeParser.formatDuration(129.0))
    }

    @Test
    fun `byte formatter formats sizes and speeds`() {
        assertEquals("512 B", ByteFormatter.formatBytes(512))
        assertEquals("1.5 MB", ByteFormatter.formatBytes(1_572_864))
        assertEquals("2.0 MB/s", ByteFormatter.formatSpeed(2_097_152.0))
    }

    @Test
    fun `container change enforces audio-only and embedding constraints`() {
        val initial = AddDownloadDraft(
            urlInput = "https://example.com/watch?v=123",
            container = Container.MP4,
            maxHeight = 1080,
            videoFormatId = "137",
            audioFormatId = "140",
            subtitlesEnabled = true,
            embedSubtitles = true,
            embedThumbnail = true,
        )
        val webm = initial.withContainer(Container.WEBM)
        assertFalse(webm.embedSubtitles)
        assertFalse(webm.embedThumbnail)
        assertEquals("137", webm.videoFormatId)

        val opus = initial.withContainer(Container.OPUS)
        assertNull(opus.videoFormatId)
        assertNull(opus.maxHeight)
        assertFalse(opus.embedSubtitles)
        assertFalse(opus.embedThumbnail)
        assertEquals("140", opus.audioFormatId)
    }

    @Test
    fun `split chapters and clipping are mutually exclusive in draft`() {
        val draft = AddDownloadDraft(urlInput = "https://example.com/watch?v=1")
            .withClipEnabled(true)
            .copy(clipStartInput = "0:10", clipEndInput = "0:30")
            .withSplitChapters(true)
        assertFalse(draft.clipEnabled)
        assertTrue(draft.splitChapters)
    }

    @Test
    fun `single video draft builds validated DownloadSpec`() {
        val draft = AddDownloadDraft(
            urlInput = "https://example.com/watch?v=abc",
            container = Container.MKV,
            maxHeight = 1080,
            clipEnabled = true,
            clipStartInput = "01:00",
            clipEndInput = "02:30",
            subtitlesEnabled = true,
            subtitleLanguagesInput = "en, es",
            subtitleFormat = SubtitleFormat.SRT,
            embedSubtitles = true,
            embedMetadata = true,
            embedThumbnail = true,
            sponsorCategories = setOf(SponsorCategory.SPONSOR, SponsorCategory.INTRO),
            useCookies = true,
        )
        val specs = draft.toDownloadSpecs(
            defaultNetwork = NetworkOptions(bytesPerSecond = 512_000L),
            requiresSafFolder = false,
        ).getOrThrow()

        assertEquals(1, specs.size)
        val spec = specs.single()
        assertEquals(MediaUrl.parse("https://example.com/watch?v=abc"), spec.url)
        assertEquals(Container.MKV, spec.options.container)
        assertEquals(1080, spec.options.maxHeight)
        assertEquals(60.0, spec.options.clip!!.startSeconds, 0.001)
        assertEquals(150.0, spec.options.clip!!.endSeconds, 0.001)
        assertEquals(setOf("en", "es"), spec.options.subtitles!!.languages)
        assertTrue(spec.options.subtitles!!.embed)
        assertTrue(spec.options.useCookies)
        assertEquals(Destination.Downloads, spec.destination)
    }

    @Test
    fun `playlist draft expands only selected entry urls into individual jobs`() {
        val collection = MediaInfo(
            id = "pl1",
            title = "Sample Playlist",
            webpageUrl = "https://example.com/playlist?list=pl1",
            thumbnailUrl = null,
            uploader = "Author",
            durationSeconds = null,
            formats = emptyList(),
            chapters = emptyList(),
            subtitles = emptyList(),
            entries = listOf(
                MediaInfo("1", "One", "https://example.com/watch?v=1", null, null, 60.0, emptyList(), emptyList(), emptyList(), emptyList(), false, null, 0),
                MediaInfo("2", "Two", "https://example.com/watch?v=2", null, null, 90.0, emptyList(), emptyList(), emptyList(), emptyList(), false, null, 0),
                MediaInfo("3", "Three", "https://example.com/watch?v=3", null, null, 120.0, emptyList(), emptyList(), emptyList(), emptyList(), false, null, 0),
            ),
            isCollection = true,
            entryCount = 3,
            unavailableEntries = 0,
        )
        val draft = AddDownloadDraft(
            urlInput = "https://example.com/playlist?list=pl1",
            container = Container.MP3,
        ).withInspectedMedia(collection)
            .togglePlaylistEntry("https://example.com/watch?v=2")

        val specs = draft.toDownloadSpecs(NetworkOptions(), requiresSafFolder = false).getOrThrow()
        assertEquals(2, specs.size)
        assertEquals(
            listOf("https://example.com/watch?v=1", "https://example.com/watch?v=3"),
            specs.map { it.url.value },
        )
    }

    @Test
    fun `netscape cookie formatter produces valid 7 column records`() {
        val output = NetscapeCookieFormatter.format(
            cookiesByUrl = mapOf(
                "https://www.example.com/login" to "SID=abc123xyz; HSID=def456; invalid_segment",
            ),
            expiresEpochSeconds = 1_900_000_000L,
        )
        val lines = output.lines().filter { it.isNotBlank() }
        assertTrue(lines.first().contains("Netscape HTTP Cookie File"))
        val dataLines = lines.filter { !it.startsWith("#") }
        assertEquals(2, dataLines.size)
        dataLines.forEach { line ->
            val cols = line.split('\t')
            assertEquals(7, cols.size)
            assertEquals(".www.example.com", cols[0])
            assertEquals("TRUE", cols[1])
            assertEquals("/", cols[2])
            assertEquals("TRUE", cols[3])
            assertEquals("1900000000", cols[4])
        }
    }

    @Test
    fun `network draft enforces aria2 and speed limit mutual exclusion`() {
        val draft = NetworkDraft(
            speedLimitKbpsInput = "1024",
            proxyInput = "socks5://127.0.0.1:1080",
            useAria2 = false,
        )
        val net = draft.toNetworkOptions().getOrThrow()
        assertEquals(1024L * 1024L, net.bytesPerSecond)
        assertEquals("socks5://127.0.0.1:1080", net.proxy)
        assertFalse(net.useAria2)

        val toggledAria = draft.withAria2(true)
        assertEquals("", toggledAria.speedLimitKbpsInput)
        assertTrue(toggledAria.useAria2)
    }

    @Test
    fun `queue filtering and fallback title formatting work accurately`() {
        assertEquals(
            "example.com/watch?v=42",
            MainViewModel.formatFallbackTitle("https://www.example.com/watch?v=42"),
        )
        val items = listOf(
            QueueItemUi(DownloadEntity("1", "{}", state = QueueState.DOWNLOADING), null, "Active Item"),
            QueueItemUi(DownloadEntity("2", "{}", state = QueueState.COMPLETED), null, "Done Item"),
            QueueItemUi(DownloadEntity("3", "{}", state = QueueState.FAILED), null, "Failed Item"),
        )
        val state = MainUiState(items = items)
        assertEquals(3, state.filteredItems.size)
        assertEquals(listOf("1"), state.copy(queueFilter = QueueFilter.ACTIVE).filteredItems.map { it.entity.id })
        assertEquals(listOf("2"), state.copy(queueFilter = QueueFilter.COMPLETED).filteredItems.map { it.entity.id })
        assertEquals(listOf("3"), state.copy(queueFilter = QueueFilter.ATTENTION).filteredItems.map { it.entity.id })
    }
}
