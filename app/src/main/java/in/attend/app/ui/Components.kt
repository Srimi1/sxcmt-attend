package `in`.attend.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

val ShortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
fun percent(value: Double?): String = value?.let { "%.2f%%".format(Locale.ENGLISH, it) } ?: "—"
fun targetLabel(bps: Int) = "${bps / 100.0}".removeSuffix(".0") + "%"

@Composable
fun Page(title: String, eyebrow: String, action: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) { Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(8.dp)); Text(title, style = MaterialTheme.typography.headlineLarge) }
            action?.invoke()
        }
        content()
    }
}

@Composable
fun InfoPanel(title: String, body: String, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ConnectPrompt(onConnect: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Your attendance, without the guesswork.", style = MaterialTheme.typography.headlineMedium)
        Text("Sign in to your college ERP to import your name, roll number and attendance. Your timetable and calendar already work offline.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onConnect, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Connect college ERP"); Spacer(Modifier.width(10.dp)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateButton(label: String, value: LocalDate, onDate: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var picking by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { picking = true }, modifier = modifier) { Text("$label · ${value.format(ShortDate)}") }
    if (picking) {
        val state = rememberDatePickerState(initialSelectedDateMillis = value.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { picking = false }, confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }; picking = false }) { Text("Choose date") }
        }, dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } }) { DatePicker(state = state) }
    }
}
