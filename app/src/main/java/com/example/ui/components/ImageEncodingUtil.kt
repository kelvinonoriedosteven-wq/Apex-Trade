package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

suspend fun encodeUriToCompactBase64(context: Context, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
        val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Unable to read selected image")
        val original = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
            ?: error("Invalid image format")
        val maxEdge = 480
        val scale = max(original.width, original.height).toFloat() / maxEdge.toFloat()
        val scaled = if (scale > 1f) {
            val w = (original.width / scale).roundToInt().coerceAtLeast(1)
            val h = (original.height / scale).roundToInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(original, w, h, true)
        } else {
            original
        }
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 68, out)
        val encoded = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        "data:image/jpeg;base64,$encoded"
    }
}

fun decodeBase64ToImageBitmap(base64String: String): ImageBitmap? {
    if (base64String.isBlank()) return null
    return try {
        val clean = base64String.substringAfter("base64,", base64String)
        val bytes = Base64.decode(clean, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}
