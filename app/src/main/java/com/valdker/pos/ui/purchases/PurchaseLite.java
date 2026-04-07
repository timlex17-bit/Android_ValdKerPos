package com.valdker.pos.ui.purchases;

import androidx.annotation.NonNull;

import org.json.JSONObject;

public class PurchaseLite {
    public int id;
    public String purchaseDate;
    public String invoiceId;
    public int supplierId;
    public String supplierName;
    public int totalItems;
    public String totalCost;

    @NonNull
    public static PurchaseLite fromJson(@NonNull JSONObject o) {
        PurchaseLite p = new PurchaseLite();

        p.id = o.optInt("id", 0);
        p.purchaseDate = o.optString("purchase_date", "");
        p.invoiceId = o.optString("invoice_id", "");
        p.supplierId = o.optInt("supplier_id", o.optInt("supplier", 0));
        p.supplierName = o.optString("supplier_name", "—");
        p.totalItems = o.optInt("items_count", o.optInt("total_items", 0));
        p.totalCost = o.optString("total_cost", "0.00");

        if (p.supplierName == null || p.supplierName.trim().isEmpty()) {
            p.supplierName = "—";
        }

        if (p.totalCost == null || p.totalCost.trim().isEmpty()) {
            p.totalCost = "0.00";
        }

        return p;
    }
}