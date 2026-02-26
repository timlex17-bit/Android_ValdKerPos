package com.example.valdker.offline.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import com.example.valdker.offline.db.entities.OrderEntity;

import java.util.List;

@Dao
public interface OrderDao {

    @Insert
    long insert(OrderEntity order);

    @Update
    void update(OrderEntity order);

    @Query("SELECT * FROM orders WHERE id = :id LIMIT 1")
    OrderEntity getById(long id);

    @Query("SELECT * FROM orders WHERE synced = 0 ORDER BY id ASC LIMIT :limit")
    List<OrderEntity> getPendingSync(int limit);

    @Query("UPDATE orders SET synced = 1, server_id = :serverId, synced_at_iso = :syncedAt WHERE id = :localId")
    void markSynced(long localId, String serverId, String syncedAt);

    @Query("DELETE FROM orders WHERE id = :id")
    void deleteById(long id);

    @Query(
            "SELECT " +
                    "COALESCE(SUM(CASE WHEN payment_method = 'CASH' THEN total ELSE 0 END), 0) AS cash_sales, " +
                    "COALESCE(SUM(CASE WHEN payment_method != 'CASH' THEN total ELSE 0 END), 0) AS non_cash_sales, " +
                    "COUNT(*) AS orders_count " +
                    "FROM orders WHERE shift_local_id = :shiftId"
    )
    com.example.valdker.offline.repo.ShiftRepository.ShiftSummary computeShiftSummary(long shiftId);
}