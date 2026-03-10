package com.example.valdker.models;

import androidx.annotation.NonNull;

public class CartItem {

    public int productId;

    public String name;
    public double price;
    public String imageUrl;
    public int qty;

    // Allowed values: "DINE_IN", "TAKE_OUT", "DELIVERY"
    public String orderType = "";

    public CartItem() {
    }

    public CartItem(int productId,
                    @NonNull String name,
                    double price,
                    String imageUrl,
                    int qty) {

        this.productId = productId;
        this.name = name;
        this.price = price;
        this.imageUrl = imageUrl;
        this.qty = qty;
        this.orderType = "";
    }

    public CartItem(int productId,
                    @NonNull String name,
                    double price,
                    String imageUrl,
                    int qty,
                    @NonNull String orderType) {

        this.productId = productId;
        this.name = name;
        this.price = price;
        this.imageUrl = imageUrl;
        this.qty = qty;
        this.orderType = orderType;
    }
}