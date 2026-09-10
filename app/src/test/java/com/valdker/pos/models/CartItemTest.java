package com.valdker.pos.models;

import static org.junit.Assert.assertEquals;

import com.valdker.pos.money.Money;

import org.junit.Test;

/**
 * Aritmetika baris keranjang. CartManager sendiri butuh Context untuk
 * SharedPreferences sehingga tidak bisa diuji di sini; yang diuji adalah
 * perhitungan yang dipakainya.
 */
public class CartItemTest {

    private static CartItem item(String priceDecimal, int qty) {
        CartItem it = new CartItem(1, 1, "Item", 0.0, "", qty);
        it.setPrice(Money.of(priceDecimal));
        return it;
    }

    @Test
    public void lineTotalIsExactForRepeatedDimes() {
        assertEquals("0.30", item("0.10", 3).lineTotal().toPlainString());
    }

    @Test
    public void lineTotalHandlesTypicalPrice() {
        assertEquals("5.25", item("1.75", 3).lineTotal().toPlainString());
    }

    @Test
    public void zeroQuantityIsZero() {
        assertEquals("0.00", item("1.75", 0).lineTotal().toPlainString());
    }

    @Test
    public void constructorFromDoubleStillProducesExactDecimal() {
        CartItem it = new CartItem(1, 1, "Item", 0.1, "", 3);
        assertEquals("0.10", it.price().toPlainString());
        assertEquals("0.30", it.lineTotal().toPlainString());
    }

    @Test
    public void setPriceKeepsBothRepresentationsInSync() {
        CartItem it = item("1.75", 1);
        assertEquals("1.75", it.priceDecimal);
        assertEquals(1.75d, it.price, 0.0000001d);
    }

    @Test
    public void itemWithoutDecimalTextFallsBackToDoubleField() {
        // Bentuk item yang dibaca dari keranjang tersimpan versi lama.
        CartItem it = new CartItem(1, 1, "Item", 2.50, "", 2);
        it.priceDecimal = "";
        assertEquals("5.00", it.lineTotal().toPlainString());
    }

    @Test
    public void summingManyLinesStaysExact() {
        Money total = Money.zero();
        for (int i = 0; i < 3; i++) {
            total = total.plus(item("0.10", 1).lineTotal());
        }
        assertEquals("0.30", total.toPlainString());
    }
}
