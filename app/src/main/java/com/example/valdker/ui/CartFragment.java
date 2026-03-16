package com.example.valdker.ui;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.valdker.repositories.CheckoutConfigRepository;
import com.example.valdker.ui.checkout.BankAccountItem;
import com.example.valdker.ui.checkout.PaymentMethodItem;

import com.example.valdker.R;
import com.example.valdker.SessionManager;
import com.example.valdker.cart.CartManager;
import com.example.valdker.models.CartItem;
import com.example.valdker.repositories.OrderRepository;
import com.example.valdker.ui.checkout.NativeCheckoutDialogFragment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CartFragment extends Fragment {

    private static final String TAG = "CART_FRAGMENT";
    private static final String TAG_NATIVE_CHECKOUT = "NATIVE_CHECKOUT";

    private RecyclerView rv;
    private TextView tvEmpty;
    private TextView tvSubtotal;
    private ImageButton btnClose;
    private Button btnContinuePayment;
    private Button btnCancelOrder;

    private CartAdapter adapter;
    private final NumberFormat usd = NumberFormat.getCurrencyInstance(Locale.US);

    private CartManager cart;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private boolean cancelInProgress = false;

    private final CartManager.Listener cartListener = this::render;

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

        if (rv != null) {
            rv.setLayoutManager(new LinearLayoutManager(requireContext()));
            rv.setHasFixedSize(true);
            rv.setItemAnimator(null); // reduce jank
        }

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
            }
        });

        if (rv != null) rv.setAdapter(adapter);

        if (btnClose != null) btnClose.setOnClickListener(v -> closeOverlaySafely());

        if (btnContinuePayment != null) {
            btnContinuePayment.setOnClickListener(v -> {
                if (cancelInProgress) return;
                openNativeCheckout();
            });
        }

        if (btnCancelOrder != null) {
            btnCancelOrder.setOnClickListener(v -> {
                if (cancelInProgress) return;
                cancelOrderSafely();
            });
        }

        render();
    }

    @Override
    public void onStart() {
        super.onStart();
        try {
            cart.addListener(cartListener);
        } catch (Exception ignored) {}
        render();
    }

    @Override
    public void onStop() {
        super.onStop();
        try {
            cart.removeListener(cartListener);
        } catch (Exception ignored) {}
    }

    // =========================================================
    // Cancel Order (reduce lag)
    // =========================================================

    private void cancelOrderSafely() {
        if (!isAdded()) return;

        cancelInProgress = true;

        if (btnCancelOrder != null) btnCancelOrder.setEnabled(false);
        if (btnContinuePayment != null) btnContinuePayment.setEnabled(false);

        Toast.makeText(requireContext(), "Order cancelled", Toast.LENGTH_SHORT).show();

        cart.clear();

        mainHandler.post(this::closeOverlaySafely);
    }

    // =========================================================
    // Checkout
    // =========================================================

    private void openNativeCheckout() {
        if (!isAdded()) return;

        List<CartItem> cartItems = cart.getItems();
        if (cartItems == null || cartItems.isEmpty()) {
            Toast.makeText(requireContext(), "Cart is empty", Toast.LENGTH_SHORT).show();
            return;
        }

        final Context appCtx = requireContext().getApplicationContext();
        final SessionManager sm = new SessionManager(appCtx);
        final String token = sm.getToken();

        if (token == null || token.trim().isEmpty()) {
            Toast.makeText(requireContext(), "Token is missing. Please login again.", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean needTable = false;
        boolean needDelivery = false;

        for (CartItem it : cartItems) {
            String t = normalizeType(it.orderType);
            if (CartManager.TYPE_DINE_IN.equals(t)) needTable = true;
            if (CartManager.TYPE_DELIVERY.equals(t)) needDelivery = true;
        }

        final double subtotal = cart.getTotalAmount();
        final boolean finalNeedTable = needTable;
        final boolean finalNeedDelivery = needDelivery;

        CheckoutConfigRepository repo = new CheckoutConfigRepository(appCtx);

        repo.fetchPaymentMethods(token, new CheckoutConfigRepository.PaymentMethodsCallback() {
            @Override
            public void onSuccess(@NonNull List<PaymentMethodItem> paymentItems) {

                repo.fetchBankAccounts(token, new CheckoutConfigRepository.BankAccountsCallback() {
                    @Override
                    public void onSuccess(@NonNull List<BankAccountItem> bankItems) {
                        if (!isAdded()) return;

                        NativeCheckoutDialogFragment dialog =
                                NativeCheckoutDialogFragment.newInstance(
                                        subtotal,
                                        finalNeedTable,
                                        finalNeedDelivery
                                );

                        List<NativeCheckoutDialogFragment.PaymentMethodOption> methodOptions = new ArrayList<>();
                        for (PaymentMethodItem item : paymentItems) {
                            methodOptions.add(
                                    new NativeCheckoutDialogFragment.PaymentMethodOption(
                                            item.id,
                                            item.code != null ? item.code : "",
                                            item.name != null ? item.name : "",
                                            item.requires_bank_account
                                    )
                            );
                        }

                        List<NativeCheckoutDialogFragment.BankAccountOption> bankOptions = new ArrayList<>();
                        for (BankAccountItem item : bankItems) {
                            String label = (item.bank_name != null ? item.bank_name : "") +
                                    " - " +
                                    (item.name != null ? item.name : "");
                            bankOptions.add(
                                    new NativeCheckoutDialogFragment.BankAccountOption(
                                            item.id,
                                            label
                                    )
                            );
                        }

                        dialog.setPaymentOptions(methodOptions);
                        dialog.setBankOptions(bankOptions);
                        dialog.setBankListener(new NativeCheckoutDialogFragment.BankListener() {
                            @Override
                            public void onConfirmBank(@NonNull NativeCheckoutDialogFragment.BankCheckoutResult result) {
                                CartFragment.this.submitCheckout(result);
                            }
                        });

                        dialog.show(requireActivity().getSupportFragmentManager(), TAG_NATIVE_CHECKOUT);
                    }

                    @Override
                    public void onError(int statusCode, @NonNull String message) {
                        if (!isAdded()) return;
                        Toast.makeText(requireContext(),
                                "Failed to load bank accounts: " + message,
                                Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(),
                        "Failed to load payment methods: " + message,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void submitCheckout(@NonNull NativeCheckoutDialogFragment.BankCheckoutResult result) {
        if (!isAdded()) return;

        if (btnContinuePayment != null) btnContinuePayment.setEnabled(false);

        final Context appCtx = requireContext().getApplicationContext();
        final SessionManager sm = new SessionManager(appCtx);
        final String token = sm.getToken();

        if (token == null || token.trim().isEmpty()) {
            Toast.makeText(requireContext(), "Token is missing. Please login again.", Toast.LENGTH_SHORT).show();
            if (btnContinuePayment != null) btnContinuePayment.setEnabled(true);
            return;
        }

        CartManager cartManager = CartManager.getInstance(requireContext());
        int activeShopId = sm.getShopId();

        if (!cartManager.belongsToShop(activeShopId)) {
            Toast.makeText(requireContext(),
                    "Cart tidak cocok dengan toko aktif. Cart akan dibersihkan, silakan pilih produk lagi.",
                    Toast.LENGTH_LONG).show();
            cartManager.clear();
            if (btnContinuePayment != null) btnContinuePayment.setEnabled(true);
            return;
        }

        final List<CartItem> snapshot = new ArrayList<>(cart.getItems());
        if (snapshot.isEmpty()) {
            Toast.makeText(requireContext(), "Cart is empty", Toast.LENGTH_SHORT).show();
            if (btnContinuePayment != null) btnContinuePayment.setEnabled(true);
            return;
        }

        boolean hasDineIn = false;
        boolean hasDelivery = false;

        for (CartItem it : snapshot) {
            String t = normalizeType(it.orderType);
            if (CartManager.TYPE_DINE_IN.equals(t)) hasDineIn = true;
            if (CartManager.TYPE_DELIVERY.equals(t)) hasDelivery = true;
        }

        final String tableFinal = result.tableNumber != null ? result.tableNumber.trim() : "";
        final String addrFinal = result.deliveryAddress != null ? result.deliveryAddress.trim() : "";

        if (hasDineIn && tableFinal.isEmpty()) {
            Toast.makeText(requireContext(), "Table number is required for dine-in.", Toast.LENGTH_LONG).show();
            if (btnContinuePayment != null) btnContinuePayment.setEnabled(true);
            return;
        }

        if (hasDelivery && addrFinal.isEmpty()) {
            Toast.makeText(requireContext(), "Delivery address is required for delivery.", Toast.LENGTH_LONG).show();
            if (btnContinuePayment != null) btnContinuePayment.setEnabled(true);
            return;
        }

        final double subtotal = calcSubtotal(snapshot);
        final double feeSafe = Math.max(0, result.deliveryFee);
        final double total = subtotal + (hasDelivery ? feeSafe : 0);

        final boolean hasDeliveryFinal = hasDelivery;
        final double deliveryFeeFinal = hasDeliveryFinal ? feeSafe : 0;
        final double subtotalFinal = subtotal;
        final double totalFinal = total;

        final String paymentCodeFinal = result.paymentMethodCode != null
                ? result.paymentMethodCode.trim().toUpperCase(Locale.US)
                : "CASH";

        final JSONObject payload = new JSONObject();
        final JSONArray itemsArr = new JSONArray();
        final JSONArray paymentsArr = new JSONArray();

        try {
            payload.put("customer", JSONObject.NULL);
            payload.put("payment_method", paymentCodeFinal);
            payload.put("subtotal", String.format(Locale.US, "%.2f", subtotalFinal));
            payload.put("discount", "0.00");
            payload.put("tax", "0.00");
            payload.put("total", String.format(Locale.US, "%.2f", totalFinal));
            payload.put("notes", "Checkout from Android");
            payload.put("is_paid", true);

            String overallType = "TAKE_OUT";
            if (hasDeliveryFinal) {
                overallType = "DELIVERY";
            } else if (hasDineIn) {
                overallType = "DINE_IN";
            }
            payload.put("default_order_type", overallType);

            payload.put("table_number", tableFinal);
            payload.put("delivery_address", addrFinal);
            payload.put("delivery_fee", String.format(Locale.US, "%.2f", deliveryFeeFinal));

            for (CartItem it : snapshot) {
                JSONObject one = new JSONObject();
                one.put("product", it.productId);
                one.put("quantity", it.qty);
                one.put("price", String.format(Locale.US, "%.2f", it.price));

                String ot = normalizeType(it.orderType);
                if (!ot.isEmpty()) {
                    one.put("order_type", ot);
                } else {
                    one.put("order_type", "TAKE_OUT");
                }

                itemsArr.put(one);
            }

            JSONObject paymentObj = new JSONObject();
            if (result.paymentMethodId != null) {
                paymentObj.put("payment_method_id", result.paymentMethodId);
            }
            if (result.bankAccountId != null) {
                paymentObj.put("bank_account_id", result.bankAccountId);
            } else {
                paymentObj.put("bank_account_id", JSONObject.NULL);
            }
            paymentObj.put("amount", String.format(Locale.US, "%.2f", totalFinal));
            paymentObj.put("reference_number", result.referenceNumber != null ? result.referenceNumber : "");
            paymentObj.put("note", result.paymentNote != null ? result.paymentNote : "");

            paymentsArr.put(paymentObj);

            payload.put("items", itemsArr);
            payload.put("payments", paymentsArr);

            Log.d(TAG, "Checkout payload = " + payload.toString());

        } catch (Exception e) {
            Toast.makeText(requireContext(), "Failed to build payload: " + e.getMessage(), Toast.LENGTH_LONG).show();
            if (btnContinuePayment != null) btnContinuePayment.setEnabled(true);
            return;
        }

        OrderRepository repo = new OrderRepository(appCtx);
        repo.createOrder(token, payload, new OrderRepository.CreateCallback() {
            @Override
            public void onSuccess(@NonNull JSONObject response) {
                mainHandler.post(() ->
                        Toast.makeText(requireContext(), "Checkout success", Toast.LENGTH_SHORT).show()
                );

                String invoiceFromApi = response.optString("invoice_number", "");
                String invoiceFinal = (invoiceFromApi != null && !invoiceFromApi.trim().isEmpty())
                        ? invoiceFromApi.trim()
                        : "INV-" + System.currentTimeMillis();

                tryAutoPrintReceipt(
                        appCtx,
                        token,
                        snapshot,
                        paymentCodeFinal,
                        subtotalFinal,
                        deliveryFeeFinal,
                        totalFinal,
                        tableFinal,
                        addrFinal,
                        invoiceFinal
                );

                cart.clear();

                mainHandler.post(() -> {
                    render();
                    closeOverlaySafely();
                    if (btnContinuePayment != null) btnContinuePayment.setEnabled(true);
                });
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), "Checkout failed: " + message, Toast.LENGTH_LONG).show();
                    if (btnContinuePayment != null) btnContinuePayment.setEnabled(true);
                });
            }
        });
    }

    // =========================================================
    // Auto Print Receipt (works even after fragment closed)
    // =========================================================

    private void tryAutoPrintReceipt(@NonNull Context appCtx,
                                     @NonNull String token,
                                     @NonNull List<CartItem> items,
                                     @NonNull String paymentMethod,
                                     double subtotal,
                                     double deliveryFee,
                                     double total,
                                     @NonNull String tableNumber,
                                     @NonNull String deliveryAddress,
                                     @NonNull String invoiceNumber) {

        boolean auto = com.example.valdker.print.PrinterPrefs.isAutoPrintEnabled(appCtx);
        if (!auto) {
            Log.i(TAG, "Auto print disabled -> skip printing");
            return;
        }

        if (!com.example.valdker.print.PrinterService.hasBtPermission(appCtx)) {
            Log.w(TAG, "Bluetooth permission not granted -> skip auto print");
            mainHandler.post(() ->
                    Toast.makeText(appCtx, "Bluetooth permission belum di-allow. Print dibatalkan.", Toast.LENGTH_LONG).show()
            );
            return;
        }

        String mac = com.example.valdker.print.PrinterPrefs.getMac(appCtx);
        if (mac == null || mac.trim().isEmpty()) {
            Log.w(TAG, "No printer selected -> skip auto print");
            mainHandler.post(() ->
                    Toast.makeText(appCtx, "Printer belum dipilih. Pilih dulu di Settings > Printer.", Toast.LENGTH_LONG).show()
            );
            return;
        }

        // If shop fetch fails, still print with fallback quickly.
        String fallbackReceipt = buildReceiptFull(
                appCtx,
                "VALDKER POS",
                "",
                "",
                items,
                paymentMethod,
                subtotal,
                deliveryFee,
                total,
                tableNumber,
                deliveryAddress,
                invoiceNumber
        );

        // Try fetch shop data, then print (or fallback)
        com.example.valdker.repositories.ShopRepository.fetchFirstShop(
                appCtx,
                token,
                new com.example.valdker.repositories.ShopRepository.Callback() {
                    @Override
                    public void onSuccess(@NonNull com.example.valdker.models.Shop shop) {
                        String shopName = (shop.name != null && !shop.name.trim().isEmpty())
                                ? shop.name.trim()
                                : "VALDKER POS";
                        String shopAddress = (shop.address != null) ? shop.address.trim() : "";
                        String shopPhone = (shop.phone != null) ? shop.phone.trim() : "";

                        String receipt = buildReceiptFull(
                                appCtx,
                                shopName,
                                shopAddress,
                                shopPhone,
                                items,
                                paymentMethod,
                                subtotal,
                                deliveryFee,
                                total,
                                tableNumber,
                                deliveryAddress,
                                invoiceNumber
                        );

                        doPrintBestEffort(appCtx, receipt);
                    }

                    @Override
                    public void onEmpty() {
                        doPrintBestEffort(appCtx, fallbackReceipt);
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        doPrintBestEffort(appCtx, fallbackReceipt);
                    }
                }
        );
    }

    private void doPrintBestEffort(@NonNull Context appCtx, @NonNull String receipt) {
        new Thread(() -> {
            try {
                com.example.valdker.print.PrinterService.printText(appCtx, receipt);
                Log.i(TAG, "Auto print success");
            } catch (Exception e) {
                Log.e(TAG, "Auto print failed: " + e.getMessage(), e);
                mainHandler.post(() ->
                        Toast.makeText(appCtx, "Print failed: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        }).start();
    }

    private String buildReceiptFull(@NonNull Context appCtx,
                                    @NonNull String shopName,
                                    @NonNull String shopAddress,
                                    @NonNull String shopPhone,
                                    @NonNull List<CartItem> items,
                                    @NonNull String paymentMethod,
                                    double subtotal,
                                    double deliveryFee,
                                    double total,
                                    @NonNull String tableNumber,
                                    @NonNull String deliveryAddress,
                                    @NonNull String invoiceNumber) {

        // Cashier (best-effort)
        String cashier = "";
        try {
            SessionManager sm = new SessionManager(appCtx);
            String u = sm.getUsername();
            cashier = (u != null) ? u.trim() : "";
        } catch (Exception ignored) {}

        java.text.SimpleDateFormat dfDate = new java.text.SimpleDateFormat("dd/MM/yy", Locale.US);
        java.text.SimpleDateFormat dfTime = new java.text.SimpleDateFormat("HH:mm", Locale.US);
        String date = dfDate.format(new java.util.Date());
        String time = dfTime.format(new java.util.Date());

        StringBuilder sb = new StringBuilder();

        sb.append("[C]<b>").append(shopName).append("</b>\n");
        if (!shopAddress.trim().isEmpty()) sb.append("[C]").append(shopAddress.trim()).append("\n");
        if (!shopPhone.trim().isEmpty()) sb.append("[C]").append(shopPhone.trim()).append("\n");
        sb.append("[C]--------------------------------\n");

        sb.append("[L]Order:[R]").append(invoiceNumber).append("\n");
        if (!cashier.isEmpty()) sb.append("[L]Kasir:[R]").append(cashier).append("\n");
        sb.append("[L]Data:[R]").append(date).append("\n");
        sb.append("[L]Ora:[R]").append(time).append("\n");

        boolean showTable = (tableNumber != null && !tableNumber.trim().isEmpty());
        boolean showDelivery = (deliveryAddress != null && !deliveryAddress.trim().isEmpty());

        if (showTable) sb.append("[L]Table:[R]").append(tableNumber.trim()).append("\n");
        if (showDelivery) sb.append("[L]Delivery:[R]").append(deliveryAddress.trim()).append("\n");

        sb.append("[C]--------------------------------\n");

        for (CartItem it : items) {
            String name = (it.name != null && !it.name.trim().isEmpty()) ? it.name.trim() : "Item";
            int qty = Math.max(0, it.qty);
            double price = Math.max(0, it.price);
            double line = qty * price;

            String type = normalizeType(it.orderType);
            String typeLabel = "";
            if (CartManager.TYPE_DINE_IN.equals(type)) typeLabel = "(■ DINE IN)";
            else if (CartManager.TYPE_TAKE_OUT.equals(type)) typeLabel = "(■ TAKE OUT)";
            else if (CartManager.TYPE_DELIVERY.equals(type)) typeLabel = "(▲ DELIVERY)";

            sb.append("[L]<b>").append(name).append("</b>[R]<b>")
                    .append(String.format(Locale.US, "$%.2f", line))
                    .append("</b>\n");

            if (!typeLabel.isEmpty()) sb.append("[L]").append(typeLabel).append("\n");

            sb.append("[L]").append(qty)
                    .append(" x ")
                    .append(String.format(Locale.US, "$%.2f", price))
                    .append("\n\n");
        }

        sb.append("[C]--------------------------------\n");

        sb.append("[L]Subtotal[R]").append(String.format(Locale.US, "$%.2f", subtotal)).append("\n");
        sb.append("[L]Discount[R]").append(String.format(Locale.US, "$%.2f", 0.00)).append("\n");
        sb.append("[L]VAT / Tax[R]").append(String.format(Locale.US, "$%.2f", 0.00)).append("\n");
        if (deliveryFee > 0) sb.append("[L]Delivery Fee[R]").append(String.format(Locale.US, "$%.2f", deliveryFee)).append("\n");

        sb.append("[C]--------------------------------\n");
        sb.append("[L]<b>Total</b>[R]<b>").append(String.format(Locale.US, "$%.2f", total)).append("</b>\n");
        sb.append("[C]--------------------------------\n");

        sb.append("[C]Obrigado ba order ona iha!\n");
        sb.append("[C]").append(shopName).append("\n\n\n");

        return sb.toString();
    }

    private double calcSubtotal(@NonNull List<CartItem> items) {
        double total = 0.0;
        for (CartItem it : items) {
            total += (Math.max(0, it.price) * Math.max(0, it.qty));
        }
        return total;
    }

    // =========================================================
    // Render
    // =========================================================

    private void render() {
        if (!isAdded() || getView() == null) return;

        List<CartItem> items = cart.getItems();
        if (adapter != null) adapter.submit(items != null ? items : new ArrayList<>());

        boolean empty = (items == null || items.isEmpty());

        if (rv != null) rv.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (tvEmpty != null) tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);

        if (tvSubtotal != null) tvSubtotal.setText(usd.format(cart.getTotalAmount()));

        if (btnContinuePayment != null) btnContinuePayment.setEnabled(!empty && !cancelInProgress);
        if (btnCancelOrder != null) btnCancelOrder.setEnabled(!empty && !cancelInProgress);

        if (empty) cancelInProgress = false;
    }

    @NonNull
    private String normalizeType(@Nullable String t) {
        if (t == null) return "";
        return t.trim().toUpperCase(Locale.US);
    }

    private void closeOverlaySafely() {
        if (!isAdded()) return;

        try {
            requireActivity().getSupportFragmentManager().popBackStack();
        } catch (Exception ignored) {}

        View overlay = requireActivity().findViewById(R.id.overlayContainer);
        if (overlay != null) overlay.setVisibility(View.GONE);
    }
}