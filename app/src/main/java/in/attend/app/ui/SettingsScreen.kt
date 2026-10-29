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
fun SettingsScreen(data: AppData, demo: Boolean, update: ((AppData) -> AppData) -> Unit, connect: () -> Unit, logout: () -> Unit, leaveDemo: () -> Unit) {
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
        Text("Teaching calendar", style = MaterialTheme.typography.titleLarge)
        Text("${data.schedule.size} sessions per week · Sunday off", style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { editing = true }, modifier = Modifier.fillMaxWidth()) { Text("Edit weekly timetable") }
        DateButton("Teaching ends", data.teachingEnd ?: data.baseline?.termEnd ?: LocalDate.now().plusMonths(3), { date -> update { it.copy(teachingEnd = date) } }, Modifier.fillMaxWidth())
        if (data.teachingEnd != null) TextButton(onClick = { update { it.copy(teachingEnd = null) } }) { Text("Remove teaching end override") }
        InfoPanel("National holidays only", "Republic Day · 26 January\nIndependence Day · 15 August\nGandhi Jayanti · 2 October\nAdd other confirmed closures in Calendar.")
        HorizontalDivider()
        Text("College connection", style = MaterialTheme.typography.titleLarge)
        Text("SXCMT ERP · Session kept on this device. Refresh when you choose.", style = MaterialTheme.typography.bodyMedium)
        Button(onClick = connect, modifier = Modifier.fillMaxWidth()) { Text(if (data.profile == null || demo) "Connect college ERP" else "Open / refresh ERP") }
        if (demo) {
            OutlinedButton(onClick = leaveDemo, modifier = Modifier.fillMaxWidth()) { Text("Leave sample preview") }
        } else {
            OutlinedButton(onClick = { confirmLogout = true }, modifier = Modifier.fillMaxWidth()) { Text("Disconnect ERP / clear personal data") }
        }
        Text("Your password is entered into the official ERP page. Attend does not read it or store a separate copy. Session and attendance stay on this device; app backups are disabled.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Attend 0.2.0 · Student attendance tracker\nUse your phone's password manager to save ERP credentials. The college controls session expiry. Login alone does not confirm an attendance import.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (editing) TimetableDialog(data, { editing = false }) { sessions -> update { it.copy(schedule = sessions) }; editing = false }
    if (confirmLogout) AlertDialog(onDismissRequest = { confirmLogout = false }, title = { Text("Sign out?") }, text = { Text("This clears the ERP session, imported identity, attendance and calendar changes on this device.") }, confirmButton = { TextButton(onClick = { confirmLogout = false; logout() }) { Text("Sign out") } }, dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancel") } })
}

@Composable
private fun TimetableDialog(data: AppData, dismiss: () -> Unit, save: (List<WeekSession>) -> Unit) {
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
        }
    }, confirmButton = { TextButton(onClick = { save(sessions) }) { Text("Save timetable") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
    if (adding || editing != null) {
        val original = editing
        SessionDialog(day, original, sessions, data.baseline?.subjects.orEmpty(), { adding = false; editing = null }) { slot ->
            sessions = sessions.filterNot { it.id == slot.id } + slot; adding = false; editing = null
        }
    }
}

@Composable
private fun SessionDialog(day: DayOfWeek, original: WeekSession?, sessions: List<WeekSession>, imported: List<SubjectAttendance>, dismiss: () -> Unit, save: (WeekSession) -> Unit) {
    val choices = (SeedTimetable.titles + imported.associate { it.code to it.name }).toList()
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
