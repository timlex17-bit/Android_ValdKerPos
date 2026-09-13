package com.valdker.pos.print;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.valdker.pos.money.Money;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Satu order yang dicetak dua kali harus menghasilkan kertas yang sama.
 *
 * <p>Dulu tidak: struk asli disusun dari objek hasil checkout, struk cetak
 * ulang disusun dari payload tersimpan, dan keduanya sudah menyimpang -
 * struk asli mencetak "Discount $0.00" mati sementara cetak ulang mencetak
 * angka sebenarnya, dan cetak ulang hanya menampilkan pembayaran pertama.
 *
 * <p>Sekarang keduanya membaca payload yang sama lewat ReceiptPayloadReader
 * dan disusun ReceiptBuilder, jadi satu-satunya yang boleh berbeda adalah
 * spanduk status di kepala - dan uji ini yang menjaganya tetap begitu.
 */
public class ReceiptReprintIdentityTest {

    private static final String REPRINT_BANNER = "OFFLINE / PENDING SYNC";
    private static final int PAPER_58MM = 32;

    @Test
    public void reprintMatchesTheOriginalLineByLine() throws Exception {
        JSONObject payload = orderPayload();

        List<String> original = paper(render(payload, ""));
        List<String> reprint = paper(render(payload, REPRINT_BANNER));

        // Spanduk status menambah dua baris di kepala: spanduknya sendiri dan
        // satu garis pemisah. Selain itu tidak boleh ada bedanya.
        int bannerAt = -1;
        for (int i = 0; i < reprint.size(); i++) {
            if (reprint.get(i).contains(REPRINT_BANNER)) {
                bannerAt = i;
                break;
            }
        }
        assertTrue("spanduk status harus tercetak", bannerAt >= 0);

        List<String> reprintWithoutBanner = new ArrayList<>(reprint);
        reprintWithoutBanner.remove(bannerAt + 1);
        reprintWithoutBanner.remove(bannerAt);

        assertEquals("jumlah baris harus sama", original.size(), reprintWithoutBanner.size());
        for (int i = 0; i < original.size(); i++) {
            assertEquals("baris ke-" + i + " berbeda",
                    original.get(i), reprintWithoutBanner.get(i));
        }
    }

    @Test
    public void bothPapersShowTheRealDiscountAndTax() throws Exception {
        JSONObject payload = orderPayload();

        for (String banner : new String[]{"", REPRINT_BANNER}) {
            List<String> paper = paper(render(payload, banner));
            assertTrue(lineStartingWith(paper, "Discount").endsWith("$1.50"));
            assertTrue(lineStartingWith(paper, "VAT / Tax").endsWith("$0.94"));
            assertTrue(lineStartingWith(paper, "Total").endsWith("$9.44"));
        }
    }

    @Test
    public void bothPapersShowEverySplitPaymentByName() throws Exception {
        JSONObject payload = orderPayload();

        for (String banner : new String[]{"", REPRINT_BANNER}) {
            List<String> paper = paper(render(payload, banner));
            assertTrue(lineStartingWith(paper, "- CASH").endsWith("$7.00"));
            assertTrue(lineStartingWith(paper, "- BANK_TRANSFER").endsWith("$2.44"));
            assertFalse("nama metode tidak boleh jatuh ke nomor id",
                    hasLineStartingWith(paper, "- Method #"));
        }
    }

    @Test
    public void bothPapersShowTableAndWaiterForDineIn() throws Exception {
        JSONObject payload = orderPayload();

        for (String banner : new String[]{"", REPRINT_BANNER}) {
            List<String> paper = paper(render(payload, banner));
            assertTrue(lineStartingWith(paper, "Table:").endsWith("12"));
            assertTrue(lineStartingWith(paper, "Waiter:").endsWith("Ana"));
        }
    }

