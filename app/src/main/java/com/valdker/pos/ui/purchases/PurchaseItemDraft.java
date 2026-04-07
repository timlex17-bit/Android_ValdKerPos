package com.valdker.pos.ui.purchases;

import androidx.annotation.NonNull;
public class PurchaseItemDraft {
    public final int productId;
    @NonNull public final String productName;

    public int qty;

    @NonNull public final String costPrice;

    // expired optional (can be "")
    @NonNull public final String expiredDate;

    public PurchaseItemDraft(int productId,
             @NonNull String productName,
             int qty,
             @NonNull String costPrice,
             @NonNull String expiredDate) {
        this.productId = productId;
        this.productName = productName;
        this.qty = qty;
        this.costPrice = costPrice;
        this.expiredDate = expiredDate != null ? expiredDate : "";
    }
}