package com.valdker.pos.local;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "pending_orders",
        indices = {
                @Index("syncStatus"),
                @Index("businessType"),
                @Index("shopId"),
                @Index("shopCode"),
                @Index("clientOrderId"),
                @Index("createdAt")
        }
)
public class PendingOrderEntity {
    @PrimaryKey
    @NonNull
    public String localOrderId = "";

    @NonNull
    public String rawPayloadJson = "";

    @NonNull
    @ColumnInfo(defaultValue = "''")
    public String clientOrderId = "";

    @NonNull
    public String businessType = "";

    public int shopId = 0;

    @NonNull
    public String shopCode = "";

    @NonNull
    public String shopName = "";

    @NonNull
    public String apiBaseUrl = "";

    @NonNull
    public String createdByUserId = "";

    @NonNull
    public String createdByUsername = "";

    @Nullable
    public Integer customerId;
    @Nullable
    public Integer paymentMethodId;
    @Nullable
    public Integer bankAccountId;

    public double subtotal = 0d;
    public double discount = 0d;
    public double tax = 0d;
    public double total = 0d;
    public double paidAmount = 0d;
    public double changeAmount = 0d;

    @NonNull
    public String orderType = "";

    @NonNull
    public String note = "";

    public long createdAt = 0L;
    public long updatedAt = 0L;

    @NonNull
    public String syncStatus = "PENDING_SYNC";

    public int syncAttemptCount = 0;

    @NonNull
    public String lastSyncError = "";
}
