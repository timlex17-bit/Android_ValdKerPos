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
public interface ProductDao {
    @Query("SELECT * FROM products WHERE isActive = 1 AND (:categoryId IS NULL OR :categoryId = '' OR :categoryId = 'all' OR :categoryId = '-1' OR categoryId = :categoryId) ORDER BY name COLLATE NOCASE")
    List<ProductEntity> getActiveProducts(@Nullable String categoryId);

    @Query("SELECT * FROM products WHERE shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE AND apiBaseUrl = :apiBaseUrl AND isActive = 1 AND (:categoryId IS NULL OR :categoryId = '' OR :categoryId = 'all' OR :categoryId = '-1' OR categoryId = :categoryId) ORDER BY name COLLATE NOCASE")
    List<ProductEntity> getActiveProductsForShop(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl, @Nullable String categoryId);

    @Query("SELECT * FROM products WHERE shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE AND apiBaseUrl = :apiBaseUrl ORDER BY name COLLATE NOCASE")
    List<ProductEntity> getAllForShop(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl);

    @Query("SELECT * FROM products WHERE barcode = :barcode COLLATE NOCASE OR sku = :barcode COLLATE NOCASE LIMIT 1")
    ProductEntity findByBarcodeOrSku(@NonNull String barcode);

    @Query("SELECT * FROM products WHERE shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE AND apiBaseUrl = :apiBaseUrl AND (barcode = :barcode COLLATE NOCASE OR sku = :barcode COLLATE NOCASE) LIMIT 1")
    ProductEntity findByBarcodeOrSkuForShop(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl, @NonNull String barcode);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(@NonNull List<ProductEntity> products);

    @Query("DELETE FROM products")
    void clearAll();

    @Query("DELETE FROM products WHERE shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE AND apiBaseUrl = :apiBaseUrl")
    void clearForShop(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl);

    @Transaction
    default void replaceAll(@NonNull List<ProductEntity> products) {
        clearAll();
        upsertAll(products);
    }

    @Transaction
    default void replaceAllForShop(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl, @NonNull List<ProductEntity> products) {
        clearForShop(shopId, shopCode, apiBaseUrl);
        upsertAll(products);
    }
}
