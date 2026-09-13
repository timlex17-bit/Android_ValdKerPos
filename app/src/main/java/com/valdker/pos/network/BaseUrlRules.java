package com.valdker.pos.network;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * Aturan tentang alamat backend mana yang boleh disimpan.
 *
 * <p>Kelas ini sengaja Java murni - tanpa {@code android.net.Uri}, tanpa
 * {@code Context} - supaya bisa diuji dengan unit test biasa. Aturannya
 * menyangkut keamanan jaringan, dan aturan keamanan yang hanya bisa diperiksa
 * dengan menjalankan aplikasi di perangkat cenderung tidak pernah diperiksa.
 *
 * <p><b>Kenapa pemeriksaan rentang ada di sini dan bukan di
 * network_security_config.xml:</b> konfigurasi keamanan jaringan Android tidak
 * mengenal notasi rentang/CIDR sama sekali. Elemen {@code <domain>} hanya
 * menerima satu hostname atau satu IP literal, jadi "izinkan 192.168.0.0/16"
 * tidak bisa dinyatakan di sana. Sementara alamat backend bisa diubah saat
 * aplikasi berjalan, jadi daftar host tidak diketahui saat build. Rentangnya
 * karena itu ditegakkan di titik masuk - sebelum alamat sempat disimpan.
 */
public final class BaseUrlRules {

    private BaseUrlRules() {
    }

    /** Alasan sebuah alamat ditolak. {@link #OK} berarti diterima. */
    public enum Verdict {
        OK,
        EMPTY,
        BAD_SCHEME,
        BAD_HOST,
        /** {@code http://} ke host publik - ditolak bahkan di build debug. */
        CLEARTEXT_PUBLIC_HOST,
        /** {@code http://} di build rilis - platform juga akan menolaknya. */
        CLEARTEXT_NOT_ALLOWED_IN_RELEASE
    }

    /**
     * Memeriksa alamat yang diketik pengguna.
     *
     * @param debugBuild apakah ini build debug; diserahkan sebagai parameter,
     *                   bukan dibaca dari {@code BuildConfig}, supaya kedua
     *                   cabang bisa diuji.
     */
    @NonNull
    public static Verdict check(@Nullable String rawUrl, boolean debugBuild) {
        String url = rawUrl == null ? "" : rawUrl.trim();
        if (url.isEmpty()) return Verdict.EMPTY;

        String lower = url.toLowerCase(Locale.US);
        boolean https = lower.startsWith("https://");
        boolean http = lower.startsWith("http://");
        if (!https && !http) return Verdict.BAD_SCHEME;

        String host = hostOf(url);
        if (host.isEmpty()) return Verdict.BAD_HOST;

        if (https) return Verdict.OK;

        // Sisanya cleartext.
        if (!debugBuild) return Verdict.CLEARTEXT_NOT_ALLOWED_IN_RELEASE;
        if (!isPrivateHost(host)) return Verdict.CLEARTEXT_PUBLIC_HOST;
        return Verdict.OK;
    }

    /**
     * Host dari sebuah URL, tanpa kredensial, port, path, query, atau kurung
     * siku IPv6. Mengembalikan string kosong kalau tidak ada host.
     */
    @NonNull
    public static String hostOf(@Nullable String rawUrl) {
        if (rawUrl == null) return "";
        String s = rawUrl.trim();

        int scheme = s.indexOf("://");
        if (scheme >= 0) s = s.substring(scheme + 3);

        // Buang path/query/fragment.
        int cut = s.length();
        for (String stop : new String[]{"/", "?", "#"}) {
            int i = s.indexOf(stop);
            if (i >= 0 && i < cut) cut = i;
        }
        s = s.substring(0, cut);

        // Buang "user:pass@".
        int at = s.lastIndexOf('@');
        if (at >= 0) s = s.substring(at + 1);

        if (s.startsWith("[")) {
            // IPv6 literal: [::1]:8000
            int close = s.indexOf(']');
            return close > 1 ? s.substring(1, close).toLowerCase(Locale.US) : "";
        }

        int colon = s.indexOf(':');
        if (colon >= 0) s = s.substring(0, colon);

        return s.toLowerCase(Locale.US);
    }

    /**
     * Apakah host ini ada di jaringan privat.
     *
     * <p>Yang diterima: loopback, {@code localhost}, nama mDNS {@code .local},
     * dan rentang RFC 1918 (10/8, 172.16/12, 192.168/16) plus link-local
     * 169.254/16. 10.0.2.2 - alias emulator untuk mesin host - masuk lewat
     * 10/8, jadi tidak perlu dicantumkan terpisah.
     *
     * <p>172.16/12 ikut diterima meski tidak disebut dalam permintaan awal:
     * ia sama-sama RFC 1918 dan itu rentang bawaan jaringan bridge Docker,
     * jadi mengecualikannya hanya akan jadi kejutan berikutnya.
     */
    public static boolean isPrivateHost(@Nullable String rawHost) {
        String host = rawHost == null ? "" : rawHost.trim().toLowerCase(Locale.US);
        if (host.isEmpty()) return false;

        if (host.equals("localhost") || host.endsWith(".localhost")) return true;
        if (host.endsWith(".local")) return true;
        if (host.equals("::1") || host.equals("0:0:0:0:0:0:0:1")) return true;

        int[] v4 = parseIpv4(host);
        if (v4 == null) {
            // Bukan IP literal dan bukan nama privat yang dikenal. Hostname
            // biasa tidak bisa dinilai tanpa resolusi DNS, dan resolusi DNS
            // tidak boleh dilakukan di sini - jadi ditolak.
            return false;
        }

        if (v4[0] == 127) return true;                    // 127.0.0.0/8
        if (v4[0] == 10) return true;                     // 10.0.0.0/8
        if (v4[0] == 192 && v4[1] == 168) return true;    // 192.168.0.0/16
        if (v4[0] == 172 && v4[1] >= 16 && v4[1] <= 31) return true;  // 172.16.0.0/12
        if (v4[0] == 169 && v4[1] == 254) return true;    // 169.254.0.0/16

        return false;
    }

    /** Empat oktet, atau {@code null} kalau bukan IPv4 dotted-quad yang sah. */
    @Nullable
    private static int[] parseIpv4(@NonNull String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) return null;

        int[] out = new int[4];
        for (int i = 0; i < 4; i++) {
            String p = parts[i];
            if (p.isEmpty() || p.length() > 3) return null;
            int value = 0;
            for (int c = 0; c < p.length(); c++) {
                char ch = p.charAt(c);
                if (ch < '0' || ch > '9') return null;
                value = value * 10 + (ch - '0');
            }
            if (value > 255) return null;
            out[i] = value;
        }
        return out;
    }

    /**
     * Bentuk yang disimpan: skema dilengkapi kalau hilang, dan selalu diakhiri
     * garis miring supaya penggabungan path tidak pernah menghasilkan dua
     * garis miring atau malah tanpa pemisah.
     */
    @NonNull
    public static String normalize(@Nullable String rawUrl) {
        String url = rawUrl == null ? "" : rawUrl.trim();
        if (url.isEmpty()) return "";
        if (!url.toLowerCase(Locale.US).startsWith("http://")
                && !url.toLowerCase(Locale.US).startsWith("https://")) {
            url = "http://" + url;
        }
        while (url.endsWith("//")) url = url.substring(0, url.length() - 1);
        if (!url.endsWith("/")) url = url + "/";
        return url;
    }
}
