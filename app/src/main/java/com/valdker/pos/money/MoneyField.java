package com.valdker.pos.money;

import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.textfield.TextInputLayout;

/**
 * Satu jalur dari kolom isian uang menuju payload API.
 *
 * <p>Tanda "$" di layar dipasang secara deklaratif - {@code app:prefixText}
 * pada TextInputLayout, atau TextView di dalam kotak uang untuk EditText polos
 * - sehingga ia tidak pernah menjadi bagian dari teks yang diedit. Kelas ini
 * mengurus sisi NILAINYA.
 *
 * <h3>Kenapa perlu, padahal prefiksnya sudah di luar teks</h3>
 *
 * <p>Karena pengguna bisa menempel. Seseorang yang menyalin "$1,250.50" dari
 * pesan WhatsApp dan menempelkannya ke kolom harga akan membuat teks kolom itu
 * benar-benar memuat tanda dolar dan koma - dan beberapa layar di aplikasi ini
 * mengirim isi kolom LANGSUNG ke server tanpa diurai sama sekali. Yang sampai
 * ke sana bukan angka yang salah, melainkan teks yang bukan angka, dan
 * kegagalannya muncul sebagai transaksi yang ditolak tanpa sebab yang jelas.
 *
 * <p>{@link #plain(EditText)} menutup celah itu di satu tempat: ia membersihkan
 * apa pun yang diketik atau ditempel, lalu mengembalikan bentuk yang memang
 * diharapkan server - "1250.50", dua desimal, tanpa simbol dan tanpa pemisah
 * ribuan.
 *
 * <h3>Kenapa kolom isian TIDAK diberi pemisah ribuan</h3>
 *
 * <p>Akan lebih enak dibaca, tetapi beberapa layar mengirim teks kolom apa
 * adanya, sehingga koma di dalam kolom berarti koma di dalam payload. Pemisah
 * ribuan adalah urusan TAMPILAN, dan tempatnya {@link Money#format()} - di
 * baris ringkasan, kartu laporan, dan struk. Di dalam kolom yang sedang
 * diedit, yang diutamakan adalah angka yang bisa dikirim apa adanya.
 */
public final class MoneyField {

    private MoneyField() {
    }

    /**
     * Nilai kolom dalam bentuk yang dikirim ke server: {@code "1250.50"}.
     *
     * <p>Aman terhadap apa pun isi kolomnya - kosong, tertempel "$1,250.50",
     * atau bukan angka sama sekali - karena seluruhnya lewat
     * {@link Money#of(String)}, yang sudah membuang simbol dan pemisah, lalu
     * jatuh ke nol bila tetap tidak bisa dibaca.
     */
    @NonNull
    public static String plain(@Nullable EditText field) {
        return money(field).toPlainString();
    }

    /** Nilai kolom sebagai {@link Money}, untuk perhitungan lanjutan. */
    @NonNull
    public static Money money(@Nullable EditText field) {
        if (field == null || field.getText() == null) return Money.zero();
        return Money.of(field.getText().toString());
    }

    /** Benarkah kolom ini berisi sesuatu yang bisa dibaca sebagai nominal? */
    public static boolean isBlank(@Nullable EditText field) {
        return field == null || field.getText() == null
                || TextUtils.isEmpty(field.getText().toString().trim());
    }

    /**
     * Merapikan isi kolom menjadi dua desimal begitu fokus berpindah.
     *
     * <p>Saat diketik, "10.5" dibiarkan apa adanya: memaksa dua desimal di
     * tengah pengetikan membuat kursor melompat dan angka yang belum selesai
     * ditulis berubah sendiri. Begitu pengguna berpindah ke kolom lain,
     * angkanya baru dibakukan - "10.5" jadi "10.50" - sehingga yang terlihat
     * sebelum menekan simpan sama persis dengan yang akan dikirim.
     *
     * <p>Kolom kosong dibiarkan kosong. Menuliskan "0.00" ke kolom yang belum
     * disentuh membuat kolom wajib terlihat seolah sudah diisi.
     */
    public static void normalizeOnBlur(@Nullable EditText... fields) {
        if (fields == null) return;
        for (EditText field : fields) {
            if (field == null) continue;
            field.setOnFocusChangeListener((View v, boolean hasFocus) -> {
                if (hasFocus) return;
                if (isBlank(field)) return;
                field.setText(money(field).toPlainString());
            });
        }
    }

    /**
     * Memasang tanda "$" pada kolom yang dibuat saat berjalan.
     *
     * <p>Kolom yang ditulis di XML memakai {@code app:prefixText}; yang dibuat
     * dari kode tidak punya XML untuk menaruhnya, jadi dipasang di sini agar
     * keduanya berakhir sama.
     */
    public static void attachPrefix(@Nullable TextInputLayout layout, @NonNull String symbol) {
        if (layout == null) return;
        layout.setPrefixText(symbol);
    }
}
