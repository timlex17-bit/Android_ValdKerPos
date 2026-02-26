package com.example.valdker;

import android.content.Context;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

public class AndroidBridge {
    private final Context context;
    private final WebView webView;

    public AndroidBridge(Context context, WebView webView) {
        this.context = context;
        this.webView = webView;
    }

    @JavascriptInterface
    public String getDeviceId() {
        // device id yang stabil (untuk lisensi 1 tablet 1 toko)
        return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    @JavascriptInterface
    public void toast(String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    // contoh callback ke web
    private void sendToWeb(String event, String payloadJson) {
        String js = "window.__nativeEvent && window.__nativeEvent(" +
                "'" + event + "', " + payloadJson + ");";
        webView.post(() -> webView.evaluateJavascript(js, null));
    }

    @JavascriptInterface
    public void ping() {
        sendToWeb("PING_OK", "{\"ok\":true}");
    }
}
