package com.ajyad.admin

import android.Manifest
import android.app.Activity
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.google.firebase.messaging.FirebaseMessaging
import org.json.JSONObject

/**
 * The store's admin panel in a WebView. Sign-in (password + authenticator code)
 * is the panel's own. While signed in, the phone's push token is sent to the
 * store over the same session, which is how alerts find this device.
 */
class MainActivity : Activity() {

    private lateinit var web: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private val host: String = Uri.parse(BuildConfig.SERVER_URL).host ?: ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        web = WebView(this)
        setContentView(web)

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(web, false)
        }

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            userAgentString = "$userAgentString AjyadAdminApp/1.0"
        }

        web.addJavascriptInterface(Bridge(), "AjyadApp")

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (uri.host == host) return false
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                } catch (_: ActivityNotFoundException) {
                }
                return true
            }

            override fun onPageFinished(view: WebView, url: String?) {
                val uri = Uri.parse(url ?: return)
                if (uri.host == host && (uri.path ?: "").startsWith("/admin")) registerPush()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (!request.isForMainFrame) return
                val html = """
                    <html dir="rtl"><head><meta name="viewport" content="width=device-width,initial-scale=1"></head>
                    <body style="font-family:sans-serif;text-align:center;padding:48px 24px;background:#FBF6F1;color:#3A2418">
                    <h2>${getString(R.string.offline_title)}</h2>
                    <p>${getString(R.string.offline_body)}</p>
                    <p><a href="${BuildConfig.SERVER_URL}/admin" style="display:inline-block;padding:12px 28px;border-radius:999px;background:#7B3F25;color:#fff;text-decoration:none">${getString(R.string.retry)}</a></p>
                    </body></html>
                """.trimIndent()
                view.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: WebChromeClient.FileChooserParams,
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                return try {
                    startActivityForResult(params.createIntent(), REQUEST_FILE)
                    true
                } catch (_: ActivityNotFoundException) {
                    fileCallback = null
                    false
                }
            }
        }

        web.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            if (Uri.parse(url).host != host) return@setDownloadListener
            val request = DownloadManager.Request(Uri.parse(url))
                .addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url) ?: "")
                .addRequestHeader("User-Agent", userAgent)
                .setMimeType(mimeType)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    URLUtil.guessFileName(url, contentDisposition, mimeType),
                )
            getSystemService(DownloadManager::class.java).enqueue(request)
        }

        askNotificationPermission()

        if (savedInstanceState == null) {
            web.loadUrl(target(intent))
        } else {
            web.restoreState(savedInstanceState)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        web.loadUrl(target(intent))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_FILE) {
            fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
            fileCallback = null
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    /** Where to open: the page named by a tapped alert, else the panel's home. Only paths on our own site. */
    private fun target(intent: Intent?): String {
        val path = intent?.getStringExtra(EXTRA_URL)
        val safe = path != null && path.startsWith("/") && !path.startsWith("//") && !path.contains("\\")
        return BuildConfig.SERVER_URL + (if (safe) path else "/admin")
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    /**
     * Sends this phone's push token to the store. Runs inside the page, so it
     * rides the signed-in session (cookie + XSRF header); signed out it is simply refused.
     */
    private fun registerPush() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val last = prefs.getLong(KEY_REGISTERED_AT, 0L)
        if (System.currentTimeMillis() - last < SIX_HOURS) return

        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            if (!Regex("^[A-Za-z0-9_:\\-.]{20,512}$").matches(token)) return@addOnSuccessListener
            val name = "${Build.MANUFACTURER} ${Build.MODEL}".replace(Regex("[^A-Za-z0-9 _\\-]"), "").take(80)
            val body = JSONObject().put("token", token).put("device_name", name).toString()
            val js = """
                (function(){try{
                  var m=document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/); if(!m) return;
                  fetch('/admin-app/devices',{method:'POST',credentials:'same-origin',
                    headers:{'Content-Type':'application/json','Accept':'application/json','X-XSRF-TOKEN':decodeURIComponent(m[1])},
                    body:JSON.stringify($body)})
                  .then(function(r){AjyadApp.registered(r.status)}).catch(function(){});
                }catch(e){}})();
            """.trimIndent()
            web.evaluateJavascript(js, null)
        }
    }

    /** The one thing the page can tell the app: whether the store accepted the token. */
    inner class Bridge {
        @JavascriptInterface
        fun registered(status: Int) {
            if (status == 200) {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putLong(KEY_REGISTERED_AT, System.currentTimeMillis()).apply()
            }
        }
    }

    companion object {
        const val EXTRA_URL = "url"
        const val PREFS = "ajyad"
        const val KEY_REGISTERED_AT = "registered_at"
        private const val REQUEST_FILE = 100
        private const val REQUEST_NOTIFICATIONS = 101
        private const val SIX_HOURS = 6L * 60 * 60 * 1000
    }
}
