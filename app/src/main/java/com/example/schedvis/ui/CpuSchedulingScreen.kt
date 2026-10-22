package com.example.schedvis.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.example.schedvis.engine.QueueConfig
import com.example.schedvis.engine.QueuePolicy
import com.example.schedvis.engine.SchedulingResult
import com.example.schedvis.engine.fcfs
import com.example.schedvis.engine.mlfq
import com.example.schedvis.engine.mlq
import com.example.schedvis.engine.priorityNonPreemptive
import com.example.schedvis.engine.priorityPreemptive
import com.example.schedvis.engine.roundRobin
import com.example.schedvis.engine.sjf
import com.example.schedvis.engine.srtf
import com.example.schedvis.scan.ScannedQuestion
import java.util.Locale

const val MAX_PROCESSES = 10
const val DEFAULT_QUANTUM = 2
const val MIN_QUEUES = 2
const val MAX_QUEUES = 3

// label = text on the button, title = full name shown above the results.
enum class CpuAlgorithm(
    val label: String,
    val title: String,
    val usesPriority: Boolean = false,
    val usesQuantum: Boolean = false,
    val usesQueues: Boolean = false
) {
    FCFS("FCFS", "First Come First Serve"),
    SJF("SJF", "Shortest Job First"),
    SRTF("SRTF", "Shortest Remaining Time First"),
    PRIORITY_NP("Priority NP", "Priority (non-preemptive)", usesPriority = true),
    PRIORITY_P("Priority P", "Priority (preemptive)", usesPriority = true),
    ROUND_ROBIN("Round Robin", "Round Robin", usesQuantum = true),
    MLQ("MLQ", "Multilevel Queue", usesQueues = true),
    MLFQ("MLFQ", "Multilevel Feedback Queue", usesQueues = true)
}

fun runCpuAlgorithm(
    algorithm: CpuAlgorithm,
    processes: List<Process>,
    quantum: Int,
    queues: List<QueueConfig>
): SchedulingResult =
    when (algorithm) {
        CpuAlgorithm.FCFS -> fcfs(processes)
        CpuAlgorithm.SJF -> sjf(processes)
        CpuAlgorithm.SRTF -> srtf(processes)
        CpuAlgorithm.PRIORITY_NP -> priorityNonPreemptive(processes)
        CpuAlgorithm.PRIORITY_P -> priorityPreemptive(processes)
        CpuAlgorithm.ROUND_ROBIN -> roundRobin(processes, quantum)
        CpuAlgorithm.MLQ -> mlq(processes, queues)
        CpuAlgorithm.MLFQ -> mlfq(processes, queues)
    }

private val SAMPLE_ARRIVALS = listOf("0", "1", "2", "3")
private val SAMPLE_BURSTS = listOf("5", "3", "8", "6")
private val SAMPLE_PRIORITIES = listOf("3", "1", "4", "2")
private val SAMPLE_QUEUES = listOf("2", "1", "2", "1")

// MLQ: a system queue served by RR above a user queue served by FCFS.
private val DEFAULT_MLQ = listOf(QueueConfig(QueuePolicy.RR, 2), QueueConfig(QueuePolicy.FCFS))

// MLFQ: Galvin's three levels.
private val DEFAULT_MLFQ = listOf(
    QueueConfig(QueuePolicy.RR, 8), QueueConfig(QueuePolicy.RR, 16), QueueConfig(QueuePolicy.FCFS)
)

// One editable queue level. quantumText is ignored for FCFS queues.
class QueueInput(policy: QueuePolicy, quantum: String) {
    var policy by mutableStateOf(policy)
    var quantumText by mutableStateOf(quantum)
}

private fun queueInputs(configs: List<QueueConfig>) =
    configs.map { QueueInput(it.policy, if (it.policy == QueuePolicy.RR) it.quantum.toString() else "4") }

// Reads queue inputs, or returns null if a quantum is missing or out of range.
private fun parseQueues(inputs: List<QueueInput>): List<QueueConfig>? {
    return inputs.map { q ->
        when (q.policy) {
            QueuePolicy.FCFS -> QueueConfig(QueuePolicy.FCFS)
            QueuePolicy.RR -> QueueConfig(
                QueuePolicy.RR,
                q.quantumText.toIntOrNull()?.takeIf { it in 1..20 } ?: return null
            )
        }
    }
}

private fun queuesTitle(queues: List<QueueConfig>) =
    queues.mapIndexed { i, q -> "Q${i + 1} ${q.label}" }.joinToString(", ")

// ==========================================
// STATE + LOGIC (kept outside the UI so inputs survive tab switches)
// ==========================================

