package com.valdker.pos.local;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.ColumnInfo;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(
        tableName = "suppliers",
        indices = {
                @Index("name"),
                @Index("cell"),
                @Index("shopId"),
                @Index("shopCode")
        }
)
public class SupplierEntity {
    @PrimaryKey
    @NonNull
    public String cacheKey = "";

    public int id = 0;

    @ColumnInfo(defaultValue = "0")
    public int shopId = 0;
    public String shopCode = "";
    public String apiBaseUrl = "";
    public String name = "";
    public String contactPerson = "";
    public String cell = "";
    public String email = "";
    public String address = "";
    public String rawJson = "";
    public long lastSyncAt = 0L;
}
