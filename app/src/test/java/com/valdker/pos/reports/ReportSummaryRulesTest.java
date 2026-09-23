package com.valdker.pos.reports;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.valdker.pos.R;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Mengunci nama field yang dicari kartu ringkasan.
 *
 * <p>Uji ini ada karena kesalahannya tidak terlihat. Sebuah kartu yang mencari
 * nama field yang salah tidak menampilkan galat, tidak kosong, dan tidak
 * mencurigakan - ia menampilkan {@code $0.00}. Pemilik bengkel membaca
 * "Pendapatan Jasa: $0.00" sebagai "hari ini tidak ada servis", padahal
 * artinya "aplikasi mencari nama yang tidak pernah dikirim server".
 *
 * <p>Nama yang benar diambil dari {@code pos/api_reports.py} fungsi
 * {@code _sales_summary}, {@code _report_payments}, {@code _report_shifts},
 * dan {@code _report_dashboard_summary} di repo Django.
 */
public class ReportSummaryRulesTest {

    private static List<String> keysOf(List<ReportSummaryRules.Card> cards, int labelRes) {
        for (ReportSummaryRules.Card card : cards) {
            if (card.labelRes == labelRes) return Arrays.asList(card.keys);
        }
        return new ArrayList<>();
    }

    private static boolean hasCard(List<ReportSummaryRules.Card> cards, int labelRes) {
        for (ReportSummaryRules.Card card : cards) {
            if (card.labelRes == labelRes) return true;
        }
        return false;
    }

    // -------------------------------------------------------------- bengkel

    /**
     * Inti perbaikannya: nama yang dikirim server adalah
     * {@code total_service_revenue}, bukan {@code service_revenue}.
     */
    @Test
    public void workshopRevenueCardsUseTheKeysTheServerActuallySends() {
        List<ReportSummaryRules.Card> cards =
                ReportSummaryRules.cardsFor("sales", "workshop");

        assertTrue(keysOf(cards, R.string.report_card_service_revenue)
                .contains("total_service_revenue"));
        assertTrue(keysOf(cards, R.string.report_card_sparepart_revenue)
                .contains("total_sparepart_revenue"));
        assertTrue(keysOf(cards, R.string.report_card_menu_revenue)
                .contains("total_menu_revenue"));
    }

    /** Nama lama tetap dicoba, supaya server versi lama tidak jadi kosong. */
    @Test
    public void workshopStillFallsBackToTheOlderKeyNames() {
        List<ReportSummaryRules.Card> cards =
                ReportSummaryRules.cardsFor("sales", "workshop");

        List<String> service = keysOf(cards, R.string.report_card_service_revenue);
        assertTrue(service.contains("service_revenue"));
        assertTrue("nama yang benar harus dicoba lebih dulu",
                service.indexOf("total_service_revenue") < service.indexOf("service_revenue"));
    }

    @Test
    public void workshopDoesNotShowRetailOnlyCards() {
        List<ReportSummaryRules.Card> cards =
                ReportSummaryRules.cardsFor("sales", "workshop");
        assertFalse(hasCard(cards, R.string.report_card_margin));
        assertFalse(hasCard(cards, R.string.report_card_dine_in_revenue));
    }

    // ---------------------------------------------------------------- ritel

    @Test
    public void retailItemsSoldAndMarginUseTheKeysTheServerSends() {
        List<ReportSummaryRules.Card> cards =
                ReportSummaryRules.cardsFor("sales", "retail");

        assertTrue(keysOf(cards, R.string.report_card_product_sold)
                .contains("total_product_sold"));
        assertTrue(keysOf(cards, R.string.report_card_margin)
                .contains("profit_margin_percent"));
    }

    @Test
    public void countsAreNotFormattedAsMoney() {
        List<ReportSummaryRules.Card> cards =
                ReportSummaryRules.cardsFor("sales", "retail");
        for (ReportSummaryRules.Card card : cards) {
            if (card.labelRes == R.string.report_card_product_sold
                    || card.labelRes == R.string.report_card_orders) {
                assertEquals(ReportSummaryRules.Format.COUNT, card.format);
            }
        }
    }

