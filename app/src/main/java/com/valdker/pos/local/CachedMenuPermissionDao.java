package com.valdker.pos.local;

import androidx.annotation.NonNull;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

@Dao
public interface CachedMenuPermissionDao {

    @Query("SELECT * FROM cached_menu_permissions WHERE shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE AND apiBaseUrl = :apiBaseUrl AND username = :username COLLATE NOCASE ORDER BY menuKey ASC")
    List<CachedMenuPermissionEntity> getForUser(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl, @NonNull String username);

    @Query("DELETE FROM cached_menu_permissions WHERE shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE AND apiBaseUrl = :apiBaseUrl AND username = :username COLLATE NOCASE")
    void deleteForUser(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl, @NonNull String username);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(@NonNull List<CachedMenuPermissionEntity> entities);

    @Transaction
    default void replaceForUser(int shopId,
                                @NonNull String shopCode,
                                @NonNull String apiBaseUrl,
                                @NonNull String username,
                                @NonNull List<CachedMenuPermissionEntity> entities) {
        deleteForUser(shopId, shopCode, apiBaseUrl, username);
        upsertAll(entities);
    }
}
