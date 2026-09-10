package com.valdker.pos.ui.offlineorders;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.valdker.pos.SessionManager;
import com.valdker.pos.local.PendingOrderEntity;
import com.valdker.pos.local.PendingOrderItemEntity;
import com.valdker.pos.models.Shop;
import com.valdker.pos.print.BluetoothPrinterManager;
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

    @NonNull
    private static String buildReceipt(@NonNull Context appCtx,
                                       @NonNull String shopName,
                                       @NonNull String shopAddress,
                                       @NonNull String shopPhone,
                                       @NonNull PendingOrderEntity order,
                                       @NonNull List<PendingOrderItemEntity> itemEntities) {
        JSONObject payload = parsePayload(order.rawPayloadJson);
        List<ReceiptItem> items = buildItems(payload, itemEntities);
        String businessType = safe(order.businessType, "").toLowerCase(Locale.US);
        String receiptNumber = firstNonEmpty(
                payload.optString("client_order_id", ""),
                order.clientOrderId,
                order.localOrderId
        );
        String transactionTime = firstNonEmpty(
                payload.optString("offline_created_at", ""),
                payload.optString("device_time", ""),
                order.createdAt > 0L ? currentDeviceTimeIso(order.createdAt) : ""
        );
        String label = labelForStatus(order.syncStatus);
        String customer = customerLabel(payload);
        String payment = paymentLabel(payload, order);
        // Kolom uang di Room kini teks desimal; dibaca lewat Money lalu baru
        // dikonversi untuk DTO struk yang masih memakai double (lapis 5).
        double subtotal = positiveOr(order.subtotalMoney().toDouble(), optDouble(payload, "subtotal"));
        double discount = positiveOr(order.discountMoney().toDouble(), optDouble(payload, "discount"));
        double tax = positiveOr(order.taxMoney().toDouble(), optDouble(payload, "tax"));
        double total = positiveOr(order.totalMoney().toDouble(), optDouble(payload, "total"));
        double paid = positiveOr(order.paidAmountMoney().toDouble(), paymentAmount(payload));
        double change = positiveOr(order.changeAmountMoney().toDouble(), optDouble(payload, "change_amount"));
        String tableNumber = payload.optString("table_number", "");
        String deliveryAddress = payload.optString("delivery_address", "");

        String cashier = "";
        try {
            String username = new SessionManager(appCtx).getUsername();
            cashier = safe(username, "");
        } catch (Exception ignored) {
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[C]<b>").append(shopName).append("</b>\n");
        if (!shopAddress.trim().isEmpty()) sb.append("[C]").append(shopAddress.trim()).append("\n");
        if (!shopPhone.trim().isEmpty()) sb.append("[C]").append(shopPhone.trim()).append("\n");
        sb.append("[C]--------------------------------\n");
        sb.append("[C]<b>").append(label).append("</b>\n");
        sb.append("[C]--------------------------------\n");
        sb.append("[L]Order:[R]").append(receiptNumber).append("\n");
        if (!cashier.isEmpty()) sb.append("[L]Cashier:[R]").append(cashier).append("\n");
        if (!customer.isEmpty()) sb.append("[L]Customer:[R]").append(customer).append("\n");
        if (!transactionTime.isEmpty()) sb.append("[L]Device Time:[R]").append(transactionTime).append("\n");
        if (!tableNumber.trim().isEmpty()) sb.append("[L]Table:[R]").append(tableNumber.trim()).append("\n");
        if (!deliveryAddress.trim().isEmpty()) sb.append("[L]Delivery:[R]").append(deliveryAddress.trim()).append("\n");
        sb.append("[C]--------------------------------\n");

        for (ReceiptItem item : items) {
            sb.append("[L]<b>").append(item.name).append("</b>[R]<b>")
                    .append(formatMoney(item.total))
                    .append("</b>\n");
            String typeLabel = itemTypeLabel(businessType, item);
            if (!typeLabel.isEmpty()) sb.append("[L]").append(typeLabel).append("\n");
            sb.append("[L]").append(Math.max(0, item.quantity))
                    .append(" x ")
                    .append(formatMoney(item.price))
                    .append("\n\n");
        }

        sb.append("[C]--------------------------------\n");
        sb.append("[L]Subtotal[R]").append(formatMoney(subtotal)).append("\n");
        sb.append("[L]Discount[R]").append(formatMoney(discount)).append("\n");
        sb.append("[L]VAT / Tax[R]").append(formatMoney(tax)).append("\n");
        double deliveryFee = optDouble(payload, "delivery_fee");
        if (deliveryFee > 0d) sb.append("[L]Delivery Fee[R]").append(formatMoney(deliveryFee)).append("\n");
        sb.append("[C]--------------------------------\n");
        sb.append("[L]<b>Total</b>[R]<b>").append(formatMoney(total)).append("</b>\n");
        sb.append("[L]Payment[R]").append(payment).append("\n");
        if (paid > 0d) {
            sb.append("[L]Paid[R]").append(formatMoney(paid)).append("\n");
            sb.append("[L]Change[R]").append(formatMoney(change)).append("\n");
        }
        sb.append("[C]--------------------------------\n");
        sb.append("[C]Receipt reprint\n");
        sb.append("[C]").append(shopName).append("\n\n\n");
        return sb.toString();
    }

    @NonNull
    private static List<ReceiptItem> buildItems(@NonNull JSONObject payload,
                                                @NonNull List<PendingOrderItemEntity> itemEntities) {
        List<ReceiptItem> items = new ArrayList<>();
        JSONArray rawItems = payload.optJSONArray("items");
        int rawCount = rawItems != null ? rawItems.length() : 0;
        int count = Math.max(rawCount, itemEntities.size());

        for (int i = 0; i < count; i++) {
            JSONObject raw = rawItems != null ? rawItems.optJSONObject(i) : null;
            PendingOrderItemEntity entity = i < itemEntities.size() ? itemEntities.get(i) : null;
            ReceiptItem item = new ReceiptItem();
            int productId = entity != null ? entity.productId : firstInt(raw, "product", "product_id", "item_id");
            item.name = firstNonEmpty(
                    entity != null ? entity.name : "",
                    raw != null ? raw.optString("name", "") : "",
                    raw != null ? raw.optString("product_name", "") : "",
                    productId > 0 ? "Product #" + productId : "Item"
            );
            item.itemType = normalizeItemType(firstNonEmpty(
                    raw != null ? raw.optString("item_type", "") : "",
                    entity != null ? entity.itemType : ""
            ));
            item.orderType = raw != null ? raw.optString("order_type", "") : "";
            item.quantity = entity != null && entity.quantity > 0
                    ? entity.quantity
                    : Math.max(0, firstInt(raw, "quantity", "qty"));
            item.price = entity != null && entity.priceMoney().isPositive()
                    ? entity.priceMoney().toDouble()
                    : optDouble(raw, "price");
            item.total = entity != null && entity.totalMoney().isPositive()
                    ? entity.totalMoney().toDouble()
                    : firstPositive(optDouble(raw, "total"), optDouble(raw, "subtotal"), item.price * item.quantity);
            items.add(item);
        }
        return items;
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
        return String.format(Locale.US, "$%.2f", Math.max(0d, value));
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
