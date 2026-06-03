package com.valdker.pos.local;

import androidx.annotation.NonNull;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

@Dao
public interface CachedProductReturnDao {
    @Query("SELECT * FROM cached_product_returns WHERE shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE AND apiBaseUrl = :apiBaseUrl ORDER BY returnedAt DESC, createdAt DESC, backendId DESC")
    List<CachedProductReturnEntity> getReturnsForShop(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl);

    @Query("SELECT * FROM cached_product_return_items WHERE shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE AND apiBaseUrl = :apiBaseUrl AND returnBackendId = :returnBackendId ORDER BY backendId ASC")
    List<CachedProductReturnItemEntity> getItemsForReturn(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl, int returnBackendId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertReturns(@NonNull List<CachedProductReturnEntity> rows);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertItems(@NonNull List<CachedProductReturnItemEntity> rows);

    @Query("DELETE FROM cached_product_returns WHERE shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE AND apiBaseUrl = :apiBaseUrl")
    void clearReturnsForShop(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl);

    @Query("DELETE FROM cached_product_return_items WHERE shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE AND apiBaseUrl = :apiBaseUrl")
    void clearItemsForShop(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl);

    @Transaction
    default void replaceForShop(int shopId,
                                @NonNull String shopCode,
                                @NonNull String apiBaseUrl,
                                @NonNull List<CachedProductReturnEntity> returns,
                                @NonNull List<CachedProductReturnItemEntity> items) {
        clearItemsForShop(shopId, shopCode, apiBaseUrl);
        clearReturnsForShop(shopId, shopCode, apiBaseUrl);
        upsertReturns(returns);
        upsertItems(items);
    }
}
