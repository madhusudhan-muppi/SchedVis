package com.example.schedvis

import com.example.schedvis.engine.Process
import com.example.schedvis.engine.fcfs
import com.example.schedvis.engine.fifo
import com.example.schedvis.engine.lru
import com.example.schedvis.engine.optimal
import com.example.schedvis.engine.priorityNonPreemptive
import com.example.schedvis.engine.priorityPreemptive
import com.example.schedvis.engine.roundRobin
import com.example.schedvis.engine.sjf
import com.example.schedvis.engine.srtf
import org.junit.Assert.assertEquals
import org.junit.Test

// Expected answers are the worked examples from Galvin, Operating System Concepts.
class ExampleUnitTest {

    private fun gantt(slots: List<com.example.schedvis.engine.GanttSlot>) =
        slots.map { "${it.pid}:${it.start}-${it.end}" }

    @Test fun fcfsAndRoundRobin() {
        val p = listOf(Process("P1", 0, 24), Process("P2", 0, 3), Process("P3", 0, 3))
        assertEquals(17.0, fcfs(p).avgWaiting, 0.01)
        assertEquals(5.67, roundRobin(p, 4).avgWaiting, 0.01)
    }

    @Test fun sjfNonPreemptive() {
        val p = listOf(Process("P1", 0, 6), Process("P2", 0, 8), Process("P3", 0, 7), Process("P4", 0, 3))
        assertEquals(7.0, sjf(p).avgWaiting, 0.01)
    }

    @Test fun srtfPreemptive() {
        val p = listOf(Process("P1", 0, 8), Process("P2", 1, 4), Process("P3", 2, 9), Process("P4", 3, 5))
        val r = srtf(p)
        assertEquals(6.5, r.avgWaiting, 0.01)
        assertEquals(listOf("P1:0-1", "P2:1-5", "P4:5-10", "P1:10-17", "P3:17-26"), gantt(r.timeline))
    }

    @Test fun priorityNonPreemptiveGalvin() {
        val p = listOf(
            Process("P1", 0, 10, 3), Process("P2", 0, 1, 1), Process("P3", 0, 2, 4),
            Process("P4", 0, 1, 5), Process("P5", 0, 5, 2)
        )
        assertEquals(8.2, priorityNonPreemptive(p).avgWaiting, 0.01)
    }

    @Test fun priorityPreemptiveHandWorked() {
        val p = listOf(
            Process("P1", 0, 5, 3), Process("P2", 1, 3, 1),
            Process("P3", 2, 8, 4), Process("P4", 3, 6, 2)
        )
        val r = priorityPreemptive(p)
        assertEquals(5.5, r.avgWaiting, 0.01)
        assertEquals(11.0, r.avgTurnaround, 0.01)
        assertEquals(listOf("P1:0-1", "P2:1-4", "P4:4-10", "P1:10-14", "P3:14-22"), gantt(r.timeline))
    }

    @Test fun pageAlgorithms() {
        val ref = listOf(7, 0, 1, 2, 0, 3, 0, 4, 2, 3, 0, 3, 2, 1, 2, 0, 1, 7, 0, 1)
        assertEquals(15, fifo(ref, 3).totalFaults)
        assertEquals(12, lru(ref, 3).totalFaults)
        assertEquals(9, optimal(ref, 3).totalFaults)
    }
}