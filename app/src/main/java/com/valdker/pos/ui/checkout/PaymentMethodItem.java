package com.valdker.pos.ui.checkout;

public class PaymentMethodItem {
    public int id;
    public String name;
    public String code;
    public String payment_type;
    public boolean requires_bank_account;
    public boolean is_active;
    public String note;

    @Override
    public String toString() {
        return name != null ? name : "";
    }
}