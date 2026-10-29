package `in`.attend.app

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import `in`.attend.core.PortalPolicy
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class PortalCaptureInstrumentedTest {
    private fun capture(html: String): JSONObject {
        val ins = InstrumentationRegistry.getInstrumentation()
        val script = ins.targetContext.assets.open("capture-portal.js").bufferedReader().use { it.readText() }
        val latch = CountDownLatch(1)
        val result = AtomicReference<String>()
        lateinit var view: WebView
        ins.runOnMainSync {
            view = WebView(ins.targetContext).apply {
                settings.javaScriptEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(v: WebView, url: String) {
                        v.evaluateJavascript(script) { result.set(it); latch.countDown() }
                    }
                }
                loadDataWithBaseURL(PortalPolicy.LOGIN, "<html><body>$html</body></html>", "text/html", "UTF-8", null)
            }
            // Detached WebViews still need layout for visible-page extraction.
            view.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(2000, android.view.View.MeasureSpec.EXACTLY))
            view.layout(0, 0, 1080, 2000)
        }
        try { assertTrue("Capture timed out", latch.await(15, TimeUnit.SECONDS)); return JSONObject(result.get()) }
        finally { ins.runOnMainSync { view.destroy() } }
    }
    @Test fun capturesMultiColumnProfileAndCollapsedAttendanceLinkWithoutReadingInputValues() {
        val page = capture("""<table><tr><td>Student Name</td><td>Test student</td><td>Roll No</td><td>1</td></tr><tr><td>Semester</td><td>5</td></tr></table><input value="PRIVATE_SENTINEL"><a href="#">Attendance menu</a><div style="display:none"><a href="/student/attendance">Attendance</a></div><script>var secret='SCRIPT_SENTINEL'</script>""")
        assertEquals("Test student", page.getJSONObject("fields").getString("Student Name"))
        assertEquals("1", page.getJSONObject("fields").getString("Roll No"))
        assertEquals(1, page.getJSONArray("links").length())
        assertFalse(page.toString().contains("PRIVATE_SENTINEL"))
        assertFalse(page.getString("text").contains("SCRIPT_SENTINEL"))
    }
    @Test fun visiblePasswordPageCannotBeReadAsAttendance() {
        val page = capture("""<input type="password" value="PASSWORD_SENTINEL"><table><tr><td>Student Name</td><td>Someone</td></tr></table>""")
        assertTrue(page.getBoolean("hasPasswordInput"))
        assertEquals(0, page.getJSONArray("tables").length())
        assertFalse(page.toString().contains("PASSWORD_SENTINEL"))
    }
}
