package com.example.valdker.ui.shift;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.valdker.offline.repo.ShiftRepository;
import com.example.valdker.offline.db.entities.ShiftEntity;

public class ShiftModeManager {

    private static final String TAG = "ShiftModeManager";

    private final Context ctx;

    public ShiftModeManager(@NonNull Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    public ShiftEntity getActiveOfflineShift() {
        return new ShiftRepository(ctx).getActiveShift();
    }

    public ShiftEntity openOffline(double openingCash) {
        return new ShiftRepository(ctx).openShift(openingCash);
    }

    public ShiftEntity closeOffline(long shiftId, double closingCash) {
        return new ShiftRepository(ctx).closeShift(shiftId, closingCash);
    }

    /**
     * ✅ AUTO CLOSE OFFLINE SHIFT
     * Dipanggil setelah ONLINE close sukses.
     */
    public void closeOfflineShiftIfAny(double closingCash) {
        try {
            ShiftRepository repo = new ShiftRepository(ctx);
            ShiftEntity active = repo.getActiveShift();

            if (active == null) return;

            if (!active.closed) {
                repo.closeShift(active.id, closingCash);

                Log.i(TAG, "Offline shift auto-closed. id=" + active.id);
            }

        } catch (Exception e) {
            Log.w(TAG, "closeOfflineShiftIfAny failed: " + e.getMessage());
        }
    }
}