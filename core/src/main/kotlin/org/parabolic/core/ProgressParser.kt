package org.parabolic.core

import kotlinx.serialization.json.*

object ProgressParser {
    const val PREFIX = "PARABOLIC_PROGRESS:"
    fun parse(line: String): DownloadProgress? {
        if (!line.startsWith(PREFIX)) return null
        val o = try { Json.parseToJsonElement(line.removePrefix(PREFIX)).jsonObject } catch (_: IllegalArgumentException) { return null }
        val downloaded = o.long("downloaded_bytes")?.coerceAtLeast(0)
        val total = (o.long("total_bytes") ?: o.long("total_bytes_estimate"))?.takeIf { it > 0 }
        return DownloadProgress(
            percent = if (downloaded != null && total != null) (downloaded.toDouble() / total * 100).toFloat().coerceIn(0f, 100f) else null,
            downloadedBytes = downloaded, totalBytes = total,
            bytesPerSecond = o.number("speed")?.takeIf { it >= 0 }, etaSeconds = o.number("eta")?.takeIf { it >= 0 }?.toLong(),
            status = o.string("status"),
        )
    }
}

object LogSanitizer {
    private val urls = Regex("(?i)(?:https?|socks[45]h?)://[^\\s<>]+")
    private val credentials = Regex("(?i)(cookie|set-cookie|authorization|password|token)([=:]\\s*).*")
    private val ansi = Regex("\\u001B\\[[0-9;]*[A-Za-z]")
    fun clean(line: String): String = credentials.replace(urls.replace(ansi.replace(line, ""), "[URL]"), "$1$2[REDACTED]")
        .filter { !it.isISOControl() || it == '\t' }.take(4096)
}
