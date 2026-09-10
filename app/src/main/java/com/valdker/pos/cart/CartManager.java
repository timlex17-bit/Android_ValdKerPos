package com.valdker.pos.cart;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.valdker.pos.money.Money;
import com.valdker.pos.models.CartItem;
import com.valdker.pos.models.Product;

import org.json.JSONArray;
import org.json.JSONObject;

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

    private final Map<String, CartItem> map = new LinkedHashMap<>();
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
        // shopId dan shop_id selalu diisi bersamaan oleh ProductRepository.
        return p.shopId;
    }

    public static synchronized CartManager getInstance(Context context) {
        if (instance == null) instance = new CartManager(context);
        return instance;
    }

    public synchronized void add(@NonNull Product p, int qty) {
        if (qty <= 0) qty = 1;

        int id = (int) parseNumeric(p.id);
        if (id <= 0) {
            Log.w(TAG, "add(): product id invalid.");
            return;
        }

        int shopId = extractShopId(p);

        Log.d(TAG, "add(): productId=" + id
                + ", name=" + safe(p.name)
                + ", extractedShopId=" + shopId);

        if (shopId <= 0) {
            Log.w(TAG, "add(): shop id missing on product. Cart item may become invalid in multi-tenant mode.");
        }

        String name = safe(p.name);
        // Product.sellPrice membawa teks desimal apa adanya dari API
        // ("1.75"); Product.price adalah hasil parse double dari alias yang
        // sama dan dicoba dengan urutan yang sama, jadi keduanya bernilai
        // sama - teksnya saja yang tidak kehilangan presisi.
        Money price = safe(p.sellPrice).isEmpty()
                ? Money.ofDouble(p.price)
                : Money.of(p.sellPrice);
        String imageUrl = safe(firstNonEmpty(p.imageUrl, p.image_url));
        String itemType = CartItem.normalizeItemType(extractItemType(p));
        String key = CartItem.buildCartKey(id, itemType);

        CartItem item = map.get(key);
        if (item == null) {
            item = new CartItem(id, shopId, name, price.toDouble(), imageUrl, qty);
            item.setPrice(price);
            item.orderType = "";
            item.itemType = itemType;
            item.refreshCartKey();
            map.put(item.cartKey, item);
        } else {
            item.qty += qty;

            if (!name.isEmpty()) item.name = name;
            if (price.isPositive()) item.setPrice(price);
            if (!imageUrl.isEmpty()) item.imageUrl = imageUrl;
            if (shopId > 0) item.shopId = shopId;
            if (!itemType.isEmpty()) item.itemType = itemType;

            item.orderType = normalizeTypeOrEmpty(item.orderType);
        }

        saveToPrefs();
        notifyChangedDebounced();
    }

    public synchronized void setQty(int productId, int qty) {
        setQty(productId, CartItem.ITEM_TYPE_PRODUCT, qty);
    }

    public synchronized void setQty(int productId, @NonNull String itemType, int qty) {
        CartItem item = findCartItem(productId, itemType);
        if (item == null) return;

        if (qty <= 0) {
            map.remove(item.cartKey);
        } else {
            item.qty = qty;
        }

        saveToPrefs();
        notifyChangedDebounced();
    }

    public synchronized void setOrderType(int productId, @NonNull String orderType) {
        setOrderType(productId, CartItem.ITEM_TYPE_PRODUCT, orderType);
    }

    public synchronized void setQtyByCartKey(@NonNull String cartKey, int qty) {
        CartItem item = map.get(cartKey);
        if (item == null) return;

        if (qty <= 0) {
            map.remove(item.cartKey);
        } else {
            item.qty = qty;
        }

        saveToPrefs();
        notifyChangedDebounced();
    }

    public synchronized void setOrderType(int productId, @NonNull String itemType, @NonNull String orderType) {
        CartItem item = findCartItem(productId, itemType);
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
        remove(productId, CartItem.ITEM_TYPE_PRODUCT);
    }

    public synchronized void remove(int productId, @NonNull String itemType) {
        CartItem item = findCartItem(productId, itemType);
        if (item != null) {
            map.remove(item.cartKey);
        }
        saveToPrefs();
        notifyChangedDebounced();
    }

    public synchronized void clear() {
        map.clear();
        sp.edit().remove(KEY_CART_JSON).apply();
        notifyChangedDebounced();
    }

    public synchronized void removeByCartKey(@NonNull String cartKey) {
        map.remove(cartKey);
        saveToPrefs();
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

    /** Total keranjang yang eksak. */
    @NonNull
    public synchronized Money getTotal() {
        Money total = Money.zero();
        for (CartItem i : map.values()) total = total.plus(i.lineTotal());
        return total;
    }

    /**
     * @deprecated pakai {@link #getTotal()}. Penjumlahannya sudah eksak;
     * konversi ke double hanya terjadi di akhir.
     */
    @Deprecated
    public synchronized double getTotalAmount() {
        return getTotal().toDouble();
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
                o.put("servicePackageId", i.servicePackageId);
                o.put("cartKey", safe(i.cartKey));
                o.put("shopId", i.shopId);
                o.put("name", safe(i.name));
                o.put("price", i.price);
                o.put("priceDecimal", i.price().toPlainString());
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
        if (item.productId <= 0 && item.servicePackageId <= 0) {
            Log.w(TAG, "add(CartItem): productId invalid.");
            return;
        }

        if (item.qty <= 0) {
            item.qty = 1;
        }

        item.itemType = CartItem.normalizeItemType(item.itemType);
        if (item.servicePackageId > 0) {
            item.itemType = CartItem.ITEM_TYPE_SERVICE;
            if (item.productId <= 0) item.productId = item.servicePackageId;
        }
        item.refreshCartKey();

        CartItem existing = map.get(item.cartKey);
        if (existing == null) {
            map.put(item.cartKey, item);
        } else {
            existing.qty += item.qty;

            if (!safe(item.name).isEmpty()) existing.name = item.name;
            if (item.price().isPositive()) existing.setPrice(item.price());
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
                int servicePackageId = o.optInt("servicePackageId", 0);
                String rawItemType = o.optString("itemType", "");
                String normalizedItemType = normalizeItemType(rawItemType);
                if (id <= 0 && servicePackageId > 0) {
                    id = servicePackageId;
                }
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
                // Keranjang yang tersimpan sebelum migrasi ini hanya punya
                // "price"; kalau ada "priceDecimal" itu yang dipakai.
                String storedDecimal = o.optString("priceDecimal", "");
                if (!storedDecimal.trim().isEmpty()) {
                    item.setPrice(Money.of(storedDecimal));
                }

                item.orderType = normalizeTypeOrEmpty(o.optString("orderType", ""));
                item.itemType = normalizedItemType;
                item.servicePackageId = servicePackageId;
                if (isLegacyPackageType(rawItemType)) {
                    item.itemType = CartItem.ITEM_TYPE_SERVICE;
                    if (item.servicePackageId <= 0) item.servicePackageId = item.productId;
                }
                item.refreshCartKey();

                if (item.qty > 0) map.put(item.cartKey, item);
            }
        } catch (Exception e) {
            Log.e(TAG, "loadFromPrefs(): corrupted cart JSON. Clearing saved cart.", e);
            sp.edit().remove(KEY_CART_JSON).apply();
            map.clear();
        }
    }

    @NonNull
    private String normalizeItemType(@Nullable String t) {
        if (t == null) return CartItem.ITEM_TYPE_PRODUCT;

        String v = t.trim().toLowerCase(Locale.US);
        if (ITEM_TYPE_SERVICE.equals(v)) return CartItem.ITEM_TYPE_SERVICE;
        if (isLegacyPackageType(v)) return CartItem.ITEM_TYPE_SERVICE;
        if (ITEM_TYPE_SPAREPART.equals(v) || "part".equals(v)) return CartItem.ITEM_TYPE_PART;
        return CartItem.ITEM_TYPE_PRODUCT;
    }

    @NonNull
    private String extractItemType(@NonNull Product p) {
        String raw = safe(p.itemType).toLowerCase(Locale.US);

        if (ITEM_TYPE_SERVICE.equals(raw)) return CartItem.ITEM_TYPE_SERVICE;
        if (isLegacyPackageType(raw)) return CartItem.ITEM_TYPE_SERVICE;
        if (ITEM_TYPE_SPAREPART.equals(raw) || "part".equals(raw)) return CartItem.ITEM_TYPE_PART;
        return CartItem.ITEM_TYPE_PRODUCT;
    }

    @Nullable
    private CartItem findCartItem(int productId, @NonNull String itemType) {
        CartItem item = map.get(CartItem.buildCartKey(productId, itemType));
        if (item != null) return item;

        String normalizedType = CartItem.normalizeItemType(itemType);
        for (CartItem candidate : map.values()) {
            if (candidate.servicePackageId > 0
                    && candidate.servicePackageId == productId
                    && CartItem.ITEM_TYPE_SERVICE.equals(normalizedType)) {
                return candidate;
            }
            if (candidate.productId == productId
                    && CartItem.normalizeItemType(candidate.itemType).equals(normalizedType)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean isLegacyPackageType(@Nullable String value) {
        if (value == null) return false;
        String clean = value.trim().toLowerCase(Locale.US);
        return "service_package".equals(clean)
                || "servicepackage".equals(clean)
                || "package".equals(clean);
    }

    @NonNull
    private String firstNonEmpty(@Nullable String a, @Nullable String b) {
        String first = safe(a);
        return first.isEmpty() ? safe(b) : first;
    }

    /**
     * Membaca angka dari nilai yang bisa saja datang sebagai teks berformat
     * ("$1,750", "1750 USD"). Menggantikan pembacaan lewat refleksi yang dulu
     * dipakai di sini: nama field ikut diobfuscate begitu R8 menyala, dan
     * seluruh harga akan diam-diam terbaca 0.00.
     */
    private double parseNumeric(@Nullable String raw) {
        String s = safe(raw)
                .replace("$", "")
                .replace("USD", "")
                .replace("usd", "")
                .replace(",", "")
                .trim();
        if (s.isEmpty()) return 0.0;
        try {
            return Double.parseDouble(s);
        } catch (Exception ignored) {
            return 0.0;
        }
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
