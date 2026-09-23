package com.valdker.pos.reports;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Penyaring mana yang sah untuk laporan yang sedang dibuka.
 *
 * <p>Server sudah menjawab pertanyaan ini sendiri: setiap respons laporan
 * membawa {@code filters.accepted}, daftar nama penyaring yang benar-benar
 * dibaca endpoint itu untuk toko ini ({@code pos/api_reports.py},
 * {@code _filters_payload}). Daftarnya berbeda per jenis usaha - ritel punya
 * {@code category_id} dan {@code product_id}, restoran punya
 * {@code menu_category} dan {@code order_type}, bengkel punya
 * {@code item_type} - dan aplikasi selama ini mengabaikannya sepenuhnya.
 *
 * <p>Akibatnya pemilik toko restoran melihat kolom "Category" yang, kalaupun
 * diisi benar, dikirim sebagai {@code category_id} dan diabaikan diam-diam
 * oleh server; laporannya kembali utuh seolah penyaringnya tidak berpengaruh.
 * Yang membingungkan bukan angkanya, melainkan kolom yang ada tapi tidak
 * bekerja.
 *
 * <p>Kelas ini Java murni supaya bisa diuji tanpa perangkat, dan menjadi satu
 * tempat kebenaran: layar hanya menanyakan "tampilkan kolom ini?" dan "kirim
 * dengan nama apa?".
 */
public final class ReportFilterRules {

    /** Nama penyaring persis seperti yang dibaca server. */
    public static final String KEY_SEARCH = "search";
    public static final String KEY_PRODUCT_ID = "product_id";
    public static final String KEY_CATEGORY_ID = "category_id";
    public static final String KEY_MENU_CATEGORY = "menu_category";
    public static final String KEY_PAYMENT_METHOD = "payment_method";

    /** Laporan yang menyaring pada tingkat BARIS ITEM, bukan tingkat pesanan. */
    private static final String REPORT_ITEMS = "items";

    private final Set<String> accepted = new LinkedHashSet<>();
    private final String reportType;

    private ReportFilterRules(@NonNull Collection<String> accepted, @Nullable String reportType) {
        for (String key : accepted) {
            if (key == null) continue;
            String clean = key.trim().toLowerCase(Locale.US);
            if (!clean.isEmpty()) this.accepted.add(clean);
        }
        this.reportType = reportType == null ? "" : reportType.trim().toLowerCase(Locale.US);
    }

    @NonNull
    public static ReportFilterRules of(@NonNull Collection<String> accepted,
                                       @Nullable String reportType) {
        return new ReportFilterRules(accepted, reportType);
    }

    /**
     * Apakah laporan ini menyaring per baris item.
     *
     * <p>Pembedaan ini penting dan tidak terlihat dari daftar
     * {@code filters.accepted} saja. Semua laporan menerima {@code search},
     * tetapi artinya berbeda: pada laporan Items ia mencocokkan nama, kode,
     * dan SKU barang; pada laporan lain ia mencocokkan nomor invoice, nama
     * pelanggan, dan metode pembayaran ({@code _apply_order_filters} di
     * {@code pos/api_reports.py}).
     *
     * <p>Karena itu kolom "Produk" hanya ditampilkan di laporan Items.
     * Menampilkannya di laporan Penjualan berarti memberi pemilik toko sebuah
     * penyaring yang menerima pilihannya, mengirimnya, lalu mengembalikan
     * laporan kosong - tanpa satu pun petunjuk bahwa yang dicari server adalah
     * nomor invoice, bukan nama barang.
     */
    public boolean isItemLevelReport() {
        return REPORT_ITEMS.equals(reportType);
    }

