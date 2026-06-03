package com.valdker.pos.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "cached_stock_transfers",
        indices = {
                @Index("backendId"),
                @Index("shopId"),
                @Index("shopCode"),
                @Index("referenceNo"),
                @Index("status"),
                @Index("createdAt")
        }
)
public class CachedStockTransferEntity {
    @PrimaryKey
    @NonNull
    public String cacheKey = "";

    public int backendId = 0;
    @ColumnInfo(defaultValue = "0")
    public int shopId = 0;
    public String shopCode = "";
    public String apiBaseUrl = "";
    public String shopName = "";
    public String referenceNo = "";
    public int sourceWarehouseId = 0;
    public String sourceWarehouseName = "";
    public String sourceWarehouseCode = "";
    public int destinationWarehouseId = 0;
    public String destinationWarehouseName = "";
    public String destinationWarehouseCode = "";
    public String status = "";
    public String note = "";
    public int createdBy = 0;
    public String createdByName = "";
    public String completedByName = "";
    public String cancelledByName = "";
    public String createdAt = "";
    public String updatedAt = "";
    public String completedAt = "";
    public String cancelledAt = "";
    public String rawJson = "";
    public long lastSyncAt = 0L;
}
