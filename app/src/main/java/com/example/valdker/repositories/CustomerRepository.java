package com.example.valdker.repositories;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.example.valdker.models.Customer;
import com.example.valdker.network.ApiClient;
import com.example.valdker.SessionManager;
import com.example.valdker.network.ApiConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomerRepository {

    public interface ListCallback {
        void onSuccess(@NonNull List<Customer> customers);
        void onError(int statusCode, @NonNull String message);
    }

    public interface ItemCallback {
        void onSuccess(@NonNull Customer customer);
        void onError(int statusCode, @NonNull String message);
    }

    public interface DeleteCallback {
        void onSuccess();
        void onError(int statusCode, @NonNull String message);
    }

    private static final String ENDPOINT_CUSTOMERS = "api/customers/";
    private final SessionManager session;
    private final Context appContext;

    // Network tuning (aligned with other repositories)
    private static final int TIMEOUT_MS = 20000;
    private static final int MAX_RETRIES = 1;
    private static final float BACKOFF_MULT = 1.2f;

    public CustomerRepository(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.session = new SessionManager(appContext);
    }

    // -------------------------
    // GET LIST
    // -------------------------
    public void fetchCustomers(@NonNull String token, @NonNull ListCallback cb) {

        String url = ApiConfig.url(session, ENDPOINT_CUSTOMERS);

        JsonArrayRequest req = new JsonArrayRequest(
                Request.Method.GET,
                url,
                null,
                (JSONArray res) -> {
                    try {
                        cb.onSuccess(parseList(res));
                    } catch (Exception e) {
                        cb.onError(0, "Parse error: " + e.getMessage());
                    }
                },
                (err) -> {
                    int code = (err.networkResponse != null)
                            ? err.networkResponse.statusCode
                            : -1;

                    String msg = buildVolleyErrorMessage(err.networkResponse, err);
                    cb.onError(code, msg);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return authHeaders(token);
            }
        };

        req.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, MAX_RETRIES, BACKOFF_MULT));
        req.setShouldCache(false);

        ApiClient.getInstance(appContext).add(req);
    }

    // -------------------------
    // POST CREATE
    // body: {name, cell, email?, address?}
    // -------------------------
    public void createCustomer(@NonNull String token,
                               @NonNull String name,
                               @NonNull String cell,
                               @Nullable String email,
                               @Nullable String address,
                               @NonNull ItemCallback cb) {
        JSONObject body = new JSONObject();
        try {
            body.put("name", name);
            body.put("cell", cell);

            // Send null as JSON null if empty
            if (email != null) body.put("email", email);
            if (address != null) body.put("address", address);

        } catch (Exception e) {
            cb.onError(0, "Build body error: " + e.getMessage());
            return;
        }

        String url = ApiConfig.url(session, ENDPOINT_CUSTOMERS);

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.POST,
                url,   // ✅ bukan BASE lagi
                body,
                (JSONObject res) -> {
                    try {
                        cb.onSuccess(parseOne(res));
                    } catch (Exception e) {
                        cb.onError(0, "Parse error: " + e.getMessage());
                    }
                },
                (err) -> {
                    int code = (err.networkResponse != null) ? err.networkResponse.statusCode : -1;
                    String msg = buildVolleyErrorMessage(err.networkResponse, err);
                    cb.onError(code, msg);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> h = authHeaders(token);
                h.put("Content-Type", "application/json");
                return h;
            }
        };

        req.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, MAX_RETRIES, BACKOFF_MULT));
        req.setShouldCache(false);

        ApiClient.getInstance(appContext).add(req);
    }

    // -------------------------
    // PUT UPDATE
    // url: /customers/{id}/
    // -------------------------
    public void updateCustomer(@NonNull String token,
                               int id,
                               @NonNull String name,
                               @NonNull String cell,
                               @Nullable String email,
                               @Nullable String address,
                               @NonNull ItemCallback cb) {

        String url = ApiConfig.url(session, ENDPOINT_CUSTOMERS + id + "/");

        JSONObject body = new JSONObject();
        try {
            body.put("name", name);
            body.put("cell", cell);

            body.put("email", email != null ? email : JSONObject.NULL);
            body.put("address", address != null ? address : JSONObject.NULL);

        } catch (Exception e) {
            cb.onError(0, "Build body error: " + e.getMessage());
            return;
        }

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.PUT,
                url,
                body,
                (JSONObject res) -> {
                    try {
                        cb.onSuccess(parseOne(res));
                    } catch (Exception e) {
                        cb.onError(0, "Parse error: " + e.getMessage());
                    }
                },
                (err) -> {
                    int code = (err.networkResponse != null) ? err.networkResponse.statusCode : -1;
                    String msg = buildVolleyErrorMessage(err.networkResponse, err);
                    cb.onError(code, msg);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> h = authHeaders(token);
                h.put("Content-Type", "application/json");
                return h;
            }
        };

        req.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, MAX_RETRIES, BACKOFF_MULT));
        req.setShouldCache(false);

        ApiClient.getInstance(appContext).add(req);
    }

    // -------------------------
    // DELETE
    // -------------------------
    public void deleteCustomer(@NonNull String token, int id, @NonNull DeleteCallback cb) {

        String url = ApiConfig.url(session, ENDPOINT_CUSTOMERS + id + "/");

        StringRequest req = new StringRequest(
                Request.Method.DELETE,
                url,
                response -> cb.onSuccess(),
                error -> {
                    int code = (error.networkResponse != null)
                            ? error.networkResponse.statusCode
                            : -1;

                    if (code == 404) { // idempotent delete
                        cb.onSuccess();
                        return;
                    }

                    String msg = buildVolleyErrorMessage(error.networkResponse, error);
                    cb.onError(code, msg);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return authHeaders(token);
            }
        };

        req.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, 0, BACKOFF_MULT));
        req.setShouldCache(false);

        ApiClient.getInstance(appContext).add(req);
    }

    // -------------------------
    // Helpers
    // -------------------------
    private Map<String, String> authHeaders(@NonNull String token) {
        Map<String, String> h = new HashMap<>();
        h.put("Accept", "application/json");
        h.put("Authorization", "Token " + token.trim());
        return h;
    }

    private List<Customer> parseList(@Nullable JSONArray arr) {
        List<Customer> out = new ArrayList<>();
        if (arr == null) return out;

        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            out.add(parseOne(o));
        }
        return out;
    }

    private Customer parseOne(@NonNull JSONObject o) {
        int id = o.optInt("id", 0);
        String name = o.optString("name", "");
        String cell = o.optString("cell", "");
        String email = o.isNull("email") ? null : o.optString("email", null);
        String address = o.isNull("address") ? null : o.optString("address", null);
        return new Customer(id, name, cell, email, address);
    }

    private static String buildVolleyErrorMessage(@Nullable NetworkResponse nr, @NonNull Exception fallbackErr) {
        if (nr == null) {
            String m = fallbackErr.getMessage();
            return (m != null && !m.trim().isEmpty()) ? m : "Network error";
        }
        try {
            if (nr.data != null) {
                String body = new String(nr.data, StandardCharsets.UTF_8).trim();
                if (body.length() > 250) body = body.substring(0, 250) + "...";
                return "HTTP " + nr.statusCode + " - " + body;
            }
        } catch (Exception ignored) {
        }
        return "HTTP " + nr.statusCode;
    }
}