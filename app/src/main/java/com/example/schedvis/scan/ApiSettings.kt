package com.example.schedvis.scan

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// Placeholders shown until the user enters their own provider details in Settings.
const val API_URL_PLACEHOLDER = "https://YOUR_PROVIDER/v1/chat/completions"
const val MODEL_PLACEHOLDER = "YOUR_VISION_MODEL"
const val API_KEY_PLACEHOLDER = "YOUR_API_KEY"

// Vision API connection details, stored on the device only.
// Any OpenAI-compatible chat-completions endpoint that accepts images works.
// Values are mirrored in Compose state so screens update as soon as they are saved.
class ApiSettings(context: Context) {
    private val prefs = context.getSharedPreferences("api_settings", Context.MODE_PRIVATE)

    var url by mutableStateOf(prefs.getString(KEY_URL, null) ?: API_URL_PLACEHOLDER)
        private set
    var model by mutableStateOf(prefs.getString(KEY_MODEL, null) ?: MODEL_PLACEHOLDER)
        private set
    var apiKey by mutableStateOf(prefs.getString(KEY_API_KEY, null) ?: API_KEY_PLACEHOLDER)
        private set

    val isConfigured: Boolean
        get() = url != API_URL_PLACEHOLDER && model != MODEL_PLACEHOLDER && apiKey != API_KEY_PLACEHOLDER

    // Blank values fall back to the placeholders.
    fun save(newUrl: String, newModel: String, newKey: String) {
        url = newUrl.trim().ifEmpty { API_URL_PLACEHOLDER }
        model = newModel.trim().ifEmpty { MODEL_PLACEHOLDER }
        apiKey = newKey.trim().ifEmpty { API_KEY_PLACEHOLDER }
        prefs.edit()
            .putString(KEY_URL, url)
            .putString(KEY_MODEL, model)
            .putString(KEY_API_KEY, apiKey)
            .apply()
    }

    fun clear() = save("", "", "")

    private companion object {
        const val KEY_URL = "url"
        const val KEY_MODEL = "model"
        const val KEY_API_KEY = "api_key"
    }
}
