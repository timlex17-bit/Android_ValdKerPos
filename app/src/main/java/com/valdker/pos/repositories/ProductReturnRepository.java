package com.valdker.pos.repositories;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.valdker.pos.SessionManager;
import com.valdker.pos.models.ProductReturn;
import com.valdker.pos.network.ApiClient;
import com.valdker.pos.network.ApiConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
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
                response -> {
                    try {
                        List<ProductReturn> out = new ArrayList<>();
                        for (int i = 0; i < response.length(); i++) {
                            JSONObject o = response.optJSONObject(i);
                            if (o != null) {
                                out.add(ProductReturn.fromJson(o));
                            }
                        }
                        cb.onSuccess(out);
                    } catch (Exception e) {
                        Log.e(TAG, "Parse error", e);
                        cb.onError("Failed to parse response.");
                    }
                },
                error -> {
                    String msg = buildVolleyErrorMessage("Request failed.", error.networkResponse);
                    logErrorBody("Fetch list error body", error.networkResponse);
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
     * Supports:
     * - invoice return: orderId != null
     * - manual return: orderId == null
     *
     * customerId is optional.
     */
    public static void create(
            @NonNull Context ctx,
            @Nullable Integer orderId,
            @Nullable Integer customerId,
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

            if (orderId != null && orderId > 0) {
                body.put("order", orderId);
            }

            if (customerId != null && customerId > 0) {
                body.put("customer_id", customerId);
            }

            if (note != null) {
                String trimmed = note.trim();
                if (!trimmed.isEmpty()) {
                    body.put("note", trimmed);
                }
            }

            if (returnedAtIso != null) {
                String trimmedReturnedAt = returnedAtIso.trim();
                if (!trimmedReturnedAt.isEmpty()) {
                    body.put("returned_at", trimmedReturnedAt);
                }
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

            Log.d(TAG, "POST body: " + body);

            String url = ApiConfig.url(new SessionManager(ctx), ENDPOINT);

            JsonObjectRequest req = new JsonObjectRequest(
                    Request.Method.POST,
                    url,
                    body,
                    response -> {
                        try {
                            ProductReturn created = ProductReturn.fromJson(response);
                            cb.onSuccess(created);
                        } catch (Exception e) {
                            Log.e(TAG, "Parse create response error", e);
                            cb.onError("Created, but failed to parse response.");
                        }
                    },
                    error -> {
                        String msg = buildVolleyErrorMessage("Create failed.", error.networkResponse);
                        logErrorBody("Create error body", error.networkResponse);
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
        @NonNull
        public final String unitPrice;

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
                response -> cb.onSuccess(),
                error -> {
                    String msg = buildVolleyErrorMessage("Delete failed.", error.networkResponse);
                    logErrorBody("Delete error body", error.networkResponse);
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
    // HELPERS
    // =======================
    private static Map<String, String> buildHeaders(@NonNull Context ctx) {
        Map<String, String> h = new HashMap<>();
        h.put("Accept", "application/json");

        String token = new SessionManager(ctx).getToken();
        if (token != null && !token.trim().isEmpty()) {
            h.put("Authorization", "Token " + token);
        }
        return h;
    }

    @NonNull
    private static String buildVolleyErrorMessage(
            @NonNull String fallback,
            @Nullable NetworkResponse networkResponse
    ) {
        if (networkResponse == null) {
            return fallback;
        }

        String message = fallback + " (" + networkResponse.statusCode + ").";

        try {
            if (networkResponse.data != null && networkResponse.data.length > 0) {
                String raw = new String(networkResponse.data, StandardCharsets.UTF_8).trim();
                if (!raw.isEmpty()) {
                    message += " " + raw;
                }
            }
        } catch (Exception ignored) {
        }

        return message;
    }

    private static void logErrorBody(
            @NonNull String prefix,
            @Nullable NetworkResponse networkResponse
    ) {
        try {
            if (networkResponse != null && networkResponse.data != null) {
                String raw = new String(networkResponse.data, StandardCharsets.UTF_8);
                Log.e(TAG, prefix + ": " + raw);
            }
        } catch (Exception ignored) {
        }
    }
}