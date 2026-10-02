package com.relay.lastchat;

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

public class MainActivity extends Activity {
    private static final String TAG = "LastLabMobile";
    private WebView webView;

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
        settings.setAllowFileAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(false);
        settings.setDisplayZoomControls(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMediaPlaybackRequiresUserGesture(false);

        CookieManager.getInstance().setAcceptCookie(true);

        webView.setWebViewClient(new AppWebViewClient());
        webView.setWebChromeClient(new AppWebChromeClient());

        setContentView(webView);
        webView.loadUrl("file:///android_asset/www/index.html");
    }

    private class AppWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            if (url != null && (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("mailto:"))) {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                    return true;
                } catch (Exception e) {
                    Log.e(TAG, "Failed to launch intent for url: " + url, e);
                }
            }
            return false;
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
