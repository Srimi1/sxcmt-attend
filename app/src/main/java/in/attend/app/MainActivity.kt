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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.attend.app.portal.PortalScreen
import `in`.attend.app.portal.PortalSession
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
    var portal by rememberSaveable { mutableStateOf(false) }
    var startingCounts by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(notice) { notice?.let { snackbar.showSnackbar(it); model.notice.value = null } }
    if (portal) {
        PortalScreen(data, model, { portal = false; startingCounts = true }, { portal = false })
        return
    }
    val connect: () -> Unit = { model.leaveDemo(); portal = true }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
        NavigationBar {
            val labels = listOf("Overview", "Calendar", "Forecast", "Settings")
            val icons = listOf(Icons.Default.SpaceDashboard, Icons.Default.CalendarMonth, Icons.AutoMirrored.Filled.ShowChart, Icons.Default.Tune)
            labels.forEachIndexed { i, label -> NavigationBarItem(selected = page == i, onClick = { page = i }, icon = { Icon(icons[i], label) }, label = { Text(label) }) }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (page) {
                0 -> Dashboard(data, demo, connect, model::showDemo, { page = 2 }, { page = 1 }, { startingCounts = true })
                1 -> CalendarScreen(data, model::update, model::mark, { page = 2 })
                2 -> ForecastScreen(data, { startingCounts = true }, { page = 1 })
                3 -> SettingsScreen(data, demo, model::update, connect, {
                    PortalSession.clear(context) {
                        model.logout { page = 0 }
                    }
                }, { model.leaveDemo(); page = 0 })
            }
        }
    }
    if (startingCounts) StartingCountsDialog(data.manualBaseline, data.reports.isNotEmpty(), { startingCounts = false }) { base ->
        model.update { it.copy(manualBaseline = base) }
        model.notice.value = "Starting counts saved. Mark periods after ${base.asOf} in Calendar."
        startingCounts = false
    }
}
