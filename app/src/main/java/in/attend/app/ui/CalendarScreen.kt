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
fun CalendarScreen(data: AppData, update: ((AppData) -> AppData) -> Unit) {
    var selected by remember { mutableStateOf(LocalDate.now()) }
    var month by remember { mutableStateOf(YearMonth.from(selected)) }
    var adding by remember { mutableStateOf(false) }
    val teachingEnd = data.teachingEnd ?: data.baseline?.termEnd
    val sessions = TeachingCalendar.generate(selected, selected, data.schedule, data.rules, teachingEnd)
    Page("Make your\ncalendar count.", "Calendar / ${data.schedule.size} sessions per week") {
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
                                val absent = data.rules.any { it.kind == ExceptionKind.ABSENCE && day in it.start..it.end }
                                val bg = when { day == selected -> MaterialTheme.colorScheme.primary; absent -> Color(0xFFF0D9AF); closed || holiday -> MaterialTheme.colorScheme.surfaceVariant; else -> Color.Transparent }
                                val fg = if (day == selected) MaterialTheme.colorScheme.onPrimary else if (absent) Ink else MaterialTheme.colorScheme.onSurface
                                Box(Modifier.fillMaxSize().background(bg, CircleShape).clickable { selected = day }, contentAlignment = Alignment.Center) { Text(day.dayOfMonth.toString(), color = fg, fontWeight = if (day == selected) FontWeight.Bold else FontWeight.Normal) }
                            }
                        }
                    }
                }
            }
        }
        Text("${selected.format(ShortDate)} · ${sessions.size} scheduled", style = MaterialTheme.typography.titleMedium)
        NationalHolidays.name(selected)?.let { InfoPanel(it, "National holiday. No scheduled attendance sessions.") }
        if (selected.dayOfWeek == DayOfWeek.SUNDAY) InfoPanel("Sunday off", "No teaching sessions scheduled.")
        if (teachingEnd != null && selected > teachingEnd) InfoPanel("Beyond teaching term", "Teaching ends ${teachingEnd.format(ShortDate)}. No future sessions are generated after this date.")
        sessions.forEach { item ->
            val absent = data.rules.any { it.kind == ExceptionKind.ABSENCE && it.applies(item.date, item.session) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("${item.session.period.toString().padStart(2, '0')}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge)
                Column(Modifier.weight(1f)) {
                    Text(item.session.title, style = MaterialTheme.typography.bodyMedium)
                    Text("${item.session.time}${if (absent) " · Planned absence" else ""}", style = MaterialTheme.typography.bodySmall, color = if (absent) Amber else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        FilledTonalButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Add absence or calendar change") }
        InfoPanel("Only three automatic holidays", "26 January · 15 August · 2 October\nOther festivals count as teaching days unless you add a college closure.")
        if (data.rules.isNotEmpty()) Text("Your calendar changes", style = MaterialTheme.typography.titleLarge)
        data.rules.sortedBy { it.start }.forEach { rule ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(rule.label, style = MaterialTheme.typography.bodyMedium)
                    Text("${rule.start.format(ShortDate)}${if (rule.end != rule.start) " – ${rule.end.format(ShortDate)}" else ""} · ${rule.kind.name.lowercase().replaceFirstChar { it.uppercase() }}${if (rule.sessionId != null) " · One period" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { update { it.copy(rules = it.rules.filterNot { old -> old.id == rule.id }) } }) { Icon(Icons.Default.Close, "Remove ${rule.label}") }
            }
        }
    }
    if (adding) RuleDialog(selected, data.schedule, { adding = false }) { rule -> update { it.copy(rules = it.rules + rule) }; adding = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleDialog(initial: LocalDate, schedule: List<WeekSession>, dismiss: () -> Unit, save: (CalendarException) -> Unit) {
    var kind by remember { mutableStateOf(ExceptionKind.ABSENCE) }
    var start by remember { mutableStateOf(initial) }
    var end by remember { mutableStateOf(initial) }
    var label by remember { mutableStateOf("") }
    var session by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Calendar change") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Absence affects attended classes. Closure or cancellation removes held classes.", style = MaterialTheme.typography.bodySmall)
            ExceptionKind.entries.forEach { choice -> FilterChip(selected = kind == choice, onClick = { kind = choice; if (choice == ExceptionKind.CLOSURE) session = null }, label = { Text(choice.name.lowercase().replaceFirstChar { it.uppercase() }) }) }
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
    }, confirmButton = { TextButton(enabled = end >= start, onClick = { save(CalendarException(UUID.randomUUID().toString(), start, end, kind, label.ifBlank { kind.name.lowercase().replaceFirstChar { it.uppercase() } }, session)) }) { Text("Save change") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}
