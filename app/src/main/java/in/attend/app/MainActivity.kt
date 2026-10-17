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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
    val loaded = ui.loaded
    val notice by model.notice.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableIntStateOf(0) }
    var portal by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(notice) { notice?.let { snackbar.showSnackbar(it); model.notice.value = null } }
    LaunchedEffect(loaded) {
        if (loaded && !model.openingRefreshHandled) { model.openingRefreshHandled = true; if (data.profile != null && !demo) portal = true }
    }
    val lifecycle = LocalLifecycleOwner.current
    val currentData by rememberUpdatedState(data)
    val currentDemo by rememberUpdatedState(demo)
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START && model.openingRefreshHandled && currentData.profile != null && !currentDemo) portal = true
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    if (portal) {
        PortalScreen(data, model) { portal = false }
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
                0 -> Dashboard(data, demo, connect, model::showDemo, { page = 2 })
                1 -> CalendarScreen(data, model::update)
                2 -> ForecastScreen(data)
                3 -> SettingsScreen(data, demo, model::update, connect, {
                    PortalSession.clear(context) {
                        model.logout { page = 0 }
                    }
                }, { model.leaveDemo(); page = 0 })
            }
        }
    }
}
