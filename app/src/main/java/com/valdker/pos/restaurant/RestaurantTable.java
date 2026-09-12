package com.valdker.pos.restaurant;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

/**
 * Satu meja dari {@code GET /api/tables/}.
 *
 * <p>{@code status} tidak tersimpan di server - ia diturunkan hidup-hidup dari
 * ada tidaknya order dine-in yang belum lunas di meja itu (lihat
 * docs/api/RESTAURANT_API.md). Jadi nilainya di sini adalah potret saat
 * daftar diambil, bukan kebenaran abadi, dan tidak boleh dipakai sebagai
 * kunci: meja yang occupied tetap boleh dipilih karena satu meja boleh punya
 * beberapa bill terbuka.
 */
public class RestaurantTable {

    public static final String STATUS_OCCUPIED = "occupied";

    public final long id;
    @NonNull public final String name;
    public final int capacity;
    @NonNull public final String area;
    public final boolean isActive;
    @NonNull public final String status;

    public RestaurantTable(long id,
                           @NonNull String name,
                           int capacity,
                           @NonNull String area,
                           boolean isActive,
                           @NonNull String status) {
        this.id = id;
        this.name = name;
        this.capacity = capacity;
        this.area = area;
        this.isActive = isActive;
        this.status = status;
    }

    public boolean isOccupied() {
        return STATUS_OCCUPIED.equalsIgnoreCase(status);
    }

    @Nullable
    public static RestaurantTable fromJson(@Nullable JSONObject o) {
        if (o == null) return null;
        long id = o.optLong("id", 0L);
        if (id <= 0L) return null;
        return new RestaurantTable(
                id,
                o.optString("name", "").trim(),
                o.optInt("capacity", 0),
                o.optString("area", "").trim(),
                o.optBoolean("is_active", true),
                o.optString("status", "").trim()
        );
    }
}
