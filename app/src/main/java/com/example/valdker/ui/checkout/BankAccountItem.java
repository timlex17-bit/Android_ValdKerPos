package com.example.valdker.ui.checkout;

public class BankAccountItem {
    public int id;
    public String name;
    public String bank_name;
    public String account_number;
    public String account_holder;
    public String account_type;
    public String current_balance;
    public boolean is_active;

    @Override
    public String toString() {
        String bank = bank_name != null ? bank_name : "";
        String nm = name != null ? name : "";
        return bank + " - " + nm;
    }
}