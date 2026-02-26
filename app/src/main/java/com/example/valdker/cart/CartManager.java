package com.example.valdker.cart;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.valdker.models.CartItem;
import com.example.valdker.models.Product;

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

    private static CartManager instance;

    private final Map<String, CartItem> map = new LinkedHashMap<>();
    private final SharedPreferences sp;

    public interface Listener {
        void onCartChanged();
    }

    private final List<Listener> listeners = new ArrayList<>();

    private CartManager(Context context) {
        sp = context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
        loadFromPrefs();
    }

    public static synchronized CartManager getInstance(Context context) {
        if (instance == null) instance = new CartManager(context);
        return instance;
    }

    public synchronized void add(@NonNull Product p, int qty) {
        if (qty <= 0) qty = 1;

        String id = safe(extractAnyString(p, "id", "productId", "uuid"));
        if (id.isEmpty()) {
            Log.w(TAG, "add(): product id is empty. Cart will not update. Check Product model fields.");
            return;
        }

        String name = safe(extractAnyString(p, "name", "title", "product_name"));
        double price = extractAnyDouble(p, "price", "selling_price", "sell_price", "sale_price", "unit_price",
                "price_usd", "usd_price", "amount");
        String imageUrl = safe(extractAnyString(p, "imageUrl", "image_url", "image", "photo", "thumbnail", "icon_url"));

        CartItem item = map.get(id);
        if (item == null) {
            item = new CartItem(id, name, price, imageUrl, qty);
            item.orderType = ""; // ✅ UNSET by default
            map.put(id, item);
        } else {
            item.qty += qty;

            if (!name.isEmpty()) item.name = name;
            if (price > 0) item.price = price;
            if (!imageUrl.isEmpty()) item.imageUrl = imageUrl;

            // keep existing type, only normalize if already set
            item.orderType = normalizeTypeOrEmpty(item.orderType);
        }

        saveToPrefs();
        notifyChanged();
    }

    public synchronized void setQty(@NonNull String productId, int qty) {
        String id = safe(productId);
        if (id.isEmpty()) return;

        CartItem item = map.get(id);
        if (item == null) return;

        if (qty <= 0) {
            map.remove(id);
        } else {
            item.qty = qty;
        }

        saveToPrefs();
        notifyChanged();
    }

    /**
     * orderType can be:
     * - TYPE_DINE_IN / TYPE_TAKE_OUT / TYPE_DELIVERY
     * - "" (UNSET)
     */
    public synchronized void setOrderType(@NonNull String productId, @NonNull String orderType) {
        String id = safe(productId);
        if (id.isEmpty()) return;

        CartItem item = map.get(id);
        if (item == null) return;

        item.orderType = normalizeTypeOrEmpty(orderType);

        saveToPrefs();
        notifyChanged();
    }

    /**
     * Set type for all items; allow "" to UNSET all.
     */
    public synchronized void setAllOrderType(@NonNull String orderType) {
        String t = normalizeTypeOrEmpty(orderType);

        for (CartItem item : map.values()) {
            item.orderType = t;
        }

        saveToPrefs();
        notifyChanged();
    }

    /**
     * FINAL RULE:
     * - if mixed types -> GENERAL
     * - if all same -> that type
     * - if empty or all UNSET -> GENERAL
     *
     * IMPORTANT: UNSET ("") is ignored (NOT treated as TAKE_OUT)
     */
    public synchronized String resolveOrderTypeForBackend() {
        if (map.isEmpty()) return "GENERAL";

        boolean hasTakeOut = false;
        boolean hasDineIn = false;
        boolean hasDelivery = false;

        for (CartItem it : map.values()) {
            String raw = (it.orderType == null) ? "" : it.orderType.trim().toUpperCase(Locale.US);
            if (raw.isEmpty()) continue; // ✅ UNSET -> ignore

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

    public synchronized void remove(@NonNull String productId) {
        String id = safe(productId);
        if (id.isEmpty()) return;

        map.remove(id);
        saveToPrefs();
        notifyChanged();
    }

    public synchronized void clear() {
        map.clear();
        sp.edit().remove(KEY_CART_JSON).apply();
        notifyChanged();
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
        notifyChanged();
    }

    public synchronized void addListener(@NonNull Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    public synchronized void removeListener(@NonNull Listener l) {
        listeners.remove(l);
    }

    private void notifyChanged() {
        List<Listener> copy;
        synchronized (this) {
            copy = new ArrayList<>(listeners);
        }
        for (Listener l : copy) {
            try {
                l.onCartChanged();
            } catch (Exception ignored) {}
        }
    }

    private void saveToPrefs() {
        try {
            JSONArray arr = new JSONArray();
            for (CartItem i : map.values()) {
                JSONObject o = new JSONObject();
                o.put("productId", safe(i.productId));
                o.put("name", safe(i.name));
                o.put("price", i.price);
                o.put("imageUrl", safe(i.imageUrl));
                o.put("qty", i.qty);

                // ✅ persist EXACT value (can be "")
                o.put("orderType", normalizeTypeOrEmpty(i.orderType));

                arr.put(o);
            }
            sp.edit().putString(KEY_CART_JSON, arr.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "saveToPrefs(): failed to save cart JSON", e);
        }
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

                String id = safe(o.optString("productId", ""));
                if (id.isEmpty()) continue;

                CartItem item = new CartItem(
                        id,
                        o.optString("name", ""),
                        o.optDouble("price", 0.0),
                        o.optString("imageUrl", ""),
                        o.optInt("qty", 0)
                );

                // ✅ restore UNSET allowed
                item.orderType = normalizeTypeOrEmpty(o.optString("orderType", ""));

                if (item.qty > 0) map.put(id, item);
            }
        } catch (Exception e) {
            Log.e(TAG, "loadFromPrefs(): corrupted cart JSON. Clearing saved cart.", e);
            sp.edit().remove(KEY_CART_JSON).apply();
            map.clear();
        }
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
            } catch (Throwable ignored) {}

            try {
                Field f = c.getDeclaredField(key);
                f.setAccessible(true);
                Object v = f.get(obj);
                if (v != null) return v;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    /**
     * Normalize with UNSET support:
     * - null/empty -> ""
     * - valid -> valid constant
     * - invalid -> ""
     */
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
