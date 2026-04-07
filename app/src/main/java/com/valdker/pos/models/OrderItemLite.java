package com.valdker.pos.models;

import org.json.JSONObject;

public class OrderItemLite {
    public int productId;
    public int quantity;
    public String price; // "1.75"
    public String orderType;

    // UI fields (filled after product lookup)
    public String productName = "";
    public String productCode = "";
    public String productSku = "";

    public static OrderItemLite fromJson(JSONObject o) {
        OrderItemLite it = new OrderItemLite();
        it.productId = o.optInt("product");
        it.quantity = o.optInt("quantity", 0);
        it.price = o.optString("price", "");
        it.orderType = o.optString("order_type", "");
        return it;
    }

    public String bestLabel() {
        String n = (productName != null && !productName.trim().isEmpty())
                ? productName
                : ("Product #" + productId);

        String extra = "";
        if (productCode != null && !productCode.trim().isEmpty()) extra = productCode;
        else if (productSku != null && !productSku.trim().isEmpty()) extra = productSku;

        if (!extra.isEmpty()) n = n + " (" + extra + ")";
        return n + " • qty " + quantity;
    }

    @Override
    public String toString() {
        return bestLabel();
    }
}