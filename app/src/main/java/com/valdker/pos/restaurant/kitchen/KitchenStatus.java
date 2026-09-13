package com.valdker.pos.restaurant.kitchen;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Alur status dapur satu item, persis seperti tabel kontrak di
 * {@code docs/api/RESTAURANT_API.md} bagian Kitchen Display.
 *
 * <pre>
 *   PENDING    -> PREPARING, CANCELLED
 *   PREPARING  -> READY,     CANCELLED
 *   READY      -> SERVED,    CANCELLED
 *   SERVED     -> (terminal)
 *   CANCELLED  -> (terminal)
 * </pre>
 *
 * <p>Tabel ini ditegakkan server di dalam {@code select_for_update()}, jadi
 * salinan di sini bukan sumber kebenaran - ia hanya menentukan tombol mana
 * yang ditawarkan. Menawarkan tombol yang pasti ditolak 400 adalah cara
 * membuat kru dapur belajar mengabaikan pesan galat.
 *
 * <p>{@code kitchen_status} boleh {@code null} di level model (item toko
 * retail/workshop tidak punya alur dapur sama sekali), tapi papan dapur tidak
 * pernah mengembalikan item seperti itu. Kalau toh muncul, ia diperlakukan
 * sebagai tidak dikenal - bukan dipaksa jadi PENDING, karena "null" berarti
 * "tidak pernah antre di dapur", bukan "sedang menunggu dimasak".
 */
public final class KitchenStatus {

    public static final String PENDING = "PENDING";
    public static final String PREPARING = "PREPARING";
    public static final String READY = "READY";
    public static final String SERVED = "SERVED";
    public static final String CANCELLED = "CANCELLED";

    private KitchenStatus() {
    }

    @NonNull
    public static String normalize(@Nullable String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.US);
    }

    public static boolean isKnown(@Nullable String raw) {
        String s = normalize(raw);
        return PENDING.equals(s) || PREPARING.equals(s)
                || READY.equals(s) || SERVED.equals(s) || CANCELLED.equals(s);
    }

    public static boolean isTerminal(@Nullable String raw) {
        String s = normalize(raw);
        return SERVED.equals(s) || CANCELLED.equals(s);
    }

    /**
     * Transisi yang sah dari status ini, berurutan: langkah maju lebih dulu,
     * lalu {@code CANCELLED}. Daftar kosong untuk status terminal maupun
     * status yang tidak dikenal.
     */
    @NonNull
    public static List<String> allowedNext(@Nullable String from) {
        String s = normalize(from);
        List<String> out = new ArrayList<>(2);

        if (PENDING.equals(s)) {
            out.add(PREPARING);
        } else if (PREPARING.equals(s)) {
            out.add(READY);
        } else if (READY.equals(s)) {
            out.add(SERVED);
        } else {
            // SERVED, CANCELLED, atau tidak dikenal: tidak ada apa pun.
            return Collections.emptyList();
        }

        out.add(CANCELLED);
        return out;
    }

    public static boolean canMove(@Nullable String from, @Nullable String to) {
        return allowedNext(from).contains(normalize(to));
    }
}
