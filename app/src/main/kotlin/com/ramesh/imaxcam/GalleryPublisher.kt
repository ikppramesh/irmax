package com.ramesh.imaxcam

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import java.io.File

private const val TAG = "IRMAX-Gallery"

/**
 * Copies the final 1.43:1-cropped deliverable into the shared MediaStore collection so it shows
 * up in the phone's Gallery app — the RAW intermediate stays app-private (it's pipeline input for
 * convert_to_imax.sh, not a "photo" the user is meant to browse).
 */
object GalleryPublisher {

    fun publishImage(context: Context, file: File): Uri? =
        publish(context, file, "image/jpeg", MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "Pictures/IRMAX")

    fun publishVideo(context: Context, file: File): Uri? =
        publish(context, file, "video/mp4", MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "Movies/IRMAX")

    private fun publish(context: Context, file: File, mimeType: String, collection: Uri, relativePath: String): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val uri = resolver.insert(collection, values)
        if (uri == null) {
            Log.e(TAG, "MediaStore insert failed for ${file.name}")
            return null
        }

        return runCatching {
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            Log.i(TAG, "Published ${file.name} to $relativePath")
            uri
        }.onFailure {
            Log.e(TAG, "Failed to publish ${file.name}", it)
            resolver.delete(uri, null, null)
        }.getOrNull()
    }
}
