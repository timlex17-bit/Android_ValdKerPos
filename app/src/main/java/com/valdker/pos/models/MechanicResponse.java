package com.valdker.pos.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

public class MechanicResponse {
    public int id;
    @NonNull public String name = "";
    @NonNull public String phone = "";
    @NonNull public String specialty = "";
    public boolean isActive = true;
    @NonNull public String createdAt = "";
    @NonNull public String updatedAt = "";

    @NonNull
    public static MechanicResponse fromJson(@NonNull JSONObject obj) {
        MechanicResponse mechanic = new MechanicResponse();
        mechanic.id = obj.optInt("id", 0);
        mechanic.name = safe(obj.optString("name", ""));
        mechanic.phone = safe(obj.optString("phone", ""));
        mechanic.specialty = safe(obj.optString("specialty", ""));
        mechanic.isActive = obj.optBoolean("is_active", true);
        mechanic.createdAt = safe(obj.optString("created_at", ""));
        mechanic.updatedAt = safe(obj.optString("updated_at", ""));
        return mechanic;
    }

    @NonNull
    private static String safe(@Nullable String value) {
        return value == null || "null".equalsIgnoreCase(value.trim()) ? "" : value.trim();
    }
}
