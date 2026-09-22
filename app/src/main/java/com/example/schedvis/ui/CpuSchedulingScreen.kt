package com.example.schedvis.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.schedvis.engine.Process
import com.example.schedvis.engine.SchedulingResult
import com.example.schedvis.engine.fcfs
import com.example.schedvis.engine.priorityNonPreemptive
import com.example.schedvis.engine.priorityPreemptive
import com.example.schedvis.engine.roundRobin
import com.example.schedvis.engine.sjf
import com.example.schedvis.engine.srtf
import java.util.Locale

const val MAX_PROCESSES = 10
const val DEFAULT_QUANTUM = 2

// label = text on the button, title = full name shown above the results.
enum class CpuAlgorithm(
    val label: String,
    val title: String,
    val usesPriority: Boolean = false,
    val usesQuantum: Boolean = false
) {
    FCFS("FCFS", "First Come First Serve"),
    SJF("SJF", "Shortest Job First"),
    SRTF("SRTF", "Shortest Remaining Time First"),
    PRIORITY_NP("Priority NP", "Priority (non-preemptive)", usesPriority = true),
    PRIORITY_P("Priority P", "Priority (preemptive)", usesPriority = true),
    ROUND_ROBIN("Round Robin", "Round Robin", usesQuantum = true)
}

fun runCpuAlgorithm(algorithm: CpuAlgorithm, processes: List<Process>, quantum: Int): SchedulingResult =
    when (algorithm) {
        CpuAlgorithm.FCFS -> fcfs(processes)
        CpuAlgorithm.SJF -> sjf(processes)
        CpuAlgorithm.SRTF -> srtf(processes)
        CpuAlgorithm.PRIORITY_NP -> priorityNonPreemptive(processes)
        CpuAlgorithm.PRIORITY_P -> priorityPreemptive(processes)
        CpuAlgorithm.ROUND_ROBIN -> roundRobin(processes, quantum)
    }

private val SAMPLE_ARRIVALS = listOf("0", "1", "2", "3")
private val SAMPLE_BURSTS = listOf("5", "3", "8", "6")
private val SAMPLE_PRIORITIES = listOf("3", "1", "4", "2")

// ==========================================
// STATE + LOGIC (kept outside the UI so inputs survive tab switches)
// ==========================================

class CpuState {
    var countText by mutableStateOf("4")
    val arrivals = mutableStateListOf(*SAMPLE_ARRIVALS.toTypedArray())
    val bursts = mutableStateListOf(*SAMPLE_BURSTS.toTypedArray())
    val priorities = mutableStateListOf(*SAMPLE_PRIORITIES.toTypedArray())
    var algorithm by mutableStateOf(CpuAlgorithm.FCFS)
    var quantumText by mutableStateOf(DEFAULT_QUANTUM.toString())

    var error by mutableStateOf<String?>(null)
    var result by mutableStateOf<SchedulingResult?>(null)
    var simulatedAlgorithm by mutableStateOf(CpuAlgorithm.FCFS)
    var simulatedQuantum by mutableStateOf(DEFAULT_QUANTUM)
    var simulatedProcesses by mutableStateOf<List<Process>>(emptyList())
    var comparison by mutableStateOf<List<Pair<CpuAlgorithm, SchedulingResult>>>(emptyList())

    // Add or remove input rows so there is one row per process.
    fun resize(n: Int) {
        while (arrivals.size < n) {
            arrivals.add("0")
            bursts.add("1")
            priorities.add("1")
        }
        while (arrivals.size > n) {
            arrivals.removeAt(arrivals.lastIndex)
            bursts.removeAt(bursts.lastIndex)
            priorities.removeAt(priorities.lastIndex)
        }
    }

    fun selectAlgorithm(newAlgorithm: CpuAlgorithm) {
        algorithm = newAlgorithm
        result = null
        error = null
    }

    fun loadSample() {
        countText = "4"
        resize(4)
        for (i in 0 until 4) {
            arrivals[i] = SAMPLE_ARRIVALS[i]
            bursts[i] = SAMPLE_BURSTS[i]
            priorities[i] = SAMPLE_PRIORITIES[i]
        }
        quantumText = DEFAULT_QUANTUM.toString()
        error = null
        result = null
    }

    fun simulate() {
        error = null
        result = null

        val n = countText.toIntOrNull()
        if (n == null || n !in 1..MAX_PROCESSES) {
            error = "Enter a number of processes between 1 and $MAX_PROCESSES."
            return
        }
        if (arrivals.size != n) resize(n)

        val processes = mutableListOf<Process>()
        for (i in 0 until n) {
            val arrival = arrivals[i].toIntOrNull()
            val burst = bursts[i].toIntOrNull()
            if (arrival == null || arrival > 100) {
                error = "P${i + 1}: enter an arrival time between 0 and 100."
                return
            }
            if (burst == null || burst !in 1..50) {
                error = "P${i + 1}: enter a burst time between 1 and 50."
                return
            }
            // Priority is only checked strictly when a priority algorithm is selected.
            val priority = priorities[i].toIntOrNull()
            if (algorithm.usesPriority && (priority == null || priority > 99)) {
                error = "P${i + 1}: enter a priority between 0 and 99."
                return
            }
            processes.add(Process("P${i + 1}", arrival, burst, (priority ?: 0).coerceIn(0, 99)))
        }

        val enteredQuantum = quantumText.toIntOrNull()
        val quantumValid = enteredQuantum != null && enteredQuantum in 1..20
        if (algorithm.usesQuantum && !quantumValid) {
            error = "Enter a time quantum between 1 and 20."
            return
        }
        val quantum = if (quantumValid) enteredQuantum!! else DEFAULT_QUANTUM

        result = runCpuAlgorithm(algorithm, processes, quantum)
        simulatedAlgorithm = algorithm
        simulatedQuantum = quantum
        simulatedProcesses = processes
        comparison = CpuAlgorithm.entries.map { it to runCpuAlgorithm(it, processes, quantum) }
    }
}

