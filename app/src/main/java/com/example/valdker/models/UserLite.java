package com.example.valdker.models;

import org.json.JSONObject;

public class UserLite {
    public int id;
    public String username;
    public String displayName;

    public static UserLite fromJson(JSONObject o) {
        UserLite u = new UserLite();
        u.id = o.optInt("id");

        // ✅ try multiple keys
        String un = o.optString("username", "");
        if (un == null || un.trim().isEmpty()) un = o.optString("email", "");
        u.username = un != null ? un : "";

        String dn = o.optString("display_name", "");
        if (dn == null || dn.trim().isEmpty()) dn = o.optString("displayName", ""); // sometimes camelCase
        if (dn == null || dn.trim().isEmpty()) dn = o.optString("full_name", "");
        u.displayName = dn != null ? dn : "";

        return u;
    }

    public String bestName() {
        if (displayName != null && !displayName.trim().isEmpty()) return displayName;
        if (username != null && !username.trim().isEmpty()) return username;
        return "User #" + id;
    }
}