package com.example.schedvis.engine

// ==========================================
// DATA CLASSES
// ==========================================

// priority: lower number = higher priority (Galvin convention). Defaults to 0 for algorithms that ignore it.
// queue: the ready queue a process belongs to in Multilevel Queue scheduling (0 = highest).
data class Process(
    val id: String,
    val arrival: Int,
    val burst: Int,
    val priority: Int = 0,
    val queue: Int = 0
)

// pid = null means CPU idle. queue is set only by the multilevel algorithms.
data class GanttSlot(val pid: String?, val start: Int, val end: Int, val queue: Int? = null)

data class ProcessResult(val pid: String, val completion: Int, val turnaround: Int, val waiting: Int)

// One moment of the simulation: what is running, who is waiting in each ready queue, and why.
// Single-queue algorithms have exactly one list in queues.
data class SimStep(val time: Int, val running: String?, val queues: List<List<String>>, val message: String)

data class SchedulingResult(
    val timeline: List<GanttSlot>,
    val perProcess: List<ProcessResult>,
    val avgTurnaround: Double,
    val avgWaiting: Double,
    val steps: List<SimStep> = emptyList()
)

// frames = contents AFTER request, null = empty. victim = page that was evicted, if any.
data class PageStep(
    val page: Int,
    val frames: List<Int?>,
    val isFault: Boolean,
    val victim: Int? = null,
    val message: String = ""
)

data class PageResult(val steps: List<PageStep>, val totalFaults: Int, val hitRatio: Double)

// ==========================================
// HELPER EXTENSIONS
// ==========================================

internal fun MutableList<GanttSlot>.addSlot(pid: String?, start: Int, end: Int, queue: Int? = null) {
    if (start == end) return
    if (isNotEmpty() && last().pid == pid && last().queue == queue) {
        val prev = removeAt(lastIndex)
        add(GanttSlot(pid, prev.start, end, queue))
    } else {
        add(GanttSlot(pid, start, end, queue))
    }
}

internal val EMPTY_SCHEDULE = SchedulingResult(emptyList(), emptyList(), 0.0, 0.0)

internal fun names(ps: List<Process>) = when (ps.size) {
    1 -> ps[0].id
    else -> ps.dropLast(1).joinToString(", ") { it.id } + " and " + ps.last().id
}

// Collects the simulation steps and announces each process's arrival exactly once.
internal class Trace(private val processes: List<Process>) {
    val steps = mutableListOf<SimStep>()
    private val announced = mutableSetOf<String>()

    fun add(time: Int, running: String?, queues: List<List<String>>, message: String) {
        steps.add(SimStep(time, running, queues, message))
    }

    // Adds one step per arrival time for every not-yet-announced process that arrived by upTo.
    // queuesAt(t) gives the ready queue contents right after the arrivals at time t.
    fun arrivals(upTo: Int, running: String?, joins: String, queuesAt: (Int) -> List<List<String>>) {
        processes.filter { it.id !in announced && it.arrival <= upTo }
            .sortedWith(compareBy<Process>({ it.arrival }, { it.id }))
            .groupBy { it.arrival }
            .forEach { (t, ps) ->
                ps.forEach { announced += it.id }
                val verb = if (ps.size == 1) "arrives" else "arrive"
                add(t, running, queuesAt(t), "${names(ps)} $verb and $joins.")
            }
    }

    fun finished(p: Process, time: Int, queues: List<List<String>>) {
        val tat = time - p.arrival
        add(
            time, null, queues,
            "${p.id} finishes at t=$time. TAT = $time − ${p.arrival} = $tat, WT = $tat − ${p.burst} = ${tat - p.burst}."
        )
    }
}

