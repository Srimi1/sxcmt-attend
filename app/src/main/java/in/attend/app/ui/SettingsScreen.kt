package `in`.attend.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import `in`.attend.app.data.AppData
import `in`.attend.core.*
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID

@Composable
fun SettingsScreen(data: AppData, demo: Boolean, update: ((AppData) -> AppData) -> Unit, clearData: () -> Unit, leaveDemo: () -> Unit,
                   publish: (List<WeekSession>) -> Unit, personal: @Composable ColumnScope.() -> Unit, routine: @Composable ColumnScope.() -> Unit) {
    val now = LocalAttendanceTime.current
    val base = data.tracking(now.toLocalDate(), now.toLocalTime()).baseline
    var target by remember(data.targetBps) { mutableStateOf((data.targetBps / 100.0).toString().removeSuffix(".0")) }
    var editing by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    val bps = runCatching { BigDecimal(target).multiply(BigDecimal(100)).intValueExact().takeIf { it in 1..10000 } }.getOrNull()
    Page("Your rules.\nYour routine.", "Settings / on this device") {
        Text("Attendance target", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = target, onValueChange = { target = it.take(6) }, label = { Text("Target %") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = bps == null)
            Button(onClick = { bps?.let { value -> update { it.copy(targetBps = value) } } }, enabled = bps != null, modifier = Modifier.padding(top = 8.dp)) { Text("Save") }
        }
        Text("Planning threshold only. Percentages are rounded for display; calculations use exact counts.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider()
        personal()
        HorizontalDivider()
        Text("Teaching calendar", style = MaterialTheme.typography.titleLarge)
        Text("${data.schedule.size} sessions per week · Sunday off", style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { editing = true }, modifier = Modifier.fillMaxWidth()) { Text("Edit weekly timetable") }
        DateButton("Teaching ends", data.teachingEnd ?: base?.termEnd ?: now.toLocalDate().plusMonths(3), { date -> update { it.copy(teachingEnd = date) } }, Modifier.fillMaxWidth())
        if (data.teachingEnd != null) TextButton(onClick = { update { it.copy(teachingEnd = null) } }) { Text("Remove teaching end override") }
        InfoPanel("National holidays only", "Republic Day · 26 January\nIndependence Day · 15 August\nGandhi Jayanti · 2 October\nAdd other confirmed closures in Calendar.")
        HorizontalDivider()
        routine()
        HorizontalDivider()
        Text("Activity credit", style = MaterialTheme.typography.titleLarge)
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Lectures per activity hour: ${data.lecturesPerHour}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(enabled = data.lecturesPerHour > 1, onClick = { update { it.copy(lecturesPerHour = it.lecturesPerHour - 1) } }) { Text("−") }
            OutlinedButton(enabled = data.lecturesPerHour < 10, onClick = { update { it.copy(lecturesPerHour = it.lecturesPerHour + 1) } }) { Text("+") }
        }
        Text("Default 1. Change only if the college confirms a different conversion.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider()
        Text("Your data", style = MaterialTheme.typography.titleLarge)
        if (demo) {
            OutlinedButton(onClick = leaveDemo, modifier = Modifier.fillMaxWidth()) { Text("Leave sample preview") }
        } else {
            OutlinedButton(onClick = { confirmLogout = true }, modifier = Modifier.fillMaxWidth()) { Text("Clear my attendance data") }
        }
        Text("Attend 0.4.0 · Self-service attendance tracker. Marks, sheet numbers and activity hours stay on this device; app backups are disabled.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (editing) TimetableDialog(data, { editing = false }) { sessions ->
        if (data.classLink != null && !demo) publish(sessions) else update { it.copy(schedule = sessions) }
        editing = false
    }
    if (confirmLogout) AlertDialog(onDismissRequest = { confirmLogout = false }, title = { Text("Clear all data?") }, text = { Text("This deletes your sheet numbers, period marks, activity hours, timetable edits and calendar changes from this device. It cannot be undone.") }, confirmButton = { TextButton(onClick = { confirmLogout = false; clearData() }) { Text("Clear") } }, dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancel") } })
}

@Composable
private fun TimetableDialog(data: AppData, dismiss: () -> Unit, save: (List<WeekSession>) -> Unit) {
    val now = LocalAttendanceTime.current
    val base = data.tracking(now.toLocalDate(), now.toLocalTime()).baseline
    var sessions by remember { mutableStateOf(data.schedule) }
    var day by remember { mutableStateOf(DayOfWeek.MONDAY) }
    var editing by remember { mutableStateOf<WeekSession?>(null) }
    var adding by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Weekly timetable") }, text = {
        Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DayOfWeek.entries.filter { it != DayOfWeek.SUNDAY }.forEach { choice -> FilterChip(selected = day == choice, onClick = { day = choice }, label = { Text(choice.name.take(3)) }) }
            }
            sessions.filter { it.day == day }.sortedBy { it.period }.forEach { slot ->
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) { Text("${slot.period}. ${slot.title}"); Text(slot.time, style = MaterialTheme.typography.bodySmall) }
                    IconButton(onClick = { editing = slot }) { Icon(Icons.Default.Edit, "Edit period ${slot.period}") }
                    IconButton(onClick = { sessions = sessions.filterNot { it.id == slot.id } }) { Icon(Icons.Default.Close, "Delete period ${slot.period}") }
                }
            }
            TextButton(enabled = sessions.count { it.day == day } < 12, onClick = { adding = true }) { Text("+ Add session") }
            TextButton(onClick = { sessions = SeedTimetable.sessions }) { Text("Reset to supplied BBA IB timetable") }
            if (data.classLink != null) Routines.validate(sessions)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(enabled = data.classLink == null || Routines.validate(sessions) == null, onClick = { save(sessions) }) { Text(if (data.classLink != null) "Share with class" else "Save timetable") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
    if (adding || editing != null) {
        val original = editing
        SessionDialog(day, original, sessions, base?.subjects.orEmpty(), { adding = false; editing = null }) { slot ->
            sessions = sessions.filterNot { it.id == slot.id } + slot; adding = false; editing = null
        }
    }
}

@Composable
private fun SessionDialog(day: DayOfWeek, original: WeekSession?, sessions: List<WeekSession>, imported: List<SubjectAttendance>, dismiss: () -> Unit, save: (WeekSession) -> Unit) {
    val choices = (SeedTimetable.titles + sessions.associate { it.subject to it.title } +
        imported.associate { it.code to it.name }).toList()
    var subject by remember { mutableStateOf(original?.subject ?: choices.first().first) }
    var period by remember { mutableStateOf((original?.period ?: (1..12).first { p -> sessions.none { it.day == day && it.period == p } }).toString()) }
    var time by remember { mutableStateOf(original?.time ?: "09:00–09:50") }
    var expanded by remember { mutableStateOf(false) }
    val p = period.toIntOrNull()
    val valid = p != null && p in 1..12 && sessions.none { it.id != original?.id && it.day == day && it.period == p } && time.isNotBlank()
    AlertDialog(onDismissRequest = dismiss, title = { Text("${day.name.lowercase().replaceFirstChar { it.uppercase() }} session") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = period, onValueChange = { period = it.take(2) }, label = { Text("Period (1–12)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, isError = !valid)
            Box {
                OutlinedButton(onClick = { expanded = true }) { Text(choices.firstOrNull { it.first == subject }?.second ?: subject) }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { choices.forEach { choice -> DropdownMenuItem(text = { Text(choice.second) }, onClick = { subject = choice.first; expanded = false }) } }
            }
            OutlinedTextField(value = time, onValueChange = { time = it.take(30) }, label = { Text("Time") }, singleLine = true)
        }
    }, confirmButton = { TextButton(enabled = valid, onClick = { save(WeekSession(original?.id ?: UUID.randomUUID().toString(), day, p!!, subject, choices.firstOrNull { it.first == subject }?.second ?: subject, time)) }) { Text("Save session") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}
