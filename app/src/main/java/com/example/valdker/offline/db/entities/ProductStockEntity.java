package com.example.valdker.offline.db.entities;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "product_stock")
public class ProductStockEntity {

    @PrimaryKey
    @NonNull
    public String product_id;

    public String product_name;

    public double stock_qty;

    public String updated_at_iso;

    public ProductStockEntity(@NonNull String product_id) {
        this.product_id = product_id;
    }
}