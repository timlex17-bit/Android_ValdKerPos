package com.valdker.pos.repositories;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.valdker.pos.SessionManager;
import com.valdker.pos.models.Order;
import com.valdker.pos.models.OrderItem;
import com.valdker.pos.network.ApiClient;
import com.valdker.pos.network.ApiConfig;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OrderRepository {

    public interface Callback {
        void onSuccess(@NonNull List<Order> orders);
        void onError(int statusCode, @NonNull String message);
    }

    public interface CreateCallback {
        void onSuccess(@NonNull JSONObject response);
        void onError(int statusCode, @NonNull String message);
    }

    private static final String TAG = "ORDER_REPO";
    private static final String ENDPOINT = "api/orders/";

    private final Context appContext;

    // Network tuning (aligned with other repositories)
    private static final int TIMEOUT_MS = 20000;
    private static final int MAX_RETRIES = 1;
    private static final float BACKOFF_MULT = 1.2f;

    public OrderRepository(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

    public void createOrder(@Nullable String token,
                            @NonNull JSONObject payload,
                            @NonNull CreateCallback cb) {

        String url = ApiConfig.url(new SessionManager(appContext), ENDPOINT);
        ensureDeviceTime(payload);

        Log.i(TAG, "createOrder() POST -> " + url);
        Log.i(TAG, "createOrder() device_time=" + payload.optString("device_time", ""));

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.POST,
                url,
                payload,
                (JSONObject res) -> {
                    Log.i(TAG, "createOrder SUCCESS response=" + res.toString());
                    cb.onSuccess(res);
                },
                (err) -> {
                    int code = (err.networkResponse != null) ? err.networkResponse.statusCode : -1;
                    String msg = buildVolleyErrorMessage(err.networkResponse, err);

                    Log.e(TAG, "createOrder ERROR status_code=" + code);
                    if (err.networkResponse != null && err.networkResponse.data != null) {
                        try {
                            String body = new String(err.networkResponse.data, StandardCharsets.UTF_8);
                            Log.e(TAG, "createOrder ERROR detail=" + safeLogDetail(body));
                        } catch (Exception ignored) {
                        }
                    } else {
                        Log.e(TAG, "createOrder ERROR body=<null networkResponse>");
                    }

                    cb.onError(code, msg);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return buildHeaders(token);
            }
        };

        req.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, MAX_RETRIES, BACKOFF_MULT));
        req.setShouldCache(false);

        ApiClient.getInstance(appContext).add(req);
    }

    private static void ensureDeviceTime(@NonNull JSONObject payload) {
        if (payload.has("device_time") && !payload.optString("device_time", "").trim().isEmpty()) {
            return;
        }

        try {
            payload.put("device_time", currentDeviceTimeIso());
        } catch (Exception e) {
            Log.w(TAG, "Unable to attach device_time to order payload", e);
        }
    }

    @NonNull
    private static String currentDeviceTimeIso() {
        return new java.text.SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                Locale.US
        ).format(new java.util.Date());
    }

    public void fetchOrders(@Nullable String token, @NonNull Callback cb) {

        String url = ApiConfig.url(new SessionManager(appContext), ENDPOINT);

        StringRequest req = new StringRequest(
                Request.Method.GET,
                url,
                response -> {
                    try {
                        List<Order> out = parseOrders(extractResultsArray(response));
                        cb.onSuccess(out);
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
                return buildHeaders(token);
            }
        };

        req.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, MAX_RETRIES, BACKOFF_MULT));
        req.setShouldCache(false);

        ApiClient.getInstance(appContext).add(req);
    }

    private static JSONArray extractResultsArray(String response) throws Exception {
        Object parsed = new JSONTokener(response == null ? "[]" : response).nextValue();

        if (parsed instanceof JSONArray) {
            return (JSONArray) parsed;
        }

        if (parsed instanceof JSONObject) {
            JSONArray results = ((JSONObject) parsed).optJSONArray("results");
            return results != null ? results : new JSONArray();
        }

        return new JSONArray();
    }

    private Map<String, String> buildHeaders(@Nullable String token) {
        Map<String, String> h = new HashMap<>();
        h.put("Accept", "application/json");

        if (token != null && !token.trim().isEmpty()) {
            h.put("Authorization", "Token " + token.trim());
        }

        return h;
    }

    @NonNull
    private List<Order> parseOrders(@Nullable JSONArray arr) {
        List<Order> out = new ArrayList<>();
        if (arr == null) return out;

        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;

            int id = o.optInt("id", 0);

            Integer customerId = null;
            if (!o.isNull("customer")) customerId = o.optInt("customer");

            String createdAt = o.optString("created_at", "");
            String paymentMethod = o.optString("payment_method", "");
            String invoiceNumber = o.optString("invoice_number", "");

            double subtotal = safeDouble(o.optString("subtotal", "0"));
            double discount = safeDouble(o.optString("discount", "0"));
            double tax = safeDouble(o.optString("tax", "0"));
            double total = safeDouble(o.optString("total", "0"));

            String notes = o.optString("notes", "");
            boolean isPaid = o.optBoolean("is_paid", true);

            List<OrderItem> items = new ArrayList<>();
            JSONArray itemsArr = o.optJSONArray("items");
            if (itemsArr != null) {
                for (int j = 0; j < itemsArr.length(); j++) {
                    JSONObject it = itemsArr.optJSONObject(j);
                    if (it == null) continue;

                    int productId = it.optInt("product", 0);
                    int qty = it.optInt("quantity", 0);
                    double price = safeDouble(it.optString("price", "0"));

                    Integer weightUnitId = null;
                    if (!it.isNull("weight_unit")) weightUnitId = it.optInt("weight_unit");

                    items.add(new OrderItem(productId, qty, price, weightUnitId));
                }
            }

            out.add(new Order(
                    id,
                    customerId,
                    invoiceNumber,
                    createdAt,
                    paymentMethod,
                    subtotal,
                    discount,
                    tax,
                    total,
                    notes,
                    isPaid,
                    items
            ));
        }

        return out;
    }

    private double safeDouble(@Nullable String s) {
        try {
            if (s == null) return 0d;
            return Double.parseDouble(s);
        } catch (Exception ignored) {
            return 0d;
        }
    }

    @NonNull
    private static String buildVolleyErrorMessage(@Nullable NetworkResponse nr, @Nullable Throwable err) {
        if (nr == null) {
            String m = (err != null) ? err.getMessage() : null;
            return (m != null && !m.trim().isEmpty()) ? m : "Network error";
        }

        try {
            if (nr.data != null) {
                String body = new String(nr.data, StandardCharsets.UTF_8).trim();
                return "HTTP " + nr.statusCode + " - " + safeLogDetail(body);
            }
        } catch (Exception ignored) {
        }

        return "HTTP " + nr.statusCode;
    }

    @NonNull
    private static String maskToken(@Nullable String token) {
        if (token == null) return "NULL";
        String t = token.trim();
        if (t.isEmpty()) return "EMPTY";
        int n = Math.min(8, t.length());
        return t.substring(0, n) + "...";
    }

    @NonNull
    private static String safeLogDetail(@Nullable String value) {
        if (value == null) return "";
        String clean = value.replace('\n', ' ').replace('\r', ' ').trim();
        String lower = clean.toLowerCase(Locale.US);
        if (lower.startsWith("<!doctype") || lower.startsWith("<html") || lower.contains("<body")) {
            return "<html omitted>";
        }
        return clean.length() > 180 ? clean.substring(0, 180).trim() + "..." : clean;
    }
}
