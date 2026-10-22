package com.example.schedvis

import com.example.schedvis.engine.Process
import com.example.schedvis.engine.fcfs
import com.example.schedvis.engine.fifo
import com.example.schedvis.engine.QueueConfig
import com.example.schedvis.engine.QueuePolicy
import com.example.schedvis.engine.lru
import com.example.schedvis.engine.mlfq
import com.example.schedvis.engine.mlq
import com.example.schedvis.engine.optimal
import com.example.schedvis.engine.priorityNonPreemptive
import com.example.schedvis.engine.priorityPreemptive
import com.example.schedvis.engine.roundRobin
import com.example.schedvis.engine.sjf
import com.example.schedvis.engine.srtf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test fun pageVictims() {
        val ref = listOf(7, 0, 1, 2, 0, 3, 0, 4, 2, 3, 0, 3, 2, 1, 2, 0, 1, 7, 0, 1)
        assertEquals(7, fifo(ref, 3).steps[3].victim)
        assertEquals(1, lru(ref, 3).steps[5].victim)
        assertEquals(7, optimal(ref, 3).steps[3].victim)
        assertEquals(null, fifo(ref, 3).steps[4].victim)   // hit
    }

    private fun ganttQ(slots: List<com.example.schedvis.engine.GanttSlot>) =
        slots.map { "${it.pid}:${it.start}-${it.end}:Q${it.queue}" }

    @Test fun mlfqDemotesThroughEveryLevel() {
        val queues = listOf(QueueConfig(QueuePolicy.RR, 4), QueueConfig(QueuePolicy.RR, 8), QueueConfig(QueuePolicy.FCFS))
        val r = mlfq(listOf(Process("P1", 0, 20)), queues)
        assertEquals(listOf("P1:0-4:Q0", "P1:4-12:Q1", "P1:12-20:Q2"), ganttQ(r.timeline))
    }

    @Test fun mlfqArrivalPreemptsLowerLevel() {
        val queues = listOf(QueueConfig(QueuePolicy.RR, 2), QueueConfig(QueuePolicy.RR, 4), QueueConfig(QueuePolicy.FCFS))
        val r = mlfq(listOf(Process("P1", 0, 7), Process("P2", 3, 2)), queues)
        assertEquals(listOf("P1:0-2:Q0", "P1:2-3:Q1", "P2:3-5:Q0", "P1:5-9:Q1"), ganttQ(r.timeline))
        assertEquals(1.0, r.avgWaiting, 0.01)
    }

    @Test fun mlqSystemAndUserQueues() {
        val queues = listOf(QueueConfig(QueuePolicy.RR, 2), QueueConfig(QueuePolicy.FCFS))
        val p = listOf(Process("P1", 0, 4, queue = 1), Process("P2", 1, 3, queue = 0), Process("P3", 2, 2, queue = 0))
        val r = mlq(p, queues)
        assertEquals(listOf("P1:0-1", "P2:1-3", "P3:3-5", "P2:5-6", "P1:6-9"), gantt(r.timeline))
        assertEquals(2.67, r.avgWaiting, 0.01)
    }

    @Test fun singleQueueMatchesPlainAlgorithms() {
        val p = listOf(Process("P1", 0, 5), Process("P2", 1, 3), Process("P3", 2, 8), Process("P4", 3, 6))
        assertEquals(gantt(roundRobin(p, 2).timeline), gantt(mlq(p, listOf(QueueConfig(QueuePolicy.RR, 2))).timeline))
        assertEquals(gantt(fcfs(p).timeline), gantt(mlfq(p, listOf(QueueConfig(QueuePolicy.FCFS))).timeline))
    }

    @Test fun everyAlgorithmProducesOrderedSteps() {
        val p = listOf(Process("P1", 2, 5, 3, 1), Process("P2", 3, 3, 1, 0), Process("P3", 4, 8, 4, 0), Process("P4", 20, 6, 2, 1))
        val queues = listOf(QueueConfig(QueuePolicy.RR, 2), QueueConfig(QueuePolicy.FCFS))
        val results = listOf(
            fcfs(p), sjf(p), srtf(p), priorityNonPreemptive(p), priorityPreemptive(p),
            roundRobin(p, 2), mlq(p, queues), mlfq(p, queues)
        )
        for (r in results) {
            assertTrue(r.steps.isNotEmpty())
            assertEquals(r.timeline.last().end, r.steps.last().time)
            assertEquals(r.steps.map { it.time }.sorted(), r.steps.map { it.time })
        }
    }
}