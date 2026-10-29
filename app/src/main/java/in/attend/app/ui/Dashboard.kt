package `in`.attend.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `in`.attend.app.data.AppData
import `in`.attend.core.*
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun Dashboard(data: AppData, demo: Boolean, connect: () -> Unit, preview: () -> Unit,
              forecast: () -> Unit, calendar: () -> Unit, startingCounts: () -> Unit) {
    val today = LocalDate.now()
    val tracking = data.tracking(today)
    val base = tracking.baseline
    val sessions = AttendanceTracker.sessionsOn(today, data.entries, data.schedule, data.rules, data.teachingEnd)
    val marked = sessions.count { s -> data.entries.any { it.date == today && it.sessionId == s.session.id } }
    val closure = data.rules.firstOrNull { it.kind == ExceptionKind.CLOSURE && today in it.start..it.end }
    Page("Your attendance", "ATTEND  /  ${today.format(ShortDate)}", action = {
        IconButton(onClick = connect) { Icon(Icons.Default.CloudSync, "Open college ERP") }
    }) {
        if (demo) InfoPanel("DEMO — sample data", "Sample figures only. Leave this preview in Settings to save your own attendance.")
        if (base != null) {
            val personal = tracking.studentCounts.held > 0 || data.manualBaseline != null || data.reports.isEmpty()
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (personal) "YOUR TRACKED ATTENDANCE" else "ERP ATTENDANCE", style = MaterialTheme.typography.labelSmall)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(progress = { ((base.counts.percentage ?: 0.0) / 100).toFloat() }, modifier = Modifier.size(92.dp), strokeWidth = 8.dp, trackColor = MaterialTheme.colorScheme.surface)
                            Text(percent(base.counts.percentage), style = MaterialTheme.typography.titleLarge)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("${base.counts.attended} / ${base.counts.held} classes", style = MaterialTheme.typography.titleLarge)
                            Text("Target ${targetLabel(data.targetBps)}", style = MaterialTheme.typography.bodyMedium)
                            Text(if (personal) "Starting counts and marked periods" else "Report through ${base.asOf.format(ShortDate)}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (base.counts.held > 0) {
                        val recovery = AttendanceMath.recoveryClasses(base.counts, data.targetBps)
                        Text(if (recovery == 0L) "${AttendanceMath.missAllowance(base.counts, data.targetBps)} more classes can be missed at this target." else "Attend ${recovery ?: "all remaining"} classes to reach your target.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        } else {
            InfoPanel("Start with today's periods", "Mark present or absent in Calendar. Your counter and forecast update as you log classes. ERP connection is optional.")
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Today", style = MaterialTheme.typography.titleLarge)
                Text(closure?.label ?: if (sessions.isEmpty()) "No classes scheduled" else "$marked of ${sessions.size} periods marked", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = calendar, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Icon(Icons.Default.EditCalendar, null); Spacer(Modifier.width(8.dp)); Text("Log attendance")
                }
            }
        }
        if (tracking.unmarked > 0 && base != null) InfoPanel("${tracking.unmarked} periods still unmarked", "Unmarked periods are excluded from your tracked count. We never assume you were present.")
        Button(onClick = forecast, modifier = Modifier.fillMaxWidth()) { Text("View my forecast") }
        OutlinedButton(onClick = startingCounts, modifier = Modifier.fillMaxWidth()) { Text(if (data.manualBaseline == null) "Add starting counts" else "Edit starting counts") }
        OutlinedButton(onClick = connect, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Link, null); Spacer(Modifier.width(8.dp)); Text(if (data.reports.isEmpty()) "Connect college ERP" else "Open / refresh ERP") }
        Text(if (data.reports.isEmpty()) "Self-reported attendance stays on this device. Connecting ERP alone does not import a report." else "${data.profile?.name} · ${if (data.manualBaseline != null) "Manual starting counts" else "ERP report"} through ${data.reportBaseline?.asOf?.format(ShortDate)}. Student logs after that date are added separately.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (base != null && base.subjects.isNotEmpty()) {
            Text("Subject breakdown", style = MaterialTheme.typography.titleLarge)
            base.subjects.forEach { subject ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(subject.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text("${subject.counts.attended} / ${subject.counts.held} · ${percent(subject.counts.percentage)}", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        data.reports.maxOfOrNull { it.fetchedAt }?.let { fetched ->
            val time = fetched.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a"))
            Text("${if (demo) "Sample timestamp" else "Last successful ERP refresh"}: $time", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (base == null) TextButton(onClick = preview) { Text("Explore with sample data") }
    }
}
