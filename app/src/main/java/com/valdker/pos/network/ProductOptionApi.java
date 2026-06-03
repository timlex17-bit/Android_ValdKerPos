package com.valdker.pos.network;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.valdker.pos.SessionManager;
import com.valdker.pos.models.ProductOption;
import com.valdker.pos.models.ProductScanResponse;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
public class ProductOptionApi {

    public interface ProductListCallback {
        void onSuccess(List<ProductOption> products);
        void onError(String message);
    }

    public interface ProductScanCallback {
        void onSuccess(ProductScanResponse response);
        void onError(String message);
    }

    private static Map<String, String> headers(SessionManager sessionManager) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");

        String token = sessionManager.getToken();
        if (token != null && !token.trim().isEmpty()) {
            headers.put("Authorization", "Token " + token);
        }

        return headers;
    }

    public static void getProducts(
            @NonNull Context context,
            @NonNull SessionManager sessionManager,
            @NonNull ProductListCallback callback
    ) {
        String url = ApiConfig.url(sessionManager, "api/products/?page_size=1000");
        fetchProductPage(context, sessionManager, url, new ArrayList<>(), callback);
    }

    private static void fetchProductPage(
            @NonNull Context context,
            @NonNull SessionManager sessionManager,
            @NonNull String url,
            @NonNull List<ProductOption> accumulated,
            @NonNull ProductListCallback callback
    ) {
        StringRequest request = new StringRequest(
                Request.Method.GET,
                url,
                response -> {
                    try {
                        Object parsed = new JSONTokener(response == null ? "[]" : response).nextValue();

                        if (parsed instanceof JSONArray) {
                            appendProducts((JSONArray) parsed, accumulated);
                            Log.d("PRODUCTS", "Loaded=" + accumulated.size());
                            callback.onSuccess(accumulated);
                            return;
                        }

                        if (parsed instanceof JSONObject) {
                            JSONObject obj = (JSONObject) parsed;
                            JSONArray results = obj.optJSONArray("results");
                            if (results == null) {
                                results = obj.optJSONArray("data");
                            }

                            appendProducts(results, accumulated);

                            String next = obj.optString("next", "");
                            if (next != null && !next.trim().isEmpty() && !"null".equalsIgnoreCase(next.trim())) {
                                fetchProductPage(context, sessionManager, normalizeProductPageUrl(sessionManager, next.trim()), accumulated, callback);
                                return;
                            }

                            Log.d("PRODUCTS", "Loaded=" + accumulated.size());
                            callback.onSuccess(accumulated);
                            return;
                        }

                        Log.d("PRODUCTS", "Loaded=" + accumulated.size());
                        callback.onSuccess(accumulated);
                    } catch (Exception e) {
                        callback.onError("Parse products failed: " + e.getMessage());
                    }
                },
                error -> callback.onError(parseVolleyError(error))
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return headers(sessionManager);
            }
        };

        ApiClient.getInstance(context).add(request);
    }

    private static void appendProducts(JSONArray arr, List<ProductOption> products) {
        if (arr == null) return;

        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.optJSONObject(i);
            if (obj != null) {
                products.add(ProductOption.fromJson(obj));
            }
        }
    }

    private static String normalizeProductPageUrl(SessionManager sessionManager, String next) {
        if (next.startsWith("http://") || next.startsWith("https://")) {
            return next;
        }

        return ApiConfig.url(sessionManager, next);
    }

    public static void scanProduct(
            @NonNull Context context,
            @NonNull SessionManager sessionManager,
            @NonNull String code,
            @NonNull ProductScanCallback callback
    ) {
        String url = ApiConfig.url(sessionManager, "products/scan/?code=" + Uri.encode(code.trim()));

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                response -> callback.onSuccess(ProductScanResponse.fromJson(response)),
                error -> callback.onError(parseVolleyError(error))
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return headers(sessionManager);
            }
        };

        ApiClient.getInstance(context).add(request);
    }

    private static String parseVolleyError(com.android.volley.VolleyError error) {
        if (error == null) return "Unknown error";

        if (error.networkResponse != null) {
            int code = error.networkResponse.statusCode;

            String body = "";
            try {
                body = new String(error.networkResponse.data, "UTF-8");
            } catch (Exception ignored) {
            }

            if (body != null && !body.trim().isEmpty()) {
                return "HTTP " + code + ": " + body;
            }

            return "HTTP " + code;
        }

        if (error.getMessage() != null) {
            return error.getMessage();
        }

        return "Network error";
    }
}
