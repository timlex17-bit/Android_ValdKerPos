package com.valdker.pos.print;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.valdker.pos.money.Money;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

/**
 * Membaca payload order menjadi {@link ReceiptContent}.
 *
 * <p>Ini yang membuat struk cetak-ulang sama dengan struk aslinya: kedua
 * jalur membaca payload yang sama persis lewat fungsi yang sama, bukan
 * menyusun barisnya sendiri-sendiri dari sumber yang berbeda. Sebelumnya
 * struk asli dibuat dari objek hasil checkout sementara cetak ulang dibuat
 * dari payload tersimpan, dan keduanya sudah menyimpang: yang satu mencetak
 * Discount/Tax nol mati, yang lain angka sebenarnya.
 *
 * <p>Nominal dibaca sebagai teks lalu diurai {@link Money} (BigDecimal).
 * Tidak ada {@code optDouble} di jalur ini - desimal yang lewat double
 * berbeda dari server di batas .005.
 *
 * <h3>Kunci lokal</h3>
 * Beberapa hal yang dibutuhkan struk tidak ada di kontrak API: nama pelayan
 * (server hanya menerima {@code waiter_id}), nama metode bayar tiap baris
 * pembayaran (server hanya menerima {@code payment_method_id}), dan nama
 * pelanggan. Ketiganya disimpan di payload dengan awalan {@code _receipt_}
 * dan DIBUANG lagi sebelum payload dikirim ke server - lihat
 * {@link #stripLocalKeys(JSONObject)}. Dengan begitu kontrak API tidak
 * berubah sama sekali, tapi struk offline tetap bisa menyebut nama.
 */
public final class ReceiptPayloadReader {

    public static final String KEY_WAITER_NAME = "_receipt_waiter_name";
    public static final String KEY_CUSTOMER_NAME = "_receipt_customer_name";
    public static final String KEY_PAYMENT_LABELS = "_receipt_payment_labels";
    public static final String KEY_CASH_RECEIVED = "_receipt_cash_received";
    public static final String KEY_CHANGE = "_receipt_change";

    private static final String[] LOCAL_KEYS = {
            KEY_WAITER_NAME, KEY_CUSTOMER_NAME, KEY_PAYMENT_LABELS,
            KEY_CASH_RECEIVED, KEY_CHANGE
    };

    private ReceiptPayloadReader() {
    }

    /**
     * Menaruh keterangan yang hanya dipakai struk ke dalam salinan payload.
     *
     * @param serverPayload payload untuk server; TIDAK diubah
     * @return salinan berisi tambahan lokal, untuk dicetak dan disimpan offline
     */
    @NonNull
    public static JSONObject withLocalExtras(@NonNull JSONObject serverPayload,
                                             @Nullable String waiterName,
                                             @Nullable String customerName,
                                             @Nullable JSONArray paymentLabels,
                                             @Nullable Money cashReceived,
                                             @Nullable Money change) {
        JSONObject copy;
        try {
            copy = new JSONObject(serverPayload.toString());
        } catch (Exception e) {
            copy = serverPayload;
        }

        try {
            if (waiterName != null && !waiterName.trim().isEmpty()) {
                copy.put(KEY_WAITER_NAME, waiterName.trim());
            }
            if (customerName != null && !customerName.trim().isEmpty()) {
                copy.put(KEY_CUSTOMER_NAME, customerName.trim());
            }
            if (paymentLabels != null && paymentLabels.length() > 0) {
                copy.put(KEY_PAYMENT_LABELS, paymentLabels);
            }
            // Uang yang diserahkan dan kembaliannya tidak ada di kontrak API -
            // yang dikirim ke server hanyalah nominal yang diterapkan ke order.
            if (cashReceived != null && cashReceived.isPositive()) {
                copy.put(KEY_CASH_RECEIVED, cashReceived.toPlainString());
            }
            if (change != null && change.isPositive()) {
                copy.put(KEY_CHANGE, change.toPlainString());
            }
        } catch (Exception ignored) {
        }

        return copy;
    }

    /** Membuang kunci lokal sebelum payload dikirim ke server. */
    @NonNull
    public static JSONObject stripLocalKeys(@NonNull JSONObject payload) {
        for (String key : LOCAL_KEYS) {
            payload.remove(key);
        }
        return payload;
    }

