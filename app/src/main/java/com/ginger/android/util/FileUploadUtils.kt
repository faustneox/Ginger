package com.ginger.android.util

import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException

suspend fun createMultipartFromUri(context: Context, uri: Uri, partName: String = "file"): MultipartBody.Part = withContext(Dispatchers.IO) {
    val contentResolver = context.contentResolver
    val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"

    val bytes = if (mimeType.startsWith("image/")) {
        // Compress images before upload to save bandwidth
        compressImageToJpegBytes(context, uri, maxWidth = 1280, maxHeight = 1280, quality = 80)
    } else {
        val inputStream = contentResolver.openInputStream(uri) ?: throw IOException("Unable to open input stream for $uri")
        inputStream.use { it.readBytes() }
    }

    val filename = queryName(context, uri) ?: uri.lastPathSegment ?: "file"
    val reqBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
    MultipartBody.Part.createFormData(partName, filename, reqBody)
}

/**
 * Compress image located at [uri] into JPEG bytes with given target dimensions and quality.
 */
suspend fun compressImageToJpegBytes(context: Context, uri: Uri, maxWidth: Int = 1280, maxHeight: Int = 1280, quality: Int = 80): ByteArray = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri).use { stream ->
        BitmapFactory.decodeStream(stream, null, options)
    }

    val (originalW, originalH) = options.outWidth to options.outHeight
    var inSampleSize = 1
    if (originalH > maxHeight || originalW > maxWidth) {
        val halfH = originalH / 2
        val halfW = originalW / 2
        while ((halfH / inSampleSize) >= maxHeight && (halfW / inSampleSize) >= maxWidth) {
            inSampleSize *= 2
        }
    }

    val decodeOptions = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize; inPreferredConfig = Bitmap.Config.ARGB_8888 }
    val bitmap: Bitmap = resolver.openInputStream(uri).use { stream ->
        BitmapFactory.decodeStream(stream, null, decodeOptions) ?: throw IOException("Failed to decode bitmap from $uri")
    }

    // Optionally scale to exact bounds while preserving aspect ratio
    val (w, h) = bitmap.width to bitmap.height
    var scaled = bitmap
    if (w > maxWidth || h > maxHeight) {
        val ratio = Math.min(maxWidth.toFloat() / w.toFloat(), maxHeight.toFloat() / h.toFloat())
        val targetW = (w * ratio).toInt()
        val targetH = (h * ratio).toInt()
        scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        if (scaled != bitmap) bitmap.recycle()
    }

    val baos = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
    baos.toByteArray()
}

private fun queryName(context: Context, uri: Uri): String? {
    var cursor: Cursor? = null
    return try {
        cursor = context.contentResolver.query(uri, null, null, null, null)
        if (cursor != null && cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index != -1) cursor.getString(index) else null
        } else null
    } finally {
        cursor?.close()
    }
}
