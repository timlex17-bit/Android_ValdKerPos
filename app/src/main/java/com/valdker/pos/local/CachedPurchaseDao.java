package com.valdker.pos.local;

import androidx.annotation.NonNull;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

@Dao
public interface CachedPurchaseDao {
    @Query("SELECT * FROM cached_purchases WHERE shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE AND apiBaseUrl = :apiBaseUrl ORDER BY purchaseDate DESC, createdAt DESC, backendId DESC")
    List<CachedPurchaseEntity> getPurchasesForShop(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl);

    @Query("SELECT * FROM cached_purchase_items WHERE shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE AND apiBaseUrl = :apiBaseUrl AND purchaseBackendId = :purchaseBackendId ORDER BY backendId ASC")
    List<CachedPurchaseItemEntity> getItemsForPurchase(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl, int purchaseBackendId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertPurchases(@NonNull List<CachedPurchaseEntity> rows);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertItems(@NonNull List<CachedPurchaseItemEntity> rows);

    @Query("DELETE FROM cached_purchases WHERE shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE AND apiBaseUrl = :apiBaseUrl")
    void clearPurchasesForShop(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl);

    @Query("DELETE FROM cached_purchase_items WHERE shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE AND apiBaseUrl = :apiBaseUrl")
    void clearItemsForShop(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl);

    @Transaction
    default void replaceForShop(int shopId,
                                @NonNull String shopCode,
                                @NonNull String apiBaseUrl,
                                @NonNull List<CachedPurchaseEntity> purchases,
                                @NonNull List<CachedPurchaseItemEntity> items) {
        clearItemsForShop(shopId, shopCode, apiBaseUrl);
        clearPurchasesForShop(shopId, shopCode, apiBaseUrl);
        upsertPurchases(purchases);
        upsertItems(items);
    }
}
