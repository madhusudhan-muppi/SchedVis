package com.example.schedvis.engine

// ==========================================
// DATA CLASSES
// ==========================================

data class Process(val id: String, val arrival: Int, val burst: Int)

data class GanttSlot(val pid: String?, val start: Int, val end: Int)   // pid = null means CPU idle

data class ProcessResult(val pid: String, val completion: Int, val turnaround: Int, val waiting: Int)

data class SchedulingResult(
    val timeline: List<GanttSlot>,
    val perProcess: List<ProcessResult>,
    val avgTurnaround: Double,
    val avgWaiting: Double
)

data class PageStep(val page: Int, val frames: List<Int?>, val isFault: Boolean)   // frames = contents AFTER request, null = empty

data class PageResult(val steps: List<PageStep>, val totalFaults: Int, val hitRatio: Double)

// ==========================================
// HELPER EXTENSIONS
// ==========================================

private fun MutableList<GanttSlot>.addSlot(pid: String?, start: Int, end: Int) {
    if (start == end) return
    if (isNotEmpty() && last().pid == pid) {
        val prev = removeAt(lastIndex)
        add(GanttSlot(pid, prev.start, end))
    } else {
        add(GanttSlot(pid, start, end))
    }
}

// ==========================================
// CPU SCHEDULING ALGORITHMS
// ==========================================

fun fcfs(processes: List<Process>): SchedulingResult {
    if (processes.isEmpty()) return SchedulingResult(emptyList(), emptyList(), 0.0, 0.0)

    val sorted = processes.sortedWith(compareBy<Process> { it.arrival }.thenBy { it.id })
    val timeline = mutableListOf<GanttSlot>()
    val completionTimes = mutableMapOf<String, Int>()
    var currentTime = 0

    for (p in sorted) {
        if (currentTime < p.arrival) {
            timeline.addSlot(null, currentTime, p.arrival)
            currentTime = p.arrival
        }
        val nextTime = currentTime + p.burst
        timeline.addSlot(p.id, currentTime, nextTime)
        currentTime = nextTime
        completionTimes[p.id] = currentTime
    }

    val perProcess = processes.map { p ->
        val completion = completionTimes[p.id] ?: 0
        val turnaround = completion - p.arrival
        val waiting = turnaround - p.burst
        ProcessResult(p.id, completion, turnaround, waiting)
    }

    val avgTurnaround = perProcess.map { it.turnaround }.average()
    val avgWaiting = perProcess.map { it.waiting }.average()

    return SchedulingResult(timeline, perProcess, avgTurnaround, avgWaiting)
}

fun sjf(processes: List<Process>): SchedulingResult {
    if (processes.isEmpty()) return SchedulingResult(emptyList(), emptyList(), 0.0, 0.0)

    val timeline = mutableListOf<GanttSlot>()
    val completionTimes = mutableMapOf<String, Int>()
    val remaining = processes.toMutableList()
    var currentTime = 0

    while (remaining.isNotEmpty()) {
        val available = remaining.filter { it.arrival <= currentTime }
        if (available.isEmpty()) {
            val nextArrival = remaining.minOf { it.arrival }
            timeline.addSlot(null, currentTime, nextArrival)
            currentTime = nextArrival
        } else {
            val selected = available.minWithOrNull(
                compareBy<Process> { it.burst }
                    .thenBy { it.arrival }
                    .thenBy { it.id }
            )!!
            remaining.remove(selected)
            val nextTime = currentTime + selected.burst
            timeline.addSlot(selected.id, currentTime, nextTime)
            currentTime = nextTime
            completionTimes[selected.id] = currentTime
        }
    }

    val perProcess = processes.map { p ->
        val completion = completionTimes[p.id] ?: 0
        val turnaround = completion - p.arrival
        val waiting = turnaround - p.burst
        ProcessResult(p.id, completion, turnaround, waiting)
    }

    val avgTurnaround = perProcess.map { it.turnaround }.average()
    val avgWaiting = perProcess.map { it.waiting }.average()

    return SchedulingResult(timeline, perProcess, avgTurnaround, avgWaiting)
}

