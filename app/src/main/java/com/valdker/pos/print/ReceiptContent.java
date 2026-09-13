package com.valdker.pos.print;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.valdker.pos.money.Money;

import java.util.ArrayList;
import java.util.List;

/**
 * Isi sebuah struk, terlepas dari dari mana datanya.
 *
 * <p>Sebelumnya ada empat pembuat struk yang menyusun teksnya masing-masing:
 * keranjang restoran, kasir retail, cetak ulang pesanan offline, dan bengkel.
 * Keempatnya menulis urutan baris yang mirip tapi tidak sama, dan
 * perbedaannya bukan kosmetik - dua di antaranya mencetak Discount dan
 * VAT/Tax sebagai "$0.00" mati sementara Total-nya sudah dipotong diskon,
 * jadi kertasnya tidak menjumlahkan dirinya sendiri; yang ketiga mencetak
 * angka yang sebenarnya. Satu order yang dicetak lalu dicetak ulang
 * menghasilkan dua kertas berbeda.
 *
 * <p>Sekarang semuanya mengisi objek ini dan menyerahkannya ke
 * {@link ReceiptBuilder}. Nominal disimpan sebagai {@link Money} (BigDecimal),
 * bukan double, sehingga tidak ada lagi jalan untuk memformatnya dengan
 * {@code %.2f}.
 */
public final class ReceiptContent {

    /** Satu baris barang pada struk. */
    public static final class Item {
        @NonNull public String name = "Item";
        public int qty;
        @NonNull public Money unitPrice = Money.zero();
        @NonNull public Money lineTotal = Money.zero();
        /** Label tipe, mis. "(* DINE IN)" atau "Service". Kosong = tidak dicetak. */
        @NonNull public String typeLabel = "";
    }

    /**
     * Satu pembayaran. Order biasa punya satu; order yang dibayar terbagi
     * punya beberapa, dan semuanya dicetak - versi lama hanya mencetak yang
     * pertama, jadi kertas tidak pernah menjelaskan sisa uangnya dibayar
     * dengan apa.
     */
    public static final class Payment {
        @NonNull public String label = "-";
        @NonNull public Money amount = Money.zero();

        public Payment() {
        }

        public Payment(@NonNull String label, @NonNull Money amount) {
            this.label = label;
            this.amount = amount;
        }
    }

    @NonNull public String shopName = "";
    @NonNull public String shopAddress = "";
    @NonNull public String shopPhone = "";

    /** Spanduk status, mis. "OFFLINE / PENDING SYNC". Kosong = tidak dicetak. */
    @NonNull public String statusBanner = "";

    @NonNull public String orderNumber = "";
    @NonNull public String cashier = "";
    @NonNull public String customer = "";
    @NonNull public String date = "";
    @NonNull public String time = "";
    @NonNull public String deviceTime = "";

    /** Kendaraan dan plat; hanya struk bengkel yang mengisinya. */
    @NonNull public String vehicleName = "";
    @NonNull public String plateNumber = "";

    @NonNull public String tableNumber = "";
    @NonNull public String waiterName = "";
    @NonNull public String deliveryAddress = "";

    /** Hanya order dine-in yang mencetak meja dan pelayan. */
    public boolean dineIn = false;

    @NonNull public final List<Item> items = new ArrayList<>();

    @NonNull public Money subtotal = Money.zero();
    @NonNull public Money discount = Money.zero();
    @NonNull public Money tax = Money.zero();
    @NonNull public Money deliveryFee = Money.zero();
    @NonNull public Money total = Money.zero();
    @NonNull public Money paid = Money.zero();
    @NonNull public Money change = Money.zero();

    @NonNull public final List<Payment> payments = new ArrayList<>();

    @NonNull public String footerNote = "";

    @NonNull
    public ReceiptContent addItem(@NonNull Item item) {
        items.add(item);
        return this;
    }

    @NonNull
    public ReceiptContent addPayment(@Nullable String label, @Nullable Money amount) {
        Payment payment = new Payment();
        payment.label = label == null || label.trim().isEmpty() ? "-" : label.trim();
        payment.amount = amount == null ? Money.zero() : amount;
        payments.add(payment);
        return this;
    }
}
