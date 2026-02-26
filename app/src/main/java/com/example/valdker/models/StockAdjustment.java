package com.example.valdker.models;

public class StockAdjustment {
    public int id;

    public int old_stock;
    public int new_stock;

    public String reason;   // LOST, DAMAGE, FOUND,
    public String note;

    public String adjusted_at; // ISO string
    public int product;        // product id
    public int adjusted_by;    // user id

    public int diff() {
        return new_stock - old_stock;
    }
}