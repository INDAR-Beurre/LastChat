package com.relay.lastlab;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.relay.lastlab.server.AndroidPlatform;
import com.relay.lastlab.server.LoopbackServer;

public class MainActivity extends Activity {
    private static final String TAG = "LastLabMobile";

    /**
     * The SPA and its model catalog are both served by this backend, so the WebView talks only
     * to loopback. Outbound calls to the relay happen in Java, not in the page.
     */
    private static final String RELAY_BASE_URL = "https://relay-gw.pages.dev";

    private WebView webView;
    private LoopbackServer server;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Immersive dark status bar & navigation bar
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.parseColor("#090c12"));
        window.setNavigationBarColor(Color.parseColor("#090c12"));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.parseColor("#090c12"));
        webView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        // Same-origin API calls to 127.0.0.1 are what carry the app's data; mixed content is
        // not a concern because the page itself is served over plain HTTP from loopback.
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(false);
        settings.setDisplayZoomControls(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMediaPlaybackRequiresUserGesture(false);

        CookieManager.getInstance().setAcceptCookie(true);

        setContentView(webView);

        // Binding and serving must happen off the UI thread: accept() blocks.
        final String startUrl = startServerAsync();
        webView.setWebViewClient(new AppWebViewClient());
        webView.setWebChromeClient(new AppWebChromeClient());
        webView.loadUrl(startUrl);
    }

    /** Starts the loopback server on a background thread and returns the URL to load. */
    private String startServerAsync() {
        try {
            final LoopbackServer created = new LoopbackServer(new AndroidPlatform(this), RELAY_BASE_URL);
            server = created;
            // Port is only known after bind(); bind synchronously so the port is available,
            // then hand the accept loop to a background thread.
            created.bind();
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        created.start();
                    } catch (Exception e) {
                        Log.e(TAG, "loopback server failed", e);
                    }
                }
            }, "lastlab-server-start");
            t.setDaemon(true);
            t.start();
            return created.getBaseUrl() + "/index.html";
        } catch (Exception e) {
            Log.e(TAG, "could not start loopback server", e);
            // Nothing to fall back to: the bundled SPA is only reachable over the server.
            return "about:blank";
        }
    }

    private boolean isInternal(String url) {
        if (url == null) {
            return false;
        }
        if (server != null && url.startsWith(server.getBaseUrl())) {
            return true;
        }
        return url.startsWith("http://127.0.0.1") || url.startsWith("http://localhost");
    }

    private class AppWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            if (isInternal(url)) {
                return false; // let the WebView navigate within the app
            }
            if (url != null && (url.startsWith("http://") || url.startsWith("https://")
                    || url.startsWith("mailto:"))) {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                    return true;
                } catch (Exception e) {
                    Log.e(TAG, "Failed to launch intent for url: " + url, e);
                }
            }
            return true;
        }
    }

    private static class AppWebChromeClient extends WebChromeClient {
        @Override
        public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
            Log.d(TAG, "[" + consoleMessage.messageLevel() + "] " +
                    consoleMessage.message() + " -- From line " +
                    consoleMessage.lineNumber() + " of " +
                    consoleMessage.sourceId());
            return true;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) {
            webView.onPause();
            webView.pauseTimers();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
            webView.resumeTimers();
        }
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (webView != null && level >= TRIM_MEMORY_MODERATE) {
            webView.clearCache(false);
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (webView != null) {
            webView.clearCache(true);
        }
    }

    @Override
    protected void onDestroy() {
        if (server != null) {
            server.stop();
            server = null;
        }
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (webView != null) {
            // First query the web app if it wants to handle back (e.g. close modal, dismiss sheet, return to chat tab)
            webView.evaluateJavascript(
                "(function(){ try { var app = window.LastLabApp || window.LastChatApp; return !!(app && app.onBackPressed && app.onBackPressed()); } catch(e){ return false; } })()",
                result -> {
                    if ("true".equals(result)) {
                        // Consumed by web UI
                        return;
                    }
                    if (webView.canGoBack()) {
                        webView.goBack();
                    } else {
                        MainActivity.super.onBackPressed();
                    }
                }
            );
            return;
        }
        super.onBackPressed();
    }
}