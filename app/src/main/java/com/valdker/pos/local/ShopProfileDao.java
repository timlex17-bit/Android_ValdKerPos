package com.valdker.pos.local;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface ShopProfileDao {
    @Nullable
    @Query("SELECT * FROM shop_profile WHERE localKey = 1 LIMIT 1")
    ShopProfileEntity getCached();

    @Nullable
    @Query("SELECT * FROM shop_profile WHERE shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE AND apiBaseUrl = :apiBaseUrl LIMIT 1")
    ShopProfileEntity getCachedForShop(@NonNull String shopId, @NonNull String shopCode, @NonNull String apiBaseUrl);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(@NonNull ShopProfileEntity entity);

    @Query("DELETE FROM shop_profile")
    void clear();
}
