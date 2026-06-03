package com.valdker.pos.local;

import androidx.annotation.NonNull;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

@Dao
public interface CachedStockTransferDao {
    @Query("SELECT * FROM cached_stock_transfers WHERE shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE AND apiBaseUrl = :apiBaseUrl ORDER BY createdAt DESC, backendId DESC")
    List<CachedStockTransferEntity> getTransfersForShop(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl);

    @Query("SELECT * FROM cached_stock_transfer_items WHERE shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE AND apiBaseUrl = :apiBaseUrl AND transferBackendId = :transferBackendId ORDER BY backendId ASC")
    List<CachedStockTransferItemEntity> getItemsForTransfer(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl, int transferBackendId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertTransfers(@NonNull List<CachedStockTransferEntity> rows);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertItems(@NonNull List<CachedStockTransferItemEntity> rows);

    @Query("DELETE FROM cached_stock_transfers WHERE shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE AND apiBaseUrl = :apiBaseUrl")
    void clearTransfersForShop(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl);

    @Query("DELETE FROM cached_stock_transfer_items WHERE shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE AND apiBaseUrl = :apiBaseUrl")
    void clearItemsForShop(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl);

    @Transaction
    default void replaceForShop(int shopId,
                                @NonNull String shopCode,
                                @NonNull String apiBaseUrl,
                                @NonNull List<CachedStockTransferEntity> transfers,
                                @NonNull List<CachedStockTransferItemEntity> items) {
        clearItemsForShop(shopId, shopCode, apiBaseUrl);
        clearTransfersForShop(shopId, shopCode, apiBaseUrl);
        upsertTransfers(transfers);
        upsertItems(items);
    }
}
