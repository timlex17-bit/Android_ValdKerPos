package com.valdker.pos.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

public class ServicePackageRequest {
    @NonNull public final String name;
    @NonNull public final String description;
    @NonNull public final String price;
    public final int durationMinutes;
    public final boolean isActive;

    public ServicePackageRequest(@NonNull String name,
                                 @Nullable String description,
                                 @NonNull String price,
                                 int durationMinutes,
                                 boolean isActive) {
        this.name = name.trim();
        this.description = description != null ? description.trim() : "";
        this.price = price.trim();
        this.durationMinutes = Math.max(0, durationMinutes);
        this.isActive = isActive;
    }

    @NonNull
    public JSONObject toJson() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("name", name);
            obj.put("description", description);
            obj.put("price", price);
            obj.put("duration_minutes", durationMinutes);
            obj.put("is_active", isActive);
        } catch (Exception ignored) {
        }
        return obj;
    }
}
