package com.valdker.pos.ui.offlineorders;

import com.valdker.pos.money.Money;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.valdker.pos.SessionManager;
import com.valdker.pos.local.PendingOrderEntity;
import com.valdker.pos.local.PendingOrderItemEntity;
import com.valdker.pos.models.Shop;
import com.valdker.pos.R;
import com.valdker.pos.print.BluetoothPrinterManager;
import com.valdker.pos.print.ReceiptBuilder;
import com.valdker.pos.print.ReceiptContent;
import com.valdker.pos.print.ReceiptPayloadReader;
import com.valdker.pos.print.PrinterPrefs;
import com.valdker.pos.print.PrinterService;
import com.valdker.pos.repositories.OfflineOrderRepository;
import com.valdker.pos.repositories.ShopRepository;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

final class PendingOrderReceiptPrinter {

    interface Callback {
        void onSuccess();
        void onError(@NonNull String message);
        void onSkipped(@NonNull String message);
        void onTimeout(@NonNull String message);
    }

    private static final String TAG = "PENDING_RECEIPT";

    private PendingOrderReceiptPrinter() {}

    static void reprint(@NonNull Context context,
                        @NonNull PendingOrderEntity order,
                        @NonNull List<PendingOrderItemEntity> items,
                        @NonNull Callback callback) {
        Context appCtx = context.getApplicationContext();

        if (!PrinterService.hasBtPermission(appCtx)) {
            callback.onError("Bluetooth permission is required.");
            return;
        }
        String mac = PrinterPrefs.getMac(appCtx);
        if (mac == null || mac.trim().isEmpty()) {
            callback.onError("Printer is not selected.");
            return;
        }

        SessionManager sessionManager = new SessionManager(appCtx);
        String token = sessionManager.getToken();
        AtomicBoolean printed = new AtomicBoolean(false);

        String storedShopName = storedShopName(order);
        String fallbackReceipt = buildReceipt(
                appCtx,
                firstNonEmpty(storedShopName, "VALDKER POS"),
                "",
                "",
                order,
                items
        );

        ShopRepository.getBestShopProfileForReceipt(appCtx, token, new ShopRepository.Callback() {
            @Override
            public void onSuccess(@NonNull Shop shop) {
                String receipt = buildReceipt(
                        appCtx,
                        firstNonEmpty(storedShopName, safe(shop.name, "VALDKER POS")),
                        safe(shop.address, ""),
                        safe(shop.phone, ""),
                        order,
                        items
                );
                printOnce(appCtx, receipt, printed, callback);
            }

            @Override
            public void onEmpty() {
                printOnce(appCtx, fallbackReceipt, printed, callback);
            }

            @Override
            public void onError(@NonNull String message) {
                Log.w(TAG, "Failed to load shop profile for reprint: " + message);
                printOnce(appCtx, fallbackReceipt, printed, callback);
            }
        });
    }

    private static void printOnce(@NonNull Context appCtx,
                                  @NonNull String receipt,
                                  @NonNull AtomicBoolean printed,
                                  @NonNull Callback callback) {
        if (!printed.compareAndSet(false, true)) {
            Log.w(TAG, "Offline receipt print skipped: already printed");
            return;
        }

        PrinterService.printTextAsync(appCtx, receipt, new BluetoothPrinterManager.PrintCallback() {
            @Override
            public void onSuccess() {
                Log.i(TAG, "Offline receipt reprint success");
                callback.onSuccess();
            }

            @Override
            public void onError(@NonNull String message) {
                Log.e(TAG, "Offline receipt reprint failed: " + message);
                callback.onError(message);
            }

            @Override
            public void onSkipped(@NonNull String message) {
                Log.w(TAG, "Offline receipt reprint skipped: " + message);
                callback.onSkipped(message);
            }

            @Override
            public void onTimeout(@NonNull String message) {
                Log.e(TAG, "Offline receipt reprint timed out: " + message);
                callback.onTimeout(message);
            }
        });
    }

