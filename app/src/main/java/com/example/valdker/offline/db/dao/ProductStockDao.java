package com.example.valdker.offline.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import com.example.valdker.offline.db.entities.ProductStockEntity;

import java.util.List;

@Dao
public interface ProductStockDao {

    @Query("SELECT * FROM product_stock WHERE product_id = :id LIMIT 1")
    ProductStockEntity getById(String id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(ProductStockEntity entity);

    @Query("UPDATE product_stock SET stock_qty = stock_qty - :qty WHERE product_id = :id")
    void deductStock(String id, double qty);

    @Query("UPDATE product_stock SET stock_qty = stock_qty + :qty WHERE product_id = :id")
    void increaseStock(String id, double qty);

    @Query("SELECT * FROM product_stock")
    List<ProductStockEntity> getAll();
}