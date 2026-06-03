package com.valdker.pos.local;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(
        tableName = "warehouses",
        indices = {
                @Index("shopId"),
                @Index("shopCode"),
                @Index("code"),
                @Index("isActive")
        }
)
public class WarehouseEntity {
    @PrimaryKey
    @NonNull
    public String cacheKey = "";

    public int id = 0;

    public int shopId = 0;
    public String shopName = "";
    public String shopCode = "";
    public String apiBaseUrl = "";
    public String name = "";
    public String code = "";
    public String location = "";
    public boolean isActive = true;
    public boolean isDefault = false;
    public String createdAt = "";
    public String updatedAt = "";
    public String rawJson = "";
    public long lastSyncAt = 0L;
}
