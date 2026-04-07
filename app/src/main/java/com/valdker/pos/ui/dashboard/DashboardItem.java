package com.valdker.pos.ui.dashboard;

import androidx.annotation.NonNull;

/**
 * Simple model for a dashboard grid item.
 * Holds the unique id, UI texts, and icon resource.
 */
public class DashboardItem {

    // Core modules
    public static final int ID_CUSTOMERS = 1;
    public static final int ID_SUPPLIERS = 2;
    public static final int ID_PRODUCTS  = 3;
    public static final int ID_POS       = 4;
    public static final int ID_EXPENSE   = 5;
    public static final int ID_ORDERS    = 6;
    public static final int ID_REPORTS   = 7;
    public static final int ID_SETTINGS  = 8;

    // Extra modules (use high values to avoid conflicts with legacy ids)
    public static final int ID_CATEGORIES = 9001;
    public static final int ID_UNITS      = 9002;
    public static final int ID_PRODUCT_RETURNS   = 11;
    public static final int ID_INVENTORY_COUNTS  = 12;
    public static final int ID_STOCK_ADJUSTMENTS = 13;
    public static final int ID_STOCK_MOVEMENTS   = 14;
    public static final int ID_BANK_ACCOUNTS = 15;
    public static final int ID_PURCHASES = 16;

    public final int id;
    @NonNull public final String title;
    @NonNull public final String subtitle;
    public final int iconRes;

    public DashboardItem(int id, @NonNull String title, @NonNull String subtitle, int iconRes) {
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.iconRes = iconRes;
    }
}
