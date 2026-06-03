package com.valdker.pos.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "cached_inventory_counts",
        indices = {
                @Index("backendId"),
                @Index("shopId"),
                @Index("shopCode"),
                @Index("warehouseId"),
                @Index("status"),
                @Index("countedAt")
        }
)
public class CachedInventoryCountEntity {
    @PrimaryKey
    @NonNull
    public String cacheKey = "";

    public int backendId = 0;
    @ColumnInfo(defaultValue = "0")
    public int shopId = 0;
    public String shopCode = "";
    public String apiBaseUrl = "";
    public int warehouseId = 0;
    public String warehouseName = "";
    public String title = "";
    public String status = "";
    public String note = "";
    public int countedBy = 0;
    public String countedByUsername = "";
    public String countedByName = "";
    public String countedAt = "";
    public String createdAt = "";
    public String rawJson = "";
    public long lastSyncAt = 0L;
}
