package com.valdker.pos.local;

import androidx.annotation.NonNull;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

@Dao
public interface CachedStockMovementDao {
    @Query("SELECT * FROM cached_stock_movements WHERE shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE AND apiBaseUrl = :apiBaseUrl ORDER BY createdAt DESC, backendId DESC")
    List<CachedStockMovementEntity> getForShop(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(@NonNull List<CachedStockMovementEntity> rows);

    @Query("DELETE FROM cached_stock_movements WHERE shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE AND apiBaseUrl = :apiBaseUrl")
    void clearForShop(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl);

    @Transaction
    default void replaceAllForShop(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl, @NonNull List<CachedStockMovementEntity> rows) {
        clearForShop(shopId, shopCode, apiBaseUrl);
        upsertAll(rows);
    }
}
