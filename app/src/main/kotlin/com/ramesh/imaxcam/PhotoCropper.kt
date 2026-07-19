package com.ramesh.imaxcam

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import androidx.exifinterface.media.ExifInterface
import android.util.Log
import java.io.File
import java.io.FileOutputStream

private const val TAG = "IRMAX-PhotoCropper"

object PhotoCropper {

    /** Crops [sourceFile] to [ratio], applies [filter], and bakes in the bottom-right watermark. */
    fun cropToImaxAndWatermark(context: Context, sourceFile: File, outputFile: File, ratio: ImaxRatio, filter: CameraFilter) {
        val orientation = runCatching { ExifInterface(sourceFile.path).rotationDegrees }.getOrDefault(0)

        var bitmap = BitmapFactory.decodeFile(sourceFile.path)
            ?: run { Log.e(TAG, "Failed to decode $sourceFile"); return }

        if (orientation != 0) {
            val matrix = Matrix().apply { postRotate(orientation.toFloat()) }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }

        val crop = imaxCropRect(bitmap.width.toFloat(), bitmap.height.toFloat(), ratio.value)
        val cropped = Bitmap.createBitmap(
            bitmap,
            crop.left.toInt(),
            crop.top.toInt(),
            crop.width.toInt(),
            crop.height.toInt()
        )

        val filtered = if (filter == CameraFilter.NONE) {
            cropped
        } else {
            Bitmap.createBitmap(cropped.width, cropped.height, Bitmap.Config.ARGB_8888).also { out ->
                val filterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    colorFilter = ColorMatrixColorFilter(androidColorMatrixFor(filter))
                }
                Canvas(out).drawBitmap(cropped, 0f, 0f, filterPaint)
            }
        }

        val watermark = BitmapFactory.decodeResource(context.resources, R.drawable.irmax_watermark)
        val canvas = Canvas(filtered)
        val wmWidth = filtered.width * WATERMARK_WIDTH_FRACTION
        val wmHeight = wmWidth * watermark.height / watermark.width
        val margin = filtered.width * WATERMARK_MARGIN_FRACTION
        val dst = RectF(
            filtered.width - wmWidth - margin,
            filtered.height - wmHeight - margin,
            filtered.width - margin,
            filtered.height - margin
        )
        val watermarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { alpha = (WATERMARK_ALPHA * 255).toInt() }
        canvas.drawBitmap(watermark, null, dst, watermarkPaint)

        FileOutputStream(outputFile).use { out ->
            filtered.compress(Bitmap.CompressFormat.JPEG, 100, out)
        }
        Log.i(TAG, "Saved ${outputFile.name}: ${filtered.width}x${filtered.height}, filter=$filter")
    }
}
