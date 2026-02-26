package com.example.valdker.models;

import org.json.JSONObject;

public class ProductLite {
    public int id;
    public String name;
    public String code;
    public String sku;
    public String sellPrice; // string from API e.g. "1.50"

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
        // tampilkan nama produk + code/sku biar jelas
        String label = (name != null && !name.trim().isEmpty()) ? name : ("Product #" + id);

        String extra = "";
        if (code != null && !code.trim().isEmpty()) extra = code;
        else if (sku != null && !sku.trim().isEmpty()) extra = sku;

        if (!extra.isEmpty()) label = label + " (" + extra + ")";
        return label;
    }
}