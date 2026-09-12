package com.valdker.pos.network;

import androidx.annotation.NonNull;
import com.valdker.pos.BuildConfig;
import com.valdker.pos.SessionManager;

public final class ApiConfig {

    private ApiConfig(){}

    @NonNull
    public static String base(@NonNull SessionManager session) {
        String base = session.getBaseUrl();

        if (base == null || base.trim().isEmpty()) {
            base = BuildConfig.BASE_URL; // fallback
        }

        base = base.trim();

        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            base = "http://" + base;
        }

        if (!base.endsWith("/")) {
            base = base + "/";
        }

        return base;
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

    @NonNull
    public static String url(@NonNull SessionManager session, @NonNull String path) {
        String base = base(session);

        if (path.startsWith("/")) path = path.substring(1);
        boolean baseIncludesApi = base.endsWith("/api/") || base.endsWith("/api");

        if (baseIncludesApi && ("api".equals(path) || "api/".equals(path))) {
            return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        }

        if (baseIncludesApi && path.startsWith("api/")) {
            path = path.substring(4);
        } else if (!baseIncludesApi && !path.startsWith("api/")) {
            path = "api/" + path;
        }

        return base + path;
    }
}
