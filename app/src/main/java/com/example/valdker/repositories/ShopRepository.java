package com.example.valdker.repositories;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.toolbox.JsonArrayRequest;
import com.example.valdker.SessionManager;
import com.example.valdker.models.Shop;
import com.example.valdker.network.ApiClient;
import com.example.valdker.network.ApiConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class ShopRepository {

    private static final String TAG = "ShopRepository";
    private static final String SHOPS_URL = "api/shops/";

    private static String shopDetailUrl(@NonNull Context ctx, int id) {
        return ApiConfig.url(new SessionManager(ctx), "api/shops/" + id + "/");
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
        String url = ApiConfig.url(new SessionManager(ctx), SHOPS_URL);

        JsonArrayRequest req = new JsonArrayRequest(
                Request.Method.GET,
                url,
                null,
                (JSONArray res) -> {
                    if (res == null || res.length() == 0) {
                        cb.onEmpty();
                        return;
                    }

                    JSONObject o = res.optJSONObject(0);
                    if (o == null) {
                        cb.onError("Invalid response");
                        return;
                    }

                    cb.onSuccess(parseShop(o));
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

    public static void updateShopMultipart(
            @NonNull Context ctx,
            int shopId,
            @NonNull String token,
            @NonNull Map<String, String> fields,
            @Nullable Uri logoUri,
            @Nullable Uri allIconUri,
            @NonNull UpdateCallback cb
    ) {
        String url = shopDetailUrl(ctx, shopId);

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
                        cb.onSuccess(parseShop(response));
                    } catch (Exception e) {
                        cb.onError(500, "Parse error: " + e.getMessage());
                    }
                },
                cb::onError
        );

        req.setShouldCache(false);
        ApiClient.getInstance(ctx).add(req);
    }

    private static Shop parseShop(@NonNull JSONObject o) {
        Shop s = new Shop();
        s.id = o.optInt("id");
        s.name = o.optString("name", "");
        s.address = o.optString("address", "");
        s.phone = o.optString("phone", "");
        s.email = o.optString("email", "");
        s.logoUrl = o.optString("logo_url", null);
        s.allCategoryIconUrl = o.optString("all_category_icon_url", null);
        return s;
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