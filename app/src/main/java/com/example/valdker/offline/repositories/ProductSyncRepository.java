package com.example.valdker.offline.repositories;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;
import com.example.valdker.SessionManager;
import com.example.valdker.offline.db.DbProvider;
import com.example.valdker.offline.db.entities.ProductEntity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProductSyncRepository {

    private static final String TAG = "ProductSyncRepo";

    /**
     * TODO: ganti endpoint sesuai backend kamu
     * contoh: https://valdker.onrender.com/api/products/
     */
    private static final String ENDPOINT = "https://valdker.onrender.com/api/products/";

    // timeout yang nyaman untuk mobile
    private static final int TIMEOUT_MS = 20000;
    private static final int MAX_RETRIES = 1;

    public interface SyncCallback {
        void onSuccess(int insertedOrUpdatedCount);
        void onError(@NonNull String message);
    }

    /**
     * Sync products dari API -> Room
     */
    public static void syncProducts(@NonNull Context ctx, @NonNull SyncCallback cb) {
        Context appCtx = ctx.getApplicationContext();
        SessionManager sm = new SessionManager(appCtx);

        JsonArrayRequest req = new JsonArrayRequest(
                Request.Method.GET,
                ENDPOINT,
                null,
                (JSONArray res) -> {
                    // parse di background thread
                    DbProvider.executor().execute(() -> {
                        try {
                            List<ProductEntity> list = parseProducts(res);
                            DbProvider.get(appCtx).productDao().upsertAll(list);
                            cb.onSuccess(list.size());
                        } catch (Exception e) {
                            Log.e(TAG, "parse/save error", e);
                            cb.onError("Gagal parse/simpan products: " + e.getMessage());
                        }
                    });
                },
                err -> {
                    String msg = (err.getMessage() != null) ? err.getMessage() : "Network error";
                    cb.onError("Gagal sync products: " + msg);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> h = new HashMap<>();
                h.put("Accept", "application/json");

                // Token auth (kalau backend pakai TokenAuthentication DRF)
                // Pastikan SessionManager kamu punya method getToken().
                String token = safeToken(sm);
                if (token != null && !token.isEmpty()) {
                    h.put("Authorization", "Token " + token);
                }
                return h;
            }
        };

        req.setRetryPolicy(new DefaultRetryPolicy(
                TIMEOUT_MS,
                MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        ));

        // Kamu bisa pakai ApiClient.getQueue(ctx) kalau sudah punya.
        Volley.newRequestQueue(appCtx).add(req);
    }

    @Nullable
    private static String safeToken(@NonNull SessionManager sm) {
        try {
            // Sesuaikan dengan implementasi SessionManager kamu
            // Umumnya: sm.getToken()
            return sm.getToken();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parse JSONArray response -> List<ProductEntity>
     * Catatan: key JSON bisa beda di backend kamu.
     * Saya buat robust pakai optString/optDouble/optInt dan fallback key alternatif.
     */
    @NonNull
    private static List<ProductEntity> parseProducts(@NonNull JSONArray arr) throws Exception {
        List<ProductEntity> out = new ArrayList<>();

        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);

            ProductEntity p = new ProductEntity();

            // --- server id ---
            // support key: id / server_id / uuid
            String serverId =
                    optStringAny(o, "id", "server_id", "uuid");
            if (serverId == null || serverId.trim().isEmpty()) {
                // skip invalid row
                continue;
            }
            p.serverId = serverId;

            // --- name ---
            p.name = optStringAny(o, "name", "product_name", "title");

            // --- barcode / sku ---
            p.barcode = optStringAny(o, "barcode", "product_code", "code");
            p.sku = optStringAny(o, "sku", "product_sku");

            // --- prices ---
            p.price = optDoubleAny(o, 0.0, "price", "selling_price", "sell_price");

            // --- stock ---
            p.stock = optIntAny(o, 0, "stock", "current_stock");
            p.minStock = optIntAny(o, 0, "min_stock", "minimum_stock");

            // --- category server id (optional) ---
            p.categoryServerId = optStringAny(o, "category", "category_id", "category_server_id");

            // --- active ---
            // support: is_active / active
            if (o.has("is_active")) p.isActive = o.optBoolean("is_active", true);
            else if (o.has("active")) p.isActive = o.optBoolean("active", true);
            else p.isActive = true;

            // --- updated_at ---
            p.updatedAtIso = optStringAny(o, "updated_at", "updated_at_iso", "modified_at");

            out.add(p);
        }

        return out;
    }

    @Nullable
    private static String optStringAny(@NonNull JSONObject o, @NonNull String... keys) {
        for (String k : keys) {
            if (o.has(k) && !o.isNull(k)) {
                String v = o.optString(k, null);
                if (v != null) return v;
            }
        }
        return null;
    }

    private static double optDoubleAny(@NonNull JSONObject o, double def, @NonNull String... keys) {
        for (String k : keys) {
            if (o.has(k) && !o.isNull(k)) {
                try {
                    Object raw = o.get(k);
                    if (raw instanceof Number) return ((Number) raw).doubleValue();
                    String s = String.valueOf(raw);
                    if (!s.trim().isEmpty()) return Double.parseDouble(s);
                } catch (Exception ignored) {}
            }
        }
        return def;
    }

    private static int optIntAny(@NonNull JSONObject o, int def, @NonNull String... keys) {
        for (String k : keys) {
            if (o.has(k) && !o.isNull(k)) {
                try {
                    Object raw = o.get(k);
                    if (raw instanceof Number) return ((Number) raw).intValue();
                    String s = String.valueOf(raw);
                    if (!s.trim().isEmpty()) return Integer.parseInt(s);
                } catch (Exception ignored) {}
            }
        }
        return def;
    }
}