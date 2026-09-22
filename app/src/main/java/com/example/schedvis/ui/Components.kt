package com.example.schedvis.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.schedvis.engine.GanttSlot
import com.example.schedvis.engine.PageStep

// ==========================================
// COLORS
// ==========================================

// One color per process: P1 gets the first color, P2 the second, and so on.
val ProcessPalette = listOf(
    Color(0xFF1E88E5), // blue
    Color(0xFF43A047), // green
    Color(0xFFF4511E), // orange
    Color(0xFF8E24AA), // purple
    Color(0xFF00897B), // teal
    Color(0xFFD81B60), // pink
    Color(0xFF3949AB), // indigo
    Color(0xFF6D4C41), // brown
    Color(0xFF00ACC1), // cyan
    Color(0xFF7CB342)  // light green
)
val IdleColor = Color(0xFF9E9E9E)
val FaultColor = Color(0xFFE53935)
val HitColor = Color(0xFF43A047)

fun colorFor(pid: String?, order: List<String>): Color {
    if (pid == null) return IdleColor
    val index = order.indexOf(pid)
    return if (index >= 0) ProcessPalette[index % ProcessPalette.size] else IdleColor
}

// ==========================================
// INPUT COMPONENTS
// ==========================================

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

// A text field that only accepts digits (max 3), so bad input can never crash the app.
@Composable
fun NumberField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { new ->
            if (new.length <= 3 && new.all { it.isDigit() }) onChange(new)
        },
        label = { Text(label, maxLines = 1) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

// Buttons in rows of three: the selected option is filled, the others are outlined.
@Composable
fun OptionSelector(
    options: List<String>,
    selected: String,
    perRow: Int = 3,
    onSelect: (String) -> Unit
) {
    val padding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(perRow).forEach { rowOptions ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowOptions.forEach { option ->
                    if (option == selected) {
                        Button(
                            onClick = { onSelect(option) },
                            modifier = Modifier.weight(1f),
                            contentPadding = padding
                        ) { Text(option, maxLines = 1) }
                    } else {
                        OutlinedButton(
                            onClick = { onSelect(option) },
                            modifier = Modifier.weight(1f),
                            contentPadding = padding
                        ) { Text(option, maxLines = 1) }
                    }
                }
                // Keep button widths equal when the last row is not full.
                repeat(perRow - rowOptions.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun ProcessBadge(label: String, color: Color) {
    Box(
        Modifier.size(40.dp).background(color, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

// ==========================================
// OUTPUT COMPONENTS
// ==========================================

// Gantt chart: one colored block per time slot, width proportional to its duration.
// Time labels sit under the left edge of each block, plus the final end time.
@Composable
fun GanttChart(timeline: List<GanttSlot>, processOrder: List<String>) {
    val unitWidth = 32.dp
    val blockHeight = 52.dp
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        timeline.forEach { slot ->
            Column(Modifier.width(unitWidth * (slot.end - slot.start))) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(blockHeight)
                        .background(colorFor(slot.pid, processOrder))
                        .border(1.dp, MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        slot.pid ?: "Idle",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                }
                Text(slot.start.toString(), fontSize = 12.sp)
            }
        }
        timeline.lastOrNull()?.let { last ->
            Column {
                Spacer(Modifier.height(blockHeight))
                Text(last.end.toString(), fontSize = 12.sp)
            }
        }
    }
}

// Page replacement table: one column per page request, showing frame contents after it.
// The frame that received a new page on a fault is tinted red.
@Composable
fun FrameTable(steps: List<PageStep>) {
    val cell = 40.dp
    val frameCount = steps.firstOrNull()?.frames?.size ?: 0
    val outline = MaterialTheme.colorScheme.outline

    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Row labels
        Column {
            Box(Modifier.size(cell), contentAlignment = Alignment.Center) {
                Text("Page", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            for (f in 0 until frameCount) {
                Box(Modifier.size(cell), contentAlignment = Alignment.Center) {
                    Text("F${f + 1}", fontSize = 12.sp)
                }
            }
            Box(Modifier.size(cell))
        }
        // One column per request
        steps.forEach { step ->
            Column {
                Box(Modifier.size(cell), contentAlignment = Alignment.Center) {
                    Text(step.page.toString(), fontWeight = FontWeight.Bold)
                }
                step.frames.forEach { value ->
                    val isNewPage = step.isFault && value == step.page
                    Box(
                        Modifier
                            .size(cell)
                            .background(if (isNewPage) FaultColor.copy(alpha = 0.25f) else Color.Transparent)
                            .border(1.dp, outline),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(value?.toString() ?: "")
                    }
                }
                Box(Modifier.size(cell), contentAlignment = Alignment.Center) {
                    Text(
                        if (step.isFault) "F" else "H",
                        color = if (step.isFault) FaultColor else HitColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

// A simple table row with equal-width columns. The first cell can be colored.
@Composable
fun DataRow(cells: List<String>, header: Boolean = false, accent: Color? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        cells.forEachIndexed { index, cell ->
            Text(
                cell,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontWeight = if (header || index == 0) FontWeight.Bold else FontWeight.Normal,
                color = if (index == 0 && accent != null) accent else Color.Unspecified
            )
        }
    }
}