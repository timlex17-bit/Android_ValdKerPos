package com.example.valdker.offline.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.valdker.offline.db.entities.OrderItemEntity;

import java.util.List;

@Dao
public interface OrderItemDao {

    @Insert
    void insertAll(List<OrderItemEntity> items);

    @Query("SELECT * FROM order_items WHERE order_id = :orderId ORDER BY id ASC")
    List<OrderItemEntity> listByOrderId(long orderId);

    @Query("DELETE FROM order_items WHERE order_id = :orderId")
    void deleteByOrderId(long orderId);
}