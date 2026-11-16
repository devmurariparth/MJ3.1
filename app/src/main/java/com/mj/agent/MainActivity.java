package com.mj.agent;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.webkit.WebViewAssetLoader;

import java.io.PrintWriter;
import java.io.StringWriter;

public class MainActivity extends Activity {
    private static final String TAG = "MJStartup";
    private WebView webView;
    private MJBridge bridge;
    private String startupStage = "created";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            startupStage = "window";
            applyImmersiveWindow();

            startupStage = "create WebView";
            webView = new WebView(this);
            webView.setBackgroundColor(Color.BLACK);
            setContentView(webView);

            startupStage = "configure WebView";
            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setJavaScriptCanOpenWindowsAutomatically(false);
            settings.setSupportMultipleWindows(false);
            settings.setAllowFileAccess(false);
            settings.setAllowContentAccess(false);
            settings.setMediaPlaybackRequiresUserGesture(true);
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                settings.setSafeBrowsingEnabled(true);
            }

            startupStage = "create local asset loader";
            WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                    .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                    .build();

            startupStage = "configure WebView client";
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public android.webkit.WebResourceResponse shouldInterceptRequest(
                        WebView view, WebResourceRequest request) {
                    return assetLoader.shouldInterceptRequest(request.getUrl());
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    Uri uri = request.getUrl();
                    if ("appassets.androidplatform.net".equals(uri.getHost())) {
                        return false;
                    }
                    String scheme = uri.getScheme();
                    if ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)) {
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW, uri));
                        } catch (Exception e) {
                            Log.w(TAG, "No external app can open " + uri, e);
                        }
                    }
                    return true;
                }

                @Override
                public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                    String message = "Android WebView renderer stopped. didCrash="
                            + detail.didCrash() + ", priorityAtExit="
                            + detail.rendererPriorityAtExit();
                    Log.e(TAG, message);
                    showStartupError("WebView renderer stopped", new RuntimeException(message));
                    return true;
                }
            });

            startupStage = "configure WebChromeClient";
            webView.setWebChromeClient(new WebChromeClient() {
                @Override
                public boolean onConsoleMessage(ConsoleMessage message) {
                    Log.d(TAG, "JS " + message.message() + " at "
                            + message.sourceId() + ":" + message.lineNumber());
                    return true;
                }
            });

            startupStage = "create native bridge";
            bridge = new MJBridge(this, webView);
            webView.addJavascriptInterface(bridge, "MJNativeBridge");

            startupStage = "load MJ interface";
            webView.loadUrl("https://appassets.androidplatform.net/assets/index.html");
            Log.i(TAG, "MJ startup completed");
        } catch (Exception | LinkageError error) {
            Log.e(TAG, "MJ startup failed at: " + startupStage, error);
            showStartupError(startupStage, error);
        }
    }

    private void applyImmersiveWindow() {
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            try {
                applyImmersiveWindow();
            } catch (RuntimeException e) {
                Log.w(TAG, "Unable to apply immersive window flags", e);
            }
        }
    }

    private void showStartupError(String stage, Throwable error) {
        StringWriter sw = new StringWriter();
        error.printStackTrace(new PrintWriter(sw));
        String report = "MJ startup diagnostic\n"
                + "Stage: " + stage + "\n"
                + "Android API: " + Build.VERSION.SDK_INT + "\n"
                + "Device: " + Build.MANUFACTURER + " " + Build.MODEL + "\n\n"
                + sw;

        try {
            if (bridge != null) {
                bridge.shutdown();
                bridge = null;
            }
            if (webView != null) {
                webView.removeJavascriptInterface("MJNativeBridge");
                webView.stopLoading();
                webView.removeAllViews();
                webView.destroy();
                webView = null;
            }

            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(28, 32, 28, 28);
            root.setBackgroundColor(Color.rgb(5, 9, 20));

            TextView title = new TextView(this);
            title.setText("MJ could not start");
            title.setTextColor(Color.WHITE);
            title.setTextSize(22);
            root.addView(title);

            TextView details = new TextView(this);
            details.setText("Startup stage: " + stage + "\n\n" + error
                    + "\n\nUse Share diagnostic report to send this error for fixing.");
            details.setTextColor(Color.LTGRAY);
            details.setTextSize(14);
            details.setPadding(0, 18, 0, 20);
            root.addView(details);

            Button share = new Button(this);
            share.setText("Share diagnostic report");
            share.setOnClickListener(v -> {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_SUBJECT, "MJ Android startup error");
                send.putExtra(Intent.EXTRA_TEXT, report);
                startActivity(Intent.createChooser(send, "Share MJ diagnostic"));
            });
            root.addView(share);

            ScrollView scroll = new ScrollView(this);
            scroll.addView(root);
            setContentView(scroll);
        } catch (Exception fallbackError) {
            Log.e(TAG, "Could not display startup diagnostic", fallbackError);
            TextView fallback = new TextView(this);
            fallback.setText("MJ could not start at: " + stage + "\n" + error);
            fallback.setTextColor(Color.WHITE);
            fallback.setBackgroundColor(Color.BLACK);
            fallback.setPadding(24, 32, 24, 24);
            setContentView(fallback);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (bridge != null) {
            bridge.handleResult(requestCode, resultCode, data);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (bridge != null) {
            bridge.shutdown();
            bridge = null;
        }
        if (webView != null) {
            webView.removeJavascriptInterface("MJNativeBridge");
            webView.stopLoading();
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
