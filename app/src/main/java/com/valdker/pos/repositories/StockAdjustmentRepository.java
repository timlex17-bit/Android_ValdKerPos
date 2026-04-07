package com.valdker.pos.repositories;

import android.content.Context;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.valdker.pos.SessionManager;
import com.valdker.pos.models.StockAdjustment;
import com.valdker.pos.network.ApiClient;
import com.valdker.pos.network.ApiConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StockAdjustmentRepository {

    public interface ListCallback {
        void onSuccess(List<StockAdjustment> list);
        void onError(String message);
    }

    public interface ObjectCallback {
        void onSuccess(StockAdjustment obj);
        void onError(String message);
    }

    public interface SimpleCallback {
        void onSuccess();
        void onError(String message);
    }

    private static final String ENDPOINT_STOCK_ADJUSTMENTS = "api/stockadjustments/";

    // =========================
    // GET LIST
    // =========================
    public static void fetch(Context ctx, ListCallback cb) {
        SessionManager session = new SessionManager(ctx);
        String url = ApiConfig.url(session, ENDPOINT_STOCK_ADJUSTMENTS);

        JsonArrayRequest req = new JsonArrayRequest(
                Request.Method.GET,
                url,
                null,
                res -> {
                    try {
                        cb.onSuccess(parseList(res));
                    } catch (Exception e) {
                        cb.onError("Parse error: " + e.getMessage());
                    }
                },
                err -> cb.onError(parseVolleyError(err))
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return authHeaders(ctx, false);
            }
        };

        req.setShouldCache(false);
        ApiClient.getInstance(ctx).add(req);
    }

    // =========================
    // POST CREATE
    // payload: {product, old_stock, new_stock, reason, note}
    // adjusted_at & adjusted_by otomatis dari backend
    // =========================
    public static void create(Context ctx, JSONObject payload, ObjectCallback cb) {
        SessionManager session = new SessionManager(ctx);
        String url = ApiConfig.url(session, ENDPOINT_STOCK_ADJUSTMENTS);

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.POST,
                url,
                payload,
                res -> {
                    try {
                        cb.onSuccess(parseOne(res));
                    } catch (Exception e) {
                        cb.onError("Parse error: " + e.getMessage());
                    }
                },
                err -> cb.onError(parseVolleyError(err))
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return authHeaders(ctx, true);
            }
        };

        req.setShouldCache(false);
        ApiClient.getInstance(ctx).add(req);
    }

    // =========================
    // PATCH UPDATE
    // =========================
    public static void update(Context ctx, int id, JSONObject payload, ObjectCallback cb) {
        SessionManager session = new SessionManager(ctx);
        String detailUrl = ApiConfig.url(session, ENDPOINT_STOCK_ADJUSTMENTS) + id + "/";

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.PATCH,
                detailUrl,
                payload,
                res -> {
                    try {
                        cb.onSuccess(parseOne(res));
                    } catch (Exception e) {
                        cb.onError("Parse error: " + e.getMessage());
                    }
                },
                err -> cb.onError(parseVolleyError(err))
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return authHeaders(ctx, true);
            }
        };

        req.setShouldCache(false);
        ApiClient.getInstance(ctx).add(req);
    }

    // =========================
    // DELETE
    // =========================
    public static void delete(Context ctx, int id, SimpleCallback cb) {
        SessionManager session = new SessionManager(ctx);
        String detailUrl = ApiConfig.url(session, ENDPOINT_STOCK_ADJUSTMENTS) + id + "/";

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.DELETE,
                detailUrl,
                null,
                res -> cb.onSuccess(),
                err -> cb.onError(parseVolleyError(err))
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return authHeaders(ctx, false);
            }
        };

        req.setShouldCache(false);
        ApiClient.getInstance(ctx).add(req);
    }

    // =========================
    // HELPERS
    // =========================
    private static Map<String, String> authHeaders(Context ctx, boolean jsonContentType) {
        SessionManager sm = new SessionManager(ctx);
        String token = sm.getToken();

        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json");

        if (jsonContentType) {
            headers.put("Content-Type", "application/json");
        }

        if (token != null && !token.trim().isEmpty()) {
            headers.put("Authorization", "Token " + token);
        }
        return headers;
    }

    private static String parseVolleyError(com.android.volley.VolleyError err) {
        String msg = "Request error";

        if (err == null) return msg;

        // HTTP status
        if (err.networkResponse != null) {
            msg += " (" + err.networkResponse.statusCode + ")";
            try {
                if (err.networkResponse.data != null) {
                    String body = new String(err.networkResponse.data, StandardCharsets.UTF_8);
                    if (body != null && !body.trim().isEmpty()) {
                        msg += ": " + body;
                    }
                }
            } catch (Exception ignored) {}
            return msg;
        }

        // non-http error
        if (err.getMessage() != null) {
            msg += ": " + err.getMessage();
        }

        return msg;
    }

    private static List<StockAdjustment> parseList(JSONArray arr) throws Exception {
        List<StockAdjustment> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            out.add(parseOne(o));
        }
        return out;
    }

    private static StockAdjustment parseOne(JSONObject o) throws Exception {
        StockAdjustment s = new StockAdjustment();
        s.id = o.optInt("id");
        s.old_stock = o.optInt("old_stock");
        s.new_stock = o.optInt("new_stock");
        s.reason = o.optString("reason");
        s.note = o.optString("note");
        s.adjusted_at = o.optString("adjusted_at");
        s.product = o.optInt("product");
        s.product_name = o.optString("product_name", "");
        s.adjusted_by = o.optInt("adjusted_by");
        s.adjusted_by_name = o.optString("adjusted_by_name", "");
        return s;
    }
}