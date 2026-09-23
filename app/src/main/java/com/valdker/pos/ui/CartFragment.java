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
import com.valdker.pos.money.Money;
import com.valdker.pos.print.ReceiptBuilder;
import com.valdker.pos.print.ReceiptContent;
import com.valdker.pos.print.ReceiptPayloadReader;
import com.valdker.pos.money.OrderTotals;
import com.valdker.pos.models.Customer;
import com.valdker.pos.repositories.CheckoutConfigRepository;
import com.valdker.pos.repositories.CustomerRepository;
import com.valdker.pos.repositories.MasterDataRepository;
import com.valdker.pos.repositories.OfflineOrderRepository;
import com.valdker.pos.restaurant.RestaurantOrderSync;
import com.valdker.pos.restaurant.RestaurantRepository;
import com.valdker.pos.restaurant.DineInTableRule;
import com.valdker.pos.restaurant.TablePickerDialogFragment;
import com.valdker.pos.restaurant.WaiterPickerDialogFragment;
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

public class CartFragment extends Fragment
        implements TablePickerDialogFragment.Listener,
        WaiterPickerDialogFragment.Listener {

    public interface DraftLifecycleHost {
        void onCartOrderFinished();

        /**
         * Kunci idempotensi untuk draft yang sedang aktif: dicetak sekali lalu
         * dipakai ulang selama draft itu belum tersimpan.
         *
         * Kuncinya dipegang host, bukan fragment ini, karena CartFragment
         * dibuat ulang setiap kali overlay keranjang dibuka — kalau disimpan di
         * sini, kunci hilang begitu kasir menutup keranjang, dan percobaan
         * berikutnya untuk draft yang sama akan mencetak kunci baru.
         */
        @NonNull
        String obtainClientOrderIdForActiveDraft();

        /**
         * Meja dan pelayan draft yang sedang aktif, atau {@code null} kalau
         * belum dipilih.
         *
         * <p>Alasannya sama persis dengan client_order_id di atas: nilainya
         * milik DRAFT, bukan milik layar. Satu meja boleh punya beberapa bill
         * terbuka, jadi kasir wajar bolak-balik antar draft; menahan meja di
         * field CartFragment atau Activity membuat dua draft saling menukar
         * mejanya begitu kasir berpindah. Host membacanya dari baris draft di
         * Room, yang juga membuatnya selamat dari proses yang dimatikan.
         */
        @Nullable
        Long dineInTableIdForActiveDraft();

        @Nullable
        String dineInTableNameForActiveDraft();

        @Nullable
        Long dineInWaiterIdForActiveDraft();

        @Nullable
        String dineInWaiterNameForActiveDraft();

        void setDineInTableForActiveDraft(@Nullable Long tableId, @Nullable String tableName);

        void setDineInWaiterForActiveDraft(@Nullable Long waiterId, @Nullable String waiterName);
    }

    private interface ReceiptPrintCallback {
        void onComplete(@NonNull com.valdker.pos.print.BluetoothPrinterManager.PrintResult result);
    }

    private View rowDineIn;
    private com.google.android.material.button.MaterialButton btnPickTable;
    private com.google.android.material.button.MaterialButton btnPickWaiter;

    private static final String TAG = "CART_FRAGMENT";
    private static final String TAG_NATIVE_CHECKOUT = "NATIVE_CHECKOUT";

    private static final String ARG_BUSINESS_TYPE = "business_type";
    private static final String ARG_ENABLE_DINE_IN = "enable_dine_in";
    private static final String ARG_ENABLE_TAKEAWAY = "enable_takeaway";
    private static final String ARG_ENABLE_DELIVERY = "enable_delivery";
    private static final String ARG_ENABLE_TABLE_NUMBER = "enable_table_number";
    private static final String ARG_ENABLE_SPLIT_PAYMENT = "enable_split_payment";

    private RecyclerView rv;
    /**
     * Keadaan kosong. Bertipe {@link View}, bukan {@code TextView}: sejak
     * tampilannya dirapikan, id tvCartEmpty menempel pada wadah berisi ikon,
     * judul, dan keterangan - bukan lagi satu baris teks abu-abu.
     */
    private View tvEmpty;
    private TextView tvSubtotal;
    private TextView tvCartCount;
    @Nullable
    private ImageButton btnClose;
    private Button btnContinuePayment;
    private Button btnCancelOrder;

    /**
     * Benar bila keranjang sedang jadi kolom tetap pada layar kasir tablet,
     * bukan laci yang menutupi menu.
     *
     * <p>Bedanya bukan kosmetik: dalam mode kolom tidak ada yang boleh
     * di-pop dari back stack setelah checkout selesai. Memanggil
     * popBackStack() di sana akan membuang entri milik layar lain, karena
     * keranjang memang tidak pernah didorong ke back stack.
     */
    private boolean embeddedInPane = false;

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

    /**
     * Berapa meja aktif yang dimiliki toko ini. {@code -1} berarti belum
     * diketahui (permintaan belum selesai atau gagal).
     *
     * <p>Angkanya menentukan mekanisme meja mana yang dipakai: grid meja hanya
     * benar-benar bisa dipakai kalau ada minimal satu meja aktif. Modul
     * "tables" yang menyala di toko yang belum membuat satu meja pun tidak
     * memberi apa-apa - dan kalau kolom nomor meja di dialog ikut
     * disembunyikan karena modulnya menyala, order dine-in jadi tidak punya
     * tempat mencatat meja sama sekali.
     */
    private int activeTableCount = -1;
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

        embeddedInPane = getId() == R.id.cartPaneContainer;

        rv = view.findViewById(R.id.rvCart);
        tvEmpty = view.findViewById(R.id.tvCartEmpty);
        tvSubtotal = view.findViewById(R.id.tvSubtotal);
        tvCartCount = view.findViewById(R.id.tvCartCount);
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
                            Toast.makeText(requireContext(), R.string.msg_order_type_not_allowed,
                    Toast.LENGTH_SHORT).show();
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

        loadActiveTableCountOnce();

        rowDineIn = view.findViewById(R.id.rowDineInAssignment);
        btnPickTable = view.findViewById(R.id.btnPickTable);
        btnPickWaiter = view.findViewById(R.id.btnPickWaiter);

        if (btnPickTable != null) {
            btnPickTable.setOnClickListener(v -> {
                DraftLifecycleHost host = resolveDraftLifecycleHost();
                TablePickerDialogFragment.newInstance(
                                host != null ? host.dineInTableIdForActiveDraft() : null)
                        .show(getChildFragmentManager(), "table_picker");
            });
        }

        if (btnPickWaiter != null) {
            btnPickWaiter.setOnClickListener(v -> {
                DraftLifecycleHost host = resolveDraftLifecycleHost();
                WaiterPickerDialogFragment.newInstance(
                                host != null ? host.dineInWaiterIdForActiveDraft() : null)
                        .show(getChildFragmentManager(), "waiter_picker");
            });
        }

        if (btnClose != null) btnClose.setOnClickListener(v -> closeOverlaySafely());

        // Menyentuh latar redup menutup laci - perilaku yang diharapkan dari
        // setiap laci, dan sebelumnya tidak disambungkan sama sekali: satu-
        // satunya jalan keluar adalah tombol silang kecil di pojok.
        View dim = view.findViewById(R.id.viewDim);
        if (dim != null) dim.setOnClickListener(v -> closeOverlaySafely());

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

        Toast.makeText(requireContext(), R.string.msg_order_cancelled, Toast.LENGTH_SHORT).show();

        finishActiveDraftAndClearCart();

        mainHandler.post(this::closeOverlaySafely);
    }

    @Nullable
    private DraftLifecycleHost resolveDraftLifecycleHost() {
        DraftLifecycleHost host = draftLifecycleHostRef != null ? draftLifecycleHostRef.get() : null;
        if (host == null && getActivity() instanceof DraftLifecycleHost) {
            host = (DraftLifecycleHost) getActivity();
        }
        return host;
    }

    @NonNull
    private String obtainClientOrderId(@NonNull android.content.Context appCtx) {
        DraftLifecycleHost host = resolveDraftLifecycleHost();
        if (host != null) {
            return host.obtainClientOrderIdForActiveDraft();
        }
        // Tanpa host tidak ada tempat menahan kunci lintas percobaan; cetak baru
        // supaya checkout tetap jalan (perilaku sebelum patch ini).
        Log.w(TAG, "Draft lifecycle host unavailable; client_order_id tidak bisa ditahan lintas percobaan.");
        return OfflineOrderRepository.newClientOrderId(appCtx);
    }

    private void finishActiveDraftAndClearCart() {
        Log.i(TAG, "checkout cleanup started pos=" + businessType);

        DraftLifecycleHost host = resolveDraftLifecycleHost();

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
            Toast.makeText(requireContext(), R.string.msg_cart_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        final Context appCtx = requireContext().getApplicationContext();
        final SessionManager sm = new SessionManager(appCtx);
        final String token = sm.getToken();

        if (token == null || token.trim().isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.error_session_expired), Toast.LENGTH_SHORT).show();
            return;
        }

        boolean hasDineInItem = false;
        boolean hasOtherTypeItem = false;
        boolean needDelivery = false;

        for (CartItem it : cartItems) {
            String t = normalizeType(it.orderType);
            if (CartManager.TYPE_DINE_IN.equals(t)) hasDineInItem = true;
            else hasOtherTypeItem = true;
            if (CartManager.TYPE_DELIVERY.equals(t) && enableDelivery) needDelivery = true;
        }

        // Satu aturan untuk dua mekanisme meja; lihat DineInTableRule untuk
        // matriks lengkapnya beserta tesnya.
        DineInTableRule.Decision tableDecision = DineInTableRule.decide(
                hasDineInItem,
                hasOtherTypeItem,
                canUseModule(RestaurantRepository.MODULE_TABLES),
                activeTableCount,
                pickedTableId(),
                enableTableNumber);

        // Kewajiban memilih meja hidup di keranjang, bukan di dialog
        // pembayaran. Dialog dulu yang menjaganya lewat kolom teks wajib,
        // tapi kolom itu ditimpa server dan kini disembunyikan - kalau
        // penjaganya tidak ikut pindah ke sini, jaminannya hilang bersama
        // kolomnya.
        if (tableDecision == DineInTableRule.Decision.MIXED_WITH_TABLE) {
            // Tidak membuka picker: yang perlu diubah adalah tipe itemnya,
            // atau mejanya dilepas - dua-duanya ada di layar ini.
            Toast.makeText(requireContext(),
                    getString(R.string.dinein_mixed_with_table), Toast.LENGTH_LONG).show();
            return;
        }

        if (DineInTableRule.blocksCheckout(tableDecision)) {
            Toast.makeText(requireContext(),
                    getString(R.string.dinein_table_required), Toast.LENGTH_LONG).show();
            if (btnPickTable != null) btnPickTable.performClick();
            return;
        }

        boolean needTable = DineInTableRule.needsFreeTextField(tableDecision);

        if (tableDecision == DineInTableRule.Decision.NO_TABLE_RECORDED) {
            // Modul meja mati DAN kolom nomor meja dimatikan: toko ini memang
            // tidak mencatat meja. Itu setelan mereka, bukan lubang yang harus
            // ditambal dengan memaksa kolom muncul - tapi dicatat supaya bisa
            // ditelusuri kalau nanti papan dapur menampilkan "Tanpa meja".
            Log.i(TAG, "Order dine-in tanpa meja: grid meja tidak tersedia"
                    + " (aktif=" + activeTableCount + ") dan enable_table_number="
                    + enableTableNumber + ". Sesuai setelan toko.");
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
                // Bilah luring milik layar kasir, bukan toast milik keranjang:
                // satu pemberitahuan untuk seluruh layar.
                if (getActivity() instanceof com.valdker.pos.MainActivity) {
                    ((com.valdker.pos.MainActivity) getActivity()).setPosOffline(true);
                } else if (!offlineNoticeShown[0]) {
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
            dialog = NativeCheckoutDialogFragment.newInstance(
                    subtotal, needTable, needDelivery);
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
            Toast.makeText(requireContext(), getString(R.string.error_session_expired), Toast.LENGTH_SHORT).show();
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
            Toast.makeText(requireContext(), R.string.msg_cart_empty, Toast.LENGTH_SHORT).show();
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

        // Hanya berlaku pada jalur nomor meja teks bebas. Kalau meja sudah
        // dipilih dari grid, tableFinal memang kosong - kolomnya tidak pernah
        // ditampilkan - dan itu benar, bukan alasan menolak checkout.
        boolean freeTextTableRequired = DineInTableRule.needsFreeTextField(
                DineInTableRule.decide(
                        hasDineIn,
                        hasNonDineIn(snapshot),
                        canUseModule(RestaurantRepository.MODULE_TABLES),
                        activeTableCount,
                        pickedTableId(),
                        enableTableNumber));

        if (freeTextTableRequired && tableFinal.isEmpty()) {
            Toast.makeText(requireContext(), R.string.msg_table_number_required, Toast.LENGTH_LONG).show();
            if (btnContinuePayment != null) btnContinuePayment.setEnabled(true);
            return;
        }

        if (hasDelivery && enableDelivery && addrFinal.isEmpty()) {
            Toast.makeText(requireContext(), R.string.msg_delivery_address_required, Toast.LENGTH_LONG).show();
            if (btnContinuePayment != null) btnContinuePayment.setEnabled(true);
            return;
        }

        final Money subtotalMoney = calcSubtotal(snapshot);
        final Money feeSafeMoney = result.deliveryFeeMoney.orZeroIfNegative();

        final boolean hasDeliveryFinal = hasDelivery;
        final Money deliveryFeeMoney = hasDeliveryFinal ? feeSafeMoney : Money.zero();

        // Rumus yang sama dengan server, termasuk urutan: pajak dihitung dari
        // (subtotal - diskon), lalu ongkir ditambahkan setelahnya.
        final OrderTotals orderTotals = OrderTotals.of(
                subtotalMoney,
                result.discountMoney,
                deliveryFeeMoney,
                new SessionManager(appCtx).getTaxPercent());
        final Money discountMoney = orderTotals.discount();
        final Money taxMoney = orderTotals.tax();
        final Money totalMoney = orderTotals.total();

        // Dipertahankan untuk pemanggil cetak struk yang belum dimigrasi.
        final double deliveryFeeFinal = deliveryFeeMoney.toDouble();
        final double subtotalFinal = subtotalMoney.toDouble();
        final double discountFinal = discountMoney.toDouble();
        final double totalFinal = totalMoney.toDouble();

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
            payload.put("subtotal", subtotalMoney.toPlainString());
            payload.put("discount", discountMoney.toPlainString());
            payload.put("tax", taxMoney.toPlainString());
            payload.put("total", totalMoney.toPlainString());
            payload.put("notes", "Checkout from Android");
            payload.put("is_paid", true);

            // PERHATIAN: untuk shop restoran, nilai ini DIABAIKAN server.
            // OrderSerializer.create() menghitung ulang default_order_type dari
            // himpunan order_type item: satu tipe seragam dipakai apa adanya,
            // lebih dari satu tipe selalu menjadi GENERAL. Jadi keranjang
            // campur (satu dine-in + satu bungkus) tidak pernah tersimpan
            // sebagai DINE_IN, betapa pun yang dikirim di sini - dan karena
            // status "meja terisi" mensyaratkan default_order_type=DINE_IN,
            // meja pada order campur tidak akan pernah tampil terisi.
            //
            // Yang dikirim di sini tetap perlu benar untuk shop NON-restoran:
            // serializer menolak nilai selain GENERAL untuk mereka dengan 400.
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

            // Meja/pelayan diambil dari DRAFT, bukan dari state layar, supaya
            // bill yang dibuka bergantian tidak saling menukar mejanya.
            // Meja dan pelayan hanya ikut kalau order ini benar-benar punya
            // item dine-in. Barisnya di keranjang juga hanya muncul untuk
            // keranjang dine-in, jadi tanpa syarat yang sama di sini sebuah
            // bill yang tadinya dine-in lalu diubah jadi bungkus akan tetap
            // membawa mejanya - tersembunyi, dan tidak bisa dibersihkan kasir
            // karena tombolnya sudah tidak tampil.
            if (isRestaurantBusiness() && hasDineIn) {
                DraftLifecycleHost dineInHost = resolveDraftLifecycleHost();
                RestaurantOrderSync.attachDineInFields(
                        payload,
                        canUseModule(RestaurantRepository.MODULE_TABLES) && dineInHost != null
                                ? dineInHost.dineInTableIdForActiveDraft() : null,
                        canUseModule(RestaurantRepository.MODULE_WAITERS) && dineInHost != null
                                ? dineInHost.dineInWaiterIdForActiveDraft() : null);
            }
            payload.put("delivery_address", addrFinal);
            payload.put("delivery_fee", deliveryFeeMoney.toPlainString());

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
                one.put("price", it.price().orZeroIfNegative().toPlainString());

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
                Toast.makeText(requireContext(), R.string.msg_cart_empty, Toast.LENGTH_LONG).show();
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
            Money splitTotal = Money.zero();
            for (NativeCheckoutDialogFragment.SplitPayment sp : result.splitPayments) {
                splitTotal = splitTotal.plus(sp.amount);
            }
            // Metode utama menanggung sisa setelah pembayaran terbagi.
            paymentObj.put("amount", totalMoney.minus(splitTotal).toPlainString());
            paymentObj.put("reference_number", result.referenceNumber != null ? result.referenceNumber : "");
            paymentObj.put("note", result.paymentNote != null ? result.paymentNote : "");

            paymentsArr.put(paymentObj);

            for (NativeCheckoutDialogFragment.SplitPayment sp : result.splitPayments) {
                JSONObject extra = new JSONObject();
                extra.put("payment_method_id", sp.paymentMethodId != null ? sp.paymentMethodId : JSONObject.NULL);
                extra.put("bank_account_id", JSONObject.NULL);
                extra.put("amount", sp.amount.toPlainString());
                extra.put("reference_number", "");
                extra.put("note", "");
                paymentsArr.put(extra);
            }

            Log.d(TAG, "FINAL itemsArr = " + itemsArr.toString());
            payload.put("items", itemsArr);
            payload.put("payments", paymentsArr);

            Log.d(TAG, "Checkout payload = " + payload.toString());

        } catch (Exception e) {
            Toast.makeText(requireContext(),
                    getString(R.string.msg_build_payload_failed, e.getMessage()),
                    Toast.LENGTH_LONG).show();
            if (btnContinuePayment != null) btnContinuePayment.setEnabled(true);
            return;
        }

        final String clientOrderId = obtainClientOrderId(appCtx);
        OfflineOrderRepository.ensureClientOrderId(payload, clientOrderId);
        final String localOrderId = clientOrderId;

        // Salinan payload berisi keterangan yang hanya dibutuhkan struk: nama
        // pelayan, nama pelanggan, nama tiap metode bayar, uang yang
        // diserahkan, dan kembaliannya. Kontrak API tidak mengenal satu pun
        // dari itu - server hanya menerima id - dan kunci-kunci ini dibuang
        // lagi sebelum payload dikirim, lihat ReceiptPayloadReader.
        //
        // Yang disimpan ke Room adalah salinan ini, sehingga struk cetak ulang
        // membaca keterangan yang sama persis dengan struk aslinya.
        final JSONObject receiptPayload = ReceiptPayloadReader.withLocalExtras(
                payload,
                dineInWaiterNameForReceipt(),
                receiptCustomerName(result.customerName),
                paymentLabelsForReceipt(result, paymentCodeFinal),
                result.cashReceivedMoney,
                result.changeAmountMoney);
        Log.i(TAG, "Checkout submit client_order_id=" + clientOrderId);

        // WRITE-AHEAD: simpan dulu ke Room, baru kirim. Kalau proses dimatikan
        // sebelum callback tiba, penjualannya tidak ikut hilang.
        new OfflineOrderRepository(appCtx).savePendingOrder(
                localOrderId,
                receiptPayload,
                businessType,
                new OfflineOrderRepository.SaveCallback() {
                    @Override
                    public void onSuccess(@NonNull String savedLocalOrderId, boolean inserted) {
                        Log.i(TAG, "Checkout write-ahead saved localOrderId=" + savedLocalOrderId
                                + " inserted=" + inserted);
                        sendCheckoutAfterWriteAhead(savedLocalOrderId, payload, receiptPayload,
                                appCtx, token, snapshot,
                                result, paymentCodeFinal, subtotalFinal, discountFinal, deliveryFeeFinal, totalFinal,
                                tableFinal, addrFinal);
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        Log.e(TAG, "Checkout write-ahead FAILED, submit dibatalkan: " + message);
                        mainHandler.post(() -> {
                            if (isAdded()) {
                                Toast.makeText(requireContext(),
                                        "Failed to save local order: " + message,
                                        Toast.LENGTH_LONG).show();
                            }
                            if (btnContinuePayment != null) btnContinuePayment.setEnabled(true);
                        });
                    }
                }
        );
    }

    private void sendCheckoutAfterWriteAhead(@NonNull String localOrderId,
                                             @NonNull JSONObject payload,
                                             @NonNull JSONObject receiptPayload,
                                             @NonNull Context appCtx,
                                             @NonNull String token,
                                             @NonNull List<CartItem> snapshot,
                                             @NonNull NativeCheckoutDialogFragment.BankCheckoutResult result,
                                             @NonNull String paymentCodeFinal,
                                             double subtotalFinal,
                                             double discountFinal,
                                             double deliveryFeeFinal,
                                             double totalFinal,
                                             @NonNull String tableFinal,
                                             @NonNull String addrFinal) {
        OrderRepository repo = new OrderRepository(appCtx);
        repo.createOrder(token, payload, new OrderRepository.CreateCallback() {
            @Override
            public void onSuccess(@NonNull JSONObject response) {
                // 201 saja bukan bukti bahwa meja/pelayan tersimpan: DRF
                // membuang field yang tidak dikenal tanpa bersuara.
                RestaurantOrderSync.reconcileAfterCreate(
                        appCtx, token, payload, response, "checkout");

                OfflineOrderRepository offlineRepo = new OfflineOrderRepository(appCtx);
                offlineRepo.markWriteAheadSynced(localOrderId);
                offlineRepo.syncPendingOrders(token);
                mainHandler.post(() -> {
                    Context toastContext = getContext();
                    if (toastContext != null) {
                        Toast.makeText(toastContext, R.string.msg_checkout_success, Toast.LENGTH_SHORT).show();
                    }
                });

                String invoiceFromApi = response.optString("invoice_number", "");
                String invoiceFinal = (invoiceFromApi != null && !invoiceFromApi.trim().isEmpty())
                        ? invoiceFromApi.trim()
                        : "INV-" + System.currentTimeMillis();

                tryPrintReceipt(appCtx, token, receiptPayload, invoiceFinal, "", null, true);

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
                OfflineOrderRepository offlineRepo = new OfflineOrderRepository(appCtx);

                if (OfflineOrderRepository.shouldSaveOffline(appCtx, statusCode, message)
                        && !ErrorHandler.isDeviceTimeValidationError(statusCode, message)) {
                    // Sudah tersimpan PENDING_SYNC oleh write-ahead.
                    finishCheckoutAfterOfflineSave(
                            localOrderId,
                            receiptPayload,
                            appCtx,
                            snapshot,
                            paymentCodeFinal,
                            subtotalFinal,
                            discountFinal,
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

                if (ErrorHandler.isDeviceTimeValidationError(statusCode, message)) {
                    offlineRepo.markWriteAheadNeedsReview(localOrderId, "device_time rejected");
                } else {
                    offlineRepo.markWriteAheadFailed(localOrderId, "HTTP " + statusCode);
                }

                Log.i(TAG, "cart kept because server rejected pos=" + businessType
                        + " statusCode=" + statusCode + " (order tersimpan lokal)");
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

    /**
     * Order sudah tersimpan lokal oleh write-ahead; ini hanya menutup transaksi
     * di layar (cetak struk offline, bersihkan keranjang).
     */
    private void finishCheckoutAfterOfflineSave(@NonNull String localOrderId,
                                                @NonNull JSONObject payload,
                                                @NonNull Context appCtx,
                                                @NonNull List<CartItem> snapshot,
                                                @NonNull String paymentMethod,
                                                double subtotal,
                                                double discount,
                                                double deliveryFee,
                                                double total,
                                                @NonNull String tableNumber,
                                                @NonNull String deliveryAddress,
                                                @NonNull String customerName,
                                                double cashReceived,
                                                double changeAmount) {
        Log.i(TAG, "Offline receipt print started order=" + offlineReceiptNumber(localOrderId, payload));
        tryPrintReceipt(
                appCtx,
                new SessionManager(appCtx).getToken(),
                payload,
                offlineReceiptNumber(localOrderId, payload),
                "OFFLINE / PENDING SYNC",
                printResult -> showOfflineReceiptResult(appCtx, printResult),
                false
        );
        finishActiveDraftAndClearCart();
        mainHandler.post(() -> {
            if (isAdded()) {
                Toast.makeText(requireContext(),
                        OfflineOrderRepository.MESSAGE_ORDER_SAVED_LOCALLY,
                        Toast.LENGTH_LONG).show();
                render();
                closeOverlaySafely();
            } else {
                CartManager.getInstance(appCtx).clear();
            }
            if (btnContinuePayment != null) btnContinuePayment.setEnabled(true);
        });
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

    /**
     * Mencetak struk dari payload order.
     *
     * <p>Semua yang tercetak datang dari payload yang sama dengan yang
     * disimpan ke Room, dibaca lewat ReceiptPayloadReader dan disusun
     * ReceiptBuilder. Sebelumnya fungsi ini menerima selusin double dan
     * menyusun barisnya sendiri, sementara cetak ulang membaca payload dan
     * menyusun barisnya sendiri pula - dan keduanya sudah menyimpang.
     */
    private void tryPrintReceipt(@NonNull Context appCtx,
                                 @NonNull String token,
                                 @NonNull JSONObject receiptPayload,
                                 @NonNull String invoiceNumber,
                                 @NonNull String receiptStatus,
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
                        Toast.makeText(appCtx, R.string.msg_bluetooth_permission_required, Toast.LENGTH_LONG).show()
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
                        Toast.makeText(appCtx, R.string.msg_printer_not_selected, Toast.LENGTH_LONG).show()
                );
            }
            if (callback != null) {
                callback.onComplete(com.valdker.pos.print.BluetoothPrinterManager.PrintResult.FAILED);
            }
            return;
        }

        String fallbackReceipt = buildReceiptText(
                appCtx, receiptPayload, invoiceNumber, receiptStatus, "VALDKER POS", "", "");

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
                        String receipt = buildReceiptText(
                                appCtx,
                                receiptPayload,
                                invoiceNumber,
                                receiptStatus,
                                shopName,
                                shop.address != null ? shop.address.trim() : "",
                                shop.phone != null ? shop.phone.trim() : "");
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
                .setMessage(R.string.msg_receipt_print_failed_retry)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(getString(R.string.action_retry), null)
                .show();
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            v.setEnabled(false);
            doPrintBestEffort(appCtx, receipt, null);
            dialog.dismiss();
        });
    }

    /**
     * Menyusun teks struk dari payload order.
     *
     * <p>Isinya - item, nominal, meja, pelayan, metode bayar - seluruhnya
     * dibaca dari payload; yang ditambahkan di sini hanya identitas toko,
     * nama kasir, dan spanduk status, yaitu hal-hal yang memang tidak ada di
     * payload. Jalur cetak ulang di PendingOrderReceiptPrinter melakukan hal
     * yang sama persis, sehingga kedua kertas tidak bisa lagi berbeda isinya.
     */
    @NonNull
    private String buildReceiptText(@NonNull Context appCtx,
                                    @NonNull JSONObject receiptPayload,
                                    @NonNull String invoiceNumber,
                                    @NonNull String receiptStatus,
                                    @NonNull String shopName,
                                    @NonNull String shopAddress,
                                    @NonNull String shopPhone) {
        ReceiptContent content = ReceiptPayloadReader.read(receiptPayload);

        content.shopName = shopName;
        content.shopAddress = shopAddress;
        content.shopPhone = shopPhone;
        content.statusBanner = receiptStatus;
        if (!invoiceNumber.trim().isEmpty()) {
            content.orderNumber = invoiceNumber.trim();
        }
        content.cashier = cashierNameForReceipt(appCtx);
        content.footerNote = getString(R.string.receipt_thank_you);

        return ReceiptBuilder.build(content);
    }

    @NonNull
    private String cashierNameForReceipt(@NonNull Context appCtx) {
        try {
            String username = new SessionManager(appCtx).getUsername();
            return username != null ? username.trim() : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    /**
     * Nama pelayan untuk struk, hanya untuk bill yang benar-benar dine-in.
     *
     * <p>Syaratnya sama dengan yang dipakai saat mengirim waiter_id ke server
     * - kalau tidak, bill yang tadinya dine-in lalu diubah jadi bungkus akan
     * tetap mencetak nama pelayan yang sudah tidak berlaku.
     */
    @NonNull
    private String dineInWaiterNameForReceipt() {
        if (!isRestaurantBusiness()) return "";
        if (!canUseModule(RestaurantRepository.MODULE_WAITERS)) return "";

        DraftLifecycleHost host = resolveDraftLifecycleHost();
        String name = host != null ? host.dineInWaiterNameForActiveDraft() : null;
        return name != null ? name.trim() : "";
    }

    /** "Walk-in Customer" tidak dicetak; itu bukan nama pelanggan. */
    @NonNull
    private String receiptCustomerName(@Nullable String customerName) {
        String clean = customerName == null ? "" : customerName.trim();
        return "Walk-in Customer".equalsIgnoreCase(clean) ? "" : clean;
    }

    /**
     * Nama metode bayar untuk tiap baris pembayaran, urut sama dengan array
     * "payments" di payload: metode utama lebih dulu, lalu tiap pembayaran
     * terbagi. Server hanya menerima payment_method_id, jadi tanpa daftar ini
     * struk cetak ulang hanya bisa menulis "Method #3".
     */
    @NonNull
    private org.json.JSONArray paymentLabelsForReceipt(
            @NonNull NativeCheckoutDialogFragment.BankCheckoutResult result,
            @NonNull String primaryCode) {
        org.json.JSONArray labels = new org.json.JSONArray();
        labels.put(primaryCode);
        for (NativeCheckoutDialogFragment.SplitPayment sp : result.splitPayments) {
            labels.put(sp.methodCode != null ? sp.methodCode : "-");
        }
        return labels;
    }

    private Money calcSubtotal(@NonNull List<CartItem> items) {
        Money total = Money.zero();
        for (CartItem it : items) {
            if (it == null || it.qty <= 0) continue;
            total = total.plus(it.price().orZeroIfNegative().times(it.qty));
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

        if (tvSubtotal != null) tvSubtotal.setText(cart.getTotal().format());

        if (tvCartCount != null) {
            int qty = cart.getTotalQty();
            tvCartCount.setText(qty == 1
                    ? getString(R.string.cart_item_count_one)
                    : getString(R.string.cart_item_count_format, qty));
        }

        if (btnContinuePayment != null) btnContinuePayment.setEnabled(!empty && !cancelInProgress);
        if (btnCancelOrder != null) btnCancelOrder.setEnabled(!empty && !cancelInProgress);

        if (empty) cancelInProgress = false;

        renderDineInRow();
    }

    /**
     * Menanyakan sekali berapa meja aktif yang dimiliki toko.
     *
     * <p>Kegagalan sengaja dibiarkan jadi "tidak diketahui" ({@code -1}),
     * bukan nol. Keduanya sama-sama berakhir di jalur nomor meja teks bebas
     * yang tidak pernah memblokir penjualan, tapi {@code -1} jujur mengatakan
     * bahwa jumlahnya belum diketahui alih-alih mengklaim toko tidak punya
     * meja. Lihat {@link DineInTableRule} untuk keputusan lengkapnya.
     */
    private void loadActiveTableCountOnce() {
        if (activeTableCount >= 0) return;
        if (!isRestaurantBusiness() || !canUseModule(RestaurantRepository.MODULE_TABLES)) {
            activeTableCount = 0;
            return;
        }

        String token = new com.valdker.pos.SessionManager(requireContext()).getToken();
        new RestaurantRepository(requireContext()).fetchTables(token,
                new RestaurantRepository.TablesCallback() {
                    @Override
                    public void onSuccess(@NonNull java.util.List<com.valdker.pos.restaurant.RestaurantTable> tables) {
                        if (!isAdded()) return;
                        // fetchTables() sudah menyaring is_active=false.
                        activeTableCount = tables.size();
                        Log.i(TAG, "Meja aktif=" + activeTableCount
                                + " -> mekanisme meja: "
                                + (activeTableCount > 0 ? "grid (table_id)" : "nomor meja teks bebas"));
                        renderDineInRow();
                    }

                    @Override
                    public void onError(int statusCode, @NonNull String message) {
                        Log.w(TAG, "Jumlah meja aktif tidak bisa dimuat (status=" + statusCode
                                + "): jatuh ke kolom nomor meja teks bebas. " + message);
                    }
                });
    }

    /**
     * Apakah tombol Meja ditampilkan.
     *
     * <p>Lebih longgar dari syarat pemblokiran di {@link DineInTableRule}
     * dengan sengaja: selama jumlah meja belum diketahui, tombolnya tetap muncul supaya tidak berkedip
     * masuk beberapa ratus milidetik setelah keranjang dibuka. Yang tidak boleh
     * optimistis adalah penjaganya - memblokir "Lanjut bayar" berdasarkan
     * tebakan bisa mengunci kasir pada toko yang memang belum punya meja.
     */
    private boolean tableButtonVisible() {
        return isRestaurantBusiness()
                && canUseModule(RestaurantRepository.MODULE_TABLES)
                && activeTableCount != 0;
    }

    /** Meja yang sudah dipilih untuk bill yang sedang aktif, kalau ada. */
    @Nullable
    private Long pickedTableId() {
        if (!isRestaurantBusiness()) return null;
        if (!canUseModule(RestaurantRepository.MODULE_TABLES)) return null;
        DraftLifecycleHost host = resolveDraftLifecycleHost();
        return host != null ? host.dineInTableIdForActiveDraft() : null;
    }

    /** Apakah daftar ini memuat item yang BUKAN dine-in. */
    private boolean hasNonDineIn(@NonNull List<CartItem> items) {
        for (CartItem it : items) {
            if (it != null && !CartManager.TYPE_DINE_IN.equals(normalizeType(it.orderType))) {
                return true;
            }
        }
        return false;
    }

    /** Apakah ada item dine-in di keranjang saat ini. */
    private boolean cartHasDineIn() {
        List<CartItem> items = cart != null ? cart.getItems() : null;
        if (items == null) return false;
        for (CartItem it : items) {
            if (it != null && CartManager.TYPE_DINE_IN.equals(normalizeType(it.orderType))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Baris meja/pelayan hanya hidup untuk shop restoran yang modulnya
     * diizinkan. Kedua tombol disembunyikan sendiri-sendiri karena "tables"
     * dan "waiters" adalah dua kunci modul terpisah dan bisa menyala tidak
     * bersamaan; barisnya sendiri hilang kalau dua-duanya mati.
     */
    private void renderDineInRow() {
        if (rowDineIn == null) return;

        boolean tablesOn = tableButtonVisible();
        boolean waitersOn = canUseModule(RestaurantRepository.MODULE_WAITERS);

        // Meja dan pelayan hanya berlaku untuk makan di tempat. Sebelumnya
        // baris ini tampil di setiap keranjang restoran, termasuk yang isinya
        // take-out semua - menawarkan "Meja: belum dipilih" pada pesanan
        // bungkus hanya membuat kasir menebak apakah itu wajib.
        if (!isRestaurantBusiness() || !cartHasDineIn() || (!tablesOn && !waitersOn)) {
            rowDineIn.setVisibility(View.GONE);
            return;
        }

        rowDineIn.setVisibility(View.VISIBLE);
        DraftLifecycleHost host = resolveDraftLifecycleHost();

        if (btnPickTable != null) {
            btnPickTable.setVisibility(tablesOn ? View.VISIBLE : View.GONE);
            String name = host != null ? host.dineInTableNameForActiveDraft() : null;
            btnPickTable.setText(getString(R.string.dinein_table_label) + ": "
                    + (name != null && !name.trim().isEmpty()
                            ? name.trim()
                            : getString(R.string.dinein_unset)));
        }

        if (btnPickWaiter != null) {
            btnPickWaiter.setVisibility(waitersOn ? View.VISIBLE : View.GONE);
            String name = host != null ? host.dineInWaiterNameForActiveDraft() : null;
            btnPickWaiter.setText(getString(R.string.dinein_waiter_label) + ": "
                    + (name != null && !name.trim().isEmpty()
                            ? name.trim()
                            : getString(R.string.dinein_unset)));
        }
    }

    private boolean canUseModule(@NonNull String moduleKey) {
        if (!isAdded()) return false;
        return new com.valdker.pos.SessionManager(requireContext()).canAccessModule(moduleKey);
    }

    @Override
    public void onTablePicked(@Nullable Long tableId, @Nullable String tableName) {
        DraftLifecycleHost host = resolveDraftLifecycleHost();
        if (host == null) {
            Log.w(TAG, "Meja tidak bisa disimpan: draft lifecycle host tidak tersedia.");
            return;
        }
        host.setDineInTableForActiveDraft(tableId, tableName);
        renderDineInRow();
    }

    @Override
    public void onWaiterPicked(@Nullable Long waiterId, @Nullable String waiterName) {
        DraftLifecycleHost host = resolveDraftLifecycleHost();
        if (host == null) {
            Log.w(TAG, "Pelayan tidak bisa disimpan: draft lifecycle host tidak tersedia.");
            return;
        }
        host.setDineInWaiterForActiveDraft(waiterId, waiterName);
        renderDineInRow();
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

        // Kolom keranjang tablet tidak pernah ditutup: ia bagian tetap dari
        // layar kasir, dan tidak ada entri back stack miliknya untuk di-pop.
        if (embeddedInPane) return;

        try {
            requireActivity().getSupportFragmentManager().popBackStack();
        } catch (Exception ignored) {
        }

        View overlay = requireActivity().findViewById(R.id.overlayContainer);
        if (overlay != null) overlay.setVisibility(View.GONE);
    }
}
