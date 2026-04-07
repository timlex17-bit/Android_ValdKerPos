package com.valdker.pos.cart;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.valdker.pos.models.CartItem;
import com.valdker.pos.models.Product;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CartManager {

    private static final String TAG = "CART_MANAGER";

    private static final String PREF = "valdker_cart";
    private static final String KEY_CART_JSON = "cart_json";

    public static final String TYPE_DINE_IN = "DINE_IN";
    public static final String TYPE_TAKE_OUT = "TAKE_OUT";
    public static final String TYPE_DELIVERY = "DELIVERY";
    public static final String TYPE_GENERAL = "GENERAL";
    public static final String ITEM_TYPE_PRODUCT = "product";
    public static final String ITEM_TYPE_SERVICE = "service";
    public static final String ITEM_TYPE_SPAREPART = "sparepart";

    private static CartManager instance;

    private final Map<Integer, CartItem> map = new LinkedHashMap<>();
    private final SharedPreferences sp;

    public interface Listener {
        void onCartChanged();
    }

    private final List<Listener> listeners = new ArrayList<>();

    // Dispatch notifications on main thread + debounce to avoid UI jank
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean notifyScheduled = false;

    private CartManager(Context context) {
        sp = context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
        loadFromPrefs();
    }

    private int extractShopId(@NonNull Product p) {
        return (int) extractAnyDouble(p,
                "shopId",
                "shop_id"
        );
    }

    public static synchronized CartManager getInstance(Context context) {
        if (instance == null) instance = new CartManager(context);
        return instance;
    }

    public synchronized void add(@NonNull Product p, int qty) {
        if (qty <= 0) qty = 1;

        int id = (int) extractAnyDouble(p, "id", "productId");
        if (id <= 0) {
            Log.w(TAG, "add(): product id invalid.");
            return;
        }

        int shopId = extractShopId(p);

        Log.d(TAG, "add(): productId=" + id
                + ", name=" + safe(extractAnyString(p, "name", "title", "product_name"))
                + ", extractedShopId=" + shopId);

        if (shopId <= 0) {
            Log.w(TAG, "add(): shop id missing on product. Cart item may become invalid in multi-tenant mode.");
        }

        String name = safe(extractAnyString(p, "name", "title", "product_name"));
        double price = extractAnyDouble(p, "price", "selling_price", "sell_price", "sale_price", "unit_price",
                "price_usd", "usd_price", "amount");
        String imageUrl = safe(extractAnyString(p, "imageUrl", "image_url", "image", "photo", "thumbnail", "icon_url"));
        String itemType = extractItemType(p);

        CartItem item = map.get(id);
        if (item == null) {
            item = new CartItem(id, shopId, name, price, imageUrl, qty);
            item.orderType = "";
            item.itemType = itemType;
            map.put(id, item);
        } else {
            item.qty += qty;

            if (!name.isEmpty()) item.name = name;
            if (price > 0) item.price = price;
            if (!imageUrl.isEmpty()) item.imageUrl = imageUrl;
            if (shopId > 0) item.shopId = shopId;
            if (!itemType.isEmpty()) item.itemType = itemType;

            item.orderType = normalizeTypeOrEmpty(item.orderType);
        }

        saveToPrefs();
        notifyChangedDebounced();
    }

    public synchronized void setQty(int productId, int qty) {
        CartItem item = map.get(productId);
        if (item == null) return;

        if (qty <= 0) {
            map.remove(productId);
        } else {
            item.qty = qty;
        }

        saveToPrefs();
        notifyChangedDebounced();
    }

    public synchronized void setOrderType(int productId, @NonNull String orderType) {
        CartItem item = map.get(productId);
        if (item == null) return;

        item.orderType = normalizeTypeOrEmpty(orderType);

        saveToPrefs();
        notifyChangedDebounced();
    }

    public synchronized void setAllOrderType(@NonNull String orderType) {
        String t = normalizeTypeOrEmpty(orderType);

        for (CartItem item : map.values()) {
            item.orderType = t;
        }

        saveToPrefs();
        notifyChangedDebounced();
    }

    public synchronized String resolveOrderTypeForBackend() {
        if (map.isEmpty()) return "GENERAL";

        boolean hasTakeOut = false;
        boolean hasDineIn = false;
        boolean hasDelivery = false;

        for (CartItem it : map.values()) {
            String raw = (it.orderType == null) ? "" : it.orderType.trim().toUpperCase(Locale.US);
            if (raw.isEmpty()) continue; // UNSET ignored

            if (TYPE_TAKE_OUT.equals(raw)) hasTakeOut = true;
            else if (TYPE_DINE_IN.equals(raw)) hasDineIn = true;
            else if (TYPE_DELIVERY.equals(raw)) hasDelivery = true;
        }

        int count = (hasTakeOut ? 1 : 0) + (hasDineIn ? 1 : 0) + (hasDelivery ? 1 : 0);

        if (count > 1) return "GENERAL";
        if (hasTakeOut) return TYPE_TAKE_OUT;
        if (hasDineIn) return TYPE_DINE_IN;
        if (hasDelivery) return TYPE_DELIVERY;

        return "GENERAL";
    }

    public synchronized void remove(int productId) {
        map.remove(productId);
        saveToPrefs();
        notifyChangedDebounced();
    }

    public synchronized void clear() {
        map.clear();
        sp.edit().remove(KEY_CART_JSON).apply();
        notifyChangedDebounced();
    }

    @NonNull
    public synchronized List<CartItem> getItems() {
        return new ArrayList<>(map.values());
    }

    public synchronized int getTotalQty() {
        int total = 0;
        for (CartItem i : map.values()) total += Math.max(0, i.qty);
        return total;
    }

    public synchronized double getTotalAmount() {
        double total = 0.0;
        for (CartItem i : map.values()) total += (i.price * i.qty);
        return total;
    }

    public synchronized void reload() {
        loadFromPrefs();
        notifyChangedDebounced();
    }

    public synchronized void addListener(@NonNull Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    public synchronized boolean belongsToShop(int activeShopId) {
        if (activeShopId <= 0) return false;

        for (CartItem item : map.values()) {
            if (item.shopId <= 0) {
                return false;
            }

            if (item.shopId != activeShopId) {
                return false;
            }
        }
        return true;
    }

    public synchronized boolean clearIfDifferentShop(int activeShopId) {
        if (activeShopId <= 0) return false;

        boolean hasMismatch = false;
        for (CartItem item : map.values()) {
            if (item.shopId <= 0 || item.shopId != activeShopId) {
                hasMismatch = true;
                break;
            }
        }

        if (hasMismatch) {
            clear();
            return true;
        }
        return false;
    }

    public synchronized void removeListener(@NonNull Listener l) {
        listeners.remove(l);
    }

    /**
     * Debounced notify:
     * - Always dispatch on main thread
     * - Coalesce multiple updates in the same frame
     */
    private void notifyChangedDebounced() {
        if (notifyScheduled) return;
        notifyScheduled = true;

        mainHandler.post(() -> {
            List<Listener> copy;
            synchronized (CartManager.this) {
                notifyScheduled = false;
                copy = new ArrayList<>(listeners);
            }

            for (Listener l : copy) {
                try {
                    l.onCartChanged();
                } catch (Exception ignored) {
                }
            }
        });
    }

    private void saveToPrefs() {
        try {
            JSONArray arr = new JSONArray();
            for (CartItem i : map.values()) {
                JSONObject o = new JSONObject();
                o.put("productId", i.productId);
                o.put("shopId", i.shopId);
                o.put("name", safe(i.name));
                o.put("price", i.price);
                o.put("imageUrl", safe(i.imageUrl));
                o.put("qty", i.qty);
                o.put("orderType", normalizeTypeOrEmpty(i.orderType));
                o.put("itemType", safe(i.itemType));
                arr.put(o);
            }
            sp.edit().putString(KEY_CART_JSON, arr.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "saveToPrefs(): failed to save cart JSON", e);
        }
    }

    public synchronized void add(@NonNull CartItem item) {
        if (item.productId <= 0) {
            Log.w(TAG, "add(CartItem): productId invalid.");
            return;
        }

        if (item.qty <= 0) {
            item.qty = 1;
        }

        item.itemType = CartItem.normalizeItemType(item.itemType);

        CartItem existing = map.get(item.productId);
        if (existing == null) {
            map.put(item.productId, item);
        } else {
            existing.qty += item.qty;

            if (!safe(item.name).isEmpty()) existing.name = item.name;
            if (item.price > 0) existing.price = item.price;
            if (!safe(item.imageUrl).isEmpty()) existing.imageUrl = item.imageUrl;
            if (item.shopId > 0) existing.shopId = item.shopId;
            if (!safe(item.orderType).isEmpty()) existing.orderType = normalizeTypeOrEmpty(item.orderType);
            if (!safe(item.itemType).isEmpty()) existing.itemType = CartItem.normalizeItemType(item.itemType);
        }

        saveToPrefs();
        notifyChangedDebounced();
    }

    private void loadFromPrefs() {
        map.clear();

        String raw = sp.getString(KEY_CART_JSON, "");
        if (raw == null || raw.trim().isEmpty()) return;

        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;

                int id = o.optInt("productId", 0);
                if (id <= 0) continue;

                int shopId = o.optInt("shopId", 0);

                CartItem item = new CartItem(
                        id,
                        shopId,
                        o.optString("name", ""),
                        o.optDouble("price", 0.0),
                        o.optString("imageUrl", ""),
                        o.optInt("qty", 0)
                );

                item.orderType = normalizeTypeOrEmpty(o.optString("orderType", ""));
                item.itemType = normalizeItemType(o.optString("itemType", ""));

                if (item.qty > 0) map.put(id, item);
            }
        } catch (Exception e) {
            Log.e(TAG, "loadFromPrefs(): corrupted cart JSON. Clearing saved cart.", e);
            sp.edit().remove(KEY_CART_JSON).apply();
            map.clear();
        }
    }

    @NonNull
    private String normalizeItemType(@Nullable String t) {
        if (t == null) return ITEM_TYPE_PRODUCT;

        String v = t.trim().toLowerCase(Locale.US);
        if (ITEM_TYPE_SERVICE.equals(v)) return ITEM_TYPE_SERVICE;
        if (ITEM_TYPE_SPAREPART.equals(v)) return ITEM_TYPE_SPAREPART;
        return ITEM_TYPE_PRODUCT;
    }

    @NonNull
    private String extractItemType(@NonNull Product p) {
        String raw = safe(extractAnyString(p,
                "itemType",
                "item_type",
                "type"
        )).toLowerCase(Locale.US);

        if (ITEM_TYPE_SERVICE.equals(raw)) return ITEM_TYPE_SERVICE;
        if (ITEM_TYPE_SPAREPART.equals(raw)) return ITEM_TYPE_SPAREPART;
        return ITEM_TYPE_PRODUCT;
    }

    private String extractAnyString(@NonNull Object obj, @NonNull String... keys) {
        Object v = extractAnyField(obj, keys);
        return v == null ? "" : String.valueOf(v);
    }

    private double extractAnyDouble(@NonNull Object obj, @NonNull String... keys) {
        Object v = extractAnyField(obj, keys);
        if (v == null) return 0.0;
        if (v instanceof Number) return ((Number) v).doubleValue();

        try {
            String s = String.valueOf(v).trim()
                    .replace("$", "")
                    .replace("USD", "")
                    .replace("usd", "")
                    .replace(",", "")
                    .trim();
            if (s.isEmpty()) return 0.0;
            return Double.parseDouble(s);
        } catch (Exception ignored) {
            return 0.0;
        }
    }

    private Object extractAnyField(@NonNull Object obj, @NonNull String... keys) {
        Class<?> c = obj.getClass();

        for (String key : keys) {
            if (key == null) continue;

            try {
                Field f = c.getField(key);
                f.setAccessible(true);
                Object v = f.get(obj);
                if (v != null) return v;
            } catch (Throwable ignored) {
            }

            try {
                Field f = c.getDeclaredField(key);
                f.setAccessible(true);
                Object v = f.get(obj);
                if (v != null) return v;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private String normalizeTypeOrEmpty(@Nullable String t) {
        if (t == null) return "";
        String v = t.trim().toUpperCase(Locale.US);
        if (v.isEmpty()) return "";
        if (TYPE_DINE_IN.equals(v)) return TYPE_DINE_IN;
        if (TYPE_TAKE_OUT.equals(v)) return TYPE_TAKE_OUT;
        if (TYPE_DELIVERY.equals(v)) return TYPE_DELIVERY;
        return "";
    }

    private String safe(@Nullable String s) {
        if (s == null) return "";
        String v = s.trim();
        if ("null".equalsIgnoreCase(v)) return "";
        return v;
    }
}