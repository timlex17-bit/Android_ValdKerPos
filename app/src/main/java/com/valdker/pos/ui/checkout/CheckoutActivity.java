package com.valdker.pos.ui.checkout;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.valdker.pos.R;

import org.json.JSONObject;

public class CheckoutActivity extends AppCompatActivity {

    public static final String EXTRA_PAYLOAD_JSON = "extra_payload_json";
    public static final String EXTRA_CHECKOUT_URL = "extra_checkout_url";

    private static final String DEFAULT_URL = "https://valdker-vue-js.vercel.app/checkout";
    private static final String TAG = "CheckoutActivity";

    private WebView webView;
    private String payloadJson;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_checkout);

        webView = findViewById(R.id.webCheckout);

        payloadJson = getIntent().getStringExtra(EXTRA_PAYLOAD_JSON);
        String checkoutUrl = getIntent().getStringExtra(EXTRA_CHECKOUT_URL);
        if (checkoutUrl == null || checkoutUrl.trim().isEmpty()) checkoutUrl = DEFAULT_URL;

        Log.i(TAG, "Open url=" + checkoutUrl);
        Log.i(TAG, "Payload len=" + (payloadJson != null ? payloadJson.length() : 0));

        // ✅ Back gesture/button support (replaces onBackPressed override)
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView != null && webView.canGoBack()) {
                    webView.goBack();
                    return;
                }
                // finish activity
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
            }
        });

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);

        // Security defaults
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);

        // https + some assets might be mixed
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        webView.setBackgroundColor(Color.WHITE);

        // ✅ JS Bridge name must be: Android
        webView.addJavascriptInterface(new AndroidBridge(), "Android");

        // ✅ Capture JS console logs into Logcat
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage m) {
                Log.d(TAG, "JS: " + m.message() + " @ " + m.sourceId() + ":" + m.lineNumber());
                return true;
            }
        });

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                String url = u != null ? u.toString() : "";
                return !isHttpOrHttps(url);
            }

            // Android < 24 fallback
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return !isHttpOrHttps(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                Log.i(TAG, "onPageFinished url=" + url);

                if (payloadJson == null) payloadJson = "{}";

                // ✅ safest way to embed a JSON string into JS
                String escaped = JSONObject.quote(payloadJson);

                String js =
                        "(function(){" +
                                "  try {" +
                                "    var raw = " + escaped + ";" +
                                "    var obj = JSON.parse(raw);" +
                                "    window.__CHECKOUT_PAYLOAD__ = obj;" +
                                "    window.dispatchEvent(new CustomEvent('android-checkout-payload', { detail: obj }));" +
                                "    console.log('[Android] payload injected', obj);" +
                                "  } catch(e) {" +
                                "    console.error('[Android] inject error', e);" +
                                "  }" +
                                "})();";

                view.evaluateJavascript(js, null);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                Log.e(TAG, "WebView error url=" + request.getUrl() + " err=" + error);
            }
        });

        webView.loadUrl(checkoutUrl);
    }

    private boolean isHttpOrHttps(String url) {
        if (url == null) return false;
        return url.startsWith("https://") || url.startsWith("http://");
    }

    // =========================================================
    // JS Bridge (called by Vue)
    // =========================================================
    private class AndroidBridge {

        @JavascriptInterface
        public void onCheckoutSuccess(String orderId) {
            Log.i(TAG, "onCheckoutSuccess orderId=" + orderId);

            Intent data = new Intent();
            data.putExtra("orderId", orderId);
            setResult(RESULT_OK, data);
            finish();
        }

        @JavascriptInterface
        public void onCheckoutCancel() {
            Log.i(TAG, "onCheckoutCancel");
            setResult(RESULT_CANCELED);
            finish();
        }

        @JavascriptInterface
        public void log(String message) {
            Log.d(TAG, "WEB: " + message);
        }
    }
}
