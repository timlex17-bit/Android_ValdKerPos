package com.valdker.pos.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "pending_order_items",
        foreignKeys = @ForeignKey(
                entity = PendingOrderEntity.class,
                parentColumns = "localOrderId",
                childColumns = "localOrderId",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {
                @Index("localOrderId"),
                @Index("productId"),
                @Index("itemType")
        }
)
public class PendingOrderItemEntity {
    @PrimaryKey(autoGenerate = true)
    public long id = 0L;

    @NonNull
    public String localOrderId = "";

    public int productId = 0;

    @NonNull
    public String itemType = "product";

    @NonNull
    public String name = "";

    @NonNull
    public String sku = "";

    @NonNull
    public String barcode = "";

    public int quantity = 0;
    public double price = 0d;
    public double discount = 0d;
    public double total = 0d;

    @NonNull
    public String note = "";
}
