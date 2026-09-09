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
import com.valdker.pos.models.ServicePackageRequest;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class ServicePackageApi {
    public interface StringCallback {
        void onSuccess(@NonNull String response);
        void onError(int statusCode, @NonNull String message);
    }

    public interface ObjectCallback {
        void onSuccess(@NonNull JSONObject response);
        void onError(int statusCode, @NonNull String message);
    }

    public interface DeleteCallback {
        void onSuccess();
        void onError(int statusCode, @NonNull String message);
    }

    public static final String ENDPOINT_SERVICE_PACKAGES = "api/workshop/service-packages/";
    private static final int TIMEOUT_MS = 20000;
    private static final int MAX_RETRIES = 1;
    private static final float BACKOFF_MULT = 1.2f;

    private final Context appContext;
    private final SessionManager session;

    public ServicePackageApi(@NonNull Context context) {
        appContext = context.getApplicationContext();
        session = new SessionManager(appContext);
    }

    public void list(@NonNull StringCallback callback) {
        StringRequest request = new StringRequest(
                Request.Method.GET,
                ApiConfig.url(session, ENDPOINT_SERVICE_PACKAGES),
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

    public void create(@NonNull ServicePackageRequest body, @NonNull ObjectCallback callback) {
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                ApiConfig.url(session, ENDPOINT_SERVICE_PACKAGES),
                body.toJson(),
                callback::onSuccess,
                error -> callback.onError(statusCode(error.networkResponse), buildErrorMessage(error.networkResponse, error))
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return jsonHeaders();
            }
        };
        enqueue(request);
    }

    public void update(int id, @NonNull ServicePackageRequest body, @NonNull ObjectCallback callback) {
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.PATCH,
                ApiConfig.url(session, ENDPOINT_SERVICE_PACKAGES + id + "/"),
                body.toJson(),
                callback::onSuccess,
                error -> callback.onError(statusCode(error.networkResponse), buildErrorMessage(error.networkResponse, error))
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return jsonHeaders();
            }
        };
        enqueue(request);
    }

    public void delete(int id, @NonNull DeleteCallback callback) {
        StringRequest request = new StringRequest(
                Request.Method.DELETE,
                ApiConfig.url(session, ENDPOINT_SERVICE_PACKAGES + id + "/"),
                response -> callback.onSuccess(),
                error -> {
                    int code = statusCode(error.networkResponse);
                    if (code == 404) {
                        callback.onSuccess();
                        return;
                    }
                    callback.onError(code, buildErrorMessage(error.networkResponse, error));
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return authHeaders();
            }
        };
        enqueue(request);
    }

    private void enqueue(@NonNull Request<?> request) {
        request.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, MAX_RETRIES, BACKOFF_MULT));
        request.setShouldCache(false);
        request.setTag("ServicePackageApi");
        ApiClient.getInstance(appContext).add(request);
    }

    @NonNull
    private Map<String, String> jsonHeaders() {
        Map<String, String> headers = authHeaders();
        headers.put("Content-Type", "application/json");
        return headers;
    }

    @NonNull
    private Map<String, String> authHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json");
        headers.put("Authorization", "Token " + session.getToken().trim());
        return headers;
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