fun roundRobin(processes: List<Process>, quantum: Int): SchedulingResult {
    if (processes.isEmpty() || quantum <= 0) return SchedulingResult(emptyList(), emptyList(), 0.0, 0.0)

    val timeline = mutableListOf<GanttSlot>()
    val completionTimes = mutableMapOf<String, Int>()
    val remBurst = processes.associate { it.id to it.burst }.toMutableMap()

    val unarrived = processes.sortedWith(compareBy<Process> { it.arrival }.thenBy { it.id }).toMutableList()
    val readyQueue = ArrayDeque<Process>()
    var currentTime = 0

    if (unarrived.isNotEmpty() && unarrived.first().arrival > 0) {
        timeline.addSlot(null, 0, unarrived.first().arrival)
        currentTime = unarrived.first().arrival
    }

    val initialArrivals = unarrived.filter { it.arrival <= currentTime }
    readyQueue.addAll(initialArrivals)
    unarrived.removeAll(initialArrivals)

    while (readyQueue.isNotEmpty() || unarrived.isNotEmpty()) {
        if (readyQueue.isEmpty()) {
            val nextArrival = unarrived.first().arrival
            timeline.addSlot(null, currentTime, nextArrival)
            currentTime = nextArrival
            val newArrivals = unarrived.filter { it.arrival <= currentTime }
            readyQueue.addAll(newArrivals)
            unarrived.removeAll(newArrivals)
        }

        val currentProc = readyQueue.removeFirst()
        val burstTime = remBurst[currentProc.id]!!
        val executeTime = minOf(quantum, burstTime)
        val nextTime = currentTime + executeTime

        timeline.addSlot(currentProc.id, currentTime, nextTime)
        remBurst[currentProc.id] = burstTime - executeTime

        val arrivedDuring = unarrived.filter { it.arrival in (currentTime + 1)..nextTime }
        readyQueue.addAll(arrivedDuring)
        unarrived.removeAll(arrivedDuring)

        if (remBurst[currentProc.id]!! > 0) {
            readyQueue.addLast(currentProc)
        } else {
            completionTimes[currentProc.id] = nextTime
        }

        currentTime = nextTime
    }

    val perProcess = processes.map { p ->
        val completion = completionTimes[p.id] ?: 0
        val turnaround = completion - p.arrival
        val waiting = turnaround - p.burst
        ProcessResult(p.id, completion, turnaround, waiting)
    }

    val avgTurnaround = perProcess.map { it.turnaround }.average()
    val avgWaiting = perProcess.map { it.waiting }.average()

    return SchedulingResult(timeline, perProcess, avgTurnaround, avgWaiting)
}

// ==========================================
// PAGE REPLACEMENT ALGORITHMS
// ==========================================

fun fifo(pages: List<Int>, frameCount: Int): PageResult {
    if (pages.isEmpty() || frameCount <= 0) return PageResult(emptyList(), 0, 0.0)

    val frames = MutableList<Int?>(frameCount) { null }
    val steps = mutableListOf<PageStep>()
    var totalFaults = 0
    val fifoQueue = ArrayDeque<Int>()

    for (page in pages) {
        val isFault: Boolean
        if (frames.contains(page)) {
            isFault = false
        } else {
            isFault = true
            totalFaults++
            val nullIdx = frames.indexOf(null)
            if (nullIdx != -1) {
                frames[nullIdx] = page
                fifoQueue.addLast(nullIdx)
            } else {
                val victimIdx = fifoQueue.removeFirst()
                frames[victimIdx] = page
                fifoQueue.addLast(victimIdx)
            }
        }
        steps.add(PageStep(page, frames.toList(), isFault))
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
        val isFault: Boolean
        if (frames.contains(page)) {
            isFault = false
            lastUsed[page] = stepIdx
        } else {
            isFault = true
            totalFaults++
            val nullIdx = frames.indexOf(null)
            if (nullIdx != -1) {
                frames[nullIdx] = page
            } else {
                val victimIdx = frames.indices.minByOrNull { idx ->
                    val p = frames[idx]!!
                    lastUsed[p] ?: -1
                }!!
                frames[victimIdx] = page
            }
            lastUsed[page] = stepIdx
        }
        steps.add(PageStep(page, frames.toList(), isFault))
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
        val isFault: Boolean
        if (frames.contains(page)) {
            isFault = false
        } else {
            isFault = true
            totalFaults++
            val nullIdx = frames.indexOf(null)
            if (nullIdx != -1) {
                frames[nullIdx] = page
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
                frames[victimIdx] = page
            }
        }
        steps.add(PageStep(page, frames.toList(), isFault))
    }

    val hitRatio = (pages.size - totalFaults).toDouble() / pages.size
    return PageResult(steps, totalFaults, hitRatio)
}