package com.valdker.pos.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "cached_payments",
        indices = {
                @Index("backendId"),
                @Index("shopId"),
                @Index("shopCode"),
                @Index("orderBackendId"),
                @Index("createdAt")
        }
)
public class CachedPaymentEntity {
    @PrimaryKey
    @NonNull
    public String cacheKey = "";

    public int backendId = 0;
    @ColumnInfo(defaultValue = "0")
    public int shopId = 0;
    public String shopCode = "";
    public String apiBaseUrl = "";
    public int orderBackendId = 0;
    public String invoiceNumber = "";
    public int paymentMethodId = 0;
    public String paymentMethod = "";
    public int bankAccountId = 0;
    public String bankAccountName = "";
    public double amount = 0d;
    public String createdAt = "";
    public String rawJson = "";
    public long lastSyncAt = 0L;
}
