package com.example.valdker.models;

import androidx.annotation.NonNull;

import org.json.JSONObject;

public class OrderLite {
    public int id;
    public String invoiceNumber;

    public static OrderLite fromJson(JSONObject o) {
        OrderLite x = new OrderLite();
        x.id = o.optInt("id");
        x.invoiceNumber = o.optString("invoice_number", "");
        if (x.invoiceNumber == null || x.invoiceNumber.trim().isEmpty()) {
            x.invoiceNumber = o.optString("order_invoice_number", "");
        }
        return x;
    }

    @NonNull
    @Override
    public String toString() {
        if (invoiceNumber != null && !invoiceNumber.trim().isEmpty()) return invoiceNumber;
        return "Order #" + id;
    }
}