package com.example.valdker.offline.db.entities;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "categories",
        indices = {
                @Index(value = {"server_id"}, unique = true),
                @Index(value = {"name"})
        }
)
public class CategoryEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "server_id")
    public String server_id;

    public String name;

    @ColumnInfo(name = "image_url")
    public String imageUrl; // ✅ tambahkan ini

    @ColumnInfo(name = "is_active")
    public boolean isActive;
}