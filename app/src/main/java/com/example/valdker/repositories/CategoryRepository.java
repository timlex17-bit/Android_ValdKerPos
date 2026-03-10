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
import com.example.valdker.models.Category;
import com.example.valdker.models.CategoryLite;
import com.example.valdker.network.ApiClient;
import com.example.valdker.network.ApiConfig;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CategoryRepository {

    private static final String TAG = "CategoryRepo";

    private static final String API_CATEGORIES_PATH = "api/categories/";
    private static final String API_SHOP_PATH = "api/shop/";

    // Network tuning (aligned with ProductRepository defaults)
    private static final int TIMEOUT_MS = 20000;
    private static final int MAX_RETRIES = 1;
    private static final float BACKOFF_MULT = 1.2f;

    public interface Callback {
        void onSuccess(@NonNull List<Category> categories);
        void onError(@NonNull String message);
    }

    /**
     * Fetches categories and injects an "All" item (id = -1) at the beginning.
     * The "All" icon is fetched from /api/shop/ (field: all_category_icon_url).
     * If the shop icon is unavailable, iconUrl will be null (UI may show a placeholder).
     */
    public static void fetchCategoriesWithAll(
            @NonNull Context context,
            @Nullable String tokenOrNull,
            @NonNull Callback cb
    ) {
        fetchAllIconUrl(context, tokenOrNull, allIconUrl -> {
            fetchCategoriesOnly(context, tokenOrNull, new Callback() {
                @Override
                public void onSuccess(@NonNull List<Category> categories) {
                    List<Category> out = new ArrayList<>();

                    out.add(new Category(-1, "All", allIconUrl));
                    out.addAll(categories);

                    cb.onSuccess(out);
                }

                @Override
                public void onError(@NonNull String message) {
                    cb.onError(message);
                }
            });
        });
    }

    /**
     * Fetches categories only from /api/categories/
     */
    public static void fetchCategoriesOnly(
            @NonNull Context context,
            @Nullable String tokenOrNull,
            @NonNull Callback cb
    ) {
        SessionManager session = new SessionManager(context.getApplicationContext());
        String apiCategoriesUrl = ApiConfig.url(session, API_CATEGORIES_PATH);
        String baseUrl = session.getBaseUrl();

        JsonArrayRequest req = new JsonArrayRequest(
                Request.Method.GET,
                apiCategoriesUrl,
                null,
                response -> {
                    try {
                        List<Category> list = new ArrayList<>();

                        for (int i = 0; i < response.length(); i++) {
                            JSONObject o = response.optJSONObject(i);
                            if (o == null) continue;

                            int id = o.optInt("id", 0);
                            String name = o.optString("name", "").trim();

                            String iconUrl = o.isNull("icon_url")
                                    ? null
                                    : o.optString("icon_url", null);

                            if (iconUrl != null && iconUrl.startsWith("/")) {
                                iconUrl = joinBaseAndRelative(baseUrl, iconUrl);
                            }

                            if (id > 0 && !name.isEmpty()) {
                                list.add(new Category(id, name, iconUrl));
                            }
                        }

                        cb.onSuccess(list);

                    } catch (Exception e) {
                        Log.e(TAG, "Parse error", e);
                        cb.onError("Parse error: " + e.getMessage());
                    }
                },
                error -> {
                    String msg = buildVolleyErrorMessage(error.networkResponse);
                    Log.e(TAG, "Volley error (categories): " + msg, error);
                    cb.onError(msg);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return buildHeaders(tokenOrNull);
            }
        };

        req.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, MAX_RETRIES, BACKOFF_MULT));
        req.setShouldCache(false);

        ApiClient.getInstance(context).add(req);
    }

    /**
     * Callback for fetching the "All" category icon URL.
     */
    private interface AllIconCallback {
        void onDone(@Nullable String allIconUrl);
    }

    /**
     * Fetches the "All" category icon from /api/shop/
     * Expected JSON: { "all_category_icon_url": "https://..." }
     */
    private static void fetchAllIconUrl(
            @NonNull Context context,
            @Nullable String tokenOrNull,
            @NonNull AllIconCallback cb
    ) {
        SessionManager session = new SessionManager(context.getApplicationContext());
        String apiShopUrl = ApiConfig.url(session, API_SHOP_PATH);
        String baseUrl = session.getBaseUrl();

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.GET,
                apiShopUrl,
                null,
                res -> {
                    String url = null;

                    try {
                        url = res.isNull("all_category_icon_url")
                                ? null
                                : res.optString("all_category_icon_url", null);

                        if (url != null && url.startsWith("/")) {
                            url = joinBaseAndRelative(baseUrl, url);
                        }
                    } catch (Exception ignored) {
                    }

                    cb.onDone(url);
                },
                err -> {
                    String msg = buildVolleyErrorMessage(err.networkResponse);
                    Log.w(TAG, "Shop icon fetch failed (fallback null): " + msg);
                    cb.onDone(null);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return buildHeaders(tokenOrNull);
            }
        };

        req.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, MAX_RETRIES, BACKOFF_MULT));
        req.setShouldCache(false);

        ApiClient.getInstance(context).add(req);
    }

    // ==========================================================
    // Lite fetch for Spinner usage (e.g., ProductFormDialog)
    // ==========================================================

    public interface LiteCallback {
        void onSuccess(@NonNull List<CategoryLite> list);
        void onError(int code, @NonNull String message);
    }

    /**
     * Lightweight categories list for Spinner:
     * returns only id + name (no "All", no shop icon).
     */
    public static void fetchLite(
            @NonNull Context context,
            @Nullable String tokenOrNull,
            @NonNull LiteCallback cb
    ) {
        SessionManager session = new SessionManager(context.getApplicationContext());
        String apiCategoriesUrl = ApiConfig.url(session, API_CATEGORIES_PATH);

        JsonArrayRequest req = new JsonArrayRequest(
                Request.Method.GET,
                apiCategoriesUrl,
                null,
                res -> {
                    try {
                        List<CategoryLite> out = new ArrayList<>();

                        for (int i = 0; i < res.length(); i++) {
                            JSONObject o = res.optJSONObject(i);
                            if (o == null) continue;

                            int id = o.optInt("id", 0);
                            String name = o.optString("name", "").trim();

                            if (id > 0 && !name.isEmpty()) {
                                out.add(new CategoryLite(id, name));
                            }
                        }

                        cb.onSuccess(out);

                    } catch (Exception e) {
                        cb.onError(0, "Parse error: " + e.getMessage());
                    }
                },
                err -> {
                    int code = (err.networkResponse != null) ? err.networkResponse.statusCode : -1;
                    String msg = buildVolleyErrorMessage(err.networkResponse);
                    cb.onError(code, msg);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return buildHeaders(tokenOrNull);
            }
        };

        req.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, MAX_RETRIES, BACKOFF_MULT));
        req.setShouldCache(false);

        ApiClient.getInstance(context).add(req);
    }

    // =========================
    // Helpers
    // =========================

    private static Map<String, String> buildHeaders(@Nullable String tokenOrNull) {
        Map<String, String> h = new HashMap<>();
        h.put("Accept", "application/json");

        if (tokenOrNull != null && !tokenOrNull.trim().isEmpty()) {
            h.put("Authorization", "Token " + tokenOrNull.trim());
        }

        return h;
    }

    private static String buildVolleyErrorMessage(@Nullable NetworkResponse nr) {
        if (nr == null) return "Network error";

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

    private static String joinBaseAndRelative(@NonNull String baseUrlWithSlash, @NonNull String relativeStartsWithSlash) {
        String base = baseUrlWithSlash;
        if (base.endsWith("/") && relativeStartsWithSlash.startsWith("/")) {
            return base.substring(0, base.length() - 1) + relativeStartsWithSlash;
        }
        return base + relativeStartsWithSlash;
    }
}