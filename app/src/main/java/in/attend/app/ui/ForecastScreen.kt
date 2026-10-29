package `in`.attend.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.attend.app.data.AppData
import `in`.attend.core.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun ForecastScreen(data: AppData, startingCounts: () -> Unit, calendar: () -> Unit) {
    val base = data.baseline
    var scenario by remember { mutableStateOf(Scenario.PLANNED_ABSENCES) }
    var end by remember(base?.asOf) { mutableStateOf(LocalDate.now().plusMonths(3).coerceAtLeast(base?.asOf ?: LocalDate.now())) }
    val result = remember(base, end, scenario, data.schedule, data.rules) {
        base?.let { runCatching { ForecastEngine.forecast(it, end, scenario, data.schedule, data.rules) } }
    }
    Page("Your attendance plan", "Forecast / based on your entries") {
        if (base == null) {
            InfoPanel("Give your forecast a starting point", "Mark completed periods present or absent, or enter the cumulative numbers from your latest college attendance sheet.")
            Button(onClick = calendar, modifier = Modifier.fillMaxWidth()) { Text("Log attendance") }
            OutlinedButton(onClick = startingCounts, modifier = Modifier.fillMaxWidth()) { Text("Enter sheet numbers") }
        } else {
            Text("Starting from ${base.counts.attended} / ${base.counts.held} recorded classes · ${percent(base.counts.percentage)}", style = MaterialTheme.typography.bodyMedium)
            DateButton("Forecast until", end, { end = it }, Modifier.fillMaxWidth())
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Scenario.entries.forEach { option -> FilterChip(selected = scenario == option, onClick = { scenario = option }, label = { Text(option.title) }) }
            }
            if (result?.isFailure == true) InfoPanel("Check forecast date", result.exceptionOrNull()?.message ?: "Cannot calculate forecast")
            result?.getOrNull()?.let { forecast ->
                val all = ForecastEngine.forecast(base, end, Scenario.ATTEND_ALL, data.schedule, data.rules)
                val missed = if (scenario == Scenario.PLANNED_ABSENCES) forecast.futureHeld - (forecast.futureAttended ?: 0.0).toInt() else null
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.extraLarge) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("PROJECTED ATTENDANCE", style = MaterialTheme.typography.labelSmall)
                        Text(percent(forecast.percentage), fontSize = 52.sp, letterSpacing = (-2).sp)
                        Text("Through ${forecast.through.format(ShortDate)} · ${forecast.futureHeld} future sessions", style = MaterialTheme.typography.bodyMedium)
                        Text("${forecast.futureAttended?.let { "%.1f".format(it) } ?: "Unknown"} estimated future attendances", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (missed != null) InfoPanel("${missed} planned missed periods", "With your plan: ${percent(forecast.percentage)}. Attend all: ${percent(all.percentage)}. Holidays and cancelled classes are excluded from both.")
                if (data.tracking().unmarked > 0) InfoPanel("${data.tracking().unmarked} unmarked past periods", "Tracked totals include only your marked periods and starting counts. Complete your log for a more useful forecast.")
                OutlinedButton(onClick = calendar, modifier = Modifier.fillMaxWidth()) { Text("Edit absences in Calendar") }
                AttendanceChart(forecast.points, data.targetBps / 100.0)
                Text("Dashed line: ${targetLabel(data.targetBps)} target", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                InfoPanel("Forecast assumptions", "Estimates start after ${base.asOf.format(ShortDate)}. My plan assumes you attend all sessions except the absences you enter. Unreported intervening days are estimates too. ${if (data.teachingEnd == null && base.termEnd == null) "Timetable continues until your chosen date; set term end in Settings." else "Stops at teaching term end."}")
                if (scenario == Scenario.PLANNED_ABSENCES) InfoPanel("Plan absences in Calendar", "All remaining sessions are treated as attended. Planned absences reduce attendance; closures and cancellations remove sessions entirely.")
                if (forecast.subjects.isNotEmpty()) Text("Subject projections", style = MaterialTheme.typography.titleLarge)
                forecast.subjects.forEach { s ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) { Text(s.name, style = MaterialTheme.typography.bodyMedium); Text("${s.futureHeld} future sessions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Text(percent(s.percentage), style = MaterialTheme.typography.titleMedium)
                    }
                }
                Text("Subject projections appear only when you enter per-subject sheet counts that match your timetable.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AttendanceChart(points: List<ForecastPoint>, target: Double) {
    val primary = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val description = points.joinToString { "${it.date}: ${percent(it.percentage)}" }
    Column {
        Canvas(Modifier.fillMaxWidth().height(160.dp).semantics { contentDescription = description }) {
            val top = 10.dp.toPx(); val bottom = size.height - 10.dp.toPx()
            fun y(value: Double) = bottom - (value.coerceIn(0.0, 100.0) / 100.0 * (bottom - top)).toFloat()
            listOf(0.0, 25.0, 50.0, 75.0, 100.0).forEach { drawLine(grid, Offset(0f, y(it)), Offset(size.width, y(it)), 1.dp.toPx()) }
            drawLine(primary.copy(alpha = .6f), Offset(0f, y(target)), Offset(size.width, y(target)), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
            if (points.isNotEmpty()) {
                val start = points.first().date.toEpochDay(); val span = (points.last().date.toEpochDay() - start).coerceAtLeast(1)
                val path = Path(); var active = false
                points.forEach { p ->
                    val value = p.percentage
                    if (value == null) { active = false } else {
                        val x = (p.date.toEpochDay() - start).toFloat() / span * size.width
                        if (!active) path.moveTo(x, y(value)) else path.lineTo(x, y(value))
                        active = true; drawCircle(primary, 4.dp.toPx(), Offset(x, y(value)))
                    }
                }
                drawPath(path, primary, style = Stroke(width = 3.dp.toPx()))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            points.firstOrNull()?.let { Text(it.date.format(DateTimeFormatter.ofPattern("d MMM")), style = MaterialTheme.typography.bodySmall) }
            points.lastOrNull()?.let { Text(it.date.format(DateTimeFormatter.ofPattern("d MMM")), style = MaterialTheme.typography.bodySmall) }
        }
    }
}
