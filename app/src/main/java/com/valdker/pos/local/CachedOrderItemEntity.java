package com.valdker.pos.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "cached_order_items",
        indices = {
                @Index("orderBackendId"),
                @Index("shopId"),
                @Index("shopCode"),
                @Index("productId"),
                @Index("itemType")
        }
)
public class CachedOrderItemEntity {
    @PrimaryKey
    @NonNull
    public String cacheKey = "";

    public int backendId = 0;
    public int orderBackendId = 0;
    @ColumnInfo(defaultValue = "0")
    public int shopId = 0;
    public String shopCode = "";
    public String apiBaseUrl = "";
    public int productId = 0;
    public String itemType = "";
    public String name = "";
    public String sku = "";
    public String barcode = "";
    public int quantity = 0;
    public double price = 0d;
    public double discount = 0d;
    public double total = 0d;
    public String note = "";
    public String rawJson = "";
    public long lastSyncAt = 0L;
}
