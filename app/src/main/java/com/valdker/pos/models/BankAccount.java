package com.valdker.pos.models;

import org.json.JSONObject;

public class BankAccount {
    private int id;
    private int shopId;
    private String shopName;
    private String shopCode;
    private String name;
    private String bankName;
    private String accountNumber;
    private String accountHolder;
    private String accountType;
    private String openingBalance;
    private String currentBalance;
    private boolean isActive;
    private String note;
    private String createdAt;

    public BankAccount() {
    }

    public static BankAccount fromJson(JSONObject obj) {
        BankAccount item = new BankAccount();
        item.id = obj.optInt("id", 0);
        item.shopId = obj.optInt("shop_id", 0);
        item.shopName = obj.optString("shop_name", "");
        item.shopCode = obj.optString("shop_code", "");
        item.name = obj.optString("name", "");
        item.bankName = obj.optString("bank_name", "");
        item.accountNumber = obj.optString("account_number", "");
        item.accountHolder = obj.optString("account_holder", "");
        item.accountType = obj.optString("account_type", "BANK");
        item.openingBalance = obj.optString("opening_balance", "0.00");
        item.currentBalance = obj.optString("current_balance", "0.00");
        item.isActive = obj.optBoolean("is_active", true);
        item.note = obj.optString("note", "");
        item.createdAt = obj.optString("created_at", "");
        return item;
    }

    public int getId() {
        return id;
    }

    public int getShopId() {
        return shopId;
    }

    public String getShopName() {
        return shopName;
    }

    public String getShopCode() {
        return shopCode;
    }

    public String getName() {
        return name;
    }

    public String getBankName() {
        return bankName;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getAccountHolder() {
        return accountHolder;
    }

    public String getAccountType() {
        return accountType;
    }

    public String getOpeningBalance() {
        return openingBalance;
    }

    public String getCurrentBalance() {
        return currentBalance;
    }

    public boolean isActive() {
        return isActive;
    }

    public String getNote() {
        return note;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}