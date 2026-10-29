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
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun Dashboard(data: AppData, demo: Boolean, mark: (LocalDate, Set<String>, AttendanceMark?) -> Unit,
              noClass: (LocalDate, WeekSession, Boolean) -> Unit, preview: () -> Unit,
              forecast: () -> Unit, calendar: () -> Unit, activity: () -> Unit, sheet: () -> Unit,
              acceptRoutine: (RoutineVersion) -> Unit = {}, dismissRoutine: (RoutineVersion) -> Unit = {}) {
    val now = LocalAttendanceTime.current
    val today = now.toLocalDate()
    val tracking = data.tracking(today, now.toLocalTime())
    val base = tracking.baseline
    val sheetBase = data.sheetBaseline
    val sessions = data.periodsOn(today)
    val todayEntries = data.entries.filter { it.date == today }
    val assumeMode = data.markingStyle == MarkingStyle.ASSUME_PRESENT
    val covered = sheetBase?.asOf?.let { today <= it } == true
    val closure = data.rules.firstOrNull { it.kind == ExceptionKind.CLOSURE && today in it.start..it.end }
    Page("Your attendance", "ATTEND  /  ${today.format(ShortDate)}") {
        if (!demo) data.pendingRoutine(today)?.let { PendingRoutineCard(it, acceptRoutine, dismissRoutine) }
        if (demo) InfoPanel("DEMO — sample data", "Sample figures only. Leave this preview in Settings to save your own attendance.")
        if (base != null) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("CUMULATIVE · CLASSES", style = MaterialTheme.typography.labelSmall)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(progress = { ((base.counts.percentage ?: 0.0) / 100).toFloat() }, modifier = Modifier.size(92.dp), strokeWidth = 8.dp,
                                color = MaterialTheme.colorScheme.secondary, trackColor = MaterialTheme.colorScheme.secondaryContainer)
                            Text(percent(base.counts.percentage), style = MaterialTheme.typography.titleLarge)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("${base.counts.attended} / ${base.counts.held} classes", style = MaterialTheme.typography.titleLarge)
                            Text("Minimum ${targetLabel(data.targetBps)}", style = MaterialTheme.typography.bodyMedium)
                            Text(if (sheetBase != null) "Sheet through ${sheetBase.asOf.format(ShortDate)} + your marks" else "Your marked periods", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (base.counts.held > 0) {
                        val recovery = AttendanceMath.recoveryClasses(base.counts, data.targetBps)
                        val allowance = AttendanceMath.missAllowance(base.counts, data.targetBps)
                        Text(if (recovery == 0L && allowance == 0L) "You're right at ${targetLabel(data.targetBps)}. Missing the next class takes you below it."
                            else if (recovery == 0L) "You can miss ${classes(allowance)} more and stay at ${targetLabel(data.targetBps)}."
                            else if (recovery == null) "Past absences mean you cannot reach 100% through future classes alone."
                            else "Attend the next ${classes(recovery)} in a row to reach ${targetLabel(data.targetBps)}.",
                            style = MaterialTheme.typography.bodyMedium, color = if (recovery == 0L) onTrackColor() else belowColor())
                    }
                }
            }
            if (data.activity.isNotEmpty()) {
                val credit = data.activityCredit(base.counts)
                val hours = ActivityMath.hoursToTarget(base.counts, data.targetBps, data.lecturesPerHour)
                InfoPanel("With activity: ${percent(credit.withActivity.percentage)}",
                    "${credit.hours} activity hours add ${credit.creditedLectures} attended lectures: ${credit.withActivity.attended} / ${credit.withActivity.held}." +
                        (if (credit.unusedLectures > 0) " ${credit.unusedLectures} lectures of credit are above 100% and unused." else "") +
                        (if (hours != null && hours > credit.hours) " Classes alone need ${hours} activity hours to reach ${targetLabel(data.targetBps)}." else ""))
            }
            val month = YearMonth.from(today)
            // Always shown once counts exist, so the first mark of the month doesn't shift the period list under the student's finger.
            val counts = tracking.month(month)
            InfoPanel("${month.format(DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH))}: ${percent(counts?.percentage)}",
                if (counts == null) "No classes marked this month yet. Your minimum applies to the cumulative figure."
                else "${counts.attended} / ${counts.held} classes this month. Your minimum applies to the cumulative figure.")
        } else {
            InfoPanel("Start with your sheet numbers", "Copy your cumulative attended and held classes from the latest college sheet, then mark each period below as P or A.")
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Today", style = MaterialTheme.typography.titleLarge)
                when {
                    closure != null -> Text(closure.label, style = MaterialTheme.typography.bodyMedium)
                    sessions.isEmpty() -> Text("No classes scheduled", style = MaterialTheme.typography.bodyMedium)
                    covered -> Text("Today is already inside your sheet totals.", style = MaterialTheme.typography.bodyMedium)
                    else -> {
                        sessions.forEach { item -> PeriodRow(today, item, data, mark, noClass) }
                        val heldSessions = sessions.filterNot { data.isNoClass(today, it.session.id) }
                        val rest = heldSessions.map { it.session.id }.filter { id -> todayEntries.none { it.sessionId == id } }.toSet()
                        if (assumeMode) {
                            val cancelled = sessions.count { data.isNoClass(today, it.session.id) }
                            val absent = sessions.count { s -> !data.isNoClass(today, s.session.id) && (todayEntries.firstOrNull { it.sessionId == s.session.id }?.mark ?: data.assumedMark(today, s.session, today, now.toLocalTime())) == AttendanceMark.ABSENT }
                            val later = sessions.count { s -> !data.isNoClass(today, s.session.id) && todayEntries.none { it.sessionId == s.session.id } && data.assumedMark(today, s.session, today, now.toLocalTime()) == null }
                            Text("$absent absent · $cancelled no class · ${sessions.size - absent - cancelled - later} present" + (if (later > 0) " · $later later today" else ""), style = MaterialTheme.typography.bodySmall)
                        } else Text("${heldSessions.size - rest.size} of ${heldSessions.size} held periods marked · tap a chip again to clear", style = MaterialTheme.typography.bodySmall)
                        if (!assumeMode && rest.isNotEmpty()) FilledTonalButton(onClick = { mark(today, rest, AttendanceMark.PRESENT) }, modifier = Modifier.fillMaxWidth()) { Text("Mark remaining ${rest.size} present") }
                    }
                }
                TextButton(onClick = calendar) { Icon(Icons.Default.EditCalendar, null); Spacer(Modifier.width(8.dp)); Text("Other days, holidays and leave") }
            }
        }
        if (tracking.unmarked > 0 && base != null) InfoPanel("${tracking.unmarked} past periods unmarked",
            if (assumeMode) "These are from before you turned on present by default, so they're left out. Mark them in Calendar if you know them."
            else "Unmarked periods are left out, so your percentage may be off. Mark them in Calendar. We never assume you were present.")
        sheetBase?.let { s ->
            val marks = tracking.studentCounts
            InfoPanel("Sheet through ${s.asOf.format(ShortDate)}: ${s.counts.attended} / ${s.counts.held}", "Your marks since then: ${marks.attended} / ${marks.held}. When the next monthly sheet arrives, enter its cumulative numbers so the college's count replaces your marks up to that date.")
        }
        OutlinedButton(onClick = sheet, modifier = Modifier.fillMaxWidth()) { Text(if (sheetBase == null) "Enter sheet numbers" else "Update from a new sheet") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = activity, modifier = Modifier.weight(1f)) { Text("Activity hours") }
            Button(onClick = forecast, modifier = Modifier.weight(1f)) { Text("Forecast") }
        }
        if (base != null && base.subjects.isNotEmpty()) {
            Text("Subject breakdown", style = MaterialTheme.typography.titleLarge)
            base.subjects.forEach { subject ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(subject.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text("${subject.counts.attended} / ${subject.counts.held} · ${percent(subject.counts.percentage)}", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Text("Your attendance stays on this device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (base == null) TextButton(onClick = preview) { Text("Explore with sample data") }
    }
}

private fun classes(n: Long) = if (n == 1L) "1 class" else "$n classes"
