package com.valdker.pos.local;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

@Dao
public interface PendingOrderDao {
    @Nullable
    @Query("SELECT * FROM pending_orders WHERE localOrderId = :localOrderId LIMIT 1")
    PendingOrderEntity getByLocalOrderId(@NonNull String localOrderId);

    @Nullable
    @Query("SELECT * FROM pending_orders WHERE clientOrderId = :clientOrderId LIMIT 1")
    PendingOrderEntity getByClientOrderId(@NonNull String clientOrderId);

    @Query("SELECT * FROM pending_orders WHERE syncStatus IN ('PENDING_SYNC', 'FAILED') ORDER BY createdAt ASC")
    List<PendingOrderEntity> getSyncableOrders();

    @Query("SELECT * FROM pending_orders WHERE syncStatus IN ('PENDING_SYNC', 'FAILED') AND shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE ORDER BY createdAt ASC")
    List<PendingOrderEntity> getSyncableOrdersForShop(int shopId, @NonNull String shopCode);

    @Query("SELECT * FROM pending_orders ORDER BY createdAt DESC")
    List<PendingOrderEntity> getAllOfflineOrders();

    @Query("SELECT * FROM pending_orders WHERE syncStatus = :status ORDER BY createdAt DESC")
    List<PendingOrderEntity> getOfflineOrdersByStatus(@NonNull String status);

    @Query("SELECT * FROM pending_orders WHERE shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE ORDER BY createdAt DESC")
    List<PendingOrderEntity> getOrdersForShop(int shopId, @NonNull String shopCode);

    @Query("SELECT * FROM pending_orders WHERE shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE AND syncStatus = :status ORDER BY createdAt DESC")
    List<PendingOrderEntity> getOrdersForShopByStatus(int shopId, @NonNull String shopCode, @NonNull String status);

    @Query("SELECT * FROM pending_orders WHERE shopId <= 0 OR shopCode = '' OR shopId != :shopId OR shopCode != :shopCode COLLATE NOCASE ORDER BY createdAt DESC")
    List<PendingOrderEntity> getOrdersNotForShop(int shopId, @NonNull String shopCode);

    @Query("SELECT * FROM pending_orders WHERE syncStatus = 'NEEDS_REVIEW' AND ((shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE) OR shopId <= 0 OR shopCode = '') ORDER BY createdAt DESC")
    List<PendingOrderEntity> getNeedsReviewOrdersForUi(int shopId, @NonNull String shopCode);

    @Query("SELECT * FROM pending_order_items WHERE localOrderId = :localOrderId ORDER BY id ASC")
    List<PendingOrderItemEntity> getItemsByLocalOrderId(@NonNull String localOrderId);

    @Query("SELECT COUNT(*) FROM pending_orders WHERE syncStatus = 'PENDING_SYNC'")
    int countPendingSync();

    @Query("SELECT COUNT(*) FROM pending_orders WHERE syncStatus = 'FAILED'")
    int countFailed();

    @Query("SELECT COUNT(*) FROM pending_orders WHERE syncStatus = 'NEEDS_REVIEW'")
    int countNeedsReview();

    @Query("SELECT COUNT(*) FROM pending_orders WHERE syncStatus = 'PENDING_SYNC' AND shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE")
    int countPendingSyncForShop(int shopId, @NonNull String shopCode);

    @Query("SELECT COUNT(*) FROM pending_orders WHERE syncStatus = 'FAILED' AND shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE")
    int countFailedForShop(int shopId, @NonNull String shopCode);

    @Query("SELECT COUNT(*) FROM pending_orders WHERE syncStatus = 'NEEDS_REVIEW' AND shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE")
    int countNeedsReviewForShop(int shopId, @NonNull String shopCode);

    @Query("SELECT COUNT(*) FROM pending_orders WHERE syncStatus = 'SYNCED' AND shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE")
    int countSyncedForShop(int shopId, @NonNull String shopCode);

    @Query("SELECT COUNT(*) FROM pending_orders WHERE shopId <= 0 OR shopCode = '' OR shopId != :shopId OR shopCode != :shopCode COLLATE NOCASE")
    int countOrdersNotForShop(int shopId, @NonNull String shopCode);

    @Query("UPDATE pending_orders SET syncStatus = 'PENDING_SYNC', updatedAt = :updatedAt WHERE syncStatus = 'SYNCING'")
    void resetSyncingToPending(long updatedAt);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertOrder(@NonNull PendingOrderEntity order);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertItems(@NonNull List<PendingOrderItemEntity> items);

    @Query("DELETE FROM pending_order_items WHERE localOrderId = :localOrderId")
    void deleteItems(@NonNull String localOrderId);

    @Transaction
    default boolean insertOrderWithItems(@NonNull PendingOrderEntity order,
                                         @NonNull List<PendingOrderItemEntity> items) {
        long rowId = insertOrder(order);
        if (rowId == -1L) return false;
        deleteItems(order.localOrderId);
        insertItems(items);
        return true;
    }

    @Query("UPDATE pending_orders SET syncStatus = :status, updatedAt = :updatedAt WHERE localOrderId = :localOrderId")
    void updateStatus(@NonNull String localOrderId, @NonNull String status, long updatedAt);

    @Query("UPDATE pending_orders SET clientOrderId = :clientOrderId, updatedAt = :updatedAt WHERE localOrderId = :localOrderId")
    void updateClientOrderId(@NonNull String localOrderId, @NonNull String clientOrderId, long updatedAt);

    @Query("UPDATE pending_orders SET syncStatus = 'SYNCED', updatedAt = :updatedAt, lastSyncError = '' WHERE localOrderId = :localOrderId")
    void markSynced(@NonNull String localOrderId, long updatedAt);

    @Query("UPDATE pending_orders SET syncStatus = 'FAILED', updatedAt = :updatedAt, syncAttemptCount = syncAttemptCount + 1, lastSyncError = :error WHERE localOrderId = :localOrderId")
    void markFailed(@NonNull String localOrderId, long updatedAt, @NonNull String error);

    @Query("UPDATE pending_orders SET syncStatus = 'NEEDS_REVIEW', updatedAt = :updatedAt, syncAttemptCount = syncAttemptCount + 1, lastSyncError = :error WHERE localOrderId = :localOrderId")
    void markNeedsReview(@NonNull String localOrderId, long updatedAt, @NonNull String error);

    @Query("UPDATE pending_orders SET syncStatus = 'NEEDS_REVIEW', updatedAt = :updatedAt, lastSyncError = :error WHERE localOrderId = :localOrderId")
    void markNeedsReviewNoAttemptIncrement(@NonNull String localOrderId, long updatedAt, @NonNull String error);

    @Query("UPDATE pending_orders SET syncStatus = 'NEEDS_REVIEW', updatedAt = :updatedAt, lastSyncError = :error WHERE localOrderId = :localOrderId")
    void markShopMismatch(@NonNull String localOrderId, long updatedAt, @NonNull String error);
}
