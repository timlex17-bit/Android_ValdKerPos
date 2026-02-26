package com.example.valdker.offline.db.entities;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * ProductEntity (Room)
 * Cache master products agar bisa tampil saat offline.
 */
@Entity(
        tableName = "products",
        indices = {
                @Index(value = {"server_id"}, unique = true),
                @Index(value = {"barcode"}),
                @Index(value = {"sku"}),
                @Index(value = {"is_active"})
        }
)
public class ProductEntity {

    @PrimaryKey(autoGenerate = true)
    public long id; // local id

    @NonNull
    @ColumnInfo(name = "server_id")
    public String serverId = "";

    @ColumnInfo(name = "name")
    public String name;

    @ColumnInfo(name = "barcode")
    public String barcode;

    @ColumnInfo(name = "sku")
    public String sku;

    // ✅ IMAGE URL cache
    @ColumnInfo(name = "image_url")
    public String imageUrl;

    @ColumnInfo(name = "price")
    public double price;

    @ColumnInfo(name = "stock")
    public int stock;

    // opsional (kalau nanti dipakai)
    @ColumnInfo(name = "min_stock")
    public int minStock;

    @ColumnInfo(name = "category_server_id")
    public String categoryServerId;

    @ColumnInfo(name = "is_active")
    public boolean isActive = true;

    @ColumnInfo(name = "updated_at_iso")
    public String updatedAtIso;

    public ProductEntity() {}
}