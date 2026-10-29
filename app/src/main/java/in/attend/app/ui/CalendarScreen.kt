package `in`.attend.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `in`.attend.app.data.AppData
import `in`.attend.core.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

@Composable
fun CalendarScreen(data: AppData, update: ((AppData) -> AppData) -> Unit,
                   mark: (LocalDate, Set<String>, AttendanceMark?) -> Unit, noClass: (LocalDate, WeekSession, Boolean) -> Unit, forecast: () -> Unit) {
    var selected by remember { mutableStateOf(LocalDate.now()) }
    var month by remember { mutableStateOf(YearMonth.from(selected)) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CalendarException?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var showMonth by remember { mutableStateOf(false) }
    val teachingEnd = data.teachingEnd ?: data.baseline?.termEnd
    val sessions = data.periodsOn(selected)
    val assumeMode = data.markingStyle == MarkingStyle.ASSUME_PRESENT
    val pastOrToday = selected <= LocalDate.now()
    val covered = data.sheetBaseline?.asOf?.let { selected <= it } == true
    val dayEntries = data.entries.filter { it.date == selected && it.sessionId in sessions.map { s -> s.session.id } }
    val closed = data.rules.filter { it.kind == ExceptionKind.CLOSURE && selected in it.start..it.end }
    fun record(ids: Set<String>, value: AttendanceMark?) {
        mark(selected, ids, value)
        feedback = if (value == null) "Marks cleared. Counter updated." else "${ids.size} period(s) marked ${value.name.lowercase()}. Counter and forecast updated."
    }
    Page("Calendar & attendance", "Mark periods / plan time off") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            DateButton("Date", selected, { selected = it; month = YearMonth.from(it); feedback = null }, Modifier.weight(1f))
            TextButton(onClick = { selected = LocalDate.now(); month = YearMonth.from(selected); feedback = null }) { Text("Today") }
        }
        val total = data.baseline?.counts
        if (total != null) InfoPanel("Your counter: ${total.attended} / ${total.held} · ${percent(total.percentage)}", if (assumeMode) "Finished periods count as present unless you tap A or No class." else "Your marked periods update this count immediately. Unmarked periods do not count as present.")
        TextButton(onClick = { showMonth = !showMonth }) { Text(if (showMonth) "Hide month" else "Show month calendar") }
        if (showMonth) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.Default.ChevronLeft, "Previous month") }
            Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { month = month.plusMonths(1) }) { Icon(Icons.Default.ChevronRight, "Next month") }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row { listOf("M", "T", "W", "T", "F", "S", "S").forEach { Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text(it, style = MaterialTheme.typography.labelSmall) } } }
            val offset = month.atDay(1).dayOfWeek.value - 1
            val cells = (0 until offset).map { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
            cells.chunked(7).forEach { week ->
                Row {
                    (week + List(7 - week.size) { null }).forEach { day ->
                        Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                            if (day != null) {
                                val holiday = NationalHolidays.name(day) != null || day.dayOfWeek == DayOfWeek.SUNDAY
                                val closed = data.rules.any { it.kind == ExceptionKind.CLOSURE && day in it.start..it.end }
                                val absent = data.rules.any { it.kind == ExceptionKind.ABSENCE && day in it.start..it.end } || data.entries.any { it.date == day && it.mark == AttendanceMark.ABSENT }
                                val bg = when { day == selected -> MaterialTheme.colorScheme.primary; absent -> Color(0xFFF0D9AF); closed || holiday -> MaterialTheme.colorScheme.surfaceVariant; else -> Color.Transparent }
                                val fg = if (day == selected) MaterialTheme.colorScheme.onPrimary else if (absent) Ink else MaterialTheme.colorScheme.onSurface
                                Box(Modifier.fillMaxSize().background(bg, CircleShape).clickable { selected = day; feedback = null; showMonth = false }, contentAlignment = Alignment.Center) { Text(day.dayOfMonth.toString(), color = fg, fontWeight = if (day == selected) FontWeight.Bold else FontWeight.Normal) }
                            }
                        }
                    }
                }
            }
        }
        }
        Text("${selected.format(ShortDate)} · ${sessions.size} scheduled", style = MaterialTheme.typography.titleMedium)
        closed.forEach { InfoPanel(it.label, "College closed. No held classes and no absence penalty. Reopens after ${it.end.format(ShortDate)}.") }
        feedback?.let { InfoPanel("Saved", it) }
        if (covered) InfoPanel("Covered by your sheet numbers", "This date is already included in the college sheet totals you entered. Mark periods after ${data.sheetBaseline!!.asOf.format(ShortDate)} to avoid double counting.")
        else if (pastOrToday && sessions.isNotEmpty()) {
            if (!assumeMode) Text("${dayEntries.count { it.mark == AttendanceMark.PRESENT }} present · ${dayEntries.count { it.mark == AttendanceMark.ABSENT }} absent · ${sessions.size - dayEntries.size} unmarked", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!assumeMode) FilledTonalButton(onClick = { record(sessions.map { it.session.id }.toSet(), AttendanceMark.PRESENT) }, modifier = Modifier.weight(1f)) { Text("All present") }
                OutlinedButton(onClick = { record(sessions.map { it.session.id }.toSet(), AttendanceMark.ABSENT) }, modifier = Modifier.weight(1f)) { Text("All absent") }
            }
        }
        if (!pastOrToday && sessions.isNotEmpty()) InfoPanel("Plan this day", "Future absences change My plan in Forecast. They become recorded attendance only when you mark completed periods.")
        NationalHolidays.name(selected)?.let { InfoPanel(it, "National holiday. No scheduled attendance sessions.") }
        if (selected.dayOfWeek == DayOfWeek.SUNDAY) InfoPanel("Sunday off", "No teaching sessions scheduled.")
        if (teachingEnd != null && selected > teachingEnd) InfoPanel("Beyond teaching term", "Teaching ends ${teachingEnd.format(ShortDate)}. No future sessions are generated after this date.")
        if (pastOrToday && !covered) sessions.forEach { item -> PeriodRow(selected, item, data, { d, ids, m -> mark(d, ids, m); feedback = null }, noClass) }
        else sessions.forEach { item ->
            val absent = data.rules.any { it.kind == ExceptionKind.ABSENCE && it.applies(item.date, item.session) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("${item.session.period.toString().padStart(2, '0')}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge)
                Column(Modifier.weight(1f)) {
                    Text(item.session.title, style = MaterialTheme.typography.bodyMedium)
                    Text("${item.session.time}${if (absent) " · Planned absence" else ""}", style = MaterialTheme.typography.bodySmall, color = if (absent) Amber else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!pastOrToday) {
                        FilterChip(selected = absent, onClick = {
                            if (absent) { editing = data.rules.first { it.kind == ExceptionKind.ABSENCE && it.applies(selected, item.session) }; adding = true }
                            else {
                                val rule = CalendarException(UUID.randomUUID().toString(), selected, selected, ExceptionKind.ABSENCE, "Planned absence · period ${item.session.period}", item.session.id)
                                update { it.copy(rules = it.rules + rule) }; feedback = "Absence planned for period ${item.session.period}. My plan forecast updated."
                            }
                        }, label = { Text(if (absent) "Absent · edit plan" else "Plan absence") })
                    }
                }
            }
        }
        Button(onClick = forecast, modifier = Modifier.fillMaxWidth()) { Text("See forecast impact") }
        FilledTonalButton(onClick = { editing = null; adding = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Add holiday, leave or cancellation") }
        InfoPanel("College holidays", if (data.rules.any { it.id == "college-navratri-2026" }) "Navratri: 17–21 October 2026. College reopens 22 October. National holidays are also excluded; add other confirmed college closures here." else "National holidays are excluded. Add college-confirmed holiday dates here.")
        if (data.rules.isNotEmpty()) Text("Your calendar changes", style = MaterialTheme.typography.titleLarge)
        data.rules.sortedBy { it.start }.forEach { rule ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(rule.label, style = MaterialTheme.typography.bodyMedium)
                    Text("${rule.start.format(ShortDate)}${if (rule.end != rule.start) " – ${rule.end.format(ShortDate)}" else ""} · ${rule.kind.name.lowercase().replaceFirstChar { it.uppercase() }}${if (rule.sessionId != null) " · One period" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { editing = rule; adding = true }) { Icon(Icons.Default.Edit, "Edit ${rule.label}") }
                IconButton(onClick = { update { it.copy(rules = it.rules.filterNot { old -> old.id == rule.id }) }; feedback = "Calendar change removed. Forecast updated." }) { Icon(Icons.Default.Close, "Remove ${rule.label}") }
            }
        }
    }
    if (adding) RuleDialog(selected, data.schedule, editing, { adding = false }) { rule ->
        update { it.copy(rules = it.rules.filterNot { old -> old.id == rule.id } + rule) }
        selected = rule.start; month = YearMonth.from(selected)
        feedback = when (rule.kind) {
            ExceptionKind.ABSENCE -> "Absence planned. Tap See forecast impact to compare My plan against Attend every session. Completed periods still need Present / Absent marks."
            ExceptionKind.CLOSURE -> "Holiday saved. Held and attended estimates exclude these dates."
            ExceptionKind.CANCELLATION -> "Cancellation saved. These periods are excluded from held classes."
        }
        adding = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleDialog(initial: LocalDate, schedule: List<WeekSession>, existing: CalendarException?, dismiss: () -> Unit, save: (CalendarException) -> Unit) {
    var kind by remember { mutableStateOf(existing?.kind ?: if (initial > LocalDate.now()) ExceptionKind.ABSENCE else ExceptionKind.CLOSURE) }
    var start by remember { mutableStateOf(existing?.start ?: initial) }
    var end by remember { mutableStateOf(existing?.end ?: initial) }
    var label by remember { mutableStateOf(existing?.label.orEmpty()) }
    var session by remember { mutableStateOf(existing?.sessionId) }
    var expanded by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Calendar change") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Absence affects attended classes. Closure or cancellation removes held classes.", style = MaterialTheme.typography.bodySmall)
            ExceptionKind.entries.forEach { choice -> FilterChip(selected = kind == choice, onClick = { kind = choice; if (choice == ExceptionKind.CLOSURE) session = null }, label = { Text(when(choice) { ExceptionKind.ABSENCE -> "I'm away · planned absence"; ExceptionKind.CLOSURE -> "College closed · holiday"; ExceptionKind.CANCELLATION -> "Class cancelled" }) }) }
            DateButton("From", start, { start = it; if (end < it) end = it; session = null })
            DateButton("Until", end, { end = it })
            OutlinedTextField(value = label, onValueChange = { label = it.take(100) }, label = { Text("Reason (optional)") }, singleLine = true)
            if (kind != ExceptionKind.CLOSURE) {
                Box {
                    OutlinedButton(onClick = { expanded = true }) { Text(session?.let { id -> schedule.firstOrNull { it.id == id }?.let { "Period ${it.period} · ${it.subject}" } } ?: "All sessions") }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text("All sessions") }, onClick = { session = null; expanded = false })
                        schedule.filter { it.day == start.dayOfWeek }.sortedBy { it.period }.forEach { slot -> DropdownMenuItem(text = { Text("Period ${slot.period} · ${slot.title}") }, onClick = { session = slot.id; expanded = false }) }
                    }
                }
            }
            if (end < start) Text("End date must follow start date.", color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(enabled = end >= start && end.toEpochDay() - start.toEpochDay() <= 3660, onClick = { save(CalendarException(existing?.id ?: UUID.randomUUID().toString(), start, end, kind, label.ifBlank { kind.name.lowercase().replaceFirstChar { it.uppercase() } }, session)) }) { Text("Save change") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}
