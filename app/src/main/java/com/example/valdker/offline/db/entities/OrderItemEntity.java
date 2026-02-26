package com.example.valdker.offline.db.entities;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "order_items",
        indices = {
                @Index(value = {"order_id"}),
                @Index(value = {"product_id"})
        }
)
public class OrderItemEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long order_id; // FK logical (kita set manual)

    public String product_id; // keep String for safety
    public String product_name;

    public int quantity;
    public double unit_price;
    public double line_total;

    public String order_type; // per item (DINE_IN / TAKE_OUT / DELIVERY)

    public OrderItemEntity(long order_id) {
        this.order_id = order_id;
    }
}