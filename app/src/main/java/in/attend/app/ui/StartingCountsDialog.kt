package `in`.attend.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import `in`.attend.core.*
import java.time.LocalDate

@Composable
fun StartingCountsDialog(existing: Baseline?, hasErp: Boolean, dismiss: () -> Unit, save: (Baseline) -> Unit) {
    var attended by remember { mutableStateOf(existing?.counts?.attended?.toString().orEmpty()) }
    var held by remember { mutableStateOf(existing?.counts?.held?.toString().orEmpty()) }
    var date by remember { mutableStateOf(existing?.asOf ?: LocalDate.now().minusDays(1)) }
    val counts = runCatching { Counts(attended.toLong(), held.toLong()) }.getOrNull()
    AlertDialog(onDismissRequest = dismiss, title = { Text("Starting attendance") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Enter your known totals, or use 0 / 0 to start fresh. These are your entries, not an ERP import.")
            if (hasErp) Text("Saving uses these starting counts for your counter and forecasts. Your next verified ERP import replaces them; saved ERP reports stay on this device.")
            OutlinedTextField(attended, { attended = it.filter(Char::isDigit).take(8) }, label = { Text("Classes attended") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            OutlinedTextField(held, { held = it.filter(Char::isDigit).take(8) }, label = { Text("Classes held") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            DateButton("Counts through", date, { date = it }, Modifier.fillMaxWidth())
            Text("Log individual periods after this date in Calendar. Future dates are planned absences.", style = MaterialTheme.typography.bodySmall)
            if (attended.isNotBlank() && held.isNotBlank() && counts == null) Text("Attended must be between 0 and held.", color = MaterialTheme.colorScheme.error)
            if (date > LocalDate.now()) Text("Choose today or an earlier date.", color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(enabled = counts != null && date <= LocalDate.now(), onClick = { save(Baseline(counts!!, emptyList(), date)) }) { Text("Save starting counts") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}
