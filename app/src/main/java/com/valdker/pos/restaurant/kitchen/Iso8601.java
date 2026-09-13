package com.valdker.pos.restaurant.kitchen;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Pembacaan timestamp ISO-8601 dari API.
 *
 * <p>Ini kelihatan seperti satu baris {@code SimpleDateFormat}, dan justru itu
 * jebakannya. Server mengirim pecahan detik <b>enam</b> digit
 * ({@code 2026-09-12T14:20:34.939538Z}), sementara pola {@code SSS} milik
 * SimpleDateFormat tidak membaca "tiga digit milidetik" - ia membaca "sebuah
 * bilangan milidetik". Diberi {@code 939538} ia menerima 939.538 milidetik,
 * yaitu lebih dari 15 menit, dan hasilnya tetap "berhasil" tanpa melempar apa
 * pun. Papan dapur yang menghitung "READY sejak berapa lama" dari nilai itu
 * akan salah belasan menit tanpa tanda apa-apa.
 *
 * <p>Karena itu pecahan detiknya dipotong ke tiga digit lebih dulu, {@code Z}
 * diubah jadi offset numerik, dan titik dua di offset dibuang - baru kemudian
 * di-parse. minSdk proyek ini 24, jadi {@code java.time} (API 26) tidak
 * tersedia tanpa desugaring.
 *
 * <p>Java murni dan tanpa dependensi Android supaya bisa diuji unit test.
 */
public final class Iso8601 {

    private Iso8601() {
    }

    /** Milidetik epoch, atau {@code null} kalau tidak bisa dibaca. */
    @Nullable
    public static Long parseToEpochMillis(@Nullable String raw) {
        String normalized = normalize(raw);
        if (normalized == null) return null;

        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US);
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        fmt.setLenient(false);
        try {
            return fmt.parse(normalized).getTime();
        } catch (ParseException e) {
            return null;
        }
    }

    /**
     * Membentuk ulang string menjadi {@code yyyy-MM-ddTHH:mm:ss.SSS+HHMM}.
     * Kembalikan {@code null} kalau bentuknya jelas bukan ISO-8601.
     */
    @Nullable
    static String normalize(@Nullable String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.length() < 19) return null;
        if (s.charAt(4) != '-' || s.charAt(7) != '-') return null;

        char sep = s.charAt(10);
        if (sep != 'T' && sep != 't' && sep != ' ') return null;
        s = s.substring(0, 10) + 'T' + s.substring(11);

        // --- pisahkan bagian offset dari bagian tanggal-waktu ---
        String offset;
        String dateTime;

        if (s.endsWith("Z") || s.endsWith("z")) {
            dateTime = s.substring(0, s.length() - 1);
            offset = "+0000";
        } else {
            // Tanda +/- setelah posisi jam; tanggal juga memakai '-', jadi
            // pencarian dimulai dari belakang bagian waktu.
            int signAt = -1;
            for (int i = s.length() - 1; i >= 11; i--) {
                char c = s.charAt(i);
                if (c == '+' || c == '-') {
                    signAt = i;
                    break;
                }
            }
            if (signAt < 0) {
                // Tanpa offset sama sekali: perlakukan sebagai UTC. Server ini
                // selalu mengirim offset, jadi ini jalur bertahan saja.
                dateTime = s;
                offset = "+0000";
            } else {
                dateTime = s.substring(0, signAt);
                String rawOffset = s.substring(signAt).replace(":", "");
                if (rawOffset.length() == 3) rawOffset = rawOffset + "00";  // "+09" -> "+0900"
                if (rawOffset.length() != 5) return null;
                offset = rawOffset;
            }
        }

        // --- pecahan detik dipaksa tepat tiga digit ---
        int dot = dateTime.indexOf('.');
        String fraction;
        if (dot < 0) {
            fraction = "000";
        } else {
            String digits = dateTime.substring(dot + 1);
            dateTime = dateTime.substring(0, dot);
            StringBuilder f = new StringBuilder();
            for (int i = 0; i < digits.length() && f.length() < 3; i++) {
                char c = digits.charAt(i);
                if (c < '0' || c > '9') break;
                f.append(c);
            }
            while (f.length() < 3) f.append('0');
            fraction = f.toString();
        }

        if (dateTime.length() != 19) return null;

        return dateTime + "." + fraction + offset;
    }

    /**
     * Durasi sejak {@code timestamp} sampai {@code nowMillis}, sebagai teks
     * pendek yang muat di kartu dapur: {@code "baru"}, {@code "4m"},
     * {@code "1j 12m"}.
     *
     * <p>{@code nowMillis} diserahkan pemanggil, bukan dibaca dari jam sistem
     * di dalam sini, supaya perhitungannya bisa diuji.
     *
     * @return null kalau timestamp tidak terbaca - pemanggil menyembunyikan
     *         labelnya alih-alih menampilkan durasi karangan.
     */
    @Nullable
    public static String shortAgo(@Nullable String timestamp, long nowMillis) {
        Long then = parseToEpochMillis(timestamp);
        if (then == null) return null;

        long deltaMs = nowMillis - then;
        // Jam perangkat dapur bisa lebih lambat dari server. Durasi negatif
        // ditampilkan sebagai "baru", bukan sebagai angka minus.
        if (deltaMs < 0) deltaMs = 0;

        long minutes = deltaMs / 60000L;
        if (minutes < 1) return "baru";
        if (minutes < 60) return minutes + "m";

        long hours = minutes / 60;
        long rest = minutes % 60;
        return rest == 0 ? (hours + "j") : (hours + "j " + rest + "m");
    }

    /** Menit penuh sejak {@code timestamp}; -1 kalau tidak terbaca. */
    public static long minutesSince(@Nullable String timestamp, long nowMillis) {
        Long then = parseToEpochMillis(timestamp);
        if (then == null) return -1;
        long delta = nowMillis - then;
        if (delta < 0) delta = 0;
        return delta / 60000L;
    }

    @NonNull
    public static String safe(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}
