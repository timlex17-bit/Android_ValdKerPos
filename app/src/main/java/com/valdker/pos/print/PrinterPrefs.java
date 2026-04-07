package com.valdker.pos.print;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class PrinterPrefs {

    private static final String SP = "printer_prefs";
    private static final String K_NAME = "printer_name";
    private static final String K_MAC = "printer_mac";
    private static final String K_AUTO = "printer_auto_print";
    private static final String K_WIDTH = "printer_paper_width"; // "58" or "80"

    private PrinterPrefs() {}

    private static SharedPreferences sp(@NonNull Context c) {
        return c.getApplicationContext().getSharedPreferences(SP, Context.MODE_PRIVATE);
    }

    public static void setSelected(@NonNull Context c, @NonNull String name, @NonNull String mac) {
        sp(c).edit()
                .putString(K_NAME, name)
                .putString(K_MAC, mac)
                .apply();
    }

    @Nullable
    public static String getName(@NonNull Context c) {
        return sp(c).getString(K_NAME, null);
    }

    @Nullable
    public static String getMac(@NonNull Context c) {
        return sp(c).getString(K_MAC, null);
    }

    public static void clear(@NonNull Context c) {
        sp(c).edit()
                .remove(K_NAME)
                .remove(K_MAC)
                .apply();
    }

    // ✅ Auto Print
    public static void setAutoPrint(@NonNull Context c, boolean enabled) {
        sp(c).edit().putBoolean(K_AUTO, enabled).apply();
    }

    public static boolean isAutoPrintEnabled(@NonNull Context c) {
        return sp(c).getBoolean(K_AUTO, true); // default ON
    }

    // ✅ Paper Width
    public static void setPaperWidthMm(@NonNull Context c, int mm) {
        sp(c).edit().putString(K_WIDTH, String.valueOf(mm)).apply();
    }

    public static int getPaperWidthMm(@NonNull Context c) {
        String v = sp(c).getString(K_WIDTH, "58");
        try { return Integer.parseInt(v); } catch (Exception ignored) { return 58; }
    }
}
