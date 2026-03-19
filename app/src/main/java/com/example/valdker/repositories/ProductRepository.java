package com.example.valdker.repositories;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.StringRequest;
import com.example.valdker.SessionManager;
import com.example.valdker.models.Product;
import com.example.valdker.network.ApiClient;
import com.example.valdker.network.ApiConfig;
import com.example.valdker.network.VolleyMultipartRequest;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProductRepository {

    private static final String TAG = "PRODUCT_REPO";
    private static final String ENDPOINT_PRODUCTS = "api/products/";
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
    private final SessionManager session;

    public ProductRepository(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.session = new SessionManager(appContext);
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
                res -> {
                    List<Product> out = new ArrayList<>(res != null ? res.length() : 0);

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

    public void createProduct(@NonNull String token,
                              @NonNull String name,
                              @NonNull String sku,
                              @NonNull String code,
                              int categoryId,
                              @Nullable String description,
                              int stock,
                              @NonNull String buyPrice,
                              @NonNull String sellPrice,
                              @NonNull String weight,
                              int unitId,
                              int supplierId,
                              @NonNull String itemType,
                              boolean isActive,
                              @Nullable Uri imageUri,
                              @NonNull ItemCallback cb) {

        final String url = ApiConfig.url(session, ENDPOINT_PRODUCTS);
        Log.i(TAG, "REQ: POST MULTIPART " + url);

        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json");
        headers.put("Authorization", "Token " + token.trim());

        VolleyMultipartRequest req = new VolleyMultipartRequest(
                Request.Method.POST,
                url,
                headers,
                response -> {
                    try {
                        String json = new String(response.data, StandardCharsets.UTF_8);
                        Log.d(TAG, "Create product response: " + json);

                        JSONObject res = new JSONObject(json);
                        cb.onSuccess(parseProduct(res));

                    } catch (Exception e) {
                        Log.e(TAG, "Create parse error", e);
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
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();

                params.put("name", safe(name));
                params.put("sku", safe(sku));
                params.put("code", safe(code));

                params.put("category_id", String.valueOf(categoryId));
                params.put("description", safe(description));
                params.put("stock", String.valueOf(stock));

                params.put("buy_price", safe(buyPrice));
                params.put("sell_price", safe(sellPrice));
                params.put("weight", safe(weight));

                params.put("unit_id", String.valueOf(unitId));
                params.put("supplier_id", String.valueOf(supplierId));

                params.put("item_type", safe(itemType));
                params.put("is_active", String.valueOf(isActive));

                Log.d(TAG, "CREATE params="
                        + " name=" + name
                        + ", code=" + code
                        + ", category_id=" + categoryId
                        + ", unit_id=" + unitId
                        + ", supplier_id=" + supplierId
                        + ", item_type=" + itemType
                        + ", is_active=" + isActive);

                return params;
            }

            @Override
            protected Map<String, DataPart> getByteData() {
                Map<String, DataPart> data = new HashMap<>();

                if (imageUri != null) {
                    try {
                        byte[] imageData = readBytesFromUri(imageUri);

                        String mimeType = appContext.getContentResolver().getType(imageUri);
                        if (mimeType == null || mimeType.trim().isEmpty()) {
                            mimeType = "image/jpeg";
                        }

                        String fileName = getFileName(imageUri);
                        if (fileName == null || fileName.trim().isEmpty()) {
                            fileName = "product_" + System.currentTimeMillis() + ".jpg";
                        }

                        data.put("image", new DataPart(fileName, imageData, mimeType));

                    } catch (Exception e) {
                        Log.e(TAG, "Create image read error", e);
                    }
                }

                return data;
            }
        };

        req.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, MAX_RETRIES, BACKOFF_MULT));
        req.setShouldCache(false);
        ApiClient.getInstance(appContext).add(req);
    }

    // IMPORTANT:
    // stock intentionally not sent on update because backend blocks direct stock edit.
    public void updateProduct(@NonNull String token,
                              int productId,
                              @NonNull String name,
                              @NonNull String sku,
                              @NonNull String code,
                              int categoryId,
                              @Nullable String description,
                              int stock,
                              @NonNull String buyPrice,
                              @NonNull String sellPrice,
                              @NonNull String weight,
                              int unitId,
                              int supplierId,
                              @NonNull String itemType,
                              boolean isActive,
                              @Nullable Uri imageUri,
                              @NonNull ItemCallback cb) {

        final String url = ApiConfig.url(session, ENDPOINT_PRODUCTS + productId + "/");
        Log.i(TAG, "REQ: PUT MULTIPART " + url);

        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json");
        headers.put("Authorization", "Token " + token.trim());

        VolleyMultipartRequest req = new VolleyMultipartRequest(
                Request.Method.PUT,
                url,
                headers,
                response -> {
                    try {
                        String json = new String(response.data, StandardCharsets.UTF_8);
                        Log.d(TAG, "Update product response: " + json);

                        JSONObject res = new JSONObject(json);
                        cb.onSuccess(parseProduct(res));

                    } catch (Exception e) {
                        Log.e(TAG, "Update parse error", e);
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
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();

                params.put("name", safe(name));
                params.put("sku", safe(sku));
                params.put("code", safe(code));

                params.put("category_id", String.valueOf(categoryId));
                params.put("description", safe(description));

                params.put("buy_price", safe(buyPrice));
                params.put("sell_price", safe(sellPrice));
                params.put("weight", safe(weight));

                params.put("unit_id", String.valueOf(unitId));
                params.put("supplier_id", String.valueOf(supplierId));

                params.put("item_type", safe(itemType));
                params.put("is_active", String.valueOf(isActive));

                Log.d(TAG, "UPDATE params="
                        + " productId=" + productId
                        + ", code=" + code
                        + ", category_id=" + categoryId
                        + ", unit_id=" + unitId
                        + ", supplier_id=" + supplierId
                        + ", item_type=" + itemType
                        + ", is_active=" + isActive);

                return params;
            }

            @Override
            protected Map<String, DataPart> getByteData() {
                Map<String, DataPart> data = new HashMap<>();

                if (imageUri != null) {
                    try {
                        byte[] imageData = readBytesFromUri(imageUri);

                        String mimeType = appContext.getContentResolver().getType(imageUri);
                        if (mimeType == null || mimeType.trim().isEmpty()) {
                            mimeType = "image/jpeg";
                        }

                        String fileName = getFileName(imageUri);
                        if (fileName == null || fileName.trim().isEmpty()) {
                            fileName = "product_" + System.currentTimeMillis() + ".jpg";
                        }

                        data.put("image", new DataPart(fileName, imageData, mimeType));

                    } catch (Exception e) {
                        Log.e(TAG, "Update image read error", e);
                    }
                }

                return data;
            }
        };

        req.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, MAX_RETRIES, BACKOFF_MULT));
        req.setShouldCache(false);
        ApiClient.getInstance(appContext).add(req);
    }

    public void deleteProduct(@NonNull String token,
                              @NonNull String productId,
                              @NonNull DeleteCallback cb) {

        final String url = ApiConfig.url(session, ENDPOINT_PRODUCTS + productId + "/");
        Log.i(TAG, "REQ: DELETE " + url);

        StringRequest req = new StringRequest(
                Request.Method.DELETE,
                url,
                response -> cb.onSuccess(),
                err -> {
                    int code2 = -1;
                    String msg = "Network error";

                    NetworkResponse nr = err.networkResponse;
                    if (nr != null) {
                        code2 = nr.statusCode;
                        msg = buildVolleyErrorMessage(nr);

                        if (code2 == 404) {
                            cb.onSuccess();
                            return;
                        }
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

        req.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, 0, BACKOFF_MULT));
        req.setShouldCache(false);
        ApiClient.getInstance(appContext).add(req);
    }

    private String buildProductsUrl(@Nullable String categoryId) {
        String c = (categoryId == null) ? "" : categoryId.trim();

        String path = ENDPOINT_PRODUCTS;
        if (!(c.isEmpty() || "all".equalsIgnoreCase(c) || "-1".equals(c))) {
            path = ENDPOINT_PRODUCTS + "?category=" + c;
        }

        return ApiConfig.url(session, path);
    }

    private byte[] readBytesFromUri(@NonNull Uri uri) throws IOException {
        InputStream is = appContext.getContentResolver().openInputStream(uri);
        if (is == null) throw new IOException("Cannot open image uri");

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int nRead;

        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }

        is.close();
        return buffer.toByteArray();
    }

    @Nullable
    private String getFileName(@NonNull Uri uri) {
        android.database.Cursor cursor = null;
        try {
            cursor = appContext.getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    return cursor.getString(index);
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return null;
    }

    private static Product parseProduct(@NonNull JSONObject o) {
        String id = asString(o, "id", "uuid");
        String name = asString(o, "name", "title", "product_name");
        String sku = asString(o, "sku");

        double price = asDouble(
                o,
                "sell_price", "selling_price", "price", "sale_price",
                "unit_price",
                "price_usd", "usd_price",
                "amount"
        );

        String barcode = asString(o, "code", "barcode");
        int stock = asInt(o, "stock", "qty", "quantity", "current_stock");
        String imageUrl = asString(o, "image_url", "image", "photo", "thumbnail", "icon_url");
        int shopId = asInt(o, "shop_id", "shop");

        String categoryIdOut = "";
        String categoryName = "";

        Object cat = o.opt("category");
        if (cat instanceof JSONObject) {
            JSONObject c = (JSONObject) cat;
            categoryIdOut = asString(c, "id");
            categoryName = asString(c, "name", "title");
        } else if (cat != null && cat != JSONObject.NULL) {
            categoryIdOut = String.valueOf(cat).trim();
        }

        if (categoryIdOut.isEmpty()) {
            categoryIdOut = asString(o, "category_id");
        }

        Product p = new Product();
        p.id = id;
        p.name = name;
        p.shopId = shopId;
        p.shop_id = shopId;

        p.sku = sku;
        p.price = price;
        p.imageUrl = imageUrl;
        p.image_url = imageUrl;
        p.stock = stock;

        p.categoryId = categoryIdOut;
        p.categoryName = categoryName;

        p.barcode = barcode;
        p.description = asString(o, "description");
        p.buyPrice = asString(o, "buy_price", "buyPrice");
        p.sellPrice = asString(o, "sell_price", "sellPrice");
        p.weight = asString(o, "weight");
        p.itemType = asString(o, "item_type");

        String activeRaw = asString(o, "is_active");
        p.isActive = "true".equalsIgnoreCase(activeRaw)
                || "1".equals(activeRaw);

        try {
            JSONObject unitObj = o.optJSONObject("unit");
            if (unitObj != null) {
                p.unitId = asString(unitObj, "id");
                p.unitName = asString(unitObj, "name");
            } else {
                p.unitId = asString(o, "unit_id", "unit");
            }

            JSONObject supObj = o.optJSONObject("supplier");
            if (supObj != null) {
                p.supplierId = asString(supObj, "id");
                p.supplierName = asString(supObj, "name");
            } else {
                p.supplierId = asString(o, "supplier_id", "supplier");
            }
        } catch (Exception ignored) {
        }

        Log.d(TAG, "parseProduct(): id=" + p.id
                + ", name=" + p.name
                + ", shopId=" + p.shopId
                + ", itemType=" + p.itemType
                + ", isActive=" + p.isActive);

        return p;
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
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
            } catch (Exception ignored) {
            }
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
            } catch (Exception ignored) {
            }
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
        } catch (Exception ignored) {
        }
        return "HTTP " + nr.statusCode;
    }
}