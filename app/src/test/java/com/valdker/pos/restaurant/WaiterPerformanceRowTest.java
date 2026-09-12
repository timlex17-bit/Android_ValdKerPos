package com.valdker.pos.restaurant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

/**
 * Uang di laporan pelayan datang sebagai string dua desimal. Uji ini mengunci
 * dua hal yang pernah gagal di perangkat sebelumnya: nilainya harus melewati
 * BigDecimal tanpa mampir ke double, dan kolom yang bertipe String tidak boleh
 * diformat dengan {@code %.2f} - itu lolos kompilasi lalu melempar
 * IllegalFormatConversionException saat layarnya dibuka.
 */
public class WaiterPerformanceRowTest {

    private static JSONObject row(String waiterId,
                                  String name,
                                  String totalSales,
                                  String avg) throws Exception {
        JSONObject o = new JSONObject();
        if (waiterId == null) {
            o.put("waiter_id", JSONObject.NULL);
        } else {
            o.put("waiter_id", Long.parseLong(waiterId));
        }
        o.put("waiter_name", name);
        o.put("orders_count", 2);
        o.put("total_sales", totalSales);
        o.put("avg_order_value", avg);
        o.put("items_served", 5);
        o.put("tables_served", 2);
        return o;
    }

    @Test
    public void parsesMoneyExactly() throws Exception {
        WaiterPerformanceRow r = WaiterPerformanceRow.fromJson(
                row("2", "Ana Waiter", "25.00", "12.50"));
        assertNotNull(r);
        assertEquals("25.00", r.totalSales.toPlainString());
        assertEquals("12.50", r.avgOrderValue.toPlainString());
    }

    /**
     * 0.1 + 0.2 lewat double menghasilkan 0.30000000000000004. Lewat Money
     * ia tetap 0.30, dan itulah alasan kolom ini tidak boleh disentuh double.
     */
    @Test
    public void moneyDoesNotDriftTheWayDoubleWould() throws Exception {
        WaiterPerformanceRow a = WaiterPerformanceRow.fromJson(
                row("2", "A", "0.10", "0.10"));
        WaiterPerformanceRow b = WaiterPerformanceRow.fromJson(
                row("3", "B", "0.20", "0.20"));
        assertNotNull(a);
        assertNotNull(b);
        assertEquals("0.30", a.totalSales.plus(b.totalSales).toPlainString());
    }

    @Test
    public void keepsTheUnassignedRow() throws Exception {
        // Baris waiter_id=null sengaja tidak dibuang server, dan tidak boleh
        // dibuang klien: justru itu baris yang paling ingin dilihat pemilik.
        WaiterPerformanceRow r = WaiterPerformanceRow.fromJson(
                row(null, "", "8.00", "8.00"));
        assertNotNull(r);
        assertNull(r.waiterId);
        assertTrue(r.isUnassigned());
        assertEquals("8.00", r.totalSales.toPlainString());
    }

    @Test
    public void unassignedRowStillHasAReadableLabel() throws Exception {
        WaiterPerformanceRow r = WaiterPerformanceRow.fromJson(row(null, "", "0.00", "0.00"));
        assertNotNull(r);
        assertFalse(r.displayName().isEmpty());
    }

    @Test
    public void namedRowPrefersTheServerName() throws Exception {
        WaiterPerformanceRow r = WaiterPerformanceRow.fromJson(
                row("2", "Ana Waiter", "1.00", "1.00"));
        assertNotNull(r);
        assertEquals("Ana Waiter", r.displayName());
    }

    @Test
    public void missingMoneyFieldsBecomeZeroRatherThanCrash() throws Exception {
        JSONObject o = new JSONObject();
        o.put("waiter_id", 9L);
        o.put("waiter_name", "No money fields");
        WaiterPerformanceRow r = WaiterPerformanceRow.fromJson(o);
        assertNotNull(r);
        assertEquals("0.00", r.totalSales.toPlainString());
        assertEquals("0.00", r.avgOrderValue.toPlainString());
    }

    /**
     * Bentuk yang benar untuk menampilkan uang: string apa adanya atau
     * Money.format(). Uji ini ada supaya siapa pun yang nanti menulis layar
     * laporannya punya contoh yang tidak memakai %f.
     */
    @Test
    public void formattingUsesTheStringNotAFloatConversion() throws Exception {
        WaiterPerformanceRow r = WaiterPerformanceRow.fromJson(
                row("2", "Ana", "25.00", "12.50"));
        assertNotNull(r);
        String line = String.format("%s: %s", r.displayName(), r.totalSales.toPlainString());
        assertEquals("Ana: 25.00", line);
    }
}
