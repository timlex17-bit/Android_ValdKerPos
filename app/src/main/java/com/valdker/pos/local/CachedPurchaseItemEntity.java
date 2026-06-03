package com.valdker.pos.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "cached_purchase_items",
        indices = {
                @Index("backendId"),
                @Index("purchaseBackendId"),
                @Index("shopId"),
                @Index("shopCode"),
                @Index("productId")
        }
)
public class CachedPurchaseItemEntity {
    @PrimaryKey
    @NonNull
    public String cacheKey = "";

    public int backendId = 0;
    public int purchaseBackendId = 0;
    @ColumnInfo(defaultValue = "0")
    public int shopId = 0;
    public String shopCode = "";
    public String apiBaseUrl = "";
    public int productId = 0;
    public String productName = "";
    public String unitName = "";
    public double quantity = 0d;
    public String price = "0.00";
    public String cost = "0.00";
    public String total = "0.00";
    public String rawJson = "";
    public long lastSyncAt = 0L;
}
