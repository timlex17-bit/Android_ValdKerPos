package com.valdker.pos.models;

import androidx.annotation.NonNull;

public class CartItem {

    public static final String ORDER_TYPE_DINE_IN = "DINE_IN";
    public static final String ORDER_TYPE_TAKE_OUT = "TAKE_OUT";
    public static final String ORDER_TYPE_DELIVERY = "DELIVERY";

    public static final String ITEM_TYPE_PRODUCT = "PRODUCT";
    public static final String ITEM_TYPE_SERVICE = "SERVICE";
    public static final String ITEM_TYPE_PART = "PART";

    public int productId;
    public int shopId;

    public String cartKey = "";
    public String name;
    public double price;
    public String imageUrl;
    public int qty;

    public String orderType = "";
    public String itemType = ITEM_TYPE_PRODUCT;

    public CartItem() {
    }

    public CartItem(int productId,
                    int shopId,
                    @NonNull String name,
                    double price,
                    String imageUrl,
                    int qty) {
        this(productId, shopId, name, price, imageUrl, qty, "", ITEM_TYPE_PRODUCT);
    }

    public CartItem(int productId,
                    int shopId,
                    @NonNull String name,
                    double price,
                    String imageUrl,
                    int qty,
                    @NonNull String orderType) {
        this(productId, shopId, name, price, imageUrl, qty, orderType, ITEM_TYPE_PRODUCT);
    }

    public CartItem(int productId,
                    int shopId,
                    @NonNull String name,
                    double price,
                    String imageUrl,
                    int qty,
                    @NonNull String orderType,
                    @NonNull String itemType) {

        this.productId = productId;
        this.shopId = shopId;
        this.name = name;
        this.price = price;
        this.imageUrl = imageUrl;
        this.qty = qty;
        this.orderType = orderType;
        this.itemType = normalizeItemType(itemType);
        this.cartKey = buildCartKey(this.productId, this.itemType);
    }

    public double getLineTotal() {
        return price * qty;
    }

    @NonNull
    public static String normalizeItemType(String raw) {
        if (raw == null) return ITEM_TYPE_PRODUCT;

        String value = raw.trim().toUpperCase();

        switch (value) {
            case "SERVICE":
                return ITEM_TYPE_SERVICE;
            case "SPAREPART":
            case "PART":
                return ITEM_TYPE_PART;
            case "PRODUCT":
            default:
                return ITEM_TYPE_PRODUCT;
        }
    }

    @NonNull
    public static String buildCartKey(int productId, String itemType) {
        return normalizeItemType(itemType) + ":" + productId;
    }

    public void refreshCartKey() {
        this.cartKey = buildCartKey(this.productId, this.itemType);
    }
}