class CpuState {
    var countText by mutableStateOf("4")
    val arrivals = mutableStateListOf(*SAMPLE_ARRIVALS.toTypedArray())
    val bursts = mutableStateListOf(*SAMPLE_BURSTS.toTypedArray())
    val priorities = mutableStateListOf(*SAMPLE_PRIORITIES.toTypedArray())
    val processQueues = mutableStateListOf(*SAMPLE_QUEUES.toTypedArray())
    var algorithm by mutableStateOf(CpuAlgorithm.FCFS)
    var quantumText by mutableStateOf(DEFAULT_QUANTUM.toString())
    val mlqQueues = mutableStateListOf(*queueInputs(DEFAULT_MLQ).toTypedArray())
    val mlfqQueues = mutableStateListOf(*queueInputs(DEFAULT_MLFQ).toTypedArray())

    var error by mutableStateOf<String?>(null)
    var result by mutableStateOf<SchedulingResult?>(null)
    var playback by mutableStateOf<PlaybackState?>(null)
    var simulatedAlgorithm by mutableStateOf(CpuAlgorithm.FCFS)
    var simulatedQuantum by mutableStateOf(DEFAULT_QUANTUM)
    var simulatedQueues by mutableStateOf<List<QueueConfig>>(emptyList())
    var simulatedProcesses by mutableStateOf<List<Process>>(emptyList())
    var comparison by mutableStateOf<List<Pair<CpuAlgorithm, SchedulingResult>>>(emptyList())

    // The queue list being edited for the selected algorithm.
    val activeQueues get() = if (algorithm == CpuAlgorithm.MLFQ) mlfqQueues else mlqQueues

    // Add or remove input rows so there is one row per process.
    fun resize(n: Int) {
        while (arrivals.size < n) {
            arrivals.add("0")
            bursts.add("1")
            priorities.add("1")
            processQueues.add("1")
        }
        while (arrivals.size > n) {
            arrivals.removeAt(arrivals.lastIndex)
            bursts.removeAt(bursts.lastIndex)
            priorities.removeAt(priorities.lastIndex)
            processQueues.removeAt(processQueues.lastIndex)
        }
    }

    fun addQueue() {
        if (activeQueues.size < MAX_QUEUES) activeQueues.add(QueueInput(QueuePolicy.FCFS, "4"))
    }

    fun removeQueue() {
        if (activeQueues.size > MIN_QUEUES) activeQueues.removeAt(activeQueues.lastIndex)
    }

    fun selectAlgorithm(newAlgorithm: CpuAlgorithm) {
        algorithm = newAlgorithm
        result = null
        playback = null
        error = null
    }

    fun loadSample() {
        countText = "4"
        resize(4)
        for (i in 0 until 4) {
            arrivals[i] = SAMPLE_ARRIVALS[i]
            bursts[i] = SAMPLE_BURSTS[i]
            priorities[i] = SAMPLE_PRIORITIES[i]
            processQueues[i] = SAMPLE_QUEUES[i]
        }
        quantumText = DEFAULT_QUANTUM.toString()
        mlqQueues.clear()
        mlqQueues.addAll(queueInputs(DEFAULT_MLQ))
        mlfqQueues.clear()
        mlfqQueues.addAll(queueInputs(DEFAULT_MLFQ))
        error = null
        result = null
        playback = null
    }

    // Fills the inputs from a scanned photo and runs the simulation.
    fun applyScan(scan: ScannedQuestion) {
        selectAlgorithm(CpuAlgorithm.entries.firstOrNull { it.name == scan.algorithm } ?: CpuAlgorithm.FCFS)
        val n = scan.processes.size
        countText = n.toString()
        if (n in 1..MAX_PROCESSES) {
            resize(n)
            scan.processes.forEachIndexed { i, p ->
                arrivals[i] = (p.arrival ?: 0).toString()
                bursts[i] = p.burst?.toString().orEmpty()
                priorities[i] = (p.priority ?: 0).toString()
                processQueues[i] = (p.queue ?: 1).toString()
            }
        }
        scan.quantum?.let { quantumText = it.toString() }
        if (algorithm.usesQueues && scan.queues.isNotEmpty()) {
            val target = activeQueues
            target.clear()
            scan.queues.take(MAX_QUEUES).forEach { target.add(QueueInput(it.policy, it.quantum?.toString() ?: "")) }
            // MLQ and MLFQ need at least two levels on screen; pad with a FCFS level.
            while (target.size < MIN_QUEUES) target.add(QueueInput(QueuePolicy.FCFS, "4"))
        }
        simulate()
    }

