package com.example.valdker.ui.checkout;

import androidx.annotation.Nullable;

public class CheckoutPaymentPayload {
    public int paymentMethodId;
    @Nullable
    public Integer bankAccountId;
    public double amount;
    public String referenceNumber;
    public String note;

    public CheckoutPaymentPayload(int paymentMethodId,
                                  @Nullable Integer bankAccountId,
                                  double amount,
                                  String referenceNumber,
                                  String note) {
        this.paymentMethodId = paymentMethodId;
        this.bankAccountId = bankAccountId;
        this.amount = amount;
        this.referenceNumber = referenceNumber;
        this.note = note;
    }
}