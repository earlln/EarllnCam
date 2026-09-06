package com.example.earllncam

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/**
 * Scopes MediaStore reads/deletes to files EarllnCam itself saved under
 * .../Pictures/EarllnCam or .../Movies/EarllnCam, so cleanup never touches
 * photos or videos the user took with other apps. The same query backs the
 * in-app gallery, so what the gallery lists is exactly what cleanup would
 * remove.
 */
object MediaVault {
    const val ALBUM = "EarllnCam"

    /** One capture saved by EarllnCam, as shown in the gallery. */
    data class Item(
        val uri: Uri,
        val displayName: String,
        /** MediaStore DATE_ADDED, in seconds since the epoch. */
        val dateAddedSeconds: Long,
        val sizeBytes: Long,
        /** Clip length in milliseconds; 0 for photos. */
        val durationMs: Long,
        val isVideo: Boolean
    ) {
        val mimeType: String get() = if (isVideo) "video/*" else "image/*"
    }

    /** Every EarllnCam capture still on the device, newest first. */
    fun listItems(context: Context): List<Item> =
        (query(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, isVideo = false) +
            query(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, isVideo = true))
            .sortedByDescending { it.dateAddedSeconds }

    fun existingUris(context: Context): List<Uri> = listItems(context).map { it.uri }

    /**
     * What a delete attempt left behind. Files this install did not create --
     * anything saved before the app was reinstalled, say -- cannot be removed
     * silently; [consent] is the system dialog to ask the user with, and the
     * files are only gone once that dialog comes back OK.
     */
    data class DeleteResult(val blocked: List<Uri>, val consent: IntentSender?)

    /**
     * Deletes what this install owns outright and reports the rest, rather than
     * swallowing the SecurityException scoped storage throws for files owned by
     * an earlier install.
     */
    fun deleteUris(context: Context, uris: List<Uri>): DeleteResult {
        val blocked = mutableListOf<Uri>()
        var recoverable: IntentSender? = null

        for (uri in uris) {
            try {
                context.contentResolver.delete(uri, null, null)
            } catch (e: SecurityException) {
                blocked.add(uri)
                if (recoverable == null && e is RecoverableSecurityException) {
                    recoverable = e.userAction.actionIntent.intentSender
                }
            }
        }

        val consent = when {
            blocked.isEmpty() -> null
            // Android 11+ asks about the whole batch in one dialog; on 10 the
            // exception itself carries the (per-file) intent to ask with.
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                MediaStore.createDeleteRequest(context.contentResolver, blocked).intentSender
            else -> recoverable
        }
        return DeleteResult(blocked, consent)
    }

    private fun query(context: Context, collectionUri: Uri, isVideo: Boolean): List<Item> {
        val projection = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.SIZE
        )
        if (isVideo) projection.add(MediaStore.Video.Media.DURATION)

        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("%/$ALBUM/")

        val result = mutableListOf<Item>()
        runCatching {
            context.contentResolver.query(
                collectionUri,
                projection.toTypedArray(),
                selection,
                selectionArgs,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val dateIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val durationIndex =
                    if (isVideo) cursor.getColumnIndex(MediaStore.Video.Media.DURATION) else -1

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    result.add(
                        Item(
                            uri = ContentUris.withAppendedId(collectionUri, id),
                            displayName = cursor.getString(nameIndex) ?: "$ALBUM-$id",
                            dateAddedSeconds = cursor.getLong(dateIndex),
                            sizeBytes = cursor.getLong(sizeIndex),
                            durationMs = if (durationIndex >= 0 && !cursor.isNull(durationIndex)) {
                                cursor.getLong(durationIndex)
                            } else {
                                0L
                            },
                            isVideo = isVideo
                        )
                    )
                }
            }
        }
        return result
    }
}
