package com.valdker.pos.drafts;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.valdker.pos.models.CartItem;

public final class PosDraftMapper {

    private PosDraftMapper() {
    }

    @Nullable
    public static CartItem toCartItem(@Nullable PosDraftItemEntity item) {
        if (item == null || item.quantity <= 0) return null;

        int productId = parseInt(item.productId);
        if (productId <= 0) return null;

        CartItem cartItem = new CartItem(
                productId,
                item.shopId,
                safe(item.productName),
                item.price,
                item.imageUrl != null ? item.imageUrl : "",
                Math.max(1, item.quantity),
                "",
                item.itemType
        );
        cartItem.refreshCartKey();
        return cartItem;
    }

    @Nullable
    public static PosDraftItemEntity fromCartItem(long draftId,
                                                  @Nullable CartItem item,
                                                  long timestamp) {
        if (draftId <= 0L || item == null || item.productId <= 0 || item.qty <= 0) return null;

        PosDraftItemEntity entity = new PosDraftItemEntity();
        entity.draftId = draftId;
        entity.productId = String.valueOf(item.productId);
        entity.productName = safe(item.name);
        entity.quantity = Math.max(1, item.qty);
        entity.price = item.price;
        entity.subtotal = item.price * entity.quantity;
        entity.itemType = CartItem.normalizeItemType(item.itemType);
        entity.shopId = item.shopId;
        entity.imageUrl = item.imageUrl;
        entity.createdAt = timestamp;
        entity.updatedAt = timestamp;
        return entity;
    }

    private static int parseInt(@Nullable String value) {
        try {
            return value == null ? 0 : Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return 0;
        }
    }

    @NonNull
    private static String safe(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}
