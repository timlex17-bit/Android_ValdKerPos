package com.valdker.pos.print;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.valdker.pos.money.Money;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Isi struk: diskon, pajak, pelayan, dan pembayaran terbagi.
 *
 * <p>Ketiganya pernah salah dengan cara yang tidak kelihatan dari kode:
 * Discount dan VAT/Tax dicetak "$0.00" mati padahal Total sudah dipotong
 * diskon, nama pelayan tidak pernah ada parameternya, dan dari beberapa
 * pembayaran hanya yang pertama yang tercetak.
 */
public class ReceiptBuilderTest {

    private static final int PAPER_58MM = 32;

    @Test
    public void discountAndTaxArePrintedWithTheirRealValues() {
        ReceiptContent c = order();
        c.subtotal = Money.of("10.00");
        c.discount = Money.of("1.50");
        c.tax = Money.of("0.94");
        c.total = Money.of("9.44");

        List<String> paper = paper(ReceiptBuilder.build(c));

        String discount = lineStartingWith(paper, "Discount");
        String tax = lineStartingWith(paper, "VAT / Tax");

        assertTrue("diskon harus angka sebenarnya: " + discount, discount.endsWith("$1.50"));
        assertTrue("pajak harus angka sebenarnya: " + tax, tax.endsWith("$0.94"));
        assertFalse("tidak boleh ada nol mati untuk nilai yang bukan nol",
                discount.endsWith("$0.00") || tax.endsWith("$0.00"));
    }

    @Test
    public void thePaperAddsUpToItsOwnTotal() {
        ReceiptContent c = order();
        c.subtotal = Money.of("10.00");
        c.discount = Money.of("1.50");
        c.tax = Money.of("0.94");
        c.deliveryFee = Money.of("2.00");
        c.total = Money.of("11.44");

        // subtotal - diskon + pajak + ongkir = total
        Money recomputed = c.subtotal.minus(c.discount).plus(c.tax).plus(c.deliveryFee);
        assertEquals(c.total, recomputed);

        List<String> paper = paper(ReceiptBuilder.build(c));
        assertTrue(lineStartingWith(paper, "Subtotal").endsWith("$10.00"));
        assertTrue(lineStartingWith(paper, "Discount").endsWith("$1.50"));
        assertTrue(lineStartingWith(paper, "VAT / Tax").endsWith("$0.94"));
        assertTrue(lineStartingWith(paper, "Delivery Fee").endsWith("$2.00"));
        assertTrue(lineStartingWith(paper, "Total").endsWith("$11.44"));
    }

    @Test
    public void zeroDiscountAndTaxRowsAreLeftOutEntirely() {
        ReceiptContent c = order();
        c.subtotal = Money.of("10.00");
        c.total = Money.of("10.00");

        List<String> paper = paper(ReceiptBuilder.build(c));

        assertFalse("baris diskon nol tidak perlu dicetak", hasLineStartingWith(paper, "Discount"));
        assertFalse("baris pajak nol tidak perlu dicetak", hasLineStartingWith(paper, "VAT / Tax"));
        assertTrue(hasLineStartingWith(paper, "Subtotal"));
    }

    @Test
    public void everySplitPaymentIsPrinted() {
        ReceiptContent c = order();
        c.total = Money.of("15.00");
        c.addPayment("CASH", Money.of("10.00"));
        c.addPayment("BANK_TRANSFER", Money.of("5.00"));

        List<String> paper = paper(ReceiptBuilder.build(c));

        assertTrue(lineStartingWith(paper, "Payment").endsWith("Split"));
        assertTrue("metode pertama harus tercetak beserta nominalnya",
                lineStartingWith(paper, "- CASH").endsWith("$10.00"));
        assertTrue("metode kedua tidak boleh hilang",
                lineStartingWith(paper, "- BANK_TRANSFER").endsWith("$5.00"));
    }

    @Test
    public void singlePaymentKeepsTheSimpleRow() {
        ReceiptContent c = order();
        c.total = Money.of("15.00");
        c.addPayment("CASH", Money.of("15.00"));

        List<String> paper = paper(ReceiptBuilder.build(c));
        assertTrue(lineStartingWith(paper, "Payment").endsWith("CASH"));
    }

    @Test
    public void waiterIsPrintedForDineInOnly() {
        ReceiptContent dineIn = order();
        dineIn.dineIn = true;
        dineIn.tableNumber = "12";
        dineIn.waiterName = "Ana";

        List<String> dineInPaper = paper(ReceiptBuilder.build(dineIn));
        assertTrue(lineStartingWith(dineInPaper, "Table:").endsWith("12"));
        assertTrue(lineStartingWith(dineInPaper, "Waiter:").endsWith("Ana"));

        ReceiptContent takeOut = order();
        takeOut.dineIn = false;
        takeOut.tableNumber = "12";
        takeOut.waiterName = "Ana";

        List<String> takeOutPaper = paper(ReceiptBuilder.build(takeOut));
        assertFalse("order bungkus tidak punya meja", hasLineStartingWith(takeOutPaper, "Table:"));
        assertFalse("order bungkus tidak punya pelayan", hasLineStartingWith(takeOutPaper, "Waiter:"));
    }

    @Test
    public void separatorsFollowThePaperWidth() {
        ReceiptContent c = order();

        String receipt = ReceiptBuilder.build(c);
        assertTrue("pembuat struk hanya menulis token, bukan 32 setrip mati",
                receipt.contains(ReceiptLayout.SEPARATOR));

        for (String line : paper(receipt, 48)) {
            if (line.startsWith("---")) {
                assertEquals(48, line.length());
            }
        }
        for (String line : paper(receipt, 32)) {
            if (line.startsWith("---")) {
                assertEquals(32, line.length());
            }
        }
    }

    @Test
    public void itemAmountsAreRightAlignedAgainstTheirNames() {
        ReceiptContent c = order();
        ReceiptContent.Item item = new ReceiptContent.Item();
        item.name = "Kopi Susu";
        item.qty = 2;
        item.unitPrice = Money.of("1.75");
        item.lineTotal = Money.of("3.50");
        c.items.add(item);

        List<String> paper = paper(ReceiptBuilder.build(c));

        String nameRow = lineStartingWith(paper, "Kopi Susu");
        assertEquals(PAPER_58MM, nameRow.length());
        assertTrue(nameRow.endsWith("$3.50"));
        assertTrue(hasLineStartingWith(paper, "2 x $1.75"));
    }

    // ------------------------------------------------------------------

    private static ReceiptContent order() {
        ReceiptContent c = new ReceiptContent();
        c.shopName = "Toko Uji";
        c.orderNumber = "INV-1";
        c.cashier = "kasir";
        return c;
    }

    private static List<String> paper(String receipt) {
        return paper(receipt, PAPER_58MM);
    }

    /** Teks yang benar-benar tercetak pada lebar kertas tertentu. */
    private static List<String> paper(String receipt, int width) {
        List<String> out = new ArrayList<>();
        for (String raw : receipt.split("\n", -1)) {
            out.add(ReceiptLayout.layout(raw, width).text);
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
