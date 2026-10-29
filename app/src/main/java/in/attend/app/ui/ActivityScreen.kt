package `in`.attend.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import `in`.attend.app.data.AppData
import `in`.attend.core.*
import java.time.LocalDate
import java.util.UUID

@Composable
fun ActivityScreen(data: AppData, update: ((AppData) -> AppData) -> Unit) {
    var editing by remember { mutableStateOf<ActivityEntry?>(null) }
    var adding by remember { mutableStateOf(false) }
    val classes = data.baseline?.counts
    Page("Activity hours", "Extracurricular / counted separately") {
        val total = ActivityMath.totalHours(data.activity)
        if (classes != null) {
            val credit = data.activityCredit(classes)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("WITH ACTIVITY", style = MaterialTheme.typography.labelSmall)
                    Text(percent(credit.withActivity.percentage), style = MaterialTheme.typography.headlineLarge)
                    Text("Classes ${classes.attended} / ${classes.held} (${percent(classes.percentage)}) + $total hours → ${credit.withActivity.attended} / ${credit.withActivity.held}", style = MaterialTheme.typography.bodyMedium)
                    ActivityMath.hoursToTarget(classes, data.targetBps, data.lecturesPerHour)?.let { needed ->
                        Text(if (needed == 0L) "Your classes alone meet the ${targetLabel(data.targetBps)} minimum." else if (needed <= total) "Your activity hours lift you to the ${targetLabel(data.targetBps)} minimum." else "${needed - total} more hours would reach ${targetLabel(data.targetBps)} with no further absences.", style = MaterialTheme.typography.bodySmall)
                    }
                    if (credit.unusedLectures > 0) Text("${credit.unusedLectures} lectures of credit are above 100% and are not used.", style = MaterialTheme.typography.bodySmall)
                }
            }
        } else InfoPanel("$total hours logged", "Enter your sheet numbers or mark periods first. Activity is added on top of class attendance.")
        InfoPanel("How hours count", "Each activity hour counts as ${data.lecturesPerHour} attended lecture${if (data.lecturesPerHour > 1) "s" else ""}. It raises the attended number only, never the held number. Example: 360 / 600 (60%) + 30 hours = 390 / 600 (65%). Don't add hours already shown in the sheet's ACTIVITY column.")
        Button(onClick = { editing = null; adding = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Add activity hours") }
        data.activity.sortedByDescending { it.date }.forEach { entry ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${entry.hours} h · ${entry.label}", style = MaterialTheme.typography.bodyMedium)
                    Text(entry.date.format(ShortDate), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { editing = entry; adding = true }) { Icon(Icons.Default.Edit, "Edit ${entry.label}") }
                IconButton(onClick = { update { it.copy(activity = it.activity.filterNot { old -> old.id == entry.id }) } }) { Icon(Icons.Default.Close, "Remove ${entry.label}") }
            }
        }
    }
    if (adding) ActivityDialog(editing, { adding = false }) { entry ->
        update { it.copy(activity = it.activity.filterNot { old -> old.id == entry.id } + entry) }
        adding = false
    }
}

@Composable
private fun ActivityDialog(original: ActivityEntry?, dismiss: () -> Unit, save: (ActivityEntry) -> Unit) {
    var hours by remember { mutableStateOf(original?.hours?.toString().orEmpty()) }
    var label by remember { mutableStateOf(original?.label.orEmpty()) }
    var date by remember { mutableStateOf(original?.date ?: LocalDate.now()) }
    val h = hours.toIntOrNull()?.takeIf { it in 1..500 }
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (original == null) "Add activity hours" else "Edit activity hours") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(hours, { hours = it.filter(Char::isDigit).take(3) }, label = { Text("Hours") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = hours.isNotBlank() && h == null)
            OutlinedTextField(label, { label = it.take(80) }, label = { Text("Activity (e.g. NSS, fest)") }, singleLine = true)
            DateButton("Date", date, { date = it }, Modifier.fillMaxWidth())
        }
    }, confirmButton = { TextButton(enabled = h != null, onClick = {
        save(ActivityEntry(original?.id ?: UUID.randomUUID().toString(), date, h!!, label.trim().ifBlank { "Activity" }))
    }) { Text("Save") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}
