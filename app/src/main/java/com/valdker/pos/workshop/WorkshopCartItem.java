package com.valdker.pos.workshop;

import androidx.annotation.NonNull;

import com.valdker.pos.models.CartItem;

public class WorkshopCartItem {

    public static final String TYPE_SERVICE = "SERVICE";
    public static final String TYPE_PART = "PART";
    public static final String TYPE_PRODUCT = "PRODUCT";

    private final String cartKey;
    private final int productId;
    private final int shopId;
    private final String name;
    private final double unitPrice;
    private final int quantity;
    private final String itemType;
    private final String imageUrl;

    public WorkshopCartItem(@NonNull String cartKey,
                            int productId,
                            int shopId,
                            @NonNull String name,
                            double unitPrice,
                            int quantity,
                            @NonNull String itemType,
                            String imageUrl) {
        this.cartKey = cartKey;
        this.productId = productId;
        this.shopId = shopId;
        this.name = name;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.itemType = normalizeItemType(itemType);
        this.imageUrl = imageUrl;
    }

    @NonNull
    public static WorkshopCartItem fromCartItem(@NonNull CartItem item) {
        String normalizedType = normalizeItemType(item.itemType);
        String key = item.cartKey == null || item.cartKey.trim().isEmpty()
                ? CartItem.buildCartKey(item.productId, normalizedType)
                : item.cartKey;

        return new WorkshopCartItem(
                key,
                item.productId,
                item.shopId,
                item.name != null ? item.name : "",
                item.price,
                item.qty,
                normalizedType,
                item.imageUrl
        );
    }

    @NonNull
    public static String normalizeItemType(String type) {
        if (type == null) return TYPE_PRODUCT;

        String value = type.trim().toUpperCase();

        switch (value) {
            case "SERVICE":
                return TYPE_SERVICE;
            case "SPAREPART":
            case "PART":
                return TYPE_PART;
            case "PRODUCT":
            default:
                return TYPE_PRODUCT;
        }
    }

    @NonNull
    public String getCartKey() {
        return cartKey;
    }

    public int getProductId() {
        return productId;
    }

    public int getShopId() {
        return shopId;
    }

    @NonNull
    public String getName() {
        return name;
    }

    public double getUnitPrice() {
        return unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    @NonNull
    public String getItemType() {
        return itemType;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public double getLineTotal() {
        return unitPrice * quantity;
    }

    public double getPrice() {
        return unitPrice;
    }

    public boolean isService() {
        return TYPE_SERVICE.equals(itemType);
    }

    public boolean isPart() {
        return TYPE_PART.equals(itemType);
    }

    public boolean isProduct() {
        return TYPE_PRODUCT.equals(itemType);
    }
}