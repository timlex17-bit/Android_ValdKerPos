package com.valdker.pos.ui.shift;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Online-only version.
 * The offline/Room module has been removed, so all offline methods are no-ops.
 *
 * This class is kept for backward compatibility to avoid breaking older code paths.
 * Calls will not crash at compile-time and will fail gracefully at runtime.
 */
public class ShiftModeManager {

    private static final String TAG = "ShiftModeManager";

    @SuppressWarnings("unused")
    private final Context ctx;

    public ShiftModeManager(@NonNull Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    // ------------------------------------------------------------------
    // Backward-compatible "offline" API (Room removed)
    // ------------------------------------------------------------------

    /**
     * Offline shift is no longer supported.
     */
    @Nullable
    public Object getActiveOfflineShift() {
        Log.w(TAG, "getActiveOfflineShift(): offline module removed (return null)");
        return null;
    }

    /**
     * Offline open is no longer supported.
     */
    @Nullable
    public Object openOffline(double openingCash) {
        Log.w(TAG, "openOffline(): offline module removed (return null)");
        return null;
    }

    /**
     * Offline close is no longer supported.
     */
    @Nullable
    public Object closeOffline(long shiftId, double closingCash) {
        Log.w(TAG, "closeOffline(): offline module removed (return null)");
        return null;
    }

    /**
     * No-op: offline auto close is no longer supported.
     */
    public void closeOfflineShiftIfAny(double closingCash) {
        Log.w(TAG, "closeOfflineShiftIfAny(): offline module removed (no-op)");
    }
}