package com.example.valdker.repositories;

import android.content.Context;

import androidx.annotation.NonNull;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.example.valdker.SessionManager;
import com.example.valdker.models.InventoryCount;
import com.example.valdker.models.InventoryCountItem;
import com.example.valdker.network.ApiClient;
import com.example.valdker.network.ApiConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InventoryCountRepository {

    public interface Callback {
        void onSuccess(@NonNull List<InventoryCount> list);
        void onError(@NonNull String message);
    }

    public interface CreateCallback {
        void onSuccess(@NonNull InventoryCount created);
        void onError(@NonNull String message);
    }

    // ✅ NEW: update callback
    public interface UpdateCallback {
        void onSuccess(@NonNull InventoryCount updated);
        void onError(@NonNull String message);
    }

    // ✅ NEW: delete callback
    public interface DeleteCallback {
        void onSuccess();
        void onError(@NonNull String message);
    }

    private static final String ENDPOINT = "api/inventorycounts/";

    // Network tuning (aligned with other repositories)
    private static final int TIMEOUT_MS = 20000;
    private static final int MAX_RETRIES = 1;
    private static final float BACKOFF_MULT = 1.2f;

    public static void create(@NonNull Context ctx,
                              @NonNull JSONObject payload,
                              @NonNull CreateCallback cb) {

        SessionManager sm = new SessionManager(ctx.getApplicationContext());
        String url = ApiConfig.url(sm, ENDPOINT);

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.POST,
                url,
                payload,
                response -> {
                    try {
                        InventoryCount ic = parseOne(response);
                        cb.onSuccess(ic);
                    } catch (Exception e) {
                        cb.onError("Parse error: " + e.getMessage());
                    }
                },
                error -> cb.onError(buildVolleyErrorMessage(error != null ? error.networkResponse : null, error))
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return buildHeaders(ctx, true);
            }
        };

        req.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, MAX_RETRIES, BACKOFF_MULT));
        req.setShouldCache(false);
        ApiClient.getInstance(ctx).add(req);
    }

    public static void update(@NonNull Context ctx,
                              int id,
                              @NonNull JSONObject payload,
                              @NonNull UpdateCallback cb) {

        SessionManager sm = new SessionManager(ctx.getApplicationContext());
        String url = ApiConfig.url(sm, ENDPOINT + id + "/");

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.PUT,
                url,
                payload,
                response -> {
                    try {
                        InventoryCount ic = parseOne(response);
                        cb.onSuccess(ic);
                    } catch (Exception e) {
                        cb.onError("Parse error: " + e.getMessage());
                    }
                },
                error -> cb.onError(buildVolleyErrorMessage(error != null ? error.networkResponse : null, error))
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return buildHeaders(ctx, true);
            }
        };

        req.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, MAX_RETRIES, BACKOFF_MULT));
        req.setShouldCache(false);
        ApiClient.getInstance(ctx).add(req);
    }

    public static void delete(@NonNull Context ctx,
                              int id,
                              @NonNull DeleteCallback cb) {

        SessionManager sm = new SessionManager(ctx.getApplicationContext());
        String url = ApiConfig.url(sm, ENDPOINT + id + "/");

        StringRequest req = new StringRequest(
                Request.Method.DELETE,
                url,
                response -> cb.onSuccess(),
                error -> cb.onError(buildVolleyErrorMessage(error != null ? error.networkResponse : null, error))
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return buildHeaders(ctx, false);
            }
        };

        req.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, MAX_RETRIES, BACKOFF_MULT));
        req.setShouldCache(false);
        ApiClient.getInstance(ctx).add(req);
    }

    public static void fetch(@NonNull Context ctx, @NonNull Callback cb) {

        SessionManager sm = new SessionManager(ctx.getApplicationContext());
        String url = ApiConfig.url(sm, ENDPOINT);

        JsonArrayRequest req = new JsonArrayRequest(
                Request.Method.GET,
                url,
                null,
                response -> {
                    try {
                        List<InventoryCount> list = parseList(response);
                        cb.onSuccess(list);
                    } catch (Exception e) {
                        cb.onError("Parse error: " + e.getMessage());
                    }
                },
                error -> cb.onError(buildVolleyErrorMessage(error != null ? error.networkResponse : null, error))
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return buildHeaders(ctx, false);
            }
        };

        req.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, MAX_RETRIES, BACKOFF_MULT));
        req.setShouldCache(false);
        ApiClient.getInstance(ctx).add(req);
    }

    // -----------------------
    // Helpers
    // -----------------------

    private static Map<String, String> buildHeaders(@NonNull Context ctx, boolean jsonBody) {
        SessionManager sm = new SessionManager(ctx);
        String token = sm.getToken();

        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json");
        if (jsonBody) headers.put("Content-Type", "application/json");
        if (token != null && !token.trim().isEmpty()) {
            headers.put("Authorization", "Token " + token.trim());
        }
        return headers;
    }

    private static InventoryCount parseOne(@NonNull JSONObject response) throws Exception {
        InventoryCount ic = new InventoryCount();
        ic.id = response.optInt("id");
        ic.title = response.optString("title");
        ic.note = response.optString("note");
        ic.counted_at = response.optString("counted_at");
        ic.status = response.optString("status", "DRAFT");
        JSONObject userObj = response.optJSONObject("counted_by");
        if (userObj != null) {
            InventoryCount.UserLite u = new InventoryCount.UserLite();
            u.id = userObj.optInt("id");
            u.username = userObj.optString("username");
            u.display_name = userObj.optString("display_name");
            ic.counted_by = u;
        }

        JSONArray items = response.optJSONArray("items");
        if (items != null) {
            for (int j = 0; j < items.length(); j++) {
                JSONObject it = items.getJSONObject(j);

                InventoryCountItem item = new InventoryCountItem();
                item.id = it.optInt("id");
                item.product = it.optInt("product");
                item.system_stock = it.optInt("system_stock");
                item.counted_stock = it.optInt("counted_stock");
                item.difference = it.optInt("difference");

                ic.items.add(item);
            }
        }

        return ic;
    }

    private static List<InventoryCount> parseList(@NonNull JSONArray arr) throws Exception {
        List<InventoryCount> list = new ArrayList<>();

        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            InventoryCount ic = parseOne(o);
            list.add(ic);
        }

        return list;
    }

    private static String buildVolleyErrorMessage(NetworkResponse nr, Throwable err) {
        if (nr == null) {
            String m = (err != null) ? err.getMessage() : null;
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