package com.valdker.pos.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "cached_product_return_items",
        indices = {
                @Index("backendId"),
                @Index("returnBackendId"),
                @Index("shopId"),
                @Index("shopCode"),
                @Index("productId"),
                @Index("itemType")
        }
)
public class CachedProductReturnItemEntity {
    @PrimaryKey
    @NonNull
    public String cacheKey = "";

    public int backendId = 0;
    public int returnBackendId = 0;
    @ColumnInfo(defaultValue = "0")
    public int shopId = 0;
    public String shopCode = "";
    public String apiBaseUrl = "";
    public int productId = 0;
    public String productName = "";
    public String productCode = "";
    public String productSku = "";
    public String itemType = "";
    public double quantity = 0d;
    public String price = "0.00";
    public String total = "0.00";
    public String reason = "";
    public String rawJson = "";
    public long lastSyncAt = 0L;
}
