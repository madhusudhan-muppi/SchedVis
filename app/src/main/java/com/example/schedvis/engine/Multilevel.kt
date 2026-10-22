package com.example.schedvis.engine

// ==========================================
// MULTILEVEL QUEUE SCHEDULING
// ==========================================

enum class QueuePolicy { RR, FCFS }

// One ready queue. quantum is only used by RR queues.
data class QueueConfig(val policy: QueuePolicy, val quantum: Int = 0) {
    val label get() = if (policy == QueuePolicy.RR) "RR (q = $quantum)" else "FCFS"
}

// Multilevel Queue: each process stays in the queue it was assigned to (Process.queue, 0 = highest).
fun mlq(processes: List<Process>, queues: List<QueueConfig>): SchedulingResult =
    runMultilevel(processes, queues, feedback = false)

// Multilevel Feedback Queue: every process starts in the top queue and is demoted one
// level each time it uses up a full quantum without finishing.
fun mlfq(processes: List<Process>, queues: List<QueueConfig>): SchedulingResult =
    runMultilevel(processes, queues, feedback = true)

// Shared simulation, one time unit at a time.
// - The CPU always serves the head of the highest non-empty queue.
// - Queues have fixed priority and preempt each other: when a process arrives in a higher queue,
//   the running lower-queue process goes back to the TAIL of its own queue (no demotion) and gets
//   a fresh quantum the next time it runs.
// - In an RR queue, a process that uses its full quantum goes to the tail of the same queue (MLQ)
//   or of the next lower queue (MLFQ; the last queue keeps it). Processes arriving at the same
//   moment join their queue before it, as in plain Round Robin.
// - In a FCFS queue, a process runs until it finishes or is preempted.
private fun runMultilevel(
    processes: List<Process>,
    queues: List<QueueConfig>,
    feedback: Boolean
): SchedulingResult {
    if (processes.isEmpty() || queues.isEmpty()) return EMPTY_SCHEDULE
    if (queues.any { it.policy == QueuePolicy.RR && it.quantum <= 0 }) return EMPTY_SCHEDULE

    val timeline = mutableListOf<GanttSlot>()
    val completionTimes = mutableMapOf<String, Int>()
    val remBurst = processes.associate { it.id to it.burst }.toMutableMap()
    val level = mutableMapOf<String, Int>()
    val levels = List(queues.size) { ArrayDeque<Process>() }
    val unarrived = processes.sortedWith(compareBy<Process>({ it.arrival }, { it.id })).toMutableList()
    val trace = Trace(processes)

    var time = 0
    var finished = 0
    var running: Process? = null
    var used = 0   // time units of the current quantum already used by the running process

    fun snapshot() = levels.map { q -> q.map { it.id } }
    fun qName(i: Int) = "Q${i + 1}"

    // Moves processes that have arrived by now into their starting queue.
    fun admitArrivals() {
        val arriving = unarrived.filter { it.arrival <= time }
        if (arriving.isEmpty()) return
        unarrived.removeAll(arriving)
        val parts = mutableListOf<String>()
        for (p in arriving) {
            if (p.burst <= 0) {
                completionTimes[p.id] = p.arrival
                finished++
                continue
            }
            val start = if (feedback) 0 else p.queue.coerceIn(0, queues.lastIndex)
            level[p.id] = start
            levels[start].addLast(p)
            parts.add("${p.id} → ${qName(start)}")
        }
        if (parts.isNotEmpty()) {
            val verb = if (parts.size == 1) "Arrival" else "Arrivals"
            trace.add(time, running?.id, snapshot(), "$verb: ${parts.joinToString(", ")}.")
        }
    }

    admitArrivals()
    while (finished < processes.size) {
        // A process waiting in a higher queue takes the CPU from a lower-queue process.
        running?.let { p ->
            val top = levels.indexOfFirst { it.isNotEmpty() }
            val current = level[p.id]!!
            if (top != -1 && top < current) {
                levels[current].addLast(p)
                running = null
                trace.add(
                    time, null, snapshot(),
                    "${levels[top].first().id} is waiting in higher-priority ${qName(top)}, so ${p.id} is preempted " +
                            "and goes back to the tail of ${qName(current)} with ${remBurst[p.id]} left."
                )
            }
        }

        if (running == null) {
            val top = levels.indexOfFirst { it.isNotEmpty() }
            if (top == -1) {
                val nextArrival = unarrived.first().arrival
                trace.add(time, null, snapshot(), "All queues are empty: CPU is idle until t=$nextArrival.")
                timeline.addSlot(null, time, nextArrival)
                time = nextArrival
                admitArrivals()
                continue
            }
            val p = levels[top].removeFirst()
            running = p
            used = 0
            val higher = if (top > 0) " (all higher queues are empty)" else ""
            val plan = when (queues[top].policy) {
                QueuePolicy.RR -> "It may run for up to ${queues[top].quantum} (RR quantum); ${remBurst[p.id]} left."
                QueuePolicy.FCFS -> "${qName(top)} is FCFS, so it runs until it finishes (${remBurst[p.id]} left) " +
                        "unless a higher queue gets a process."
            }
            trace.add(time, p.id, snapshot(), "${p.id} is at the head of ${qName(top)}$higher and gets the CPU. $plan")
        }

        val p = running!!
        val current = level[p.id]!!
        timeline.addSlot(p.id, time, time + 1, current)
        time++
        used++
        remBurst[p.id] = remBurst[p.id]!! - 1

        admitArrivals()

        val config = queues[current]
        if (remBurst[p.id] == 0) {
            completionTimes[p.id] = time
            finished++
            running = null
            trace.finished(p, time, snapshot())
        } else if (config.policy == QueuePolicy.RR && used == config.quantum) {
            val next = if (feedback) minOf(current + 1, queues.lastIndex) else current
            level[p.id] = next
            levels[next].addLast(p)
            running = null
            val message = if (next != current) {
                "${p.id} used its full quantum of ${config.quantum} in ${qName(current)} with ${remBurst[p.id]} left, " +
                        "so it is demoted to ${qName(next)}."
            } else {
                "${p.id}'s quantum expires with ${remBurst[p.id]} left, so it goes to the tail of ${qName(current)}."
            }
            trace.add(time, null, snapshot(), message)
        }
    }
    return buildResult(processes, timeline, completionTimes, trace)
}
