package com.example.valdker.offline.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.valdker.offline.db.entities.CategoryEntity;

import java.util.List;

@Dao
public interface CategoryDao {

    @Query("SELECT COUNT(*) FROM categories")
    int countAll();

    @Query("SELECT * FROM categories WHERE is_active = 1 ORDER BY name COLLATE NOCASE ASC")
    List<CategoryEntity> getAllActive();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<CategoryEntity> list);

    @Query("UPDATE categories SET is_active = 0")
    void markAllInactive();
}