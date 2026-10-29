package com.showcase.desertant.privacy

import ai.desertant.eye.Box
import ai.desertant.eye.Options
import ai.desertant.eye.Part
import ai.desertant.eye.Tags
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.showcase.desertant.app
import com.showcase.desertant.core.ModelKind
import com.showcase.desertant.photos.mightBeIntimate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** A region of the photo in 0..1 coordinates. */
data class Region(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** What Burrow saw in a photo that's about to be shared. */
data class ShareReport(
    val bitmap: Bitmap,
    val faces: List<Region>,
    /** "an ID card", "a bank card or receipt"… if something private is in frame. */
    val sensitive: String?,
    val intimate: Boolean,
    val hasLocation: Boolean,
    /** "Samsung SM-F966B · Tue 28 Oct 20:42", if the file carries camera data. */
    val cameraData: String?,
    /** Eye's best crop of the main subject, if it's meaningfully tighter than the photo. */
    val crop: Region?,
)

object ShareCheck {
    private const val MAX_SIDE = 2048

    suspend fun check(context: Context, uri: Uri): ShareReport = withContext(Dispatchers.IO) {
        val app = context.app
        val bitmap = decode(context, uri)

        // Location is only readable with ACCESS_MEDIA_LOCATION and the original bytes; otherwise Android
        // hides it from us — but not necessarily from the app the photo is shared to.
        val exif = runCatching {
            val original = runCatching { MediaStore.setRequireOriginal(uri) }.getOrDefault(uri)
            context.contentResolver.openInputStream(original)?.use { ExifInterface(it) }
        }.getOrNull() ?: runCatching { context.contentResolver.openInputStream(uri)?.use { ExifInterface(it) } }.getOrNull()
        val hasLocation = exif?.getLatLong(FloatArray(2)) == true
        val camera = listOfNotNull(
            listOfNotNull(exif?.getAttribute(ExifInterface.TAG_MAKE), exif?.getAttribute(ExifInterface.TAG_MODEL)).joinToString(" ").ifBlank { null },
            exif?.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL),
        ).joinToString(" · ").ifBlank { null }

        app.hub.ensureReady(ModelKind.EYE)
        val a = app.hub.eye.analyze(bitmap, Options(parts = setOf(Part.PHOTO, Part.FACES), maxFaces = 16))
        val w = a.width.toFloat().coerceAtLeast(1f)
        val h = a.height.toFloat().coerceAtLeast(1f)
        fun Box.region(): Region {
            // Eye reports boxes in the analyzed image's pixels; accept normalized ones too.
            val norm = x1 <= 1.0 && y1 <= 1.0
            return if (norm) Region(x0.toFloat(), y0.toFloat(), x1.toFloat(), y1.toFloat())
            else Region((x0 / w).toFloat(), (y0 / h).toFloat(), (x1 / w).toFloat(), (y1 / h).toFloat())
        }

        val sensitive = when {
            a.has(Tags.ID_CARD) || a.has(Tags.IDENTITY_DOCUMENT) -> "an ID or bank card"
            a.has(Tags.RECEIPT) -> "a receipt"
            a.has(Tags.MEDICAL_REFERENCE) -> "something medical"
            a.has(Tags.DOCUMENT) && a.has(Tags.SENSITIVE) -> "a private document"
            a.has(Tags.SCREENSHOT) && a.has(Tags.SENSITIVE) -> "personal details on a screen"
            else -> null
        }
        val intimate = mightBeIntimate(a) && runCatching {
            app.hub.ensureReady(ModelKind.MODERATOR)
            app.hub.moderator.analyze(bitmap, ai.desertant.moderator.Options()).isNSFW
        }.getOrDefault(false)
        val crop = a.bestCrop(bitmap.width.toDouble() / bitmap.height)?.region()
            ?.takeIf { (it.right - it.left) * (it.bottom - it.top) < 0.85f }

        app.ledger.add(LedgerEntry.PHOTOS_CHECKED_BEFORE_SHARING)
        ShareReport(bitmap, a.faces.orEmpty().map { it.box.region() }, sensitive, intimate, hasLocation, camera, crop)
    }

    /**
     * Make the copy that leaves the phone: optionally cropped and with faces
     * pixelated. Re-encoding writes no camera data or location at all.
     */
    suspend fun cleanCopy(context: Context, report: ShareReport, crop: Boolean, blurFaces: Boolean): Uri = withContext(Dispatchers.IO) {
        val src = report.bitmap
        var out = src.copy(Bitmap.Config.ARGB_8888, true)
        if (blurFaces) {
            val canvas = Canvas(out)
            report.faces.forEach { f -> pixelate(src, canvas, f.toRect(src.width, src.height, grow = 0.18f)) }
        }
        val c = report.crop
        if (crop && c != null) {
            val r = c.toRect(out.width, out.height)
            out = Bitmap.createBitmap(out, r.left, r.top, r.width().coerceAtLeast(1), r.height().coerceAtLeast(1))
        }
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        // Old copies go as soon as a new one is made; the share target has already read them.
        dir.listFiles { f -> f.name.startsWith("burrow-clean-") }?.forEach { it.delete() }
        val file = File(dir, "burrow-clean-${System.currentTimeMillis()}.jpg")
        file.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    private fun pixelate(src: Bitmap, canvas: Canvas, r: Rect) {
        if (r.width() <= 0 || r.height() <= 0) return
        val face = Bitmap.createBitmap(src, r.left, r.top, r.width(), r.height())
        val tiny = Bitmap.createScaledBitmap(face, (r.width() / 18).coerceAtLeast(1), (r.height() / 18).coerceAtLeast(1), true)
        canvas.drawBitmap(tiny, null, r, Paint().apply { isFilterBitmap = false })
        face.recycle()
        tiny.recycle()
    }

    private fun Region.toRect(w: Int, h: Int, grow: Float = 0f): Rect {
        val gx = (right - left) * grow
        val gy = (bottom - top) * grow
        return Rect(
            ((left - gx) * w).toInt().coerceIn(0, w - 1),
            ((top - gy) * h).toInt().coerceIn(0, h - 1),
            ((right + gx) * w).toInt().coerceIn(1, w),
            ((bottom + gy) * h).toInt().coerceIn(1, h),
        )
    }

    private fun decode(context: Context, uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val side = maxOf(info.size.width, info.size.height)
            if (side > MAX_SIDE) decoder.setTargetSampleSize(Integer.highestOneBit(side / MAX_SIDE).coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }.let { if (it.config == Bitmap.Config.ARGB_8888) it else it.copy(Bitmap.Config.ARGB_8888, false) }
    }
}
