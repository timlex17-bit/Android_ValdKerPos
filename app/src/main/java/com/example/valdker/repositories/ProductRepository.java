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
import com.example.valdker.models.Product;
import com.example.valdker.network.ApiClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ProductRepository {

    private static final String TAG = "PRODUCT_REPO";
    private static final String ENDPOINT_PRODUCTS =
            "https://valdker.onrender.com/api/products/";

    private static final int TIMEOUT_MS = 20000;
    private static final int MAX_RETRIES = 1;
    private static final float BACKOFF_MULT = 1.2f;

    public interface Callback {
        void onSuccess(@NonNull List<Product> products);
        void onError(int statusCode, @NonNull String message);
    }

    public interface ItemCallback {
        void onSuccess(@NonNull Product product);
        void onError(int statusCode, @NonNull String message);
    }

    public interface DeleteCallback {
        void onSuccess();
        void onError(int statusCode, @NonNull String message);
    }

    private final Context appContext;

    public ProductRepository(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

    public void fetchProducts(@Nullable String token,
                              @Nullable String categoryId,
                              @NonNull Callback cb) {

        final String url = buildProductsUrl(categoryId);
        Log.i(TAG, "REQ: GET " + url);

        JsonArrayRequest req = new JsonArrayRequest(
                Request.Method.GET,
                url,
                null,
                (JSONArray res) -> {

                    List<Product> out =
                            new ArrayList<>(res != null ? res.length() : 0);

                    if (res == null) {
                        cb.onSuccess(out);
                        return;
                    }

                    for (int i = 0; i < res.length(); i++) {
                        JSONObject o = res.optJSONObject(i);
                        if (o == null) continue;

                        if (i == 0) Log.d(TAG, "First product JSON: " + o);

                        out.add(parseProduct(o));
                    }

                    cb.onSuccess(out);
                },
                err -> {
                    int code = -1;
                    String message = "Unknown error";

                    NetworkResponse nr = err.networkResponse;
                    if (nr != null) {
                        code = nr.statusCode;
                        message = buildVolleyErrorMessage(nr);
                    } else if (err.getMessage() != null) {
                        message = err.getMessage();
                    }

                    Log.e(TAG, "Fetch products failed: " + code + " " + message, err);
                    cb.onError(code, message);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> h = new HashMap<>();
                h.put("Accept", "application/json");
                if (token != null && !token.trim().isEmpty()) {
                    h.put("Authorization", "Token " + token.trim());
                }
                return h;
            }
        };

        req.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, MAX_RETRIES, BACKOFF_MULT));
        req.setShouldCache(false);

        ApiClient.getInstance(appContext).add(req);
    }

    public void fetchProducts(@Nullable String token, @NonNull Callback cb) {
        fetchProducts(token, "all", cb);
    }

    // ==========================================================
    // CREATE PRODUCT (JSON) - send dual keys for FK fields
    // ==========================================================
    public void createProduct(@NonNull String token,
                              @NonNull String name,
                              @NonNull String sku,          // ✅ NEW
                              @NonNull String code,
                              int categoryId,
                              @Nullable String description,
                              int stock,
                              @NonNull String buyPrice,
                              @NonNull String sellPrice,
                              @NonNull String weight,
                              int unitId,
                              int supplierId,
                              @NonNull ItemCallback cb) {

        final String url = ENDPOINT_PRODUCTS;
        Log.i(TAG, "REQ: POST " + url);

        JSONObject body = new JSONObject();
        try {
            body.put("name", name);
            body.put("sku", sku); // ✅ NEW
            body.put("code", code);

            // category can be writable as "category" OR "category_id"
            body.put("category", categoryId);
            body.put("category_id", categoryId);

            body.put("description", description != null ? description : "");
            body.put("stock", stock);

            body.put("buy_price", buyPrice);
            body.put("sell_price", sellPrice);
            body.put("weight", weight);

            // unit/supplier can be writable as "_id" on many DRF serializers
            body.put("unit", unitId);
            body.put("unit_id", unitId);

            body.put("supplier", supplierId);
            body.put("supplier_id", supplierId);

        } catch (Exception e) {
            cb.onError(0, "Build body error: " + e.getMessage());
            return;
        }

        Log.d(TAG, "POST body: " + body);

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.POST,
                url,
                body,
                (JSONObject res) -> {
                    try {
                        Log.d(TAG, "Create product response: " + res);
                        cb.onSuccess(parseProduct(res));
                    } catch (Exception e) {
                        cb.onError(0, "Parse error: " + e.getMessage());
                    }
                },
                err -> {
                    int code2 = -1;
                    String msg = "Network error";

                    NetworkResponse nr = err.networkResponse;
                    if (nr != null) {
                        code2 = nr.statusCode;
                        msg = buildVolleyErrorMessage(nr);
                    } else if (err.getMessage() != null) {
                        msg = err.getMessage();
                    }

                    Log.e(TAG, "Create product failed: " + code2 + " " + msg, err);
                    cb.onError(code2, msg);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> h = new HashMap<>();
                h.put("Accept", "application/json");
                h.put("Content-Type", "application/json");
                h.put("Authorization", "Token " + token.trim());
                return h;
            }
        };

        req.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, MAX_RETRIES, BACKOFF_MULT));
        req.setShouldCache(false);

        ApiClient.getInstance(appContext).add(req);
    }

    // ==========================================================
    // UPDATE PRODUCT (JSON) - send dual keys for FK fields
    // ==========================================================
    public void updateProduct(@NonNull String token,
                              @NonNull String productId,
                              @NonNull String name,
                              @NonNull String sku,          // ✅ NEW
                              @NonNull String code,
                              int categoryId,
                              @Nullable String description,
                              int stock,
                              @NonNull String buyPrice,
                              @NonNull String sellPrice,
                              @NonNull String weight,
                              int unitId,
                              int supplierId,
                              @NonNull ItemCallback cb) {

        final String url = ENDPOINT_PRODUCTS + productId + "/";
        Log.i(TAG, "REQ: PUT " + url);

        JSONObject body = new JSONObject();
        try {
            body.put("name", name);
            body.put("sku", sku); // ✅ NEW
            body.put("code", code);

            // category dual keys
            body.put("category", categoryId);
            body.put("category_id", categoryId);

            body.put("description", description != null ? description : "");
            body.put("stock", stock);

            body.put("buy_price", buyPrice);
            body.put("sell_price", sellPrice);
            body.put("weight", weight);

            // unit/supplier dual keys (fix: server often expects unit_id/supplier_id)
            body.put("unit", unitId);
            body.put("unit_id", unitId);

            body.put("supplier", supplierId);
            body.put("supplier_id", supplierId);

        } catch (Exception e) {
            cb.onError(0, "Build body error: " + e.getMessage());
            return;
        }

        Log.d(TAG, "PUT body: " + body);

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.PUT,
                url,
                body,
                res -> {
                    try {
                        Log.d(TAG, "Update product response: " + res);
                        cb.onSuccess(parseProduct(res));
                    } catch (Exception e) {
                        cb.onError(0, "Parse error: " + e.getMessage());
                    }
                },
                err -> {
                    int code2 = -1;
                    String msg = "Network error";

                    NetworkResponse nr = err.networkResponse;
                    if (nr != null) {
                        code2 = nr.statusCode;
                        msg = buildVolleyErrorMessage(nr);
                    } else if (err.getMessage() != null) {
                        msg = err.getMessage();
                    }

                    Log.e(TAG, "Update product failed: " + code2 + " " + msg, err);
                    cb.onError(code2, msg);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> h = new HashMap<>();
                h.put("Accept", "application/json");
                h.put("Content-Type", "application/json");
                h.put("Authorization", "Token " + token.trim());
                return h;
            }
        };

        req.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, MAX_RETRIES, BACKOFF_MULT));
        req.setShouldCache(false);

        ApiClient.getInstance(appContext).add(req);
    }

    // ==========================================================
    // DELETE PRODUCT
    // ==========================================================
    public void deleteProduct(@NonNull String token,
                              @NonNull String productId,
                              @NonNull DeleteCallback cb) {

        final String url = ENDPOINT_PRODUCTS + productId + "/";
        Log.i(TAG, "REQ: DELETE " + url);

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.DELETE,
                url,
                null,
                res -> cb.onSuccess(),
                err -> {
                    int code2 = -1;
                    String msg = "Network error";

                    NetworkResponse nr = err.networkResponse;
                    if (nr != null) {
                        code2 = nr.statusCode;
                        msg = buildVolleyErrorMessage(nr);
                    } else if (err.getMessage() != null) {
                        msg = err.getMessage();
                    }

                    Log.e(TAG, "Delete product failed: " + code2 + " " + msg, err);
                    cb.onError(code2, msg);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> h = new HashMap<>();
                h.put("Accept", "application/json");
                h.put("Authorization", "Token " + token.trim());
                return h;
            }
        };

        req.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, MAX_RETRIES, BACKOFF_MULT));
        req.setShouldCache(false);

        ApiClient.getInstance(appContext).add(req);
    }

    // =========================
    // URL builder
    // =========================
    private static String buildProductsUrl(@Nullable String categoryId) {
        String c = (categoryId == null) ? "" : categoryId.trim();
        if (c.isEmpty() || "all".equalsIgnoreCase(c) || "-1".equals(c)) {
            return ENDPOINT_PRODUCTS;
        }
        return ENDPOINT_PRODUCTS + "?category=" + c;
    }

    // =========================
    // Parsing (now includes sku + buy/sell/weight + unit/supplier ids)
    // =========================
    private static Product parseProduct(@NonNull JSONObject o) {
        String id = asString(o, "id", "uuid");
        String name = asString(o, "name", "title", "product_name");

        // ✅ SKU is its own field (new backend field)
        String sku = asString(o, "sku");

        double price = asDouble(
                o,
                "sell_price", "selling_price", "price", "sale_price",
                "unit_price",
                "price_usd", "usd_price",
                "amount"
        );

        // ✅ barcode/code is NOT sku (keep code first)
        String barcode = asString(o, "code", "barcode");
        int stock = asInt(o, "stock", "qty", "quantity", "current_stock");

        String imageUrl = asString(o, "image_url", "image", "photo", "thumbnail", "icon_url");

        String categoryIdOut = "";
        String categoryName = "";

        Object cat = o.opt("category");
        if (cat instanceof JSONObject) {
            JSONObject c = (JSONObject) cat;
            categoryIdOut = asString(c, "id");
            categoryName = asString(c, "name", "title");
        } else if (cat != null) {
            categoryIdOut = String.valueOf(cat).trim();
        }
        if (categoryIdOut.isEmpty()) categoryIdOut = asString(o, "category_id");

        Product p = new Product(
                id,
                name,
                sku,       // ✅ NEW (constructor param)
                price,
                imageUrl,
                stock,
                categoryIdOut,
                categoryName,
                barcode
        );

        // ✅ ensure sku also stored (even if you later change constructor)
        p.sku = sku;

        // ✅ extra fields (used by ProductFormDialog)
        try {
            p.buyPrice = asString(o, "buy_price", "buyPrice");
            p.sellPrice = asString(o, "sell_price", "sellPrice");
            p.weight = asString(o, "weight");

            // unit nested
            JSONObject unitObj = o.optJSONObject("unit");
            if (unitObj != null) {
                p.unitId = asString(unitObj, "id");
                p.unitName = asString(unitObj, "name");
            } else {
                // fallback
                p.unitId = asString(o, "unit_id", "unit");
            }

            // supplier nested
            JSONObject supObj = o.optJSONObject("supplier");
            if (supObj != null) {
                p.supplierId = asString(supObj, "id");
                p.supplierName = asString(supObj, "name");
            } else {
                // fallback
                p.supplierId = asString(o, "supplier_id", "supplier");
            }

        } catch (Exception ignored) {}

        Log.d(TAG, String.format(Locale.US,
                "Parsed product: %s | %.2f | stock=%d | sku=%s | cat=%s | unit=%s | sup=%s",
                name, price, stock, safe(p.sku), categoryIdOut,
                safe(p.unitId), safe(p.supplierId)
        ));

        return p;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String asString(@NonNull JSONObject o, String... keys) {
        for (String k : keys) {
            if (k == null) continue;
            if (o.has(k) && !o.isNull(k)) {
                String v = String.valueOf(o.opt(k)).trim();
                if (!v.isEmpty() && !"null".equalsIgnoreCase(v)) return v;
            }
        }
        return "";
    }

    private static int asInt(@NonNull JSONObject o, String... keys) {
        for (String k : keys) {
            try {
                if (o.has(k) && !o.isNull(k)) {
                    Object raw = o.opt(k);
                    if (raw instanceof Number) return ((Number) raw).intValue();
                    return Integer.parseInt(String.valueOf(raw).replace(",", "").trim());
                }
            } catch (Exception ignored) {}
        }
        return 0;
    }

    private static double asDouble(@NonNull JSONObject o, String... keys) {
        for (String k : keys) {
            try {
                if (o.has(k) && !o.isNull(k)) {
                    Object raw = o.opt(k);
                    if (raw instanceof Number) return ((Number) raw).doubleValue();

                    String s = String.valueOf(raw)
                            .replace("$", "")
                            .replace("USD", "")
                            .replace("usd", "")
                            .replace(",", "")
                            .trim();

                    if (!s.isEmpty()) return Double.parseDouble(s);
                }
            } catch (Exception ignored) {}
        }
        return 0.0;
    }

    private static String buildVolleyErrorMessage(@NonNull NetworkResponse nr) {
        try {
            if (nr.data != null) {
                String body = new String(nr.data, StandardCharsets.UTF_8).trim();
                if (body.length() > 500) body = body.substring(0, 500) + "...";
                return "HTTP " + nr.statusCode + " - " + body;
            }
        } catch (Exception ignored) {}
        return "HTTP " + nr.statusCode;
    }
}
