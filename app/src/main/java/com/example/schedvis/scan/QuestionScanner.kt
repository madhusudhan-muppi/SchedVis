package com.example.schedvis.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

// Longest side of the image sent to the API. Larger photos only cost more and are not read better.
private const val MAX_IMAGE_SIDE = 1568

// Sends a JPEG photo plus the prompt to a vision model and returns the model's text reply.
// Swap in another implementation to support a provider with a different request format.
fun interface VisionExtractor {
    fun extract(jpeg: ByteArray, prompt: String): String
}

// Works with any OpenAI-compatible chat-completions endpoint
// (OpenAI, Gemini's OpenAI-compatible endpoint, OpenRouter, Groq, ...).
class OpenAiCompatibleExtractor(
    private val url: String,
    private val model: String,
    private val apiKey: String
) : VisionExtractor {

    override fun extract(jpeg: ByteArray, prompt: String): String {
        val imageUrl = "data:image/jpeg;base64," + Base64.encodeToString(jpeg, Base64.NO_WRAP)
        val content = JSONArray()
            .put(JSONObject().put("type", "text").put("text", prompt))
            .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", imageUrl)))
        val body = JSONObject()
            .put("model", model)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))

        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw ScanException("The API URL in Settings is not valid.")
        }
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 20_000
            connection.readTimeout = 120_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.outputStream.use { it.write(body.toString().toByteArray()) }

            val code = connection.responseCode
            if (code !in 200..299) {
                val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw ScanException(httpErrorMessage(code, errorText))
            }
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            return replyText(response)
        } catch (e: IOException) {
            throw ScanException("Could not reach the API. Check your internet connection and the API URL.")
        } finally {
            connection.disconnect()
        }
    }

    private fun httpErrorMessage(code: Int, errorText: String): String {
        val detail = try {
            JSONObject(errorText).optJSONObject("error")?.optString("message").orEmpty()
        } catch (e: Exception) {
            ""
        }
        val reason = when (code) {
            401, 403 -> "The API key was rejected. Check it in Settings."
            404 -> "The API URL or model was not found. Check them in Settings."
            429 -> "Too many requests or no credit left on this API key. Try again later."
            in 500..599 -> "The API service had a problem. Try again in a moment."
            else -> "The API returned an error ($code)."
        }
        return if (detail.isNotBlank()) "$reason\n$detail" else reason
    }

    // choices[0].message.content is usually a string, but some providers return a list of parts.
    private fun replyText(response: String): String {
        val message = try {
            JSONObject(response).getJSONArray("choices").getJSONObject(0).getJSONObject("message")
        } catch (e: Exception) {
            throw ScanException("The API reply was not in the expected chat-completions format.")
        }
        return when (val content = message.opt("content")) {
            is String -> content
            is JSONArray -> (0 until content.length()).joinToString("") {
                content.optJSONObject(it)?.optString("text").orEmpty()
            }
            else -> throw ScanException("The API reply was empty. The model may not support images.")
        }
    }
}

// ==========================================
// IMAGE LOADING
// ==========================================

// Loads a photo, applies its camera rotation, shrinks it and encodes it as JPEG.
// Returns the bitmap too, for the preview.
fun loadPhoto(context: Context, uri: Uri): Pair<Bitmap, ByteArray> {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw ScanException("Could not open the photo.")

    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_IMAGE_SIDE) sample *= 2
    val decoded = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: throw ScanException("Could not open the photo.")

    val rotation = try {
        resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
    } catch (e: Exception) {
        0
    }
    val scale = minOf(1f, MAX_IMAGE_SIDE.toFloat() / maxOf(decoded.width, decoded.height))
    val matrix = Matrix().apply {
        postScale(scale, scale)
        postRotate(rotation.toFloat())
    }
    val bitmap = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
    return bitmap to out.toByteArray()
}

private val ExifInterface.rotationDegrees: Int
    get() = when (getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }
