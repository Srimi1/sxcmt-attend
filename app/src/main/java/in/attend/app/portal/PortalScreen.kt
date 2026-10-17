package `in`.attend.app.portal

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import `in`.attend.app.AttendViewModel
import `in`.attend.app.data.AppData
import `in`.attend.core.*
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

private data class Candidate(val reports: List<AttendanceSnapshot>, val signature: String)

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortalScreen(data: AppData, model: AttendViewModel, onClose: () -> Unit) {
    val latestData by rememberUpdatedState(data)
    val context = LocalContext.current
    val captureScript = remember { context.assets.open("capture-portal.js").bufferedReader().use { it.readText() } }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var status by remember { mutableStateOf("Connecting to official ERP…") }
    var loading by remember { mutableStateOf(true) }
    val visit = remember { PortalVisit() }
    var profileUrl by remember { mutableStateOf<String?>(null) }
    var candidate by remember { mutableStateOf<Candidate?>(null) }
    val visited = remember { mutableSetOf<String>() }
    var generation by remember { mutableIntStateOf(0) }
    var disposed by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    val readPage: () -> Unit = read@ {
        val view = webView ?: return@read
        val url = view.url ?: return@read
        if (!PortalPolicy.allowed(url)) return@read
        val requestGeneration = generation
        view.evaluateJavascript(captureScript) callback@ { raw ->
            if (disposed || generation != requestGeneration || view.url != url || raw == "null") return@callback
            val parsed = runCatching { decodePage(JSONObject(raw)) }
            if (parsed.isFailure) { status = "Could not read this page. Navigate to your attendance report and tap Read page."; return@callback }
            val page = parsed.getOrThrow()
            if (page.url != url) return@callback
            when (val result = visit.inspect(page)) {
                PortalResult.LoginRequired -> {
                    profileUrl = null; candidate = null; visited.clear()
                    status = "Sign in on the official ERP page below. Your password is not read or saved by Attend."
                }
                is PortalResult.Profile -> {
                    profileUrl = url; model.observeIdentity(result.profile)
                    status = "Profile found: ${result.profile.name}. Open your attendance report."
                }
                is PortalResult.Ready -> {
                    val profile = result.reports.first().profile
                    // Seeing all identity fields on this report is also sufficient to bind it to this visit.
                    model.observeIdentity(profile)
                    if (profileUrl == null) profileUrl = url
                    val sig = schemaSignature(page)
                    candidate = Candidate(result.reports, sig)
                    status = "Attendance found. Review the imported counts before saving."
                    if (!importing && latestData.profile?.let { !StudentIdentity.conflicts(it, profile) } == true && sig in latestData.verifiedLayouts) {
                        importing = true
                        model.accept(result.reports, sig, profileUrl, onClose) { message -> importing = false; status = message }
                    }
                }
                is PortalResult.Unsupported -> status = if (page.url.substringBefore('?').endsWith("/error/ErrorMessage", true)) {
                    "ERP session may have expired, or the portal is unavailable. Use Sign in to retry. Saved attendance is unchanged."
                } else result.reason
                is PortalResult.IdentityChanged -> {
                    profileUrl = null; candidate = null; visited.clear(); status = result.reason
                }
            }
            if (candidate == null && !page.hasPasswordInput) {
                val destination = if (visit.profile == null) {
                    page.links.firstOrNull { it.label.contains("profile", true) && it.url !in visited }?.url
                } else {
                    latestData.attendanceUrl?.takeIf {
                        latestData.profile?.let { saved -> visit.profile?.let { current -> !StudentIdentity.conflicts(saved, current) } } == true &&
                            it != url && it !in visited && PortalPolicy.allowed(it)
                    }
                        ?: page.links.firstOrNull { it.label.contains("attendance", true) && it.url !in visited }?.url
                }
                if (destination != null && visited.size < 5) { visited += destination; view.loadUrl(destination) }
            }
        }
    }
    DisposableEffect(Unit) {
        WebView.setWebContentsDebuggingEnabled(false)
        onDispose { disposed = true; webView?.apply { stopLoading(); removeAllViews(); destroy() }; webView = null }
    }
    BackHandler { if (candidate != null) candidate = null else if (webView?.canGoBack() == true) webView?.goBack() else onClose() }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Connect ERP") }, navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close portal") } }, actions = {
            IconButton(onClick = { candidate = null; readPage() }) { Icon(Icons.Default.Refresh, "Read page") }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("erp.sxcpatna.edu.in", style = MaterialTheme.typography.labelLarge)
                    Text(status, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            AndroidView(factory = { ctx ->
                WebView(ctx).apply {
                    webView = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.safeBrowsingEnabled = true
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    settings.setSupportMultipleWindows(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val blocked = !PortalPolicy.allowed(request.url.toString())
                            if (blocked) status = "External navigation blocked. Only the official ERP portal is used for sign-in."
                            return blocked
                        }
                        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                            generation++; candidate = null; loading = true
                            if (url != null && !PortalPolicy.allowed(url)) { view.stopLoading(); status = "Unsupported ERP redirect."; loading = false }
                        }
                        override fun onPageFinished(view: WebView, url: String?) {
                            loading = false
                            if (url != null && PortalPolicy.allowed(url)) {
                                readPage()
                                val current = generation
                                view.postDelayed({ if (!disposed && current == generation && candidate == null) readPage() }, 1200)
                            }
                        }
                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                            if (request.isForMainFrame) { loading = false; status = "ERP could not be reached. Your saved attendance is unchanged." }
                        }
                        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                            if (request.isForMainFrame) { loading = false; status = "ERP returned an error (${response.statusCode}). Saved attendance is unchanged." }
                        }
                        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                            handler.cancel(); loading = false; status = "Secure connection failed. ERP connection was stopped."
                        }
                    }
                    loadUrl(data.profileUrl?.takeIf(PortalPolicy::allowed) ?: PortalPolicy.LOGIN)
                }
            }, modifier = Modifier.weight(1f).fillMaxWidth())
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = { visit.reset(); profileUrl = null; candidate = null; webView?.loadUrl(PortalPolicy.LOGIN) }) { Text("Sign in") }
                TextButton(onClick = { webView?.loadUrl(PortalPolicy.DASHBOARD) }) { Text("Dashboard") }
                FilledTonalButton(onClick = readPage) { Text("Read page") }
            }
        }
    }
    candidate?.let { item ->
        val baseline = remember(item) { Ledger.baseline(item.reports)!! }
        AlertDialog(onDismissRequest = { candidate = null }, title = { Text("Check against your ERP") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val p = item.reports.first().profile
                Text("${p.name}\nRoll ${p.rollNumber} · Semester ${p.semester}")
                Text("${baseline.counts.attended} / ${baseline.counts.held} classes · ${baseline.counts.percentage?.let { "%.2f%%".format(it) } ?: "No classes"}", style = MaterialTheme.typography.titleMedium)
                Text("Report through ${baseline.asOf}. ${item.reports.first().kind.name.lowercase()} scope.")
                Text("Confirm these values match your ERP page. This prototype has not been independently verified against a signed-in student report.", style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = {
            TextButton(enabled = !importing, onClick = { importing = true; model.accept(item.reports, item.signature, profileUrl, onClose) { message -> importing = false; status = message; candidate = null } }) { Text("Matches ERP · Save") }
        }, dismissButton = { TextButton(onClick = { candidate = null }) { Text("Keep browsing") } })
    }
}

private fun decodePage(j: JSONObject): PortalPage {
    val fields = j.getJSONObject("fields").let { obj -> obj.keys().asSequence().associateWith { obj.getString(it) } }
    val tables = j.getJSONArray("tables").objects().map { t ->
        val rows = t.getJSONArray("rows").let { a -> (0 until a.length()).map { i -> a.getJSONArray(i).let { r -> (0 until r.length()).map(r::getString) } } }
        PortalTable(t.getString("context"), rows)
    }
    val links = j.getJSONArray("links").objects().map { PortalLink(it.getString("label"), it.getString("url")) }
    return PortalPage(j.getString("url"), j.getString("title"), j.getString("text"), fields, tables, links, j.getBoolean("hasPasswordInput"))
}
private fun JSONArray.objects() = (0 until length()).map(::getJSONObject)
private fun schemaSignature(page: PortalPage): String {
    val shape = page.fields.keys.sorted().joinToString("|") + page.tables.joinToString("|") { it.context + ":" + it.rows.firstOrNull().orEmpty().joinToString(",") }
    return MessageDigest.getInstance("SHA-256").digest(shape.toByteArray()).joinToString("") { "%02x".format(it) }
}
