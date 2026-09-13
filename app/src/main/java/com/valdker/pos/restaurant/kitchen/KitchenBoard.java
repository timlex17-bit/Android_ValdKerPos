package com.valdker.pos.restaurant.kitchen;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Isi satu respons {@code GET /api/restaurant/kitchen/}.
 *
 * <p>Bentuknya mengikuti kontrak apa adanya: daftar order, tiap order membawa
 * nama meja dan nama pelayan yang sudah disertakan server, jadi papan tidak
 * pernah perlu request kedua hanya untuk menampilkan "Meja A1".
 */
public final class KitchenBoard {

    /** Satu item makanan di dapur. */
    public static final class Item {
        public final long id;
        @NonNull public final String productName;
        public final int quantity;
        @NonNull public final String kitchenStatus;
        @Nullable public final String kitchenStatusUpdatedAt;

        Item(long id,
             @NonNull String productName,
             int quantity,
             @NonNull String kitchenStatus,
             @Nullable String kitchenStatusUpdatedAt) {
            this.id = id;
            this.productName = productName;
            this.quantity = quantity;
            this.kitchenStatus = kitchenStatus;
            this.kitchenStatusUpdatedAt = kitchenStatusUpdatedAt;
        }

        @Nullable
        static Item fromJson(@Nullable JSONObject o) {
            if (o == null) return null;
            long id = o.optLong("id", -1L);
            if (id <= 0) return null;

            // kitchen_status boleh null di level model, dan item seperti itu
            // tidak punya alur dapur. Papan tidak menampilkannya.
            String status = KitchenStatus.normalize(o.optString("kitchen_status", ""));
            if (!KitchenStatus.isKnown(status)) return null;

            String updatedAt = o.isNull("kitchen_status_updated_at")
                    ? null : Iso8601.safe(o.optString("kitchen_status_updated_at", ""));
            if (updatedAt != null && updatedAt.isEmpty()) updatedAt = null;

            return new Item(
                    id,
                    Iso8601.safe(o.optString("product_name", "")),
                    o.optInt("quantity", 0),
                    status,
                    updatedAt);
        }

        /** Salinan dengan status baru; dipakai setelah POST berhasil. */
        @NonNull
        public Item withStatus(@NonNull String newStatus, @Nullable String newUpdatedAt) {
            return new Item(id, productName, quantity,
                    KitchenStatus.normalize(newStatus), newUpdatedAt);
        }
    }

    /** Satu order di papan. */
    public static final class Order {
        public final long orderId;
        @NonNull public final String invoiceNumber;
        @Nullable public final String table;
        @Nullable public final String waiter;
        @NonNull public final List<Item> items;

        Order(long orderId,
              @NonNull String invoiceNumber,
              @Nullable String table,
              @Nullable String waiter,
              @NonNull List<Item> items) {
            this.orderId = orderId;
            this.invoiceNumber = invoiceNumber;
            this.table = table;
            this.waiter = waiter;
            this.items = items;
        }

        @Nullable
        static Order fromJson(@Nullable JSONObject o) {
            if (o == null) return null;
            long id = o.optLong("order_id", -1L);
            if (id <= 0) return null;

            List<Item> items = new ArrayList<>();
            JSONArray arr = o.optJSONArray("items");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    Item item = Item.fromJson(arr.optJSONObject(i));
                    if (item != null) items.add(item);
                }
            }
            // Order tanpa item yang bisa ditampilkan tidak punya apa pun untuk
            // dikerjakan dapur.
            if (items.isEmpty()) return null;

            return new Order(
                    id,
                    Iso8601.safe(o.optString("invoice_number", "")),
                    nullableString(o, "table"),
                    nullableString(o, "waiter"),
                    items);
        }

        /** Ganti satu item, kembalikan order baru. */
        @NonNull
        Order replacing(@NonNull Item updated) {
            List<Item> out = new ArrayList<>(items.size());
            for (Item it : items) {
                out.add(it.id == updated.id ? updated : it);
            }
            return new Order(orderId, invoiceNumber, table, waiter, out);
        }
    }

    /** Kosong kalau tidak ada apa pun di dapur. */
    public static final KitchenBoard EMPTY =
            new KitchenBoard(null, Collections.<Order>emptyList());

    @Nullable public final String lastChangedAt;
    @NonNull public final List<Order> orders;

    KitchenBoard(@Nullable String lastChangedAt, @NonNull List<Order> orders) {
        this.lastChangedAt = lastChangedAt;
        this.orders = orders;
    }

    @NonNull
    public static KitchenBoard fromJson(@Nullable JSONObject root) {
        if (root == null) return EMPTY;

        List<Order> out = new ArrayList<>();
        JSONArray arr = root.optJSONArray("orders");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                Order order = Order.fromJson(arr.optJSONObject(i));
                if (order != null) out.add(order);
            }
        }
        return new KitchenBoard(nullableString(root, "last_changed_at"), out);
    }

    public int itemCount() {
        int n = 0;
        for (Order o : orders) n += o.items.size();
        return n;
    }

    public int countWithStatus(@NonNull String status) {
        String target = KitchenStatus.normalize(status);
        int n = 0;
        for (Order o : orders) {
            for (Item it : o.items) {
                if (it.kitchenStatus.equals(target)) n++;
            }
        }
        return n;
    }

    /**
     * Papan baru dengan satu item diganti.
     *
     * <p>Dipakai setelah POST status berhasil, supaya perubahan terlihat
     * seketika tanpa menunggu putaran polling berikutnya. Item yang mencapai
     * status terminal ({@code SERVED}/{@code CANCELLED}) dibuang - itulah yang
     * akan dilakukan server pada polling berikutnya, dan membiarkannya
     * sebentar lalu menghilang sendiri justru terlihat seperti gangguan.
     */
    @NonNull
    public KitchenBoard withItemStatus(long itemId,
                                       @NonNull String newStatus,
                                       @Nullable String newUpdatedAt) {
        List<Order> out = new ArrayList<>(orders.size());
        boolean terminal = KitchenStatus.isTerminal(newStatus);

        for (Order order : orders) {
            boolean touched = false;
            for (Item it : order.items) {
                if (it.id == itemId) {
                    touched = true;
                    break;
                }
            }
            if (!touched) {
                out.add(order);
                continue;
            }

            if (!terminal) {
                Item replacement = null;
                for (Item it : order.items) {
                    if (it.id == itemId) replacement = it.withStatus(newStatus, newUpdatedAt);
                }
                out.add(order.replacing(replacement));
                continue;
            }

            List<Item> remaining = new ArrayList<>();
            for (Item it : order.items) {
                if (it.id != itemId) remaining.add(it);
            }
            if (!remaining.isEmpty()) {
                out.add(new Order(order.orderId, order.invoiceNumber,
                        order.table, order.waiter, remaining));
            }
        }

        return new KitchenBoard(lastChangedAt, out);
    }

    @Nullable
    private static String nullableString(@NonNull JSONObject o, @NonNull String key) {
        if (o.isNull(key)) return null;
        String v = Iso8601.safe(o.optString(key, ""));
        return v.isEmpty() ? null : v;
    }
}
