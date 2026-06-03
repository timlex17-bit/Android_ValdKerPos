package com.valdker.pos.ui;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import com.valdker.pos.utils.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.valdker.pos.R;
import com.valdker.pos.SessionManager;
import com.valdker.pos.cart.CartManager;
import com.valdker.pos.models.CartItem;
import com.valdker.pos.models.Customer;
import com.valdker.pos.repositories.CheckoutConfigRepository;
import com.valdker.pos.repositories.CustomerRepository;
import com.valdker.pos.repositories.MasterDataRepository;
import com.valdker.pos.repositories.OfflineOrderRepository;
import com.valdker.pos.repositories.OrderRepository;
import com.valdker.pos.ui.checkout.BankAccountItem;
import com.valdker.pos.ui.checkout.NativeCheckoutDialogFragment;
import com.valdker.pos.ui.checkout.PaymentMethodItem;
import com.valdker.pos.utils.ErrorHandler;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class CartFragment extends Fragment {

    public interface DraftLifecycleHost {
        void onCartOrderFinished();
    }

    private interface ReceiptPrintCallback {
        void onComplete(@NonNull com.valdker.pos.print.BluetoothPrinterManager.PrintResult result);
    }

    private static final String TAG = "CART_FRAGMENT";
    private static final String TAG_NATIVE_CHECKOUT = "NATIVE_CHECKOUT";

    private static final String ARG_BUSINESS_TYPE = "business_type";
    private static final String ARG_ENABLE_DINE_IN = "enable_dine_in";
    private static final String ARG_ENABLE_TAKEAWAY = "enable_takeaway";
    private static final String ARG_ENABLE_DELIVERY = "enable_delivery";
    private static final String ARG_ENABLE_TABLE_NUMBER = "enable_table_number";
    private static final String ARG_ENABLE_SPLIT_PAYMENT = "enable_split_payment";

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
    @Nullable
    private WeakReference<DraftLifecycleHost> draftLifecycleHostRef;

    private boolean cancelInProgress = false;

    private String businessType = "";
    private boolean enableDineIn = false;
    private boolean enableTakeaway = false;
    private boolean enableDelivery = false;
    private boolean enableTableNumber = false;
    private boolean enableSplitPayment = false;

    private final CartManager.Listener cartListener = this::render;

    public CartFragment() {
        super(R.layout.fragment_cart);
    }

    public static CartFragment newInstance(
            String businessType,
            boolean enableDineIn,
            boolean enableTakeaway,
            boolean enableDelivery,
            boolean enableTableNumber,
            boolean enableSplitPayment
    ) {
        CartFragment f = new CartFragment();
        Bundle b = new Bundle();
        b.putString(ARG_BUSINESS_TYPE, businessType);
        b.putBoolean(ARG_ENABLE_DINE_IN, enableDineIn);
        b.putBoolean(ARG_ENABLE_TAKEAWAY, enableTakeaway);
        b.putBoolean(ARG_ENABLE_DELIVERY, enableDelivery);
        b.putBoolean(ARG_ENABLE_TABLE_NUMBER, enableTableNumber);
        b.putBoolean(ARG_ENABLE_SPLIT_PAYMENT, enableSplitPayment);
        f.setArguments(b);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        readArgs();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof DraftLifecycleHost) {
            draftLifecycleHostRef = new WeakReference<>((DraftLifecycleHost) context);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        cart = CartManager.getInstance(requireContext());

        fallbackConfigFromSession();
        syncPendingOrdersIfOnline();

        rv = view.findViewById(R.id.rvCart);
        tvEmpty = view.findViewById(R.id.tvCartEmpty);
        tvSubtotal = view.findViewById(R.id.tvSubtotal);
        btnClose = view.findViewById(R.id.btnCloseCart);
        btnContinuePayment = view.findViewById(R.id.btnContinuePayment);
        btnCancelOrder = view.findViewById(R.id.btnCancelOrder);

        if (rv != null) {
            rv.setLayoutManager(new LinearLayoutManager(requireContext()));
            rv.setHasFixedSize(true);
            rv.setItemAnimator(null);
        }

        adapter = new CartAdapter(
                new CartAdapter.Listener() {
                    @Override
                    public void onIncrease(@NonNull CartItem item) {
                        cart.setQty(item.productId, item.itemType, item.qty + 1);
                    }

                    @Override
                    public void onDecrease(@NonNull CartItem item) {
                        cart.setQty(item.productId, item.itemType, item.qty - 1);
                    }

                    @Override
                    public void onRemove(@NonNull CartItem item) {
                        cart.remove(item.productId, item.itemType);
                    }

                    @Override
                    public void onTypeChanged(@NonNull CartItem item, @NonNull String orderType) {
                        if (!isOrderTypeAllowed(orderType)) {
                            Toast.makeText(requireContext(), "Order type not allowed for this shop.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        cart.setOrderType(item.productId, item.itemType, orderType);
                    }
                },
                businessType,
                enableDineIn,
                enableTakeaway,
                enableDelivery
        );

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

        normalizeCartItemsForBusinessType();
        render();
    }

    @Override
    public void onStart() {
        super.onStart();
        try {
            cart.addListener(cartListener);
        } catch (Exception ignored) {
        }
        render();
    }

    @Override
    public void onStop() {
        super.onStop();
        try {
            cart.removeListener(cartListener);
        } catch (Exception ignored) {
        }
    }

    private void readArgs() {
        Bundle args = getArguments();
        if (args == null) return;

        businessType = safeLower(args.getString(ARG_BUSINESS_TYPE, "retail"));
        enableDineIn = args.getBoolean(ARG_ENABLE_DINE_IN, false);
        enableTakeaway = args.getBoolean(ARG_ENABLE_TAKEAWAY, false);
        enableDelivery = args.getBoolean(ARG_ENABLE_DELIVERY, false);
        enableTableNumber = args.getBoolean(ARG_ENABLE_TABLE_NUMBER, false);
        enableSplitPayment = args.getBoolean(ARG_ENABLE_SPLIT_PAYMENT, false);
    }

    private void fallbackConfigFromSession() {
        Context appCtx = requireContext().getApplicationContext();
        SessionManager sm = new SessionManager(appCtx);

        String sessionBusinessType = safeLower(sm.getBusinessType());

        if (businessType == null || businessType.trim().isEmpty()) {
            businessType = sessionBusinessType;
        }

        if (!"restaurant".equals(businessType)
                && !"retail".equals(businessType)
                && !"workshop".equals(businessType)) {
            businessType = sessionBusinessType;
        }

        if ("restaurant".equals(businessType)) {
            enableDineIn = sm.enableDineIn();
            enableTakeaway = sm.enableTakeaway();
            enableDelivery = sm.enableDelivery();
            enableTableNumber = sm.enableTableNumber();
            enableSplitPayment = sm.enableSplitPayment();
        } else {
            enableDineIn = false;
            enableTakeaway = false;
            enableDelivery = false;
            enableTableNumber = false;
            enableSplitPayment = sm.enableSplitPayment();
        }

        Log.d(TAG, "businessType=" + businessType
                + ", enableDineIn=" + enableDineIn
                + ", enableTakeaway=" + enableTakeaway
                + ", enableDelivery=" + enableDelivery);
    }

    private void normalizeCartItemsForBusinessType() {
        List<CartItem> items = cart.getItems();
        if (items == null || items.isEmpty()) return;

        for (CartItem item : items) {
            String normalized = normalizeType(item.orderType);

            if (!isOrderTypeAllowed(normalized)) {
                cart.setOrderType(item.productId, item.itemType, getDefaultOrderType());
            } else if (normalized.isEmpty()) {
                cart.setOrderType(item.productId, item.itemType, getDefaultOrderType());
            }
        }
    }

    @NonNull
    private String getDefaultOrderType() {
        if ("restaurant".equals(businessType)) {
            if (enableTakeaway) return CartManager.TYPE_TAKE_OUT;
            if (enableDineIn) return CartManager.TYPE_DINE_IN;
            if (enableDelivery) return CartManager.TYPE_DELIVERY;
            return CartManager.TYPE_TAKE_OUT;
        }
        return CartManager.TYPE_GENERAL;
    }

    private boolean isOrderTypeAllowed(@Nullable String type) {
        String t = normalizeType(type);

        if ("restaurant".equals(businessType)) {
            if (CartManager.TYPE_DINE_IN.equals(t)) return enableDineIn;
            if (CartManager.TYPE_TAKE_OUT.equals(t)) return enableTakeaway;
            if (CartManager.TYPE_DELIVERY.equals(t)) return enableDelivery;
            return false;
        }

        return CartManager.TYPE_GENERAL.equals(t) || t.isEmpty();
    }

    private void cancelOrderSafely() {
        if (!isAdded()) return;

        cancelInProgress = true;

        if (btnCancelOrder != null) btnCancelOrder.setEnabled(false);
        if (btnContinuePayment != null) btnContinuePayment.setEnabled(false);

        Toast.makeText(requireContext(), "Order cancelled", Toast.LENGTH_SHORT).show();

        finishActiveDraftAndClearCart();

        mainHandler.post(this::closeOverlaySafely);
    }

    private void finishActiveDraftAndClearCart() {
        Log.i(TAG, "checkout cleanup started pos=" + businessType);

        DraftLifecycleHost host = draftLifecycleHostRef != null ? draftLifecycleHostRef.get() : null;
        if (host == null && getActivity() instanceof DraftLifecycleHost) {
            host = (DraftLifecycleHost) getActivity();
        }

        if (host != null) {
            host.onCartOrderFinished();
            return;
        }

        Log.e(TAG, "cleanup failed with error: draft lifecycle host unavailable");
        if (cart != null) {
            cart.clear();
            Log.i(TAG, "cart cleanup success pos=" + businessType + " draft_host_unavailable=true");
        }
    }

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
            if (CartManager.TYPE_DINE_IN.equals(t) && enableTableNumber) needTable = true;
            if (CartManager.TYPE_DELIVERY.equals(t) && enableDelivery) needDelivery = true;
        }

        final double subtotal = cart.getTotalAmount();
        final boolean finalNeedTable = needTable;
        final boolean finalNeedDelivery = needDelivery;

        MasterDataRepository masterDataRepository = new MasterDataRepository(appCtx);
        final NativeCheckoutDialogFragment[] dialogRef = {null};
        final boolean[] dialogShown = {false};
        final boolean[] offlineNoticeShown = {false};
        final boolean[] noLocalDataNoticeShown = {false};

        masterDataRepository.loadCheckoutDataRoomFirst(token, new MasterDataRepository.CheckoutDataCallback() {
            @Override
            public void onLocalCheckoutData(@NonNull List<Customer> customers,
                                            @NonNull List<PaymentMethodItem> paymentItems,
                                            @NonNull List<BankAccountItem> bankItems) {
                if (!isAdded()) return;
                if (!paymentItems.isEmpty()) {
                    showOrUpdateCheckoutDialog(dialogRef, dialogShown, subtotal, finalNeedTable, finalNeedDelivery,
                            customers, paymentItems, bankItems);
                }
            }

            @Override
            public void onRemoteCheckoutData(@NonNull List<Customer> customers,
                                             @NonNull List<PaymentMethodItem> paymentItems,
                                             @NonNull List<BankAccountItem> bankItems) {
                if (!isAdded()) return;
                showOrUpdateCheckoutDialog(dialogRef, dialogShown, subtotal, finalNeedTable, finalNeedDelivery,
                        customers, paymentItems, bankItems);
            }

            @Override
            public void onNoInternet(@NonNull List<Customer> customers,
                                     @NonNull List<PaymentMethodItem> paymentItems,
                                     @NonNull List<BankAccountItem> bankItems) {
                if (!isAdded()) return;
                if (paymentItems.isEmpty()) {
                    if (!noLocalDataNoticeShown[0]) {
                        noLocalDataNoticeShown[0] = true;
                        Toast.makeText(requireContext(), MasterDataRepository.MESSAGE_NO_LOCAL_POS_DATA, Toast.LENGTH_SHORT).show();
                    }
                    return;
                }
                if (!offlineNoticeShown[0]) {
                    offlineNoticeShown[0] = true;
                    Toast.makeText(requireContext(), MasterDataRepository.MESSAGE_NO_INTERNET_SHOWING_LOCAL, Toast.LENGTH_SHORT).show();
                }
                if (!dialogShown[0]) {
                    showOrUpdateCheckoutDialog(dialogRef, dialogShown, subtotal, finalNeedTable, finalNeedDelivery,
                            customers, paymentItems, bankItems);
                }
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                if (!isAdded()) return;
                if (!dialogShown[0]) {
                    ErrorHandler.handleApiError(requireContext(), "Failed to load payment methods: " + message);
                }
            }
        });
    }

    private void showOrUpdateCheckoutDialog(@NonNull NativeCheckoutDialogFragment[] dialogRef,
                                            @NonNull boolean[] dialogShown,
                                            double subtotal,
                                            boolean needTable,
                                            boolean needDelivery,
                                            @NonNull List<Customer> customers,
                                            @NonNull List<PaymentMethodItem> paymentItems,
                                            @NonNull List<BankAccountItem> bankItems) {
        NativeCheckoutDialogFragment dialog = dialogRef[0];
        if (dialog == null) {
            dialog = NativeCheckoutDialogFragment.newInstance(subtotal, needTable, needDelivery);
            dialog.setBankListener(result -> CartFragment.this.submitCheckout(result));
            dialogRef[0] = dialog;
        }

        dialog.setCustomerOptions(toCustomerOptions(customers));
        dialog.setPaymentOptions(toPaymentOptions(paymentItems));
        dialog.setBankOptions(toBankOptions(bankItems));

        if (!dialogShown[0] && isAdded()) {
            dialogShown[0] = true;
            dialog.show(requireActivity().getSupportFragmentManager(), TAG_NATIVE_CHECKOUT);
        }
    }

    @NonNull
    private List<NativeCheckoutDialogFragment.CustomerOption> toCustomerOptions(@NonNull List<Customer> customers) {
        List<NativeCheckoutDialogFragment.CustomerOption> options = new ArrayList<>();
        options.add(new NativeCheckoutDialogFragment.CustomerOption(0, "Walk-in Customer", 0L));
        for (Customer c : customers) {
            if (c == null) continue;
            options.add(new NativeCheckoutDialogFragment.CustomerOption(
                    c.id,
                    c.name != null && !c.name.trim().isEmpty() ? c.name : "Customer #" + c.id,
                    c.points
            ));
        }
        return options;
    }

    @NonNull
    private List<NativeCheckoutDialogFragment.PaymentMethodOption> toPaymentOptions(@NonNull List<PaymentMethodItem> paymentItems) {
        List<NativeCheckoutDialogFragment.PaymentMethodOption> options = new ArrayList<>();
        for (PaymentMethodItem item : paymentItems) {
            if (item == null || !item.is_active) continue;
            options.add(new NativeCheckoutDialogFragment.PaymentMethodOption(
                    item.id,
                    item.code != null ? item.code : "",
                    item.name != null ? item.name : "",
                    item.requires_bank_account
            ));
        }
        return options;
    }

    @NonNull
    private List<NativeCheckoutDialogFragment.BankAccountOption> toBankOptions(@NonNull List<BankAccountItem> bankItems) {
        List<NativeCheckoutDialogFragment.BankAccountOption> options = new ArrayList<>();
        for (BankAccountItem item : bankItems) {
            if (item == null || !item.is_active) continue;
            String label = (item.bank_name != null ? item.bank_name : "") + " - " + (item.name != null ? item.name : "");
            options.add(new NativeCheckoutDialogFragment.BankAccountOption(item.id, label));
        }
        return options;
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
                    "The cart does not match any active stores. The cart will be cleared. Please select the product again.",
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

        if (hasDineIn && enableTableNumber && tableFinal.isEmpty()) {
            Toast.makeText(requireContext(), "Table number is required for dine-in.", Toast.LENGTH_LONG).show();
            if (btnContinuePayment != null) btnContinuePayment.setEnabled(true);
            return;
        }

        if (hasDelivery && enableDelivery && addrFinal.isEmpty()) {
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
            if (result.customerId != null && result.customerId > 0) {
                payload.put("customer", result.customerId);
            } else {
                payload.put("customer", JSONObject.NULL);
            }
            payload.put("device_time", currentDeviceTimeIso());
            payload.put("payment_method", paymentCodeFinal);
            payload.put("subtotal", String.format(Locale.US, "%.2f", subtotalFinal));
            payload.put("discount", "0.00");
            payload.put("tax", "0.00");
            payload.put("total", String.format(Locale.US, "%.2f", totalFinal));
            payload.put("notes", "Checkout from Android");
            payload.put("is_paid", true);

            String overallType;
            if (isRestaurantBusiness()) {
                overallType = getDefaultOrderType();
                if (hasDeliveryFinal) {
                    overallType = CartManager.TYPE_DELIVERY;
                } else if (hasDineIn) {
                    overallType = CartManager.TYPE_DINE_IN;
                }
            } else {
                overallType = CartManager.TYPE_GENERAL;
            }

            payload.put("default_order_type", overallType);
            payload.put("table_number", tableFinal);
            payload.put("delivery_address", addrFinal);
            payload.put("delivery_fee", String.format(Locale.US, "%.2f", deliveryFeeFinal));

            Log.d(TAG, "snapshot size = " + snapshot.size());

            for (int i = 0; i < snapshot.size(); i++) {
                CartItem it = snapshot.get(i);

                if (it == null) {
                    Log.w(TAG, "Cart item at index " + i + " is null, skipped.");
                    continue;
                }

                Log.d(TAG, "Cart item[" + i + "] productId=" + it.productId
                        + ", qty=" + it.qty
                        + ", price=" + it.price
                        + ", orderType=" + it.orderType
                        + ", name=" + it.name);

                JSONObject one = new JSONObject();

                one.put("product", it.productId);
                one.put("quantity", Math.max(1, it.qty));
                one.put("price", String.format(Locale.US, "%.2f", Math.max(0, it.price)));

                String ot;
                if ("restaurant".equalsIgnoreCase(businessType)) {
                    ot = normalizeType(it.orderType);
                    if (!isOrderTypeAllowed(ot) || ot.isEmpty()) {
                        ot = getDefaultOrderType();
                    }
                } else {
                    ot = CartManager.TYPE_GENERAL;
                }

                one.put("order_type", ot);

                itemsArr.put(one);

                Log.d(TAG, "itemsArr length after put = " + itemsArr.length());
            }

            if (itemsArr.length() == 0) {
                Toast.makeText(requireContext(), "Checkout failed: items empty.", Toast.LENGTH_LONG).show();
                Log.e(TAG, "Checkout aborted because itemsArr is empty. snapshot size=" + snapshot.size());
                if (btnContinuePayment != null) btnContinuePayment.setEnabled(true);
                return;
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

            Log.d(TAG, "FINAL itemsArr = " + itemsArr.toString());
            payload.put("items", itemsArr);
            payload.put("payments", paymentsArr);

            Log.d(TAG, "Checkout payload = " + payload.toString());

        } catch (Exception e) {
            Toast.makeText(requireContext(), "Failed to build payload: " + e.getMessage(), Toast.LENGTH_LONG).show();
            if (btnContinuePayment != null) btnContinuePayment.setEnabled(true);
            return;
        }

        final String clientOrderId = OfflineOrderRepository.newClientOrderId(appCtx);
        OfflineOrderRepository.ensureClientOrderId(payload, clientOrderId);
        final String localOrderId = clientOrderId;
        Log.i(TAG, "Checkout submit client_order_id=" + clientOrderId);
        OrderRepository repo = new OrderRepository(appCtx);
        repo.createOrder(token, payload, new OrderRepository.CreateCallback() {
            @Override
            public void onSuccess(@NonNull JSONObject response) {
                new OfflineOrderRepository(appCtx).syncPendingOrders(token);
                mainHandler.post(() -> {
                    Context toastContext = getContext();
                    if (toastContext != null) {
                        Toast.makeText(toastContext, "Checkout success", Toast.LENGTH_SHORT).show();
                    }
                });

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
                        invoiceFinal,
                        result.customerName != null ? result.customerName : "",
                        result.cashReceived,
                        result.changeAmount
                );

                finishActiveDraftAndClearCart();

                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    render();
                    closeOverlaySafely();
                    if (btnContinuePayment != null) btnContinuePayment.setEnabled(true);
                });
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                if (OfflineOrderRepository.shouldSaveOffline(appCtx, statusCode, message)
                        && !ErrorHandler.isDeviceTimeValidationError(statusCode, message)) {
                    saveCheckoutOffline(
                            localOrderId,
                            payload,
                            appCtx,
                            snapshot,
                            paymentCodeFinal,
                            subtotalFinal,
                            deliveryFeeFinal,
                            totalFinal,
                            tableFinal,
                            addrFinal,
                            result.customerName != null ? result.customerName : "",
                            result.cashReceived,
                            result.changeAmount
                    );
                    return;
                }

                Log.i(TAG, "cleanup skipped because save/submit failed pos=" + businessType
                        + " statusCode=" + statusCode);
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    if (ErrorHandler.isDeviceTimeValidationError(statusCode, message)) {
                        ErrorHandler.showDeviceTimeDialog(requireContext());
                        if (btnContinuePayment != null) btnContinuePayment.setEnabled(true);
                        return;
                    }
                    ErrorHandler.handleApiError(requireContext(), "Checkout failed: " + message);
                    if (btnContinuePayment != null) btnContinuePayment.setEnabled(true);
                });
            }
        });
    }

    private void saveCheckoutOffline(@NonNull String localOrderId,
                                     @NonNull JSONObject payload,
                                     @NonNull Context appCtx,
                                     @NonNull List<CartItem> snapshot,
                                     @NonNull String paymentMethod,
                                     double subtotal,
                                     double deliveryFee,
                                     double total,
                                     @NonNull String tableNumber,
                                     @NonNull String deliveryAddress,
                                     @NonNull String customerName,
                                     double cashReceived,
                                     double changeAmount) {
        new OfflineOrderRepository(appCtx).savePendingOrder(
                localOrderId,
                payload,
                businessType,
                new OfflineOrderRepository.SaveCallback() {
                    @Override
                    public void onSuccess(@NonNull String savedLocalOrderId, boolean inserted) {
                        String offlineReceiptNumber = offlineReceiptNumber(savedLocalOrderId, payload);
                        tryAutoPrintOfflineReceipt(
                                appCtx,
                                new SessionManager(appCtx).getToken(),
                                snapshot,
                                paymentMethod,
                                subtotal,
                                deliveryFee,
                                total,
                                tableNumber,
                                deliveryAddress,
                                offlineReceiptNumber,
                                customerName,
                                cashReceived,
                                changeAmount,
                                payload.optString("device_time", ""),
                                result -> showOfflineReceiptResult(appCtx, result)
                        );
                        finishActiveDraftAndClearCart();
                        if (isAdded()) {
                            render();
                            closeOverlaySafely();
                        } else {
                            CartManager.getInstance(appCtx).clear();
                        }
                        if (btnContinuePayment != null) btnContinuePayment.setEnabled(true);
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        Log.i(TAG, "cleanup skipped because save/submit failed pos=" + businessType
                                + " local_save_error=" + message);
                        if (isAdded()) {
                            Toast.makeText(requireContext(),
                                    "Failed to save local order: " + message,
                                    Toast.LENGTH_LONG).show();
                        }
                        if (btnContinuePayment != null) btnContinuePayment.setEnabled(true);
                    }
                }
        );
    }

    private void syncPendingOrdersIfOnline() {
        if (!isAdded()) return;
        Context appCtx = requireContext().getApplicationContext();
        String token = new SessionManager(appCtx).getToken();
        new OfflineOrderRepository(appCtx).syncPendingOrders(token);
    }

    private boolean isRestaurantBusiness() {
        return "restaurant".equalsIgnoreCase(businessType);
    }

    @NonNull
    private static String currentDeviceTimeIso() {
        return new java.text.SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                Locale.US
        ).format(new java.util.Date());
    }

    @NonNull
    private String offlineReceiptNumber(@NonNull String localOrderId, @NonNull JSONObject payload) {
        String clientOrderId = payload.optString("client_order_id", "");
        if (clientOrderId != null && !clientOrderId.trim().isEmpty()) {
            return clientOrderId.trim();
        }
        return localOrderId.trim().isEmpty() ? "OFFLINE-" + System.currentTimeMillis() : localOrderId.trim();
    }

    private void showOfflineReceiptResult(@NonNull Context appCtx,
                                          @NonNull com.valdker.pos.print.BluetoothPrinterManager.PrintResult result) {
        String message;
        switch (result) {
            case SUCCESS:
                message = "Order saved locally and receipt printed.";
                break;
            case SKIPPED:
                message = "Order saved locally. Receipt printing skipped because auto-print is disabled.";
                break;
            case TIMEOUT:
                message = "Order saved locally. Printer connection timed out. Please check printer and try reprint.";
                break;
            case FAILED:
            default:
                message = "Order saved locally, but receipt failed to print.";
                break;
        }
        mainHandler.post(() -> Toast.makeText(appCtx, message, Toast.LENGTH_LONG).show());
    }

    private void tryAutoPrintReceipt(@NonNull Context appCtx,
                                     @NonNull String token,
                                     @NonNull List<CartItem> items,
                                     @NonNull String paymentMethod,
                                     double subtotal,
                                     double deliveryFee,
                                     double total,
                                     @NonNull String tableNumber,
                                     @NonNull String deliveryAddress,
                                     @NonNull String invoiceNumber,
                                     @NonNull String customerName,
                                     double cashReceived,
                                     double changeAmount) {
        tryPrintReceipt(
                appCtx,
                token,
                items,
                paymentMethod,
                subtotal,
                deliveryFee,
                total,
                tableNumber,
                deliveryAddress,
                invoiceNumber,
                customerName,
                cashReceived,
                changeAmount,
                "",
                "",
                null,
                true
        );
    }

    private void tryAutoPrintOfflineReceipt(@NonNull Context appCtx,
                                            @NonNull String token,
                                            @NonNull List<CartItem> items,
                                            @NonNull String paymentMethod,
                                            double subtotal,
                                            double deliveryFee,
                                            double total,
                                            @NonNull String tableNumber,
                                            @NonNull String deliveryAddress,
                                            @NonNull String invoiceNumber,
                                            @NonNull String customerName,
                                            double cashReceived,
                                            double changeAmount,
                                            @NonNull String deviceTime,
                                            @NonNull ReceiptPrintCallback callback) {
        Log.i(TAG, "Offline receipt print started order=" + invoiceNumber);
        tryPrintReceipt(
                appCtx,
                token,
                items,
                paymentMethod,
                subtotal,
                deliveryFee,
                total,
                tableNumber,
                deliveryAddress,
                invoiceNumber,
                customerName,
                cashReceived,
                changeAmount,
                "OFFLINE / PENDING SYNC",
                deviceTime,
                callback,
                false
        );
    }

    private void tryPrintReceipt(@NonNull Context appCtx,
                                 @NonNull String token,
                                 @NonNull List<CartItem> items,
                                 @NonNull String paymentMethod,
                                 double subtotal,
                                 double deliveryFee,
                                 double total,
                                 @NonNull String tableNumber,
                                 @NonNull String deliveryAddress,
                                 @NonNull String invoiceNumber,
                                 @NonNull String customerName,
                                 double cashReceived,
                                 double changeAmount,
                                 @NonNull String receiptStatus,
                                 @NonNull String deviceTime,
                                 @Nullable ReceiptPrintCallback callback,
                                 boolean showPreconditionToast) {
        boolean auto = com.valdker.pos.print.PrinterPrefs.isAutoPrintEnabled(appCtx);
        if (!auto) {
            Log.i(TAG, "PRINTER: auto print disabled -> skip printing");
            if (callback != null) {
                callback.onComplete(com.valdker.pos.print.BluetoothPrinterManager.PrintResult.SKIPPED);
            } else {
                mainHandler.post(() -> Toast.makeText(
                        appCtx,
                        "Receipt printing skipped because auto-print is disabled.",
                        Toast.LENGTH_LONG
                ).show());
            }
            return;
        }

        if (!com.valdker.pos.print.PrinterService.hasBtPermission(appCtx)) {
            Log.w(TAG, "PRINTER: Bluetooth permission not granted -> skip auto print");
            if (showPreconditionToast) {
                mainHandler.post(() ->
                        Toast.makeText(appCtx, "Bluetooth permission has not been allowed. Print canceled.", Toast.LENGTH_LONG).show()
                );
            }
            if (callback != null) {
                callback.onComplete(com.valdker.pos.print.BluetoothPrinterManager.PrintResult.FAILED);
            }
            return;
        }

        String mac = com.valdker.pos.print.PrinterPrefs.getMac(appCtx);
        if (mac == null || mac.trim().isEmpty()) {
            Log.w(TAG, "PRINTER: no printer selected -> skip auto print");
            if (showPreconditionToast) {
                mainHandler.post(() ->
                        Toast.makeText(appCtx, "The printer isn't selected. Select it first in Settings > Printer.", Toast.LENGTH_LONG).show()
                );
            }
            if (callback != null) {
                callback.onComplete(com.valdker.pos.print.BluetoothPrinterManager.PrintResult.FAILED);
            }
            return;
        }

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
                invoiceNumber,
                customerName,
                cashReceived,
                changeAmount,
                receiptStatus,
                deviceTime
        );

        AtomicBoolean receiptPrinted = new AtomicBoolean(false);
        com.valdker.pos.repositories.ShopRepository.getBestShopProfileForReceipt(
                appCtx,
                token,
                new com.valdker.pos.repositories.ShopRepository.Callback() {
                    @Override
                    public void onSuccess(@NonNull com.valdker.pos.models.Shop shop) {
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
                                invoiceNumber,
                                customerName,
                                cashReceived,
                                changeAmount,
                                receiptStatus,
                                deviceTime
                        );

                        printReceiptOnce(appCtx, receipt, receiptPrinted, callback);
                    }

                    @Override
                    public void onEmpty() {
                        printReceiptOnce(appCtx, fallbackReceipt, receiptPrinted, callback);
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        printReceiptOnce(appCtx, fallbackReceipt, receiptPrinted, callback);
                    }
                }
        );
    }

    private void printReceiptOnce(@NonNull Context appCtx,
                                  @NonNull String receipt,
                                  @NonNull AtomicBoolean printed,
                                  @Nullable ReceiptPrintCallback callback) {
        if (!printed.compareAndSet(false, true)) {
            Log.w(TAG, callback != null
                    ? "Offline receipt print skipped: already printed"
                    : "Receipt print skipped: already printed");
            return;
        }
        doPrintBestEffort(appCtx, receipt, callback);
    }

    private void doPrintBestEffort(@NonNull Context appCtx,
                                   @NonNull String receipt,
                                   @Nullable ReceiptPrintCallback callback) {
        com.valdker.pos.print.PrinterService.printTextAsync(
                appCtx,
                receipt,
                new com.valdker.pos.print.BluetoothPrinterManager.PrintCallback() {
                    @Override
                    public void onSuccess() {
                        if (callback != null) {
                            Log.i(TAG, "Offline receipt print success");
                            callback.onComplete(com.valdker.pos.print.BluetoothPrinterManager.PrintResult.SUCCESS);
                        } else {
                            Log.i(TAG, "PRINTER: auto print success");
                        }
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        if (callback != null) {
                            Log.e(TAG, "Offline receipt print failed: " + message);
                            callback.onComplete(com.valdker.pos.print.BluetoothPrinterManager.PrintResult.FAILED);
                        } else {
                            Log.e(TAG, "PRINTER: auto print failed: " + message);
                        }
                        showPrintFailureWithRetry(appCtx, receipt, message);
                    }

                    @Override
                    public void onSkipped(@NonNull String message) {
                        if (callback != null) {
                            Log.w(TAG, "Offline receipt print skipped: " + message);
                            callback.onComplete(com.valdker.pos.print.BluetoothPrinterManager.PrintResult.SKIPPED);
                        } else {
                            Log.w(TAG, "PRINTER: auto print skipped: " + message);
                        }
                    }

                    @Override
                    public void onTimeout(@NonNull String message) {
                        if (callback != null) {
                            Log.e(TAG, "Offline receipt print timed out: " + message);
                            callback.onComplete(com.valdker.pos.print.BluetoothPrinterManager.PrintResult.TIMEOUT);
                        } else {
                            Log.e(TAG, "PRINTER: auto print timed out: " + message);
                        }
                        showPrintFailureWithRetry(appCtx, receipt, message);
                    }
                }
        );
    }

    private void showPrintFailureWithRetry(@NonNull Context appCtx,
                                           @NonNull String receipt,
                                           @NonNull String message) {
        if (!isAdded()) {
            Toast.makeText(appCtx,
                    "Transaction saved, but receipt failed to print. " + message,
                    Toast.LENGTH_LONG).show();
            return;
        }

        androidx.appcompat.app.AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setMessage("Transaction saved, but receipt failed to print. Retry print?")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Retry", null)
                .show();
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            v.setEnabled(false);
            doPrintBestEffort(appCtx, receipt, null);
            dialog.dismiss();
        });
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
                                    @NonNull String invoiceNumber,
                                    @NonNull String customerName,
                                    double cashReceived,
                                    double changeAmount,
                                    @NonNull String receiptStatus,
                                    @NonNull String deviceTime) {

        String cashier = "";
        try {
            SessionManager sm = new SessionManager(appCtx);
            String u = sm.getUsername();
            cashier = (u != null) ? u.trim() : "";
        } catch (Exception ignored) {
        }

        java.text.SimpleDateFormat dfDate = new java.text.SimpleDateFormat("dd/MM/yy", Locale.US);
        java.text.SimpleDateFormat dfTime = new java.text.SimpleDateFormat("HH:mm", Locale.US);
        String date = dfDate.format(new java.util.Date());
        String time = dfTime.format(new java.util.Date());

        StringBuilder sb = new StringBuilder();

        sb.append("[C]<b>").append(shopName).append("</b>\n");
        if (!shopAddress.trim().isEmpty()) sb.append("[C]").append(shopAddress.trim()).append("\n");
        if (!shopPhone.trim().isEmpty()) sb.append("[C]").append(shopPhone.trim()).append("\n");
        sb.append("[C]--------------------------------\n");
        if (!receiptStatus.trim().isEmpty()) {
            sb.append("[C]<b>").append(receiptStatus.trim()).append("</b>\n");
            sb.append("[C]--------------------------------\n");
        }

        sb.append("[L]Order:[R]").append(invoiceNumber).append("\n");
        if (!cashier.isEmpty()) sb.append("[L]Cashier:[R]").append(cashier).append("\n");
        if (!customerName.trim().isEmpty() && !"Walk-in Customer".equalsIgnoreCase(customerName.trim())) {
            sb.append("[L]Customer:[R]").append(customerName.trim()).append("\n");
        }
        sb.append("[L]Date:[R]").append(date).append("\n");
        sb.append("[L]Time:[R]").append(time).append("\n");
        if (!deviceTime.trim().isEmpty()) {
            sb.append("[L]Device Time:[R]").append(deviceTime.trim()).append("\n");
        }

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
            if (CartManager.TYPE_DINE_IN.equals(type)) typeLabel = "(* DINE IN)";
            else if (CartManager.TYPE_TAKE_OUT.equals(type)) typeLabel = "(* TAKE OUT)";
            else if (CartManager.TYPE_DELIVERY.equals(type)) typeLabel = "(^ DELIVERY)";

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
        sb.append("[L]Payment[R]").append(paymentMethod).append("\n");
        if (cashReceived > 0) {
            sb.append("[L]Paid[R]").append(String.format(Locale.US, "$%.2f", cashReceived)).append("\n");
            sb.append("[L]Change[R]").append(String.format(Locale.US, "$%.2f", changeAmount)).append("\n");
        }
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

    @NonNull
    private String safeLower(@Nullable String t) {
        if (t == null) return "";
        return t.trim().toLowerCase(Locale.US);
    }

    private void closeOverlaySafely() {
        if (!isAdded()) return;

        try {
            requireActivity().getSupportFragmentManager().popBackStack();
        } catch (Exception ignored) {
        }

        View overlay = requireActivity().findViewById(R.id.overlayContainer);
        if (overlay != null) overlay.setVisibility(View.GONE);
    }
}
