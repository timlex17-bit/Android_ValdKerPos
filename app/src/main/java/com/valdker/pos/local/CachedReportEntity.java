package com.valdker.pos.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "cached_reports",
        indices = {
                @Index("shopId"),
                @Index("shopCode"),
                @Index("reportType"),
                @Index("queryHash"),
                @Index("dateFrom"),
                @Index("dateTo"),
                @Index("lastSyncAt")
        }
)
public class CachedReportEntity {
    @PrimaryKey
    @NonNull
    public String cacheKey = "";

    @ColumnInfo(defaultValue = "0")
    public int shopId;
    public String shopCode;
    public String apiBaseUrl;
    public String reportType;
    public String queryHash;
    public String filterJson;
    public String responseJson;
    public String title;
    public String dateFrom;
    public String dateTo;
    public long lastSyncAt;
    public long createdAt;
}
