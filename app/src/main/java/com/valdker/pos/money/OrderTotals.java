package com.valdker.pos.money;

import androidx.annotation.NonNull;

import java.math.BigDecimal;

/**
 * Perhitungan total satu order, meniru rumus server persis.
 *
 * <p>Backend (`pos/serializers.py`) menghitung:
 * <pre>
 *   subtotal = sum(quantity x price)
 *   tax_expected = ((subtotal - discount) * tax_percent / 100).quantize(0.01)
 *   total = subtotal + delivery_fee - discount + tax
 * </pre>
 *
 * <p>Urutannya penting: pajak dihitung dari <b>subtotal dikurangi diskon</b>,
 * bukan dari subtotal kotor. Kalau klien memakai basis yang berbeda, angkanya
 * akan menyimpang dari server begitu diskon dipakai.
 *
 * <p>Pembulatan mengikuti {@link Money}: HALF_EVEN pada dua desimal, sama
 * dengan {@code Decimal.quantize()} bawaan Python yang dipakai backend.
 */
public final class OrderTotals {

    private final Money subtotal;
    private final Money discount;
    private final Money deliveryFee;
    private final Money tax;
    private final Money total;

    private OrderTotals(@NonNull Money subtotal,
                        @NonNull Money discount,
                        @NonNull Money deliveryFee,
                        @NonNull Money tax,
                        @NonNull Money total) {
        this.subtotal = subtotal;
        this.discount = discount;
        this.deliveryFee = deliveryFee;
        this.tax = tax;
        this.total = total;
    }

    /**
     * @param subtotal    jumlah seluruh baris item
     * @param discount    potongan pada tingkat order; dijepit ke rentang
     *                    0..subtotal supaya tidak pernah menghasilkan total
     *                    negatif yang akan ditolak server
     * @param deliveryFee ongkos kirim
     * @param taxPercent  POSSettings.tax_percent milik toko; nol berarti tidak
     *                    ada baris pajak
     */
    @NonNull
    public static OrderTotals of(@NonNull Money subtotal,
                                 @NonNull Money discount,
                                 @NonNull Money deliveryFee,
                                 @NonNull BigDecimal taxPercent) {
        Money safeSubtotal = subtotal.orZeroIfNegative();
        Money safeDelivery = deliveryFee.orZeroIfNegative();
        Money safeDiscount = clampDiscount(discount, safeSubtotal);

        Money taxableBase = safeSubtotal.minus(safeDiscount);
        Money tax = taxPercent.signum() > 0
                ? taxableBase.percent(taxPercent)
                : Money.zero();

        Money total = safeSubtotal
                .plus(safeDelivery)
                .minus(safeDiscount)
                .plus(tax);

        return new OrderTotals(safeSubtotal, safeDiscount, safeDelivery, tax, total);
    }

    /** Diskon tidak boleh negatif maupun melebihi subtotal. */
    @NonNull
    private static Money clampDiscount(@NonNull Money discount, @NonNull Money subtotal) {
        if (discount.isNegative()) return Money.zero();
        return discount.isGreaterThanOrEqual(subtotal) ? subtotal : discount;
    }

    /**
     * Diskon dari persentase subtotal, mis. "10%" dari 99.99.
     * Dibulatkan sekali dengan aturan yang sama seperti pajak.
     */
    @NonNull
    public static Money discountFromPercent(@NonNull Money subtotal, @NonNull BigDecimal percent) {
        if (percent.signum() <= 0) return Money.zero();
        BigDecimal capped = percent.compareTo(new BigDecimal("100")) > 0
                ? new BigDecimal("100")
                : percent;
        return subtotal.orZeroIfNegative().percent(capped);
    }

    @NonNull public Money subtotal() { return subtotal; }
    @NonNull public Money discount() { return discount; }
    @NonNull public Money deliveryFee() { return deliveryFee; }
    @NonNull public Money tax() { return tax; }
    @NonNull public Money total() { return total; }

    public boolean hasDiscount() { return discount.isPositive(); }
    public boolean hasTax() { return tax.isPositive(); }

    @NonNull
    @Override
    public String toString() {
        return "subtotal=" + subtotal
                + " discount=" + discount
                + " delivery=" + deliveryFee
                + " tax=" + tax
                + " total=" + total;
    }
}
