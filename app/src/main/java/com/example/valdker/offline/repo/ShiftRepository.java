package com.example.valdker.offline.repo;

import android.content.Context;

import androidx.annotation.NonNull;

import com.example.valdker.SessionManager;
import com.example.valdker.offline.db.ValdkerDatabase;
import com.example.valdker.offline.db.entities.ShiftEntity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ShiftRepository {

    private final Context ctx;
    private final ValdkerDatabase db;

    public ShiftRepository(@NonNull Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.db = ValdkerDatabase.get(this.ctx);
    }

    private String nowIso() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(new Date());
    }

    public ShiftEntity getActiveShift() {
        return db.shiftDao().getActiveShift();
    }

    public ShiftEntity openShift(double openingCash) {
        ShiftEntity active = db.shiftDao().getActiveShift();
        if (active != null) {
            throw new IllegalStateException("Shift masih aktif. Tutup dulu sebelum buka shift baru.");
        }

        SessionManager sm = new SessionManager(ctx);
        String cashier = sm.getUsername();
        if (cashier == null || cashier.trim().isEmpty()) cashier = "-";

        ShiftEntity s = new ShiftEntity(cashier, nowIso(), Math.max(0, openingCash));
        long id = db.shiftDao().insert(s);
        return db.shiftDao().getById(id);
    }

    /**
     * Close shift:
     * - hitung summary dari orders yang punya shift_local_id = shift.id
     * - expected_cash = opening_cash + cash_sales
     * - simpan closing_cash input user
     */
    public ShiftEntity closeShift(long shiftId, double closingCash) {
        ShiftEntity s = db.shiftDao().getById(shiftId);
        if (s == null) throw new IllegalStateException("Shift not found");
        if (s.closed) throw new IllegalStateException("Shift sudah ditutup");

        // summary from orders table (lihat query di OrderDao section bawah)
        ShiftSummary sum = db.orderDao().computeShiftSummary(shiftId);

        double cashSales = sum == null ? 0 : sum.cash_sales;
        double nonCash = sum == null ? 0 : sum.non_cash_sales;
        int ordersCount = sum == null ? 0 : sum.orders_count;

        s.cash_sales = cashSales;
        s.non_cash_sales = nonCash;
        s.orders_count = ordersCount;

        s.expected_cash = s.opening_cash + cashSales;
        s.closing_cash = Math.max(0, closingCash);

        s.closed = true;
        s.closed_at_iso = nowIso();

        db.shiftDao().update(s);
        return db.shiftDao().getById(shiftId);
    }

    // ===== DTO summary =====
    public static class ShiftSummary {
        public double cash_sales;
        public double non_cash_sales;
        public int orders_count;
    }
}