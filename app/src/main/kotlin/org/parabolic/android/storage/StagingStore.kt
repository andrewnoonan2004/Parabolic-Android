package org.parabolic.android.storage

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StagingStore @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val mediaExtensions = setOf("mp4", "webm", "mkv", "mp3", "opus", "flac", "wav", "m4a")
    private val exportExtensions = mediaExtensions + setOf("srt", "vtt", "ass", "jpg", "png", "webp")
    fun directory(jobId: String): File {
        require(UUID.fromString(jobId).toString() == jobId)
        return File(context.noBackupFilesDir, "downloads/$jobId").also {
            check(it.isDirectory || it.mkdirs()) { "Cannot create download staging directory" }
        }
    }
    fun completedFiles(jobId: String): List<File> {
        val root = directory(jobId).canonicalFile
        val files = root.walkTopDown().filter {
            it.isFile && it.canonicalFile.toPath().startsWith(root.toPath()) && it.extension.lowercase() in exportExtensions &&
                !Regex("\\]\\.f[0-9A-Za-z_.-]+\\.[^.]+$").containsMatchIn(it.name) &&
                !Regex("\\.temp\\.[^.]+$").containsMatchIn(it.name)
        }.sortedBy { it.name }.toList()
        return if (files.any { it.extension.lowercase() in mediaExtensions }) files else emptyList()
    }
    fun clear(jobId: String) { directory(jobId).deleteRecursively() }
}
