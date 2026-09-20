// SPDX-License-Identifier: GPL-3.0-only

package com.openrecorder.app

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.Immutable

@Immutable
internal data class RecordingItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val durationMillis: Long,
    val sizeBytes: Long,
    val dateAddedSeconds: Long,
)

internal data class RecordingDeletionResult(
    val deletedCount: Int,
    val failedCount: Int,
)

internal class RecordingRepository(context: Context) {
    private val applicationContext = context.applicationContext
    private val resolver = applicationContext.contentResolver

    fun loadRecordings(): List<RecordingItem> {
        val collection = MediaStore.Video.Media.getContentUri(
            MediaStore.VOLUME_EXTERNAL_PRIMARY,
        )
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED,
        )
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? " +
            "AND ${MediaStore.MediaColumns.MIME_TYPE} = ? " +
            "AND ${MediaStore.MediaColumns.OWNER_PACKAGE_NAME} = ? " +
            "AND ${MediaStore.MediaColumns.IS_PENDING} = 0"
        val selectionArgs = arrayOf(
            "$RECORDINGS_DIRECTORY%",
            VIDEO_MIME_TYPE,
            applicationContext.packageName,
        )
        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC, " +
            "${MediaStore.Video.Media._ID} DESC"

        return resolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            sortOrder,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)

            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    add(
                        RecordingItem(
                            id = id,
                            uri = ContentUris.withAppendedId(collection, id),
                            name = cursor.getString(nameColumn).orEmpty(),
                            durationMillis = cursor.getLong(durationColumn),
                            sizeBytes = cursor.getLong(sizeColumn),
                            dateAddedSeconds = cursor.getLong(dateColumn),
                        ),
                    )
                }
            }
        }.orEmpty()
    }

    fun deleteRecordings(recordings: List<RecordingItem>): RecordingDeletionResult {
        var deletedCount = 0
        var failedCount = 0

        recordings.distinctBy(RecordingItem::id).forEach { recording ->
            try {
                if (resolver.delete(recording.uri, null, null) > 0) {
                    deletedCount++
                } else {
                    failedCount++
                }
            } catch (_: RuntimeException) {
                failedCount++
            }
        }

        return RecordingDeletionResult(
            deletedCount = deletedCount,
            failedCount = failedCount,
        )
    }

    private companion object {
        const val RECORDINGS_DIRECTORY = "Movies/Open Recorder/"
        const val VIDEO_MIME_TYPE = "video/mp4"
    }
}
