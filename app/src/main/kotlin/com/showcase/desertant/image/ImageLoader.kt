package com.showcase.desertant.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

object ImageLoader {

    /**
     * Decode [uri] as a software ARGB_8888 bitmap (the layout Eye and Moderator
     * read), downscaled so its long edge is at most [maxDimension]. The models
     * resize internally, so a smaller input only saves memory and copying.
     */
    suspend fun load(context: Context, uri: Uri, maxDimension: Int = 1280): Bitmap = withContext(Dispatchers.IO) {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longEdge = max(info.size.width, info.size.height)
                if (longEdge > maxDimension) {
                    val scale = maxDimension.toDouble() / longEdge
                    decoder.setTargetSize(
                        max(1, (info.size.width * scale).toInt()),
                        max(1, (info.size.height * scale).toInt()),
                    )
                }
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDimension) sample *= 2
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: error("This image could not be read.")
        }
        val argb = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap
            else bitmap.copy(Bitmap.Config.ARGB_8888, false).also { bitmap.recycle() }
        val longEdge = max(argb.width, argb.height)
        if (longEdge <= maxDimension) argb
        else {
            val scale = min(1.0, maxDimension.toDouble() / longEdge)
            Bitmap.createScaledBitmap(argb, (argb.width * scale).toInt(), (argb.height * scale).toInt(), true)
        }
    }
}
