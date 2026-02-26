package com.example.valdker.offline.db;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import com.example.valdker.offline.db.dao.CategoryDao;
import com.example.valdker.offline.db.dao.ProductDao;
import com.example.valdker.offline.db.entities.CategoryEntity;
import com.example.valdker.offline.db.entities.ProductEntity;

@Database(
        entities = {
                ProductEntity.class,
                CategoryEntity.class
        },
        version = 4,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {
    public abstract ProductDao productDao();
    public abstract CategoryDao categoryDao();
}