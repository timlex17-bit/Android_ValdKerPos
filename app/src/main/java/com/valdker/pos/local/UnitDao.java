package com.valdker.pos.local;

import androidx.annotation.NonNull;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

@Dao
public interface UnitDao {
    @Query("SELECT * FROM units ORDER BY name COLLATE NOCASE")
    List<UnitEntity> getAll();

    @Query("SELECT * FROM units WHERE shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE AND apiBaseUrl = :apiBaseUrl ORDER BY name COLLATE NOCASE")
    List<UnitEntity> getAllForShop(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(@NonNull List<UnitEntity> units);

    @Query("DELETE FROM units")
    void clearAll();

    @Query("DELETE FROM units WHERE shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE AND apiBaseUrl = :apiBaseUrl")
    void clearForShop(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl);

    @Transaction
    default void replaceAll(@NonNull List<UnitEntity> units) {
        clearAll();
        upsertAll(units);
    }

    @Transaction
    default void replaceAllForShop(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl, @NonNull List<UnitEntity> units) {
        clearForShop(shopId, shopCode, apiBaseUrl);
        upsertAll(units);
    }
}
