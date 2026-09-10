package com.valdker.pos.money;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Satu-satunya tempat aritmetika uang di aplikasi ini.
 *
 * <h3>Kenapa BigDecimal, bukan long sen</h3>
 * Backend menyimpan setiap nilai uang sebagai
 * {@code DecimalField(max_digits=12, decimal_places=2)} dan mengirimkannya
 * sebagai string desimal. BigDecimal memetakan satu-lawan-satu ke bentuk itu,
 * jadi nilai bisa berpindah klien-server tanpa konversi yang bisa salah. Long
 * sen akan menambah satu konversi di setiap batas (JSON, Room, tampilan), dan
 * setiap konversi adalah tempat baru munculnya galat.
 *
 * <h3>Skala dan pembulatan</h3>
 * Skala tetap {@value #SCALE} desimal, mengikuti {@code decimal_places=2} di
 * backend. Aplikasi ini hanya menangani USD (seluruh pemformatan memakai
 * {@code Locale.US}); tidak ada field mata uang di mana pun, di klien maupun
 * server.
 *
 * <p>Pembulatan memakai {@link RoundingMode#HALF_EVEN}, <b>bukan</b> HALF_UP
 * yang lazim di POS. Ini disengaja: backend membulatkan pajak dengan
 * {@code (base * percent / 100).quantize(Decimal("0.01"))}
 * (pos/serializers.py), dan konteks {@code decimal} bawaan Python adalah
 * ROUND_HALF_EVEN. Memakai HALF_UP di klien akan berbeda satu sen tepat di
 * batas .005 - dan komentar di serializer menyatakan server akan menjadi
 * otoritatif atas pajak di "Stage B", sehingga selisih itu akan berubah dari
 * peringatan di log menjadi nilai yang ditimpa. Menyamakan pembulatan sekarang
 * membuat angka di layar kasir dan di server tetap identik nanti.
 *
 * <h3>Catatan penting soal total</h3>
 * Server <b>menghitung ulang</b> {@code subtotal} dan {@code total} dari
 * {@code items[].price x quantity} lalu menimpa nilai kiriman klien
 * (pos/serializers.py). Jadi yang harus cocok dengan layar bukan field
 * {@code total} yang kita kirim, melainkan hasil penjumlahan baris item.
 */
public final class Money implements Comparable<Money> {

    /** Jumlah desimal, mengikuti decimal_places=2 di backend. */
    public static final int SCALE = 2;

    /** Lihat catatan kelas: menyamai pembulatan backend, bukan HALF_UP. */
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public static final Money ZERO = new Money(BigDecimal.ZERO);

    private final BigDecimal value;

    private Money(@NonNull BigDecimal raw) {
        this.value = raw.setScale(SCALE, ROUNDING);
    }

    // ---------------------------------------------------------------- pabrik

    @NonNull
    public static Money zero() {
        return ZERO;
    }

    @NonNull
    public static Money of(@Nullable BigDecimal raw) {
        return raw == null ? ZERO : new Money(raw);
    }

    /**
     * Membaca nilai uang dari teks. Menerima bentuk yang benar-benar datang
     * dari API dan dari input kasir: "12.35", "$12.35", "1,234.50", "12.35 USD",
     * kosong, atau null.
     */
    @NonNull
    public static Money of(@Nullable String raw) {
        if (raw == null) return ZERO;
        String s = raw.trim()
                .replace("$", "")
                .replace("USD", "")
                .replace("usd", "")
                .replace(",", "")
                .trim();
        if (s.isEmpty()) return ZERO;
        try {
            return new Money(new BigDecimal(s));
        } catch (NumberFormatException e) {
            return ZERO;
        }
    }

    /**
     * Jembatan untuk kode yang masih memegang {@code double}. Dipakai selama
     * migrasi; setiap pemanggil yang tersisa adalah utang yang belum lunas.
     */
    @NonNull
    public static Money ofDouble(double raw) {
        // BigDecimal.valueOf lewat Double.toString, jadi 0.1 jadi "0.1" dan
        // bukan ekspansi biner 0.1000000000000000055511151231257827.
        return new Money(BigDecimal.valueOf(raw));
    }

    /** Membaca field uang pertama yang ada di antara beberapa nama alias. */
    @NonNull
    public static Money fromJson(@Nullable JSONObject o, @NonNull String... keys) {
        if (o == null) return ZERO;
        for (String key : keys) {
            if (key == null || !o.has(key) || o.isNull(key)) continue;
            return of(o.optString(key, ""));
        }
        return ZERO;
    }

    // ------------------------------------------------------------- aritmetika

    @NonNull
    public Money plus(@NonNull Money other) {
        return new Money(value.add(other.value));
    }

    @NonNull
    public Money minus(@NonNull Money other) {
        return new Money(value.subtract(other.value));
    }

    /**
     * Harga baris: qty x harga satuan. Dengan qty bilangan bulat dan harga
     * berskala 2, hasilnya tepat - tidak ada pembulatan yang terjadi di sini.
     */
    @NonNull
    public Money times(long qty) {
        return new Money(value.multiply(BigDecimal.valueOf(qty)));
    }

    /** Perkalian dengan kuantitas pecahan (satuan berat, konversi unit). */
    @NonNull
    public Money times(@NonNull BigDecimal qty) {
        return new Money(value.multiply(qty));
    }

    /**
     * Persentase dari nilai ini, mis. pajak 11% atau diskon 10%.
     * Perkalian dan pembagian dilakukan pada presisi penuh dulu, baru
     * dibulatkan sekali di akhir - sama seperti yang dilakukan backend.
     */
    @NonNull
    public Money percent(@NonNull BigDecimal percent) {
        BigDecimal exact = value.multiply(percent);
        return new Money(exact.divide(HUNDRED, SCALE + 4, ROUNDING));
    }

    @NonNull
    public Money negate() {
        return new Money(value.negate());
    }

    /** Tidak pernah mengembalikan nilai negatif; dipakai untuk kembalian. */
    @NonNull
    public Money orZeroIfNegative() {
        return isNegative() ? ZERO : this;
    }

    // ------------------------------------------------------------ perbandingan

    public boolean isZero() {
        return value.signum() == 0;
    }

    public boolean isNegative() {
        return value.signum() < 0;
    }

    public boolean isPositive() {
        return value.signum() > 0;
    }

    public boolean isLessThan(@NonNull Money other) {
        return value.compareTo(other.value) < 0;
    }

    public boolean isGreaterThanOrEqual(@NonNull Money other) {
        return value.compareTo(other.value) >= 0;
    }

    @Override
    public int compareTo(@NonNull Money other) {
        return value.compareTo(other.value);
    }

    // ------------------------------------------------------------------ keluar

    @NonNull
    public BigDecimal toBigDecimal() {
        return value;
    }

    /**
     * Bentuk untuk payload JSON dan kolom Room: "12.35", selalu dua desimal,
     * tanpa notasi ilmiah dan tanpa simbol mata uang.
     */
    @NonNull
    public String toPlainString() {
        return value.toPlainString();
    }

    /** Bentuk untuk layar dan struk: "$12.35". */
    @NonNull
    public String format() {
        return NumberFormat.getCurrencyInstance(Locale.US).format(value);
    }

    /**
     * Jembatan balik ke {@code double} untuk antarmuka yang belum dimigrasi.
     * Sama seperti {@link #ofDouble}, setiap pemanggil adalah utang.
     */
    public double toDouble() {
        return value.doubleValue();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money)) return false;
        return value.compareTo(((Money) o).value) == 0;
    }

    @Override
    public int hashCode() {
        return value.stripTrailingZeros().hashCode();
    }

    @NonNull
    @Override
    public String toString() {
        return toPlainString();
    }
}
