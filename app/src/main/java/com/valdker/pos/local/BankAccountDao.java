package com.valdker.pos.local;

import androidx.annotation.NonNull;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

@Dao
public interface BankAccountDao {
    @Query("SELECT * FROM bank_accounts ORDER BY bankName COLLATE NOCASE, name COLLATE NOCASE")
    List<BankAccountEntity> getAll();

    @Query("SELECT * FROM bank_accounts WHERE shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE AND apiBaseUrl = :apiBaseUrl ORDER BY bankName COLLATE NOCASE, name COLLATE NOCASE")
    List<BankAccountEntity> getAllForShop(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl);

    @Query("SELECT * FROM bank_accounts WHERE isActive = 1 ORDER BY bankName COLLATE NOCASE, name COLLATE NOCASE")
    List<BankAccountEntity> getActive();

    @Query("SELECT * FROM bank_accounts WHERE shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE AND apiBaseUrl = :apiBaseUrl AND isActive = 1 ORDER BY bankName COLLATE NOCASE, name COLLATE NOCASE")
    List<BankAccountEntity> getActiveForShop(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(@NonNull List<BankAccountEntity> bankAccounts);

    @Query("DELETE FROM bank_accounts")
    void clearAll();

    @Query("DELETE FROM bank_accounts WHERE shopId = :shopId AND shopCode = :shopCode COLLATE NOCASE AND apiBaseUrl = :apiBaseUrl")
    void clearForShop(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl);

    @Transaction
    default void replaceAll(@NonNull List<BankAccountEntity> bankAccounts) {
        clearAll();
        upsertAll(bankAccounts);
    }

    @Transaction
    default void replaceAllForShop(int shopId, @NonNull String shopCode, @NonNull String apiBaseUrl, @NonNull List<BankAccountEntity> bankAccounts) {
        clearForShop(shopId, shopCode, apiBaseUrl);
        upsertAll(bankAccounts);
    }
}
