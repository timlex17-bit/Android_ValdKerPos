package com.example.valdker.ui;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.valdker.R;
import com.example.valdker.SessionManager;
import com.example.valdker.cart.CartManager;
import com.example.valdker.models.CartItem;
import com.example.valdker.offline.db.models.OrderWithItems;
import com.example.valdker.offline.repo.OfflineOrderRepository;
import com.example.valdker.offline.sync.SyncScheduler;
import com.example.valdker.ui.checkout.CheckoutDialogFragment;
import com.example.valdker.ui.checkout.NativeCheckoutDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.dantsu.escposprinter.EscPosPrinter;
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection;
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CartFragment extends Fragment {

    private static final String TAG = "CART_FRAGMENT";
    private static final int REQ_BT_CONNECT = 2001;

    // prefs untuk simpan input terakhir (table/delivery)
    private static final String PREF_META = "valdker_receipt_meta";
    private static final String KEY_TABLE = "table_number";
    private static final String KEY_DELIVERY_ADDR = "delivery_address";
    private static final String KEY_DELIVERY_FEE = "delivery_fee";

    private RecyclerView rv;
    private TextView tvEmpty;
    private TextView tvSubtotal;
    private ImageButton btnClose;
    private Button btnContinuePayment;
    private Button btnCancelOrder;

    // Set All chips (header)
    private ChipGroup chipGroupSetAllType;
    private Chip chipSetAllDineIn, chipSetAllTakeOut, chipSetAllDelivery;

    private CartAdapter adapter;
    private final NumberFormat usd = NumberFormat.getCurrencyInstance(Locale.US);

    private CartManager cart;

    // Listener to refresh the UI whenever the cart changes
    private final CartManager.Listener cartListener = this::render;

    // Keep last successful payment order data for printing
    private OrderData pendingPrintOrder = null;

    public CartFragment() {
        super(R.layout.fragment_cart);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        cart = CartManager.getInstance(requireContext());

        rv = view.findViewById(R.id.rvCart);
        tvEmpty = view.findViewById(R.id.tvCartEmpty);
        tvSubtotal = view.findViewById(R.id.tvSubtotal);
        btnClose = view.findViewById(R.id.btnCloseCart);
        btnContinuePayment = view.findViewById(R.id.btnContinuePayment);
        btnCancelOrder = view.findViewById(R.id.btnCancelOrder);

        // header Set All chips
        chipGroupSetAllType = view.findViewById(R.id.chipGroupSetAllType);
        chipSetAllDineIn = view.findViewById(R.id.chipSetAllDineIn);
        chipSetAllTakeOut = view.findViewById(R.id.chipSetAllTakeOut);
        chipSetAllDelivery = view.findViewById(R.id.chipSetAllDelivery);

        View dim = view.findViewById(R.id.viewDim);
        if (dim != null) dim.setOnClickListener(v -> close());

        rv.setLayoutManager(new LinearLayoutManager(requireContext()));

        adapter = new CartAdapter(new CartAdapter.Listener() {
            @Override
            public void onIncrease(@NonNull CartItem item) {
                cart.setQty(item.productId, item.qty + 1);
            }

            @Override
            public void onDecrease(@NonNull CartItem item) {
                cart.setQty(item.productId, item.qty - 1);
            }

            @Override
            public void onRemove(@NonNull CartItem item) {
                cart.remove(item.productId);
            }

            @Override
            public void onTypeChanged(@NonNull CartItem item, @NonNull String orderType) {
                cart.setOrderType(item.productId, orderType);
                syncSetAllChipsFromCart();
            }
        });

        rv.setAdapter(adapter);

        btnClose.setOnClickListener(v -> close());

        setupSetAllChips();

        // ✅ sekarang tombol bayar akan minta info Table/Delivery dulu (kalau perlu)
//        btnContinuePayment.setOnClickListener(v -> openCheckoutPopup());
        btnContinuePayment.setOnClickListener(v -> openNativeCheckout());

        btnCancelOrder.setOnClickListener(v -> {
            cart.clear();
            Toast.makeText(requireContext(), "Order cancelled", Toast.LENGTH_SHORT).show();
            close();
        });

        render();
    }

    private void openNativeCheckout() {

        List<CartItem> items = cart.getItems();
        if (items.isEmpty()) return;

        boolean needTable = false;
        boolean needDelivery = false;

        for (CartItem it : items) {
            String t = normalizeType(it.orderType);
            if ("DINE_IN".equals(t)) needTable = true;
            if ("DELIVERY".equals(t)) needDelivery = true;
        }

        double total = cart.getTotalAmount();

        NativeCheckoutDialogFragment dialog =
                NativeCheckoutDialogFragment.newInstance(total, needTable, needDelivery);

        dialog.setListener((paymentMethod, cashReceived, changeAmount,
                            tableNumber, deliveryAddress, deliveryFee) -> {

            // Simpan ke Room
            new Thread(() -> {

                OfflineOrderRepository repo =
                        new OfflineOrderRepository(requireContext());

                try {

                    var result = repo.createOfflineOrder(
                            items,
                            cart.resolveOrderTypeForBackend(),
                            paymentMethod,
                            tableNumber,
                            deliveryAddress,
                            deliveryFee,
                            cashReceived,
                            changeAmount
                    );

                    OrderData od = convertToOrderData(result);

                    requireActivity().runOnUiThread(() -> {
                        pendingPrintOrder = od;
                        tryPrintPendingOrder();
                        cart.clear();
                    });

                    SyncScheduler.enqueue(requireContext());

                } catch (IllegalStateException ex) {

                    requireActivity().runOnUiThread(() ->
                            Toast.makeText(requireContext(),
                                    "Stock Error: " + ex.getMessage(),
                                    Toast.LENGTH_LONG).show()
                    );
                }

            }).start();
        });

        dialog.show(getParentFragmentManager(), "native_checkout");
    }

    private OrderData convertToOrderData(
            com.example.valdker.offline.db.models.OrderWithItems data) {

        OrderData o = new OrderData();
        o.shopName = "ValdKer POS";
        o.invoice = data.order.invoice_number;
        o.cashier = data.order.cashier_username;
        o.date = data.order.created_at_iso;

        o.tableNumber = data.order.table_number;
        o.deliveryAddress = data.order.delivery_address;
        o.deliveryFee = data.order.delivery_fee;

        o.subtotal = data.order.subtotal;
        o.discount = data.order.discount;
        o.tax = data.order.tax;
        o.total = data.order.total;

        for (var it : data.items) {
            OrderItemData row = new OrderItemData();
            row.name = it.product_name;
            row.qty = it.quantity;
            row.unitPrice = it.unit_price;
            row.lineTotal = it.line_total;
            row.orderType = it.order_type;
            o.items.add(row);
        }

        return o;
    }

    @Override
    public void onStart() {
        super.onStart();
        cart.addListener(cartListener);
        render();
    }

    @Override
    public void onStop() {
        super.onStop();
        cart.removeListener(cartListener);
    }

    private void setupSetAllChips() {
        if (chipGroupSetAllType == null) return;

        chipGroupSetAllType.setOnCheckedChangeListener((group, checkedId) -> {
            if (!isAdded()) return;

            String type;
            if (checkedId == R.id.chipSetAllDineIn) {
                type = CartManager.TYPE_DINE_IN;
            } else if (checkedId == R.id.chipSetAllDelivery) {
                type = CartManager.TYPE_DELIVERY;
            } else if (checkedId == R.id.chipSetAllTakeOut) {
                type = CartManager.TYPE_TAKE_OUT;
            } else {
                type = "";
            }

            cart.setAllOrderType(type);
        });
    }

    private void syncSetAllChipsFromCart() {
        if (!isAdded() || getView() == null) return;
        if (chipGroupSetAllType == null) return;

        List<CartItem> items = cart.getItems();

        if (items.isEmpty()) {
            setSetAllCheckedSilently("");
            return;
        }

        for (CartItem it : items) {
            if (it.orderType == null || it.orderType.trim().isEmpty()) {
                setSetAllCheckedSilently("");
                return;
            }
        }

        String first = normalizeType(items.get(0).orderType);
        boolean same = true;
        for (CartItem it : items) {
            if (!first.equals(normalizeType(it.orderType))) {
                same = false;
                break;
            }
        }

        if (same) setSetAllCheckedSilently(first);
        else setSetAllCheckedSilently("");
    }

    private void setSetAllCheckedSilently(@NonNull String type) {
        if (chipGroupSetAllType == null) return;

        chipGroupSetAllType.setOnCheckedChangeListener(null);

        if (type.isEmpty()) {
            chipGroupSetAllType.clearCheck();
        } else if (CartManager.TYPE_DINE_IN.equals(type)) {
            if (chipSetAllDineIn != null) chipSetAllDineIn.setChecked(true);
        } else if (CartManager.TYPE_DELIVERY.equals(type)) {
            if (chipSetAllDelivery != null) chipSetAllDelivery.setChecked(true);
        } else if (CartManager.TYPE_TAKE_OUT.equals(type)) {
            if (chipSetAllTakeOut != null) chipSetAllTakeOut.setChecked(true);
        } else {
            chipGroupSetAllType.clearCheck();
        }

        setupSetAllChips();
    }

    private String normalizeType(@Nullable String t) {
        if (t == null) return "";
        String v = t.trim().toUpperCase(Locale.US);
        if (CartManager.TYPE_DINE_IN.equals(v)) return CartManager.TYPE_DINE_IN;
        if (CartManager.TYPE_TAKE_OUT.equals(v)) return CartManager.TYPE_TAKE_OUT;
        if (CartManager.TYPE_DELIVERY.equals(v)) return CartManager.TYPE_DELIVERY;
        return "";
    }

    // =========================================================
    // CHECKOUT FLOW (minta Table/Delivery dulu kalau perlu)
    // =========================================================
    private void openCheckoutPopup() {
        if (!isAdded()) return;

        List<CartItem> itemsNow = cart.getItems();
        if (itemsNow.isEmpty()) {
            Toast.makeText(requireContext(), "Cart is empty", Toast.LENGTH_SHORT).show();
            return;
        }

        // ✅ REQUIRED: all items must be selected
        for (CartItem it : itemsNow) {
            if (it.orderType == null || it.orderType.trim().isEmpty()) {
                Toast.makeText(requireContext(),
                        "Pilih tipe pesanan (Dine-In/Take-Out/Delivery) untuk semua item.",
                        Toast.LENGTH_LONG).show();
                return;
            }
        }

        // cek kebutuhan table/delivery dari item
        boolean needTable = false;
        boolean needDelivery = false;
        for (CartItem it : itemsNow) {
            String t = normalizeType(it.orderType);
            if (CartManager.TYPE_DINE_IN.equals(t)) needTable = true;
            if (CartManager.TYPE_DELIVERY.equals(t)) needDelivery = true;
        }

        if (!needTable && !needDelivery) {
            // tidak perlu input tambahan
            openCheckoutPopupWithMeta(itemsNow, "", "", 0.0);
            return;
        }

        // ambil default terakhir dari prefs
        SharedPreferences sp = requireContext().getSharedPreferences(PREF_META, Context.MODE_PRIVATE);
        String lastTable = sp.getString(KEY_TABLE, "");
        String lastAddr = sp.getString(KEY_DELIVERY_ADDR, "");
        String lastFee = sp.getString(KEY_DELIVERY_FEE, "0.00");

        // bikin form simple
        LinearLayout wrap = new LinearLayout(requireContext());
        wrap.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        wrap.setPadding(pad, pad, pad, pad);

        EditText etTable = new EditText(requireContext());
        etTable.setHint("Table Number (contoh: 2)");
        etTable.setInputType(InputType.TYPE_CLASS_NUMBER);
        etTable.setText(lastTable);

        EditText etAddr = new EditText(requireContext());
        etAddr.setHint("Delivery (contoh: Comoro)");
        etAddr.setInputType(InputType.TYPE_CLASS_TEXT);
        etAddr.setText(lastAddr);

        EditText etFee = new EditText(requireContext());
        etFee.setHint("Delivery Fee (contoh: 1.00)");
        etFee.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etFee.setText(lastFee);

        if (needTable) wrap.addView(etTable);
        if (needDelivery) {
            if (needTable) addSpace(wrap, 12);
            wrap.addView(etAddr);
            addSpace(wrap, 12);
            wrap.addView(etFee);
        }

        final boolean fNeedTable = needTable;
        final boolean fNeedDelivery = needDelivery;
        final List<CartItem> fItemsNow = new ArrayList<>(itemsNow);
        final SharedPreferences fSp = sp;

        String title = "Detail Pesanan";

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setView(wrap)
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .setPositiveButton("OK", (d, w) -> {

                    String table = fNeedTable ? safeStr(etTable.getText().toString()) : "";
                    String addr  = fNeedDelivery ? safeStr(etAddr.getText().toString()) : "";
                    double fee   = fNeedDelivery ? parseMoney(etFee.getText().toString()) : 0.0;

                    // validasi ringan
                    if (fNeedTable && table.isEmpty()) {
                        Toast.makeText(requireContext(), "Table number wajib untuk Dine-In.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (fNeedDelivery && addr.isEmpty()) {
                        Toast.makeText(requireContext(), "Delivery wajib untuk pesanan Delivery.", Toast.LENGTH_LONG).show();
                        return;
                    }

                    if (fee < 0) fee = 0.0; // ✅ ini OK, fee variabel lokal di lambda

                    // simpan terakhir
                    fSp.edit()
                            .putString(KEY_TABLE, table)
                            .putString(KEY_DELIVERY_ADDR, addr)
                            .putString(KEY_DELIVERY_FEE, String.format(Locale.US, "%.2f", fee))
                            .apply();

                    openCheckoutPopupWithMeta(fItemsNow, table, addr, fee);
                })
        .show();
    }

    private void addSpace(@NonNull LinearLayout wrap, int dp) {
        View v = new View(requireContext());
        int h = (int) (dp * getResources().getDisplayMetrics().density);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, h
        ));
        wrap.addView(v);
    }

    private String safeStr(String s) {
        return s == null ? "" : s.trim();
    }

    private double parseMoney(String s) {
        try {
            if (s == null) return 0.0;
            String t = s.trim().replace(",", "");
            if (t.isEmpty()) return 0.0;
            return Double.parseDouble(t);
        } catch (Exception ignored) {
            return 0.0;
        }
    }

    private void openCheckoutPopupWithMeta(@NonNull List<CartItem> itemsNow,
                                           @NonNull String tableNumber,
                                           @NonNull String deliveryAddress,
                                           double deliveryFee) {
        // snapshot for printing later (do NOT depend on cart after clear)
        final OrderData draftForPrint = buildOrderDataSnapshot(itemsNow, "", tableNumber, deliveryAddress, deliveryFee);

        try {
            SessionManager session = new SessionManager(requireContext());
            String token = session.getToken();

            if (token == null || token.trim().isEmpty()) {
                Toast.makeText(requireContext(), "Token kosong. Silakan login ulang.", Toast.LENGTH_LONG).show();
                return;
            }

            JSONObject payload = new JSONObject();
            payload.put("token", token.trim());

            double subtotal = cart.getTotalAmount();
            payload.put("subtotal", subtotal);
            payload.put("customer_id", JSONObject.NULL);

            // order_type header -> DINE_IN / TAKE_OUT / DELIVERY / GENERAL
            String finalType = cart.resolveOrderTypeForBackend();
            payload.put("order_type", finalType);
            payload.put("default_order_type", finalType);

            // ✅ kirim meta sesuai struk
            payload.put("table_number", tableNumber);
            payload.put("delivery_address", deliveryAddress);
            payload.put("delivery_fee", deliveryFee);

            // items
            JSONArray itemsArr = new JSONArray();
            for (CartItem it : itemsNow) {
                JSONObject row = new JSONObject();
                row.put("product", safeIntOrString(it.productId));
                row.put("quantity", Math.max(1, it.qty));
                row.put("order_type", normalizeType(it.orderType));
                itemsArr.put(row);
            }
            payload.put("items", itemsArr);

            // backward compat
            payload.put("cart", itemsArr);

            Log.d("CART_PAYLOAD", payload.toString());

            String payloadJson = payload.toString();

            CheckoutDialogFragment dialog = CheckoutDialogFragment.newInstance(
                    payloadJson,
                    "https://valdker-vue-js.vercel.app/checkout"
            );

            dialog.setListener(new CheckoutDialogFragment.Listener() {
                @Override
                public void onSuccess(@NonNull String orderId) {
                    if (!isAdded()) return;

                    draftForPrint.invoice = "INV" + (orderId.isEmpty() ? "-" : orderId);
                    pendingPrintOrder = draftForPrint;

                    tryPrintPendingOrder();

                    cart.clear();

                    Toast.makeText(
                            requireContext(),
                            "Payment success. Order #" + (orderId.isEmpty() ? "-" : orderId),
                            Toast.LENGTH_LONG
                    ).show();

                    close();
                }

                @Override
                public void onCancel() {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), "Payment cancelled", Toast.LENGTH_SHORT).show();
                }
            });

            dialog.show(requireActivity().getSupportFragmentManager(), "checkout_dialog");

        } catch (Exception e) {
            Toast.makeText(requireContext(),
                    "Failed to open checkout: " + e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private Object safeIntOrString(@Nullable String v) {
        if (v == null) return "";
        String s = v.trim();
        try {
            return Integer.parseInt(s);
        } catch (Exception ignored) {
            return s;
        }
    }

    private void render() {
        if (!isAdded() || getView() == null) return;

        List<CartItem> items = cart.getItems();

        for (CartItem it : items) {
            it.orderType = normalizeType(it.orderType);
        }

        adapter.submit(items);

        boolean empty = items.isEmpty();
        rv.setVisibility(empty ? View.GONE : View.VISIBLE);
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);

        tvSubtotal.setText(usd.format(cart.getTotalAmount()));

        btnContinuePayment.setEnabled(!empty);
        btnCancelOrder.setEnabled(!empty);

        syncSetAllChipsFromCart();
    }

    private void close() {
        if (!isAdded()) return;

        View overlay = requireActivity().findViewById(R.id.overlayContainer);
        if (overlay != null) overlay.setVisibility(View.GONE);

        requireActivity().getSupportFragmentManager().popBackStack();
    }

    // =========================================================
    // BLUETOOTH PRINT (ESC/POS)
    // =========================================================

    private void tryPrintPendingOrder() {
        if (!isAdded() || pendingPrintOrder == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQ_BT_CONNECT);
                return;
            }
        }

        printReceiptWithRetry(pendingPrintOrder, 1);
        pendingPrintOrder = null;
    }

    @Nullable
    private BluetoothConnection selectSavedPrinterConnection() {
        String mac = com.example.valdker.print.PrinterPrefs.getMac(requireContext());
        if (mac == null || mac.trim().isEmpty()) return null;

        BluetoothPrintersConnections printers = new BluetoothPrintersConnections();
        BluetoothConnection[] list = printers.getList();
        if (list == null) return null;

        for (BluetoothConnection c : list) {
            try {
                if (c != null && c.getDevice() != null) {
                    String addr = c.getDevice().getAddress();
                    if (mac.equalsIgnoreCase(addr)) return c;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private void printReceiptWithRetry(@NonNull OrderData order, int attempt) {
        try {
            BluetoothConnection connection = selectSavedPrinterConnection();
            if (connection == null) {
                Toast.makeText(requireContext(),
                        "Printer belum dipilih. Buka Settings > Printer.",
                        Toast.LENGTH_LONG).show();
                return;
            }

            EscPosPrinter printer = new EscPosPrinter(connection, 203, 48f, 32);
            printer.printFormattedText(buildReceiptText(order));

            Toast.makeText(requireContext(), "Receipt printed", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            if (attempt < 3) {
                int nextAttempt = attempt + 1;
                Toast.makeText(requireContext(),
                        "Print retry " + nextAttempt + "/3...",
                        Toast.LENGTH_SHORT).show();

                if (rv != null) {
                    rv.postDelayed(() -> printReceiptWithRetry(order, nextAttempt), 700);
                } else {
                    printReceiptWithRetry(order, nextAttempt);
                }
            } else {
                Toast.makeText(requireContext(),
                        "Print falha setelah 3x: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
                Log.e(TAG, "Print failed after retry", e);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_BT_CONNECT) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted) {
                tryPrintPendingOrder();
            } else {
                Toast.makeText(requireContext(),
                        "Bluetooth permission denied. Labele print.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private OrderData buildOrderDataSnapshot(@NonNull List<CartItem> itemsNow,
                                             @NonNull String invoice,
                                             @NonNull String tableNumber,
                                             @NonNull String deliveryAddress,
                                             double deliveryFee) {
        OrderData o = new OrderData();
        o.shopName = "ValdKer POS";
        o.shopAddress = "";
        o.shopPhone = "";

        o.invoice = invoice.isEmpty() ? "INV-" : invoice;

        try {
            SessionManager sm = new SessionManager(requireContext());
            String username = sm.getUsername();
            o.cashier = (username == null || username.isEmpty()) ? "-" : username;
        } catch (Exception ignored) {
            o.cashier = "-";
        }

        o.date = new SimpleDateFormat("dd/MM/yy HH:mm", Locale.US).format(new Date());

        o.tableNumber = tableNumber;
        o.deliveryAddress = deliveryAddress;
        o.deliveryFee = Math.max(0, deliveryFee);

        double subtotal = cart.getTotalAmount();
        o.subtotal = subtotal;
        o.discount = 0.0;
        o.tax = 0.0;

        // ✅ total seperti contoh: subtotal + deliveryFee
        o.total = subtotal + o.deliveryFee;

        for (CartItem it : itemsNow) {
            OrderItemData row = new OrderItemData();
            row.name = (it.name != null && !it.name.isEmpty()) ? it.name : ("Product #" + it.productId);
            row.qty = Math.max(1, it.qty);
            row.unitPrice = Math.max(0, it.price);
            row.lineTotal = row.qty * row.unitPrice;
            row.orderType = normalizeType(it.orderType);
            o.items.add(row);
        }

        return o;
    }

    // =========================================================
    // RECEIPT TEXT (samakan dengan gambar)
    // =========================================================
    private String buildReceiptText(@NonNull OrderData o) {
        StringBuilder sb = new StringBuilder();

        sb.append("[C]<b>").append(safe(o.shopName)).append("</b>\n");
        if (!safe(o.shopAddress).isEmpty()) sb.append("[C]").append(o.shopAddress).append("\n");
        if (!safe(o.shopPhone).isEmpty()) sb.append("[C]").append(o.shopPhone).append("\n");

        sb.append("[C]--------------------------------\n");
        sb.append("[L]Order:   ").append(safe(o.invoice)).append("\n");
        sb.append("[L]Kasir:   ").append(safe(o.cashier)).append("\n");
        sb.append("[L]Data:    ").append(safe(o.date)).append("\n");

        // ✅ Table hanya kalau ada nilainya
        if (!safe(o.tableNumber).isEmpty()) {
            sb.append("[L]Table:   ").append(safe(o.tableNumber)).append("\n");
        }

        // ✅ Delivery hanya kalau ada nilainya
        if (!safe(o.deliveryAddress).isEmpty()) {
            sb.append("[L]Delivery:").append(" ").append(safe(o.deliveryAddress)).append("\n");
        }

        sb.append("[C]--------------------------------\n");

        for (OrderItemData it : o.items) {
            sb.append("[L]<b>").append(safe(it.name)).append("</b>")
                    .append("[R]<b>$").append(fmt(it.lineTotal)).append("</b>\n");

            sb.append("[L](").append(typeIcon(it.orderType)).append(" ").append(typeLabel(it.orderType)).append(")\n");
            sb.append("[L]").append(it.qty).append(" x $").append(fmt(it.unitPrice)).append("\n\n");
        }

        sb.append("[C]--------------------------------\n");
        sb.append("[L]Subtotal").append("[R]$").append(fmt(o.subtotal)).append("\n");
        sb.append("[L]Discount").append("[R]$").append(fmt(o.discount)).append("\n");
        sb.append("[L]VAT / Tax").append("[R]$").append(fmt(o.tax)).append("\n");

        // ✅ Delivery Fee baris seperti gambar (muncul kalau >0)
        if (o.deliveryFee > 0.00001) {
            sb.append("[L]Delivery Fee").append("[R]$").append(fmt(o.deliveryFee)).append("\n");
        }

        sb.append("[L]<b>Total</b>").append("[R]<b>$").append(fmt(o.total)).append("</b>\n");
        sb.append("[C]--------------------------------\n");
        sb.append("[C]Obrigado ita boot sosa iha ").append(safe(o.shopName)).append("!\n");
        sb.append("[C]").append(safe(o.shopName)).append("\n\n\n");

        return sb.toString();
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private String fmt(double v) {
        return String.format(Locale.US, "%.2f", v);
    }

    private String typeLabel(String t) {
        if ("DINE_IN".equals(t)) return "DINE IN";
        if ("TAKE_OUT".equals(t)) return "TAKE OUT";
        if ("DELIVERY".equals(t)) return "DELIVERY";
        return (t == null || t.trim().isEmpty()) ? "-" : t;
    }

    private String typeIcon(String t) {
        if ("DINE_IN".equals(t)) return "■■";
        if ("TAKE_OUT".equals(t)) return "■";
        if ("DELIVERY".equals(t)) return "▲";
        return "•";
    }

    private static class OrderData {
        String shopName;
        String shopAddress;
        String shopPhone;

        String invoice;
        String cashier;
        String date;

        // ✅ tambahan header seperti struk contoh
        String tableNumber;
        String deliveryAddress;
        double deliveryFee;

        double subtotal;
        double discount;
        double tax;
        double total;

        List<OrderItemData> items = new ArrayList<>();
    }

    private static class OrderItemData {
        String name;
        int qty;
        double unitPrice;
        double lineTotal;
        String orderType;
    }
}