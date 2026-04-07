package com.valdker.pos.repositories;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.valdker.pos.SessionManager;
import com.valdker.pos.network.ApiClient;
import com.valdker.pos.network.ApiConfig;

import java.util.HashMap;
import java.util.Map;

public class ProductDetailRepository {

    private static final String TAG = "PRODUCT_DETAIL_REPO";
    private static final String ENDPOINT = "api/products/";

    public static class ProductMini {
        public int id;
        public String name;
        public String code;
        public String sku;
    }

    public interface Callback {
        void onSuccess(@NonNull ProductMini p);
        void onError(@NonNull String message);
    }

    public static void fetch(@NonNull Context ctx, int productId, @NonNull Callback cb) {

        SessionManager sm = new SessionManager(ctx);
        String url = ApiConfig.url(sm, ENDPOINT + productId + "/");

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                res -> {
                    try {
                        ProductMini p = new ProductMini();
                        p.id = res.optInt("id");
                        p.name = res.optString("name", "");
                        p.code = res.optString("code", "");
                        p.sku = res.optString("sku", "");
                        cb.onSuccess(p);
                    } catch (Exception e) {
                        Log.e(TAG, "parse error", e);
                        cb.onError("Failed to parse product detail.");
                    }
                },
                err -> {
                    String msg = "Failed to load product detail.";
                    if (err.networkResponse != null) {
                        msg += " (" + err.networkResponse.statusCode + ")";
                    }
                    Log.e(TAG, msg, err);
                    cb.onError(msg);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> h = new HashMap<>();
                h.put("Accept", "application/json");
                String token = new SessionManager(ctx).getToken();
                if (token != null && !token.trim().isEmpty()) {
                    h.put("Authorization", "Token " + token);
                }
                return h;
            }
        };

        req.setRetryPolicy(new DefaultRetryPolicy(20000, 1, 1.0f));
        ApiClient.getInstance(ctx).add(req);
    }
}