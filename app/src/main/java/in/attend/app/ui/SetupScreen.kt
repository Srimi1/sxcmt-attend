package `in`.attend.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import `in`.attend.app.data.AppData
import `in`.attend.core.MarkingStyle
import java.math.BigDecimal
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ReminderFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

/** First run: each student picks how the app works for them. Everything stays on this phone. */
@Composable
fun SetupScreen(data: AppData, finish: (Int, MarkingStyle, Set<String>, LocalTime?) -> Unit) {
    var target by remember { mutableStateOf((data.targetBps / 100.0).toString().removeSuffix(".0")) }
    var style by remember { mutableStateOf(MarkingStyle.ASSUME_PRESENT) }
    var hidden by remember { mutableStateOf(data.hiddenSubjects) }
    var reminder by remember { mutableStateOf<LocalTime?>(data.reminderTime ?: LocalTime.of(17, 0)) }
    val bps = runCatching { BigDecimal(target).multiply(BigDecimal(100)).intValueExact().takeIf { it in 1..10000 } }.getOrNull()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    Page("Make it yours", "Attend / quick setup") {
        Text("Four choices. You can change any of them later in Settings.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Minimum attendance", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(target, { target = it.take(6) }, label = { Text("Target %") }, singleLine = true, isError = bps == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        MarkingStyleChooser(style) { style = it }
        SubjectsChooser(data, hidden) { hidden = it }
        ReminderChooser(reminder) { reminder = it }
        Button(onClick = {
            if (reminder != null && Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            finish(bps!!, style, hidden, reminder)
        }, enabled = bps != null, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Start") }
    }
}

@Composable
fun MarkingStyleChooser(style: MarkingStyle, choose: (MarkingStyle) -> Unit) {
    Text("How you mark", style = MaterialTheme.typography.titleLarge)
    StyleOption(style == MarkingStyle.ASSUME_PRESENT, "Present by default", "Finished periods count as present. Tap A only when you miss one, or No class if it wasn't held.") { choose(MarkingStyle.ASSUME_PRESENT) }
    StyleOption(style == MarkingStyle.MARK_EACH, "Mark every period", "Only periods you tap count. Untapped ones stay unmarked.") { choose(MarkingStyle.MARK_EACH) }
}

@Composable
private fun StyleOption(selected: Boolean, title: String, body: String, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RadioButton(selected = selected, onClick = onClick)
            Column { Text(title, style = MaterialTheme.typography.titleMedium); Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubjectsChooser(data: AppData, hidden: Set<String>, change: (Set<String>) -> Unit) {
    val subjects = data.schedule.map { it.subject to it.title }.distinctBy { it.first }
    Text("Subjects you take", style = MaterialTheme.typography.titleLarge)
    Text("Untick electives or groups that aren't yours. They're hidden on this phone only and never counted.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        subjects.forEach { (code, title) ->
            FilterChip(selected = code !in hidden, label = { Text(title) }, onClick = { change(if (code in hidden) hidden - code else hidden + code) },
                leadingIcon = if (code !in hidden) ({ Icon(Icons.Default.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }) else null)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderChooser(time: LocalTime?, change: (LocalTime?) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun ask() { if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS) }
    Text("Daily reminder", style = MaterialTheme.typography.titleLarge)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(if (time == null) "Off" else "Every teaching day at ${time.format(ReminderFormat)}", style = MaterialTheme.typography.bodyMedium)
            Text("\"Any absences today?\" Works offline.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = time != null, onCheckedChange = { on -> if (on) { ask(); change(LocalTime.of(17, 0)) } else change(null) })
    }
    if (time != null) OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) { Text("Change time · ${time.format(ReminderFormat)}") }
    if (picking && time != null) {
        val state = rememberTimePickerState(time.hour, time.minute, is24Hour = false)
        AlertDialog(onDismissRequest = { picking = false }, title = { Text("Reminder time") }, text = { TimePicker(state = state) },
            confirmButton = { TextButton(onClick = { ask(); change(LocalTime.of(state.hour, state.minute)); picking = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } })
    }
}

/** The same choices as setup, in Settings. Leaving present-by-default keeps what was already counted. */
@Composable
fun PersonalSection(data: AppData, setStyle: (MarkingStyle) -> Unit, setHidden: (Set<String>) -> Unit, setReminder: (LocalTime?) -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    MarkingStyleChooser(data.markingStyle) { if (it == MarkingStyle.MARK_EACH && data.markingStyle == MarkingStyle.ASSUME_PRESENT) confirm = true else setStyle(it) }
    data.assumePresentFrom?.takeIf { data.markingStyle == MarkingStyle.ASSUME_PRESENT }?.let {
        Text("Counting present by default since ${it.format(ShortDate)}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider()
    SubjectsChooser(data, data.hiddenSubjects, setHidden)
    HorizontalDivider()
    ReminderChooser(data.reminderTime, setReminder)
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Mark every period?") },
        text = { Text("Periods already counted as present stay counted, as saved P marks. From now on only periods you tap will count.") },
        confirmButton = { TextButton(onClick = { confirm = false; setStyle(MarkingStyle.MARK_EACH) }) { Text("Switch") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } })
}
