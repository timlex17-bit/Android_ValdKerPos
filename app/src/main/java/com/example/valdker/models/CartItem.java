package com.example.valdker.models;

import androidx.annotation.NonNull;

public class CartItem {
    public String productId;
    public String name;
    public double price;
    public String imageUrl;
    public int qty;

    // ✅ NEW: order type per item
    // Allowed: "DINE_IN", "TAKE_OUT", "DELIVERY"
    public String orderType = "TAKE_OUT";

    public CartItem() {}

    public CartItem(@NonNull String productId, @NonNull String name, double price, String imageUrl, int qty) {
        this.productId = productId;
        this.name = name;
        this.price = price;
        this.imageUrl = imageUrl;
        this.qty = qty;
        this.orderType = "TAKE_OUT"; // default
    }

    // ✅ Optional convenience constructor (if you want to set initial orderType)
    public CartItem(@NonNull String productId, @NonNull String name, double price, String imageUrl, int qty, @NonNull String orderType) {
        this.productId = productId;
        this.name = name;
        this.price = price;
        this.imageUrl = imageUrl;
        this.qty = qty;
        this.orderType = orderType;
    }
}
