package com.valdker.pos.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "cached_stock_adjustments",
        indices = {
                @Index("backendId"),
                @Index("shopId"),
                @Index("shopCode"),
                @Index("productId"),
                @Index("adjustedAt")
        }
)
public class CachedStockAdjustmentEntity {
    @PrimaryKey
    @NonNull
    public String cacheKey = "";

    public int backendId = 0;
    @ColumnInfo(defaultValue = "0")
    public int shopId = 0;
    public String shopCode = "";
    public String apiBaseUrl = "";
    public int productId = 0;
    public String productName = "";
    public int oldStock = 0;
    public int newStock = 0;
    public String reason = "";
    public String note = "";
    public int adjustedBy = 0;
    public String adjustedByName = "";
    public String adjustedAt = "";
    public String rawJson = "";
    public long lastSyncAt = 0L;
}
