package com.valdker.pos.local;

import androidx.annotation.NonNull;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface CachedReportDao {

    @Query("SELECT * FROM cached_reports WHERE cacheKey = :cacheKey LIMIT 1")
    CachedReportEntity getByCacheKey(@NonNull String cacheKey);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(@NonNull CachedReportEntity entity);

    @Query("DELETE FROM cached_reports WHERE shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE AND apiBaseUrl = :apiBaseUrl AND lastSyncAt < :cutoff")
    void deleteOlderThanForShop(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl, long cutoff);
}
