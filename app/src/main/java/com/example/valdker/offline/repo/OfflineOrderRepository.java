package com.example.valdker.offline.repo;

import android.content.Context;

import androidx.annotation.NonNull;

import com.example.valdker.SessionManager;
import com.example.valdker.models.CartItem;
import com.example.valdker.offline.db.ValdkerDatabase;
import com.example.valdker.offline.db.entities.OrderEntity;
import com.example.valdker.offline.db.entities.OrderItemEntity;
import com.example.valdker.offline.db.entities.ProductStockEntity;
import com.example.valdker.offline.db.entities.ShiftEntity;
import com.example.valdker.offline.db.models.OrderWithItems;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class OfflineOrderRepository {

    private final ValdkerDatabase db;
    private final Context ctx;

    public OfflineOrderRepository(@NonNull Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.db = ValdkerDatabase.get(this.ctx);
    }

    // buat invoice lokal: OFF-20260224-0001 (contoh)
    private String generateLocalInvoice() {
        String d = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
        // simple: pakai timestamp. (nanti bisa ganti ke counter per hari)
        return "OFF-" + d + "-" + System.currentTimeMillis();
    }

    private String nowIso() {
        // cukup ISO sederhana
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(new Date());
    }

    public OrderWithItems createOfflineOrder(@NonNull List<CartItem> cartItems,
                                             @NonNull String orderTypeHeader,
                                             @NonNull String paymentMethod,
                                             @NonNull String tableNumber,
                                             @NonNull String deliveryAddress,
                                             double deliveryFee,
                                             double cashReceived,
                                             double changeAmount) {

        String invoice = generateLocalInvoice();
        String createdAt = nowIso();

        SessionManager sm = new SessionManager(ctx);
        String cashier = sm.getUsername();

        double subtotal = 0.0;
        List<OrderItemEntity> items = new ArrayList<>();

        // hitung subtotal dan items
        for (CartItem it : cartItems) {

            int qty = Math.max(1, it.qty);

            // 🔴 VALIDASI STOCK
            ProductStockEntity stock = db.productStockDao().getById(it.productId);

            if (stock == null) {
                throw new IllegalStateException("Stock not found for product: " + it.productId);
            }

            if (stock.stock_qty < qty) {
                throw new IllegalStateException("Stock not enough for: " + it.name);
            }

            double unit = Math.max(0, it.price);
            double line = qty * unit;
            subtotal += line;

            OrderItemEntity row = new OrderItemEntity(-1);
            row.product_id = it.productId;
            row.product_name = it.name;
            row.quantity = qty;
            row.unit_price = unit;
            row.line_total = line;
            row.order_type = it.orderType;

            items.add(row);
        }

        OrderEntity order = new OrderEntity(invoice, createdAt);
        order.cashier_username = cashier;
        order.order_type = orderTypeHeader;

        order.table_number = tableNumber;
        order.delivery_address = deliveryAddress;

        order.payment_method = paymentMethod;
        order.cash_received = cashReceived;
        order.change_amount = changeAmount;

        order.subtotal = subtotal;
        order.discount = 0.0;
        order.tax = 0.0;
        order.delivery_fee = Math.max(0, deliveryFee);
        order.total = subtotal + order.delivery_fee;

        // ✅ WAJIB SHIFT AKTIF (offline)
        ShiftEntity active = db.shiftDao().getActiveShift();
        if (active == null) {
            throw new IllegalStateException("Shift belum dibuka. Silakan OPEN SHIFT dulu.");
        }
        order.shift_local_id = active.id;

        // insert order + items
        long orderId = db.orderDao().insert(order);
        for (OrderItemEntity row : items) {
            db.productStockDao().deductStock(row.product_id, row.quantity);
        }
        for (OrderItemEntity row : items) row.order_id = orderId;
        db.orderItemDao().insertAll(items);

        for (OrderItemEntity row : items) {
            db.productStockDao().deductStock(row.product_id, row.quantity);
        }

        OrderWithItems out = new OrderWithItems();
        out.order = db.orderDao().getById(orderId);
        out.items = db.orderItemDao().listByOrderId(orderId);
        return out;
    }
}