// Turns completion times into per-process metrics and averages.
internal fun buildResult(
    processes: List<Process>,
    timeline: List<GanttSlot>,
    completionTimes: Map<String, Int>,
    trace: Trace? = null
): SchedulingResult {
    val perProcess = processes.map { p ->
        val completion = completionTimes[p.id] ?: 0
        val turnaround = completion - p.arrival
        ProcessResult(p.id, completion, turnaround, turnaround - p.burst)
    }
    val avgTurnaround = perProcess.map { it.turnaround }.average()
    val avgWaiting = perProcess.map { it.waiting }.average()

    val steps = trace?.steps?.toMutableList() ?: mutableListOf()
    if (steps.isNotEmpty()) {
        val end = timeline.lastOrNull()?.end ?: 0
        steps.add(
            SimStep(
                end, null, steps.last().queues.map { emptyList() },
                "All processes have finished at t=$end. Average TAT = %.2f, average WT = %.2f."
                    .format(java.util.Locale.US, avgTurnaround, avgWaiting)
            )
        )
    }
    return SchedulingResult(timeline, perProcess, avgTurnaround, avgWaiting, steps)
}

// ==========================================
// CPU SCHEDULING ALGORITHMS
// ==========================================

// First Come First Serve: the earliest arrival runs to completion.
fun fcfs(processes: List<Process>): SchedulingResult =
    runNonPreemptive(processes, compareBy<Process>({ it.arrival }, { it.id })) {
        "it arrived first (t=${it.arrival})"
    }

// Shortest Job First (non-preemptive): the arrived process with the smallest burst runs to completion.
fun sjf(processes: List<Process>): SchedulingResult =
    runNonPreemptive(processes, compareBy<Process>({ it.burst }, { it.arrival }, { it.id })) {
        "it has the shortest burst among the ready processes (${it.burst})"
    }

// Shortest Remaining Time First: preemptive SJF.
// Every time unit, the arrived process with the least remaining time runs.
fun srtf(processes: List<Process>): SchedulingResult =
    runPreemptive(processes, "remaining time") { _, remaining -> remaining }

// Priority (non-preemptive): when the CPU is free, the arrived process with the
// highest priority (lowest number) runs to completion.
fun priorityNonPreemptive(processes: List<Process>): SchedulingResult =
    runNonPreemptive(processes, compareBy<Process>({ it.priority }, { it.arrival }, { it.id })) {
        "it has the highest priority among the ready processes (${it.priority})"
    }

// Priority (preemptive): a newly arrived process with a higher priority
// (lower number) takes the CPU away from the running one.
fun priorityPreemptive(processes: List<Process>): SchedulingResult =
    runPreemptive(processes, "priority number") { p, _ -> p.priority }

// Shared logic for non-preemptive algorithms: pick the best available process, run it to the end.
// why(process) explains the choice in the step log.
private fun runNonPreemptive(
    processes: List<Process>,
    order: Comparator<Process>,
    why: (Process) -> String
): SchedulingResult {
    if (processes.isEmpty()) return EMPTY_SCHEDULE

    val timeline = mutableListOf<GanttSlot>()
    val completionTimes = mutableMapOf<String, Int>()
    val remaining = processes.toMutableList()
    val trace = Trace(processes)
    var currentTime = 0

    fun readyAt(t: Int) = listOf(remaining.filter { it.arrival <= t }.sortedWith(order).map { it.id })

    while (remaining.isNotEmpty()) {
        trace.arrivals(currentTime, null, "joins the ready queue", ::readyAt)
        val available = remaining.filter { it.arrival <= currentTime }
        if (available.isEmpty()) {
            val nextArrival = remaining.minOf { it.arrival }
            trace.add(currentTime, null, readyAt(currentTime), "CPU is idle until the next arrival at t=$nextArrival.")
            timeline.addSlot(null, currentTime, nextArrival)
            currentTime = nextArrival
            continue
        }
        val selected = available.minWithOrNull(order)!!
        remaining.remove(selected)
        val end = currentTime + selected.burst
        trace.add(
            currentTime, selected.id, readyAt(currentTime),
            "${selected.id} gets the CPU because ${why(selected)}. It runs for ${selected.burst} without interruption."
        )
        trace.arrivals(end, selected.id, "waits in the ready queue", ::readyAt)
        timeline.addSlot(selected.id, currentTime, end)
        currentTime = end
        completionTimes[selected.id] = currentTime
        trace.finished(selected, currentTime, readyAt(currentTime))
    }
    return buildResult(processes, timeline, completionTimes, trace)
}

