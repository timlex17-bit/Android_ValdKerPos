package com.valdker.pos.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "cached_roles",
        indices = {
                @Index("backendId"),
                @Index("shopId"),
                @Index("shopCode"),
                @Index("role")
        }
)
public class CachedRoleEntity {
    @PrimaryKey
    @NonNull
    public String cacheKey = "";

    public int backendId;
    @ColumnInfo(defaultValue = "0")
    public int shopId;
    public String shopCode;
    public String apiBaseUrl;
    public String role;
    public String name;
    public String description;
    public String rawJson;
    public long lastSyncAt;
}
