package com.valdker.pos.local;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.ColumnInfo;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(
        tableName = "payment_methods",
        indices = {
                @Index("code"),
                @Index("shopId"),
                @Index("shopCode"),
                @Index("isActive")
        }
)
public class PaymentMethodEntity {
    @PrimaryKey
    @NonNull
    public String cacheKey = "";

    public int id = 0;

    @ColumnInfo(defaultValue = "0")
    public int shopId = 0;
    public String shopCode = "";
    public String apiBaseUrl = "";
    public String name = "";
    public String code = "";
    public String paymentType = "";
    public boolean requiresBankAccount = false;
    public boolean isActive = true;
    public String note = "";
    public String rawJson = "";
    public long lastSyncAt = 0L;
}
