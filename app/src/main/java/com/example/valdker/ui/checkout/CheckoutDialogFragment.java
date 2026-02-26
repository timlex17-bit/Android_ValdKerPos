package com.example.valdker.ui.checkout;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.example.valdker.R;

import org.json.JSONObject;

public class CheckoutDialogFragment extends DialogFragment {

    public interface Listener {
        void onSuccess(@NonNull String orderId);
        void onCancel();
    }

    private static final String TAG = "CheckoutDialog";
    private static final String DEFAULT_URL = "https://valdker-vue-js.vercel.app/checkout";

    private static final String ARG_PAYLOAD = "payload_json";
    private static final String ARG_URL = "checkout_url";

    private WebView webView;
    private Listener listener;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static CheckoutDialogFragment newInstance(@NonNull String payloadJson, @Nullable String url) {
        CheckoutDialogFragment f = new CheckoutDialogFragment();
        Bundle b = new Bundle();
        b.putString(ARG_PAYLOAD, payloadJson);
        b.putString(ARG_URL, url);
        f.setArguments(b);
        return f;
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog d = super.onCreateDialog(savedInstanceState);
        d.setCanceledOnTouchOutside(true);
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        return d;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater,
                             @Nullable android.view.ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_checkout, container, false);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ImageButton btnClose = view.findViewById(R.id.btnCloseCheckout);
        webView = view.findViewById(R.id.webCheckoutDialog);

        btnClose.setOnClickListener(v -> {
            if (listener != null) listener.onCancel();
            dismissAllowingStateLoss();
        });

        String payloadJson = "{}";
        String url = DEFAULT_URL;

        if (getArguments() != null) {
            String p = getArguments().getString(ARG_PAYLOAD);
            String u = getArguments().getString(ARG_URL);
            if (p != null && !p.trim().isEmpty()) payloadJson = p;
            if (u != null && !u.trim().isEmpty()) url = u.trim();
        }

        final String payloadFinal = payloadJson;

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        // Bridge name MUST be Android
        webView.addJavascriptInterface(new AndroidBridge(), "Android");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage m) {
                Log.d(TAG, "JS: " + m.message() + " @ " + m.sourceId() + ":" + m.lineNumber());
                return true;
            }
        });

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(@NonNull WebView view, @NonNull WebResourceRequest request) {
                Uri u = request.getUrl();
                String link = (u != null) ? u.toString() : "";
                // Allow normal http(s). Block intent:// and custom schemes.
                return !(link.startsWith("https://") || link.startsWith("http://"));
            }

            @Override
            public void onPageCommitVisible(@NonNull WebView view, @NonNull String url) {
                super.onPageCommitVisible(view, url);
                Log.i(TAG, "onPageCommitVisible url=" + url);
                injectPayloadWithRetry(view, payloadFinal, 0);
            }

            @Override
            public void onPageFinished(@NonNull WebView view, @NonNull String finishedUrl) {
                super.onPageFinished(view, finishedUrl);
                Log.i(TAG, "onPageFinished url=" + finishedUrl);
                injectPayloadWithRetry(view, payloadFinal, 0);
            }
        });

        Log.i(TAG, "Open url=" + url);
        webView.loadUrl(url);
    }

    /**
     * Inject payload into the page + dispatch event.
     * Retry a few times to handle SPA timing (Vue mount not ready yet).
     */
    private void injectPayloadWithRetry(@NonNull WebView view, @NonNull String payloadJson, int attempt) {
        if (!isAdded()) return;
        if (attempt > 3) return; // small retry only

        final String escaped = JSONObject.quote(payloadJson);

        // Keep it compatible and defensive.
        final String js =
                "(function(){" +
                        "  try{" +
                        "    var raw=" + escaped + ";" +
                        "    var obj=JSON.parse(raw);" +
                        "    window.__CHECKOUT_PAYLOAD__=obj;" +
                        "    window.dispatchEvent(new CustomEvent('android-checkout-payload',{detail:obj}));" +
                        "    console.log('[Android] payload injected ok', obj);" +
                        "    return 'ok';" +
                        "  }catch(e){" +
                        "    console.error('[Android] inject error', e);" +
                        "    return 'err';" +
                        "  }" +
                        "})();";

        view.evaluateJavascript(js, value -> {
            // value typically: "ok" or "err" (as a JSON string)
            String v = (value == null) ? "" : value.replace("\"", "").trim();
            if ("ok".equalsIgnoreCase(v)) {
                // success
                return;
            }
            // retry after short delay
            int next = attempt + 1;
            mainHandler.postDelayed(() -> {
                if (webView != null) {
                    injectPayloadWithRetry(webView, payloadJson, next);
                }
            }, 180L * next);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        try {
            if (webView != null) {
                webView.removeJavascriptInterface("Android");
                webView.setWebChromeClient(null);
                webView.setWebViewClient(null);
                webView.destroy();
            }
        } catch (Exception ignored) {
        } finally {
            webView = null;
        }
    }

    private class AndroidBridge {

        @JavascriptInterface
        public void onCheckoutSuccess(String orderId) {
            Log.i(TAG, "onCheckoutSuccess orderId=" + orderId);
            if (listener != null) listener.onSuccess(orderId != null ? orderId : "");
            dismissAllowingStateLoss();
        }

        @JavascriptInterface
        public void onCheckoutCancel() {
            Log.i(TAG, "onCheckoutCancel");
            if (listener != null) listener.onCancel();
            dismissAllowingStateLoss();
        }

        @JavascriptInterface
        public void log(String msg) {
            Log.d(TAG, "WEB: " + msg);
        }
    }
}
