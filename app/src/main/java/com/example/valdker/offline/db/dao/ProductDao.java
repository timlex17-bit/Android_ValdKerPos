package com.example.valdker.offline.db.dao;

import androidx.annotation.NonNull;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.valdker.offline.db.entities.ProductEntity;

import java.util.List;

@Dao
public interface ProductDao {

    // ====== READ ======

    @Query("SELECT * FROM products WHERE is_active=1 ORDER BY name ASC")
    List<ProductEntity> getAllActive();

    @Query("SELECT * FROM products WHERE server_id=:serverId LIMIT 1")
    ProductEntity findByServerId(@NonNull String serverId);

    @Query("SELECT * FROM products WHERE barcode=:barcode AND is_active=1 LIMIT 1")
    ProductEntity findByBarcode(@NonNull String barcode);

    @Query("SELECT * FROM products " +
            "WHERE is_active=1 AND (" +
            "name LIKE :q OR barcode LIKE :q OR sku LIKE :q" +
            ") ORDER BY name ASC LIMIT 80")
    List<ProductEntity> search(@NonNull String q);

    // ====== WRITE ======

    /**
     * Upsert by unique index server_id.
     * REPLACE akan mengganti row lama jika server_id sama.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(@NonNull List<ProductEntity> items);

    @Query("DELETE FROM products")
    void clearAll();

    @Query("SELECT COUNT(*) FROM products")
    int countAll();
}