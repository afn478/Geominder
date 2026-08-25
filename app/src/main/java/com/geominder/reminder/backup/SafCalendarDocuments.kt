package com.geominder.reminder.backup

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import java.io.IOException

/** Storage Access Framework contracts; these require no broad storage permission. */
object CalendarDocumentContract {
    const val MIME_TYPE = "text/calendar"
    const val DEFAULT_FILE_NAME = "geominder-reminders.ics"

    fun createDocumentIntent(fileName: String = DEFAULT_FILE_NAME): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = MIME_TYPE
            putExtra(Intent.EXTRA_TITLE, normalizedFileName(fileName))
        }

    fun openDocumentIntent(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = MIME_TYPE
    }

    private fun normalizedFileName(fileName: String): String {
        val safeName = fileName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .trim()
            .ifEmpty { DEFAULT_FILE_NAME }
        return if (safeName.endsWith(".ics", ignoreCase = true)) safeName else "$safeName.ics"
    }
}

/** Opens and closes provider-backed streams around [ReminderBackupManager]. */
class SafReminderBackup(
    private val contentResolver: ContentResolver,
    private val manager: ReminderBackupManager,
) {
    suspend fun exportTo(uri: Uri): CalendarExportResult {
        val output = contentResolver.openOutputStream(uri, "rwt")
            ?: throw IOException("The selected document could not be opened for writing.")
        return output.use { manager.exportTo(it) }
    }

    suspend fun importFrom(uri: Uri): CalendarImportResult {
        val input = contentResolver.openInputStream(uri)
            ?: throw IOException("The selected document could not be opened for reading.")
        return input.use { manager.importFrom(it) }
    }
}
