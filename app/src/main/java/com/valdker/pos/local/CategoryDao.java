package com.valdker.pos.local;

import androidx.annotation.NonNull;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

@Dao
public interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE")
    List<CategoryEntity> getAll();

    @Query("SELECT * FROM categories WHERE shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE AND apiBaseUrl = :apiBaseUrl ORDER BY name COLLATE NOCASE")
    List<CategoryEntity> getAllForShop(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(@NonNull List<CategoryEntity> categories);

    @Query("DELETE FROM categories")
    void clearAll();

    @Query("DELETE FROM categories WHERE shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE AND apiBaseUrl = :apiBaseUrl")
    void clearForShop(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl);

    @Transaction
    default void replaceAll(@NonNull List<CategoryEntity> categories) {
        clearAll();
        upsertAll(categories);
    }

    @Transaction
    default void replaceAllForShop(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl, @NonNull List<CategoryEntity> categories) {
        clearForShop(shopId, shopCode, apiBaseUrl);
        upsertAll(categories);
    }
}
