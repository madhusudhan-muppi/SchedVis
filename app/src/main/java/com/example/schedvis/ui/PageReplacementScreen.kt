package com.example.schedvis.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.schedvis.engine.PageResult
import com.example.schedvis.engine.fifo
import com.example.schedvis.engine.lru
import com.example.schedvis.engine.optimal
import com.example.schedvis.scan.ScannedQuestion
import java.util.Locale

val PAGE_ALGORITHMS = listOf("FIFO", "LRU", "Optimal")
const val MAX_FRAMES = 10
const val MAX_REFERENCES = 40
const val SAMPLE_REFERENCE = "7 0 1 2 0 3 0 4 2 3 0 3 2 1 2 0 1 7 0 1"

private fun runPageAlgorithm(name: String, pages: List<Int>, frames: Int): PageResult =
    when (name) {
        "LRU" -> lru(pages, frames)
        "Optimal" -> optimal(pages, frames)
        else -> fifo(pages, frames)
    }

// ==========================================
// STATE + LOGIC
// ==========================================

class PageState {
    var referenceText by mutableStateOf(SAMPLE_REFERENCE)
    var framesText by mutableStateOf("3")
    var algorithm by mutableStateOf(PAGE_ALGORITHMS[0])

    var error by mutableStateOf<String?>(null)
    var result by mutableStateOf<PageResult?>(null)
    var playback by mutableStateOf<PlaybackState?>(null)
    var resultTitle by mutableStateOf("")
    var simulatedAlgorithm by mutableStateOf(PAGE_ALGORITHMS[0])
    var comparison by mutableStateOf<List<Pair<String, Int>>>(emptyList())

    fun selectAlgorithm(name: String) {
        algorithm = name
        result = null
        playback = null
        error = null
    }

    fun loadSample() {
        referenceText = SAMPLE_REFERENCE
        framesText = "3"
        error = null
        result = null
        playback = null
    }

    // Fills the inputs from a scanned photo and runs the simulation.
    fun applyScan(scan: ScannedQuestion) {
        selectAlgorithm(PAGE_ALGORITHMS.firstOrNull { it.equals(scan.algorithm, ignoreCase = true) } ?: PAGE_ALGORITHMS[0])
        referenceText = scan.references.joinToString(" ")
        scan.frames?.let { framesText = it.toString() }
        simulate()
    }

    fun simulate() {
        error = null
        result = null
        playback = null

        val tokens = referenceText.split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) {
            error = "Enter a page reference string, for example: 7 0 1 2 0 3"
            return
        }
        if (tokens.size > MAX_REFERENCES) {
            error = "Use at most $MAX_REFERENCES page references."
            return
        }
        val pages = tokens.mapNotNull { it.toIntOrNull() }
        if (pages.size != tokens.size || pages.any { it > 99 }) {
            error = "Page numbers must be whole numbers from 0 to 99."
            return
        }

        val frames = framesText.toIntOrNull()
        if (frames == null || frames !in 1..MAX_FRAMES) {
            error = "Enter a number of frames between 1 and $MAX_FRAMES."
            return
        }

        val newResult = runPageAlgorithm(algorithm, pages, frames)
        result = newResult
        playback = PlaybackState(newResult.steps.size)
        simulatedAlgorithm = algorithm
        resultTitle = "$algorithm with $frames frames"
        comparison = PAGE_ALGORITHMS.map { it to runPageAlgorithm(it, pages, frames).totalFaults }
    }
}

// ==========================================
// SCREEN
// ==========================================

@Composable
fun PageReplacementScreen(state: PageState) {
    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionTitle("Algorithm")
        OptionSelector(PAGE_ALGORITHMS, state.algorithm) { state.selectAlgorithm(it) }

        OutlinedTextField(
            value = state.referenceText,
            onValueChange = { new ->
                if (new.all { it.isDigit() || it == ' ' || it == ',' }) state.referenceText = new
            },
            label = { Text("Page reference string") },
            supportingText = { Text("Separate pages with spaces or commas") },
            modifier = Modifier.fillMaxWidth()
        )

        NumberField("Number of frames (1-$MAX_FRAMES)", state.framesText, Modifier.fillMaxWidth()) {
            state.framesText = it
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { state.loadSample() }, modifier = Modifier.weight(1f)) {
                Text("Load sample")
            }
            Button(onClick = { state.simulate() }, modifier = Modifier.weight(1f)) {
                Text("Simulate")
            }
        }

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        val result = state.result
        val playback = state.playback
        if (result != null && playback != null) {
            PageResults(state.resultTitle, state.simulatedAlgorithm, result, playback, state.comparison)
        }
    }
}

@Composable
private fun PageResults(
    title: String,
    algorithm: String,
    result: PageResult,
    playback: PlaybackState,
    comparison: List<Pair<String, Int>>
) {
    val index = playback.index
    val shown = result.steps.take(index + 1)

    HorizontalDivider()
    SectionTitle("Memory frames: $title")
    PlaybackControls(playback)
    FrameTable(result.steps, visibleCount = index + 1, highlightIndex = if (playback.atEnd) null else index)
    Text(
        "F = page fault (new page tinted red), H = hit",
        style = MaterialTheme.typography.bodySmall
    )
    result.steps.getOrNull(index)?.let { step ->
        StepMessage("Request #${index + 1}: page ${step.page}", step.message)
    }
    Text(
        "So far: ${shown.count { it.isFault }} faults, ${shown.count { !it.isFault }} hits",
        style = MaterialTheme.typography.bodyMedium
    )
    StepLog(shown.mapIndexed { i, s -> "#${i + 1} (page ${s.page}): ${s.message}" })

    if (!playback.atEnd) {
        Text(
            "The totals appear at the end of the simulation. Tap \"Skip to result\" to see them now.",
            style = MaterialTheme.typography.bodySmall
        )
        return
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard("Page faults", result.totalFaults.toString(), Modifier.weight(1f))
        MetricCard("Hits", (result.steps.size - result.totalFaults).toString(), Modifier.weight(1f))
        MetricCard(
            "Hit ratio",
            String.format(Locale.US, "%.0f%%", result.hitRatio * 100),
            Modifier.weight(1f)
        )
    }

    SectionTitle("Page faults for each algorithm")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        comparison.forEach { (name, faults) ->
            MetricCard(name, faults.toString(), Modifier.weight(1f), highlighted = name == algorithm)
        }
    }
}