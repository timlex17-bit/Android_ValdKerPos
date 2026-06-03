package com.valdker.pos.local;

import androidx.annotation.NonNull;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

@Dao
public interface WarehouseStockDao {
    @Query("SELECT * FROM warehouse_stocks ORDER BY warehouseName COLLATE NOCASE, productName COLLATE NOCASE")
    List<WarehouseStockEntity> getAll();

    @Query("SELECT * FROM warehouse_stocks WHERE shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE AND apiBaseUrl = :apiBaseUrl ORDER BY warehouseName COLLATE NOCASE, productName COLLATE NOCASE")
    List<WarehouseStockEntity> getAllForShop(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl);

    @Query("SELECT * FROM warehouse_stocks WHERE product = :productId ORDER BY warehouseName COLLATE NOCASE")
    List<WarehouseStockEntity> getByProduct(int productId);

    @Query("SELECT * FROM warehouse_stocks WHERE shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE AND apiBaseUrl = :apiBaseUrl AND product = :productId ORDER BY warehouseName COLLATE NOCASE")
    List<WarehouseStockEntity> getByProductForShop(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl, int productId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(@NonNull List<WarehouseStockEntity> stocks);

    @Query("DELETE FROM warehouse_stocks")
    void clearAll();

    @Query("DELETE FROM warehouse_stocks WHERE shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE AND apiBaseUrl = :apiBaseUrl")
    void clearForShop(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl);

    @Transaction
    default void replaceAll(@NonNull List<WarehouseStockEntity> stocks) {
        clearAll();
        upsertAll(stocks);
    }

    @Transaction
    default void replaceAllForShop(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl, @NonNull List<WarehouseStockEntity> stocks) {
        clearForShop(shopId, shopCode, apiBaseUrl);
        upsertAll(stocks);
    }
}
