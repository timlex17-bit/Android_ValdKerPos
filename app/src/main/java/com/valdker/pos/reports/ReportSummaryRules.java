package com.valdker.pos.reports;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.valdker.pos.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Kartu ringkasan apa yang ditampilkan untuk tiap laporan dan tiap jenis usaha.
 *
 * <p>Dipisahkan dari layar karena dua alasan. Pertama, isinya adalah
 * pengetahuan tentang KONTRAK server - nama field yang dikirim
 * {@code pos/api_reports.py} - dan itu harus ada di satu tempat yang bisa
 * diuji, bukan tersebar sebagai string ajaib di tengah kode tampilan. Kedua,
 * nama-nama itu ternyata sudah salah sejak lama dan salahnya tidak kelihatan:
 *
 * <ul>
 *   <li>Bengkel mencari {@code service_revenue}, {@code sparepart_revenue},
 *       {@code menu_revenue}. Server mengirim {@code total_service_revenue},
 *       {@code total_sparepart_revenue}, {@code total_menu_revenue}. Tidak ada
 *       yang cocok, jadi ketiga kartu itu SELALU menampilkan $0.00 - bahkan
 *       ketika bengkelnya ramai.</li>
 *   <li>Ritel mencari {@code product_sold}/{@code qty} dan {@code margin}.
 *       Server mengirim {@code total_product_sold} dan
 *       {@code profit_margin_percent}. Sama: selalu 0.</li>
 * </ul>
 *
 * <p>Kartu yang menampilkan nol dengan percaya diri lebih berbahaya daripada
 * kartu yang hilang - pemilik toko tidak punya alasan mencurigainya. Karena
 * itu nama lama tetap disimpan sebagai cadangan (kalau-kalau versi server
 * lama masih dipakai), tetapi nama yang benar diperiksa lebih dulu.
 */
public final class ReportSummaryRules {

    /** Bagaimana sebuah nilai ditampilkan. */
    public enum Format {
        /** Uang; diberi simbol mata uang oleh pemanggil. */
        MONEY,
        /** Cacah - jumlah pesanan, jumlah barang. Tanpa simbol mata uang. */
        COUNT,
        /** Persentase. */
        PERCENT
    }

    /** Satu kartu: label yang dibaca, kunci yang dicari, dan cara menampilkan. */
    public static final class Card {
        public final int labelRes;
        @NonNull public final String[] keys;
        @NonNull public final Format format;

        Card(int labelRes, @NonNull Format format, @NonNull String... keys) {
            this.labelRes = labelRes;
            this.format = format;
            this.keys = keys;
        }
    }

    private ReportSummaryRules() {
    }

    /**
     * @param reportType   salah satu {@code ReportRepository.TYPE_*}
     * @param businessType retail / restaurant / workshop
     */
    @NonNull
    public static List<Card> cardsFor(@Nullable String reportType, @Nullable String businessType) {
        String report = normalize(reportType);
        String business = normalize(businessType);

        switch (report) {
            case "payments":
                return Arrays.asList(
                        new Card(R.string.report_card_total_received, Format.MONEY,
                                "total_payments", "total_revenue")
                );

            case "shifts":
                return Arrays.asList(
                        new Card(R.string.report_card_shift_count, Format.COUNT, "shift_count"),
                        new Card(R.string.report_card_total_sales, Format.MONEY,
                                "total_sales", "total_revenue")
                );

            case "daily":
                return Arrays.asList(
                        new Card(R.string.report_card_revenue, Format.MONEY,
                                "total_revenue", "sales_today", "total_sales"),
                        new Card(R.string.report_card_expenses, Format.MONEY,
                                "total_expenses", "total_expense", "expenses", "expense"),
                        new Card(R.string.report_card_net_profit, Format.MONEY,
                                "net_profit", "net_income", "net", "profit"),
                        new Card(R.string.report_card_orders, Format.COUNT, "total_orders"),
                        new Card(R.string.report_card_average_order, Format.MONEY,
                                "average_order_value")
                );

            default:
                return salesCards(business);
        }
    }

