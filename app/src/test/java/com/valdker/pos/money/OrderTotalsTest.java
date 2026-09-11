package com.valdker.pos.money;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.math.BigDecimal;

public class OrderTotalsTest {

    private static final BigDecimal NO_TAX = BigDecimal.ZERO;
    private static final BigDecimal TAX_11 = new BigDecimal("11");

    private static OrderTotals totals(String subtotal, String discount, String delivery, BigDecimal pct) {
        return OrderTotals.of(Money.of(subtotal), Money.of(discount), Money.of(delivery), pct);
    }

    // ------------------------------------------------------------------ pajak

    @Test
    public void noTaxPercentMeansNoTaxLine() {
        OrderTotals t = totals("10.00", "0.00", "0.00", NO_TAX);
        assertEquals("0.00", t.tax().toPlainString());
        assertEquals("10.00", t.total().toPlainString());
        assertFalse(t.hasTax());
    }

    @Test
    public void taxElevenPercentOfTen() {
        OrderTotals t = totals("10.00", "0.00", "0.00", TAX_11);
        assertEquals("1.10", t.tax().toPlainString());
        assertEquals("11.10", t.total().toPlainString());
    }

    @Test
    public void taxMatchesServerExampleOfThirtyCents() {
        // Kasus yang diamati di log backend: base 0.30, 11% -> expected_tax 0.03
        OrderTotals t = totals("0.30", "0.00", "0.00", TAX_11);
        assertEquals("0.03", t.tax().toPlainString());
        assertEquals("0.33", t.total().toPlainString());
    }

    @Test
    public void taxIsComputedAfterDiscountNotBeforeIt() {
        // Server memakai (subtotal - discount) sebagai basis.
        // 100.00 - 10.00 = 90.00 -> 11% -> 9.90
        OrderTotals t = totals("100.00", "10.00", "0.00", TAX_11);
        assertEquals("9.90", t.tax().toPlainString());
        // total = 100.00 + 0 - 10.00 + 9.90
        assertEquals("99.90", t.total().toPlainString());
    }

    @Test
    public void deliveryFeeIsNotTaxed() {
        // Server menambahkan delivery_fee setelah pajak dihitung.
        OrderTotals t = totals("10.00", "0.00", "5.00", TAX_11);
        assertEquals("1.10", t.tax().toPlainString());
        assertEquals("16.10", t.total().toPlainString());
    }

    // ----------------------------------------------------------------- diskon

    @Test
    public void discountIsSubtractedFromTotal() {
        OrderTotals t = totals("50.00", "5.00", "0.00", NO_TAX);
        assertEquals("45.00", t.total().toPlainString());
        assertTrue(t.hasDiscount());
    }

    @Test
    public void discountLargerThanSubtotalIsClampedNotNegative() {
        OrderTotals t = totals("10.00", "25.00", "0.00", NO_TAX);
        assertEquals("10.00", t.discount().toPlainString());
        assertEquals("0.00", t.total().toPlainString());
    }

    @Test
    public void negativeDiscountIsTreatedAsZero() {
        OrderTotals t = totals("10.00", "-5.00", "0.00", NO_TAX);
        assertEquals("0.00", t.discount().toPlainString());
        assertEquals("10.00", t.total().toPlainString());
    }

    @Test
    public void tenPercentDiscountOfNinetyNineNinetyNine() {
        // Kasus yang putaran lalu hanya bisa diuji lewat unit test.
        Money d = OrderTotals.discountFromPercent(Money.of("99.99"), new BigDecimal("10"));
        assertEquals("10.00", d.toPlainString());
        OrderTotals t = OrderTotals.of(Money.of("99.99"), d, Money.zero(), NO_TAX);
        assertEquals("89.99", t.total().toPlainString());
    }

    @Test
    public void discountPercentIsCappedAtOneHundred() {
        Money d = OrderTotals.discountFromPercent(Money.of("50.00"), new BigDecimal("250"));
        assertEquals("50.00", d.toPlainString());
    }

    @Test
    public void zeroOrNegativePercentGivesNoDiscount() {
        assertEquals("0.00", OrderTotals.discountFromPercent(Money.of("50.00"), BigDecimal.ZERO).toPlainString());
        assertEquals("0.00", OrderTotals.discountFromPercent(Money.of("50.00"), new BigDecimal("-5")).toPlainString());
    }

    // ------------------------------------------------------- gabungan & tepi

    @Test
    public void discountAndTaxAndDeliveryTogether() {
        // subtotal 99.99, diskon 10% = 10.00, basis pajak 89.99, 11% = 9.90
        // total = 99.99 + 2.50 - 10.00 + 9.90 = 102.39
        Money d = OrderTotals.discountFromPercent(Money.of("99.99"), new BigDecimal("10"));
        OrderTotals t = OrderTotals.of(Money.of("99.99"), d, Money.of("2.50"), TAX_11);
        assertEquals("10.00", t.discount().toPlainString());
        assertEquals("9.90", t.tax().toPlainString());
        assertEquals("102.39", t.total().toPlainString());
    }

    @Test
    public void threeDimesWithTaxStayExact() {
        Money subtotal = Money.of("0.10").times(3);
        OrderTotals t = OrderTotals.of(subtotal, Money.zero(), Money.zero(), TAX_11);
        assertEquals("0.30", t.subtotal().toPlainString());
        assertEquals("0.03", t.tax().toPlainString());
        assertEquals("0.33", t.total().toPlainString());
    }

    @Test
    public void negativeSubtotalIsTreatedAsZero() {
        OrderTotals t = totals("-10.00", "0.00", "0.00", TAX_11);
        assertEquals("0.00", t.subtotal().toPlainString());
        assertEquals("0.00", t.total().toPlainString());
    }

    @Test
    public void fullDiscountLeavesNoTax() {
        OrderTotals t = totals("40.00", "40.00", "0.00", TAX_11);
        assertEquals("0.00", t.tax().toPlainString());
        assertEquals("0.00", t.total().toPlainString());
    }
}
