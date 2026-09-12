package com.valdker.pos.restaurant;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Satu kandidat pelayan dari {@code GET /api/restaurant/waiters/}.
 *
 * <p>Tidak ada role "waiter" di backend dan tidak boleh dibuat di sini:
 * endpoint itu sudah mengembalikan staff aktif toko ini saja, dan menyaring
 * ulang berdasarkan {@code role} justru akan membuang pelayan yang sah.
 * {@code role} disimpan hanya untuk ditampilkan.
 */
public class Waiter {

    public final long id;
    @NonNull public final String name;
    @NonNull public final String username;
    @NonNull public final String role;
    public final int activeOrdersCount;
    @NonNull public final List<String> activeTables;

    public Waiter(long id,
                  @NonNull String name,
                  @NonNull String username,
                  @NonNull String role,
                  int activeOrdersCount,
                  @NonNull List<String> activeTables) {
        this.id = id;
        this.name = name;
        this.username = username;
        this.role = role;
        this.activeOrdersCount = activeOrdersCount;
        this.activeTables = activeTables;
    }

    /** Nama tampil; sebagian staff tidak mengisi nama lengkap. */
    @NonNull
    public String displayName() {
        return !name.isEmpty() ? name : username;
    }

    @Nullable
    public static Waiter fromJson(@Nullable JSONObject o) {
        if (o == null) return null;
        long id = o.optLong("id", 0L);
        if (id <= 0L) return null;

        List<String> tables = new ArrayList<>();
        JSONArray arr = o.optJSONArray("active_tables");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                String t = arr.optString(i, "").trim();
                if (!t.isEmpty()) tables.add(t);
            }
        }

        return new Waiter(
                id,
                o.optString("name", "").trim(),
                o.optString("username", "").trim(),
                o.optString("role", "").trim(),
                o.optInt("active_orders_count", 0),
                tables
        );
    }
}
