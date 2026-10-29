package `in`.attend.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.attend.app.AttendViewModel
import `in`.attend.app.data.AppData
import `in`.attend.core.*
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Settings section for the timetable shared with classmates. Attendance itself never leaves the phone. */
@Composable
fun ClassRoutineSection(data: AppData, demo: Boolean, model: AttendViewModel) {
    val today = LocalAttendanceTime.current.toLocalDate()
    val account by model.account.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    var signingIn by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val link = data.classLink
    Text("Class routine", style = MaterialTheme.typography.titleLarge)
    when {
        demo -> Text("Leave the sample preview to share a routine.", style = MaterialTheme.typography.bodyMedium)
        account == null -> {
            Text("Share one timetable with your class so nobody has to type it twice. Only the timetable and your email for sign-in are stored online; attendance stays on this phone.", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { signingIn = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Sign in with email code") }
        }
        link == null -> {
            var code by remember { mutableStateOf("") }
            var name by remember { mutableStateOf("SXCMT BBA(IB) Sem III 2026-27") }
            Text("Signed in as $account", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(code, { code = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(10) }, label = { Text("Class join code") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = { model.joinClass(code) }, enabled = !busy && code.length == 10, modifier = Modifier.fillMaxWidth()) { Text("Join class") }
            Text("No code yet? Create the class from your current timetable:", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(name, { name = it.take(80) }, label = { Text("Class name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = { model.createClass(name) }, enabled = !busy && name.trim().length >= 3, modifier = Modifier.fillMaxWidth()) { Text("Create class") }
            TextButton(onClick = model::signOut) { Text("Sign out") }
        }
        else -> {
            val active = Routines.active(data.routineVersions, today)
            val upcoming = Routines.upcoming(data.routineVersions, today)
            data.pendingRoutine(today)?.let { PendingRoutineCard(it, model::acceptRoutine, model::dismissRoutine) }
            InfoPanel(link.name, "Join code ${link.joinCode}\n" + (active?.let { "Version ${it.version} · in force from ${it.effectiveFrom.format(ShortDate)}" } ?: if (data.routineVersions.isNotEmpty()) "No routine is in force yet" else "No routine downloaded yet") +
                (upcoming?.let { "\nVersion ${it.version} starts ${it.effectiveFrom.format(ShortDate)}" } ?: ""))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, "Join ${link.name} in Attend with code ${link.joinCode}"), "Share join code"))
                }, modifier = Modifier.weight(1f)) { Text("Share code") }
                OutlinedButton(onClick = { model.syncRoutine() }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Check updates") }
            }
            if (link.isOwner) TextButton(onClick = model::rotateCode, enabled = !busy) { Text("Change join code (if it leaked)") }
            Text("Editing the weekly timetable publishes a new version for everyone in the class. Periods you already marked keep their original subject.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { history = true }) { Text("Version history (${data.routineVersions.size})") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = model::leaveClass, enabled = !busy) { Text("Leave class") }
                TextButton(onClick = model::signOut) { Text("Sign out") }
            }
        }
    }
    if (account != null && !demo) TextButton(onClick = { confirmDelete = true }, enabled = !busy) { Text("Delete my class-sharing data") }
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (signingIn) SignInDialog(model, busy) { signingIn = false }
    if (history) HistoryDialog(data, busy, { history = false }, { v -> model.acceptRoutine(v); history = false }) { v ->
        model.publishRoutine(v.sessions, today, "Restored version ${v.version}"); history = false
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete class-sharing data?") },
        text = { Text("You leave every class, your account is removed from routine history and you are signed out. Timetables you published stay for classmates without your name. Attendance on this phone is not affected.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; model.deleteClassAccountData() }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } })
}

@Composable
private fun SignInDialog(model: AttendViewModel, busy: Boolean, dismiss: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    val validEmail = Regex("[^@\\s]+@[^@\\s]+\\.[^@\\s]+").matches(email.trim())
    AlertDialog(onDismissRequest = dismiss, title = { Text("Sign in") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (!sent) "We email you a 6-digit code. No password needed." else "Enter the code sent to ${email.trim()}.")
            OutlinedTextField(email, { email = it.take(120) }, label = { Text("Email") }, singleLine = true, enabled = !sent,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            if (sent) OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, label = { Text("6-digit code") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }, confirmButton = {
        if (!sent) TextButton(enabled = validEmail && !busy, onClick = { model.sendCode(email.trim()) { sent = true } }) { Text("Send code") }
        else TextButton(enabled = code.length == 6 && !busy, onClick = { model.verifyCode(email, code, dismiss) }) { Text("Sign in") }
    }, dismissButton = {
        if (sent) TextButton(onClick = { sent = false; code = "" }) { Text("Change email") } else TextButton(onClick = dismiss) { Text("Cancel") }
    })
}

@Composable
private fun HistoryDialog(data: AppData, busy: Boolean, dismiss: () -> Unit, use: (RoutineVersion) -> Unit, restore: (RoutineVersion) -> Unit) {
    val active = data.routineVersions.firstOrNull { it.version == data.appliedRoutineVersion }
    val time = DateTimeFormatter.ofPattern("d MMM, h:mm a")
    AlertDialog(onDismissRequest = dismiss, title = { Text("Routine history") }, text = {
        Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            data.routineVersions.sortedByDescending { it.version }.forEach { v ->
                Column {
                    Text("Version ${v.version}${if (v == active) " · on this phone" else ""}", style = MaterialTheme.typography.titleSmall)
                    Text("${v.sessions.size} periods/week · from ${v.effectiveFrom.format(ShortDate)} · ${if (v.byMe) "by you" else "by a classmate"} · ${v.createdAt.atZone(ZoneId.systemDefault()).format(time)}", style = MaterialTheme.typography.bodySmall)
                    if (v.note.isNotBlank()) Text(v.note, style = MaterialTheme.typography.bodySmall)
                    if (v != active) Row {
                        TextButton(onClick = { use(v) }) { Text("Use on my phone") }
                        TextButton(onClick = { restore(v) }, enabled = !busy) { Text("Restore for class") }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = dismiss) { Text("Close") } })
}

/** A classmate changed the shared timetable; nothing changes on this phone until the student agrees. */
@Composable
fun PendingRoutineCard(version: RoutineVersion, accept: (RoutineVersion) -> Unit, dismiss: (RoutineVersion) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("A classmate updated the class timetable", style = MaterialTheme.typography.titleSmall)
            Text("Version ${version.version} · from ${version.effectiveFrom.format(ShortDate)} · ${version.sessions.size} periods a week" +
                (if (version.note.isNotBlank()) "\n\"${version.note}\"" else "") + "\nPeriods you already marked keep their subject.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { accept(version) }) { Text("Use it") }
                OutlinedButton(onClick = { dismiss(version) }) { Text("Keep mine") }
            }
        }
    }
}
