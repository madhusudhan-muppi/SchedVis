package com.example.schedvis

import com.example.schedvis.engine.QueuePolicy
import com.example.schedvis.scan.ScanException
import com.example.schedvis.scan.parseScannedQuestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanParserTest {

    @Test fun plainJson() {
        val q = parseScannedQuestion(
            """{"kind":"cpu","algorithm":"ROUND_ROBIN","processes":[{"arrival":0,"burst":24,"priority":null,"queue":null},
               {"arrival":0,"burst":3,"priority":null,"queue":null}],"quantum":4,"queues":[],"references":[],"frames":null,"notes":""}"""
        )
        assertEquals("cpu", q.kind)
        assertEquals("ROUND_ROBIN", q.algorithm)
        assertEquals(2, q.processes.size)
        assertEquals(24, q.processes[0].burst)
        assertNull(q.processes[0].priority)
        assertEquals(4, q.quantum)
    }

    @Test fun codeFencesAndStringNumbers() {
        val q = parseScannedQuestion(
            "Here is the data:\n```json\n" +
                    """{"kind":"cpu","algorithm":"mlfq","processes":[{"arrival":"2","burst":"7"}],""" +
                    """"queues":[{"policy":"RR","quantum":8},{"policy":"FCFS","quantum":null}],"notes":null}""" +
                    "\n```"
        )
        assertEquals("MLFQ", q.algorithm)
        assertEquals(2, q.processes[0].arrival)
        assertEquals(7, q.processes[0].burst)
        assertEquals(listOf(QueuePolicy.RR, QueuePolicy.FCFS), q.queues.map { it.policy })
        assertEquals(8, q.queues[0].quantum)
        assertEquals("", q.notes)
    }

    @Test fun pageQuestion() {
        val q = parseScannedQuestion(
            """{"kind":"page","algorithm":"LRU","processes":[],"references":[7,0,1,"2"],"frames":3}"""
        )
        assertEquals("page", q.kind)
        assertEquals(listOf(7, 0, 1, 2), q.references)
        assertEquals(3, q.frames)
    }

    @Test fun unexpectedKindBecomesUnknown() {
        assertEquals("unknown", parseScannedQuestion("""{"kind":"disk"}""").kind)
    }

    @Test(expected = ScanException::class) fun noJson() {
        parseScannedQuestion("Sorry, I cannot read this image.")
    }

    @Test(expected = ScanException::class) fun brokenJson() {
        parseScannedQuestion("""{"kind":"cpu", "processes": [ }""")
    }
}
