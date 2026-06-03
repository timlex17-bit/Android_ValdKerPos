package com.valdker.pos.repositories;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.valdker.pos.SessionManager;
import com.valdker.pos.local.ShopProfileEntity;
import com.valdker.pos.local.ValoraLocalDatabase;
import com.valdker.pos.models.Shop;
import com.valdker.pos.network.ApiClient;
import com.valdker.pos.network.ApiConfig;
import com.valdker.pos.utils.NetworkUtils;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ShopRepository {

    private static final String TAG = "ShopRepository";
    private static final String MY_SHOP_URL = "api/shop/me/";
    private static final ExecutorService DB_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static String myShopUrl(@NonNull Context ctx) {
        return ApiConfig.url(new SessionManager(ctx), MY_SHOP_URL);
    }

    public interface Callback {
        void onSuccess(@NonNull Shop shop);
        void onEmpty();
        void onError(@NonNull String message);
    }

    public interface UpdateCallback {
        void onSuccess(@NonNull Shop updatedShop);
        void onError(int statusCode, @NonNull String message);
    }

    public static void fetchFirstShop(
            @NonNull Context ctx,
            @Nullable String token,
            @NonNull Callback cb
    ) {
        loadShopProfileRoomFirst(ctx, token, cb);
    }

    public static void loadShopProfileRoomFirst(
            @NonNull Context ctx,
            @Nullable String token,
            @NonNull Callback cb
    ) {
        Context appCtx = ctx.getApplicationContext();
        DB_EXECUTOR.execute(() -> {
            Shop cached = shopFromEntity(ValoraLocalDatabase.getInstance(appCtx)
                    .shopProfileDao()
                    .getCachedForShop(currentShopId(appCtx), currentShopCode(appCtx), currentApiBaseUrl(appCtx)));
            boolean hasCached = cached != null;

            if (hasCached) {
                MAIN.post(() -> cb.onSuccess(cached));
            }

            if (!NetworkUtils.isNetworkAvailable(appCtx)) {
                if (!hasCached) {
                    MAIN.post(cb::onEmpty);
                }
                return;
            }

            if (token == null || token.trim().isEmpty()) {
                if (!hasCached) {
                    MAIN.post(() -> cb.onError("Token missing"));
                }
                return;
            }

            MAIN.post(() -> refreshShopProfileFromApi(appCtx, token, new Callback() {
                @Override
                public void onSuccess(@NonNull Shop shop) {
                    cb.onSuccess(shop);
                }

                @Override
                public void onEmpty() {
                    if (!hasCached) cb.onEmpty();
                }

                @Override
                public void onError(@NonNull String message) {
                    if (hasCached) {
                        Log.w(TAG, "Keeping cached shop profile after API error: " + message);
                    } else {
                        cb.onError(message);
                    }
                }
            }));
        });
    }

    public static void refreshShopProfileFromApi(
            @NonNull Context ctx,
            @Nullable String token,
            @NonNull Callback cb
    ) {
        String url = myShopUrl(ctx);

        com.android.volley.toolbox.JsonObjectRequest req = new com.android.volley.toolbox.JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                (JSONObject res) -> {
                    if (res == null) {
                        cb.onError("Invalid response");
                        return;
                    }

                    Shop shop = parseShop(ctx, res);
                    saveShopProfileToRoom(ctx, shop);
                    cb.onSuccess(shop);
                },
                err -> {
                    int code = (err.networkResponse != null) ? err.networkResponse.statusCode : -1;
                    String body = "";

                    try {
                        if (err.networkResponse != null && err.networkResponse.data != null) {
                            body = new String(err.networkResponse.data, StandardCharsets.UTF_8);
                        }
                    } catch (Exception ignored) {
                    }

                    String msg = (code != -1)
                            ? ("HTTP " + code + " " + body)
                            : ("Network: " + err);
                    cb.onError(msg);
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

        req.setShouldCache(false);
        ApiClient.getInstance(ctx).add(req);
    }

    public static void getBestShopProfileForReceipt(
            @NonNull Context ctx,
            @Nullable String token,
            @NonNull Callback cb
    ) {
        Context appCtx = ctx.getApplicationContext();
        DB_EXECUTOR.execute(() -> {
            Shop cached = shopFromEntity(ValoraLocalDatabase.getInstance(appCtx)
                    .shopProfileDao()
                    .getCachedForShop(currentShopId(appCtx), currentShopCode(appCtx), currentApiBaseUrl(appCtx)));

            if (cached != null) {
                Log.i(TAG, "Receipt shop profile loaded from Room");
                MAIN.post(() -> cb.onSuccess(cached));
                return;
            }

            if (!NetworkUtils.isNetworkAvailable(appCtx)) {
                MAIN.post(cb::onEmpty);
                return;
            }

            if (token == null || token.trim().isEmpty()) {
                MAIN.post(() -> cb.onError("Token missing"));
                return;
            }

            MAIN.post(() -> refreshShopProfileFromApi(appCtx, token, new Callback() {
                @Override
                public void onSuccess(@NonNull Shop shop) {
                    Log.i(TAG, "Receipt shop profile loaded from API");
                    cb.onSuccess(shop);
                }

                @Override
                public void onEmpty() {
                    cb.onEmpty();
                }

                @Override
                public void onError(@NonNull String message) {
                    cb.onError(message);
                }
            }));
        });
    }

    public static void saveShopProfileToRoom(@NonNull Context ctx, @NonNull Shop shop) {
        Context appCtx = ctx.getApplicationContext();
        DB_EXECUTOR.execute(() -> saveShopProfileToRoomSync(appCtx, shop));
    }

    public static void getCachedShopProfile(@NonNull Context ctx, @NonNull Callback cb) {
        Context appCtx = ctx.getApplicationContext();
        DB_EXECUTOR.execute(() -> {
            Shop cached = shopFromEntity(ValoraLocalDatabase.getInstance(appCtx)
                    .shopProfileDao()
                    .getCachedForShop(currentShopId(appCtx), currentShopCode(appCtx), currentApiBaseUrl(appCtx)));
            if (cached != null) {
                MAIN.post(() -> cb.onSuccess(cached));
            } else {
                MAIN.post(cb::onEmpty);
            }
        });
    }

    public static void updateShopMultipart(
            @NonNull Context ctx,
            int shopId,
            @NonNull String token,
            @NonNull Map<String, String> fields,
            @Nullable Uri logoUri,
            @Nullable Uri allIconUri,
            @NonNull UpdateCallback cb
    ) {
        String url = myShopUrl(ctx);

        MultipartRequest req = new MultipartRequest(
                Request.Method.PATCH,
                url,
                token,
                fields,
                logoUri,
                allIconUri,
                ctx,
                response -> {
                    try {
                        Shop shop = parseShop(ctx, response);
                        saveShopProfileToRoom(ctx, shop);
                        cb.onSuccess(shop);
                    } catch (Exception e) {
                        cb.onError(500, "Parse error: " + e.getMessage());
                    }
                },
                cb::onError
        );

        req.setShouldCache(false);
        ApiClient.getInstance(ctx).add(req);
    }

    private static Shop parseShop(@NonNull Context ctx, @NonNull JSONObject o) {
        Shop s = new Shop();
        s.id = o.optInt("id");
        s.shopId = firstNonEmpty(o.optString("shop_id", ""), o.optString("shopId", ""));
        s.name = firstNonEmpty(o.optString("name", ""), o.optString("store_name", ""));
        s.storeName = firstNonEmpty(o.optString("store_name", ""), s.name);
        s.address = o.optString("address", "");
        s.phone = o.optString("phone", "");
        s.email = o.optString("email", "");
        s.logoUrl = normalizeMediaUrl(ctx, firstNonEmpty(o.optString("logo_url", null), o.optString("logo", null)));
        s.location = o.optString("location", "");
        s.version = o.optString("version", "");
        s.updatedAt = firstNonEmpty(o.optString("updated_at", ""), o.optString("updatedAt", ""));
        s.lastSyncAt = System.currentTimeMillis();
        s.allCategoryIconUrl = normalizeMediaUrl(ctx, o.optString("all_category_icon_url", null));
        return s;
    }

    private static void saveShopProfileToRoomSync(@NonNull Context ctx, @NonNull Shop shop) {
        try {
            ValoraLocalDatabase.getInstance(ctx)
                    .shopProfileDao()
                    .upsert(entityFromShop(ctx, shop));
        } catch (Exception e) {
            Log.e(TAG, "Failed to cache shop profile", e);
        }
    }

    @NonNull
    private static ShopProfileEntity entityFromShop(@NonNull Context ctx, @NonNull Shop shop) {
        ShopProfileEntity entity = new ShopProfileEntity();
        entity.localKey = 1;
        entity.id = shop.id;
        entity.shopId = currentShopId(ctx);
        entity.shopCode = currentShopCode(ctx);
        entity.apiBaseUrl = currentApiBaseUrl(ctx);
        entity.cacheKey = cacheKey(ctx, String.valueOf(shop.id));
        entity.name = safe(shop.name);
        entity.storeName = safe(shop.storeName);
        entity.address = safe(shop.address);
        entity.phone = safe(shop.phone);
        entity.email = safe(shop.email);
        entity.logoUrl = safe(shop.logoUrl);
        entity.location = safe(shop.location);
        entity.version = safe(shop.version);
        entity.updatedAt = safe(shop.updatedAt);
        entity.lastSyncAt = shop.lastSyncAt > 0L ? shop.lastSyncAt : System.currentTimeMillis();
        return entity;
    }

    @Nullable
    private static Shop shopFromEntity(@Nullable ShopProfileEntity entity) {
        if (entity == null) return null;

        Shop shop = new Shop();
        shop.id = entity.id;
        shop.shopId = entity.shopId;
        shop.name = firstNonEmpty(entity.name, firstNonEmpty(entity.storeName, "ValoraPOS"));
        shop.storeName = firstNonEmpty(entity.storeName, shop.name);
        shop.address = entity.address;
        shop.phone = entity.phone;
        shop.email = entity.email;
        shop.logoUrl = entity.logoUrl;
        shop.location = entity.location;
        shop.version = entity.version;
        shop.updatedAt = entity.updatedAt;
        shop.lastSyncAt = entity.lastSyncAt;
        return shop;
    }

    @NonNull
    private static String firstNonEmpty(@Nullable String first, @Nullable String second) {
        String a = safe(first);
        return !a.isEmpty() ? a : safe(second);
    }

    @NonNull
    private static String safe(@Nullable String value) {
        if (value == null) return "";
        String clean = value.trim();
        return "null".equalsIgnoreCase(clean) ? "" : clean;
    }

    @NonNull
    private static String currentShopId(@NonNull Context ctx) {
        SessionManager session = new SessionManager(ctx);
        int shopId = session.getShopId();
        return shopId > 0 ? String.valueOf(shopId) : "";
    }

    @NonNull
    private static String currentShopCode(@NonNull Context ctx) {
        return safe(new SessionManager(ctx).getShopCode()).toUpperCase(java.util.Locale.US);
    }

    @NonNull
    private static String currentApiBaseUrl(@NonNull Context ctx) {
        return safe(new SessionManager(ctx).getBaseUrl());
    }

    @NonNull
    private static String cacheKey(@NonNull Context ctx, @NonNull String backendId) {
        return currentApiBaseUrl(ctx) + "|" + currentShopId(ctx) + "|" + currentShopCode(ctx) + "|" + safe(backendId);
    }

    @Nullable
    private static String normalizeMediaUrl(@NonNull Context ctx, @Nullable String url) {
        if (url == null) return null;

        String clean = url.trim();
        if (clean.isEmpty() || "null".equalsIgnoreCase(clean)) return null;

        if (clean.startsWith("http://")) {
            return "https://" + clean.substring("http://".length());
        }

        if (clean.startsWith("https://")) {
            return clean;
        }

        if (!clean.startsWith("/")) {
            return clean;
        }

        String base = ApiConfig.base(new SessionManager(ctx));
        int apiIndex = base.indexOf("/api/");
        if (apiIndex >= 0) {
            base = base.substring(0, apiIndex + 1);
        }

        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }

        return base + clean;
    }

    private static class MultipartRequest extends Request<JSONObject> {

        interface Listener {
            void onResponse(@NonNull JSONObject response);
        }

        interface ErrorListener {
            void onError(int statusCode, @NonNull String message);
        }

        private final Listener listener;
        private final ErrorListener errorListener;
        private final String boundary = "----ValdKerBoundary" + System.currentTimeMillis();
        private final String mimeType = "multipart/form-data; boundary=" + boundary;

        private final String token;
        private final Map<String, String> fields;
        private final Uri logoUri;
        private final Uri allIconUri;
        private final Context ctx;

        MultipartRequest(
                int method,
                @NonNull String url,
                @NonNull String token,
                @NonNull Map<String, String> fields,
                @Nullable Uri logoUri,
                @Nullable Uri allIconUri,
                @NonNull Context ctx,
                @NonNull Listener listener,
                @NonNull ErrorListener errorListener
        ) {
            super(method, url, error -> {
                int code = (error != null && error.networkResponse != null)
                        ? error.networkResponse.statusCode
                        : -1;

                String msg = "Network error";
                try {
                    if (error != null && error.networkResponse != null && error.networkResponse.data != null) {
                        msg = new String(error.networkResponse.data, StandardCharsets.UTF_8);
                    } else if (error != null && error.getMessage() != null) {
                        msg = error.getMessage();
                    }
                } catch (Exception ignored) {
                }

                errorListener.onError(code, msg);
            });

            this.token = token;
            this.fields = fields;
            this.logoUri = logoUri;
            this.allIconUri = allIconUri;
            this.ctx = ctx.getApplicationContext();
            this.listener = listener;
            this.errorListener = errorListener;
        }

        @Override
        public String getBodyContentType() {
            return mimeType;
        }

        @Override
        public Map<String, String> getHeaders() throws AuthFailureError {
            Map<String, String> h = new HashMap<>();
            h.put("Accept", "application/json");
            h.put("Authorization", "Token " + token.trim());
            return h;
        }

        @Override
        public byte[] getBody() {
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();

                for (Map.Entry<String, String> e : fields.entrySet()) {
                    writeFormField(bos, e.getKey(), e.getValue() == null ? "" : e.getValue());
                }

                if (logoUri != null) {
                    String logoName = getFileName(ctx, logoUri, "shop_logo.jpg");
                    writeFileField(bos, "logo", logoUri, logoName);
                }

                if (allIconUri != null) {
                    String iconName = getFileName(ctx, allIconUri, "all_icon.png");
                    writeFileField(bos, "all_category_icon", allIconUri, iconName);
                }

                bos.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
                return bos.toByteArray();

            } catch (Exception e) {
                Log.e(TAG, "Multipart build error", e);
                return null;
            }
        }

        private static String getFileName(@NonNull Context ctx, @NonNull Uri uri, @NonNull String fallback) {
            android.database.Cursor cursor = null;
            try {
                cursor = ctx.getContentResolver().query(uri, null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (index >= 0) {
                        String name = cursor.getString(index);
                        if (name != null && !name.trim().isEmpty()) return name;
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (cursor != null) cursor.close();
            }
            return fallback;
        }

        @Override
        protected Response<JSONObject> parseNetworkResponse(NetworkResponse response) {
            try {
                String json = new String(response.data, StandardCharsets.UTF_8);
                JSONObject obj = new JSONObject(json);
                return Response.success(obj, null);
            } catch (Exception e) {
                return Response.error(new com.android.volley.ParseError(e));
            }
        }

        @Override
        protected void deliverResponse(JSONObject response) {
            listener.onResponse(response);
        }

        private void writeFormField(ByteArrayOutputStream bos, String name, String value) throws Exception {
            bos.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            bos.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n").getBytes(StandardCharsets.UTF_8));
            bos.write(("Content-Type: text/plain; charset=UTF-8\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            bos.write((value + "\r\n").getBytes(StandardCharsets.UTF_8));
        }

        private void writeFileField(ByteArrayOutputStream bos, String fieldName, Uri uri, String fileName) throws Exception {
            byte[] fileBytes = readAllBytes(uri);
            if (fileBytes == null || fileBytes.length == 0) {
                return;
            }

            String contentType = ctx.getContentResolver().getType(uri);
            if (contentType == null || contentType.trim().isEmpty()) {
                contentType = "application/octet-stream";
            }

            bos.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            bos.write(("Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + fileName + "\"\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            bos.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            bos.write(fileBytes);
            bos.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }

        private byte[] readAllBytes(Uri uri) {
            try (InputStream is = ctx.getContentResolver().openInputStream(uri);
                 ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {

                if (is == null) return null;

                byte[] data = new byte[8192];
                int n;
                while ((n = is.read(data)) != -1) {
                    buffer.write(data, 0, n);
                }
                return buffer.toByteArray();

            } catch (Exception e) {
                Log.e(TAG, "readAllBytes error: " + e.getMessage(), e);
                return null;
            }
        }
    }
}
