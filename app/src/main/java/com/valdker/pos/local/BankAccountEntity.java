package com.valdker.pos.local;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.ColumnInfo;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(
        tableName = "bank_accounts",
        indices = {
                @Index("bankName"),
                @Index("shopId"),
                @Index("shopCode"),
                @Index("isActive")
        }
)
public class BankAccountEntity {
    @PrimaryKey
    @NonNull
    public String cacheKey = "";

    public int id = 0;

    @ColumnInfo(defaultValue = "0")
    public int shopId = 0;
    public String shopName = "";
    public String shopCode = "";
    public String apiBaseUrl = "";
    public String name = "";
    public String bankName = "";
    public String accountNumber = "";
    public String accountHolder = "";
    public String accountType = "";
    public String openingBalance = "0.00";
    public String currentBalance = "0.00";
    public String note = "";
    public boolean isActive = true;
    public String rawJson = "";
    public long lastSyncAt = 0L;
}
