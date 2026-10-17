package `in`.attend.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.attend.app.data.AppData
import `in`.attend.core.AttendanceMath
import java.time.*
import java.time.temporal.ChronoUnit

@Composable
fun Dashboard(data: AppData, demo: Boolean, connect: () -> Unit, preview: () -> Unit, forecast: () -> Unit) {
    val profile = data.profile
    val base = data.baseline
    Page(if (profile == null) "A little planning.\nA lot less worry." else "Your attendance\nat a glance.", "Attend / ${if (demo) "Sample preview" else "College companion"}", action = {
        if (profile != null) IconButton(onClick = connect) { Icon(Icons.Default.Refresh, "Refresh ERP") }
    }) {
        if (profile == null || base == null) {
            ConnectPrompt(connect)
            OutlinedButton(onClick = preview, modifier = Modifier.fillMaxWidth()) { Text("Explore with sample data") }
            InfoPanel("Ready for your week", "24 scheduled sessions · Sunday off\nOnly Republic Day, Independence Day and Gandhi Jayanti are automatic holidays.")
            Text("01  Connect ERP\n02  Check your class calendar\n03  Plan your next few months", style = MaterialTheme.typography.bodyLarge, lineHeight = 36.sp)
        } else {
            if (demo) InfoPanel("DEMO — sample data", "This preview uses an anonymized example from your sheets. Nothing here is a live ERP import.")
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(profile.name, style = MaterialTheme.typography.titleLarge)
                Text("Roll ${profile.rollNumber} · Semester ${profile.semester}${if (profile.course.isNotBlank()) " · ${profile.course}" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(color = Forest, contentColor = Color.White, shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("RECORDED ATTENDANCE", style = MaterialTheme.typography.labelSmall, color = Lime)
                    Text(percent(base.counts.percentage), fontSize = 54.sp, fontWeight = FontWeight.Light, letterSpacing = (-2).sp)
                    Text("${base.counts.attended} attended / ${base.counts.held} held", color = Color(0xFFDCE7DD))
                    LinearProgressIndicator(progress = { ((base.counts.percentage ?: 0.0) / 100).toFloat() }, modifier = Modifier.fillMaxWidth().height(5.dp), color = Lime, trackColor = Color.White.copy(alpha = .16f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Through ${base.asOf.format(ShortDate)}", style = MaterialTheme.typography.bodySmall)
                        Text("Target ${targetLabel(data.targetBps)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (base.counts.held > 0) {
                val recovery = AttendanceMath.recoveryClasses(base.counts, data.targetBps)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stat("${AttendanceMath.missAllowance(base.counts, data.targetBps)}", "classes you can miss now", Modifier.weight(1f))
                    Stat(recovery?.toString() ?: "∞", "classes to reach target", Modifier.weight(1f))
                }
            }
            val unreported = ChronoUnit.DAYS.between(base.asOf, LocalDate.now()).coerceAtLeast(0)
            if (unreported > 0) InfoPanel("$unreported days since this report", "Classes after ${base.asOf.format(ShortDate)} are unreported. Forecasts estimate this gap; recorded attendance stays unchanged.")
            Button(onClick = forecast, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("See my forecast") }
            if (base.subjects.isNotEmpty()) {
                Text("Subject breakdown", style = MaterialTheme.typography.titleLarge)
                base.subjects.forEach { subject ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(subject.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text(percent(subject.counts.percentage), style = MaterialTheme.typography.labelLarge)
                        }
                        LinearProgressIndicator(progress = { ((subject.counts.percentage ?: 0.0) / 100).toFloat() }, modifier = Modifier.fillMaxWidth().height(4.dp), color = if ((subject.counts.percentage ?: 0.0) >= data.targetBps / 100.0) MaterialTheme.colorScheme.primary else Amber)
                        Text("${subject.counts.attended} / ${subject.counts.held} classes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            val fetched = data.reports.maxOf { it.fetchedAt }.atZone(ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a"))
            Text("${if (demo) "Sample timestamp" else "Last successful refresh"}: $fetched", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            InfoPanel("Scheduled ≠ recorded", "Timetable predicts classes that could be held. Only ERP counts establish recorded attendance; cancelled classes and closures can create differences.")
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(value, style = MaterialTheme.typography.headlineMedium)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
