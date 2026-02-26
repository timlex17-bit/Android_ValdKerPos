package com.example.valdker.models;

import androidx.annotation.Nullable;

public class OrderItem {
    private final int productId;
    private final int quantity;
    private final double price;
    @Nullable
    private final Integer weightUnitId;

    public OrderItem(int productId, int quantity, double price, @Nullable Integer weightUnitId) {
        this.productId = productId;
        this.quantity = quantity;
        this.price = price;
        this.weightUnitId = weightUnitId;
    }

    public int getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public double getPrice() {
        return price;
    }

    @Nullable
    public Integer getWeightUnitId() {
        return weightUnitId;
    }

    public double getLineTotal() {
        return price * quantity;
    }
}
