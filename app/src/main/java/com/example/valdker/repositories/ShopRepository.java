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
import com.android.volley.toolbox.JsonObjectRequest;
import com.example.valdker.models.Shop;
import com.example.valdker.network.ApiClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.HashMap;

public class ShopRepository {

    private static final String TAG = "ShopRepository";

    // ✅ LIST shops
    private static final String SHOPS_URL = "https://valdker.onrender.com/api/shops/";
    // ✅ DETAIL shop (update)
    private static String shopDetailUrl(int id) {
        return "https://valdker.onrender.com/api/shops/" + id + "/";
    }

    // ------------------------------------------------------------
    // Callback for GET
    // ------------------------------------------------------------
    public interface Callback {
        void onSuccess(@NonNull Shop shop);
        void onEmpty();
        void onError(@NonNull String message);
    }

    // ------------------------------------------------------------
    // ✅ Callback for UPDATE (THIS IS WHAT YOU ARE MISSING)
    // ------------------------------------------------------------
    public interface UpdateCallback {
        void onSuccess(@NonNull Shop updatedShop);
        void onError(int statusCode, @NonNull String message);
    }

    // ------------------------------------------------------------
    // GET first shop
    // ------------------------------------------------------------
    public static void fetchFirstShop(
            @NonNull Context ctx,
            @Nullable String token,
            @NonNull Callback cb
    ) {
        JsonArrayRequest req = new JsonArrayRequest(
                Request.Method.GET,
                SHOPS_URL,
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
                    String msg = (err.getMessage() != null) ? err.getMessage() : "Network error";
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

    // ------------------------------------------------------------
    // UPDATE shop multipart
    // fields: name, address, phone, email
    // files: logo, all_category_icon
    // ------------------------------------------------------------
    public static void updateShopMultipart(
            @NonNull Context ctx,
            int shopId,
            @NonNull String token,
            @NonNull Map<String, String> fields,
            @Nullable Uri logoUri,
            @Nullable Uri allIconUri,
            @NonNull UpdateCallback cb
    ) {

        String url = shopDetailUrl(shopId);

        // ✅ backend biasanya terima PATCH untuk partial update
        int method = Request.Method.PATCH; // ganti ke Request.Method.PUT kalau backend butuh PUT

        MultipartRequest req = new MultipartRequest(
                method,
                url,
                token,
                fields,
                logoUri,
                allIconUri,
                ctx,
                response -> {
                    try {
                        // response JSON object shop
                        cb.onSuccess(parseShop(response));
                    } catch (Exception e) {
                        cb.onError(500, "Parse error: " + e.getMessage());
                    }
                },
                (status, message) -> cb.onError(status, message)
        );

        req.setShouldCache(false);
        ApiClient.getInstance(ctx).add(req);
    }

    // ------------------------------------------------------------
    // Parse helper
    // ------------------------------------------------------------
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

    // ============================================================
    // MultipartRequest (single file class inside repository)
    // ============================================================
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
                int code = (error != null && error.networkResponse != null) ? error.networkResponse.statusCode : -1;
                String msg = (error != null && error.getMessage() != null) ? error.getMessage() : "Network error";
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

                // fields
                for (Map.Entry<String, String> e : fields.entrySet()) {
                    writeFormField(bos, e.getKey(), e.getValue() == null ? "" : e.getValue());
                }

                // files (names must match backend fields)
                if (logoUri != null) {
                    writeFileField(bos, "logo", logoUri, "shop_logo.jpg");
                }
                if (allIconUri != null) {
                    writeFileField(bos, "all_category_icon", allIconUri, "all_icon.png");
                }

                // end
                bos.write(("--" + boundary + "--\r\n").getBytes());
                return bos.toByteArray();

            } catch (Exception e) {
                Log.e(TAG, "Multipart build error", e);
                return null;
            }
        }

        @Override
        protected Response<JSONObject> parseNetworkResponse(NetworkResponse response) {
            try {
                String json = new String(response.data);
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
            bos.write(("--" + boundary + "\r\n").getBytes());
            bos.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n").getBytes());
            bos.write(("Content-Type: text/plain; charset=UTF-8\r\n\r\n").getBytes());
            bos.write((value + "\r\n").getBytes());
        }

        private void writeFileField(ByteArrayOutputStream bos, String fieldName, Uri uri, String fileName) throws Exception {
            byte[] fileBytes = readAllBytes(uri);
            if (fileBytes == null) return;

            String contentType = ctx.getContentResolver().getType(uri);
            if (contentType == null) contentType = "application/octet-stream";

            bos.write(("--" + boundary + "\r\n").getBytes());
            bos.write(("Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + fileName + "\"\r\n").getBytes());
            bos.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes());
            bos.write(fileBytes);
            bos.write("\r\n".getBytes());
        }

        private byte[] readAllBytes(Uri uri) {
            try (InputStream is = ctx.getContentResolver().openInputStream(uri)) {
                if (is == null) return null;
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
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
