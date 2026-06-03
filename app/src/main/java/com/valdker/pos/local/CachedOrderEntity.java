package com.valdker.pos.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "cached_orders",
        indices = {
                @Index("backendId"),
                @Index("shopId"),
                @Index("shopCode"),
                @Index("invoiceNumber"),
                @Index("createdAt")
        }
)
public class CachedOrderEntity {
    @PrimaryKey
    @NonNull
    public String cacheKey = "";

    public int backendId = 0;
    @ColumnInfo(defaultValue = "0")
    public int shopId = 0;
    public String shopCode = "";
    public String apiBaseUrl = "";
    public String invoiceNumber = "";
    public Integer customerId;
    public String customerName = "";
    public double subtotal = 0d;
    public double discount = 0d;
    public double tax = 0d;
    public double total = 0d;
    public String paymentMethod = "";
    public String paymentStatus = "";
    public String orderStatus = "";
    public String orderType = "";
    public String businessType = "";
    public String notes = "";
    public boolean isPaid = true;
    public int itemsCount = 0;
    public String cashierName = "";
    public String createdAt = "";
    public String rawJson = "";
    public long lastSyncAt = 0L;
}
