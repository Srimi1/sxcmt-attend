package `in`.attend.app

import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import `in`.attend.core.PortalPolicy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PortalFlowInstrumentedTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private fun node(text: String): AccessibilityNodeInfo? {
        fun search(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            n ?: return null
            if (n.text?.toString() == text || n.contentDescription?.toString() == text) return n
            return (0 until n.childCount).firstNotNullOfOrNull { search(n.getChild(it)) }
        }
        return search(ins.uiAutomation.rootInActiveWindow)
    }

    private fun click(text: String) {
        val until = SystemClock.uptimeMillis() + 15000
        var item: AccessibilityNodeInfo? = null
        while (item == null && SystemClock.uptimeMillis() < until) { item = node(text); if (item == null) SystemClock.sleep(100) }
        assertNotNull("Missing $text", item)
        while (item != null && !item.isClickable) item = item.parent
        assertTrue("Could not click $text", item?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
    }

    @Test fun dismissedImportStaysDismissedUntilStudentExplicitlyReadsAgain() {
        val activity = ins.startActivitySync(Intent(ins.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        try {
            click("Open college ERP")
            fun findWeb(view: View): WebView? = if (view is WebView) view else if (view is ViewGroup)
                (0 until view.childCount).firstNotNullOfOrNull { findWeb(view.getChildAt(it)) } else null
            lateinit var web: WebView
            val until = SystemClock.uptimeMillis() + 10000
            var found: WebView? = null
            while (found == null && SystemClock.uptimeMillis() < until) {
                ins.runOnMainSync { found = findWeb(activity.window.decorView) }
                if (found == null) SystemClock.sleep(100)
            }
            assertNotNull(found)
            web = found!!
            ins.runOnMainSync {
                web.stopLoading()
                val html = """
                    <html><body><table><tr><td>Student Name</td><td>Test student</td></tr>
                    <tr><td>Roll Number</td><td>1</td></tr><tr><td>Semester</td><td>5</td></tr></table>
                    <p>As Of: 30/09/2026</p><table><caption>Cumulative attendance</caption>
                    <tr><th>Subject</th><th>Present</th><th>Held</th></tr>
                    <tr><td>Total</td><td>75</td><td>100</td></tr></table></body></html>
                """.trimIndent()
                val original = web.webViewClient
                val fixtureUrl = PortalPolicy.ORIGIN + "/synthetic-test"
                web.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(v: WebView, request: WebResourceRequest): WebResourceResponse? =
                        if (request.url.toString() == fixtureUrl) WebResourceResponse("text/html", "UTF-8", html.byteInputStream())
                        else original.shouldInterceptRequest(v, request)
                    override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest) = original.shouldOverrideUrlLoading(v, request)
                    override fun onPageStarted(v: WebView, url: String, icon: android.graphics.Bitmap?) = original.onPageStarted(v, url, icon)
                    override fun onPageFinished(v: WebView, url: String) = original.onPageFinished(v, url)
                }
                web.loadUrl(fixtureUrl)
            }
            click("Keep browsing")
            SystemClock.sleep(4000)
            assertNull("Automatic reread reopened the rejected preview", node("Keep browsing"))
            click("Import this page")
            click("Keep browsing")
        } finally { ins.runOnMainSync { activity.finish() } }
    }
}
