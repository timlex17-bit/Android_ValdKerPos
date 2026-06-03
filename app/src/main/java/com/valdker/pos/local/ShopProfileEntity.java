package com.valdker.pos.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "shop_profile",
        indices = {
                @Index("id"),
                @Index("shopId"),
                @Index("shopCode")
        }
)
public class ShopProfileEntity {
    @PrimaryKey
    @NonNull
    public String cacheKey = "";

    public int localKey = 1;

    public int id = 0;

    @NonNull
    public String shopId = "";

    @NonNull
    public String shopCode = "";

    @NonNull
    public String apiBaseUrl = "";

    @NonNull
    public String name = "";

    @NonNull
    public String storeName = "";

    @NonNull
    public String address = "";

    @NonNull
    public String phone = "";

    @NonNull
    public String email = "";

    @NonNull
    public String logoUrl = "";

    @NonNull
    public String location = "";

    @NonNull
    public String version = "";

    @NonNull
    public String updatedAt = "";

    public long lastSyncAt = 0L;
}
