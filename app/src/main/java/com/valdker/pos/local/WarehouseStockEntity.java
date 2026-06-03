package com.valdker.pos.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "warehouse_stocks",
        indices = {
                @Index("id"),
                @Index("shopId"),
                @Index("shopCode"),
                @Index("warehouse"),
                @Index("product"),
                @Index("productUnit")
        }
)
public class WarehouseStockEntity {
    @PrimaryKey
    @NonNull
    public String cacheKey = "";

    public int id = 0;
    public int shopId = 0;
    public String shopName = "";
    public String shopCode = "";
    public String apiBaseUrl = "";
    public int warehouse = 0;
    public String warehouseName = "";
    public String warehouseCode = "";
    public int product = 0;
    public int productUnit = 0;
    public String productName = "";
    public String productCode = "";
    public String productSku = "";
    public boolean productTrackStock = true;
    public double quantity = 0d;
    public double minStock = 0d;
    public double quantityBase = 0d;
    public String baseUnitName = "";
    public String displayStock = "";
    public String displayMinimumStock = "";
    public boolean isLowStock = false;
    public String createdAt = "";
    public String updatedAt = "";
    public String rawJson = "";
    public long lastSyncAt = 0L;
}
