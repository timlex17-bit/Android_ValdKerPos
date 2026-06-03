package com.valdker.pos.local;

import androidx.annotation.NonNull;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

@Dao
public interface PaymentMethodDao {
    @Query("SELECT * FROM payment_methods ORDER BY name COLLATE NOCASE")
    List<PaymentMethodEntity> getAll();

    @Query("SELECT * FROM payment_methods WHERE shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE AND apiBaseUrl = :apiBaseUrl ORDER BY name COLLATE NOCASE")
    List<PaymentMethodEntity> getAllForShop(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl);

    @Query("SELECT * FROM payment_methods WHERE isActive = 1 ORDER BY name COLLATE NOCASE")
    List<PaymentMethodEntity> getActive();

    @Query("SELECT * FROM payment_methods WHERE shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE AND apiBaseUrl = :apiBaseUrl AND isActive = 1 ORDER BY name COLLATE NOCASE")
    List<PaymentMethodEntity> getActiveForShop(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(@NonNull List<PaymentMethodEntity> paymentMethods);

    @Query("DELETE FROM payment_methods")
    void clearAll();

    @Query("DELETE FROM payment_methods WHERE shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE AND apiBaseUrl = :apiBaseUrl")
    void clearForShop(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl);

    @Transaction
    default void replaceAll(@NonNull List<PaymentMethodEntity> paymentMethods) {
        clearAll();
        upsertAll(paymentMethods);
    }

    @Transaction
    default void replaceAllForShop(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl, @NonNull List<PaymentMethodEntity> paymentMethods) {
        clearForShop(shopId, shopCode, apiBaseUrl);
        upsertAll(paymentMethods);
    }
}
