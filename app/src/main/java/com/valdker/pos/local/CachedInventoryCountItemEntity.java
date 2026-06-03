package com.valdker.pos.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "cached_inventory_count_items",
        indices = {
                @Index("backendId"),
                @Index("inventoryCountBackendId"),
                @Index("shopId"),
                @Index("shopCode"),
                @Index("productId")
        }
)
public class CachedInventoryCountItemEntity {
    @PrimaryKey
    @NonNull
    public String cacheKey = "";

    public int backendId = 0;
    public int inventoryCountBackendId = 0;
    @ColumnInfo(defaultValue = "0")
    public int shopId = 0;
    public String shopCode = "";
    public String apiBaseUrl = "";
    public int productId = 0;
    public String productName = "";
    public int systemStock = 0;
    public int countedStock = 0;
    public int difference = 0;
    public String costPrice = "";
    public String rawJson = "";
    public long lastSyncAt = 0L;
}
