package com.example.schedvis.scan

import com.example.schedvis.engine.QueuePolicy
import org.json.JSONException
import org.json.JSONObject

// ==========================================
// WHAT THE VISION API READ FROM THE PHOTO
// ==========================================

// Values the photo did not show are null; the input screens then keep their defaults
// or report a validation error the user can fix by hand.
data class ScannedProcess(val arrival: Int?, val burst: Int?, val priority: Int?, val queue: Int?)

data class ScannedQueue(val policy: QueuePolicy, val quantum: Int?)

data class ScannedQuestion(
    val kind: String,                  // "cpu", "page" or "unknown"
    val algorithm: String?,            // e.g. "ROUND_ROBIN", "MLFQ", "LRU"
    val processes: List<ScannedProcess>,
    val quantum: Int?,
    val queues: List<ScannedQueue>,
    val references: List<Int>,
    val frames: Int?,
    val notes: String
)

class ScanException(message: String) : Exception(message)

// The instruction sent with the photo. It asks for one JSON object in a fixed shape.
const val SCAN_PROMPT = """You are reading a photo of an operating systems exercise about CPU scheduling or page replacement.
Extract the input data exactly as written. Do not solve the exercise.

Reply with ONLY one JSON object, no other text, in this shape:
{
  "kind": "cpu" | "page" | "unknown",
  "algorithm": one of "FCFS", "SJF", "SRTF", "PRIORITY_NP", "PRIORITY_P", "ROUND_ROBIN", "MLQ", "MLFQ", "FIFO", "LRU", "OPTIMAL", or null,
  "processes": [ { "arrival": int or null, "burst": int or null, "priority": int or null, "queue": int or null } ],
  "quantum": int or null,
  "queues": [ { "policy": "RR" | "FCFS", "quantum": int or null } ],
  "references": [ int ],
  "frames": int or null,
  "notes": "short remarks, e.g. assumptions or other algorithms the question also asks for"
}

Rules:
- "processes" are in the order P1, P2, P3, ... as listed. If no arrival times are given, use 0.
- "priority": a lower number means higher priority. If the question says a higher number means higher priority, convert it and say so in notes.
- "queue" is the 1-based queue number a process belongs to (MLQ only); "queues" lists the queues from highest to lowest priority.
- Preemptive SJF is "SRTF". Non-preemptive priority is "PRIORITY_NP", preemptive is "PRIORITY_P".
- If several algorithms are asked for, pick the first one and list the others in notes.
- For page replacement, put the reference string in "references" and the frame count in "frames"; leave "processes" empty.
- If the photo is not one of these exercises, use "kind": "unknown" and explain in notes."""

// ==========================================
// PARSING
// ==========================================

private fun JSONObject.int(key: String): Int? {
    if (!has(key) || isNull(key)) return null
    return when (val v = get(key)) {
        is Number -> v.toInt()
        is String -> v.trim().toIntOrNull()
        else -> null
    }
}

private fun JSONObject.str(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).trim().ifEmpty { null }

// Reads the model's reply. Tolerates code fences or extra text around the JSON object.
fun parseScannedQuestion(reply: String): ScannedQuestion {
    val start = reply.indexOf('{')
    val end = reply.lastIndexOf('}')
    if (start == -1 || end <= start) {
        throw ScanException("The API reply did not contain the question data. Try a clearer photo.")
    }
    val json = try {
        JSONObject(reply.substring(start, end + 1))
    } catch (e: JSONException) {
        throw ScanException("The API reply could not be read. Try again or use a clearer photo.")
    }

    val processes = json.optJSONArray("processes")?.let { arr ->
        (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let {
                ScannedProcess(it.int("arrival"), it.int("burst"), it.int("priority"), it.int("queue"))
            }
        }
    } ?: emptyList()

    val queues = json.optJSONArray("queues")?.let { arr ->
        (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let {
                val policy = if (it.str("policy")?.uppercase()?.startsWith("FCFS") == true) QueuePolicy.FCFS
                else QueuePolicy.RR
                ScannedQueue(policy, it.int("quantum"))
            }
        }
    } ?: emptyList()

    val references = json.optJSONArray("references")?.let { arr ->
        (0 until arr.length()).mapNotNull { i ->
            when (val v = arr.opt(i)) {
                is Number -> v.toInt()
                is String -> v.trim().toIntOrNull()
                else -> null
            }
        }
    } ?: emptyList()

    val kind = when (json.str("kind")?.lowercase()) {
        "cpu" -> "cpu"
        "page" -> "page"
        else -> "unknown"
    }

    return ScannedQuestion(
        kind = kind,
        algorithm = json.str("algorithm")?.uppercase()?.replace(' ', '_'),
        processes = processes,
        quantum = json.int("quantum"),
        queues = queues,
        references = references,
        frames = json.int("frames"),
        notes = json.str("notes") ?: ""
    )
}
