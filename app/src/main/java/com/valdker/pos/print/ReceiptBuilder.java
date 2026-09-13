package com.valdker.pos.print;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.valdker.pos.money.Money;

/**
 * Menyusun teks struk dari {@link ReceiptContent}.
 *
 * <p>Satu-satunya tempat urutan baris struk ditentukan. Dengan begitu struk
 * yang dicetak saat checkout dan struk yang dicetak ulang dari daftar pesanan
 * offline tidak bisa lagi berbeda: keduanya melewati fungsi yang sama, dan
 * satu-satunya yang membedakan adalah spanduk status di kepala.
 *
 * <p>Garis pemisah ditulis sebagai {@link ReceiptLayout#SEPARATOR}; lebar
 * sesungguhnya diisi saat teks diubah jadi byte, mengikuti lebar kertas yang
 * dipilih di Pengaturan. Sebelumnya tiap pembuat struk menuliskan 32 setrip
 * mati, yang benar hanya untuk kertas 58mm.
 *
 * <p>Baris nominal nol tidak dicetak sama sekali. Yang dilarang adalah
 * mencetak nol untuk nilai yang bukan nol - itu yang dilakukan versi lama.
 */
public final class ReceiptBuilder {

    private ReceiptBuilder() {
    }

    @NonNull
    public static String build(@NonNull ReceiptContent c) {
        StringBuilder sb = new StringBuilder();

        centerBold(sb, c.shopName);
        center(sb, c.shopAddress);
        center(sb, c.shopPhone);
        separator(sb);

        if (!c.statusBanner.trim().isEmpty()) {
            centerBold(sb, c.statusBanner);
            separator(sb);
        }

        row(sb, "Order:", c.orderNumber);
        row(sb, "Cashier:", c.cashier);
        row(sb, "Customer:", c.customer);
        row(sb, "Vehicle:", c.vehicleName);
        row(sb, "Plate:", c.plateNumber);
        row(sb, "Date:", c.date);
        row(sb, "Time:", c.time);

        // Waktu perangkat dalam bentuk ISO hanya dicetak kalau tanggal dan jam
        // gagal diturunkan darinya - keduanya memang berasal dari stempel yang
        // sama, jadi mencetak ketiganya hanya mengulang hal yang sama. Pada
        // kertas 58mm stempel ISO juga tidak muat: 25 karakter angka memakan
        // seluruh baris dan labelnya sendiri yang terpotong jadi "Device ".
        if (c.date.isEmpty() && c.time.isEmpty()) {
            row(sb, "Device Time:", c.deviceTime);
        }

        // Meja dan pelayan hanya untuk order dine-in. Pelayan sebelumnya
        // tidak pernah tercetak sama sekali: pembuat struk tidak punya
        // parameternya, padahal kasir sudah memilihnya di keranjang.
        if (c.dineIn) {
            row(sb, "Table:", c.tableNumber);
            row(sb, "Waiter:", c.waiterName);
        }
        row(sb, "Delivery:", c.deliveryAddress);

        separator(sb);

        for (ReceiptContent.Item item : c.items) {
            if (item == null) continue;

            sb.append("[L]<b>").append(safe(item.name, "Item")).append("</b>")
                    .append("[R]<b>").append(money(item.lineTotal)).append("</b>\n");

            if (!item.typeLabel.trim().isEmpty()) {
                sb.append("[L]").append(item.typeLabel.trim()).append("\n");
            }

            sb.append("[L]").append(Math.max(0, item.qty))
                    .append(" x ").append(money(item.unitPrice)).append("\n\n");
        }

        separator(sb);

        amountRow(sb, "Subtotal", c.subtotal, true);
        amountRow(sb, "Discount", c.discount, false);
        amountRow(sb, "VAT / Tax", c.tax, false);
        amountRow(sb, "Delivery Fee", c.deliveryFee, false);

        separator(sb);

        sb.append("[L]<b>Total</b>[R]<b>").append(money(c.total)).append("</b>\n");
        appendPayments(sb, c);

        if (c.paid.isPositive()) {
            amountRow(sb, "Paid", c.paid, true);
            amountRow(sb, "Change", c.change, true);
        }

        separator(sb);
        center(sb, c.footerNote);
        center(sb, c.shopName);
        sb.append("\n\n");

        return sb.toString();
    }

    /**
     * Satu baris per metode pembayaran.
     *
     * <p>Order dengan satu metode tetap tampil seperti dulu. Order yang
     * dibayar terbagi kini mencetak seluruh metodenya beserta nominal
     * masing-masing; versi lama hanya menampilkan metode utama, sehingga
     * kertas tidak pernah menjelaskan sisanya dibayar dengan apa.
     */
    private static void appendPayments(@NonNull StringBuilder sb, @NonNull ReceiptContent c) {
        if (c.payments.isEmpty()) return;

        if (c.payments.size() == 1) {
            sb.append("[L]Payment[R]").append(c.payments.get(0).label).append("\n");
            return;
        }

        sb.append("[L]Payment[R]Split\n");
        for (ReceiptContent.Payment payment : c.payments) {
            if (payment == null) continue;
            // Penanda "- ", bukan spasi: teks baris dirapikan dengan trim()
            // sebelum dicetak, jadi indentasi spasi tidak sampai ke kertas.
            sb.append("[L]- ").append(payment.label)
                    .append("[R]").append(money(payment.amount)).append("\n");
        }
    }

    /** Baris label-nilai; dilewati kalau nilainya kosong. */
    private static void row(@NonNull StringBuilder sb, @NonNull String label, @Nullable String value) {
        if (value == null || value.trim().isEmpty()) return;
        sb.append("[L]").append(label).append("[R]").append(value.trim()).append("\n");
    }

    /** Baris nominal; nol hanya dicetak kalau memang wajib tampil. */
    private static void amountRow(@NonNull StringBuilder sb,
                                  @NonNull String label,
                                  @NonNull Money amount,
                                  boolean printWhenZero) {
        if (!printWhenZero && amount.isZero()) return;
        sb.append("[L]").append(label).append("[R]").append(money(amount)).append("\n");
    }

    private static void separator(@NonNull StringBuilder sb) {
        sb.append("[C]").append(ReceiptLayout.SEPARATOR).append("\n");
    }

    private static void center(@NonNull StringBuilder sb, @Nullable String text) {
        if (text == null || text.trim().isEmpty()) return;
        sb.append("[C]").append(text.trim()).append("\n");
    }

    private static void centerBold(@NonNull StringBuilder sb, @Nullable String text) {
        if (text == null || text.trim().isEmpty()) return;
        sb.append("[C]<b>").append(text.trim()).append("</b>\n");
    }

    /** Satu-satunya tempat nominal jadi teks: lewat Money, tanpa %.2f. */
    @NonNull
    private static String money(@NonNull Money amount) {
        return "$" + amount.orZeroIfNegative().toPlainString();
    }

    @NonNull
    private static String safe(@Nullable String value, @NonNull String fallback) {
        if (value == null) return fallback;
        String clean = value.trim();
        return clean.isEmpty() ? fallback : clean;
    }
}
