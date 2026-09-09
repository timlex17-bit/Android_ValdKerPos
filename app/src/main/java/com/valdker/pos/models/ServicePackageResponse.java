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
        item.createdAt = safe(obj.optString("created_at", ""));
        item.updatedAt = safe(obj.optString("updated_at", ""));
        return item;
    }

    @NonNull
    private static String safe(@Nullable String value) {
        return value == null || "null".equalsIgnoreCase(value.trim()) ? "" : value.trim();
    }
}
