package com.valdker.pos.local;

import androidx.annotation.NonNull;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

@Dao
public interface CachedUserDao {

    @Query("SELECT * FROM cached_users WHERE shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE AND apiBaseUrl = :apiBaseUrl AND username = :username COLLATE NOCASE LIMIT 1")
    CachedUserEntity getUserForSession(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl, @NonNull String username);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertUser(@NonNull CachedUserEntity entity);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertRole(@NonNull CachedRoleEntity entity);

    @Transaction
    default void upsertUserAndRole(@NonNull CachedUserEntity user, @NonNull CachedRoleEntity role) {
        upsertUser(user);
        upsertRole(role);
    }
}
