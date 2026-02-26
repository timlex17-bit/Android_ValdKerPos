package com.example.valdker.repositories;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonArrayRequest;
import com.example.valdker.SessionManager;
import com.example.valdker.models.CustomerLite;
import com.example.valdker.models.OrderLite;
import com.example.valdker.models.ProductLite;
import com.example.valdker.network.ApiClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LiteRepository {

    private static final String TAG = "LITE_REPO";

    private static final String ORDERS_URL = "https://valdker.onrender.com/api/orders/";
    private static final String CUSTOMERS_URL = "https://valdker.onrender.com/api/customers/";
    private static final String PRODUCTS_URL = "https://valdker.onrender.com/api/products/";

    public interface LiteCallback<T> {
        void onSuccess(@NonNull List<T> items);
        void onError(@NonNull String message);
    }

    // -----------------------
    // ORDERS
    // -----------------------
    public static void fetchOrdersLite(@NonNull Context ctx, @NonNull LiteCallback<OrderLite> cb) {
        JsonArrayRequest req = new JsonArrayRequest(
                Request.Method.GET,
                ORDERS_URL,
                null,
                (JSONArray response) -> {
                    try {
                        List<OrderLite> out = new ArrayList<>();
                        for (int i = 0; i < response.length(); i++) {
                            JSONObject o = response.optJSONObject(i);
                            if (o != null) out.add(OrderLite.fromJson(o));
                        }
                        cb.onSuccess(out);
                    } catch (Exception e) {
                        Log.e(TAG, "orders parse error", e);
                        cb.onError("Failed to parse orders.");
                    }
                },
                error -> {
                    String msg = "Failed to load orders.";
                    if (error.networkResponse != null) msg += " (" + error.networkResponse.statusCode + ")";
                    Log.e(TAG, msg, error);
                    cb.onError(msg);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return buildHeaders(ctx);
            }
        };

        req.setRetryPolicy(new DefaultRetryPolicy(20000, 1, 1.0f));
        ApiClient.getInstance(ctx).add(req);
    }

    // -----------------------
    // CUSTOMERS
    // -----------------------
    public static void fetchCustomersLite(@NonNull Context ctx, @NonNull LiteCallback<CustomerLite> cb) {
        JsonArrayRequest req = new JsonArrayRequest(
                Request.Method.GET,
                CUSTOMERS_URL,
                null,
                (JSONArray response) -> {
                    try {
                        List<CustomerLite> out = new ArrayList<>();
                        for (int i = 0; i < response.length(); i++) {
                            JSONObject o = response.optJSONObject(i);
                            if (o != null) out.add(CustomerLite.fromJson(o));
                        }
                        cb.onSuccess(out);
                    } catch (Exception e) {
                        Log.e(TAG, "customers parse error", e);
                        cb.onError("Failed to parse customers.");
                    }
                },
                error -> {
                    String msg = "Failed to load customers.";
                    if (error.networkResponse != null) msg += " (" + error.networkResponse.statusCode + ")";
                    Log.e(TAG, msg, error);
                    cb.onError(msg);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return buildHeaders(ctx);
            }
        };

        req.setRetryPolicy(new DefaultRetryPolicy(20000, 1, 1.0f));
        ApiClient.getInstance(ctx).add(req);
    }

    // -----------------------
    // PRODUCTS
    // -----------------------
    public static void fetchProductsLite(@NonNull Context ctx, @NonNull LiteCallback<ProductLite> cb) {
        JsonArrayRequest req = new JsonArrayRequest(
                Request.Method.GET,
                PRODUCTS_URL,
                null,
                (JSONArray response) -> {
                    try {
                        List<ProductLite> out = new ArrayList<>();
                        for (int i = 0; i < response.length(); i++) {
                            JSONObject o = response.optJSONObject(i);
                            if (o != null) out.add(ProductLite.fromJson(o));
                        }
                        cb.onSuccess(out);
                    } catch (Exception e) {
                        Log.e(TAG, "products parse error", e);
                        cb.onError("Failed to parse products.");
                    }
                },
                error -> {
                    String msg = "Failed to load products.";
                    if (error.networkResponse != null) msg += " (" + error.networkResponse.statusCode + ")";
                    Log.e(TAG, msg, error);
                    cb.onError(msg);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return buildHeaders(ctx);
            }
        };

        req.setRetryPolicy(new DefaultRetryPolicy(20000, 1, 1.0f));
        ApiClient.getInstance(ctx).add(req);
    }

    // -----------------------
    // Headers helper
    // -----------------------
    private static Map<String, String> buildHeaders(@NonNull Context ctx) {
        Map<String, String> h = new HashMap<>();
        h.put("Accept", "application/json");

        String token = new SessionManager(ctx).getToken();
        if (token != null && !token.trim().isEmpty()) {
            h.put("Authorization", "Token " + token);
        }
        return h;
    }
}