// Shared logic for preemptive algorithms: simulate one time unit at a time and
// re-pick the best process every unit. key(process, remainingTime): smaller = runs first.
// Ties go to the earlier arrival, then the smaller id. keyName describes the key in the step log.
private fun runPreemptive(
    processes: List<Process>,
    keyName: String,
    key: (Process, Int) -> Int
): SchedulingResult {
    if (processes.isEmpty()) return EMPTY_SCHEDULE

    val timeline = mutableListOf<GanttSlot>()
    val completionTimes = mutableMapOf<String, Int>()
    val remBurst = processes.associate { it.id to it.burst }.toMutableMap()
    val trace = Trace(processes)
    val order = compareBy<Process>({ key(it, remBurst[it.id]!!) }, { it.arrival }, { it.id })
    var finished = 0
    var running: Process? = null

    // Processes with no burst time finish the moment they arrive.
    for (p in processes) {
        if (p.burst <= 0) {
            completionTimes[p.id] = p.arrival
            finished++
        }
    }

    var currentTime = 0
    fun readyAt(t: Int) = listOf(
        processes.filter { it.arrival <= t && remBurst[it.id]!! > 0 && it != running }
            .sortedWith(order).map { it.id }
    )

    while (finished < processes.size) {
        trace.arrivals(currentTime, running?.id, "joins the ready queue", ::readyAt)
        val available = processes.filter { it.arrival <= currentTime && remBurst[it.id]!! > 0 }
        if (available.isEmpty()) {
            val nextArrival = processes.filter { remBurst[it.id]!! > 0 }.minOf { it.arrival }
            trace.add(currentTime, null, readyAt(currentTime), "CPU is idle until the next arrival at t=$nextArrival.")
            timeline.addSlot(null, currentTime, nextArrival)
            currentTime = nextArrival
            continue
        }

        val selected = available.minWithOrNull(order)!!
        if (selected != running) {
            val value = key(selected, remBurst[selected.id]!!)
            val previous = running
            running = selected
            val message = if (previous == null) {
                "${selected.id} gets the CPU: it has the smallest $keyName among the ready processes ($value)."
            } else {
                val prevValue = key(previous, remBurst[previous.id]!!)
                "${selected.id} preempts ${previous.id}: $keyName $value < $prevValue. " +
                        "${previous.id} goes back to the ready queue with ${remBurst[previous.id]} left."
            }
            trace.add(currentTime, selected.id, readyAt(currentTime), message)
        }

        timeline.addSlot(selected.id, currentTime, currentTime + 1)
        currentTime++
        remBurst[selected.id] = remBurst[selected.id]!! - 1

        if (remBurst[selected.id] == 0) {
            completionTimes[selected.id] = currentTime
            finished++
            running = null
            trace.finished(selected, currentTime, readyAt(currentTime))
        }
    }
    return buildResult(processes, timeline, completionTimes, trace)
}

