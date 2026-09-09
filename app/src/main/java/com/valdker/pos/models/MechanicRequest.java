package com.valdker.pos.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

public class MechanicRequest {
    @NonNull public final String name;
    @NonNull public final String phone;
    @NonNull public final String specialty;
    public final boolean isActive;

    public MechanicRequest(@NonNull String name,
                           @Nullable String phone,
                           @Nullable String specialty,
                           boolean isActive) {
        this.name = name.trim();
        this.phone = phone != null ? phone.trim() : "";
        this.specialty = specialty != null ? specialty.trim() : "";
        this.isActive = isActive;
    }

    @NonNull
    public JSONObject toJson() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("name", name);
            obj.put("phone", phone);
            obj.put("specialty", specialty);
            obj.put("is_active", isActive);
        } catch (Exception ignored) {
        }
        return obj;
    }
}