    /**
     * Menyusun teks struk cetak ulang.
     *
     * <p>Dibaca dari payload yang sama dengan yang dipakai struk aslinya,
     * lewat ReceiptPayloadReader, lalu disusun ReceiptBuilder. Versi lama
     * menyusun barisnya sendiri di sini, dan hasilnya berbeda dari struk asli:
     * Discount dan VAT/Tax tercetak dengan angka sebenarnya di sini sementara
     * struk asli mencetak nol mati, dan dari beberapa pembayaran hanya yang
     * pertama yang muncul.
     *
     * <p>Nominal dari kolom Room dipakai kalau ada - itu angka yang benar-benar
     * tersimpan - dan payload jadi cadangannya.
     */
    @NonNull
    private static String buildReceipt(@NonNull Context appCtx,
                                       @NonNull String shopName,
                                       @NonNull String shopAddress,
                                       @NonNull String shopPhone,
                                       @NonNull PendingOrderEntity order,
                                       @NonNull List<PendingOrderItemEntity> itemEntities) {
        JSONObject payload = parsePayload(order.rawPayloadJson);
        ReceiptContent content = ReceiptPayloadReader.read(payload);

        content.shopName = shopName;
        content.shopAddress = shopAddress;
        content.shopPhone = shopPhone;
        content.statusBanner = labelForStatus(order.syncStatus);
        content.footerNote = appCtx.getString(R.string.receipt_thank_you);

        content.orderNumber = firstNonEmpty(
                content.orderNumber,
                order.clientOrderId,
                order.localOrderId);
        if (content.deviceTime.isEmpty() && order.createdAt > 0L) {
            content.deviceTime = currentDeviceTimeIso(order.createdAt);
            content.date = ReceiptPayloadReader.isoDate(content.deviceTime);
            content.time = ReceiptPayloadReader.isoTime(content.deviceTime);
        }
        if (content.customer.isEmpty()) {
            content.customer = customerLabel(payload);
        }

        try {
            content.cashier = safe(new SessionManager(appCtx).getUsername(), "");
        } catch (Exception ignored) {
        }

        overrideMoneyFromRoom(content, order);
        overrideItemsFromRoom(content, order, itemEntities);

        return ReceiptBuilder.build(content);
    }

    /**
     * Kolom uang di Room adalah teks desimal yang ditulis saat order disimpan.
     * Dibaca lewat Money, bukan double: struk harus menunjukkan angka yang
     * sama persis dengan yang dikirim ke server.
     */
    private static void overrideMoneyFromRoom(@NonNull ReceiptContent content,
                                              @NonNull PendingOrderEntity order) {
        content.subtotal = preferStored(order.subtotalMoney(), content.subtotal);
        content.discount = preferStored(order.discountMoney(), content.discount);
        content.tax = preferStored(order.taxMoney(), content.tax);
        content.total = preferStored(order.totalMoney(), content.total);
        content.paid = preferStored(order.paidAmountMoney(), content.paid);
        content.change = preferStored(order.changeAmountMoney(), content.change);
    }

    @NonNull
    private static Money preferStored(@NonNull Money stored, @NonNull Money fallback) {
        return stored.isPositive() ? stored : fallback;
    }

    /**
     * Nama dan jumlah item dari Room kalau payload tidak memuatnya - order
     * lama tersimpan sebelum payload memuat nama produk.
     */
    private static void overrideItemsFromRoom(@NonNull ReceiptContent content,
                                              @NonNull PendingOrderEntity order,
                                              @NonNull List<PendingOrderItemEntity> itemEntities) {
        if (itemEntities.isEmpty()) return;

        boolean businessIsWorkshop = safe(order.businessType, "")
                .toLowerCase(Locale.US).equals("workshop");

        if (content.items.isEmpty()) {
            for (PendingOrderItemEntity entity : itemEntities) {
                if (entity == null) continue;
                ReceiptContent.Item item = new ReceiptContent.Item();
                item.name = safe(entity.name, "Item");
                item.qty = Math.max(0, entity.quantity);
                item.unitPrice = entity.priceMoney();
                item.lineTotal = entity.totalMoney().isPositive()
                        ? entity.totalMoney()
                        : entity.priceMoney().times(Math.max(0, entity.quantity));
                if (businessIsWorkshop) {
                    item.typeLabel = workshopItemLabel(entity.itemType);
                }
                content.items.add(item);
            }
            return;
        }

        for (int i = 0; i < content.items.size() && i < itemEntities.size(); i++) {
            PendingOrderItemEntity entity = itemEntities.get(i);
            if (entity == null) continue;
            ReceiptContent.Item item = content.items.get(i);
            if ("Item".equals(item.name) && !safe(entity.name, "").isEmpty()) {
                item.name = entity.name.trim();
            }
            if (businessIsWorkshop) {
                item.typeLabel = workshopItemLabel(entity.itemType);
            }
        }
    }

    @NonNull
    private static String workshopItemLabel(@Nullable String itemType) {
        String value = safe(itemType, "").toUpperCase(Locale.US);
        if ("SERVICE".equals(value)) return "Service";
        if ("SPAREPART".equals(value) || "PART".equals(value)) return "Sparepart";
        if ("MENU".equals(value)) return "Menu";
        return "Product";
    }


    @NonNull
    private static String labelForStatus(@NonNull String status) {
        if (OfflineOrderRepository.STATUS_SYNCED.equals(status)) return "SYNCED / REPRINT";
        if (OfflineOrderRepository.STATUS_NEEDS_REVIEW.equals(status)) return "NEEDS REVIEW / REPRINT";
        return "OFFLINE / PENDING SYNC";
    }