fun roundRobin(processes: List<Process>, quantum: Int): SchedulingResult {
    if (processes.isEmpty() || quantum <= 0) return EMPTY_SCHEDULE

    val timeline = mutableListOf<GanttSlot>()
    val completionTimes = mutableMapOf<String, Int>()
    val remBurst = processes.associate { it.id to it.burst }.toMutableMap()
    val trace = Trace(processes)

    val unarrived = processes.sortedWith(compareBy<Process> { it.arrival }.thenBy { it.id }).toMutableList()
    val readyQueue = ArrayDeque<Process>()
    var currentTime = 0

    fun queueIds() = listOf(readyQueue.map { it.id })

    if (unarrived.isNotEmpty() && unarrived.first().arrival > 0) {
        trace.add(0, null, queueIds(), "CPU is idle until the first arrival at t=${unarrived.first().arrival}.")
        timeline.addSlot(null, 0, unarrived.first().arrival)
        currentTime = unarrived.first().arrival
    }

    val initialArrivals = unarrived.filter { it.arrival <= currentTime }
    readyQueue.addAll(initialArrivals)
    unarrived.removeAll(initialArrivals)
    trace.arrivals(currentTime, null, "joins the ready queue") { queueIds() }

    while (readyQueue.isNotEmpty() || unarrived.isNotEmpty()) {
        if (readyQueue.isEmpty()) {
            val nextArrival = unarrived.first().arrival
            trace.add(currentTime, null, queueIds(), "CPU is idle until the next arrival at t=$nextArrival.")
            timeline.addSlot(null, currentTime, nextArrival)
            currentTime = nextArrival
            val newArrivals = unarrived.filter { it.arrival <= currentTime }
            readyQueue.addAll(newArrivals)
            unarrived.removeAll(newArrivals)
            trace.arrivals(currentTime, null, "joins the ready queue") { queueIds() }
        }

        val currentProc = readyQueue.removeFirst()
        val burstTime = remBurst[currentProc.id]!!
        val executeTime = minOf(quantum, burstTime)
        val nextTime = currentTime + executeTime

        trace.add(
            currentTime, currentProc.id, queueIds(),
            "${currentProc.id} is at the head of the ready queue and runs for $executeTime" +
                    (if (executeTime < quantum) " (it needs only $burstTime more, less than the quantum $quantum)."
                    else " (one full quantum).")
        )

        timeline.addSlot(currentProc.id, currentTime, nextTime)
        remBurst[currentProc.id] = burstTime - executeTime

        val arrivedDuring = unarrived.filter { it.arrival in (currentTime + 1)..nextTime }
        val waitingBefore = readyQueue.map { it.id }
        trace.arrivals(nextTime, currentProc.id, "joins the tail of the ready queue") { t ->
            listOf(waitingBefore + arrivedDuring.filter { it.arrival <= t }.map { it.id })
        }
        readyQueue.addAll(arrivedDuring)
        unarrived.removeAll(arrivedDuring)

        if (remBurst[currentProc.id]!! > 0) {
            readyQueue.addLast(currentProc)
            trace.add(
                nextTime, null, queueIds(),
                "${currentProc.id}'s quantum expires with ${remBurst[currentProc.id]} left, " +
                        "so it goes to the tail of the ready queue."
            )
        } else {
            completionTimes[currentProc.id] = nextTime
            trace.finished(currentProc, nextTime, queueIds())
        }

        currentTime = nextTime
    }

    return buildResult(processes, timeline, completionTimes, trace)
}

// ==========================================
// PAGE REPLACEMENT ALGORITHMS
// ==========================================

private fun hitMessage(page: Int, frames: List<Int?>) =
    "Page $page is already in frame F${frames.indexOf(page) + 1}, so this is a hit."

private fun loadMessage(page: Int, frameIdx: Int) =
    "Page $page is not in memory: page fault. It is loaded into empty frame F${frameIdx + 1}."

private fun evictMessage(page: Int, victim: Int, frameIdx: Int, reason: String) =
    "Page $page is not in memory: page fault. All frames are full, so page $victim in F${frameIdx + 1} " +
            "is replaced because $reason."

