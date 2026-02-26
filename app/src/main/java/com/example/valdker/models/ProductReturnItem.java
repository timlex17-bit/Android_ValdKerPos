package com.example.valdker.models;

import org.json.JSONObject;

public class ProductReturnItem {
    public int id;
    public ProductLite product;      // nested
    public double quantity;
    public String unitPrice;         // string "1.50"

    public static ProductReturnItem fromJson(JSONObject o) {
        ProductReturnItem it = new ProductReturnItem();
        it.id = o.optInt("id");

        JSONObject p = o.optJSONObject("product");
        if (p != null) it.product = ProductLite.fromJson(p);

        it.quantity = o.optDouble("quantity", 0);
        it.unitPrice = o.optString("unit_price", "0");

        return it;
    }

    public double unitPriceAsDouble() {
        try { return Double.parseDouble(unitPrice); }
        catch (Exception e) { return 0; }
    }

    public double lineTotal() {
        return quantity * unitPriceAsDouble();
    }
}