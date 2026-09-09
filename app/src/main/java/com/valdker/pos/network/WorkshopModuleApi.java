package com.valdker.pos.network;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.valdker.pos.SessionManager;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class WorkshopModuleApi {
    public interface StringCallback {
        void onSuccess(@NonNull String response);
        void onError(int statusCode, @NonNull String message);
    }

    public interface ObjectCallback {
        void onSuccess(@NonNull JSONObject response);
        void onError(int statusCode, @NonNull String message);
    }

    private static final int TIMEOUT_MS = 20000;
    private static final int MAX_RETRIES = 1;
    private static final float BACKOFF_MULT = 1.2f;

    private final Context appContext;
    private final SessionManager session;

    public WorkshopModuleApi(@NonNull Context context) {
        appContext = context.getApplicationContext();
        session = new SessionManager(appContext);
    }

    public void list(@NonNull String endpoint,
                     @Nullable Map<String, String> query,
                     @NonNull StringCallback callback) {
        StringRequest request = new StringRequest(
                Request.Method.GET,
                ApiConfig.url(session, endpoint) + buildQuery(query),
                callback::onSuccess,
                error -> callback.onError(statusCode(error.networkResponse), buildErrorMessage(error.networkResponse, error))
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return authHeaders();
            }
        };
        enqueue(request);
    }

    public void create(@NonNull String endpoint,
                       @NonNull JSONObject body,
                       @NonNull ObjectCallback callback) {
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                ApiConfig.url(session, endpoint),
                body,
                callback::onSuccess,
                error -> callback.onError(statusCode(error.networkResponse), buildErrorMessage(error.networkResponse, error))
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = authHeaders();
                headers.put("Content-Type", "application/json");
                return headers;
            }
        };
        enqueue(request);
    }

    private void enqueue(@NonNull Request<?> request) {
        request.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, MAX_RETRIES, BACKOFF_MULT));
        request.setShouldCache(false);
        request.setTag("WorkshopModuleApi");
        ApiClient.getInstance(appContext).add(request);
    }

    @NonNull
    private Map<String, String> authHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json");
        headers.put("Authorization", "Token " + session.getToken().trim());
        String shopCode = session.getShopCode();
        if (shopCode != null && !shopCode.trim().isEmpty()) {
            headers.put("X-Shop-Code", shopCode.trim());
        }
        return headers;
    }

    @NonNull
    private static String buildQuery(@Nullable Map<String, String> query) {
        if (query == null || query.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : query.entrySet()) {
            if (entry == null || entry.getKey() == null || entry.getValue() == null) continue;
            String key = entry.getKey().trim();
            String value = entry.getValue().trim();
            if (key.isEmpty() || value.isEmpty()) continue;
            sb.append(sb.length() == 0 ? "?" : "&")
                    .append(android.net.Uri.encode(key))
                    .append("=")
                    .append(android.net.Uri.encode(value));
        }
        return sb.toString();
    }

    private static int statusCode(@Nullable NetworkResponse response) {
        return response != null ? response.statusCode : -1;
    }

    @NonNull
    private static String buildErrorMessage(@Nullable NetworkResponse response, @NonNull Exception fallback) {
        if (response == null) {
            String message = fallback.getMessage();
            return message != null && !message.trim().isEmpty() ? message : "Network error";
        }
        try {
            if (response.data != null) {
                String body = new String(response.data, StandardCharsets.UTF_8).trim();
                if (body.length() > 240) body = body.substring(0, 240) + "...";
                return "HTTP " + response.statusCode + " - " + body;
            }
        } catch (Exception ignored) {
        }
        return "HTTP " + response.statusCode;
    }
}