    // ------------------------------------------------------------- restoran

    @Test
    public void restaurantKeepsItsOrderTypeRevenueCards() {
        List<ReportSummaryRules.Card> cards =
                ReportSummaryRules.cardsFor("sales", "restaurant");

        assertTrue(keysOf(cards, R.string.report_card_dine_in_revenue).contains("dine_in_revenue"));
        assertTrue(keysOf(cards, R.string.report_card_takeaway_revenue).contains("takeaway_revenue"));
        assertTrue(keysOf(cards, R.string.report_card_delivery_revenue).contains("delivery_revenue"));
        assertFalse(hasCard(cards, R.string.report_card_service_revenue));
    }

    // ------------------------------------------------- laporan non-penjualan

    /**
     * Dulu ketiga laporan ini jatuh ke pembuangan kunci mentah: apa pun yang
     * dikirim server, dipotong enam, urutannya sesuka JSON, dan uangnya tampil
     * tanpa simbol mata uang.
     */
    @Test
    public void paymentsShiftsAndDailyHaveCuratedCardsInsteadOfARawDump() {
        List<ReportSummaryRules.Card> payments = ReportSummaryRules.cardsFor("payments", "retail");
        assertTrue(keysOf(payments, R.string.report_card_total_received).contains("total_payments"));

        List<ReportSummaryRules.Card> shifts = ReportSummaryRules.cardsFor("shifts", "retail");
        assertTrue(keysOf(shifts, R.string.report_card_shift_count).contains("shift_count"));
        assertTrue(keysOf(shifts, R.string.report_card_total_sales).contains("total_sales"));

        List<ReportSummaryRules.Card> daily = ReportSummaryRules.cardsFor("daily", "retail");
        assertTrue(keysOf(daily, R.string.report_card_expenses).contains("total_expenses"));
        assertTrue(keysOf(daily, R.string.report_card_net_profit).contains("net_profit"));
    }

    /** Jumlah shift adalah cacah, bukan uang. */
    @Test
    public void shiftCountIsACount() {
        List<ReportSummaryRules.Card> shifts = ReportSummaryRules.cardsFor("shifts", "retail");
        for (ReportSummaryRules.Card card : shifts) {
            if (card.labelRes == R.string.report_card_shift_count) {
                assertEquals(ReportSummaryRules.Format.COUNT, card.format);
            }
        }
    }

    // ---------------------------------------------------------------- judul

    @Test
    public void itemReportIsNamedInTheLanguageOfEachBusiness() {
        assertEquals(R.string.report_title_items_retail,
                ReportSummaryRules.titleResFor("items", "retail"));
        assertEquals(R.string.report_title_items_restaurant,
                ReportSummaryRules.titleResFor("items", "restaurant"));
        assertEquals(R.string.report_title_items_workshop,
                ReportSummaryRules.titleResFor("items", "workshop"));
    }

    @Test
    public void businessLabelNeverLeaksTheServerEnum() {
        assertEquals(R.string.business_type_workshop,
                ReportSummaryRules.businessLabelRes("workshop"));
        assertEquals(R.string.business_type_restaurant,
                ReportSummaryRules.businessLabelRes("RESTAURANT"));
        assertEquals(R.string.business_type_retail,
                ReportSummaryRules.businessLabelRes(null));
        assertEquals(R.string.business_type_workshop,
                ReportSummaryRules.businessLabelRes("general_workshop"));
    }

    @Test
    public void unknownBusinessTypeFallsBackToRetailCards() {
        List<ReportSummaryRules.Card> cards = ReportSummaryRules.cardsFor("sales", "kios-ajaib");
        assertTrue(hasCard(cards, R.string.report_card_gross_profit));
        assertTrue(hasCard(cards, R.string.report_card_total_revenue));
    }
}
