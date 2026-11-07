package org.parabolic.android.storage

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Credentials live only in internal, non-backed-up storage. The WebView login UI belongs to Step 4. */
@Singleton
class CookieStore @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val lock = Mutex()
    private val jar get() = File(context.noBackupFilesDir, "cookies.txt")

    suspend fun import(uri: Uri) = withContext(Dispatchers.IO) {
        require(uri.scheme == "content")
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (output.size() <= 2 * 1024 * 1024) {
                val count = input.read(buffer, 0, minOf(buffer.size, 2 * 1024 * 1024 + 1 - output.size()))
                if (count < 0) break
                output.write(buffer, 0, count)
            }
            output.toByteArray()
         }
            ?: error("Cannot read cookie file")
        require(bytes.size <= 2 * 1024 * 1024) { "Cookie file exceeds 2 MiB" }
        val text = bytes.toString(Charsets.UTF_8)
        saveValidatedNetscapeText(text)
    }

    suspend fun importNetscapeText(text: String) = withContext(Dispatchers.IO) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        require(bytes.size <= 2 * 1024 * 1024) { "Cookie file exceeds 2 MiB" }
        saveValidatedNetscapeText(text)
    }

    private suspend fun saveValidatedNetscapeText(text: String) {
        require(text.lineSequence().firstOrNull()?.let { it.contains("Netscape HTTP Cookie File") || it.contains("HTTP Cookie File") } == true) {
            "Select a Netscape cookies.txt file"
        }
        require(text.lineSequence().filter { it.isNotBlank() && (!it.startsWith("#") || it.startsWith("#HttpOnly_")) }
            .all { it.split('\t').size == 7 }) { "Malformed cookie record" }
        val bytes = text.toByteArray(Charsets.UTF_8)
        lock.withLock {
            val tmp = File(context.noBackupFilesDir, "cookies.import")
            try {
                tmp.outputStream().use { output -> output.write(bytes); output.fd.sync() }
                check(tmp.renameTo(jar)) { "Cannot store cookies" }
            } finally { tmp.delete() }
        }
    }

    suspend fun hasCookies(): Boolean = withContext(Dispatchers.IO) {
        lock.withLock { jar.isFile && jar.length() > 0L }
    }

    suspend fun cookieCount(): Int = withContext(Dispatchers.IO) {
        lock.withLock {
            if (!jar.isFile) 0
            else jar.useLines { lines ->
                lines.count { it.isNotBlank() && (!it.startsWith("#") || it.startsWith("#HttpOnly_")) }
            }
        }
    }

    suspend fun <T> withSnapshot(enabled: Boolean, block: suspend (File?) -> T): T = withContext(Dispatchers.IO) {
        val copy = if (enabled) lock.withLock {
            check(jar.isFile) { "Import cookies before using authentication" }
            val directory = File(context.noBackupFilesDir, "cookie-sessions").also { it.mkdirs() }
            val file = File.createTempFile("cookies-", ".txt", directory)
            try { jar.copyTo(file, overwrite = true); file }
            catch (error: Exception) { file.delete(); throw error }
        } else null
        try { block(copy) } finally { copy?.delete() }
    }
    suspend fun clear() = withContext(Dispatchers.IO) { lock.withLock { jar.delete(); Unit } }
}

/** Formats standard HTTP `Cookie:` headers captured from authenticated WebView sessions into Netscape cookies.txt. */
object NetscapeCookieFormatter {
    fun format(cookiesByUrl: Map<String, String>, expiresEpochSeconds: Long = (System.currentTimeMillis() / 1000L) + 86400L * 30): String {
        val rows = mutableListOf<String>()
        val seen = LinkedHashSet<Pair<String, String>>()
        for ((rawUrl, rawCookieHeader) in cookiesByUrl) {
            if (rawCookieHeader.isBlank()) continue
            val uri = runCatching { java.net.URI(rawUrl.trim()) }.getOrNull() ?: continue
            val host = uri.host?.lowercase()?.trim()?.takeIf { it.isNotBlank() } ?: continue
            val secure = if (uri.scheme.equals("https", ignoreCase = true)) "TRUE" else "FALSE"
            val domainField = if (host.contains('.')) ".$host" else host
            val includeSubdomains = if (domainField.startsWith(".")) "TRUE" else "FALSE"
            for (segment in rawCookieHeader.split(';')) {
                val trimmed = segment.trim()
                val eq = trimmed.indexOf('=')
                if (eq <= 0) continue
                val name = sanitizeField(trimmed.substring(0, eq).trim())
                val value = sanitizeField(trimmed.substring(eq + 1).trim())
                if (name.isEmpty()) continue
                if (seen.add(domainField to name)) {
                    rows += listOf(domainField, includeSubdomains, "/", secure, expiresEpochSeconds.coerceAtLeast(0L).toString(), name, value)
                        .joinToString("\t")
                }
            }
        }
        require(rows.isNotEmpty()) { "No session cookies were found on this page" }
        return buildString {
            appendLine("# Netscape HTTP Cookie File")
            appendLine("# Generated by Parabolic for Android")
            rows.forEach { appendLine(it) }
        }
    }

    private fun sanitizeField(input: String): String =
        input.filter { !it.isISOControl() && it != '\t' }
}
