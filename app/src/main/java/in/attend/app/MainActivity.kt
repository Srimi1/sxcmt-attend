package `in`.attend.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.attend.app.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AttendTheme { AttendApp() } }
    }
}

@Composable
private fun AttendApp(model: AttendViewModel = viewModel()) {
    val ui by model.ui.collectAsStateWithLifecycle()
    val data = ui.data
    val demo = ui.isDemo
    val notice by model.notice.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableIntStateOf(0) }
    var startingCounts by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(notice) { notice?.let { snackbar.showSnackbar(it); model.notice.value = null } }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
        NavigationBar {
            val labels = listOf("Today", "Calendar", "Activity", "Forecast", "Settings")
            val icons = listOf(Icons.Default.SpaceDashboard, Icons.Default.CalendarMonth, Icons.Default.EmojiEvents, Icons.AutoMirrored.Filled.ShowChart, Icons.Default.Tune)
            labels.forEachIndexed { i, label -> NavigationBarItem(selected = page == i, onClick = { page = i }, icon = { Icon(icons[i], label) }, label = { Text(label) }) }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (page) {
                0 -> Dashboard(data, demo, model::mark, model::showDemo, { page = 3 }, { page = 1 }, { page = 2 }, { startingCounts = true },
                    model::acceptRoutine, model::dismissRoutine)
                1 -> CalendarScreen(data, model::update, model::mark, { page = 3 })
                2 -> ActivityScreen(data, model::update)
                3 -> ForecastScreen(data, { startingCounts = true }, { page = 1 })
                4 -> SettingsScreen(data, demo, model::update, { model.clearData { page = 0 } }, { model.leaveDemo(); page = 0 },
                    { sessions -> model.publishRoutine(sessions, java.time.LocalDate.now(), "Timetable edited") }) { ClassRoutineSection(data, demo, model) }
            }
        }
    }
    if (startingCounts) StartingCountsDialog(data, { startingCounts = false }) { base ->
        model.update { it.copy(manualBaseline = base) }
        model.notice.value = "Sheet numbers saved. Mark periods after ${base.asOf} each day."
        startingCounts = false
    }
}
