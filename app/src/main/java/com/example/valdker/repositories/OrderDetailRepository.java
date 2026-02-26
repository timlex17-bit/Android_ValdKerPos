package com.example.valdker.repositories;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.example.valdker.SessionManager;
import com.example.valdker.models.OrderItemLite;
import com.example.valdker.network.ApiClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OrderDetailRepository {

    private static final String TAG = "ORDER_DETAIL_REPO";
    private static final String BASE = "https://valdker.onrender.com/api/orders/";

    public static class OrderDetail {
        public int id;
        public String invoiceNumber;
        public Integer customerId = null;
        public String customerName = null;
        public List<OrderItemLite> items = new ArrayList<>();
    }

    public interface Callback {
        void onSuccess(@NonNull OrderDetail detail);
        void onError(@NonNull String message);
    }

    public static void fetch(@NonNull Context ctx, int orderId, @NonNull Callback cb) {
        String url = BASE + orderId + "/";

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                res -> {
                    try {
                        OrderDetail d = new OrderDetail();
                        d.id = res.optInt("id");
                        d.invoiceNumber = res.optString("invoice_number", "");

                        // customer bisa null
                        JSONObject cust = res.optJSONObject("customer");
                        if (cust != null) {
                            d.customerId = cust.optInt("id");
                            d.customerName = cust.optString("name", "");
                        }

                        JSONArray arr = res.optJSONArray("items");
                        if (arr != null) {
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject o = arr.optJSONObject(i);
                                if (o != null) d.items.add(OrderItemLite.fromJson(o));
                            }
                        }

                        cb.onSuccess(d);

                    } catch (Exception e) {
                        Log.e(TAG, "parse error", e);
                        cb.onError("Failed to parse order detail.");
                    }
                },
                err -> {
                    String msg = "Failed to load order detail.";
                    if (err.networkResponse != null) msg += " (" + err.networkResponse.statusCode + ")";
                    Log.e(TAG, msg, err);
                    cb.onError(msg);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> h = new HashMap<>();
                h.put("Accept", "application/json");
                String token = new SessionManager(ctx).getToken();
                if (token != null && !token.trim().isEmpty()) h.put("Authorization", "Token " + token);
                return h;
            }
        };

        req.setRetryPolicy(new DefaultRetryPolicy(20000, 1, 1.0f));
        ApiClient.getInstance(ctx).add(req);
    }
}