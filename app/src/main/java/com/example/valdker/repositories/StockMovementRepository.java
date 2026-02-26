package com.example.valdker.repositories;

import android.content.Context;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonArrayRequest;
import com.example.valdker.SessionManager;
import com.example.valdker.models.StockMovement;
import com.example.valdker.network.ApiClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class StockMovementRepository {

    public interface Callback {
        void onSuccess(List<StockMovement> list);
        void onError(String message);
    }

    private static final String URL = "https://valdker.onrender.com/api/stockmovements/";

    public static void fetch(Context ctx, Callback cb) {
        JsonArrayRequest req = new JsonArrayRequest(
                Request.Method.GET,
                URL,
                null,
                res -> {
                    try {
                        cb.onSuccess(parse(res));
                    } catch (Exception e) {
                        cb.onError("Parse error: " + e.getMessage());
                    }
                },
                err -> {
                    String msg = "Request error";
                    if (err != null && err.networkResponse != null) {
                        msg += " (" + err.networkResponse.statusCode + ")";
                    } else if (err != null && err.getMessage() != null) {
                        msg += ": " + err.getMessage();
                    }
                    cb.onError(msg);
                }
        ) {
            @Override
            public java.util.Map<String, String> getHeaders() {
                SessionManager sm = new SessionManager(ctx);
                String token = sm.getToken();

                java.util.Map<String, String> headers = new java.util.HashMap<>();
                headers.put("Accept", "application/json");
                if (token != null && !token.trim().isEmpty()) {
                    headers.put("Authorization", "Token " + token);
                }
                return headers;
            }
        };

        req.setShouldCache(false);
        ApiClient.getInstance(ctx).add(req);
    }

    private static List<StockMovement> parse(JSONArray arr) throws Exception {
        List<StockMovement> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);

            StockMovement m = new StockMovement();
            m.id = o.optInt("id");
            m.created_at = o.optString("created_at");
            m.movement_type = o.optString("movement_type");
            m.quantity_delta = o.optInt("quantity_delta");
            m.before_stock = o.optInt("before_stock");
            m.after_stock = o.optInt("after_stock");
            m.note = o.optString("note");

            m.ref_model = o.optString("ref_model");
            m.ref_id = o.optInt("ref_id");

            m.product = o.optInt("product");
            m.product_name = o.optString("product_name");
            m.product_code = o.optString("product_code");
            m.product_sku = o.optString("product_sku");

            m.created_by = o.optInt("created_by");

            out.add(m);
        }
        return out;
    }
}