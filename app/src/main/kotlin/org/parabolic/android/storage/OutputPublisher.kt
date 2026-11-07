package org.parabolic.android.storage

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import org.parabolic.android.data.DownloadDao
import org.parabolic.android.data.ExportEntity
import org.parabolic.core.Destination
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Journal each output URI before copying. Retries reuse that URI instead of duplicating completed files. */
@Singleton
class OutputPublisher @Inject constructor(
    @param:ApplicationContext private val context: Context, private val dao: DownloadDao,
) {
    fun persistTreePermission(uri: Uri, resultFlags: Int) {
        require(uri.scheme == "content")
        val flags = resultFlags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        require(flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0) { "The selected folder is not writable" }
        context.contentResolver.takePersistableUriPermission(uri, flags)
    }

    suspend fun publish(jobId: String, files: List<File>, destination: Destination): List<Uri> = withContext(Dispatchers.IO) {
        files.map { file ->
            ensureActive()
            val existing = dao.export(jobId, file.name)
            if (existing?.completed == true) return@map existing.uri.toUri()
            val uri = existing?.uri?.toUri() ?: withContext(NonCancellable) {
                val created = create(file, destination)
                try { dao.saveExport(ExportEntity(jobId, file.name, created.toString())); created }
                catch (error: Exception) { delete(created); throw error }
            }
            try {
                context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                    file.inputStream().use { input ->
                        val buffer = ByteArray(128 * 1024)
                        while (true) {
                            ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                        output.flush()
                    }
                } ?: error("The selected destination is no longer writable")
                withContext(NonCancellable) {
                    if (Build.VERSION.SDK_INT >= 29 && destination == Destination.Downloads) {
                        check(context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null) == 1)
                    }
                    dao.saveExport(ExportEntity(jobId, file.name, uri.toString(), completed = true))
                }
                uri
            } catch (error: Exception) {
                withContext(NonCancellable) { delete(uri); dao.removeExport(jobId, file.name) }
                throw error
            }
        }
    }
    private fun create(file: File, destination: Destination): Uri {
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"
        return when (destination) {
            Destination.Downloads -> {
                if (Build.VERSION.SDK_INT < 29) error("Select a folder with the system picker on Android 8 or 9")
                context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Parabolic")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }) ?: error("Cannot create an entry in Downloads")
            }
            is Destination.Tree -> {
                val tree = DocumentFile.fromTreeUri(context, destination.uri.toUri())
                check(tree != null && tree.canWrite()) { "Select the destination folder again to restore access" }
                tree.createFile(mime, file.name)?.uri ?: error("Cannot create a file in the selected folder")
            }
        }
    }
    private fun delete(uri: Uri) {
        runCatching {
            if (android.provider.DocumentsContract.isDocumentUri(context, uri))
                android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri)
            else context.contentResolver.delete(uri, null, null)
        }
    }
}
