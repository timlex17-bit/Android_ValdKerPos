package com.valdker.pos.local;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.ColumnInfo;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(
        tableName = "customers",
        indices = {
                @Index("name"),
                @Index("cell"),
                @Index("shopId"),
                @Index("shopCode")
        }
)
public class CustomerEntity {
    @PrimaryKey
    @NonNull
    public String cacheKey = "";

    public int id = 0;

    @ColumnInfo(defaultValue = "0")
    public int shopId = 0;
    public String shopCode = "";
    public String apiBaseUrl = "";
    public String name = "";
    public String cell = "";
    public String email = "";
    public String address = "";
    public long points = 0L;
    public String rawJson = "";
    public long lastSyncAt = 0L;
}
