package com.valdker.pos.models;

public class StockMovement {
    public int id;

    public String created_at;       // ISO
    public String movement_type;    // SALE, PURCHASE, ADJUSTMENT, RETURN, etc
    public int quantity_delta;      // -10, +5, etc

    public int before_stock;
    public int after_stock;

    public String note;

    public String ref_model;        // "Order"
    public int ref_id;              // 22

    public int product;
    public String product_name;
    public String product_code;
    public String product_sku;

    public int created_by;

    public String refLabel() {
        if (ref_model == null || ref_model.trim().isEmpty() || ref_id == 0) return "-";
        return ref_model + " #" + ref_id;
    }
}