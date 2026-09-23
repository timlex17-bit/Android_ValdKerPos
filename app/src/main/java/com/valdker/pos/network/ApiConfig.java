package com.valdker.pos.network;

import androidx.annotation.NonNull;
import com.valdker.pos.BuildConfig;
import com.valdker.pos.SessionManager;

public final class ApiConfig {

    private ApiConfig(){}

    @NonNull
    public static String base(@NonNull SessionManager session) {
        return ApiUrlRules.normalizeBase(session.getBaseUrl(), BuildConfig.BASE_URL);
    }

    /**
     * Dari mana base URL yang sedang dipakai berasal.
     *
     * <p>Ada dua sumber dan keduanya bisa mengejutkan: nilai yang tersimpan di
     * sesi mengalahkan {@code BuildConfig.BASE_URL}, jadi build yang
     * dikompilasi untuk lokal tetap bisa menunjuk ke tempat lain kalau sesi
     * lama menyimpan alamat lain. Label ini dipakai layar Settings supaya
     * perbedaan itu terlihat, bukan tersembunyi.
     */
    @NonNull
    public static String originLabel(@NonNull SessionManager session) {
        if (session.hasStoredBaseUrl()) {
            return "sesi tersimpan";
        }
        return BuildConfig.DEBUG ? "bawaan build debug" : "bawaan build rilis";
    }

    /** Satu baris siap tampil/log: alamat aktif beserta asalnya. */
    @NonNull
    public static String describe(@NonNull SessionManager session) {
        return base(session) + " (" + originLabel(session) + ")";
    }

    /**
     * Aturan perakitannya ada di {@link ApiUrlRules} - Java murni, sehingga
     * bisa diuji tanpa perangkat. Di sini hanya penyambungan ke sesi.
     */
    @NonNull
    public static String url(@NonNull SessionManager session, @NonNull String path) {
        return ApiUrlRules.join(base(session), path);
    }
}
