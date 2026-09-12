package com.valdker.pos.restaurant;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.valdker.pos.money.Money;

import org.json.JSONObject;

/**
 * Satu baris {@code GET /api/reports/waiter-performance/}.
 *
 * <p>Uang datang sebagai string dua desimal dan disimpan sebagai {@link Money}
 * (BigDecimal di dalamnya). Jangan pernah mengubahnya jadi {@code double} untuk
 * ditampilkan, dan jangan memformat string uang dengan {@code %.2f} -
 * {@code String.format} menerima String pada slot {@code %f} saat kompilasi lalu
 * melempar {@code IllegalFormatConversionException} di perangkat.
 */
public class WaiterPerformanceRow {

    /** {@code null} untuk baris order tanpa pelayan tercatat. */
    @Nullable public final Long waiterId;
    @NonNull public final String waiterName;
    public final int ordersCount;
    @NonNull public final Money totalSales;
    @NonNull public final Money avgOrderValue;
    public final int itemsServed;
    public final int tablesServed;

    public WaiterPerformanceRow(@Nullable Long waiterId,
                                @NonNull String waiterName,
                                int ordersCount,
                                @NonNull Money totalSales,
                                @NonNull Money avgOrderValue,
                                int itemsServed,
                                int tablesServed) {
        this.waiterId = waiterId;
        this.waiterName = waiterName;
        this.ordersCount = ordersCount;
        this.totalSales = totalSales;
        this.avgOrderValue = avgOrderValue;
        this.itemsServed = itemsServed;
        this.tablesServed = tablesServed;
    }

    /**
     * Baris "tanpa pelayan" sengaja tidak dibuang oleh server, dan tidak boleh
     * dibuang di sini: justru itu baris yang paling ingin dilihat pemilik.
     */
    public boolean isUnassigned() {
        return waiterId == null;
    }

    @NonNull
    public String displayName() {
        if (!waiterName.isEmpty()) return waiterName;
        return isUnassigned() ? "(tanpa pelayan)" : ("#" + waiterId);
    }

    @Nullable
    public static WaiterPerformanceRow fromJson(@Nullable JSONObject o) {
        if (o == null) return null;
        Long id = o.isNull("waiter_id") ? null : o.optLong("waiter_id");
        return new WaiterPerformanceRow(
                id,
                o.optString("waiter_name", "").trim(),
                o.optInt("orders_count", 0),
                Money.fromJson(o, "total_sales"),
                Money.fromJson(o, "avg_order_value"),
                o.optInt("items_served", 0),
                o.optInt("tables_served", 0)
        );
    }
}