    @NonNull
    private static String itemTypeLabel(@NonNull String businessType, @NonNull ReceiptItem item) {
        if ("workshop".equals(businessType)) {
            if ("service".equals(item.itemType)) return "Service";
            if ("sparepart".equals(item.itemType)) return "Sparepart";
            if ("menu".equals(item.itemType)) return "Menu";
            return "Product";
        }
        String orderType = safe(item.orderType, "").toUpperCase(Locale.US);
        if ("DINE_IN".equals(orderType)) return "(* DINE IN)";
        if ("TAKE_OUT".equals(orderType)) return "(* TAKE OUT)";
        if ("DELIVERY".equals(orderType)) return "(^ DELIVERY)";
        return "";
    }

    @NonNull
    private static String paymentLabel(@NonNull JSONObject payload, @NonNull PendingOrderEntity order) {
        JSONObject payment = firstPayment(payload);
        return firstNonEmpty(
                payload.optString("payment_method", ""),
                payment.optString("method_code", ""),
                payment.optString("payment_method", ""),
                order.paymentMethodId != null ? String.valueOf(order.paymentMethodId) : "",
                "-"
        );
    }

    @NonNull
    private static String customerLabel(@NonNull JSONObject payload) {
        String value = payload.optString("customer_name", "");
        if (!value.trim().isEmpty()) return value.trim();
        Object customer = payload.opt("customer");
        if (customer == null || JSONObject.NULL.equals(customer)) return "";
        String s = String.valueOf(customer).trim();
        return s.isEmpty() || "0".equals(s) ? "" : "Customer #" + s;
    }

    @NonNull
    private static String storedShopName(@NonNull PendingOrderEntity order) {
        String name = safe(order.shopName, "");
        String code = safe(order.shopCode, "");
        if (!name.isEmpty() && !code.isEmpty()) return name + " (" + code + ")";
        if (!name.isEmpty()) return name;
        return code;
    }

    @NonNull
    private static JSONObject firstPayment(@NonNull JSONObject payload) {
        JSONArray payments = payload.optJSONArray("payments");
        if (payments == null || payments.length() == 0) return new JSONObject();
        JSONObject payment = payments.optJSONObject(0);
        return payment != null ? payment : new JSONObject();
    }

    private static double paymentAmount(@NonNull JSONObject payload) {
        return optDouble(firstPayment(payload), "amount");
    }

    @NonNull
    private static JSONObject parsePayload(@Nullable String rawPayloadJson) {
        try {
            if (rawPayloadJson == null || rawPayloadJson.trim().isEmpty()) return new JSONObject();
            return new JSONObject(rawPayloadJson);
        } catch (Exception e) {
            Log.e(TAG, "Unable to parse pending order payload for receipt", e);
            return new JSONObject();
        }
    }

    @NonNull
    private static String normalizeItemType(@Nullable String itemType) {
        String value = safe(itemType, "").toUpperCase(Locale.US);
        if ("SERVICE".equals(value)) return "service";
        if ("SPAREPART".equals(value) || "PART".equals(value)) return "sparepart";
        if ("MENU".equals(value)) return "menu";
        return "product";
    }

    @NonNull
    private static String currentDeviceTimeIso(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(new Date(millis));
    }

    @NonNull
    private static String formatMoney(double value) {
        return Money.ofDouble(value).orZeroIfNegative().format();
    }

    private static double optDouble(@Nullable JSONObject object, @NonNull String key) {
        if (object == null) return 0d;
        try {
            return object.optDouble(key, 0d);
        } catch (Exception ignored) {
            return 0d;
        }
    }

    private static int firstInt(@Nullable JSONObject object, @NonNull String... keys) {
        if (object == null) return 0;
        for (String key : keys) {
            if (!object.has(key) || object.isNull(key)) continue;
            Object value = object.opt(key);
            if (value instanceof Number) return ((Number) value).intValue();
            try {
                return Integer.parseInt(String.valueOf(value).trim());
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    private static double positiveOr(double primary, double fallback) {
        return primary > 0d ? primary : Math.max(0d, fallback);
    }

    private static double firstPositive(double... values) {
        for (double value : values) {
            if (value > 0d) return value;
        }
        return 0d;
    }

    @NonNull
    private static String firstNonEmpty(@Nullable String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value == null) continue;
            String clean = value.trim();
            if (!clean.isEmpty()) return clean;
        }
        return "";
    }

    @NonNull
    private static String safe(@Nullable String value, @NonNull String fallback) {
        if (TextUtils.isEmpty(value)) return fallback;
        String clean = value.trim();
        return clean.isEmpty() ? fallback : clean;
    }

    private static final class ReceiptItem {
        String name = "Item";
        String itemType = "product";
        String orderType = "";
        int quantity = 0;
        double price = 0d;
        double total = 0d;
    }
}
