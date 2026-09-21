package com.example.schedvis

import org.junit.Test
import com.example.schedvis.engine.*
import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }
    @Test fun cpuAlgorithms() {
        val p = listOf(Process("P1",0,24), Process("P2",0,3), Process("P3",0,3))
        assertEquals(17.0, fcfs(p).avgWaiting, 0.01)
        assertEquals(5.67, roundRobin(p, 4).avgWaiting, 0.01)
        val s = listOf(Process("P1",0,6), Process("P2",0,8), Process("P3",0,7), Process("P4",0,3))
        assertEquals(7.0, sjf(s).avgWaiting, 0.01)
    }
    @Test fun pageAlgorithms() {
        val ref = listOf(7,0,1,2,0,3,0,4,2,3,0,3,2,1,2,0,1,7,0,1)
        assertEquals(15, fifo(ref, 3).totalFaults)
        assertEquals(12, lru(ref, 3).totalFaults)
        assertEquals(9, optimal(ref, 3).totalFaults)
    }
}