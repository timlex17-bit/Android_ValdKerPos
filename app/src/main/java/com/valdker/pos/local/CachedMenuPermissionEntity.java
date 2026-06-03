package com.valdker.pos.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "cached_menu_permissions",
        indices = {
                @Index("shopId"),
                @Index("shopCode"),
                @Index("userId"),
                @Index("username"),
                @Index("role"),
                @Index("menuKey"),
                @Index("canView")
        }
)
public class CachedMenuPermissionEntity {
    @PrimaryKey
    @NonNull
    public String cacheKey = "";

    @ColumnInfo(defaultValue = "0")
    public int shopId;
    public String shopCode;
    public String apiBaseUrl;
    public int userId;
    public String username;
    public String role;
    public String menuKey;
    public boolean canView;
    public boolean enabled;
    public String rawJson;
    public long lastSyncAt;
}
