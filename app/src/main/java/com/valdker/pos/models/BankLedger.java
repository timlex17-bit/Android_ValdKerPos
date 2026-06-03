package com.valdker.pos.models;

import org.json.JSONObject;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class BankLedger {

    private int id;
    private int shopId;
    private String shopName;
    private String shopCode;
    private int bankAccount;
    private String bankAccountName;
    private String transactionType;
    private String direction;
    private String amount;
    private String balanceBefore;
    private String balanceAfter;
    private int referenceOrder;
    private String referenceOrderInvoice;
    private int referencePayment;
    private String description;
    private String createdAt;
    private int createdBy;

    public static BankLedger fromJson(JSONObject obj) {
        BankLedger ledger = new BankLedger();
        if (obj == null) return ledger;

        ledger.id = obj.optInt("id", 0);
        ledger.shopId = obj.optInt("shop_id", 0);
        ledger.shopName = obj.optString("shop_name", "");
        ledger.shopCode = obj.optString("shop_code", "");
        ledger.bankAccount = obj.optInt("bank_account", 0);
        ledger.bankAccountName = obj.optString("bank_account_name", "");
        ledger.transactionType = obj.optString("transaction_type", "");
        ledger.direction = obj.optString("direction", "");
        ledger.amount = obj.optString("amount", "0.00");
        ledger.balanceBefore = obj.optString("balance_before", "0.00");
        ledger.balanceAfter = obj.optString("balance_after", "0.00");
        ledger.referenceOrder = obj.optInt("reference_order", 0);
        ledger.referenceOrderInvoice = obj.optString("reference_order_invoice", "");
        ledger.referencePayment = obj.optInt("reference_payment", 0);
        ledger.description = obj.optString("description", "");
        ledger.createdAt = obj.optString("created_at", "");
        ledger.createdBy = obj.optInt("created_by", 0);

        return ledger;
    }

    public boolean isIn() {
        return "IN".equalsIgnoreCase(direction);
    }

    public boolean isOut() {
        return "OUT".equalsIgnoreCase(direction);
    }

    public String getFormattedAmount() {
        return formatCurrency(amount);
    }

    public String getFormattedBalanceAfter() {
        return formatCurrency(balanceAfter);
    }

    public String getFormattedBalanceBefore() {
        return formatCurrency(balanceBefore);
    }

    public String getDisplayTransactionType() {
        String value = safe(transactionType);
        if (value.isEmpty()) return "-";
        return value.replace("_", " ");
    }

    public String getDisplayDate() {
        String value = safe(createdAt);
        if (value.isEmpty()) return "-";

        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ssXXX"
        };

        for (String pattern : patterns) {
            try {
                SimpleDateFormat input = new SimpleDateFormat(pattern, Locale.US);
                input.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date date = input.parse(value);
                if (date == null) continue;

                SimpleDateFormat output = new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.US);
                return output.format(date);
            } catch (Exception ignored) {
            }
        }

        return value;
    }

    private static String formatCurrency(String raw) {
        try {
            double value = Double.parseDouble(safe(raw).replace(",", ""));
            return NumberFormat.getCurrencyInstance(Locale.US).format(value);
        } catch (Exception e) {
            String value = safe(raw);
            return value.isEmpty() ? "$0.00" : value;
        }
    }

    private static String safe(String value) {
        return value == null || "null".equalsIgnoreCase(value.trim()) ? "" : value.trim();
    }

    public int getId() { return id; }
    public int getShopId() { return shopId; }
    public String getShopName() { return safe(shopName); }
    public String getShopCode() { return safe(shopCode); }
    public int getBankAccount() { return bankAccount; }
    public String getBankAccountName() { return safe(bankAccountName); }
    public String getTransactionType() { return safe(transactionType); }
    public String getDirection() { return safe(direction); }
    public String getAmount() { return safe(amount); }
    public String getBalanceBefore() { return safe(balanceBefore); }
    public String getBalanceAfter() { return safe(balanceAfter); }
    public int getReferenceOrder() { return referenceOrder; }
    public String getReferenceOrderInvoice() { return safe(referenceOrderInvoice); }
    public int getReferencePayment() { return referencePayment; }
    public String getDescription() { return safe(description); }
    public String getCreatedAt() { return safe(createdAt); }
    public int getCreatedBy() { return createdBy; }
}
