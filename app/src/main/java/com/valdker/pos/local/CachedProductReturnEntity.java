package com.valdker.pos.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "cached_product_returns",
        indices = {
                @Index("backendId"),
                @Index("shopId"),
                @Index("shopCode"),
                @Index("returnNumber"),
                @Index("orderId"),
                @Index("customerId"),
                @Index("returnedAt")
        }
)
public class CachedProductReturnEntity {
    @PrimaryKey
    @NonNull
    public String cacheKey = "";

    public int backendId = 0;
    @ColumnInfo(defaultValue = "0")
    public int shopId = 0;
    public String shopCode = "";
    public String apiBaseUrl = "";
    public String returnNumber = "";
    public Integer orderId;
    public String orderInvoice = "";
    public int customerId = 0;
    public String customerName = "";
    public String totalAmount = "0.00";
    public String reason = "";
    public String note = "";
    public String status = "";
    public String returnedAt = "";
    public String createdAt = "";
    public int createdBy = 0;
    public String createdByName = "";
    public String rawJson = "";
    public long lastSyncAt = 0L;
}