    @NonNull
    private static List<Card> salesCards(@NonNull String business) {
        List<Card> cards = new ArrayList<>();
        cards.add(new Card(R.string.report_card_total_revenue, Format.MONEY,
                "total_revenue", "revenue", "total_sales"));

        if (business.startsWith("workshop") || business.startsWith("general_workshop")) {
            cards.add(new Card(R.string.report_card_service_revenue, Format.MONEY,
                    "total_service_revenue", "service_revenue"));
            cards.add(new Card(R.string.report_card_sparepart_revenue, Format.MONEY,
                    "total_sparepart_revenue", "sparepart_revenue"));
            cards.add(new Card(R.string.report_card_menu_revenue, Format.MONEY,
                    "total_menu_revenue", "menu_revenue"));
            cards.add(new Card(R.string.report_card_service_jobs, Format.COUNT,
                    "total_service_jobs"));
        } else if (business.startsWith("restaurant")) {
            cards.add(new Card(R.string.report_card_dine_in_revenue, Format.MONEY,
                    "dine_in_revenue"));
            cards.add(new Card(R.string.report_card_takeaway_revenue, Format.MONEY,
                    "takeaway_revenue"));
            cards.add(new Card(R.string.report_card_delivery_revenue, Format.MONEY,
                    "delivery_revenue"));
            cards.add(new Card(R.string.report_card_delivery_fee, Format.MONEY,
                    "delivery_fee_total"));
        } else {
            cards.add(new Card(R.string.report_card_product_sold, Format.COUNT,
                    "total_product_sold", "product_sold", "products_sold"));
            cards.add(new Card(R.string.report_card_gross_profit, Format.MONEY,
                    "gross_profit", "profit"));
            cards.add(new Card(R.string.report_card_margin, Format.PERCENT,
                    "profit_margin_percent", "margin_percent", "margin"));
        }

        cards.add(new Card(R.string.report_card_orders, Format.COUNT, "total_orders"));
        cards.add(new Card(R.string.report_card_discount, Format.MONEY,
                "total_discount", "discount_total"));
        cards.add(new Card(R.string.report_card_net_sales, Format.MONEY,
                "net_sales", "net", "net_revenue"));
        return cards;
    }

    /**
     * Judul laporan dalam bahasa jenis usahanya sendiri.
     *
     * <p>"Product / Item Report" tidak berarti apa-apa bagi pemilik restoran
     * yang menjual menu, atau bengkel yang menjual jasa dan suku cadang.
     */
    public static int titleResFor(@Nullable String reportType, @Nullable String businessType) {
        String report = normalize(reportType);
        String business = normalize(businessType);

        switch (report) {
            case "daily":
                return R.string.report_title_daily;
            case "payments":
                return R.string.report_title_payments;
            case "shifts":
                return R.string.report_title_shifts;
            case "items":
                if (business.startsWith("restaurant")) return R.string.report_title_items_restaurant;
                if (business.startsWith("workshop") || business.startsWith("general_workshop")) {
                    return R.string.report_title_items_workshop;
                }
                return R.string.report_title_items_retail;
            default:
                return R.string.report_title_sales;
        }
    }

    /** Label chip pemilih laporan barang, mengikuti jenis usaha. */
    public static int itemsChipResFor(@Nullable String businessType) {
        String business = normalize(businessType);
        if (business.startsWith("restaurant")) return R.string.report_chip_items_restaurant;
        if (business.startsWith("workshop") || business.startsWith("general_workshop")) {
            return R.string.report_chip_items_workshop;
        }
        return R.string.report_chip_items;
    }

    /** Nama jenis usaha yang dibaca manusia, bukan enum server. */
    public static int businessLabelRes(@Nullable String businessType) {
        String business = normalize(businessType);
        if (business.startsWith("restaurant")) return R.string.business_type_restaurant;
        if (business.startsWith("workshop") || business.startsWith("general_workshop")) {
            return R.string.business_type_workshop;
        }
        return R.string.business_type_retail;
    }

    @NonNull
    private static String normalize(@Nullable String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
    }
}
