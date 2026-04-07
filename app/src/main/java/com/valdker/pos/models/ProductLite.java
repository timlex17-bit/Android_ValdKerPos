package com.valdker.pos.models;

import org.json.JSONObject;

public class ProductLite {
    public int id;
    public String name;
    public String code;
    public String sku;
    public String sellPrice; // String from API, e.g. "1.50"

    public static ProductLite fromJson(JSONObject o) {
        ProductLite p = new ProductLite();
        p.id = o.optInt("id");
        p.name = o.optString("name", "");
        p.code = o.optString("code", "");
        p.sku = o.optString("sku", "");
        p.sellPrice = o.optString("sell_price", "");
        return p;
    }

    @Override
    public String toString() {
        // Show product name + code/sku to make the label more explicit.
        String label = (name != null && !name.trim().isEmpty())
                ? name.trim()
                : ("Product #" + id);

        String extra = "";
        if (code != null && !code.trim().isEmpty()) {
            extra = code.trim();
        } else if (sku != null && !sku.trim().isEmpty()) {
            extra = sku.trim();
        }

        if (!extra.isEmpty()) {
            label = label + " (" + extra + ")";
        }
        return label;
    }
}