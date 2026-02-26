package com.example.valdker.models;

import org.json.JSONObject;

public class CustomerLite {
    public int id;
    public String name;

    public static CustomerLite fromJson(JSONObject o) {
        CustomerLite c = new CustomerLite();
        c.id = o.optInt("id");

        String n = o.optString("name", "");
        if (n == null || n.trim().isEmpty()) n = o.optString("full_name", "");
        if (n == null || n.trim().isEmpty()) n = o.optString("customer_name", "");
        c.name = n != null ? n : "";

        return c;
    }

    public String bestName() {
        if (name != null && !name.trim().isEmpty()) return name;
        return "Customer #" + id;
    }

    @Override
    public String toString() {
        return bestName();
    }
}