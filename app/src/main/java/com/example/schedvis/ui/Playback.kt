package com.example.schedvis.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.schedvis.engine.GanttSlot
import com.example.schedvis.engine.Process
import com.example.schedvis.engine.SimStep
import kotlinx.coroutines.delay

// Time between two steps at 1x speed.
private const val STEP_DELAY_MS = 1400L
private val SPEEDS = listOf("0.5x" to 0.5f, "1x" to 1f, "2x" to 2f, "4x" to 4f)

// ==========================================
// PLAYBACK STATE
// ==========================================

// Which step of a simulation is on screen. Starts playing from the first step.
class PlaybackState(val total: Int) {
    var index by mutableIntStateOf(0)
    var playing by mutableStateOf(total > 1)
    var speed by mutableFloatStateOf(1f)

    val atEnd get() = index >= total - 1

    fun next() {
        if (index < total - 1) index++
        if (atEnd) playing = false
    }

    fun prev() {
        playing = false
        if (index > 0) index--
    }

    fun restart() {
        index = 0
        playing = total > 1
    }

    fun skipToEnd() {
        index = (total - 1).coerceAtLeast(0)
        playing = false
    }

    fun togglePlay() {
        if (playing) {
            playing = false
        } else {
            if (atEnd) index = 0
            playing = total > 1
        }
    }
}

// ==========================================
// CONTROLS
// ==========================================

@Composable
fun PlaybackControls(state: PlaybackState) {
    LaunchedEffect(state, state.playing, state.index) {
        if (state.playing && !state.atEnd) {
            delay((STEP_DELAY_MS / state.speed).toLong())
            state.next()
        }
    }

    val padding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { state.restart() }, modifier = Modifier.weight(1f), contentPadding = padding
            ) { Text("Restart", maxLines = 1) }
            OutlinedButton(
                onClick = { state.prev() }, enabled = state.index > 0,
                modifier = Modifier.weight(1f), contentPadding = padding
            ) { Text("◀ Prev", maxLines = 1) }
            Button(
                onClick = { state.togglePlay() }, modifier = Modifier.weight(1f), contentPadding = padding
            ) { Text(if (state.playing) "Pause" else "Play", maxLines = 1) }
            OutlinedButton(
                onClick = { state.playing = false; state.next() }, enabled = !state.atEnd,
                modifier = Modifier.weight(1f), contentPadding = padding
            ) { Text("Next ▶", maxLines = 1) }
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Step ${state.index + 1} / ${state.total}", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.weight(1f)) {
                OptionSelector(
                    SPEEDS.map { it.first },
                    SPEEDS.first { it.second == state.speed }.first,
                    perRow = SPEEDS.size
                ) { label -> state.speed = SPEEDS.first { it.first == label }.second }
            }
        }
        Button(
            onClick = { state.skipToEnd() }, enabled = !state.atEnd, modifier = Modifier.fillMaxWidth()
        ) { Text("Skip to result") }
    }
}

// The explanation for the current step, highlighted.
@Composable
fun StepMessage(title: String, message: String) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// ==========================================
// CPU SIMULATION PANEL
// ==========================================

// Time left for each process after everything in the timeline before `time` has run.
private fun remainingAt(processes: List<Process>, timeline: List<GanttSlot>, time: Int): Map<String, Int> =
    processes.associate { p ->
        val ran = timeline.filter { it.pid == p.id }
            .sumOf { (minOf(it.end, time) - it.start).coerceAtLeast(0) }
        p.id to p.burst - ran
    }

@Composable
fun CpuSimPanel(
    steps: List<SimStep>,
    index: Int,
    processes: List<Process>,
    timeline: List<GanttSlot>,
    queueLabels: List<String>
) {
    val step = steps[index]
    val order = processes.map { it.id }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("t = ${step.time}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("CPU:", style = MaterialTheme.typography.titleMedium)
        if (step.running != null) {
            ProcessBadge(step.running, colorFor(step.running, order))
        } else {
            Text("free", style = MaterialTheme.typography.titleMedium, color = IdleColor)
        }
    }

    step.queues.forEachIndexed { i, queue ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                queueLabels.getOrElse(i) { "Q${i + 1}" },
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.width(96.dp)
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (queue.isEmpty()) Text("empty", color = IdleColor)
                queue.forEach { pid -> ProcessBadge(pid, colorFor(pid, order)) }
            }
        }
    }

    val remaining = remainingAt(processes, timeline, step.time)
    Text(
        "Time left: " + processes.joinToString("   ") { p ->
            val left = remaining[p.id] ?: 0
            if (left == 0 && step.time >= p.arrival) "${p.id} ✓" else "${p.id} $left"
        },
        style = MaterialTheme.typography.bodyMedium
    )

    StepMessage("What happens at t = ${step.time}", step.message)
    StepLog(steps.take(index + 1).map { "t=${it.time}: ${it.message}" })
}

// Earlier steps, most recent last. Long logs are collapsed to the last few lines.
@Composable
fun StepLog(lines: List<String>) {
    var expanded by remember { mutableStateOf(false) }
    val previous = lines.dropLast(1)
    if (previous.isEmpty()) return
    val shown = if (expanded) previous else previous.takeLast(4)

    Text("Earlier steps", style = MaterialTheme.typography.labelLarge)
    shown.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
    if (previous.size > 4) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Show fewer steps" else "Show all ${previous.size} earlier steps")
        }
    }
}
