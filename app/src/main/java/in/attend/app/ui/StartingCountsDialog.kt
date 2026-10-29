package `in`.attend.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import `in`.attend.app.data.AppData
import `in`.attend.core.*
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Numbers copied from the college's monthly attendance sheet. */
@Composable
fun StartingCountsDialog(data: AppData, dismiss: () -> Unit, save: (Baseline) -> Unit) {
    val existing = data.manualBaseline
    var attended by remember { mutableStateOf(existing?.counts?.attended?.toString().orEmpty()) }
    var held by remember { mutableStateOf(existing?.counts?.held?.toString().orEmpty()) }
    var monthAttended by remember { mutableStateOf(existing?.month?.attended?.toString().orEmpty()) }
    var monthHeld by remember { mutableStateOf(existing?.month?.held?.toString().orEmpty()) }
    var date by remember { mutableStateOf(existing?.asOf ?: LocalDate.now().minusMonths(1).with(TemporalAdjusters.lastDayOfMonth())) }
    var showSubjects by remember { mutableStateOf(existing?.subjects?.isNotEmpty() == true) }
    val subjectCodes = data.schedule.map { it.subject to it.title }.distinctBy { it.first }
    val subjectInputs = remember { mutableStateMapOf<String, Pair<String, String>>().apply {
        existing?.subjects?.forEach { put(it.code, it.counts.attended.toString() to it.counts.held.toString()) }
    } }
    fun parse(a: String, h: String) = if (a.isBlank() && h.isBlank()) null else runCatching { Counts(a.toLong(), h.toLong()) }.getOrNull()
    val counts = parse(attended, held)
    val month = parse(monthAttended, monthHeld)
    val monthInvalid = (monthAttended.isNotBlank() || monthHeld.isNotBlank()) && (month == null || counts == null || month.attended > counts.attended || month.held > counts.held)
    val subjects = if (!showSubjects) emptyList() else subjectCodes.mapNotNull { (code, title) ->
        subjectInputs[code]?.let { (a, h) -> parse(a, h)?.let { SubjectAttendance(code, title, it) } }
    }
    val valid = counts != null && date <= LocalDate.now() && !monthInvalid
    AlertDialog(onDismissRequest = dismiss, title = { Text("Numbers from your sheet") }, text = {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Copy your row from the latest monthly sheet. Cumulative attended = LAST ATT + this month's Σ. Cumulative held = the sheet's total classes.")
            DateButton("Sheet covers classes through", date, { date = it }, Modifier.fillMaxWidth())
            NumberField("Cumulative classes attended", attended) { attended = it }
            NumberField("Cumulative classes held", held) { held = it }
            counts?.let { Text("Cumulative: ${it.attended} / ${it.held} = ${percent(it.percentage)}. Check this matches the sheet's Cum_% column.", style = MaterialTheme.typography.bodySmall) }
            if (attended.isNotBlank() && held.isNotBlank() && counts == null) Text("Attended must be between 0 and held.", color = MaterialTheme.colorScheme.error)
            Text("Optional: this month's totals (Σ and lectures held)", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Month attended", monthAttended, Modifier.weight(1f)) { monthAttended = it }
                NumberField("Month held", monthHeld, Modifier.weight(1f)) { monthHeld = it }
            }
            if (monthInvalid) Text("Month totals must be valid and no larger than cumulative totals.", color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { showSubjects = !showSubjects }) { Text(if (showSubjects) "Hide subject counts" else "Add subject counts (optional)") }
            if (showSubjects) {
                Text("Cumulative per subject, if you know them. Leave a subject blank to skip it.", style = MaterialTheme.typography.bodySmall)
                subjectCodes.forEach { (code, title) ->
                    val (a, h) = subjectInputs[code] ?: ("" to "")
                    Text(title, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField("Attended", a, Modifier.weight(1f)) { subjectInputs[code] = it to h }
                        NumberField("Held", h, Modifier.weight(1f)) { subjectInputs[code] = a to it }
                    }
                }
            }
            Text("Periods after this date are counted from your P / A marks. Marks on or before it are replaced by the sheet, so nothing is counted twice.", style = MaterialTheme.typography.bodySmall)
            if (date > LocalDate.now()) Text("Choose today or an earlier date.", color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(enabled = valid, onClick = { save(Baseline(counts!!, subjects, date, month = month)) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

@Composable
private fun NumberField(label: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(value, { onChange(it.filter(Char::isDigit).take(7)) }, label = { Text(label) }, modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
}
