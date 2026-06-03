package com.valdker.pos.local;

import androidx.annotation.NonNull;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

@Dao
public interface CachedOrderDao {
    @Query("SELECT * FROM cached_orders WHERE shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE AND apiBaseUrl = :apiBaseUrl ORDER BY createdAt DESC, backendId DESC")
    List<CachedOrderEntity> getOrdersForShop(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl);

    @Query("SELECT * FROM cached_order_items WHERE shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE AND apiBaseUrl = :apiBaseUrl AND orderBackendId = :orderBackendId ORDER BY backendId ASC")
    List<CachedOrderItemEntity> getItemsForOrder(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl, int orderBackendId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertOrders(@NonNull List<CachedOrderEntity> orders);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertItems(@NonNull List<CachedOrderItemEntity> items);

    @Query("DELETE FROM cached_orders WHERE shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE AND apiBaseUrl = :apiBaseUrl")
    void clearOrdersForShop(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl);

    @Query("DELETE FROM cached_order_items WHERE shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE AND apiBaseUrl = :apiBaseUrl")
    void clearItemsForShop(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl);

    @Transaction
    default void replaceForShop(int shopId,
                                @NonNull String shopCode,
                                @NonNull String apiBaseUrl,
                                @NonNull List<CachedOrderEntity> orders,
                                @NonNull List<CachedOrderItemEntity> items) {
        clearItemsForShop(shopId, shopCode, apiBaseUrl);
        clearOrdersForShop(shopId, shopCode, apiBaseUrl);
        upsertOrders(orders);
        upsertItems(items);
    }
}
