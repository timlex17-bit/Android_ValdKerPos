package com.valdker.pos.network;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Aturan merakit alamat API, dipisahkan dari Android supaya bisa diuji.
 *
 * <p>{@link ApiConfig} dulu memuat logika ini di dalam dirinya sendiri, dan
 * karena ia menyentuh {@code SessionManager} serta {@code BuildConfig},
 * satu-satunya cara memeriksanya adalah menjalankan aplikasi. Padahal perakitan
 * inilah yang paling mudah salah: base URL toko bisa berakhiran {@code /api},
 * {@code /api/}, atau tanpa {@code /api} sama sekali, dan tiap bentuk menuntut
 * perlakuan berbeda agar tidak menghasilkan {@code /api/api/...}.
 *
 * <p>Perilakunya sengaja disalin apa adanya dari versi sebelumnya - termasuk
 * kejanggalannya pada masukan kosong - supaya pemindahan ini tidak mengubah
 * satu pun alamat yang sudah dipakai modul lain.
 */
public final class ApiUrlRules {

    private ApiUrlRules() {
    }

    /**
     * Membakukan base URL: memakai cadangan bila kosong, menambahkan skema
     * bila tidak ada, dan memastikan berakhiran garis miring.
     *
     * <p>Catatan: bila {@code raw} dan {@code fallback} sama-sama kosong,
     * hasilnya {@code "http:///"}. Itu bukan alamat yang berguna, tetapi itulah
     * perilaku yang sudah ada dan modul lain tidak boleh berubah karena
     * pemindahan ini.
     */
    @NonNull
    public static String normalizeBase(@Nullable String raw, @Nullable String fallback) {
        String base = raw;

        if (base == null || base.trim().isEmpty()) {
            base = fallback;
        }
        if (base == null) {
            base = "";
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
     * Menggabungkan base yang sudah dibakukan dengan path endpoint.
     *
     * @param normalizedBase hasil {@link #normalizeBase}; selalu berakhiran "/"
     * @param path           path endpoint, boleh diawali {@code api/} atau tidak
     */
    @NonNull
    public static String join(@NonNull String normalizedBase, @NonNull String path) {
        String base = normalizedBase;

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
