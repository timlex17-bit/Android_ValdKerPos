package com.valdker.pos.repositories;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.valdker.pos.SessionManager;
import com.valdker.pos.network.ApiClient;
import com.valdker.pos.network.ApiConfig;
import com.valdker.pos.ui.purchases.PurchaseLite;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PurchaseRepository {

    private static final String TAG = "PURCHASE_REPO";
    private static final String BASE = "api/purchases/";

    public static final String TAG_LIST = "PURCHASES";
    public static final String TAG_CREATE = "PURCHASES_CREATE";

    private static final int TIMEOUT_MS = 20000;
    private static final int MAX_RETRIES = 1;
    private static final float BACKOFF = 1.2f;

    public interface ListCallback {
        void onSuccess(@NonNull List<PurchaseLite> list);
        void onError(int code, @NonNull String message);
    }

    public interface CreateCallback {
        void onSuccess(@NonNull JSONObject response);
        void onError(int code, @NonNull String message);
    }

    private final Context appContext;

    public PurchaseRepository(@NonNull Context ctx) {
        this.appContext = ctx.getApplicationContext();
    }

    // ==========================================================
    // LIST
    // ==========================================================
    public void fetchPurchases(@NonNull ListCallback cb) {
        final String token = new SessionManager(appContext).getToken();

        String url = ApiConfig.url(new SessionManager(appContext), BASE);
        Log.i(TAG, "REQ: GET " + url);

        JsonArrayRequest req = new JsonArrayRequest(
                Request.Method.GET,
                url,
                null,
                (JSONArray res) -> {
                    try {
                        List<PurchaseLite> out = new ArrayList<>();
                        if (res != null) {
                            for (int i = 0; i < res.length(); i++) {
                                JSONObject o = res.optJSONObject(i);
                                if (o != null) out.add(PurchaseLite.fromJson(o));
                            }
                        }
                        cb.onSuccess(out);
                    } catch (Exception e) {
                        Log.e(TAG, "Parse error", e);
                        cb.onError(0, "Parse error: " + e.getMessage());
                    }
                },
                err -> {
                    int code = -1;
                    String msg = "Failed to load purchases.";

                    NetworkResponse nr = err.networkResponse;
                    if (nr != null) {
                        code = nr.statusCode;
                        msg = buildVolleyErrorMessage(nr, msg);
                    } else if (err.getMessage() != null && !err.getMessage().trim().isEmpty()) {
                        msg = err.getMessage();
                    }

                    Log.e(TAG, "fetchPurchases failed: " + code + " " + msg, err);
                    cb.onError(code, msg);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return buildHeaders(token, false);
            }
        };

        req.setTag(TAG_LIST);
        req.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, MAX_RETRIES, BACKOFF));
        req.setShouldCache(false);

        ApiClient.getInstance(appContext).add(req);
    }

    // ==========================================================
    // CREATE
    // ==========================================================
    public void createPurchase(@NonNull JSONObject body, @NonNull CreateCallback cb) {
        final String token = new SessionManager(appContext).getToken();
        if (token == null || token.trim().isEmpty()) {
            cb.onError(401, "Session expired. Please login again.");
            return;
        }

        String url = ApiConfig.url(new SessionManager(appContext), BASE);
        Log.i(TAG, "REQ: POST " + url);
        Log.d(TAG, "POST body: " + body);

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.POST,
                url,
                body,
                (JSONObject res) -> cb.onSuccess(res),
                err -> {
                    int code = -1;
                    String msg = "Create purchase failed.";

                    NetworkResponse nr = err.networkResponse;
                    if (nr != null) {
                        code = nr.statusCode;

                        // Friendlier messages for common cases
                        if (code == 400) msg = "Validation failed. Please check your inputs.";
                        else if (code == 401) msg = "Unauthorized. Please login again.";
                        else if (code == 403) msg = "Forbidden. You don't have access.";
                        else if (code >= 500) msg = "Server error. Try again later.";

                        msg = buildVolleyErrorMessage(nr, msg);
                    } else if (err.getMessage() != null && !err.getMessage().trim().isEmpty()) {
                        msg = err.getMessage();
                    }

                    Log.e(TAG, "createPurchase failed: " + code + " " + msg, err);
                    cb.onError(code, msg);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return buildHeaders(token, true);
            }
        };

        req.setTag(TAG_CREATE);
        req.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, MAX_RETRIES, BACKOFF));
        req.setShouldCache(false);

        ApiClient.getInstance(appContext).add(req);
    }

    // ==========================================================
    // Headers helper
    // ==========================================================
    private static Map<String, String> buildHeaders(@Nullable String token, boolean json) {
        Map<String, String> h = new HashMap<>();
        h.put("Accept", "application/json");
        if (json) h.put("Content-Type", "application/json");
        if (token != null && !token.trim().isEmpty()) {
            h.put("Authorization", "Token " + token.trim());
        }
        return h;
    }

    // ==========================================================
    // Error helper (keeps body snippet for debugging)
    // ==========================================================
    private static String buildVolleyErrorMessage(@NonNull NetworkResponse nr, @NonNull String fallback) {
        try {
            if (nr.data != null && nr.data.length > 0) {
                String body = new String(nr.data, StandardCharsets.UTF_8).trim();
                if (body.length() > 600) body = body.substring(0, 600) + "...";
                return fallback + " (HTTP " + nr.statusCode + ")\n" + body;
            }
        } catch (Exception ignored) {}

        return fallback + " (HTTP " + nr.statusCode + ")";
    }
}