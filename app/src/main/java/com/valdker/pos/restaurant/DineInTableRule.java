package com.valdker.pos.restaurant;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Mekanisme meja mana yang dipakai sebuah order dine-in.
 *
 * <p>Ada dua, dan keduanya tidak boleh ditanyakan bersamaan:
 *
 * <ul>
 *   <li>{@code table_id} - relasi ke entitas {@code Table}, dipilih dari grid
 *       meja di keranjang. Ini penerusnya; server mengisi ulang
 *       {@code table_number} dari {@code Table.name} begitu field ini
 *       terkirim.</li>
 *   <li>{@code table_number} - teks bebas di dialog pembayaran. Warisan dari
 *       masa sebelum entitas {@code Table} ada, dan hanya berguna untuk toko
 *       yang memang tidak punya meja untuk dipilih.</li>
 * </ul>
 *
 * <p>Sebelum aturan ini ada, keduanya ditanyakan sekaligus: kasir memilih meja
 * A1 dari grid, lalu tetap diwajibkan mengetik nomor meja di dialog - dan yang
 * diketik itu dijamin ditimpa server. Aturannya diletakkan di kelas sendiri,
 * tanpa dependensi Android, supaya matriks keputusannya bisa diuji dan tidak
 * bisa berubah diam-diam saat salah satu layar disentuh.
 */
public final class DineInTableRule {

    /** Apa yang harus dilakukan layar untuk sebuah keranjang. */
    public enum Decision {
        /** Keranjang tidak punya item dine-in: meja tidak relevan sama sekali. */
        NOT_APPLICABLE,
        /** Grid meja bisa dipakai tapi belum ada yang dipilih: blokir lanjut bayar. */
        BLOCK_UNTIL_PICKED,
        /**
         * Meja sudah dipilih tapi keranjang memuat item non-dine-in.
         *
         * <p>Kombinasi ini merusak diam-diam: server menghitung
         * {@code default_order_type} dari himpunan tipe item, dan begitu
         * tipenya lebih dari satu hasilnya selalu {@code GENERAL}. Status
         * "meja terisi" mensyaratkan {@code DINE_IN}, jadi satu item bungkus
         * saja sudah cukup membuat meja yang jelas-jelas ditempati tampil
         * kosong di grid.
         */
        MIXED_WITH_TABLE,
        /** Meja sudah dipilih: kirim {@code table_id}, jangan tanya apa pun lagi. */
        USE_PICKED_TABLE,
        /** Tidak ada grid yang bisa dipakai: minta nomor meja teks bebas. */
        ASK_FREE_TEXT,
        /** Toko ini memang tidak mencatat meja. Lanjut tanpa meja. */
        NO_TABLE_RECORDED
    }

    private DineInTableRule() {
    }

    /**
     * @param hasDineInItem      keranjang memuat minimal satu item DINE_IN
     * @param hasOtherTypeItem   keranjang memuat minimal satu item NON-dine-in
     * @param tablesModuleOn     modul {@code "tables"} aktif untuk user ini
     * @param activeTableCount   jumlah meja aktif; {@code -1} kalau belum diketahui
     * @param pickedTableId      meja yang sudah dipilih untuk bill ini, kalau ada
     * @param tableNumberFieldOn fitur toko {@code enable_table_number}
     */
    @NonNull
    public static Decision decide(boolean hasDineInItem,
                                  boolean hasOtherTypeItem,
                                  boolean tablesModuleOn,
                                  int activeTableCount,
                                  @Nullable Long pickedTableId,
                                  boolean tableNumberFieldOn) {
        if (!hasDineInItem) return Decision.NOT_APPLICABLE;

        boolean picked = pickedTableId != null && pickedTableId > 0L;

        // Mencampur tipe TIDAK dilarang secara umum - bungkus plus antar dalam
        // satu bon tidak merusak apa pun, dan papan dapur tetap benar karena
        // setiap item restoran punya alur dapurnya sendiri. Yang dilarang hanya
        // kombinasi yang merusak: bon yang memegang sebuah meja.
        if (picked && hasOtherTypeItem) return Decision.MIXED_WITH_TABLE;

        if (picked) return Decision.USE_PICKED_TABLE;

        // Jumlah meja yang belum diketahui (-1) sengaja TIDAK memblokir:
        // memblokir berdasarkan tebakan bisa mengunci kasir di toko yang
        // memang belum punya meja sama sekali.
        boolean pickerUsable = tablesModuleOn && activeTableCount > 0;
        if (pickerUsable) return Decision.BLOCK_UNTIL_PICKED;

        if (tableNumberFieldOn) return Decision.ASK_FREE_TEXT;

        return Decision.NO_TABLE_RECORDED;
    }

    /** Apakah dialog pembayaran menampilkan kolom "Nomor meja". */
    public static boolean needsFreeTextField(@NonNull Decision decision) {
        return decision == Decision.ASK_FREE_TEXT;
    }

    /** Apakah "Lanjut bayar" harus ditahan. */
    public static boolean blocksCheckout(@NonNull Decision decision) {
        return decision == Decision.BLOCK_UNTIL_PICKED
                || decision == Decision.MIXED_WITH_TABLE;
    }
}
