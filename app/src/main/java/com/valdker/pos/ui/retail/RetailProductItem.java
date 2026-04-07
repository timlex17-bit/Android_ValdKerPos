package com.valdker.pos.ui.retail;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

public class RetailProductItem {

    public long id;
    public String name = "";
    public String sku = "";
    public String barcode = "";
    public String imageUrl = "";
    public double price = 0d;
    public double stock = 0d;
    public long categoryId = 0L;
    public String categoryName = "";
    public boolean trackStock = false;
    public boolean active = true;
    public String description = "";

    public RetailProductItem() {
    }

    @NonNull
    public static RetailProductItem fromJson(@Nullable JSONObject o) {
        RetailProductItem item = new RetailProductItem();
        if (o == null) return item;

        item.id = optLong(o, "id");
        item.name = optString(o, "name");
        item.sku = optString(o, "sku");
        item.barcode = optString(o, "barcode");
        item.imageUrl = firstNonEmpty(
                optString(o, "image_url"),
                optString(o, "imageUrl"),
                optString(o, "photo"),
                optString(o, "thumbnail")
        );
        item.price = optDouble(o, "price");
        if (item.price <= 0d) {
            item.price = firstPositive(
                    optDouble(o, "sell_price"),
                    optDouble(o, "selling_price"),
                    optDouble(o, "unit_price")
            );
        }

        item.stock = firstPositiveOrZero(
                optDouble(o, "stock"),
                optDouble(o, "current_stock"),
                optDouble(o, "qty")
        );

        item.categoryId = firstPositiveLong(
                optLong(o, "category"),
                optLong(o, "category_id")
        );

        item.categoryName = firstNonEmpty(
                optString(o, "category_name"),
                optString(o, "categoryName")
        );

        item.trackStock = optBoolean(o, "track_stock", false);
        item.active = optBoolean(o, "is_active", true);
        item.description = optString(o, "description");

        return item;
    }

    public boolean matchesCategory(long selectedCategoryId) {
        return selectedCategoryId <= 0L || selectedCategoryId == -1L || categoryId == selectedCategoryId;
    }

    public boolean matchesQuery(@Nullable String rawQuery) {
        String q = normalize(rawQuery);
        if (q.isEmpty()) return true;

        return normalize(name).contains(q)
                || normalize(sku).contains(q)
                || normalize(barcode).contains(q)
                || normalize(categoryName).contains(q)
                || normalize(description).contains(q);
    }

    public boolean matchesBarcode(@Nullable String rawBarcode) {
        String q = normalize(rawBarcode);
        if (q.isEmpty()) return false;

        return normalize(barcode).equals(q)
                || normalize(sku).equals(q);
    }

    @NonNull
    public String getSubtitle() {
        String safeSku = sku == null || sku.trim().isEmpty() ? "-" : sku.trim();
        return "SKU: " + safeSku;
    }

    private static long optLong(@NonNull JSONObject o, @NonNull String key) {
        try {
            return o.optLong(key, 0L);
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static double optDouble(@NonNull JSONObject o, @NonNull String key) {
        try {
            return o.optDouble(key, 0d);
        } catch (Exception ignored) {
            return 0d;
        }
    }

    private static boolean optBoolean(@NonNull JSONObject o, @NonNull String key, boolean fallback) {
        try {
            if (!o.has(key) || o.isNull(key)) return fallback;
            return o.optBoolean(key, fallback);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    @NonNull
    private static String optString(@NonNull JSONObject o, @NonNull String key) {
        try {
            String value = o.optString(key, "");
            return value == null ? "" : value.trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    @NonNull
    private static String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) {
                return v.trim();
            }
        }
        return "";
    }

    private static double firstPositive(double... values) {
        if (values == null) return 0d;
        for (double v : values) {
            if (v > 0d) return v;
        }
        return 0d;
    }

    private static double firstPositiveOrZero(double... values) {
        if (values == null || values.length == 0) return 0d;
        for (double v : values) {
            if (v >= 0d) return v;
        }
        return 0d;
    }

    private static long firstPositiveLong(long... values) {
        if (values == null) return 0L;
        for (long v : values) {
            if (v > 0L) return v;
        }
        return 0L;
    }

    @NonNull
    private static String normalize(@Nullable String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}