    /** Membaca seluruh bagian struk yang memang ada di payload. */
    @NonNull
    public static ReceiptContent read(@Nullable JSONObject payload) {
        ReceiptContent content = new ReceiptContent();
        if (payload == null) return content;

        content.orderNumber = text(payload, "client_order_id");
        content.customer = firstNonEmpty(
                text(payload, KEY_CUSTOMER_NAME),
                text(payload, "customer_name"));
        content.deviceTime = firstNonEmpty(
                text(payload, "offline_created_at"),
                text(payload, "device_time"));

        // Tanggal dan jam diambil dari waktu transaksi di payload, bukan dari
        // jam saat mencetak. Kalau dari jam cetak, struk cetak ulang tidak
        // akan pernah bisa sama dengan struk aslinya - dan yang benar memang
        // waktu penjualannya, bukan waktu kertasnya keluar.
        content.date = isoDate(content.deviceTime);
        content.time = isoTime(content.deviceTime);

        String orderType = text(payload, "default_order_type").toUpperCase(Locale.US);
        content.dineIn = "DINE_IN".equals(orderType);
        content.tableNumber = text(payload, "table_number");
        content.waiterName = text(payload, KEY_WAITER_NAME);
        content.deliveryAddress = text(payload, "delivery_address");

        content.subtotal = money(payload, "subtotal");
        content.discount = money(payload, "discount");
        content.tax = money(payload, "tax");
        content.deliveryFee = money(payload, "delivery_fee");
        content.total = money(payload, "total");
        content.change = money(payload, "change_amount");

        readItems(payload, content);
        readPayments(payload, content);

        // Uang yang diserahkan kalau dicatat; kalau tidak, jumlah seluruh
        // pembayaran. Versi lama hanya membaca nominal pembayaran pertama,
        // jadi order terbagi tampak dibayar kurang dari totalnya.
        Money cashReceived = money(payload, KEY_CASH_RECEIVED);
        if (cashReceived.isPositive()) {
            content.paid = cashReceived;
            content.change = money(payload, KEY_CHANGE);
        } else {
            Money paid = Money.zero();
            for (ReceiptContent.Payment payment : content.payments) {
                paid = paid.plus(payment.amount);
            }
            content.paid = paid;
        }

        return content;
    }

    private static void readItems(@NonNull JSONObject payload, @NonNull ReceiptContent content) {
        JSONArray items = payload.optJSONArray("items");
        if (items == null) return;

        for (int i = 0; i < items.length(); i++) {
            JSONObject raw = items.optJSONObject(i);
            if (raw == null) continue;

            ReceiptContent.Item item = new ReceiptContent.Item();
            item.name = firstNonEmpty(
                    text(raw, "name"),
                    text(raw, "product_name"),
                    "Item");
            item.qty = Math.max(0, raw.optInt("quantity", raw.optInt("qty", 0)));
            item.unitPrice = money(raw, "price");

            Money lineTotal = money(raw, "total");
            if (lineTotal.isZero()) lineTotal = money(raw, "subtotal");
            if (lineTotal.isZero()) lineTotal = item.unitPrice.times(item.qty);
            item.lineTotal = lineTotal;

            item.typeLabel = orderTypeLabel(text(raw, "order_type"));
            content.items.add(item);
        }
    }

    private static void readPayments(@NonNull JSONObject payload, @NonNull ReceiptContent content) {
        JSONArray payments = payload.optJSONArray("payments");
        String primaryLabel = firstNonEmpty(text(payload, "payment_method"), "-");
        JSONArray labels = payload.optJSONArray(KEY_PAYMENT_LABELS);

        if (payments == null || payments.length() == 0) {
            content.addPayment(primaryLabel, money(payload, "total"));
            return;
        }

        for (int i = 0; i < payments.length(); i++) {
            JSONObject raw = payments.optJSONObject(i);
            if (raw == null) continue;

            String label = labels != null ? text(labels.optString(i, "")) : "";
            if (label.isEmpty()) {
                label = firstNonEmpty(
                        text(raw, "method_code"),
                        text(raw, "payment_method"),
                        i == 0 ? primaryLabel : "",
                        methodIdLabel(raw));
            }
            content.addPayment(label, money(raw, "amount"));
        }
    }

    @NonNull
    private static String methodIdLabel(@NonNull JSONObject payment) {
        int id = payment.optInt("payment_method_id", 0);
        return id > 0 ? "Method #" + id : "-";
    }

    @NonNull
    public static String orderTypeLabel(@Nullable String orderType) {
        String value = orderType == null ? "" : orderType.trim().toUpperCase(Locale.US);
        if ("DINE_IN".equals(value)) return "(* DINE IN)";
        if ("TAKE_OUT".equals(value)) return "(* TAKE OUT)";
        if ("DELIVERY".equals(value)) return "(^ DELIVERY)";
        return "";
    }

    /**
     * Membaca nominal sebagai teks lalu menguraikannya dengan Money.
     * Sengaja bukan optDouble: payload menyimpan desimal eksak, dan
     * melewatkannya lewat double adalah cara kehilangan sen.
     */
    @NonNull
    private static Money money(@Nullable JSONObject source, @NonNull String key) {
        if (source == null || source.isNull(key)) return Money.zero();
        return Money.of(source.optString(key, "")).orZeroIfNegative();
    }

    @NonNull
    private static String text(@Nullable JSONObject source, @NonNull String key) {
        if (source == null || source.isNull(key)) return "";
        return source.optString(key, "").trim();
    }

    @NonNull
    private static String text(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    /** "2026-09-13T16:09:33+09:00" menjadi "13/09/26". */
    @NonNull
    public static String isoDate(@NonNull String iso) {
        if (iso.length() < 10 || iso.charAt(4) != '-' || iso.charAt(7) != '-') return "";
        String year = iso.substring(2, 4);
        String month = iso.substring(5, 7);
        String day = iso.substring(8, 10);
        return day + "/" + month + "/" + year;
    }

    /** "2026-09-13T16:09:33+09:00" menjadi "16:09". */
    @NonNull
    public static String isoTime(@NonNull String iso) {
        if (iso.length() < 16 || iso.charAt(13) != ':') return "";
        return iso.substring(11, 16);
    }

    @NonNull
    private static String firstNonEmpty(@NonNull String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }
}