    fun simulate() {
        error = null
        result = null
        playback = null

        val n = countText.toIntOrNull()
        if (n == null || n !in 1..MAX_PROCESSES) {
            error = "Enter a number of processes between 1 and $MAX_PROCESSES."
            return
        }
        if (arrivals.size != n) resize(n)

        val queueCount = activeQueues.size
        val processes = mutableListOf<Process>()
        for (i in 0 until n) {
            val arrival = arrivals[i].toIntOrNull()
            val burst = bursts[i].toIntOrNull()
            if (arrival == null || arrival !in 0..100) {
                error = "P${i + 1}: enter an arrival time between 0 and 100."
                return
            }
            if (burst == null || burst !in 1..50) {
                error = "P${i + 1}: enter a burst time between 1 and 50."
                return
            }
            // Priority is only checked strictly when a priority algorithm is selected.
            val priority = priorities[i].toIntOrNull()
            if (algorithm.usesPriority && (priority == null || priority !in 0..99)) {
                error = "P${i + 1}: enter a priority between 0 and 99."
                return
            }
            // Same for the MLQ queue number (1 = highest).
            val queue = processQueues[i].toIntOrNull()
            if (algorithm == CpuAlgorithm.MLQ && (queue == null || queue !in 1..queueCount)) {
                error = "P${i + 1}: enter a queue between 1 and $queueCount."
                return
            }
            processes.add(
                Process(
                    "P${i + 1}", arrival, burst,
                    (priority ?: 0).coerceIn(0, 99),
                    ((queue ?: 1) - 1).coerceIn(0, queueCount - 1)
                )
            )
        }

        val enteredQuantum = quantumText.toIntOrNull()
        val quantumValid = enteredQuantum != null && enteredQuantum in 1..20
        if (algorithm.usesQuantum && !quantumValid) {
            error = "Enter a time quantum between 1 and 20."
            return
        }
        val quantum = if (quantumValid) enteredQuantum!! else DEFAULT_QUANTUM

        val activeConfig = parseQueues(activeQueues)
        if (algorithm.usesQueues && activeConfig == null) {
            error = "Enter a quantum between 1 and 20 for every RR queue."
            return
        }
        // The comparison also runs the multilevel algorithm that is not selected; fall back to its defaults.
        val mlqConfig = parseQueues(mlqQueues) ?: DEFAULT_MLQ
        val mlfqConfig = parseQueues(mlfqQueues) ?: DEFAULT_MLFQ
        fun queuesFor(a: CpuAlgorithm) = if (a == CpuAlgorithm.MLFQ) mlfqConfig else mlqConfig

        val newResult = runCpuAlgorithm(algorithm, processes, quantum, queuesFor(algorithm))
        result = newResult
        playback = PlaybackState(newResult.steps.size)
        simulatedAlgorithm = algorithm
        simulatedQuantum = quantum
        simulatedQueues = queuesFor(algorithm)
        simulatedProcesses = processes
        comparison = CpuAlgorithm.entries.map { it to runCpuAlgorithm(it, processes, quantum, queuesFor(it)) }
    }
}

// ==========================================
// SCREEN
// ==========================================

