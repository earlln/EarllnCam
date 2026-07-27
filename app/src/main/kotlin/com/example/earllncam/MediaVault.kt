package com.example.earllncam

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

/**
 * Scopes MediaStore reads/deletes to files EarllnCam itself saved under
 * .../Pictures/EarllnCam or .../Movies/EarllnCam, so cleanup never touches
 * photos or videos the user took with other apps.
 */
object MediaVault {
    const val ALBUM = "EarllnCam"

    fun existingUris(context: Context): List<Uri> {
        val result = mutableListOf<Uri>()
        val collections = listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI to MediaStore.Images.Media._ID,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI to MediaStore.Video.Media._ID
        )
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("%/$ALBUM/")

        for ((collectionUri, idColumn) in collections) {
            context.contentResolver.query(
                collectionUri,
                arrayOf(idColumn),
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(idColumn)
                while (cursor.moveToNext()) {
                    result.add(ContentUris.withAppendedId(collectionUri, cursor.getLong(idIndex)))
                }
            }
        }
        return result
    }

    fun deleteUris(context: Context, uris: List<Uri>) {
        for (uri in uris) {
            runCatching { context.contentResolver.delete(uri, null, null) }
        }
    }
}
