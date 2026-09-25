package com.srideep.pocketforge.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** A photo prepared for the model: a local JPEG plus the size the vision encoder should see. */
data class PreparedImage(val file: File, val visionWidth: Int, val visionHeight: Int) {

    /**
     * MNN's inline image reference. Without the `<hw>` hint MNN squashes every image to the
     * export's square `image_size`, which distorts a portrait photo of a sketch badly enough
     * that the model misreads its layout.
     */
    val mnnTag: String get() = "<img><hw>$visionHeight,$visionWidth</hw>${file.absolutePath}</img>"
}

/**
 * Turns a camera capture or gallery pick into something MNN can read.
 *
 * MNN decodes with its own `imread`, which cannot read a content:// Uri and ignores EXIF
 * rotation, and a 12 MP photo is wasted decode time when the encoder sees ~0.3 MP. So the
 * image is decoded here (ImageDecoder applies EXIF orientation), downscaled, and written
 * to app storage as a plain JPEG.
 */
object ImageInput {

    /**
     * Long side the vision encoder works at. A multiple of both 28 and 32, the patch
     * alignments of the Qwen VL encoders, so MNN does not round it. At 672x504 a Qwen3.5
     * encoder produces ~330 image tokens: enough to read handwriting, cheap to prefill.
     */
    private const val VISION_LONG_SIDE = 672

    /** Stored copy: larger than the encoder needs, so the chat thumbnail stays sharp. */
    private const val STORED_LONG_SIDE = 1280

    fun prepare(context: Context, uri: Uri, outDir: File): PreparedImage {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val (w, h) = fit(info.size.width, info.size.height, STORED_LONG_SIDE)
            decoder.setTargetSize(w, h)
            // Software bitmaps only: a hardware bitmap cannot be compressed back to JPEG.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        outDir.mkdirs()
        val file = File(outDir, "photo_" + System.currentTimeMillis() + ".jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        val (vw, vh) = fit(bitmap.width, bitmap.height, VISION_LONG_SIDE)
        bitmap.recycle()
        return PreparedImage(file, vw, vh)
    }

    /** Scales (width, height) so the longer side is at most [longSide], keeping aspect. */
    private fun fit(width: Int, height: Int, longSide: Int): Pair<Int, Int> {
        val longest = max(width, height)
        if (longest <= longSide) return width to height
        val scale = longSide.toFloat() / longest
        return (width * scale).roundToInt().coerceAtLeast(1) to
            (height * scale).roundToInt().coerceAtLeast(1)
    }
}
