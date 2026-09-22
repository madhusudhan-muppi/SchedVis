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
import com.example.schedvis.engine.roundRobin
import com.example.schedvis.engine.sjf
import java.util.Locale

val CPU_ALGORITHMS = listOf("FCFS", "SJF", "Round Robin")
const val MAX_PROCESSES = 10

// ==========================================
// STATE + LOGIC (kept outside the UI so inputs survive tab switches)
// ==========================================

class CpuState {
    var countText by mutableStateOf("4")
    val arrivals = mutableStateListOf("0", "1", "2", "3")
    val bursts = mutableStateListOf("5", "3", "8", "6")
    var algorithm by mutableStateOf(CPU_ALGORITHMS[0])
    var quantumText by mutableStateOf("2")

    var error by mutableStateOf<String?>(null)
    var result by mutableStateOf<SchedulingResult?>(null)
    var resultTitle by mutableStateOf("")
    var simulatedProcesses by mutableStateOf<List<Process>>(emptyList())

    // Add or remove input rows so there is one row per process.
    fun resize(n: Int) {
        while (arrivals.size < n) {
            arrivals.add("0")
            bursts.add("1")
        }
        while (arrivals.size > n) {
            arrivals.removeAt(arrivals.lastIndex)
            bursts.removeAt(bursts.lastIndex)
        }
    }

    fun loadSample() {
        countText = "4"
        resize(4)
        listOf("0", "1", "2", "3").forEachIndexed { i, v -> arrivals[i] = v }
        listOf("5", "3", "8", "6").forEachIndexed { i, v -> bursts[i] = v }
        quantumText = "2"
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
            processes.add(Process("P${i + 1}", arrival, burst))
        }

        val quantum = quantumText.toIntOrNull()
        if (algorithm == "Round Robin" && (quantum == null || quantum !in 1..20)) {
            error = "Enter a time quantum between 1 and 20."
            return
        }

        result = when (algorithm) {
            "SJF" -> sjf(processes)
            "Round Robin" -> roundRobin(processes, quantum!!)
            else -> fcfs(processes)
        }
        resultTitle = if (algorithm == "Round Robin") "Round Robin (quantum = $quantum)" else algorithm
        simulatedProcesses = processes
    }
}

// ==========================================
// SCREEN
// ==========================================

@Composable
fun CpuSchedulingScreen(state: CpuState) {
    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionTitle("Algorithm")
        OptionSelector(CPU_ALGORITHMS, state.algorithm) {
            state.algorithm = it
            state.result = null
            state.error = null
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField("Processes (1-$MAX_PROCESSES)", state.countText, Modifier.weight(1f)) { text ->
                state.countText = text
                text.toIntOrNull()?.let { if (it in 1..MAX_PROCESSES) state.resize(it) }
            }
            if (state.algorithm == "Round Robin") {
                NumberField("Time quantum", state.quantumText, Modifier.weight(1f)) {
                    state.quantumText = it
                }
            }
        }

        SectionTitle("Processes")
        for (i in state.arrivals.indices) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ProcessBadge("P${i + 1}", ProcessPalette[i % ProcessPalette.size])
                NumberField("Arrival time", state.arrivals[i], Modifier.weight(1f)) {
                    state.arrivals[i] = it
                }
                NumberField("Burst time", state.bursts[i], Modifier.weight(1f)) {
                    state.bursts[i] = it
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
            CpuResults(state.resultTitle, state.simulatedProcesses, result)
        }
    }
}

@Composable
private fun CpuResults(title: String, processes: List<Process>, result: SchedulingResult) {
    val order = processes.map { it.id }

    HorizontalDivider()
    SectionTitle("Gantt chart: $title")
    GanttChart(result.timeline, order)

    SectionTitle("Process details")
    DataRow(listOf("Process", "AT", "BT", "CT", "TAT", "WT"), header = true)
    result.perProcess.forEach { pr ->
        val p = processes.first { it.id == pr.pid }
        DataRow(
            listOf(pr.pid, p.arrival, p.burst, pr.completion, pr.turnaround, pr.waiting).map { it.toString() },
            accent = colorFor(pr.pid, order)
        )
    }
    Text(
        "AT = arrival, BT = burst, CT = completion, TAT = turnaround, WT = waiting",
        style = MaterialTheme.typography.bodySmall
    )

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard(
            "Avg turnaround time",
            String.format(Locale.US, "%.2f", result.avgTurnaround),
            Modifier.weight(1f)
        )
        MetricCard(
            "Avg waiting time",
            String.format(Locale.US, "%.2f", result.avgWaiting),
            Modifier.weight(1f)
        )
    }
}