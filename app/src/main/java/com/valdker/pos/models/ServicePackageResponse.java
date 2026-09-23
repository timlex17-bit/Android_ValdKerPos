package com.valdker.pos.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

public class ServicePackageResponse {
    public int id;
    @NonNull public String name = "";
    @NonNull public String description = "";
    @NonNull public String price = "0.00";
    public int durationMinutes = 0;
    public boolean isActive = true;
    /**
     * The package's sellable shadow Product id, or null when it doesn't
     * have one yet - an old backend (before this field existed), a
     * package created before the one-time backfill ran, or a genuinely
     * missing/failed sync. A caller MUST check this for null before
     * offering to sell the package (see WorkshopPOSFragment); it is never
     * assumed present just because the field exists in the response.
     */
    @Nullable public Integer productId;
    @NonNull public String createdAt = "";
    @NonNull public String updatedAt = "";

    @NonNull
    public static ServicePackageResponse fromJson(@NonNull JSONObject obj) {
        ServicePackageResponse item = new ServicePackageResponse();
        item.id = obj.optInt("id", 0);
        item.name = safe(obj.optString("name", ""));
        item.description = safe(obj.optString("description", ""));
        item.price = safe(obj.optString("price", "0.00"));
        item.durationMinutes = obj.has("duration_minutes")
                ? obj.optInt("duration_minutes", 0)
                : obj.optInt("estimated_minutes", 0);
        item.isActive = obj.has("is_active")
                ? obj.optBoolean("is_active", true)
                : obj.optBoolean("active", true);
        item.productId = optPositiveInteger(obj, "product_id");
        item.createdAt = safe(obj.optString("created_at", ""));
        item.updatedAt = safe(obj.optString("updated_at", ""));
        return item;
    }

    @NonNull
    private static String safe(@Nullable String value) {
        return value == null || "null".equalsIgnoreCase(value.trim()) ? "" : value.trim();
    }

    /**
     * Absent key, JSON null, and a non-positive value all mean the same
     * thing here - "no product" - so all three collapse to null rather
     * than a caller having to separately check obj.has(...) and
     * obj.isNull(...) and value &gt; 0 every time it reads this field.
     * Safe against an old backend response that never sends this key at
     * all (obj.has() is false) and a malformed one that sends it as JSON
     * null explicitly.
     */
    @Nullable
    private static Integer optPositiveInteger(@NonNull JSONObject obj, @NonNull String key) {
        if (!obj.has(key) || obj.isNull(key)) return null;
        int value = obj.optInt(key, 0);
        return value > 0 ? value : null;
    }
}
