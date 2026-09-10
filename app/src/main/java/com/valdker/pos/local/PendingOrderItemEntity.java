package com.valdker.pos.local;

import androidx.annotation.NonNull;

import com.valdker.pos.money.Money;
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
    // Teks desimal, alasannya sama seperti di PendingOrderEntity.
    @NonNull public String price = "0.00";
    @NonNull public String discount = "0.00";
    @NonNull public String total = "0.00";

    @NonNull public Money priceMoney() { return Money.of(price); }
    @NonNull public Money totalMoney() { return Money.of(total); }

    @NonNull
    public String note = "";
}
