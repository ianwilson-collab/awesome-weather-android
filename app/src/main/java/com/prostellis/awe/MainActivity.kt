package com.prostellis.awe

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.webkit.WebViewAssetLoader
import com.prostellis.awe.data.Store
import com.prostellis.awe.widget.Refresh

/**
 * The app: the bundled web app (assets/web/index.html, unchanged) in a full-screen web view,
 * plus a small bridge so the widgets follow the location saved in the app.
 */
class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private var pageReady = false
    private var openLocationWhenReady = false
    private var pendingGeolocation: Pair<String, GeolocationPermissions.Callback>? = null

    private val askLocation = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingGeolocation?.let { (origin, callback) -> callback.invoke(origin, granted, false) }
        pendingGeolocation = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this)
        webView = WebView(this)
        root.addView(webView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        setContentView(root)
        fitSystemBars(root)
        holdSplashUntilPageLoads(root)

        val assets = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.addJavascriptInterface(Bridge(), "AWEBridge")
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                assets.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.url.host == APP_HOST) return false
                startActivity(Intent(Intent.ACTION_VIEW, request.url))
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                view.evaluateJavascript(pageScript(), null)
                pageReady = true
                if (openLocationWhenReady) openLocation()
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    callback.invoke(origin, true, false)
                } else {
                    pendingGeolocation = origin to callback
                    askLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Back closes an open popup (location, day details) first, then leaves the app.
                webView.evaluateJavascript(CLOSE_DIALOG_JS) { closed ->
                    if (closed != "true") {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        })

        openLocationWhenReady = intent.getBooleanExtra(EXTRA_OPEN_LOCATION, false)
        if (savedInstanceState == null) webView.loadUrl(START_URL) else webView.restoreState(savedInstanceState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_LOCATION, false)) {
            if (pageReady) openLocation() else openLocationWhenReady = true
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    private fun openLocation() {
        openLocationWhenReady = false
        webView.evaluateJavascript("document.getElementById('placeBtn')?.click()", null)
    }

    /** Keeps the web app clear of the status bar, navigation bar and keyboard (icon colors come from the theme). */
    private fun fitSystemBars(root: FrameLayout) {
        root.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val ime = insets.getInsets(WindowInsets.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            WindowInsets.CONSUMED
        }
    }

    /** The loading screen stays up until the page has drawn (at most a couple of seconds). */
    private fun holdSplashUntilPageLoads(root: FrameLayout) {
        Handler(Looper.getMainLooper()).postDelayed({ pageReady = true }, 2500)
        root.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (!pageReady) return false
                root.viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        })
    }

    /** Runs after the page loads: reports the saved place to the widgets and adds the name under the footer. */
    private fun pageScript(): String {
        val version = packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        return """
            (() => {
              if (window.__awe) return;
              window.__awe = true;
              const report = (v) => { if (v) AWEBridge.placeSaved(v); };
              const setItem = Storage.prototype.setItem;
              Storage.prototype.setItem = function (k, v) {
                setItem.call(this, k, v);
                if (this === window.localStorage && k === "wx7.loc") report(v);
              };
              report(localStorage.getItem("wx7.loc"));

              const css = document.createElement("style");
              css.textContent = `
                #aweSig { margin-top: 16px; display: flex; flex-direction: column; align-items: center; gap: 3px; }
                #aweSig .awe-a { font-size: 7.5px; letter-spacing: 2.6px; font-weight: 700; padding-left: 2.6px; color: #c98a06; line-height: 1; }
                #aweSig .awe-b { font-size: 15px; font-weight: 700; letter-spacing: -.2px; line-height: 1.05;
                  background-image: linear-gradient(135deg, #2f9e78, #2b7fb0); -webkit-background-clip: text; background-clip: text; color: transparent; }
                #aweSig .awe-v { font-size: 11px; color: var(--ink-3); }
                @media (prefers-color-scheme: dark) {
                  #aweSig .awe-a { color: #ffc24a; }
                  #aweSig .awe-b { background-image: linear-gradient(135deg, #95dabd, #6cb4d5); }
                }`;
              document.head.appendChild(css);
              const foot = document.querySelector(".foot");
              if (foot) {
                const sig = document.createElement("div");
                sig.id = "aweSig";
                sig.innerHTML = '<span class="awe-a">AWESOME</span><span class="awe-b">Weather</span><span class="awe-v">Version $version</span>';
                foot.appendChild(sig);
              }
            })();
        """.trimIndent()
    }

    private inner class Bridge {
        /** The web app saved a place (the same JSON it keeps in localStorage). */
        @JavascriptInterface
        fun placeSaved(json: String) {
            if (Store(applicationContext).savePlaceJson(json)) Refresh.now(applicationContext)
        }
    }

    companion object {
        const val EXTRA_OPEN_LOCATION = "com.prostellis.awe.OPEN_LOCATION"
        private const val APP_HOST = "appassets.androidplatform.net"
        private const val START_URL = "https://$APP_HOST/assets/web/index.html"
        private const val CLOSE_DIALOG_JS =
            "(() => { const d = document.querySelector('dialog[open]'); if (d) { d.close(); return true; } return false; })()"
    }
}
