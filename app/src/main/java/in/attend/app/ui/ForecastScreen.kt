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
fun ForecastScreen(data: AppData) {
    val base = data.baseline
    var scenario by remember { mutableStateOf(Scenario.CONTINUE_RATE) }
    var end by remember(base?.asOf) { mutableStateOf(LocalDate.now().plusMonths(3).coerceAtLeast(base?.asOf ?: LocalDate.now())) }
    val result = remember(base, end, scenario, data.schedule, data.rules) {
        base?.let { runCatching { ForecastEngine.forecast(it, end, scenario, data.schedule, data.rules) } }
    }
    Page("See what\ncomes next.", "Forecast / estimates") {
        if (base == null) {
            InfoPanel("Connect ERP first", "Your calendar works now. Import attended and held class counts to unlock personal forecasts.")
        } else {
            DateButton("Forecast until", end, { end = it }, Modifier.fillMaxWidth())
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Scenario.entries.forEach { option -> FilterChip(selected = scenario == option, onClick = { scenario = option }, label = { Text(option.title) }) }
            }
            if (result?.isFailure == true) InfoPanel("Check forecast date", result.exceptionOrNull()?.message ?: "Cannot calculate forecast")
            result?.getOrNull()?.let { forecast ->
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.extraLarge) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("PROJECTED ATTENDANCE", style = MaterialTheme.typography.labelSmall)
                        Text(percent(forecast.percentage), fontSize = 52.sp, letterSpacing = (-2).sp)
                        Text("Through ${forecast.through.format(ShortDate)} · ${forecast.futureHeld} future sessions", style = MaterialTheme.typography.bodyMedium)
                        Text("${forecast.futureAttended?.let { "%.1f".format(it) } ?: "Unknown"} estimated future attendances", style = MaterialTheme.typography.bodySmall)
                    }
                }
                AttendanceChart(forecast.points, data.targetBps / 100.0)
                Text("Dashed line: ${targetLabel(data.targetBps)} target", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                InfoPanel("Every future value is an estimate", "Starts after recorded report: ${base.asOf.format(ShortDate)}. Unreported days between that date and today are estimated too. ${if (data.teachingEnd == null && base.termEnd == null) "Assumes this timetable continues through the forecast date; set a teaching end date in Settings." else "Forecast stops at the teaching term end."}")
                if (scenario == Scenario.PLANNED_ABSENCES) InfoPanel("Plan absences in Calendar", "All remaining sessions are treated as attended. Planned absences reduce attendance; closures and cancellations remove sessions entirely.")
                if (forecast.subjects.isNotEmpty()) Text("Subject projections", style = MaterialTheme.typography.titleLarge)
                forecast.subjects.forEach { s ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) { Text(s.name, style = MaterialTheme.typography.bodyMedium); Text("${s.futureHeld} future sessions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Text(percent(s.percentage), style = MaterialTheme.typography.titleMedium)
                    }
                }
                Text("Subject projections appear only when imported counts can be matched to your timetable.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
