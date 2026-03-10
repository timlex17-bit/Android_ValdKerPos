package com.example.valdker.repositories;

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
import com.example.valdker.SessionManager;
import com.example.valdker.models.Order;
import com.example.valdker.models.OrderItem;
import com.example.valdker.network.ApiClient;
import com.example.valdker.network.ApiConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

        Log.i(TAG, "createOrder() POST -> " + url);
        Log.i(TAG, "createOrder() token=" + maskToken(token));
        Log.i(TAG, "createOrder() payload=" + payload.toString());

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

                    Log.e(TAG, "createOrder ERROR code=" + code + " msg=" + msg);

                    if (err.networkResponse != null && err.networkResponse.data != null) {
                        try {
                            String body = new String(err.networkResponse.data, StandardCharsets.UTF_8);
                            Log.e(TAG, "createOrder ERROR_BODY=" + body);
                        } catch (Exception ignored) {
                        }
                    } else {
                        Log.e(TAG, "createOrder ERROR_BODY=<null networkResponse>");
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

    public void fetchOrders(@Nullable String token, @NonNull Callback cb) {

        String url = ApiConfig.url(new SessionManager(appContext), ENDPOINT);

        JsonArrayRequest req = new JsonArrayRequest(
                Request.Method.GET,
                url,
                null,
                (JSONArray res) -> {
                    try {
                        List<Order> out = parseOrders(res);
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
                if (body.length() > 250) body = body.substring(0, 250) + "...";
                return "HTTP " + nr.statusCode + " - " + body;
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
}