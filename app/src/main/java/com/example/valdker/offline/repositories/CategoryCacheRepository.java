package com.example.valdker.offline.repositories;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonArrayRequest;
import com.example.valdker.SessionManager;
import com.example.valdker.models.Category;
import com.example.valdker.network.ApiClient;
import com.example.valdker.offline.db.DbProvider;
import com.example.valdker.offline.db.entities.CategoryEntity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CategoryCacheRepository {

    private static final String TAG = "CAT_CACHE_REPO";
    private static final String URL = "https://valdker.onrender.com/api/categories/";

    public interface ListCallback {
        void onSuccess(@NonNull List<Category> items);
        void onError(@NonNull String message);
    }

    // =========================================================
    // 1) LOAD FROM ROOM (offline fast)
    // =========================================================
    public static void loadFromRoom(@NonNull Context ctx, @NonNull ListCallback cb) {
        DbProvider.executor().execute(() -> {
            try {
                List<CategoryEntity> cached = DbProvider.get(ctx).categoryDao().getAllActive();
                List<Category> mapped = mapEntitiesToModel(cached);
                cb.onSuccess(mapped);
            } catch (Exception e) {
                Log.e(TAG, "loadFromRoom error", e);
                cb.onError("Room error: " + e.getMessage());
            }
        });
    }

    // =========================================================
    // 2) SYNC ONLINE + CACHE TO ROOM
    // =========================================================
    public static void syncOnlineAndCache(@NonNull Context ctx, @NonNull ListCallback cb) {
        SessionManager session = new SessionManager(ctx);
        String token = session.getToken();
        if (token == null || token.trim().isEmpty()) {
            cb.onError("Token empty");
            return;
        }

        JsonArrayRequest req = new JsonArrayRequest(
                Request.Method.GET,
                URL,
                null,
                (JSONArray res) -> {
                    try {
                        List<Category> list = parseCategories(res);

                        // cache room
                        cacheToRoom(ctx, list);

                        cb.onSuccess(list);
                    } catch (Exception e) {
                        Log.e(TAG, "parse/cache error", e);
                        cb.onError("Parse/cache error: " + e.getMessage());
                    }
                },
                err -> {
                    Log.w(TAG, "online fail: " + err);
                    cb.onError("Online fail: " + (err.getMessage() != null ? err.getMessage() : "unknown"));
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> h = new HashMap<>();
                h.put("Authorization", "Token " + token);
                return h;
            }
        };

        req.setTag(TAG);
        ApiClient.getInstance(ctx).add(req);
    }

    // =========================================================
    // Helpers
    // =========================================================
    private static List<Category> parseCategories(@NonNull JSONArray res) {
        List<Category> out = new ArrayList<>();
        for (int i = 0; i < res.length(); i++) {
            JSONObject o = res.optJSONObject(i);
            if (o == null) continue;

            Category c = new Category();
            c.id = o.optInt("id", 0);
            c.name = o.optString("name", "");
            // backend kamu pakai icon_url (sesuai code CategoriesFragment kamu)
            c.iconUrl = o.optString("icon_url", "");
            out.add(c);
        }
        return out;
    }

    private static void cacheToRoom(@NonNull Context ctx, @NonNull List<Category> list) {
        DbProvider.executor().execute(() -> {
            try {
                // Strategy aman:
                // - mark all inactive
                // - upsert semua hasil terbaru (aktif)
                DbProvider.get(ctx).categoryDao().markAllInactive();

                List<CategoryEntity> entities = new ArrayList<>();
                for (Category c : list) {
                    if (c == null) continue;
                    if (c.id <= 0) continue;

                    CategoryEntity e = new CategoryEntity();
                    e.server_id = String.valueOf(c.id);
                    e.name = c.name;

                    // ✅ pakai field Java
                    e.imageUrl = c.iconUrl;   // iconUrl -> imageUrl
                    e.isActive = true;        // ✅ bukan is_active

                    entities.add(e);
                }

                if (!entities.isEmpty()) {
                    DbProvider.get(ctx).categoryDao().upsertAll(entities);
                }

                Log.i(TAG, "Cached categories: " + entities.size());
            } catch (Exception e) {
                Log.e(TAG, "cacheToRoom error", e);
            }
        });
    }

    public static List<Category> mapEntitiesToModel(List<CategoryEntity> cached) {
        List<Category> out = new ArrayList<>();
        if (cached == null) return out;

        for (CategoryEntity e : cached) {
            if (e == null) continue;
            Category c = new Category();

            try { c.id = Integer.parseInt(e.server_id); } catch (Exception ex) { c.id = 0; }
            c.name = e.name;
            c.iconUrl = e.imageUrl; // ✅ FIX

            out.add(c);
        }
        return out;
    }
}