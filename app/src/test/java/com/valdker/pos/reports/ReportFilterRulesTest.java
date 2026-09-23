package com.valdker.pos.reports;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/**
 * Penyaring mana yang boleh tampil, dan dikirim dengan nama apa.
 *
 * <p>Daftar {@code accepted} di bawah disalin dari {@code _filters_payload} di
 * {@code pos/api_reports.py}. Nama yang salah tidak menghasilkan galat: server
 * mengabaikan parameter yang tidak dikenalnya, laporan kembali utuh, dan
 * pengguna menyimpulkan penyaringnya rusak.
 */
public class ReportFilterRulesTest {

    // ------------------------------------------------------------- kategori

    @Test
    public void retailSendsCategoryAsCategoryId() {
        ReportFilterRules rules = ReportFilterRules.of(Arrays.asList(
                "start_date", "end_date", "payment_method", "search",
                "product_id", "category_id", "supplier_id"), "items");

        assertTrue(rules.showsCategoryFilter());
        assertEquals("category_id", rules.categoryFilterKey());
    }

    /**
     * Restoran memakai nama yang berbeda untuk hal yang sama. Sebelumnya
     * kolom kategori malah disembunyikan sepenuhnya untuk restoran, jadi
     * penyaring yang didukung server tidak pernah bisa dipakai.
     */
    @Test
    public void restaurantSendsCategoryAsMenuCategory() {
        ReportFilterRules rules = ReportFilterRules.of(Arrays.asList(
                "start_date", "end_date", "payment_method", "search",
                "order_type", "table_number", "menu_category"), "items");

        assertTrue(rules.showsCategoryFilter());
        assertEquals("menu_category", rules.categoryFilterKey());
    }

    @Test
    public void workshopHasNoCategoryFilterAtAll() {
        ReportFilterRules rules = ReportFilterRules.of(Arrays.asList(
                "start_date", "end_date", "payment_method", "search",
                "item_type", "vehicle_plate", "mechanic_id"), "items");

        assertFalse(rules.showsCategoryFilter());
    }

    // --------------------------------------------------------------- produk

    @Test
    public void retailPrefersProductIdOverFreeTextSearch() {
        ReportFilterRules rules = ReportFilterRules.of(Arrays.asList("search", "product_id"), "items");

        assertTrue(rules.showsProductFilter());
        assertTrue(rules.productFilterUsesId());
        assertEquals("product_id", rules.productFilterKey());
    }

    /**
     * Tanpa {@code product_id}, kolom produk disembunyikan - bukan jatuh ke
     * {@code search}. Server menerapkan {@code search} dua kali pada laporan
     * item (tingkat pesanan lalu tingkat baris, digabung AND), sehingga nama
     * produk apa pun selalu menghasilkan laporan kosong.
     */
    @Test
    public void withoutProductIdTheProductFilterIsHiddenInsteadOfSilentlyFailing() {
        ReportFilterRules rules = ReportFilterRules.of(Arrays.asList("search", "item_type"), "items");

        assertFalse(rules.showsProductFilter());
        assertFalse(rules.productFilterUsesId());
    }

    @Test
    public void aReportThatAcceptsNeitherHidesTheProductFilter() {
        ReportFilterRules rules = ReportFilterRules.of(Arrays.asList("start_date", "end_date"), "items");
        assertFalse(rules.showsProductFilter());
    }

    // ----------------------------------------------------------- pembayaran

    @Test
    public void paymentFilterFollowsTheServer() {
        assertTrue(ReportFilterRules.of(Collections.singletonList("payment_method"), "items")
                .showsPaymentFilter());
        assertFalse(ReportFilterRules.of(Collections.singletonList("start_date"), "items")
                .showsPaymentFilter());
    }

    // -------------------------------------------------------------- cadangan

    @Test
    public void fallbackMatchesWhatEachBusinessTypeActuallyAccepts() {
        ReportFilterRules retail = ReportFilterRules.fallbackFor("retail", "items");
        assertEquals("category_id", retail.categoryFilterKey());
        assertTrue(retail.productFilterUsesId());

        ReportFilterRules restaurant = ReportFilterRules.fallbackFor("restaurant", "items");
        assertEquals("menu_category", restaurant.categoryFilterKey());
        assertFalse("restoran tidak menerima product_id", restaurant.productFilterUsesId());

        ReportFilterRules workshop = ReportFilterRules.fallbackFor("workshop", "items");
        assertFalse(workshop.showsCategoryFilter());
        assertFalse("bengkel tidak menerima product_id", workshop.showsProductFilter());
        assertTrue(workshop.showsPaymentFilter());
    }

    @Test
    public void unknownBusinessTypeFallsBackToRetail() {
        ReportFilterRules rules = ReportFilterRules.fallbackFor("kios-ajaib", "items");
        assertEquals("category_id", rules.categoryFilterKey());
    }

    @Test
    public void generalWorkshopIsTreatedLikeWorkshop() {
        ReportFilterRules rules = ReportFilterRules.fallbackFor("general_workshop", "items");
        assertFalse(rules.showsCategoryFilter());
    }

    // ------------------------------------------------------- bentuk masukan

    @Test
    public void namesAreMatchedCaseInsensitivelyAndTrimmed() {
        ReportFilterRules rules = ReportFilterRules.of(
                Arrays.asList("  Category_ID  ", "PRODUCT_ID"), "items");
        assertTrue(rules.accepts("category_id"));
        assertTrue(rules.showsProductFilter());
    }

    @Test
    public void emptyAcceptedListShowsNothingRatherThanEverything() {
        ReportFilterRules rules = ReportFilterRules.of(Collections.emptyList(), "items");
        assertFalse(rules.showsCategoryFilter());
        assertFalse(rules.showsProductFilter());
        assertFalse(rules.showsPaymentFilter());
    }

    // --------------------------------------------- penyaring per tingkat

    /**
     * Semua laporan menerima {@code search}, tetapi artinya berbeda. Pada
     * laporan Penjualan server mencocokkan nomor invoice, nama pelanggan, dan
     * metode pembayaran - bukan nama barang. Kolom "Produk" di sana akan
     * menerima pilihan pengguna, mengirimnya, lalu mengembalikan laporan
     * kosong tanpa penjelasan apa pun.
     */
    @Test
    public void productAndCategoryFiltersOnlyAppearOnTheItemLevelReport() {
        for (String report : new String[]{"sales", "daily", "payments", "shifts"}) {
            ReportFilterRules rules = ReportFilterRules.of(
                    Arrays.asList("search", "product_id", "category_id"), report);
            assertFalse(report, rules.showsProductFilter());
            assertFalse(report, rules.showsCategoryFilter());
        }

        ReportFilterRules items = ReportFilterRules.of(
                Arrays.asList("search", "product_id", "category_id"), "items");
        assertTrue(items.showsProductFilter());
        assertTrue(items.showsCategoryFilter());
    }

    /** Metode pembayaran bekerja di tingkat pesanan, jadi selalu tersedia. */
    @Test
    public void paymentFilterStaysAvailableOnEveryReport() {
        for (String report : new String[]{"sales", "daily", "payments", "shifts", "items"}) {
            ReportFilterRules rules = ReportFilterRules.of(
                    Collections.singletonList("payment_method"), report);
            assertTrue(report, rules.showsPaymentFilter());
        }
    }

    @Test
    public void nullReportTypeIsNotTreatedAsItemLevel() {
        ReportFilterRules rules = ReportFilterRules.of(Arrays.asList("search", "product_id"), null);
        assertFalse(rules.isItemLevelReport());
        assertFalse(rules.showsProductFilter());
    }
}
