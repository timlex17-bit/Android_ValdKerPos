package com.valdker.pos.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "cached_bank_ledgers",
        indices = {
                @Index("backendId"),
                @Index("shopId"),
                @Index("shopCode"),
                @Index("bankAccount"),
                @Index("createdAt")
        }
)
public class CachedBankLedgerEntity {
    @PrimaryKey
    @NonNull
    public String cacheKey = "";

    public int backendId = 0;
    @ColumnInfo(defaultValue = "0")
    public int shopId = 0;
    public String shopCode = "";
    public String apiBaseUrl = "";
    public String shopName = "";
    public int bankAccount = 0;
    public String bankAccountName = "";
    public String transactionType = "";
    public String direction = "";
    public String amount = "0.00";
    public String balanceBefore = "0.00";
    public String balanceAfter = "0.00";
    public int referenceOrder = 0;
    public String referenceOrderInvoice = "";
    public int referencePayment = 0;
    public String description = "";
    public String createdAt = "";
    public int createdBy = 0;
    public String rawJson = "";
    public long lastSyncAt = 0L;
}
