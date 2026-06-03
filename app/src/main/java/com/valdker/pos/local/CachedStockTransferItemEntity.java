package com.valdker.pos.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "cached_stock_transfer_items",
        indices = {
                @Index("backendId"),
                @Index("transferBackendId"),
                @Index("shopId"),
                @Index("shopCode"),
                @Index("productId")
        }
)
public class CachedStockTransferItemEntity {
    @PrimaryKey
    @NonNull
    public String cacheKey = "";

    public int backendId = 0;
    public int transferBackendId = 0;
    @ColumnInfo(defaultValue = "0")
    public int shopId = 0;
    public String shopCode = "";
    public String apiBaseUrl = "";
    public int productId = 0;
    public int productUnitId = 0;
    public String productName = "";
    public String productCode = "";
    public String productSku = "";
    public double quantity = 0d;
    public double quantityInput = 0d;
    public double quantityBase = 0d;
    public String unitName = "";
    public String note = "";
    public String rawJson = "";
    public long lastSyncAt = 0L;
}
