package com.valdker.pos.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "cached_stock_movements",
        indices = {
                @Index("backendId"),
                @Index("shopId"),
                @Index("shopCode"),
                @Index("productId"),
                @Index("movementType"),
                @Index("createdAt")
        }
)
public class CachedStockMovementEntity {
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
    public String productCode = "";
    public String productSku = "";
    public int warehouseId = 0;
    public String warehouseName = "";
    public String movementType = "";
    public int quantity = 0;
    public int beforeStock = 0;
    public int afterStock = 0;
    public String referenceType = "";
    public int referenceId = 0;
    public String note = "";
    public String createdAt = "";
    public int createdBy = 0;
    public String rawJson = "";
    public long lastSyncAt = 0L;
}
