package com.example.schedvis.scan

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.schedvis.ui.CpuAlgorithm
import com.example.schedvis.ui.SectionTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val SETTINGS_MESSAGE =
    "Set your API URL, model and key in Settings (top right) before scanning."

// ==========================================
// STATE (kept outside the UI so the last scan survives tab switches)
// ==========================================

class ScanState {
    var preview by mutableStateOf<Bitmap?>(null)
    var lastPhoto by mutableStateOf<Uri?>(null)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var scanned by mutableStateOf<ScannedQuestion?>(null)
}

// The camera app writes the full-resolution photo here.
private fun cameraOutputUri(context: Context): Uri {
    val dir = File(context.cacheDir, "scans").apply { mkdirs() }
    val file = File(dir, "question.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

// ==========================================
// SCREEN
// ==========================================

@Composable
fun ScanScreen(
    state: ScanState,
    settings: ApiSettings,
    onOpenSettings: () -> Unit,
    onSolve: (ScannedQuestion) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cameraUri = remember { cameraOutputUri(context) }

    fun read(uri: Uri) {
        state.lastPhoto = uri
        scope.launch {
            state.loading = true
            state.error = null
            state.scanned = null
            try {
                val (bitmap, jpeg) = withContext(Dispatchers.IO) { loadPhoto(context, uri) }
                state.preview = bitmap
                if (!settings.isConfigured) throw ScanException(SETTINGS_MESSAGE)
                val extractor = OpenAiCompatibleExtractor(settings.url, settings.model, settings.apiKey)
                val reply = withContext(Dispatchers.IO) { extractor.extract(jpeg, SCAN_PROMPT) }
                val scanned = parseScannedQuestion(reply)
                if (scanned.kind == "unknown") {
                    throw ScanException(
                        "This does not look like a CPU scheduling or page replacement question." +
                                (if (scanned.notes.isNotBlank()) "\n${scanned.notes}" else "")
                    )
                }
                state.scanned = scanned
            } catch (e: ScanException) {
                state.error = e.message
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                state.error = "Something went wrong while reading the photo: ${e.message}"
            } finally {
                state.loading = false
            }
        }
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) read(cameraUri)
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { read(it) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionTitle("Scan a question")
        Text(
            "Take a photo of a CPU scheduling or page replacement exercise. The vision API reads the values, " +
                    "then the app solves it step by step.",
            style = MaterialTheme.typography.bodyMedium
        )
        if (!settings.isConfigured) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(SETTINGS_MESSAGE, style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = onOpenSettings) { Text("Open Settings") }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { takePicture.launch(cameraUri) },
                enabled = !state.loading,
                modifier = Modifier.weight(1f)
            ) { Text("Take photo") }
            OutlinedButton(
                onClick = {
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                enabled = !state.loading,
                modifier = Modifier.weight(1f)
            ) { Text("From gallery") }
        }

        state.preview?.let { bitmap ->
            Image(
                bitmap.asImageBitmap(),
                contentDescription = "Photo of the question",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)
            )
        }

        if (state.loading) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text("Reading the question…")
            }
        }

        state.error?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error)
            state.lastPhoto?.let { uri ->
                if (!state.loading) OutlinedButton(onClick = { read(uri) }) { Text("Try again") }
            }
        }

        state.scanned?.let { scanned ->
            HorizontalDivider()
            ScanSummary(scanned)
            Button(onClick = { onSolve(scanned) }, modifier = Modifier.fillMaxWidth()) {
                Text("Solve step by step")
            }
            Text(
                "All values can be edited on the next screen if something was read wrongly.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun algorithmName(scanned: ScannedQuestion): String =
    if (scanned.kind == "page") scanned.algorithm ?: "FIFO (not stated)"
    else CpuAlgorithm.entries.firstOrNull { it.name == scanned.algorithm }?.title ?: "FCFS (not stated)"

// What was read from the photo, so the user can check it before solving.
@Composable
private fun ScanSummary(scanned: ScannedQuestion) {
    SectionTitle(if (scanned.kind == "page") "Page replacement question" else "CPU scheduling question")
    Text("Algorithm: ${algorithmName(scanned)}", style = MaterialTheme.typography.bodyMedium)

    if (scanned.kind == "page") {
        Text("Reference string: ${scanned.references.joinToString(" ")}", style = MaterialTheme.typography.bodyMedium)
        Text("Frames: ${scanned.frames ?: "not stated"}", style = MaterialTheme.typography.bodyMedium)
    } else {
        scanned.processes.forEachIndexed { i, p ->
            val parts = listOfNotNull(
                "arrival ${p.arrival ?: 0}",
                "burst ${p.burst ?: "?"}",
                p.priority?.let { "priority $it" },
                p.queue?.let { "queue $it" }
            )
            Text("P${i + 1}: ${parts.joinToString(", ")}", style = MaterialTheme.typography.bodyMedium)
        }
        scanned.quantum?.let { Text("Time quantum: $it", style = MaterialTheme.typography.bodyMedium) }
        scanned.queues.forEachIndexed { i, q ->
            val detail = if (q.quantum != null) "${q.policy} (q = ${q.quantum})" else q.policy.name
            Text("Q${i + 1}: $detail", style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (scanned.notes.isNotBlank()) {
        Text("Notes: ${scanned.notes}", style = MaterialTheme.typography.bodySmall)
    }
}
