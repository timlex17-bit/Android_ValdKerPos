package com.valdker.pos.local;

import androidx.annotation.NonNull;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

@Dao
public interface WarehouseDao {
    @Query("SELECT * FROM warehouses ORDER BY name COLLATE NOCASE")
    List<WarehouseEntity> getAll();

    @Query("SELECT * FROM warehouses WHERE shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE AND apiBaseUrl = :apiBaseUrl ORDER BY name COLLATE NOCASE")
    List<WarehouseEntity> getAllForShop(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl);

    @Query("SELECT * FROM warehouses WHERE isActive = 1 ORDER BY name COLLATE NOCASE")
    List<WarehouseEntity> getActive();

    @Query("SELECT * FROM warehouses WHERE shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE AND apiBaseUrl = :apiBaseUrl AND isActive = 1 ORDER BY name COLLATE NOCASE")
    List<WarehouseEntity> getActiveForShop(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(@NonNull List<WarehouseEntity> warehouses);

    @Query("DELETE FROM warehouses")
    void clearAll();

    @Query("DELETE FROM warehouses WHERE shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE AND apiBaseUrl = :apiBaseUrl")
    void clearForShop(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl);

    @Transaction
    default void replaceAll(@NonNull List<WarehouseEntity> warehouses) {
        clearAll();
        upsertAll(warehouses);
    }

    @Transaction
    default void replaceAllForShop(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl, @NonNull List<WarehouseEntity> warehouses) {
        clearForShop(shopId, shopCode, apiBaseUrl);
        upsertAll(warehouses);
    }
}
