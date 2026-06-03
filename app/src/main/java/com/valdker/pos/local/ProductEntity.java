package com.valdker.pos.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "products",
        indices = {
                @Index("barcode"),
                @Index("sku"),
                @Index("categoryId"),
                @Index("itemType"),
                @Index("shopId"),
                @Index("shopCode"),
                @Index("isActive")
        }
)
public class ProductEntity {
    @PrimaryKey
    @NonNull
    public String cacheKey = "";

    @NonNull
    public String id = "";

    public String name = "";
    public int shopId = 0;
    public String shopCode = "";
    public String apiBaseUrl = "";
    public String sku = "";
    public String barcode = "";
    public double price = 0d;
    public int stock = 0;
    public String imageUrl = "";
    public String categoryId = "";
    public String categoryName = "";
    public String description = "";
    public String buyPrice = "";
    public String sellPrice = "";
    public String weight = "";
    public String unitId = "";
    public String unitName = "";
    public String supplierId = "";
    public String supplierName = "";
    public String itemType = "";
    public boolean isActive = true;
    public boolean trackStock = true;
    public String productUnitsJson = "[]";
    public String rawJson = "";
    public long lastSyncAt = 0L;
}