    @Test
    public void localReceiptKeysNeverReachTheServer() throws Exception {
        JSONObject payload = orderPayload();

        assertTrue(payload.has(ReceiptPayloadReader.KEY_WAITER_NAME));
        assertTrue(payload.has(ReceiptPayloadReader.KEY_PAYMENT_LABELS));
        assertTrue(payload.has(ReceiptPayloadReader.KEY_CASH_RECEIVED));

        ReceiptPayloadReader.stripLocalKeys(payload);

        assertFalse(payload.has(ReceiptPayloadReader.KEY_WAITER_NAME));
        assertFalse(payload.has(ReceiptPayloadReader.KEY_CUSTOMER_NAME));
        assertFalse(payload.has(ReceiptPayloadReader.KEY_PAYMENT_LABELS));
        assertFalse(payload.has(ReceiptPayloadReader.KEY_CASH_RECEIVED));
        assertFalse(payload.has(ReceiptPayloadReader.KEY_CHANGE));

        // Yang dikirim ke server harus tetap utuh.
        assertEquals("9.44", payload.optString("total"));
        assertEquals(2, payload.optJSONArray("payments").length());
    }

    @Test
    public void dateAndTimeComeFromTheSaleNotFromThePrintingMoment() throws Exception {
        JSONObject payload = orderPayload();

        ReceiptContent content = ReceiptPayloadReader.read(payload);

        assertEquals("13/09/26", content.date);
        assertEquals("16:09", content.time);
    }

    // ------------------------------------------------------------------

    /**
     * Payload seperti yang dibangun CartFragment, lengkap dengan kunci lokal
     * untuk struk.
     */
    private static JSONObject orderPayload() throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("client_order_id", "OFFLINE-1");
        payload.put("device_time", "2026-09-13T16:09:33+09:00");
        payload.put("payment_method", "CASH");
        payload.put("subtotal", "10.00");
        payload.put("discount", "1.50");
        payload.put("tax", "0.94");
        payload.put("total", "9.44");
        payload.put("default_order_type", "DINE_IN");
        payload.put("table_number", "12");

        JSONArray items = new JSONArray();
        JSONObject item = new JSONObject();
        item.put("name", "Kopi Susu");
        item.put("quantity", 2);
        item.put("price", "1.75");
        item.put("total", "3.50");
        item.put("order_type", "DINE_IN");
        items.put(item);

        JSONObject item2 = new JSONObject();
        item2.put("name", "Nasi Goreng Spesial Pedas Telur Dadar");
        item2.put("quantity", 1);
        item2.put("price", "6.50");
        item2.put("total", "6.50");
        item2.put("order_type", "DINE_IN");
        items.put(item2);
        payload.put("items", items);

        JSONArray payments = new JSONArray();
        JSONObject primary = new JSONObject();
        primary.put("payment_method_id", 1);
        primary.put("amount", "7.00");
        payments.put(primary);
        JSONObject split = new JSONObject();
        split.put("payment_method_id", 3);
        split.put("amount", "2.44");
        payments.put(split);
        payload.put("payments", payments);

        JSONArray labels = new JSONArray();
        labels.put("CASH");
        labels.put("BANK_TRANSFER");

        return ReceiptPayloadReader.withLocalExtras(
                payload, "Ana", "Maria", labels, Money.of("10.00"), Money.of("0.56"));
    }

    /**
     * Meniru kedua jalur cetak: keduanya membaca payload yang sama lalu
     * menambahkan identitas toko, nama kasir, dan spanduk status.
     */
    private static String render(JSONObject payload, String banner) {
        ReceiptContent content = ReceiptPayloadReader.read(payload);
        content.shopName = "Toko Uji";
        content.shopAddress = "Jalan Uji 1";
        content.cashier = "kasir";
        content.statusBanner = banner;
        content.footerNote = "Thank you!";
        return ReceiptBuilder.build(content);
    }

    private static List<String> paper(String receipt) {
        List<String> out = new ArrayList<>();
        for (String raw : receipt.split("\n", -1)) {
            out.add(ReceiptLayout.layout(raw, PAPER_58MM).text);
        }
        return out;
    }

    private static String lineStartingWith(List<String> paper, String prefix) {
        for (String line : paper) {
            if (line.startsWith(prefix)) return line;
        }
        throw new AssertionError("tidak ada baris yang dimulai dengan: " + prefix + "\n" + paper);
    }

    private static boolean hasLineStartingWith(List<String> paper, String prefix) {
        for (String line : paper) {
            if (line.startsWith(prefix)) return true;
        }
        return false;
    }
}
