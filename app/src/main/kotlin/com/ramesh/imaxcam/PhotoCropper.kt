package com.ramesh.imaxcam

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import androidx.exifinterface.media.ExifInterface
import android.util.Log
import java.io.File
import java.io.FileOutputStream

private const val TAG = "IRMAX-PhotoCropper"

/** Watermark sized relative to the cropped frame width, matching the live viewfinder's proportions. */
private const val WATERMARK_WIDTH_FRACTION = 0.10f
private const val WATERMARK_MARGIN_FRACTION = 0.025f
private const val WATERMARK_ALPHA = 0.3f

object PhotoCropper {

    /** Crops [sourceFile] to 1.43:1 (trimming whichever dimension is in excess) and bakes in the bottom-right watermark. */
    fun cropToImaxAndWatermark(context: Context, sourceFile: File, outputFile: File) {
        val orientation = runCatching { ExifInterface(sourceFile.path).rotationDegrees }.getOrDefault(0)

        var bitmap = BitmapFactory.decodeFile(sourceFile.path)
            ?: run { Log.e(TAG, "Failed to decode $sourceFile"); return }

        if (orientation != 0) {
            val matrix = Matrix().apply { postRotate(orientation.toFloat()) }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }

        val crop = imaxCropRect(bitmap.width.toFloat(), bitmap.height.toFloat())
        val cropped = Bitmap.createBitmap(
            bitmap,
            crop.left.toInt(),
            crop.top.toInt(),
            crop.width.toInt(),
            crop.height.toInt()
        )

        val watermark = BitmapFactory.decodeResource(context.resources, R.drawable.irmax_watermark)
        val canvas = Canvas(cropped)
        val wmWidth = cropped.width * WATERMARK_WIDTH_FRACTION
        val wmHeight = wmWidth * watermark.height / watermark.width
        val margin = cropped.width * WATERMARK_MARGIN_FRACTION
        val dst = RectF(
            cropped.width - wmWidth - margin,
            cropped.height - wmHeight - margin,
            cropped.width - margin,
            cropped.height - margin
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { alpha = (WATERMARK_ALPHA * 255).toInt() }
        canvas.drawBitmap(watermark, null, dst, paint)

        FileOutputStream(outputFile).use { out ->
            cropped.compress(Bitmap.CompressFormat.JPEG, 100, out)
        }
        Log.i(TAG, "Saved ${outputFile.name}: ${cropped.width}x${cropped.height}")
    }
}
