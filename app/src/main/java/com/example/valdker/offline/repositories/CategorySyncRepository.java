package com.example.valdker.offline.repositories;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;
import com.example.valdker.SessionManager;
import com.example.valdker.offline.db.DbProvider;
import com.example.valdker.offline.db.entities.CategoryEntity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CategorySyncRepository {

    private static final String TAG = "CategorySyncRepo";

    // TODO: sesuaikan endpoint kamu
    private static final String ENDPOINT = "https://valdker.onrender.com/api/categories/";

    private static final int TIMEOUT_MS = 20000;
    private static final int MAX_RETRIES = 1;

    public interface SyncCallback {
        void onSuccess(int count);
        void onError(@NonNull String message);
    }

    public static void sync(@NonNull Context ctx, @NonNull SyncCallback cb) {
        Context appCtx = ctx.getApplicationContext();
        SessionManager sm = new SessionManager(appCtx);
        String token = sm.getToken();

        JsonArrayRequest req = new JsonArrayRequest(
                Request.Method.GET,
                ENDPOINT,
                null,
                (JSONArray res) -> DbProvider.executor().execute(() -> {
                    try {
                        List<CategoryEntity> list = parse(res);
                        DbProvider.get(appCtx).categoryDao().upsertAll(list);
                        int total = DbProvider.get(appCtx).categoryDao().countAll();
                        Log.i(TAG, "Saved categories=" + list.size() + " total=" + total);
                        cb.onSuccess(list.size());
                    } catch (Exception e) {
                        Log.e(TAG, "parse/save error", e);
                        cb.onError("Gagal parse/simpan kategori: " + e.getMessage());
                    }
                }),
                err -> cb.onError("Gagal sync kategori: " + (err.getMessage() != null ? err.getMessage() : "network error"))
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> h = new HashMap<>();
                h.put("Accept", "application/json");
                if (token != null && !token.trim().isEmpty()) {
                    h.put("Authorization", "Token " + token);
                }
                return h;
            }
        };

        req.setRetryPolicy(new DefaultRetryPolicy(
                TIMEOUT_MS, MAX_RETRIES, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        ));

        Volley.newRequestQueue(appCtx).add(req);
    }

    @NonNull
    private static List<CategoryEntity> parse(@NonNull JSONArray arr) throws Exception {
        List<CategoryEntity> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);

            CategoryEntity c = new CategoryEntity();
            String id = o.optString("id", "");
            if (id == null || id.trim().isEmpty() || "null".equalsIgnoreCase(id)) continue;
            c.server_id = id;

            c.name = o.optString("name", o.optString("title", "-"));
            c.imageUrl = o.optString("image_url", o.optString("image", null));

            if (o.has("is_active")) c.isActive = o.optBoolean("is_active", true);
            else if (o.has("active")) c.isActive = o.optBoolean("active", true);
            else c.isActive = true;

            out.add(c);
        }
        return out;
    }
}