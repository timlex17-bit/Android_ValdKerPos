package com.valdker.pos.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "cached_expenses",
        indices = {
                @Index("backendId"),
                @Index("shopId"),
                @Index("shopCode"),
                @Index("date")
        }
)
public class CachedExpenseEntity {
    @PrimaryKey
    @NonNull
    public String cacheKey = "";

    public int backendId = 0;
    @ColumnInfo(defaultValue = "0")
    public int shopId = 0;
    public String shopCode = "";
    public String apiBaseUrl = "";
    public String name = "";
    public String title = "";
    public String note = "";
    public String amount = "0.00";
    public String category = "";
    public String date = "";
    public String time = "";
    public String createdAt = "";
    public String rawJson = "";
    public long lastSyncAt = 0L;
}
