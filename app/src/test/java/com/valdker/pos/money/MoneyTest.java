package com.valdker.pos.money;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.math.BigDecimal;

/**
 * Kasus uji di sini adalah angka yang menjebak aritmetika double, plus bentuk
 * teks yang benar-benar datang dari API dan dari input kasir.
 */
public class MoneyTest {

    // ------------------------------------------------------------ parsing

    @Test
    public void parsesPlainDecimalString() {
        assertEquals("12.35", Money.of("12.35").toPlainString());
    }

    @Test
    public void parsesCurrencyAndThousandSeparators() {
        assertEquals("1234.50", Money.of("$1,234.50").toPlainString());
        assertEquals("12.35", Money.of("12.35 USD").toPlainString());
    }

    @Test
    public void treatsNullEmptyAndGarbageAsZero() {
        assertEquals("0.00", Money.of((String) null).toPlainString());
        assertEquals("0.00", Money.of("").toPlainString());
        assertEquals("0.00", Money.of("   ").toPlainString());
        assertEquals("0.00", Money.of("abc").toPlainString());
    }

    @Test
    public void alwaysCarriesTwoDecimals() {
        assertEquals("5.00", Money.of("5").toPlainString());
        assertEquals("5.50", Money.of("5.5").toPlainString());
    }

    @Test
    public void ofDoubleDoesNotLeakBinaryExpansion() {
        // BigDecimal.valueOf(0.1) -> "0.1", bukan 0.1000000000000000055511151231257827
        assertEquals("0.10", Money.ofDouble(0.1).toPlainString());
    }

    // --------------------------------------------------- kasus jebakan double

    @Test
    public void threeDimesSumToExactlyThirtyCents() {
        // 0.1 + 0.1 + 0.1 dengan double menghasilkan 0.30000000000000004
        Money dime = Money.of("0.10");
        Money total = dime.plus(dime).plus(dime);
        assertEquals("0.30", total.toPlainString());
    }

    @Test
    public void threeDimesByMultiplicationAlsoExact() {
        assertEquals("0.30", Money.of("0.10").times(3).toPlainString());
    }

    @Test
    public void classicPointOnePlusPointTwo() {
        assertEquals("0.30", Money.of("0.10").plus(Money.of("0.20")).toPlainString());
    }

    @Test
    public void changeFromHundredIsExact() {
        // 100.00 - 87.65 dengan double menghasilkan 12.350000000000009
        Money change = Money.of("100.00").minus(Money.of("87.65"));
        assertEquals("12.35", change.toPlainString());
    }

    // ------------------------------------------------------------- persentase

    @Test
    public void tenPercentDiscountOfNinetyNineNinetyNine() {
        // 9.999 -> HALF_EVEN pada 2 desimal -> 10.00
        assertEquals("10.00", Money.of("99.99").percent(new BigDecimal("10")).toPlainString());
    }

    @Test
    public void elevenPercentTaxOfFifteenCents() {
        // 0.15 * 11 / 100 = 0.0165 -> 0.02
        assertEquals("0.02", Money.of("0.15").percent(new BigDecimal("11")).toPlainString());
    }

    @Test
    public void roundingIsHalfEvenToMatchBackend() {
        // Titik .005 persis: HALF_EVEN membulatkan ke digit genap.
        // 0.125 -> 0.12 (2 genap), 0.135 -> 0.14 (4 genap).
        // HALF_UP akan memberi 0.13 dan 0.14, sehingga berbeda dari server.
        assertEquals("0.12", Money.of("2.50").percent(new BigDecimal("5")).toPlainString());
        assertEquals("0.14", Money.of("2.70").percent(new BigDecimal("5")).toPlainString());
    }

    @Test
    public void percentOfZeroIsZero() {
        assertEquals("0.00", Money.zero().percent(new BigDecimal("11")).toPlainString());
    }

    // ---------------------------------------------------------- split payment

    @Test
    public void threeWaySplitOfOneHundredLeavesNoRemainder() {
        Money paid = Money.of("33.33").plus(Money.of("33.33")).plus(Money.of("33.34"));
        assertEquals("100.00", paid.toPlainString());
        assertTrue(paid.isGreaterThanOrEqual(Money.of("100.00")));
        assertEquals("0.00", paid.minus(Money.of("100.00")).toPlainString());
    }

    // ------------------------------------------------------------- baris item

    @Test
    public void lineTotalIsExactForIntegerQuantities() {
        assertEquals("5.25", Money.of("1.75").times(3).toPlainString());
        assertEquals("0.00", Money.of("1.75").times(0).toPlainString());
    }

    @Test
    public void lineTotalSupportsFractionalQuantity() {
        // 2.5 kg @ 3.30
        assertEquals("8.25", Money.of("3.30").times(new BigDecimal("2.5")).toPlainString());
    }

    @Test
    public void cartOfManySmallItemsStaysExact() {
        Money total = Money.zero();
        for (int i = 0; i < 100; i++) {
            total = total.plus(Money.of("0.07"));
        }
        assertEquals("7.00", total.toPlainString());
    }

    // ------------------------------------------------------------ perbandingan

    @Test
    public void comparisonsBehave() {
        assertTrue(Money.of("87.65").isLessThan(Money.of("100.00")));
        assertTrue(Money.of("100.00").isGreaterThanOrEqual(Money.of("100.00")));
        assertTrue(Money.of("-1.00").isNegative());
        assertTrue(Money.zero().isZero());
        assertFalse(Money.of("0.01").isZero());
    }

    @Test
    public void cashExactlyEqualToTotalIsAccepted() {
        // Regresi: dengan double, 0.1+0.2 = 0.30000000000000004 membuat uang pas
        // 0.30 tampak kurang dan pembayaran ditolak.
        Money total = Money.of("0.10").plus(Money.of("0.20"));
        Money cash = Money.of("0.30");
        assertTrue(cash.isGreaterThanOrEqual(total));
        assertEquals("0.00", cash.minus(total).toPlainString());
    }

    @Test
    public void negativeChangeCanBeClampedToZero() {
        assertEquals("0.00", Money.of("-5.00").orZeroIfNegative().toPlainString());
        assertEquals("5.00", Money.of("5.00").orZeroIfNegative().toPlainString());
    }

    // ------------------------------------------------------------- pemformatan

    @Test
    public void formatsAsUsdForDisplay() {
        assertEquals("$12.35", Money.of("12.35").format());
        assertEquals("$0.00", Money.zero().format());
    }

    @Test
    public void plainStringNeverUsesScientificNotation() {
        assertEquals("0.01", Money.of("1E-2").toPlainString());
    }

    @Test
    public void equalityIgnoresTrailingZeroDifferences() {
        assertEquals(Money.of("5"), Money.of("5.00"));
    }
}