// ==========================================
// SCREEN
// ==========================================

@Composable
fun CpuSchedulingScreen(state: CpuState) {
    val showPriority = state.algorithm.usesPriority

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionTitle("Algorithm")
        OptionSelector(CpuAlgorithm.entries.map { it.label }, state.algorithm.label) { label ->
            state.selectAlgorithm(CpuAlgorithm.entries.first { it.label == label })
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField("Processes (1-$MAX_PROCESSES)", state.countText, Modifier.weight(1f)) { text ->
                state.countText = text
                text.toIntOrNull()?.let { if (it in 1..MAX_PROCESSES) state.resize(it) }
            }
            if (state.algorithm.usesQuantum) {
                NumberField("Time quantum", state.quantumText, Modifier.weight(1f)) {
                    state.quantumText = it
                }
            }
        }

        SectionTitle("Processes")
        if (showPriority) {
            Text(
                "Lower number = higher priority",
                style = MaterialTheme.typography.bodySmall
            )
        }
        for (i in state.arrivals.indices) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProcessBadge("P${i + 1}", ProcessPalette[i % ProcessPalette.size])
                NumberField(if (showPriority) "Arrival" else "Arrival time", state.arrivals[i], Modifier.weight(1f)) {
                    state.arrivals[i] = it
                }
                NumberField(if (showPriority) "Burst" else "Burst time", state.bursts[i], Modifier.weight(1f)) {
                    state.bursts[i] = it
                }
                if (showPriority) {
                    NumberField("Priority", state.priorities[i], Modifier.weight(1f)) {
                        state.priorities[i] = it
                    }
                }
            }
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

        state.result?.let { result ->
            CpuResults(state, result)
        }
    }
}

private fun formatTime(value: Double) = String.format(Locale.US, "%.2f", value)

@Composable
private fun CpuResults(state: CpuState, result: SchedulingResult) {
    val algorithm = state.simulatedAlgorithm
    val processes = state.simulatedProcesses
    val order = processes.map { it.id }
    val title = if (algorithm.usesQuantum) "${algorithm.title} (quantum = ${state.simulatedQuantum})"
    else algorithm.title

    HorizontalDivider()
    SectionTitle("Gantt chart: $title")
    GanttChart(result.timeline, order)

    SectionTitle("Process details")
    val header = if (algorithm.usesPriority) listOf("Proc", "AT", "BT", "Pr", "CT", "TAT", "WT")
    else listOf("Process", "AT", "BT", "CT", "TAT", "WT")
    DataRow(header, header = true)
    result.perProcess.forEach { pr ->
        val p = processes.first { it.id == pr.pid }
        val cells = if (algorithm.usesPriority)
            listOf(pr.pid, p.arrival, p.burst, p.priority, pr.completion, pr.turnaround, pr.waiting)
        else
            listOf(pr.pid, p.arrival, p.burst, pr.completion, pr.turnaround, pr.waiting)
        DataRow(cells.map { it.toString() }, accent = colorFor(pr.pid, order))
    }
    Text(
        if (algorithm.usesPriority)
            "AT = arrival, BT = burst, Pr = priority, CT = completion, TAT = turnaround, WT = waiting"
        else
            "AT = arrival, BT = burst, CT = completion, TAT = turnaround, WT = waiting",
        style = MaterialTheme.typography.bodySmall
    )

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard("Avg turnaround time", formatTime(result.avgTurnaround), Modifier.weight(1f))
        MetricCard("Avg waiting time", formatTime(result.avgWaiting), Modifier.weight(1f))
    }

    // Compare all six algorithms on the same input.
    SectionTitle("Average waiting time for each algorithm")
    state.comparison.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { (alg, res) ->
                MetricCard(
                    alg.label,
                    formatTime(res.avgWaiting),
                    Modifier.weight(1f),
                    highlighted = alg == algorithm
                )
            }
        }
    }
    val best = state.comparison.minByOrNull { it.second.avgWaiting }
    if (best != null) {
        Text(
            "Lowest average waiting time: ${best.first.title}" +
                    (if (best.first.usesQuantum) " (quantum = ${state.simulatedQuantum})" else ""),
            style = MaterialTheme.typography.bodyMedium
        )
    }
    Text(
        "Priority algorithms use the priorities entered above (0 if left blank). " +
                "Round Robin uses quantum ${state.simulatedQuantum}.",
        style = MaterialTheme.typography.bodySmall
    )
}