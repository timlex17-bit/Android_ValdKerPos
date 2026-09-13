package com.valdker.pos.network;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.valdker.pos.BuildConfig;
import com.valdker.pos.R;
import com.valdker.pos.SessionManager;

/**
 * Satu jalur untuk mengubah alamat backend.
 *
 * <p>Sebelumnya hanya ada satu tempat yang bisa mengubahnya - layar Settings,
 * yang berada di balik login. Kalau alamatnya salah, login gagal, Settings
 * tidak bisa dibuka, dan satu-satunya pemulihan adalah menghapus data
 * aplikasi - yang juga menghapus order offline yang belum tersinkron. Itu
 * bukan kasus tepi; itu yang terjadi pada pemilik toko dengan base URL debug
 * 10.0.2.2 di perangkat sungguhan.
 *
 * <p>Sekarang layar Login juga bisa mengubahnya, dan kedua layar memakai
 * validasi serta efek samping yang sama persis lewat kelas ini.
 */
public final class BaseUrlStore {

    private BaseUrlStore() {
    }

    /** Hasil penyimpanan: berhasil, atau pesan galat siap tampil. */
    public static final class Result {
        public final boolean ok;
        @StringRes
        public final int messageRes;

        private Result(boolean ok, @StringRes int messageRes) {
            this.ok = ok;
            this.messageRes = messageRes;
        }
    }

    /**
     * Memvalidasi lalu menyimpan alamat baru.
     *
     * <p>Menyimpan alamat berarti sesi lama tidak lagi berlaku - token milik
     * server yang berbeda. Auth dan shift ikut dibersihkan, dan cache jaringan
     * dibuang, supaya data server lama tidak bocor ke tampilan server baru.
     *
     * <p>Yang sengaja <b>tidak</b> disentuh: order offline yang belum
     * tersinkron. Itu milik perangkat, bukan milik sesi.
     */
    @NonNull
    public static Result save(@NonNull Context context, @Nullable String rawUrl) {
        BaseUrlRules.Verdict verdict = BaseUrlRules.check(rawUrl, BuildConfig.DEBUG);
        if (verdict != BaseUrlRules.Verdict.OK) {
            return new Result(false, messageFor(verdict));
        }

        String normalized = BaseUrlRules.normalize(rawUrl);

        SessionManager session = new SessionManager(context);
        session.setBaseUrl(normalized);
        forgetServerBoundState(context, session);

        return new Result(true, R.string.login_server_saved);
    }

    /**
     * Membuang alamat tersimpan sehingga aplikasi kembali ke
     * {@code BuildConfig.BASE_URL}.
     *
     * <p>Inilah pemanggil {@link SessionManager#clearBaseUrl()} - sebelumnya
     * metode itu tidak dipanggil dari mana pun.
     *
     * @return false kalau memang tidak ada yang tersimpan (tidak ada yang
     *         perlu direset), sehingga pemanggil bisa mengatakannya apa adanya
     *         alih-alih melaporkan keberhasilan palsu.
     */
    public static boolean resetToBuildDefault(@NonNull Context context) {
        SessionManager session = new SessionManager(context);
        if (!session.hasStoredBaseUrl()) return false;

        session.clearBaseUrl();
        forgetServerBoundState(context, session);
        return true;
    }

    private static void forgetServerBoundState(@NonNull Context context,
                                               @NonNull SessionManager session) {
        session.clearAuth();
        session.clearShift();

        ApiClient client = ApiClient.getInstance(context.getApplicationContext());
        client.cancelAll("DASHBOARD");
        client.cancelAll("ShopRepository");
        client.cancelAll("ApiClient");
        client.clearCache();
    }

    @StringRes
    public static int messageFor(@NonNull BaseUrlRules.Verdict verdict) {
        switch (verdict) {
            case EMPTY:
                return R.string.msg_base_url_empty;
            case BAD_SCHEME:
                return R.string.msg_base_url_invalid_scheme;
            case BAD_HOST:
                return R.string.msg_base_url_bad_host;
            case CLEARTEXT_PUBLIC_HOST:
                return R.string.msg_base_url_cleartext_public;
            case CLEARTEXT_NOT_ALLOWED_IN_RELEASE:
                return R.string.msg_base_url_cleartext_release;
            case OK:
            default:
                return R.string.login_server_saved;
        }
    }
}