@Composable
fun CpuSchedulingScreen(state: CpuState) {
    val showPriority = state.algorithm.usesPriority
    val showQueue = state.algorithm == CpuAlgorithm.MLQ

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

        if (state.algorithm.usesQueues) {
            QueueEditor(state)
        }

        SectionTitle("Processes")
        if (showPriority) {
            Text(
                "Lower number = higher priority",
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (showQueue) {
            Text(
                "Queue: which ready queue the process belongs to (1 = highest)",
                style = MaterialTheme.typography.bodySmall
            )
        }
        val compact = showPriority || showQueue
        for (i in state.arrivals.indices) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProcessBadge("P${i + 1}", ProcessPalette[i % ProcessPalette.size])
                NumberField(if (compact) "Arrival" else "Arrival time", state.arrivals[i], Modifier.weight(1f)) {
                    state.arrivals[i] = it
                }
                NumberField(if (compact) "Burst" else "Burst time", state.bursts[i], Modifier.weight(1f)) {
                    state.bursts[i] = it
                }
                if (showPriority) {
                    NumberField("Priority", state.priorities[i], Modifier.weight(1f)) {
                        state.priorities[i] = it
                    }
                }
                if (showQueue) {
                    NumberField("Queue", state.processQueues[i], Modifier.weight(1f)) {
                        state.processQueues[i] = it
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

        val result = state.result
        val playback = state.playback
        if (result != null && playback != null) {
            CpuResults(state, result, playback)
        }
    }
}

// One row per queue level: policy (RR or FCFS) and, for RR, its quantum.
@Composable
private fun QueueEditor(state: CpuState) {
    val queues = state.activeQueues
    SectionTitle("Queues")
    Text(
        if (state.algorithm == CpuAlgorithm.MLFQ)
            "Every process starts in Q1. Using a full quantum without finishing moves it down one queue."
        else
            "Q1 has the highest priority. A lower queue runs only when all higher queues are empty.",
        style = MaterialTheme.typography.bodySmall
    )
    queues.forEachIndexed { i, q ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Q${i + 1}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(32.dp))
            Row(Modifier.weight(1f)) {
                OptionSelector(QueuePolicy.entries.map { it.name }, q.policy.name, perRow = 2) {
                    q.policy = QueuePolicy.valueOf(it)
                }
            }
            if (q.policy == QueuePolicy.RR) {
                NumberField("Quantum", q.quantumText, Modifier.width(110.dp)) { q.quantumText = it }
            } else {
                Text("runs to completion", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(110.dp))
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick = { state.removeQueue() }, enabled = queues.size > MIN_QUEUES, modifier = Modifier.weight(1f)
        ) { Text("Remove queue") }
        OutlinedButton(
            onClick = { state.addQueue() }, enabled = queues.size < MAX_QUEUES, modifier = Modifier.weight(1f)
        ) { Text("Add queue") }
    }
}

private fun formatTime(value: Double) = String.format(Locale.US, "%.2f", value)

@Composable
private fun CpuResults(state: CpuState, result: SchedulingResult, playback: PlaybackState) {
    val algorithm = state.simulatedAlgorithm
    val processes = state.simulatedProcesses
    val order = processes.map { it.id }
    val title = when {
        algorithm.usesQuantum -> "${algorithm.title} (quantum = ${state.simulatedQuantum})"
        algorithm.usesQueues -> "${algorithm.title}: ${queuesTitle(state.simulatedQueues)}"
        else -> algorithm.title
    }
    val steps = result.steps
    val step = steps.getOrNull(playback.index)
    val endTime = result.timeline.lastOrNull()?.end ?: 0
    val visibleUntil by animateFloatAsState(
        targetValue = (step?.time ?: endTime).toFloat(),
        animationSpec = tween(durationMillis = 500),
        label = "gantt"
    )

    HorizontalDivider()
    SectionTitle("Simulation: $title")
    if (steps.isNotEmpty()) PlaybackControls(playback)
    GanttChart(result.timeline, order, visibleUntil = if (playback.atEnd) null else visibleUntil)
    if (step != null) {
        val queueLabels = if (algorithm.usesQueues)
            state.simulatedQueues.mapIndexed { i, q -> "Q${i + 1} ${q.label}" }
        else listOf("Ready queue")
        CpuSimPanel(steps, playback.index, processes, result.timeline, queueLabels)
    }

    if (!playback.atEnd) {
        Text(
            "The results table appears at the end of the simulation. Tap \"Skip to result\" to see it now.",
            style = MaterialTheme.typography.bodySmall
        )
        return
    }

    SectionTitle("Process details")
    val showPriority = algorithm.usesPriority
    val showQueue = algorithm == CpuAlgorithm.MLQ
    val header = listOf(if (showPriority || showQueue) "Proc" else "Process", "AT", "BT") +
            (if (showPriority) listOf("Pr") else emptyList()) +
            (if (showQueue) listOf("Q") else emptyList()) +
            listOf("CT", "TAT", "WT")
    DataRow(header, header = true)
    result.perProcess.forEach { pr ->
        val p = processes.first { it.id == pr.pid }
        val cells = listOf<Any>(pr.pid, p.arrival, p.burst) +
                (if (showPriority) listOf(p.priority) else emptyList()) +
                (if (showQueue) listOf(p.queue + 1) else emptyList()) +
                listOf(pr.completion, pr.turnaround, pr.waiting)
        DataRow(cells.map { it.toString() }, accent = colorFor(pr.pid, order))
    }
    Text(
        "AT = arrival, BT = burst, " +
                (if (showPriority) "Pr = priority, " else "") +
                (if (showQueue) "Q = queue, " else "") +
                "CT = completion, TAT = turnaround, WT = waiting",
        style = MaterialTheme.typography.bodySmall
    )

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard("Avg turnaround time", formatTime(result.avgTurnaround), Modifier.weight(1f))
        MetricCard("Avg waiting time", formatTime(result.avgWaiting), Modifier.weight(1f))
    }

    // Compare every algorithm on the same input.
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
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
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
                "Round Robin uses quantum ${state.simulatedQuantum}. " +
                "MLQ and MLFQ use their queue settings (defaults if not valid).",
        style = MaterialTheme.typography.bodySmall
    )
}
