package `in`.attend.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `in`.attend.app.data.AppData
import `in`.attend.core.*
import java.time.LocalDate

/**
 * One completed or current period with P, A and No class. In present-by-default mode an untapped, finished period
 * shows P in a lighter "counted present" style; tapping A records the absence and tapping it again returns to the default.
 */
@Composable
fun PeriodRow(date: LocalDate, item: DatedSession, data: AppData, mark: (LocalDate, Set<String>, AttendanceMark?) -> Unit,
              noClass: (LocalDate, WeekSession, Boolean) -> Unit) {
    val session = item.session
    val entry = data.entries.lastOrNull { it.date == date && it.sessionId == session.id }
    val cancelled = data.isNoClass(date, session.id)
    val assumed = if (entry == null && !cancelled) data.assumedMark(date, session) else null
    val shown = entry?.mark ?: assumed
    // Assumed marks: outlined in the accent colour on a plain background, so they read as "counted, not tapped".
    val assumedColors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.surface,
        selectedLabelColor = MaterialTheme.colorScheme.secondary)
    val assumedBorder = FilterChipDefaults.filterChipBorder(enabled = true, selected = true,
        selectedBorderColor = MaterialTheme.colorScheme.secondary, selectedBorderWidth = 1.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("${session.period}", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 2.dp))
        Column(Modifier.weight(1f)) {
            Text(session.title, style = MaterialTheme.typography.bodyMedium)
            Text(session.time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = !cancelled && shown == AttendanceMark.PRESENT, label = { Text("P") },
                    colors = if (entry == null) assumedColors else FilterChipDefaults.filterChipColors(),
                    border = if (entry == null && shown == AttendanceMark.PRESENT) assumedBorder else FilterChipDefaults.filterChipBorder(enabled = true, selected = !cancelled && shown == AttendanceMark.PRESENT),
                    onClick = { if (cancelled) noClass(date, session, false); mark(date, setOf(session.id), if (entry?.mark == AttendanceMark.PRESENT) null else AttendanceMark.PRESENT) })
                FilterChip(selected = !cancelled && shown == AttendanceMark.ABSENT, label = { Text("A") },
                    colors = if (entry == null) assumedColors else FilterChipDefaults.filterChipColors(),
                    border = if (entry == null && shown == AttendanceMark.ABSENT) assumedBorder else FilterChipDefaults.filterChipBorder(enabled = true, selected = !cancelled && shown == AttendanceMark.ABSENT),
                    onClick = { if (cancelled) noClass(date, session, false); mark(date, setOf(session.id), if (entry?.mark == AttendanceMark.ABSENT) null else AttendanceMark.ABSENT) })
                FilterChip(selected = cancelled, label = { Text("No class") }, onClick = { noClass(date, session, !cancelled) })
            }
            val note = when {
                cancelled -> "Not held · left out of your count"
                assumed == AttendanceMark.PRESENT -> "Counted present · tap A if you missed it"
                assumed == AttendanceMark.ABSENT -> "Planned leave · counted absent"
                else -> null
            }
            note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
