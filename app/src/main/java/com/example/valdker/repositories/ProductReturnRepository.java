package com.example.valdker.repositories;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.example.valdker.SessionManager;
import com.example.valdker.models.ProductReturn;
import com.example.valdker.network.ApiClient;
import com.example.valdker.network.ApiConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProductReturnRepository {

    private static final String TAG = "PRODUCT_RETURN_REPO";
    private static final String ENDPOINT = "api/productreturns/";

    // =======================
    // GET LIST
    // =======================
    public interface ListCallback {
        void onSuccess(@NonNull List<ProductReturn> items);
        void onError(@NonNull String message);
    }

    public static void fetchAll(@NonNull Context ctx, @NonNull ListCallback cb) {

        String url = ApiConfig.url(new SessionManager(ctx), ENDPOINT);

        JsonArrayRequest req = new JsonArrayRequest(
                Request.Method.GET,
                url,
                null,
                (JSONArray response) -> {
                    try {
                        List<ProductReturn> out = new ArrayList<>();
                        for (int i = 0; i < response.length(); i++) {
                            JSONObject o = response.optJSONObject(i);
                            if (o != null) out.add(ProductReturn.fromJson(o));
                        }
                        cb.onSuccess(out);
                    } catch (Exception e) {
                        Log.e(TAG, "Parse error", e);
                        cb.onError("Failed to parse response.");
                    }
                },
                error -> {
                    String msg = "Request failed.";
                    if (error.networkResponse != null) {
                        msg = "Request failed (" + error.networkResponse.statusCode + ").";
                    }
                    Log.e(TAG, msg, error);
                    cb.onError(msg);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return buildHeaders(ctx);
            }
        };

        req.setRetryPolicy(new DefaultRetryPolicy(
                20000,
                1,
                1.0f
        ));

        ApiClient.getInstance(ctx).add(req);
    }

    // =======================
    // CREATE (POST)
    // =======================
    public interface CreateCallback {
        void onSuccess(@NonNull ProductReturn created);
        void onError(@NonNull String message);
    }

    /**
     * Create Product Return (POST /api/productreturns/)
     *
     * @param orderId   required
     * @param customerId required
     * @param note      optional
     * @param returnedAtIso optional (example: "2026-02-21T13:13:17+09:00"), can be null
     * @param items     required minimal 1 item
     */
    public static void create(
            @NonNull Context ctx,
            int orderId,
            int customerId,
            @Nullable String note,
            @Nullable String returnedAtIso,
            @NonNull List<CreateItem> items,
            @NonNull CreateCallback cb
    ) {
        try {
            if (items.isEmpty()) {
                cb.onError("Items cannot be empty.");
                return;
            }

            JSONObject body = new JSONObject();
            body.put("order", orderId);

            if (customerId > 0) {
                body.put("customer_id", customerId);
            }

            if (note != null) {
                body.put("note", note);
            }

            JSONArray arr = new JSONArray();
            for (CreateItem it : items) {
                JSONObject jo = new JSONObject();
                jo.put("product_id", it.productId);
                jo.put("quantity", it.quantity);
                jo.put("unit_price", it.unitPrice);
                arr.put(jo);
            }
            body.put("items", arr);

            Log.d(TAG, "POST body: " + body.toString());

            String url = ApiConfig.url(new SessionManager(ctx), ENDPOINT);

            JsonObjectRequest req = new JsonObjectRequest(
                    Request.Method.POST,
                    url,
                    body,
                    (JSONObject response) -> {
                        try {
                            ProductReturn created = ProductReturn.fromJson(response);
                            cb.onSuccess(created);
                        } catch (Exception e) {
                            Log.e(TAG, "Parse create response error", e);
                            cb.onError("Created, but failed to parse response.");
                        }
                    },
                    error -> {
                        String msg = "Create failed.";
                        if (error.networkResponse != null) {
                            msg = "Create failed (" + error.networkResponse.statusCode + ").";
                            try {
                                if (error.networkResponse.data != null) {
                                    String raw = new String(error.networkResponse.data);
                                    Log.e(TAG, "Create error body: " + raw);
                                }
                            } catch (Exception ignored) {}
                        }
                        Log.e(TAG, msg, error);
                        cb.onError(msg);
                    }
            ) {
                @Override
                public Map<String, String> getHeaders() {
                    Map<String, String> h = buildHeaders(ctx);
                    h.put("Content-Type", "application/json");
                    return h;
                }
            };

            req.setRetryPolicy(new DefaultRetryPolicy(
                    25000,
                    1,
                    1.0f
            ));

            ApiClient.getInstance(ctx).add(req);

        } catch (Exception e) {
            Log.e(TAG, "Create build body error", e);
            cb.onError("Failed to build request body.");
        }
    }

    public static class CreateItem {
        public final int productId;
        public final int quantity;
        @NonNull public final String unitPrice;

        public CreateItem(int productId, int quantity, @NonNull String unitPrice) {
            this.productId = productId;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }
    }

    // =======================
    // DELETE
    // =======================
    public interface DeleteCallback {
        void onSuccess();
        void onError(@NonNull String message);
    }

    /**
     * Delete Product Return (DELETE /api/productreturns/{id}/)
     */
    public static void delete(
            @NonNull Context ctx,
            int productReturnId,
            @NonNull DeleteCallback cb
    ) {

        String url = ApiConfig.url(new SessionManager(ctx), ENDPOINT + productReturnId + "/");

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.DELETE,
                url,
                null,
                response -> {
                    // biasanya DELETE 204 no content, volley bisa return empty object
                    cb.onSuccess();
                },
                error -> {
                    String msg = "Delete failed.";
                    if (error.networkResponse != null) {
                        msg = "Delete failed (" + error.networkResponse.statusCode + ").";
                        try {
                            if (error.networkResponse.data != null) {
                                String raw = new String(error.networkResponse.data);
                                Log.e(TAG, "Delete error body: " + raw);
                            }
                        } catch (Exception ignored) {}
                    }
                    Log.e(TAG, msg, error);
                    cb.onError(msg);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return buildHeaders(ctx);
            }
        };

        req.setRetryPolicy(new DefaultRetryPolicy(
                20000,
                1,
                1.0f
        ));

        ApiClient.getInstance(ctx).add(req);
    }

    // =======================
    // Helpers
    // =======================
    private static Map<String, String> buildHeaders(@NonNull Context ctx) {
        Map<String, String> h = new HashMap<>();
        h.put("Accept", "application/json");

        // Token Auth
        String token = new SessionManager(ctx).getToken();
        if (token != null && !token.trim().isEmpty()) {
            h.put("Authorization", "Token " + token);
        }
        return h;
    }
}