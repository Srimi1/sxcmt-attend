package `in`.attend.app.portal

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView

object PortalSession {
    /** Call on the main thread. Clear the HTTP cache as well as cookies and local storage. */
    fun clear(context: Context, complete: () -> Unit) {
        val view = WebView(context)
        view.clearCache(true)
        view.clearHistory()
        WebStorage.getInstance().deleteAllData()
        CookieManager.getInstance().removeAllCookies {
            CookieManager.getInstance().flush()
            view.destroy()
            complete()
        }
    }
}
