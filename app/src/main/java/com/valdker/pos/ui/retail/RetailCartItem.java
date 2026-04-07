package com.valdker.pos.ui.retail;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class RetailCartItem {

    public long productId;
    public String name = "";
    public String sku = "";
    public String barcode = "";
    public String imageUrl = "";
    public double unitPrice = 0d;
    public int qty = 1;
    public double stock = 0d;
    public String note = "";

    public RetailCartItem() {
    }

    @NonNull
    public static RetailCartItem fromProduct(@NonNull RetailProductItem product) {
        RetailCartItem item = new RetailCartItem();
        item.productId = product.id;
        item.name = safe(product.name);
        item.sku = safe(product.sku);
        item.barcode = safe(product.barcode);
        item.imageUrl = safe(product.imageUrl);
        item.unitPrice = product.price;
        item.qty = 1;
        item.stock = product.stock;
        return item;
    }

    public double getSubtotal() {
        return unitPrice * qty;
    }

    public void increaseQty() {
        qty++;
    }

    public void decreaseQty() {
        if (qty > 1) qty--;
    }

    public void setQty(int value) {
        qty = Math.max(1, value);
    }

    @NonNull
    private static String safe(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}