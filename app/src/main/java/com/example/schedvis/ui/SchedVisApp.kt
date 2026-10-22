package com.example.schedvis.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.schedvis.scan.API_KEY_PLACEHOLDER
import com.example.schedvis.scan.API_URL_PLACEHOLDER
import com.example.schedvis.scan.ApiSettings
import com.example.schedvis.scan.MODEL_PLACEHOLDER
import com.example.schedvis.scan.ScanScreen
import com.example.schedvis.scan.ScanState

@Composable
fun SchedVisApp() {
    // State lives here so each tab keeps its inputs when you switch back and forth.
    val cpuState = remember { CpuState() }
    val pageState = remember { PageState() }
    val scanState = remember { ScanState() }
    val context = LocalContext.current
    val settings = remember { ApiSettings(context.applicationContext) }
    var selectedTab by remember { mutableStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    val tabs = listOf("CPU Scheduling", "Page Replacement", "Scan")

    Scaffold { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "OS SchedVis",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Visualize CPU scheduling and page replacement",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    TextButton(onClick = { showSettings = true }) { Text("Settings") }
                }
            }

            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTab) {
                0 -> CpuSchedulingScreen(cpuState)
                1 -> PageReplacementScreen(pageState)
                else -> ScanScreen(
                    scanState,
                    settings,
                    onOpenSettings = { showSettings = true },
                    onSolve = { scanned ->
                        if (scanned.kind == "page") {
                            pageState.applyScan(scanned)
                            selectedTab = 1
                        } else {
                            cpuState.applyScan(scanned)
                            selectedTab = 0
                        }
                    }
                )
            }
        }
    }

    if (showSettings) {
        SettingsDialog(settings) { showSettings = false }
    }
}

// Vision API details. Placeholders are shown as hints and stored only until replaced.
@Composable
private fun SettingsDialog(settings: ApiSettings, onDismiss: () -> Unit) {
    fun initial(value: String, placeholder: String) = if (value == placeholder) "" else value
    var url by remember { mutableStateOf(initial(settings.url, API_URL_PLACEHOLDER)) }
    var model by remember { mutableStateOf(initial(settings.model, MODEL_PLACEHOLDER)) }
    var key by remember { mutableStateOf(initial(settings.apiKey, API_KEY_PLACEHOLDER)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Vision API settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Any OpenAI-compatible chat-completions API with image support works " +
                            "(for example OpenAI, Gemini, OpenRouter or Groq). Stored only on this device.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text("API URL") }, placeholder = { Text(API_URL_PLACEHOLDER) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = model, onValueChange = { model = it },
                    label = { Text("Model") }, placeholder = { Text(MODEL_PLACEHOLDER) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = key, onValueChange = { key = it },
                    label = { Text("API key") }, placeholder = { Text(API_KEY_PLACEHOLDER) },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                settings.save(url, model, key)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    settings.clear()
                    onDismiss()
                }) { Text("Clear") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