fun fifo(pages: List<Int>, frameCount: Int): PageResult {
    if (pages.isEmpty() || frameCount <= 0) return PageResult(emptyList(), 0, 0.0)

    val frames = MutableList<Int?>(frameCount) { null }
    val steps = mutableListOf<PageStep>()
    var totalFaults = 0
    val fifoQueue = ArrayDeque<Int>()

    for (page in pages) {
        if (frames.contains(page)) {
            steps.add(PageStep(page, frames.toList(), false, message = hitMessage(page, frames)))
            continue
        }
        totalFaults++
        val nullIdx = frames.indexOf(null)
        if (nullIdx != -1) {
            frames[nullIdx] = page
            fifoQueue.addLast(nullIdx)
            steps.add(PageStep(page, frames.toList(), true, message = loadMessage(page, nullIdx)))
        } else {
            val victimIdx = fifoQueue.removeFirst()
            val victim = frames[victimIdx]!!
            frames[victimIdx] = page
            fifoQueue.addLast(victimIdx)
            steps.add(
                PageStep(
                    page, frames.toList(), true, victim,
                    evictMessage(page, victim, victimIdx, "it has been in memory the longest")
                )
            )
        }
    }

    val hitRatio = (pages.size - totalFaults).toDouble() / pages.size
    return PageResult(steps, totalFaults, hitRatio)
}

fun lru(pages: List<Int>, frameCount: Int): PageResult {
    if (pages.isEmpty() || frameCount <= 0) return PageResult(emptyList(), 0, 0.0)

    val frames = MutableList<Int?>(frameCount) { null }
    val steps = mutableListOf<PageStep>()
    var totalFaults = 0
    val lastUsed = mutableMapOf<Int, Int>()

    for ((stepIdx, page) in pages.withIndex()) {
        if (frames.contains(page)) {
            lastUsed[page] = stepIdx
            steps.add(PageStep(page, frames.toList(), false, message = hitMessage(page, frames)))
            continue
        }
        totalFaults++
        val nullIdx = frames.indexOf(null)
        if (nullIdx != -1) {
            frames[nullIdx] = page
            steps.add(PageStep(page, frames.toList(), true, message = loadMessage(page, nullIdx)))
        } else {
            val victimIdx = frames.indices.minByOrNull { idx ->
                val p = frames[idx]!!
                lastUsed[p] ?: -1
            }!!
            val victim = frames[victimIdx]!!
            frames[victimIdx] = page
            steps.add(
                PageStep(
                    page, frames.toList(), true, victim,
                    evictMessage(
                        page, victim, victimIdx,
                        "it is the least recently used (last used at request #${lastUsed[victim]!! + 1})"
                    )
                )
            )
        }
        lastUsed[page] = stepIdx
    }

    val hitRatio = (pages.size - totalFaults).toDouble() / pages.size
    return PageResult(steps, totalFaults, hitRatio)
}

fun optimal(pages: List<Int>, frameCount: Int): PageResult {
    if (pages.isEmpty() || frameCount <= 0) return PageResult(emptyList(), 0, 0.0)

    val frames = MutableList<Int?>(frameCount) { null }
    val steps = mutableListOf<PageStep>()
    var totalFaults = 0

    for (i in pages.indices) {
        val page = pages[i]
        if (frames.contains(page)) {
            steps.add(PageStep(page, frames.toList(), false, message = hitMessage(page, frames)))
            continue
        }
        totalFaults++
        val nullIdx = frames.indexOf(null)
        if (nullIdx != -1) {
            frames[nullIdx] = page
            steps.add(PageStep(page, frames.toList(), true, message = loadMessage(page, nullIdx)))
        } else {
            var victimIdx = 0
            var maxNextUse = -1

            for (fIdx in frames.indices) {
                val framePage = frames[fIdx]!!
                var nextUse = Int.MAX_VALUE
                for (j in (i + 1) until pages.size) {
                    if (pages[j] == framePage) {
                        nextUse = j
                        break
                    }
                }
                if (nextUse > maxNextUse) {
                    maxNextUse = nextUse
                    victimIdx = fIdx
                }
            }
            val victim = frames[victimIdx]!!
            frames[victimIdx] = page
            val reason = if (maxNextUse == Int.MAX_VALUE) "it is never needed again"
            else "it is needed furthest in the future (next at request #${maxNextUse + 1})"
            steps.add(PageStep(page, frames.toList(), true, victim, evictMessage(page, victim, victimIdx, reason)))
        }
    }

    val hitRatio = (pages.size - totalFaults).toDouble() / pages.size
    return PageResult(steps, totalFaults, hitRatio)
}
