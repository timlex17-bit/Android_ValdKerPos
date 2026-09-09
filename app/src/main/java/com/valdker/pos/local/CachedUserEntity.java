package com.valdker.pos.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "cached_users",
        indices = {
                @Index("backendId"),
                @Index("shopId"),
                @Index("shopCode"),
                @Index("username"),
                @Index("role")
        }
)
public class CachedUserEntity {
    @PrimaryKey
    @NonNull
    public String cacheKey = "";

    public int backendId;
    @ColumnInfo(defaultValue = "0")
    public int shopId;
    public String shopCode;
    public String apiBaseUrl;
    public String username;
    public String fullName;
    public String email;
    public String role;
    public String shopName;
    @ColumnInfo(defaultValue = "'retail'")
    public String businessType;
    @ColumnInfo(defaultValue = "'basic'")
    public String plan;
    @ColumnInfo(defaultValue = "'[]'")
    public String effectiveModulesJson;
    public boolean isActive;
    public String lastLogin;
    public String syncedAt;
    public String permissionsJson;
    public String menuPermissionsJson;
    public String featuresJson;
    public boolean isSuperuser;
    public boolean isPlatformAdmin;
    public boolean isShopOwner;
    public boolean isShopManager;
    public boolean isShopCashier;
    public String rawJson;
    public long lastSyncAt;
}
