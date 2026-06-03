package com.valdker.pos.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "cached_purchases",
        indices = {
                @Index("backendId"),
                @Index("shopId"),
                @Index("shopCode"),
                @Index("invoiceNumber"),
                @Index("supplierId"),
                @Index("purchaseDate"),
                @Index("createdAt")
        }
)
public class CachedPurchaseEntity {
    @PrimaryKey
    @NonNull
    public String cacheKey = "";

    public int backendId = 0;
    @ColumnInfo(defaultValue = "0")
    public int shopId = 0;
    public String shopCode = "";
    public String apiBaseUrl = "";
    public String invoiceNumber = "";
    public int supplierId = 0;
    public String supplierName = "";
    public int totalItems = 0;
    public String totalAmount = "0.00";
    public String paidAmount = "0.00";
    public String status = "";
    public String purchaseDate = "";
    public String createdAt = "";
    public String note = "";
    public int createdBy = 0;
    public String createdByName = "";
    public String rawJson = "";
    public long lastSyncAt = 0L;
}