    /**
     * Tebakan sebelum respons pertama datang.
     *
     * <p>Layar harus menggambar sesuatu sebelum permintaan pertama selesai.
     * Nilai di sini menyalin {@code _filters_payload} apa adanya, dan akan
     * digantikan oleh jawaban server begitu ia tiba - jadi kalau backend
     * berubah, yang usang hanyalah tampilan sepersekian detik pertama, bukan
     * perilakunya.
     */
    @NonNull
    public static ReportFilterRules fallbackFor(@Nullable String businessType,
                                                @Nullable String reportType) {
        String type = businessType == null ? "" : businessType.trim().toLowerCase(Locale.US);

        if (type.startsWith("workshop") || type.startsWith("general_workshop")) {
            return of(Arrays.asList(KEY_SEARCH, KEY_PAYMENT_METHOD, "item_type"), reportType);
        }
        if (type.startsWith("restaurant")) {
            return of(Arrays.asList(KEY_SEARCH, KEY_PAYMENT_METHOD, KEY_MENU_CATEGORY, "order_type"),
                    reportType);
        }
        return of(Arrays.asList(KEY_SEARCH, KEY_PAYMENT_METHOD, KEY_PRODUCT_ID, KEY_CATEGORY_ID),
                reportType);
    }

    public boolean accepts(@NonNull String key) {
        return accepted.contains(key.trim().toLowerCase(Locale.US));
    }

    // ------------------------------------------------------------- produk

    /**
     * Penyaring produk hanya ditampilkan kalau server menerima
     * {@code product_id}.
     *
     * <p>{@code search} sengaja TIDAK dipakai sebagai cadangan, meski ia ada
     * di daftar {@code accepted} untuk semua laporan. Penyebabnya ada di
     * {@code _apply_item_filters} ({@code pos/api_reports.py}): ia memanggil
     * {@code _apply_order_filters} lebih dulu - yang mencocokkan {@code search}
     * dengan nomor invoice, nama pelanggan, dan metode pembayaran - lalu
     * mencocokkannya SEKALI LAGI dengan nama barang. Keduanya digabung dengan
     * AND, jadi sebuah nama produk hanya lolos bila nama itu kebetulan juga
     * muncul di nomor invoice atau nama pelanggan pesanan yang sama.
     *
     * <p>Akibatnya, pada toko yang tidak menerima {@code product_id} (bengkel
     * dan restoran), memilih produk apa pun selalu mengembalikan laporan
     * kosong. Menyembunyikan kolomnya lebih jujur daripada menawarkan
     * penyaring yang tidak mungkin berhasil.
     */
    public boolean showsProductFilter() {
        if (!isItemLevelReport()) return false;
        return accepts(KEY_PRODUCT_ID);
    }

    /**
     * Nama penyaring untuk produk yang dipilih.
     *
     * <p>{@code product_id} lebih tepat - ia mencocokkan satu baris - jadi
     * dipakai kalau tersedia. Kalau tidak, {@code search} menjadi cadangan:
     * ia mencocokkan nama, kode, dan SKU, jadi nama produk yang dipilih tetap
     * menemukan barangnya.
     */
    @NonNull
    public String productFilterKey() {
        return accepts(KEY_PRODUCT_ID) ? KEY_PRODUCT_ID : KEY_SEARCH;
    }

    /** True bila yang harus dikirim adalah id produk, bukan namanya. */
    public boolean productFilterUsesId() {
        return accepts(KEY_PRODUCT_ID);
    }

    // ----------------------------------------------------------- kategori

    public boolean showsCategoryFilter() {
        if (!isItemLevelReport()) return false;
        return accepts(KEY_CATEGORY_ID) || accepts(KEY_MENU_CATEGORY);
    }

    /**
     * Restoran memakai {@code menu_category}, ritel memakai
     * {@code category_id}. Keduanya menerima id kategori, jadi yang berbeda
     * hanya namanya - dan mengirim nama yang salah berarti penyaringnya
     * diabaikan tanpa pesan galat apa pun.
     */
    @NonNull
    public String categoryFilterKey() {
        return accepts(KEY_CATEGORY_ID) ? KEY_CATEGORY_ID : KEY_MENU_CATEGORY;
    }

    // ---------------------------------------------------------- pembayaran

    public boolean showsPaymentFilter() {
        return accepts(KEY_PAYMENT_METHOD);
    }
}
