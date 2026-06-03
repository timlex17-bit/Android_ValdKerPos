package com.valdker.pos.local;

import androidx.annotation.NonNull;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

@Dao
public interface CachedInventoryCountDao {
    @Query("SELECT * FROM cached_inventory_counts WHERE shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE AND apiBaseUrl = :apiBaseUrl ORDER BY countedAt DESC, backendId DESC")
    List<CachedInventoryCountEntity> getCountsForShop(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl);

    @Query("SELECT * FROM cached_inventory_count_items WHERE shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE AND apiBaseUrl = :apiBaseUrl AND inventoryCountBackendId = :inventoryCountBackendId ORDER BY backendId ASC")
    List<CachedInventoryCountItemEntity> getItemsForCount(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl, int inventoryCountBackendId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertCounts(@NonNull List<CachedInventoryCountEntity> rows);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertItems(@NonNull List<CachedInventoryCountItemEntity> rows);

    @Query("DELETE FROM cached_inventory_counts WHERE shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE AND apiBaseUrl = :apiBaseUrl")
    void clearCountsForShop(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl);

    @Query("DELETE FROM cached_inventory_count_items WHERE shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE AND apiBaseUrl = :apiBaseUrl")
    void clearItemsForShop(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl);

    @Transaction
    default void replaceForShop(int shopId,
                                @NonNull String shopCode,
                                @NonNull String apiBaseUrl,
                                @NonNull List<CachedInventoryCountEntity> counts,
                                @NonNull List<CachedInventoryCountItemEntity> items) {
        clearItemsForShop(shopId, shopCode, apiBaseUrl);
        clearCountsForShop(shopId, shopCode, apiBaseUrl);
        upsertCounts(counts);
        upsertItems(items);
    }
}
