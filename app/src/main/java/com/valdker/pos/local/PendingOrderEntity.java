package com.valdker.pos.local;

import androidx.annotation.NonNull;

import com.valdker.pos.money.Money;
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

    // Uang disimpan sebagai teks desimal ("12.35"), bukan REAL. Ini order yang
    // BELUM tersinkron; menyimpannya sebagai biner mengambang berarti angka
    // pada struk cetak ulang bisa berbeda dari yang dilihat kasir.
    @NonNull public String subtotal = "0.00";
    @NonNull public String discount = "0.00";
    @NonNull public String tax = "0.00";
    @NonNull public String total = "0.00";
    @NonNull public String paidAmount = "0.00";
    @NonNull public String changeAmount = "0.00";

    @NonNull public Money subtotalMoney() { return Money.of(subtotal); }
    @NonNull public Money discountMoney() { return Money.of(discount); }
    @NonNull public Money taxMoney() { return Money.of(tax); }
    @NonNull public Money totalMoney() { return Money.of(total); }
    @NonNull public Money paidAmountMoney() { return Money.of(paidAmount); }
    @NonNull public Money changeAmountMoney() { return Money.of(changeAmount); }

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
