package com.valdker.pos.workshop;

import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.res.ColorStateList;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import com.valdker.pos.utils.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.graphics.Color;
import android.os.Build;
import android.view.Window;
import android.view.WindowManager;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.valdker.pos.ModuleRegistry;
import com.valdker.pos.R;
import com.valdker.pos.ui.common.SystemBars;
import com.valdker.pos.SessionManager;
import com.valdker.pos.cart.CartManager;
import com.valdker.pos.drafts.PosDraftEntity;
import com.valdker.pos.drafts.PosDraftItemEntity;
import com.valdker.pos.drafts.PosDraftMapper;
import com.valdker.pos.drafts.PosDraftRepository;
import com.valdker.pos.drafts.PosDraftSnapshot;
import com.valdker.pos.models.CartItem;
import com.valdker.pos.models.Product;
import com.valdker.pos.models.ServicePackageResponse;
import com.valdker.pos.models.Shop;
import com.valdker.pos.network.WorkshopModuleApi;
import com.valdker.pos.repositories.CheckoutConfigRepository;
import com.valdker.pos.repositories.MasterDataRepository;
import com.valdker.pos.money.Money;
import com.valdker.pos.print.ReceiptPayloadReader;
import com.valdker.pos.print.ReceiptContent;
import com.valdker.pos.print.ReceiptBuilder;
import com.valdker.pos.money.OrderTotals;
import com.valdker.pos.repositories.OfflineOrderRepository;
import com.valdker.pos.repositories.OrderRepository;
import com.valdker.pos.repositories.ProductRepository;
import com.valdker.pos.repositories.ServicePackageRepository;
import com.valdker.pos.repositories.ShopRepository;
import com.valdker.pos.shop.ShopEvents;
import com.valdker.pos.ui.checkout.BankAccountItem;
import com.valdker.pos.ui.checkout.NativeCheckoutDialogFragment;
import com.valdker.pos.ui.checkout.PaymentMethodItem;
import com.valdker.pos.utils.ErrorHandler;
import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WorkshopPOSFragment extends Fragment
        implements WorkshopWorkspaceAdapter.Listener, CartManager.Listener {

    private interface ReceiptPrintCallback {
        void onComplete(@NonNull com.valdker.pos.print.BluetoothPrinterManager.PrintResult result);
    }

    private static final String TAG = "WORKSHOP_POS";
    private static final long SHOP_HEADER_REFRESH_INTERVAL_MS = 5000L;
    private static final String POS_TYPE_WORKSHOP = "workshop";

    public interface WorkshopHostActions {
        void openOfflineOrdersFromUserMenu();
        void openPrivacyPolicyFromUserMenu();
        void requestCloseShiftFromUserMenu();
        void requestLogoutFromUserMenu();
    }

    public static final String REQUEST_KEY_WORKSHOP_ITEM = "request_workshop_item";
    public static final String BUNDLE_KEY_ITEM_ID = "item_id";
    public static final String BUNDLE_KEY_ITEM_NAME = "item_name";
    public static final String BUNDLE_KEY_ITEM_PRICE = "item_price";
    public static final String BUNDLE_KEY_ITEM_TYPE = "item_type";
    public static final String BUNDLE_KEY_ITEM_SKU = "item_sku";
    public static final String BUNDLE_KEY_ITEM_BARCODE = "item_barcode";
    public static final String BUNDLE_KEY_ITEM_IMAGE = "item_image";
    public static final String BUNDLE_KEY_ITEM_STOCK = "item_stock";

    private ProductRepository productRepository;
    private MasterDataRepository masterDataRepository;
    private OrderRepository orderRepository;
    private CheckoutConfigRepository checkoutConfigRepository;
    private ServicePackageRepository servicePackageRepository;
    private WorkshopModuleApi workshopModuleApi;
    private SessionManager sessionManager;
    private WorkshopHostActions hostActions;

    private final List<Product> workshopProducts = new ArrayList<>();
    private boolean productsLoaded = false;
    private boolean productsLoading = false;
    private boolean offlineNoticeShown = false;
    private boolean noLocalDataNoticeShown = false;
    private boolean checkoutOfflineNoticeShown = false;
    private boolean checkoutNoLocalDataNoticeShown = false;
    private boolean checkoutSubmitting = false;

    // Satu kunci idempotensi per draft, dipertahankan lintas percobaan supaya
    // retry manual memakai kunci yang sama dan dedup di server bisa bekerja.
    // Harus berupa peta, bukan satu slot: kasir bisa berpindah draft lalu
    // kembali, dan draft yang ditinggalkan wajib menemukan kunci lamanya utuh.
    private final Map<Long, String> pendingWorkshopClientOrderIds = new ConcurrentHashMap<>();

    private final List<PaymentMethodItem> checkoutPaymentMethods = new ArrayList<>();
    private final List<BankAccountItem> checkoutBankAccounts = new ArrayList<>();
    private boolean checkoutConfigLoaded = false;
    private boolean checkoutConfigLoading = false;
    private boolean shopHeaderLoading = false;
    private long lastShopHeaderRefreshAt = 0L;

    private TextView txtCustomerName;
    private TextView txtVehicleName;
    private TextView txtPlateNumber;
    private TextView txtMechanicName;
    private TextView txtWorkOrderCode;
    private TextView txtBookingInfo;
    private TextView txtVehicleSummary;
    private TextView txtWorkshopSummary;
    private View layoutWorkshopSummary;
    private ChipGroup layoutWorkshopChips;
    private Chip chipMechanicSummary;
    private Chip chipWorkOrderSummary;
    private Chip chipBookingSummary;
    private Spinner spVehicleType;
    private boolean bindingVehicleTypeSpinner = false;
    private Long selectedCustomerId;
    private Long selectedVehicleId;
    private Long selectedMechanicId;
    private Long selectedBookingId;
    private Long selectedWorkOrderId;
    private String selectedVehicleType;
    private String selectedVehicleName;
    private String selectedPlateNumber;
    private String selectedMechanicName;
    private String selectedBookingInfo;
    private String selectedWorkOrderInfo;

    private android.widget.ImageView imgLogo;
    private TextView tvBrand;
    private TextView tvShopAddress;
    private Chip chipAddDraft;
    private ChipGroup chipGroupDrafts;

    private TextView txtServiceTotal;
    private TextView txtPartsTotal;
    private TextView txtProductTotal;
    private TextView txtGrandTotal;

    private RecyclerView recyclerWorkspace;
    /** Keadaan kosong kolom item; hanya ada pada varian layar lebar. */
    @Nullable
    private View workshopItemsEmpty;
    private MaterialButton btnSelectCustomer;
    private MaterialButton btnSelectMechanic;
    private MaterialButton btnSelectWorkOrder;
    private MaterialButton btnSelectBooking;
    /**
     * Keempat tombol tambah adalah View, bukan MaterialButton.
     *
     * <p>Di implementasi 4 masing-masing berupa kartu berwarna berisi ikon di
     * atas teks - dua anak dalam satu kotak - dan MaterialButton hanya bisa
     * menampung satu teks dengan satu ikon di sampingnya. Yang dipakai kode di
     * sini hanya setOnClickListener, setEnabled, dan setAlpha, yang ketiganya
     * milik View, jadi tipenya diturunkan alih-alih memaksa bentuk tombolnya.
     */
    private View btnAddService;
    private View btnAddPackage;
    private View btnAddPart;
    private View btnAddProduct;
    private MaterialButton btnCheckout;
    private WorkshopWorkspaceAdapter adapter;
    private CartManager cartManager;
    @Nullable
    private PosDraftRepository posDraftRepository;
    private final ExecutorService draftExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<PosDraftEntity> posDrafts = new ArrayList<>();
    private final Map<Long, Integer> draftItemCounts = new HashMap<>();
    private long activeDraftId = 0L;
    private boolean loadingDraftFromRoom = false;

    private final BroadcastReceiver shopUpdatedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshShopHeaderFromSession();
        }
    };

    private final WorkshopHeader header = new WorkshopHeader();
    private final List<WorkshopCartItem> cartItems = new ArrayList<>();

    public static WorkshopPOSFragment newInstance() {
        return new WorkshopPOSFragment();
    }

    public WorkshopPOSFragment() {
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof WorkshopHostActions) {
            hostActions = (WorkshopHostActions) context;
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        hostActions = null;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_workshop_pos, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        applyWorkshopSystemBars(view);

        cartManager = CartManager.getInstance(requireContext());
        sessionManager = new SessionManager(requireContext());
        productRepository = new ProductRepository(requireContext());
        masterDataRepository = new MasterDataRepository(requireContext());
        orderRepository = new OrderRepository(requireContext());
        checkoutConfigRepository = new CheckoutConfigRepository(requireContext());
        servicePackageRepository = new ServicePackageRepository(requireContext());
        workshopModuleApi = new WorkshopModuleApi(requireContext());

        bindViews(view);
        setupHeader();
        setupDraftChips();
        loadShopHeaderFromApi(false);
        setupRecycler();
        setupButtons();
        setupUserMenu();
        setupFragmentResults();
        initDraftStorage();
        syncPendingOrdersIfOnline();
    }

    /**
     * Bilah sistem POS bengkel.
     *
     * <p>Yang dulu di sini: menghapus flag layar penuh warisan, memaksa ikon
     * bilah status jadi terang, memanggil {@code setStatusBarColor} ungu, lalu
     * menambahkan tinggi bilah status sebagai padding akar. Baris terakhir itu
     * bekerja, tapi warnanya tidak: sejak targetSdk 36 Android 15+ mengabaikan
     * setStatusBarColor, sehingga yang tampak di belakang bilah status adalah
     * latar abu-abu muda layar ini - dengan ikon terang di atasnya.
     *
     * <p>Sekarang strip ungu digambar layout sendiri lewat posHeaderScrim,
     * jadi hasilnya sama di setiap versi Android.
     */
    private void applyWorkshopSystemBars(@NonNull View root) {
        if (!isAdded()) return;

        SystemBars.apply(requireActivity());
        SystemBars.fitStatusScrim(root.findViewById(R.id.posHeaderScrim));
        SystemBars.padBottom(root.findViewById(R.id.workshopContent));
    }

    @Override
    public void onStart() {
        super.onStart();
        if (cartManager != null) {
            cartManager.addListener(this);
        }
        ContextCompat.registerReceiver(
                requireContext(),
                shopUpdatedReceiver,
                new IntentFilter(ShopEvents.ACTION_SHOP_UPDATED),
                ContextCompat.RECEIVER_NOT_EXPORTED
        );
    }

    @Override
    public void onStop() {
        super.onStop();
        if (cartManager != null) {
            cartManager.removeListener(this);
        }
        try {
            requireContext().unregisterReceiver(shopUpdatedReceiver);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        draftExecutor.shutdownNow();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshShopHeaderFromSession();
        loadShopHeaderFromApi(false);
    }

    private void bindViews(@NonNull View view) {
        imgLogo = view.findViewById(R.id.imgLogo);

        // Lencana tipe (gembok + "Workshop") dan strip tiga kartu aksi cepat
        // sengaja TIDAK dipasang. Lencananya hanya mengulang satu hal yang
        // tidak pernah berubah - satu login terikat pada satu jenis usaha -
        // dan kartu aksi cepatnya tidak pernah punya listener sama sekali,
        // jadi menekan Work order, Rezerva, atau Istoria benar-benar tidak
        // melakukan apa-apa. Ketiganya tetap bisa dicapai lewat menunya.
        tvBrand = view.findViewById(R.id.tvBrand);
        tvShopAddress = view.findViewById(R.id.tvShopAddress);
        chipAddDraft = view.findViewById(R.id.chipAddDraft);
        chipGroupDrafts = view.findViewById(R.id.chipGroupDrafts);

        txtCustomerName = view.findViewById(R.id.txtCustomerName);
        txtVehicleName = view.findViewById(R.id.txtVehicleName);
        txtPlateNumber = view.findViewById(R.id.txtPlateNumber);
        txtMechanicName = view.findViewById(R.id.txtMechanicName);
        txtWorkOrderCode = view.findViewById(R.id.txtWorkOrderCode);
        txtBookingInfo = view.findViewById(R.id.txtBookingInfo);
        txtVehicleSummary = view.findViewById(R.id.txtVehicleSummary);
        txtWorkshopSummary = view.findViewById(R.id.txtWorkshopSummary);
        layoutWorkshopSummary = view.findViewById(R.id.layoutWorkshopSummary);
        layoutWorkshopChips = view.findViewById(R.id.layoutWorkshopChips);
        chipMechanicSummary = view.findViewById(R.id.chipMechanicSummary);
        chipWorkOrderSummary = view.findViewById(R.id.chipWorkOrderSummary);
        chipBookingSummary = view.findViewById(R.id.chipBookingSummary);
        spVehicleType = view.findViewById(R.id.spVehicleType);

        txtServiceTotal = view.findViewById(R.id.txtServiceTotal);
        txtPartsTotal = view.findViewById(R.id.txtPartsTotal);
        txtProductTotal = view.findViewById(R.id.txtProductTotal);
        txtGrandTotal = view.findViewById(R.id.txtGrandTotal);

        recyclerWorkspace = view.findViewById(R.id.recyclerWorkspace);
        workshopItemsEmpty = view.findViewById(R.id.workshopItemsEmpty);
        btnSelectCustomer = view.findViewById(R.id.btnSelectCustomer);
        btnSelectMechanic = view.findViewById(R.id.btnSelectMechanic);
        btnSelectWorkOrder = view.findViewById(R.id.btnSelectWorkOrder);
        btnSelectBooking = view.findViewById(R.id.btnSelectBooking);
        btnAddService = view.findViewById(R.id.btnAddService);
        btnAddPackage = view.findViewById(R.id.btnAddPackage);
        btnAddPart = view.findViewById(R.id.btnAddPart);
        btnAddProduct = view.findViewById(R.id.btnAddProduct);
        btnCheckout = view.findViewById(R.id.btnCheckout);
    }

    /**
     * Memasang tombol "+" bon baru. Hanya itu.
     *
     * <p>Chip bon-nya sendiri tidak ada di layout dan tidak dibuat di sini:
     * seluruhnya dibangun {@link #renderDraftChips()} dari isi basis data.
     *
     * <p>Sebelumnya di sini terpasang tiga chip tetap berikut label contoh
     * "A - 3", "B - 12", dan "Walk-in - 1" - sisa kerangka rancangan yang
     * ikut terkirim ke pengguna. Kasir membuka aplikasi, melihat bon yang
     * tidak pernah ia buat, menekannya, lalu menemukan keranjang kosong.
     */
    private void setupDraftChips() {
        if (chipAddDraft == null) return;

        styleAddDraftChip(chipAddDraft);
        chipAddDraft.setOnClickListener(v -> createAndActivateDraft());
    }

    private void initDraftStorage() {
        if (!isAdded()) return;
        posDraftRepository = new PosDraftRepository(requireContext());
        loadDraftsAfterPruning();
    }

    /**
     * Pembacaan pertama: bon kosong yang tersisa dari sesi sebelumnya dibuang
     * lebih dulu, baru daftarnya ditampilkan.
     *
     * <p>Tanpa ini, satu kali tekan "+" yang tidak jadi dipakai meninggalkan
     * "B - 0" di baris bon untuk selamanya. Yang tersisa sesudah pembersihan
     * adalah bon aktif dan bon yang benar-benar ada isinya; selebihnya muncul
     * ketika kasir menekan "+".
     *
     * <p>Hanya di pembacaan pertama. Sesudah layar terbuka, bon kosong yang
     * baru saja dibuat kasir jelas masih dipakai.
     */
    private void loadDraftsAfterPruning() {
        PosDraftRepository repository = posDraftRepository;
        if (repository == null) return;

        draftExecutor.execute(() -> {
            try {
                PosDraftSnapshot snapshot =
                        repository.pruneEmptyInactiveDrafts(POS_TYPE_WORKSHOP);
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    applyDraftSnapshot(snapshot, true);
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to prune empty workshop drafts", e);
                loadDraftsFromRoom(true, null);
            }
        });
    }

    private void loadDraftsFromRoom(boolean loadItems, @Nullable String toastMessage) {
        PosDraftRepository repository = posDraftRepository;
        if (repository == null) return;

        draftExecutor.execute(() -> {
            try {
                PosDraftSnapshot snapshot = repository.loadSnapshot(POS_TYPE_WORKSHOP, loadItems);
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    applyDraftSnapshot(snapshot, loadItems);
                    if (!TextUtils.isEmpty(toastMessage)) {
                        Toast.makeText(requireContext(), toastMessage, Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to load workshop drafts", e);
            }
        });
    }

    private void applyDraftSnapshot(@NonNull PosDraftSnapshot snapshot, boolean loadItems) {
        posDrafts.clear();
        posDrafts.addAll(snapshot.drafts);
        draftItemCounts.clear();
        draftItemCounts.putAll(snapshot.itemCounts);
        activeDraftId = snapshot.activeDraft != null ? snapshot.activeDraft.id : 0L;

        renderDraftChips();

        if (loadItems) {
            loadWorkshopDraftItems(snapshot.items);
        }
    }

    private void renderDraftChips() {
        if (chipGroupDrafts == null) return;

        chipGroupDrafts.removeAllViews();
        for (int i = 0; i < posDrafts.size(); i++) {
            PosDraftEntity draft = posDrafts.get(i);
            Chip chip = createDraftChip();

            // Id dibangkitkan, bukan dipetakan ke chipDraftA/B/C seperti dulu.
            // Pemetaan itu hanya menjangkau tiga bon pertama, sehingga bon
            // keempat dan seterusnya tidak punya id sama sekali - padahal
            // ChipGroup memakai id untuk melacak mana yang sedang terpilih.
            chip.setId(View.generateViewId());

            boolean active = draft.id == activeDraftId;
            int count = draftItemCounts.containsKey(draft.id) ? draftItemCounts.get(draft.id) : 0;
            applyDraftChipStyle(chip, active, draft.name, count);
            chip.setOnClickListener(v -> activateDraft(draft.id, "Draft " + draft.name + " aktif"));
            chipGroupDrafts.addView(chip);
        }

        if (chipAddDraft != null) {
            chipAddDraft.setText("+");
            chipAddDraft.setMinWidth(dp(30));
            styleAddDraftChip(chipAddDraft);
            chipAddDraft.setOnClickListener(v -> createAndActivateDraft());
        }
    }

    /**
     * Chip bon memakai gaya bersama {@code Widget.Valora.FilterChip}, sama
     * dengan POS retail dan chip filter di modul lain. Sebelumnya tiga tempat
     * berbeda menuliskan sendiri tinggi, radius, dan enam warna hex chip ini,
     * dan ketiganya sudah menyimpang tipis satu sama lain.
     */
    @NonNull
    private Chip createDraftChip() {
        // Gaya chip datang dari view_draft_chip.xml, satu definisi yang juga
        // dipakai chip bon yang ditulis langsung di layout POS.
        return (Chip) LayoutInflater.from(requireContext())
                .inflate(R.layout.view_draft_chip, chipGroupDrafts, false);
    }

    private void applyDraftChipStyle(@NonNull Chip chip, boolean active, @NonNull String name, int count) {
        chip.setChecked(active);
        chip.setText((active ? "\u25CF " : "") + name + " \u2022 " + Math.max(0, count));
    }

    @Nullable
    private PosDraftEntity findActiveDraft() {
        for (PosDraftEntity draft : posDrafts) {
            if (draft != null && draft.id == activeDraftId) return draft;
        }
        return null;
    }

    private void activateDraft(long draftId, @NonNull String toastMessage) {
        PosDraftRepository repository = posDraftRepository;
        if (repository == null || draftId <= 0L) return;

        draftExecutor.execute(() -> {
            try {
                PosDraftSnapshot snapshot = repository.activateDraft(POS_TYPE_WORKSHOP, draftId, true);
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    applyDraftSnapshot(snapshot, true);
                    Toast.makeText(requireContext(), toastMessage, Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to activate workshop draft", e);
            }
        });
    }

    private void createAndActivateDraft() {
        PosDraftRepository repository = posDraftRepository;
        if (repository == null) return;

        draftExecutor.execute(() -> {
            try {
                PosDraftRepository.CreatedDraftResult result = repository.createAndActivateDraft(POS_TYPE_WORKSHOP);
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    applyDraftSnapshot(result.snapshot, true);
                    Toast.makeText(requireContext(), "Draft " + result.draftName + " aktif", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to create workshop draft", e);
            }
        });
    }

    private void loadWorkshopDraftItems(@Nullable List<PosDraftItemEntity> items) {
        if (cartManager == null) return;

        loadingDraftFromRoom = true;
        try {
            cartManager.clear();
            if (items != null) {
                for (PosDraftItemEntity item : items) {
                    CartItem cartItem = toCartItem(item);
                    if (cartItem != null) {
                        cartManager.add(cartItem);
                    }
                }
            }
            loadCartFromManager();
        } finally {
            mainHandler.post(() -> loadingDraftFromRoom = false);
        }
    }

    @Nullable
    private CartItem toCartItem(@Nullable PosDraftItemEntity item) {
        return PosDraftMapper.toCartItem(item);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void persistWorkshopCartToActiveDraft() {
        PosDraftRepository repository = posDraftRepository;
        long draftId = activeDraftId;
        if (repository == null || draftId <= 0L) return;

        List<WorkshopCartItem> snapshot = new ArrayList<>(cartItems);
        draftExecutor.execute(() -> {
            try {
                List<CartItem> cartSnapshot = new ArrayList<>();
                for (WorkshopCartItem item : snapshot) {
                    if (item == null) continue;
                    CartItem cartItem = new CartItem(
                            item.getProductId(),
                            item.getShopId(),
                            item.getName(),
                            item.getPrice(),
                            item.getImageUrl(),
                            item.getQuantity(),
                            "",
                            item.getItemType()
                    );
                    cartSnapshot.add(cartItem);
                }
                repository.replaceDraftItems(draftId, cartSnapshot);
                refreshDraftChipsFromRoom();
            } catch (Exception e) {
                Log.e(TAG, "Failed to save workshop draft cart", e);
            }
        });
    }

    private void refreshDraftChipsFromRoom() {
        PosDraftRepository repository = posDraftRepository;
        if (repository == null) return;
        try {
            PosDraftSnapshot snapshot = repository.loadSnapshot(POS_TYPE_WORKSHOP, false);
            mainHandler.post(() -> {
                if (!isAdded()) return;
                applyDraftSnapshot(snapshot, false);
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed to refresh workshop draft chips", e);
        }
    }

    private void completeActiveWorkshopDraftCheckout() {
        PosDraftRepository repository = posDraftRepository;
        long draftId = activeDraftId;
        if (repository == null || draftId <= 0L) {
            loadWorkshopDraftItems(new ArrayList<>());
            Log.i(TAG, "draft cleanup success pos=workshop draftId=" + draftId + " reason=no_active_draft");
            return;
        }

        draftExecutor.execute(() -> {
            try {
                PosDraftSnapshot snapshot = repository.cleanupActiveDraftAfterCheckout(POS_TYPE_WORKSHOP, draftId);
                Log.i(TAG, "draft cleanup success pos=workshop draftId=" + draftId);
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    applyDraftSnapshot(snapshot, true);
                });
            } catch (Exception e) {
                Log.e(TAG, "cleanup failed with error pos=workshop draftId=" + draftId, e);
            }
        });
    }

    private void styleAddDraftChip(@NonNull Chip chip) {
        chip.setChecked(false);
        chip.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#6204BF")));
        chip.setTextColor(Color.WHITE);
        chip.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    }

    private void setupHeader() {
        if (TextUtils.isEmpty(header.customerName)) {
            header.customerName = "Walk-in Customer";
        }
        if (TextUtils.isEmpty(header.vehicleName)) {
            header.vehicleName = "-";
        }
        if (TextUtils.isEmpty(header.vehicleTypeCode)) {
            header.vehicleTypeCode = "CAR";
        }
        if (TextUtils.isEmpty(header.plateNumber)) {
            header.plateNumber = "-";
        }
        if (TextUtils.isEmpty(header.status)) {
            header.status = "Draft";
        }
        selectedVehicleType = header.vehicleTypeCode;
        selectedVehicleName = header.vehicleName;
        selectedPlateNumber = header.plateNumber;

        refreshShopHeaderFromSession();

        updateHeaderUI();
        setupVehicleTypeSpinner();
    }

    private void setupVehicleTypeSpinner() {
        if (spVehicleType == null) return;

        selectVehicleTypeSpinner(header.vehicleTypeCode);
        spVehicleType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (bindingVehicleTypeSpinner) return;
                String code = getSelectedVehicleTypeCode();
                header.vehicleTypeCode = TextUtils.isEmpty(code) ? "CAR" : code;
                selectedVehicleType = header.vehicleTypeCode;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                if (TextUtils.isEmpty(header.vehicleTypeCode)) {
                    header.vehicleTypeCode = "CAR";
                }
            }
        });
    }

    private void refreshShopHeaderFromSession() {
        if (sessionManager == null) return;

        bindShopHeader(
                sessionManager.getShopName(),
                sessionManager.getShopAddress(),
                sessionManager.getShopLogo()
        );
    }

    private void loadShopHeaderFromApi(boolean force) {
        if (!isAdded() || sessionManager == null) return;

        String token = sessionManager.getToken();
        if (TextUtils.isEmpty(token)) return;

        long now = System.currentTimeMillis();
        if (!force && (shopHeaderLoading || now - lastShopHeaderRefreshAt < SHOP_HEADER_REFRESH_INTERVAL_MS)) {
            return;
        }

        shopHeaderLoading = true;
        lastShopHeaderRefreshAt = now;

        ShopRepository.fetchFirstShop(requireContext(), token, new ShopRepository.Callback() {
            @Override
            public void onSuccess(@NonNull Shop shop) {
                if (!isAdded()) return;

                shopHeaderLoading = false;

                sessionManager.updateShopProfile(
                        shop.name,
                        shop.address,
                        shop.logoUrl
                );

                bindShopHeader(
                        shop.name,
                        shop.address,
                        shop.logoUrl
                );
            }

            @Override
            public void onEmpty() {
                if (!isAdded()) return;
                shopHeaderLoading = false;
                Log.w(TAG, "Shop header empty from api/shop/me/");
            }

            @Override
            public void onError(@NonNull String message) {
                if (!isAdded()) return;
                shopHeaderLoading = false;
                Log.w(TAG, "Failed to load shop header: " + message);
                refreshShopHeaderFromSession();
            }
        });
    }

    private void bindShopHeader(@Nullable String shopName,
                                @Nullable String shopAddress,
                                @Nullable String logoUrl) {
        if (tvBrand != null) {
            tvBrand.setText(safeText(shopName, "Shop"));
        }

        if (tvShopAddress != null) {
            tvShopAddress.setText(safeText(shopAddress, "-"));
        }

        if (imgLogo == null) return;

        if (!TextUtils.isEmpty(logoUrl)) {
            Glide.with(this)
                    .load(logoUrl)
                    .placeholder(R.drawable.bg_logo_circle)
                    .error(R.drawable.bg_logo_circle)
                    .circleCrop()
                    .into(imgLogo);
        } else {
            imgLogo.setImageResource(R.drawable.bg_logo_circle);
        }
    }

    private void updateHeaderUI() {
        if (txtVehicleName != null) {
            txtVehicleName.setText(safeText(header.vehicleName, "-"));
        }
        if (txtPlateNumber != null) {
            txtPlateNumber.setText(safeText(header.plateNumber, "-"));
        }
        if (txtMechanicName != null) {
            txtMechanicName.setText(safeText(selectedMechanicName, "-"));
        }
        if (txtWorkOrderCode != null) {
            txtWorkOrderCode.setText(safeText(selectedWorkOrderInfo, "-"));
        }
        if (txtBookingInfo != null) {
            txtBookingInfo.setText(safeText(selectedBookingInfo, "-"));
        }
        renderWorkshopHeaderSummary();
        selectVehicleTypeSpinner(header.vehicleTypeCode);
    }

    private void renderWorkshopHeaderSummary() {
        if (txtCustomerName != null) {
            txtCustomerName.setText(buildWorkshopHeaderMainSummary());
        }
        if (txtVehicleSummary != null) {
            txtVehicleSummary.setVisibility(View.GONE);
        }
        if (txtWorkshopSummary != null) {
            txtWorkshopSummary.setVisibility(View.GONE);
        }

        boolean hasChip = false;
        hasChip |= renderWorkshopSummaryChip(
                chipMechanicSummary,
                getString(R.string.label_mechanic) + ": ",
                selectedMechanicName
        );
        hasChip |= renderWorkshopSummaryChip(
                chipWorkOrderSummary,
                "WO: ",
                selectedWorkOrderInfo
        );
        hasChip |= renderWorkshopSummaryChip(
                chipBookingSummary,
                getString(R.string.label_booking) + ": ",
                selectedBookingInfo
        );

        if (layoutWorkshopChips != null) {
            layoutWorkshopChips.setVisibility(hasChip ? View.VISIBLE : View.GONE);
        }
    }

    @NonNull
    private String buildWorkshopHeaderMainSummary() {
        String customerName = compactHeaderValue(header.customerName);
        if (customerName.isEmpty()) {
            customerName = getString(R.string.workshop_walk_in_customer);
        }
        return customerName + " • " + buildCompactVehicleSummaryText();
    }

    @NonNull
    private String buildCompactVehicleSummaryText() {
        String vehicleName = compactHeaderValue(header.vehicleName);
        String plateNumber = compactHeaderValue(header.plateNumber);
        boolean hasVehicle = !vehicleName.isEmpty();
        boolean hasPlate = !plateNumber.isEmpty();

        if (selectedVehicleId == null && !hasVehicle && !hasPlate) {
            return getString(R.string.workshop_vehicle_not_selected);
        }

        String vehicleType = normalizeVehicleTypeCode(header.vehicleTypeCode);
        StringBuilder out = new StringBuilder(vehicleType);
        if (hasVehicle) out.append(" • ").append(vehicleName);
        if (hasPlate) out.append(" • ").append(plateNumber);
        return out.toString();
    }

    private boolean renderWorkshopSummaryChip(@Nullable Chip chip,
                                              @NonNull String prefix,
                                              @Nullable String value) {
        if (chip == null) return false;

        String clean = compactHeaderValue(value);
        if (clean.isEmpty()) {
            chip.setVisibility(View.GONE);
            return false;
        }

        chip.setText(prefix + clean);
        chip.setVisibility(View.VISIBLE);
        return true;
    }

    @NonNull
    private String compactHeaderValue(@Nullable String value) {
        String clean = cleanDash(value);
        return "null".equalsIgnoreCase(clean) ? "" : clean;
    }

    @NonNull
    private String safeText(@Nullable String value, @NonNull String fallback) {
        return TextUtils.isEmpty(value) ? fallback : value;
    }

    @Nullable
    private String getSelectedVehicleTypeCode() {
        if (spVehicleType == null || spVehicleType.getSelectedItem() == null) {
            return normalizeVehicleTypeCode(header.vehicleTypeCode);
        }

        String selected = spVehicleType.getSelectedItem().toString();

        if ("Car".equalsIgnoreCase(selected)) {
            return "CAR";
        }

        if ("Motorcycle".equalsIgnoreCase(selected)) {
            return "MOTORCYCLE";
        }

        return normalizeVehicleTypeCode(header.vehicleTypeCode);
    }

    @NonNull
    private String normalizeVehicleTypeCode(@Nullable String value) {
        if (value == null) return "CAR";

        String normalized = value.trim().toUpperCase(Locale.US);
        if ("MOTORCYCLE".equals(normalized)) return "MOTORCYCLE";
        if ("MOTOR".equals(normalized) || "BIKE".equals(normalized)) return "MOTORCYCLE";
        if ("CAR".equals(normalized)) return "CAR";

        return "CAR";
    }

    private void selectVehicleTypeSpinner(@Nullable String vehicleTypeCode) {
        if (spVehicleType == null) return;

        String code = normalizeVehicleTypeCode(vehicleTypeCode);
        int target = "MOTORCYCLE".equals(code) ? 1 : 0;
        if (spVehicleType.getSelectedItemPosition() == target) return;

        bindingVehicleTypeSpinner = true;
        spVehicleType.setSelection(target);
        bindingVehicleTypeSpinner = false;
    }

    private void setupRecycler() {
        adapter = new WorkshopWorkspaceAdapter(this);

        recyclerWorkspace.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerWorkspace.setNestedScrollingEnabled(true);
        recyclerWorkspace.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        recyclerWorkspace.setHasFixedSize(false);
        recyclerWorkspace.setAdapter(adapter);
    }

    private void setupButtons() {
        applyWorkshopModuleVisibility();

        if (txtVehicleName != null) {
            txtVehicleName.setClickable(true);
            txtVehicleName.setFocusable(true);
            txtVehicleName.setOnClickListener(v -> {
                if (!ensureShiftIsOpen()) return;
                openWorkshopInfoDialog();
            });
        }
        if (txtPlateNumber != null) {
            txtPlateNumber.setClickable(true);
            txtPlateNumber.setFocusable(true);
            txtPlateNumber.setOnClickListener(v -> {
                if (!ensureShiftIsOpen()) return;
                openWorkshopInfoDialog();
            });
        }

        if (layoutWorkshopSummary != null) {
            layoutWorkshopSummary.setClickable(true);
            layoutWorkshopSummary.setFocusable(true);
            layoutWorkshopSummary.setOnClickListener(v -> {
                if (!ensureShiftIsOpen()) return;
                openWorkshopInfoDialog();
            });
        }
        if (txtCustomerName != null) {
            txtCustomerName.setClickable(true);
            txtCustomerName.setFocusable(true);
            txtCustomerName.setOnClickListener(v -> {
                if (!ensureShiftIsOpen()) return;
                openWorkshopInfoDialog();
            });
        }
        if (txtVehicleSummary != null) {
            txtVehicleSummary.setClickable(true);
            txtVehicleSummary.setFocusable(true);
            txtVehicleSummary.setOnClickListener(v -> {
                if (!ensureShiftIsOpen()) return;
                openWorkshopInfoDialog();
            });
        }
        if (txtWorkshopSummary != null) {
            txtWorkshopSummary.setClickable(true);
            txtWorkshopSummary.setFocusable(true);
            txtWorkshopSummary.setOnClickListener(v -> {
                if (!ensureShiftIsOpen()) return;
                openWorkshopInfoDialog();
            });
        }
        if (layoutWorkshopChips != null) {
            layoutWorkshopChips.setClickable(true);
            layoutWorkshopChips.setFocusable(true);
            layoutWorkshopChips.setOnClickListener(v -> {
                if (!ensureShiftIsOpen()) return;
                openWorkshopInfoDialog();
            });
        }
        if (chipMechanicSummary != null) {
            chipMechanicSummary.setOnClickListener(v -> {
                if (!ensureShiftIsOpen()) return;
                openWorkshopInfoDialog();
            });
        }
        if (chipWorkOrderSummary != null) {
            chipWorkOrderSummary.setOnClickListener(v -> {
                if (!ensureShiftIsOpen()) return;
                openWorkshopInfoDialog();
            });
        }
        if (chipBookingSummary != null) {
            chipBookingSummary.setOnClickListener(v -> {
                if (!ensureShiftIsOpen()) return;
                openWorkshopInfoDialog();
            });
        }

        btnSelectCustomer.setOnClickListener(v -> {
            if (!ensureShiftIsOpen()) return;
            openWorkshopInfoDialog();
        });

        if (btnSelectMechanic != null) {
            btnSelectMechanic.setOnClickListener(v -> {
                if (!ensureShiftIsOpen()) return;
                openWorkshopInfoDialog();
            });
        }
        if (txtMechanicName != null) {
            txtMechanicName.setOnClickListener(v -> {
                if (!ensureShiftIsOpen()) return;
                openWorkshopInfoDialog();
            });
        }

        if (btnSelectWorkOrder != null) {
            btnSelectWorkOrder.setOnClickListener(v -> {
                if (!ensureShiftIsOpen()) return;
                openWorkshopInfoDialog();
            });
        }
        if (txtWorkOrderCode != null) {
            txtWorkOrderCode.setOnClickListener(v -> {
                if (!ensureShiftIsOpen()) return;
                openWorkshopInfoDialog();
            });
        }

        if (btnSelectBooking != null) {
            btnSelectBooking.setOnClickListener(v -> {
                if (!ensureShiftIsOpen()) return;
                openWorkshopInfoDialog();
            });
        }
        if (txtBookingInfo != null) {
            txtBookingInfo.setOnClickListener(v -> {
                if (!ensureShiftIsOpen()) return;
                openWorkshopInfoDialog();
            });
        }

        btnAddService.setOnClickListener(v -> {
            if (!ensureShiftIsOpen()) return;
            openWorkshopItemPicker(WorkshopCartItem.TYPE_SERVICE);
        });

        if (btnAddPackage != null) {
            btnAddPackage.setOnClickListener(v -> {
                if (!ensureShiftIsOpen()) return;
                openServicePackagePicker();
            });
        }

        btnAddPart.setOnClickListener(v -> {
            if (!ensureShiftIsOpen()) return;
            openWorkshopItemPicker(WorkshopCartItem.TYPE_PART);
        });

        btnAddProduct.setOnClickListener(v -> {
            if (!ensureShiftIsOpen()) return;
            openWorkshopItemPicker(WorkshopCartItem.TYPE_PRODUCT);
        });

        btnCheckout.setOnClickListener(v -> {
            if (!ensureShiftIsOpen()) return;
            handleCheckout();
        });
    }

    private void setupUserMenu() {
        if (imgLogo != null) {
            imgLogo.setClickable(true);
            imgLogo.setFocusable(true);
            imgLogo.setOnClickListener(this::showUserPopup);
        }
    }

    private void applyWorkshopModuleVisibility() {
        boolean workshop = sessionManager != null && sessionManager.isWorkshop();
        setModuleControlEnabled(txtMechanicName, btnSelectMechanic,
                workshop && canAccessWorkshopModule(ModuleRegistry.MECHANICS));
        setModuleControlEnabled(txtWorkOrderCode, btnSelectWorkOrder,
                workshop && canAccessWorkshopModule(ModuleRegistry.WORK_ORDERS));
        setModuleControlEnabled(txtBookingInfo, btnSelectBooking,
                workshop && canAccessWorkshopModule(ModuleRegistry.BOOKINGS));
        if (btnAddPackage != null) {
            boolean enabled = workshop && canAccessWorkshopModule(ModuleRegistry.SERVICE_PACKAGES);
            btnAddPackage.setEnabled(enabled);
            btnAddPackage.setAlpha(enabled ? 1f : 0.45f);
        }
    }

    private boolean canAccessWorkshopModule(@NonNull String moduleKey) {
        return sessionManager == null || sessionManager.canAccessModule(moduleKey);
    }

    private boolean ensureWorkshopModule(@NonNull String moduleKey, @NonNull String label) {
        if (sessionManager != null && !sessionManager.isWorkshop()) {
            Toast.makeText(requireContext(), label + " hanya tersedia untuk bisnis Workshop.", Toast.LENGTH_LONG).show();
            return false;
        }
        if (!canAccessWorkshopModule(moduleKey)) {
            Toast.makeText(requireContext(), "Anda tidak punya akses ke module " + label + ".", Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    private void setModuleControlEnabled(@Nullable TextView textView,
                                         @Nullable MaterialButton button,
                                         boolean enabled) {
        if (textView != null) {
            textView.setEnabled(enabled);
            textView.setAlpha(enabled ? 1f : 0.45f);
        }
        if (button != null) {
            button.setEnabled(enabled);
            button.setAlpha(enabled ? 1f : 0.45f);
        }
    }

    private void showUserPopup(@NonNull View anchor) {
        Context context = requireContext();

        View popupView = LayoutInflater.from(context).inflate(R.layout.popup_user_menu, null, false);

        TextView tvUserName = popupView.findViewById(R.id.tvUserName);
        TextView tvUserRole = popupView.findViewById(R.id.tvUserRole);

        TextView btnOfflineOrders = popupView.findViewById(R.id.btnOfflineOrders);
        TextView btnPrivacy = popupView.findViewById(R.id.btnPrivacy);
        TextView btnCloseShift = popupView.findViewById(R.id.btnCloseShift);
        TextView btnLogout = popupView.findViewById(R.id.btnLogout);

        String fullName = sessionManager.getFullName();
        String username = sessionManager.getUsername();
        String role = sessionManager.getRole();

        if (TextUtils.isEmpty(fullName)) {
            fullName = username;
        }
        if (TextUtils.isEmpty(fullName)) {
            fullName = "User";
        }
        if (TextUtils.isEmpty(role)) {
            role = "cashier";
        }

        tvUserName.setText(fullName);
        tvUserRole.setText(role);

        boolean canCloseShift = sessionManager != null
                && sessionManager.isShiftOpen()
                && sessionManager.getShiftId() > 0;
        btnCloseShift.setEnabled(canCloseShift);
        btnCloseShift.setAlpha(canCloseShift ? 1f : 0.45f);

        final PopupWindow popupWindow = new PopupWindow(
                popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );

        popupWindow.setElevation(12f);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setFocusable(true);

        popupView.measure(
                View.MeasureSpec.UNSPECIFIED,
                View.MeasureSpec.UNSPECIFIED
        );

        int popupWidth = popupView.getMeasuredWidth();

        anchor.post(() -> {
            int xOff = -(popupWidth - anchor.getWidth());
            popupWindow.showAsDropDown(anchor, xOff, 12);
        });

        btnOfflineOrders.setOnClickListener(v -> {
            popupWindow.dismiss();
            if (hostActions != null) {
                hostActions.openOfflineOrdersFromUserMenu();
            } else {
                Toast.makeText(requireContext(), "Offline Orders unavailable", Toast.LENGTH_SHORT).show();
            }
        });

        btnPrivacy.setOnClickListener(v -> {
            popupWindow.dismiss();
            if (hostActions != null) {
                hostActions.openPrivacyPolicyFromUserMenu();
            } else {
                Toast.makeText(requireContext(), "Privacy Policy unavailable", Toast.LENGTH_SHORT).show();
            }
        });

        btnCloseShift.setOnClickListener(v -> {
            popupWindow.dismiss();
            if (hostActions != null) {
                hostActions.requestCloseShiftFromUserMenu();
            } else {
                Toast.makeText(requireContext(), "Close Shift unavailable", Toast.LENGTH_SHORT).show();
            }
        });

        btnLogout.setOnClickListener(v -> {
            popupWindow.dismiss();
            if (hostActions != null) {
                hostActions.requestLogoutFromUserMenu();
            } else {
                Toast.makeText(requireContext(), "Logout unavailable", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupFragmentResults() {
        getParentFragmentManager().setFragmentResultListener(
                REQUEST_KEY_WORKSHOP_ITEM,
                getViewLifecycleOwner(),
                (requestKey, result) -> {
                    int itemId = result.getInt(BUNDLE_KEY_ITEM_ID, -1);
                    String itemName = result.getString(BUNDLE_KEY_ITEM_NAME, "");
                    double price = result.getDouble(BUNDLE_KEY_ITEM_PRICE, 0d);
                    String itemType = result.getString(BUNDLE_KEY_ITEM_TYPE, WorkshopCartItem.TYPE_PRODUCT);
                    String sku = result.getString(BUNDLE_KEY_ITEM_SKU, "");
                    String barcode = result.getString(BUNDLE_KEY_ITEM_BARCODE, "");
                    String image = result.getString(BUNDLE_KEY_ITEM_IMAGE, "");
                    int stock = result.getInt(BUNDLE_KEY_ITEM_STOCK, 0);

                    if (itemId <= 0 || TextUtils.isEmpty(itemName)) {
                        Toast.makeText(requireContext(), "Item workshop tidak valid", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    addSelectedItemToCart(
                            itemId,
                            0,
                            itemName,
                            itemType,
                            price,
                            sku,
                            barcode,
                            image,
                            stock
                    );
                }
        );
    }

    private void openWorkshopItemPicker(String itemType) {
        ensureWorkshopProductsLoaded(() -> showWorkshopItemPicker(itemType));
    }

    private void ensureWorkshopProductsLoaded(@NonNull Runnable onReady) {
        if (productsLoaded) {
            onReady.run();
            return;
        }

        if (productsLoading) {
            Toast.makeText(requireContext(), "Sedang memuat item...", Toast.LENGTH_SHORT).show();
            return;
        }

        productsLoading = true;

        String token = sessionManager.getToken();
        if (token == null || token.trim().isEmpty()) {
            productsLoading = false;
            Toast.makeText(requireContext(), getString(R.string.error_session_expired), Toast.LENGTH_SHORT).show();
            return;
        }

        final boolean[] pickerOpened = {false};

        masterDataRepository.loadProductsRoomFirst(token, "all", new MasterDataRepository.ProductsCallback() {
            @Override
            public void onLocalProducts(@NonNull List<Product> products) {
                if (!isAdded()) return;

                workshopProducts.clear();
                workshopProducts.addAll(products);

                if (!products.isEmpty()) {
                    productsLoading = false;
                    productsLoaded = true;
                    pickerOpened[0] = true;
                    onReady.run();
                }
            }

            @Override
            public void onRemoteProducts(@NonNull List<Product> products) {
                if (!isAdded()) return;

                productsLoading = false;
                productsLoaded = true;
                offlineNoticeShown = false;
                noLocalDataNoticeShown = false;

                workshopProducts.clear();
                workshopProducts.addAll(products);

                if (!pickerOpened[0]) {
                    pickerOpened[0] = true;
                    onReady.run();
                }
            }

            @Override
            public void onNoInternet(@NonNull List<Product> localProducts) {
                if (!isAdded()) return;

                productsLoading = false;
                productsLoaded = !workshopProducts.isEmpty();
                if (workshopProducts.isEmpty() && localProducts.isEmpty()) {
                    showNoLocalDataNoticeOnce();
                    return;
                }
                showOfflineNoticeOnce();

                if (!pickerOpened[0]) {
                    pickerOpened[0] = true;
                    onReady.run();
                }
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                if (!isAdded()) return;

                productsLoading = false;
                productsLoaded = !workshopProducts.isEmpty();

                if (!pickerOpened[0] && !workshopProducts.isEmpty()) {
                    pickerOpened[0] = true;
                    onReady.run();
                    return;
                }

                ErrorHandler.handleApiError(requireContext(), "Gagal memuat item: " + message);
            }
        });
    }

    private void showOfflineNoticeOnce() {
        if (offlineNoticeShown) return;
        offlineNoticeShown = true;
        Toast.makeText(
                requireContext(),
                MasterDataRepository.MESSAGE_NO_INTERNET_SHOWING_LOCAL,
                Toast.LENGTH_SHORT
        ).show();
    }

    private void showNoLocalDataNoticeOnce() {
        if (noLocalDataNoticeShown) return;
        noLocalDataNoticeShown = true;
        Toast.makeText(
                requireContext(),
                MasterDataRepository.MESSAGE_NO_LOCAL_POS_DATA,
                Toast.LENGTH_SHORT
        ).show();
    }

    private boolean ensureShiftIsOpen() {
        boolean shiftOpen = sessionManager != null && sessionManager.isShiftOpen();

        if (!shiftOpen) {
            Toast.makeText(requireContext(),
                    "Shift belum dibuka. Silakan open shift terlebih dahulu.",
                    Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void showWorkshopItemPicker(@NonNull String itemType) {
        List<Product> filtered = new ArrayList<>();
        String normalizedTarget = CartItem.normalizeItemType(itemType);

        for (Product product : workshopProducts) {
            if (product == null) continue;
            if (!product.isActive) continue;

            String normalizedProductType = CartItem.normalizeItemType(product.itemType);
            if (TextUtils.equals(normalizedTarget, normalizedProductType)) {
                filtered.add(product);
            }
        }

        if (filtered.isEmpty()) {
            String label;
            switch (normalizedTarget) {
                case CartItem.ITEM_TYPE_SERVICE:
                    label = "service";
                    break;
                case CartItem.ITEM_TYPE_PART:
                    label = "part";
                    break;
                case CartItem.ITEM_TYPE_PRODUCT:
                default:
                    label = "product";
                    break;
            }

            Toast.makeText(requireContext(), "Belum ada data " + label, Toast.LENGTH_SHORT).show();
            return;
        }

        String title;
        switch (normalizedTarget) {
            case CartItem.ITEM_TYPE_SERVICE:
                title = "Pilih Service";
                break;
            case CartItem.ITEM_TYPE_PART:
                title = "Pilih Part";
                break;
            case CartItem.ITEM_TYPE_PRODUCT:
            default:
                title = "Pilih Product";
                break;
        }

        String[] labels = new String[filtered.size()];
        for (int i = 0; i < filtered.size(); i++) {
            Product p = filtered.get(i);

            String stockText = "";
            if (!CartItem.ITEM_TYPE_SERVICE.equals(normalizedTarget)) {
                stockText = " - Stock: " + p.stock;
            }

            labels[i] = safeText(p.name, "-") + " - " + formatMoney(p.price) + stockText;
        }

        new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setItems(labels, (dialog, which) -> {
                    Product selected = filtered.get(which);

                    int itemId;
                    try {
                        itemId = Integer.parseInt(selected.id);
                    } catch (Exception e) {
                        Toast.makeText(requireContext(), "ID item tidak valid", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    int shopId = selected.shopId > 0
                            ? selected.shopId
                            : (selected.shop_id > 0 ? selected.shop_id : sessionManager.getShopId());

                    addSelectedItemToCart(
                            itemId,
                            shopId,
                            safeText(selected.name, ""),
                            normalizedTarget,
                            selected.price,
                            selected.sku,
                            selected.barcode,
                            !TextUtils.isEmpty(selected.imageUrl) ? selected.imageUrl : selected.image_url,
                            selected.stock
                    );
                })
                .setNegativeButton(getString(R.string.action_cancel), null)
                .show();
    }

    private void addSelectedItemToCart(int itemId,
                                       int shopId,
                                       @NonNull String itemName,
                                       @NonNull String itemType,
                                       double price,
                                       @Nullable String sku,
                                       @Nullable String barcode,
                                       @Nullable String imageUrl,
                                       int stock) {
        if (cartManager == null) return;

        String normalizedType = WorkshopCartItem.normalizeItemType(itemType);

        if (WorkshopCartItem.TYPE_PART.equals(normalizedType)
                || WorkshopCartItem.TYPE_PRODUCT.equals(normalizedType)) {
            if (stock <= 0) {
                Toast.makeText(requireContext(), "Stock item kosong", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        CartItem existing = findExistingCartItem(itemId, normalizedType);

        if (existing != null) {
            int nextQty = existing.qty + 1;

            if ((WorkshopCartItem.TYPE_PART.equals(normalizedType)
                    || WorkshopCartItem.TYPE_PRODUCT.equals(normalizedType))
                    && stock > 0
                    && nextQty > stock) {
                Toast.makeText(requireContext(), "Qty melebihi stock tersedia", Toast.LENGTH_SHORT).show();
                return;
            }

            cartManager.setQty(existing.productId, existing.itemType, nextQty);
        } else {
            CartItem cartItem = new CartItem();
            cartItem.productId = itemId;
            cartItem.shopId = shopId;
            cartItem.name = itemName;
            cartItem.price = price;
            cartItem.imageUrl = imageUrl != null ? imageUrl : "";
            cartItem.qty = 1;
            cartItem.orderType = "";
            cartItem.itemType = CartItem.normalizeItemType(normalizedType);
            cartManager.add(cartItem);
        }

        Toast.makeText(requireContext(), itemName + " ditambahkan", Toast.LENGTH_SHORT).show();
        loadCartFromManager();
        persistWorkshopCartToActiveDraft();
    }

    @Nullable
    private CartItem findExistingCartItem(int productId, @NonNull String itemType) {
        if (cartManager == null) return null;

        List<CartItem> items = cartManager.getItems();
        if (items == null) return null;

        String normalizedType = CartItem.normalizeItemType(itemType);

        for (CartItem item : items) {
            if (item.productId == productId
                    && TextUtils.equals(CartItem.normalizeItemType(item.itemType), normalizedType)) {
                return item;
            }
        }
        return null;
    }

    @Nullable
    private CartItem findExistingServicePackageCartItem(int servicePackageId) {
        if (cartManager == null || servicePackageId <= 0) return null;

        List<CartItem> items = cartManager.getItems();
        if (items == null) return null;

        for (CartItem item : items) {
            if (item.servicePackageId == servicePackageId) {
                return item;
            }
        }
        return null;
    }

    private static class WorkshopSelectionDraft {
        @Nullable Long customerId;
        @NonNull String customerName = "Walk-in Customer";
        @Nullable Long vehicleId;
        @NonNull String vehicleTypeCode = "CAR";
        @NonNull String vehicleName = "";
        @NonNull String plateNumber = "";
        @Nullable Long mechanicId;
        @NonNull String mechanicName = "";
        @Nullable Long bookingId;
        @NonNull String bookingInfo = "";
        @Nullable Long workOrderId;
        @NonNull String workOrderInfo = "";
    }

    @NonNull
    private WorkshopSelectionDraft currentWorkshopSelectionDraft() {
        WorkshopSelectionDraft draft = new WorkshopSelectionDraft();
        draft.customerId = selectedCustomerId;
        draft.customerName = safeText(header.customerName, "Walk-in Customer");
        draft.vehicleId = selectedVehicleId;
        draft.vehicleTypeCode = normalizeVehicleTypeCode(header.vehicleTypeCode);
        draft.vehicleName = cleanDash(selectedVehicleName != null ? selectedVehicleName : header.vehicleName);
        draft.plateNumber = cleanDash(selectedPlateNumber != null ? selectedPlateNumber : header.plateNumber);
        draft.mechanicId = selectedMechanicId;
        draft.mechanicName = cleanDash(selectedMechanicName);
        draft.bookingId = selectedBookingId;
        draft.bookingInfo = cleanDash(selectedBookingInfo);
        draft.workOrderId = selectedWorkOrderId;
        draft.workOrderInfo = cleanDash(selectedWorkOrderInfo);
        return draft;
    }

    private void applyWorkshopSelectionDraft(@NonNull WorkshopSelectionDraft draft) {
        selectedCustomerId = draft.customerId;
        header.customerId = draft.customerId != null ? draft.customerId.intValue() : 0;
        header.customerName = safeText(draft.customerName, "Walk-in Customer");

        selectedVehicleId = draft.vehicleId;
        selectedVehicleType = normalizeVehicleTypeCode(draft.vehicleTypeCode);
        selectedVehicleName = cleanDash(draft.vehicleName);
        selectedPlateNumber = cleanDash(draft.plateNumber);
        header.vehicleTypeCode = selectedVehicleType;
        header.vehicleName = TextUtils.isEmpty(selectedVehicleName) ? "-" : selectedVehicleName;
        header.plateNumber = TextUtils.isEmpty(selectedPlateNumber) ? "-" : selectedPlateNumber;

        selectedMechanicId = draft.mechanicId;
        selectedMechanicName = cleanDash(draft.mechanicName);
        selectedBookingId = draft.bookingId;
        selectedBookingInfo = cleanDash(draft.bookingInfo);
        selectedWorkOrderId = draft.workOrderId;
        selectedWorkOrderInfo = cleanDash(draft.workOrderInfo);

        updateHeaderUI();
    }

    /**
     * Lembar "Troka": memilih pelanggan, kendaraan, mekanik, work order, dan
     * booking untuk bon yang sedang dibuka.
     *
     * <p>Dulu ini AlertDialog di tengah layar yang seluruh isinya dirakit dari
     * kode - sebelas View dibuat dengan {@code new TextView(...)}, masing-masing
     * diberi ukuran dan warna hex sendiri. Sekarang isinya ada di
     * {@code sheet_workshop_info.xml} dan muncul sebagai bottom sheet.
     *
     * <p>Alasannya bukan selera. Isinya tiga bagian dengan kolom isian; sebuah
     * dialog tengah pada ponsel lanskap menyisakan tinggi di bawah 300dp untuk
     * semuanya, sehingga tombol Simpan terdorong ke luar layar. Lembar bawah
     * boleh setinggi 88% layar dan menggulir di dalamnya - dan ia muncul di
     * tempat ibu jari kasir sudah berada.
     */
    private void openWorkshopInfoDialog() {
        if (!isAdded()) return;

        WorkshopSelectionDraft draft = currentWorkshopSelectionDraft();

        View sheet = LayoutInflater.from(requireContext())
                .inflate(R.layout.sheet_workshop_info, null, false);

        TextView customerValue = sheet.findViewById(R.id.txtSheetCustomer);
        TextView mechanicValue = sheet.findViewById(R.id.txtSheetMechanic);
        TextView workOrderValue = sheet.findViewById(R.id.txtSheetWorkOrder);
        TextView bookingValue = sheet.findViewById(R.id.txtSheetBooking);
        EditText vehicleInput = sheet.findViewById(R.id.etSheetVehicleName);
        EditText plateInput = sheet.findViewById(R.id.etSheetPlate);
        TextView typeCar = sheet.findViewById(R.id.chipVehCar);
        TextView typeMotorcycle = sheet.findViewById(R.id.chipVehMotorcycle);

        customerValue.setText(draft.customerName);
        mechanicValue.setText(displayOrDash(draft.mechanicName));
        workOrderValue.setText(displayOrDash(draft.workOrderInfo));
        bookingValue.setText(displayOrDash(draft.bookingInfo));
        vehicleInput.setText(draft.vehicleName);
        plateInput.setText(draft.plateNumber);

        // Jenis kendaraan: dua sel yang keduanya terlihat, bukan Spinner.
        // Yang terpilih diberi latar aksen; yang lain transparan.
        final String[] typeCode = {normalizeVehicleTypeCode(draft.vehicleTypeCode)};
        Runnable paintType = () -> {
            boolean motor = "MOTORCYCLE".equals(typeCode[0]);
            // Yang terpilih memakai kartu ungu pucat, yang lain kartu netral.
            // Dua keadaan itu harus berbeda pada WARNA LATAR, bukan hanya pada
            // warna teks: pada layar yang dilihat sambil bergerak, perbedaan
            // warna teks 13sp terlalu halus untuk terbaca sekilas.
            typeCar.setBackgroundResource(motor
                    ? R.drawable.bg_pos_ghost_row : R.drawable.bg_pos_add_accent);
            typeCar.setTextColor(ContextCompat.getColor(requireContext(), motor
                    ? R.color.pos_ink : R.color.pos_accent_deep));
            typeMotorcycle.setBackgroundResource(motor
                    ? R.drawable.bg_pos_add_accent : R.drawable.bg_pos_ghost_row);
            typeMotorcycle.setTextColor(ContextCompat.getColor(requireContext(), motor
                    ? R.color.pos_accent_deep : R.color.pos_ink));
        };
        paintType.run();
        typeCar.setOnClickListener(v -> {
            typeCode[0] = "CAR";
            paintType.run();
        });
        typeMotorcycle.setOnClickListener(v -> {
            typeCode[0] = "MOTORCYCLE";
            paintType.run();
        });

        sheet.findViewById(R.id.rowSheetCustomer).setOnClickListener(
                v -> openCustomerPickerForDialog(draft, customerValue));
        sheet.findViewById(R.id.rowSheetMechanic).setOnClickListener(
                v -> openMechanicPickerForDialog(draft, mechanicValue));
        sheet.findViewById(R.id.rowSheetWorkOrder).setOnClickListener(
                v -> openWorkOrderPickerForDialog(draft, workOrderValue));
        sheet.findViewById(R.id.rowSheetBooking).setOnClickListener(
                v -> openBookingPickerForDialog(draft, bookingValue));

        com.google.android.material.bottomsheet.BottomSheetDialog dialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(requireContext());
        dialog.setContentView(sheet);

        // Pemilih kendaraan mengisi kembali nama, pelat, dan jenisnya.
        vehicleInput.setOnClickListener(null);
        sheet.findViewById(R.id.gridVehicleType).setTag(typeCode);

        sheet.findViewById(R.id.btnSheetClose).setOnClickListener(v -> dialog.dismiss());
        sheet.findViewById(R.id.btnSheetSave).setOnClickListener(v -> {
            draft.vehicleTypeCode = typeCode[0];
            draft.vehicleName = cleanDash(vehicleInput.getText() != null
                    ? vehicleInput.getText().toString() : "");
            draft.plateNumber = cleanDash(plateInput.getText() != null
                    ? plateInput.getText().toString() : "");
            applyWorkshopSelectionDraft(draft);
            dialog.dismiss();
        });

        // Lembar dibuka penuh sejak awal: separuh terbuka menyembunyikan
        // bagian 2 dan 3, dan kasir tidak punya petunjuk bahwa keduanya ada.
        dialog.setOnShowListener(d -> {
            View parent = dialog.findViewById(
                    com.google.android.material.R.id.design_bottom_sheet);
            if (parent != null) {
                com.google.android.material.bottomsheet.BottomSheetBehavior<View> behavior =
                        com.google.android.material.bottomsheet.BottomSheetBehavior.from(parent);
                behavior.setState(
                        com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
        });

        dialog.show();
    }


    private TextView buildDialogFieldLabel(@NonNull String text) {
        TextView label = new TextView(requireContext());
        label.setText(text);
        label.setTextColor(Color.parseColor("#64748B"));
        label.setTextSize(11f);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        return label;
    }


    private LinearLayout.LayoutParams dialogCardLayoutParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.topMargin = dp(6);
        return lp;
    }

    @NonNull
    private MaterialCardView buildDialogFieldCard() {
        MaterialCardView card = new MaterialCardView(requireContext());
        card.setCardBackgroundColor(Color.parseColor("#F8FAFC"));
        card.setCardElevation(0f);
        // Kartu ini hidup di dalam dialog, jadi sudutnya ikut radius popup.
        card.setRadius(getResources().getDimensionPixelSize(R.dimen.radius_dialog));
        card.setStrokeColor(Color.parseColor("#E2E8F0"));
        card.setStrokeWidth(dp(1));
        card.setContentPadding(dp(12), dp(8), dp(10), dp(8));
        return card;
    }





    private MaterialButton buildSmallDialogButton(@NonNull String text) {
        MaterialButton button = new MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(text);
        button.setTextSize(11f);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setMinHeight(dp(34));
        button.setMinWidth(dp(48));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setTextColor(Color.parseColor("#6204BF"));
        button.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#BB80F4")));
        button.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#F5ECFE")));
        button.setCornerRadius(getResources().getDimensionPixelSize(R.dimen.radius_dialog));
        return button;
    }


    private void openCustomerPickerForDialog(@NonNull WorkshopSelectionDraft draft,
                                             @NonNull TextView customerValue) {
        CustomerPickerDialog dialog = CustomerPickerDialog.newInstance();
        dialog.setListener(customer -> {
            Long previousCustomerId = draft.customerId;
            if (customer.id > 0) {
                draft.customerId = (long) customer.id;
                draft.customerName = safeText(customer.name, "Customer #" + customer.id);
            } else {
                draft.customerId = null;
                draft.customerName = "Walk-in Customer";
            }

            if ((previousCustomerId == null && draft.customerId != null)
                    || (previousCustomerId != null && !previousCustomerId.equals(draft.customerId))) {
                draft.vehicleId = null;
                draft.vehicleName = "";
                draft.plateNumber = "";
                draft.bookingId = null;
                draft.bookingInfo = "";
                draft.workOrderId = null;
                draft.workOrderInfo = "";
            }
            customerValue.setText(draft.customerName);
        });
        dialog.show(getParentFragmentManager(), "workshop_customer_picker");
    }

    private void openVehiclePickerForDialog(@NonNull WorkshopSelectionDraft draft,
                                            @NonNull EditText vehicleInput,
                                            @NonNull EditText plateInput,
                                            @Nullable Runnable onTypeChanged) {
        if (!ensureWorkshopModule(ModuleRegistry.VEHICLES, getString(R.string.menu_vehicles))) return;

        AlertDialog loading = showLoadingDialog("Memuat kendaraan...");
        Map<String, String> query = new HashMap<>();
        if (draft.customerId != null && draft.customerId > 0) {
            query.put("customer", String.valueOf(draft.customerId));
        }
        listWorkshopEndpoint("api/workshop/vehicles/", query, new WorkshopListCallback() {
            @Override
            public void onSuccess(@NonNull JSONArray array) {
                if (!isAdded()) return;
                loading.dismiss();

                List<JSONObject> rows = filterByCustomer(array, draft.customerId);
                if (rows.isEmpty()) {
                    showEmptyDialog("Vehicles", "Tidak ada kendaraan untuk customer ini.");
                    return;
                }

                String[] labels = new String[rows.size()];
                for (int i = 0; i < rows.size(); i++) {
                    labels[i] = vehicleLabel(rows.get(i));
                }

                new AlertDialog.Builder(requireContext())
                        .setTitle(getString(R.string.select_vehicle))
                        .setItems(labels, (dialog, which) -> {
                            JSONObject row = rows.get(which);
                            draft.vehicleId = longOrNull(row.opt("id"));
                            draft.vehicleTypeCode = normalizeVehicleTypeCode(firstNonEmpty(row.optString("vehicle_type", ""), row.optString("type", "")));
                            draft.vehicleName = buildVehicleName(row);
                            draft.plateNumber = firstNonEmpty(row.optString("plate_number", ""), row.optString("plate", ""));
                            vehicleInput.setText(draft.vehicleName);
                            plateInput.setText(draft.plateNumber);
                            if (onTypeChanged != null) onTypeChanged.run();
                        })
                        .setNegativeButton(getString(R.string.action_cancel), null)
                        .show();
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                if (!isAdded()) return;
                loading.dismiss();
                showErrorDialog("Gagal memuat kendaraan", message);
            }
        });
    }

    private void openMechanicPickerForDialog(@NonNull WorkshopSelectionDraft draft,
                                             @NonNull TextView mechanicValue) {
        if (!ensureWorkshopModule(ModuleRegistry.MECHANICS, getString(R.string.menu_mechanics))) return;

        AlertDialog loading = showLoadingDialog("Memuat mechanic...");
        Map<String, String> query = new HashMap<>();
        query.put("is_active", "true");
        listWorkshopEndpoint("api/workshop/mechanics/", query, new WorkshopListCallback() {
            @Override
            public void onSuccess(@NonNull JSONArray array) {
                if (!isAdded()) return;
                loading.dismiss();

                List<JSONObject> rows = activeRows(array);
                if (rows.isEmpty()) {
                    showEmptyDialog("Mechanics", "Tidak ada mechanic aktif.");
                    return;
                }

                String[] labels = new String[rows.size()];
                for (int i = 0; i < rows.size(); i++) {
                    JSONObject row = rows.get(i);
                    labels[i] = firstNonEmpty(row.optString("name", ""), "Mechanic #" + row.optInt("id", 0))
                            + optionalSuffix(row, "specialty");
                }

                new AlertDialog.Builder(requireContext())
                        .setTitle(getString(R.string.select_mechanic))
                        .setItems(labels, (dialog, which) -> {
                            JSONObject selected = rows.get(which);
                            draft.mechanicId = longOrNull(selected.opt("id"));
                            draft.mechanicName = firstNonEmpty(selected.optString("name", ""), labels[which]);
                            mechanicValue.setText(displayOrDash(draft.mechanicName));
                        })
                        .setNegativeButton(getString(R.string.action_cancel), null)
                        .show();
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                if (!isAdded()) return;
                loading.dismiss();
                showErrorDialog("Gagal memuat mechanic", message);
            }
        });
    }

    private void openWorkOrderPickerForDialog(@NonNull WorkshopSelectionDraft draft,
                                              @NonNull TextView workOrderValue) {
        if (!ensureWorkshopModule(ModuleRegistry.WORK_ORDERS, getString(R.string.menu_work_orders))) return;

        AlertDialog loading = showLoadingDialog("Memuat work order...");
        Map<String, String> query = new HashMap<>();
        if (draft.customerId != null && draft.customerId > 0) query.put("customer", String.valueOf(draft.customerId));
        if (draft.vehicleId != null && draft.vehicleId > 0) query.put("vehicle", String.valueOf(draft.vehicleId));

        listWorkshopEndpoint("api/workshop/work-orders/", query, new WorkshopListCallback() {
            @Override
            public void onSuccess(@NonNull JSONArray array) {
                if (!isAdded()) return;
                loading.dismiss();

                List<JSONObject> rows = filterByCustomerAndVehicle(array, draft.customerId, draft.vehicleId);
                if (rows.isEmpty()) {
                    showEmptyDialog("Work Order", "Tidak ada work order yang sesuai.");
                    return;
                }

                String[] labels = new String[rows.size()];
                for (int i = 0; i < rows.size(); i++) {
                    labels[i] = workOrderLabel(rows.get(i));
                }

                new AlertDialog.Builder(requireContext())
                        .setTitle(getString(R.string.select_work_order))
                        .setItems(labels, (dialog, which) -> {
                            JSONObject selected = rows.get(which);
                            draft.workOrderId = longOrNull(selected.opt("id"));
                            draft.workOrderInfo = labels[which];
                            workOrderValue.setText(displayOrDash(draft.workOrderInfo));
                        })
                        .setNegativeButton(getString(R.string.action_cancel), null)
                        .show();
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                if (!isAdded()) return;
                loading.dismiss();
                showErrorDialog("Gagal memuat work order", message);
            }
        });
    }

    private void openBookingPickerForDialog(@NonNull WorkshopSelectionDraft draft,
                                            @NonNull TextView bookingValue) {
        if (!ensureWorkshopModule(ModuleRegistry.BOOKINGS, getString(R.string.menu_bookings))) return;

        AlertDialog loading = showLoadingDialog("Memuat booking...");
        Map<String, String> query = new HashMap<>();
        if (draft.customerId != null && draft.customerId > 0) query.put("customer", String.valueOf(draft.customerId));
        if (draft.vehicleId != null && draft.vehicleId > 0) query.put("vehicle", String.valueOf(draft.vehicleId));

        listWorkshopEndpoint("api/workshop/bookings/", query, new WorkshopListCallback() {
            @Override
            public void onSuccess(@NonNull JSONArray array) {
                if (!isAdded()) return;
                loading.dismiss();

                List<JSONObject> rows = filterByCustomerAndVehicle(array, draft.customerId, draft.vehicleId);
                if (rows.isEmpty()) {
                    showEmptyDialog("Bookings", "Tidak ada booking yang sesuai.");
                    return;
                }

                String[] labels = new String[rows.size()];
                for (int i = 0; i < rows.size(); i++) {
                    labels[i] = bookingLabel(rows.get(i));
                }

                new AlertDialog.Builder(requireContext())
                        .setTitle(getString(R.string.select_booking))
                        .setItems(labels, (dialog, which) -> {
                            JSONObject selected = rows.get(which);
                            draft.bookingId = longOrNull(selected.opt("id"));
                            draft.bookingInfo = labels[which];
                            bookingValue.setText(displayOrDash(draft.bookingInfo));
                        })
                        .setNegativeButton(getString(R.string.action_cancel), null)
                        .show();
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                if (!isAdded()) return;
                loading.dismiss();
                showErrorDialog("Gagal memuat booking", message);
            }
        });
    }

    @NonNull
    private List<JSONObject> filterByCustomer(@NonNull JSONArray array, @Nullable Long customerId) {
        List<JSONObject> out = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject row = array.optJSONObject(i);
            if (row == null) continue;
            if (customerId != null && customerId > 0) {
                Long rowCustomerId = relationId(row, "customer", "customer_id");
                if (rowCustomerId != null && !customerId.equals(rowCustomerId)) continue;
            }
            out.add(row);
        }
        return out;
    }

    @NonNull
    private List<JSONObject> filterByCustomerAndVehicle(@NonNull JSONArray array,
                                                        @Nullable Long customerId,
                                                        @Nullable Long vehicleId) {
        List<JSONObject> out = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject row = array.optJSONObject(i);
            if (row == null) continue;
            if (customerId != null && customerId > 0) {
                Long rowCustomerId = relationId(row, "customer", "customer_id");
                if (rowCustomerId != null && !customerId.equals(rowCustomerId)) continue;
            }
            if (vehicleId != null && vehicleId > 0) {
                Long rowVehicleId = relationId(row, "vehicle", "vehicle_id");
                if (rowVehicleId != null && !vehicleId.equals(rowVehicleId)) continue;
            }
            out.add(row);
        }
        return out;
    }

    @NonNull
    private String getVehicleTypeCodeFromSpinner(@NonNull Spinner spinner) {
        Object selected = spinner.getSelectedItem();
        return normalizeVehicleTypeCode(selected != null ? selected.toString() : "CAR");
    }

    @NonNull
    private String cleanDash(@Nullable String value) {
        if (value == null) return "";
        String clean = value.trim();
        return "-".equals(clean) ? "" : clean;
    }

    @NonNull
    private String displayOrDash(@Nullable String value) {
        String clean = cleanDash(value);
        return clean.isEmpty() ? "-" : clean;
    }

    private void openCustomerPicker() {
        CustomerPickerDialog dialog = CustomerPickerDialog.newInstance();

        dialog.setListener(customer -> {
            if (customer.id > 0) {
                header.customerId = customer.id;
                header.customerName = customer.name;
                selectedCustomerId = (long) customer.id;
            } else {
                header.customerId = 0;
                header.customerName = "Walk-in Customer";
                selectedCustomerId = null;
            }

            selectedVehicleId = null;
            selectedBookingId = null;
            selectedWorkOrderId = null;
            selectedVehicleName = null;
            selectedPlateNumber = null;
            selectedBookingInfo = null;
            selectedWorkOrderInfo = null;
            header.vehicleName = "-";
            header.plateNumber = "-";
            updateHeaderUI();
        });

        dialog.show(getParentFragmentManager(), "customer_picker");
    }

    private void openVehicleInput() {
        VehicleInputDialog dialog = VehicleInputDialog.newInstance();
        dialog.setInitialVehicleTypeCode(header.vehicleTypeCode);

        dialog.setListener((vehicleTypeCode, vehicle, plate) -> {
            header.vehicleTypeCode = TextUtils.isEmpty(vehicleTypeCode) ? "CAR" : vehicleTypeCode;
            header.vehicleName = vehicle;
            header.plateNumber = plate;
            updateHeaderUI();
        });

        dialog.show(getParentFragmentManager(), "vehicle_input");
    }

    private void openVehiclePicker() {
        if (!ensureWorkshopModule(ModuleRegistry.VEHICLES, getString(R.string.menu_vehicles))) return;

        AlertDialog loading = showLoadingDialog("Memuat kendaraan...");
        Map<String, String> query = new HashMap<>();
        if (selectedCustomerId != null && selectedCustomerId > 0) {
            query.put("customer", String.valueOf(selectedCustomerId));
        }
        listWorkshopEndpoint("api/workshop/vehicles/", query, new WorkshopListCallback() {
            @Override
            public void onSuccess(@NonNull JSONArray array) {
                if (!isAdded()) return;
                loading.dismiss();

                List<JSONObject> rows = filterBySelectedCustomer(array);
                if (rows.isEmpty()) {
                    showEmptyDialog("Vehicles", "Tidak ada kendaraan untuk customer ini.");
                    return;
                }

                String[] labels = new String[rows.size()];
                for (int i = 0; i < rows.size(); i++) {
                    labels[i] = vehicleLabel(rows.get(i));
                }

                new AlertDialog.Builder(requireContext())
                        .setTitle(getString(R.string.select_vehicle))
                        .setItems(labels, (dialog, which) -> applySelectedVehicle(rows.get(which)))
                        .setNegativeButton(getString(R.string.action_cancel), null)
                        .show();
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                if (!isAdded()) return;
                loading.dismiss();
                showErrorDialog("Gagal memuat kendaraan", message);
            }
        });
    }

    private void openMechanicPicker() {
        if (!ensureWorkshopModule(ModuleRegistry.MECHANICS, getString(R.string.menu_mechanics))) return;

        AlertDialog loading = showLoadingDialog("Memuat mechanic...");
        Map<String, String> query = new HashMap<>();
        query.put("is_active", "true");
        listWorkshopEndpoint("api/workshop/mechanics/", query, new WorkshopListCallback() {
            @Override
            public void onSuccess(@NonNull JSONArray array) {
                if (!isAdded()) return;
                loading.dismiss();

                List<JSONObject> rows = activeRows(array);
                if (rows.isEmpty()) {
                    showEmptyDialog("Mechanics", "Tidak ada mechanic aktif.");
                    return;
                }

                String[] labels = new String[rows.size()];
                for (int i = 0; i < rows.size(); i++) {
                    JSONObject row = rows.get(i);
                    labels[i] = firstNonEmpty(row.optString("name", ""), "Mechanic #" + row.optInt("id", 0))
                            + optionalSuffix(row, "specialty");
                }

                new AlertDialog.Builder(requireContext())
                        .setTitle(getString(R.string.select_mechanic))
                        .setItems(labels, (dialog, which) -> {
                            JSONObject selected = rows.get(which);
                            selectedMechanicId = longOrNull(selected.opt("id"));
                            selectedMechanicName = firstNonEmpty(selected.optString("name", ""), labels[which]);
                            updateHeaderUI();
                        })
                        .setNegativeButton(getString(R.string.action_cancel), null)
                        .show();
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                if (!isAdded()) return;
                loading.dismiss();
                showErrorDialog("Gagal memuat mechanic", message);
            }
        });
    }

    private void openBookingPicker() {
        if (!ensureWorkshopModule(ModuleRegistry.BOOKINGS, getString(R.string.menu_bookings))) return;

        AlertDialog loading = showLoadingDialog("Memuat booking...");
        Map<String, String> query = new HashMap<>();
        if (selectedCustomerId != null && selectedCustomerId > 0) query.put("customer", String.valueOf(selectedCustomerId));
        if (selectedVehicleId != null && selectedVehicleId > 0) query.put("vehicle", String.valueOf(selectedVehicleId));

        listWorkshopEndpoint("api/workshop/bookings/", query, new WorkshopListCallback() {
            @Override
            public void onSuccess(@NonNull JSONArray array) {
                if (!isAdded()) return;
                loading.dismiss();

                List<JSONObject> rows = filterBySelectedCustomerAndVehicle(array);
                if (rows.isEmpty()) {
                    showEmptyDialog("Bookings", "Tidak ada booking yang sesuai.");
                    return;
                }

                String[] labels = new String[rows.size()];
                for (int i = 0; i < rows.size(); i++) {
                    labels[i] = bookingLabel(rows.get(i));
                }

                new AlertDialog.Builder(requireContext())
                        .setTitle(getString(R.string.select_booking))
                        .setItems(labels, (dialog, which) -> {
                            JSONObject selected = rows.get(which);
                            selectedBookingId = longOrNull(selected.opt("id"));
                            selectedBookingInfo = labels[which];
                            updateHeaderUI();
                        })
                        .setNegativeButton(getString(R.string.action_cancel), null)
                        .show();
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                if (!isAdded()) return;
                loading.dismiss();
                showErrorDialog("Gagal memuat booking", message);
            }
        });
    }

    private void openWorkOrderPicker() {
        if (!ensureWorkshopModule(ModuleRegistry.WORK_ORDERS, getString(R.string.menu_work_orders))) return;

        AlertDialog loading = showLoadingDialog("Memuat work order...");
        Map<String, String> query = new HashMap<>();
        if (selectedCustomerId != null && selectedCustomerId > 0) query.put("customer", String.valueOf(selectedCustomerId));
        if (selectedVehicleId != null && selectedVehicleId > 0) query.put("vehicle", String.valueOf(selectedVehicleId));

        listWorkshopEndpoint("api/workshop/work-orders/", query, new WorkshopListCallback() {
            @Override
            public void onSuccess(@NonNull JSONArray array) {
                if (!isAdded()) return;
                loading.dismiss();

                List<JSONObject> rows = filterBySelectedCustomerAndVehicle(array);
                String[] labels = new String[rows.size() + 1];
                labels[0] = getString(R.string.action_create) + " New Work Order";
                for (int i = 0; i < rows.size(); i++) {
                    labels[i + 1] = workOrderLabel(rows.get(i));
                }

                new AlertDialog.Builder(requireContext())
                        .setTitle(getString(R.string.select_work_order))
                        .setItems(labels, (dialog, which) -> {
                            if (which == 0) {
                                openCreateWorkOrderDialog();
                                return;
                            }
                            JSONObject selected = rows.get(which - 1);
                            selectedWorkOrderId = longOrNull(selected.opt("id"));
                            selectedWorkOrderInfo = labels[which];
                            updateHeaderUI();
                        })
                        .setNegativeButton(getString(R.string.action_cancel), null)
                        .show();
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                if (!isAdded()) return;
                loading.dismiss();
                new AlertDialog.Builder(requireContext())
                        .setTitle("Work Order")
                        .setMessage("Gagal memuat work order: " + message)
                        .setPositiveButton(getString(R.string.action_create), (d, w) -> openCreateWorkOrderDialog())
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
    }

    private void openCreateWorkOrderDialog() {
        if (!ensureWorkshopModule(ModuleRegistry.WORK_ORDERS, getString(R.string.menu_work_orders))) return;

        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        container.setPadding(pad, pad, pad, 0);

        EditText complaint = buildDialogEditText("Complaint *", true);
        EditText diagnosis = buildDialogEditText("Diagnosis", true);
        container.addView(complaint);
        container.addView(diagnosis);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Create Work Order")
                .setView(container)
                .setPositiveButton(getString(R.string.action_create), null)
                .setNegativeButton(getString(R.string.action_cancel), null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String complaintValue = complaint.getText() != null ? complaint.getText().toString().trim() : "";
            if (complaintValue.isEmpty()) {
                complaint.setError(getString(R.string.error_required));
                complaint.requestFocus();
                return;
            }

            try {
                JSONObject payload = new JSONObject();
                payload.put("complaint", complaintValue);
                String diagnosisValue = diagnosis.getText() != null ? diagnosis.getText().toString().trim() : "";
                if (!diagnosisValue.isEmpty()) payload.put("diagnosis", diagnosisValue);
                payload.put("status", "OPEN");
                payload.put("total_amount", 0);
                putNullableLong(payload, "customer", selectedCustomerId);
                putNullableLong(payload, "vehicle", selectedVehicleId);
                putNullableLong(payload, "mechanic", selectedMechanicId);

                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("Saving...");
                workshopModuleApi.create("api/workshop/work-orders/", payload, new WorkshopModuleApi.ObjectCallback() {
                    @Override
                    public void onSuccess(@NonNull JSONObject response) {
                        if (!isAdded()) return;
                        selectedWorkOrderId = longOrNull(response.opt("id"));
                        selectedWorkOrderInfo = workOrderLabel(response);
                        updateHeaderUI();
                        dialog.dismiss();
                    }

                    @Override
                    public void onError(int statusCode, @NonNull String message) {
                        if (!isAdded()) return;
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(getString(R.string.action_create));
                        ErrorHandler.handleApiError(requireContext(), "Gagal membuat Work Order: " + message);
                    }
                });
            } catch (Exception e) {
                ErrorHandler.handleApiError(requireContext(), "Error Work Order: " + e.getMessage());
            }
        }));
        dialog.show();
    }

    private void openServicePackagePicker() {
        if (!ensureWorkshopModule(ModuleRegistry.SERVICE_PACKAGES, getString(R.string.menu_service_packages))) return;
        if (servicePackageRepository == null) {
            Toast.makeText(requireContext(), "Service package repository belum siap", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog loading = showLoadingDialog("Memuat service package...");
        servicePackageRepository.fetchServicePackages(new ServicePackageRepository.ListCallback() {
            @Override
            public void onSuccess(@NonNull List<ServicePackageResponse> packages) {
                if (!isAdded()) return;
                loading.dismiss();

                List<ServicePackageResponse> active = new ArrayList<>();
                for (ServicePackageResponse item : packages) {
                    if (item != null && item.id > 0 && item.isActive) active.add(item);
                }
                if (active.isEmpty()) {
                    showEmptyDialog("Package", "Tidak ada service package aktif.");
                    return;
                }

                String[] labels = new String[active.size()];
                for (int i = 0; i < active.size(); i++) {
                    ServicePackageResponse item = active.get(i);
                    String desc = TextUtils.isEmpty(item.description) ? "" : "\n" + item.description;
                    labels[i] = safeText(item.name, "Package #" + item.id)
                            + " - " + formatMoney(parseMoney(item.price))
                            + " - " + Math.max(0, item.durationMinutes) + " min"
                            + desc;
                }

                new AlertDialog.Builder(requireContext())
                        .setTitle("Pilih Package")
                        .setItems(labels, (dialog, which) -> addServicePackageToCart(active.get(which)))
                        .setNegativeButton(getString(R.string.action_cancel), null)
                        .show();
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                if (!isAdded()) return;
                loading.dismiss();
                showErrorDialog("Gagal memuat package", message);
            }
        });
    }

    private interface WorkshopListCallback {
        void onSuccess(@NonNull JSONArray array);
        void onError(int statusCode, @NonNull String message);
    }

    private void listWorkshopEndpoint(@NonNull String endpoint,
                                      @Nullable Map<String, String> query,
                                      @NonNull WorkshopListCallback callback) {
        if (workshopModuleApi == null) {
            callback.onError(0, "Workshop API belum siap");
            return;
        }
        workshopModuleApi.list(endpoint, query, new WorkshopModuleApi.StringCallback() {
            @Override
            public void onSuccess(@NonNull String response) {
                try {
                    callback.onSuccess(extractArray(response));
                } catch (Exception e) {
                    callback.onError(0, "Parse error: " + e.getMessage());
                }
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                callback.onError(statusCode, message);
            }
        });
    }

    @NonNull
    private static JSONArray extractArray(@NonNull String response) throws Exception {
        Object parsed = new JSONTokener(response).nextValue();
        if (parsed instanceof JSONArray) return (JSONArray) parsed;
        if (parsed instanceof JSONObject) {
            JSONArray results = ((JSONObject) parsed).optJSONArray("results");
            if (results != null) return results;
            JSONArray data = ((JSONObject) parsed).optJSONArray("data");
            return data != null ? data : new JSONArray();
        }
        return new JSONArray();
    }

    @NonNull
    private List<JSONObject> filterBySelectedCustomer(@NonNull JSONArray array) {
        List<JSONObject> out = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject row = array.optJSONObject(i);
            if (row == null) continue;
            if (selectedCustomerId != null && selectedCustomerId > 0) {
                Long rowCustomerId = relationId(row, "customer", "customer_id");
                if (rowCustomerId != null && !selectedCustomerId.equals(rowCustomerId)) continue;
            }
            out.add(row);
        }
        return out;
    }

    @NonNull
    private List<JSONObject> filterBySelectedCustomerAndVehicle(@NonNull JSONArray array) {
        List<JSONObject> out = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject row = array.optJSONObject(i);
            if (row == null) continue;
            if (selectedCustomerId != null && selectedCustomerId > 0) {
                Long rowCustomerId = relationId(row, "customer", "customer_id");
                if (rowCustomerId != null && !selectedCustomerId.equals(rowCustomerId)) continue;
            }
            if (selectedVehicleId != null && selectedVehicleId > 0) {
                Long rowVehicleId = relationId(row, "vehicle", "vehicle_id");
                if (rowVehicleId != null && !selectedVehicleId.equals(rowVehicleId)) continue;
            }
            out.add(row);
        }
        return out;
    }

    @NonNull
    private List<JSONObject> activeRows(@NonNull JSONArray array) {
        List<JSONObject> out = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject row = array.optJSONObject(i);
            if (row == null) continue;
            if (row.has("is_active") && !row.optBoolean("is_active", true)) continue;
            if (row.has("active") && !row.optBoolean("active", true)) continue;
            out.add(row);
        }
        return out;
    }

    private void applySelectedVehicle(@NonNull JSONObject row) {
        selectedVehicleId = longOrNull(row.opt("id"));
        selectedVehicleType = normalizeVehicleTypeCode(firstNonEmpty(row.optString("vehicle_type", ""), row.optString("type", "")));
        selectedVehicleName = buildVehicleName(row);
        selectedPlateNumber = firstNonEmpty(row.optString("plate_number", ""), row.optString("plate", ""));

        Long vehicleCustomerId = relationId(row, "customer", "customer_id");
        if (selectedCustomerId != null && vehicleCustomerId != null && !selectedCustomerId.equals(vehicleCustomerId)) {
            Toast.makeText(requireContext(), "Vehicle ini milik customer berbeda.", Toast.LENGTH_LONG).show();
        }

        header.vehicleTypeCode = selectedVehicleType;
        header.vehicleName = safeText(selectedVehicleName, "-");
        header.plateNumber = safeText(selectedPlateNumber, "-");
        updateHeaderUI();
    }

    private void addServicePackageToCart(@NonNull ServicePackageResponse item) {
        if (cartManager == null || item.id <= 0) return;

        String name = safeText(item.name, "Package #" + item.id);

        if (!item.isActive) {
            // openServicePackagePicker() already filters inactive packages
            // out of the list, so this only fires for a package that went
            // inactive between opening the dialog and tapping it - the
            // authoritative rejection still happens server-side at
            // checkout regardless (an inactive package's product is kept
            // is_active=false in lockstep - see sync_service_package_
            // product), this is just an immediate, clearer message.
            Toast.makeText(requireContext(), getString(R.string.workshop_service_package_inactive, name), Toast.LENGTH_LONG).show();
            return;
        }

        if (item.productId == null || item.productId <= 0) {
            // The package has no linked sellable product yet - an old
            // cached response, a package created before the one-time
            // backfill ran, or a sync that hasn't completed. Never falls
            // back to sending service_package_id in its place - the
            // server has nowhere to put that on OrderItem (see the
            // saleability audit); the only fix is a real product_id.
            showServicePackageMissingProduct(name);
            return;
        }

        CartItem cartItem = new CartItem();
        cartItem.productId = item.productId;
        // Metadata only - identifies which package this line came from
        // for receipt/analytics purposes. The checkout payload always
        // sends "product": productId, never service_package_id (see
        // buildOrderPayload's item-building loop) - selling still goes
        // through the exact same product-based OrderItem path as any
        // other item, unchanged.
        cartItem.servicePackageId = item.id;
        cartItem.shopId = sessionManager != null ? sessionManager.getShopId() : 0;
        cartItem.name = name;
        // The price shown/held locally is for display only - the server
        // always resolves the authoritative sale price itself from the
        // linked product's current sell_price at checkout (see
        // OrderItemSerializer.validate() on the backend) and ignores
        // whatever price this cart item carries.
        cartItem.setPrice(Money.of(item.price));
        cartItem.imageUrl = "";
        cartItem.qty = 1;
        cartItem.orderType = "";
        cartItem.itemType = CartItem.ITEM_TYPE_SERVICE;
        cartItem.refreshCartKey();
        cartManager.add(cartItem);

        Toast.makeText(requireContext(), name + " ditambahkan", Toast.LENGTH_SHORT).show();
        loadCartFromManager();
        persistWorkshopCartToActiveDraft();
    }

    private void showServicePackageMissingProduct(@NonNull String name) {
        if (!isAdded()) return;

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.workshop_service_package_title))
                .setMessage(getString(R.string.workshop_service_package_missing_product, name))
                .setPositiveButton(getString(R.string.action_close), null)
                .show();
    }

    /**
     * Menjelaskan kenapa paket servis belum bisa dijual, dan menawarkan jalan
     * keluar yang benar-benar bekerja hari ini.
     *
     * @param cartKey bila bukan null, paketnya sudah telanjur ada di keranjang
     *                (mis. dari draf lama) dan bisa dihapus dari dialog ini
     */
    private void showServicePackageUnsupported(@NonNull String name, @Nullable String cartKey) {
        if (!isAdded()) return;

        androidx.appcompat.app.AlertDialog.Builder builder =
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle(getString(R.string.workshop_service_package_title))
                        .setMessage(getString(R.string.workshop_service_package_unsupported, name))
                        .setPositiveButton(getString(R.string.action_close), null);

        if (cartKey != null && !cartKey.trim().isEmpty() && cartManager != null) {
            builder.setNegativeButton(getString(R.string.workshop_service_package_remove),
                    (dialog, which) -> {
                        cartManager.removeByCartKey(cartKey);
                        loadCartFromManager();
                        persistWorkshopCartToActiveDraft();
                    });
        }

        builder.show();
    }

    /**
     * Paket servis yang masih tersangkut di keranjang tanpa product_id
     * yang valid, bila ada - satu-satunya bentuk yang benar-benar tidak
     * bisa dijual (peninggalan draf lama dari sebelum fitur ini ada).
     * Sebuah paket dengan servicePackageId &gt; 0 TAPI productId valid
     * (dibuat lewat addServicePackageToCart() sejak perbaikan ini) sah
     * untuk checkout seperti item lain - method ini sengaja tidak
     * memakai isServicePackage() sendirian, supaya tidak ikut menghadang
     * paket yang justru sudah benar.
     */
    @Nullable
    private WorkshopCartItem firstServicePackageInCart() {
        for (WorkshopCartItem item : cartItems) {
            if (item != null && item.isServicePackage() && item.getProductId() <= 0) return item;
        }
        return null;
    }

    @NonNull
    private String vehicleLabel(@NonNull JSONObject row) {
        String plate = firstNonEmpty(row.optString("plate_number", ""), row.optString("plate", "-"));
        String name = buildVehicleName(row);
        String type = normalizeVehicleTypeCode(firstNonEmpty(row.optString("vehicle_type", ""), row.optString("type", "")));
        return plate + " - " + safeText(name, "-") + " - " + type;
    }

    @NonNull
    private String buildVehicleName(@NonNull JSONObject row) {
        String brand = firstNonEmpty(row.optString("brand", ""), row.optString("make", ""));
        String model = row.optString("model", "");
        String joined = (brand + " " + model).trim();
        return firstNonEmpty(joined, row.optString("name", ""));
    }

    @NonNull
    private String bookingLabel(@NonNull JSONObject row) {
        String date = firstNonEmpty(row.optString("booking_date", ""), row.optString("date", ""));
        String time = firstNonEmpty(row.optString("booking_time", ""), row.optString("time", ""));
        String status = firstNonEmpty(row.optString("status", ""), "-");
        String customer = relationLabel(row, "customer", "customer_name");
        String vehicle = relationLabel(row, "vehicle", "vehicle_name");
        return (date + " " + time).trim()
                + " - " + status
                + (customer.isEmpty() ? "" : " - " + customer)
                + (vehicle.isEmpty() ? "" : " - " + vehicle);
    }

    @NonNull
    private String workOrderLabel(@NonNull JSONObject row) {
        String code = firstNonEmpty(row.optString("code", ""), row.optString("work_order_code", ""));
        if (code.isEmpty()) code = "WO #" + row.optInt("id", 0);
        String status = firstNonEmpty(row.optString("status", ""), "-");
        String complaint = firstNonEmpty(row.optString("complaint", ""), row.optString("notes", ""));
        double total = parseMoney(firstNonEmpty(row.optString("total_amount", ""), row.optString("total", "")));
        return code + " - " + status
                + (complaint.isEmpty() ? "" : " - " + complaint)
                + (total > 0 ? " - " + formatMoney(total) : "");
    }

    @NonNull
    private String relationLabel(@NonNull JSONObject row,
                                 @NonNull String objectKey,
                                 @NonNull String flatKey) {
        String flat = row.optString(flatKey, "");
        if (!TextUtils.isEmpty(flat)) return flat.trim();
        JSONObject object = row.optJSONObject(objectKey);
        if (object == null) return "";
        return firstNonEmpty(object.optString("name", ""), object.optString("plate_number", ""));
    }

    @Nullable
    private Long relationId(@NonNull JSONObject row,
                            @NonNull String objectKey,
                            @NonNull String flatKey) {
        Long flat = longOrNull(row.opt(flatKey));
        if (flat != null) return flat;
        Object relation = row.opt(objectKey);
        if (relation instanceof JSONObject) {
            return longOrNull(((JSONObject) relation).opt("id"));
        }
        return longOrNull(relation);
    }

    @Nullable
    private static Long longOrNull(@Nullable Object value) {
        if (value == null || value == JSONObject.NULL) return null;
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            String clean = String.valueOf(value).trim();
            if (clean.isEmpty() || "null".equalsIgnoreCase(clean)) return null;
            return Long.parseLong(clean);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void putNullableLong(@NonNull JSONObject payload,
                                        @NonNull String key,
                                        @Nullable Long value) throws Exception {
        if (value != null && value > 0) payload.put(key, value);
        else payload.put(key, JSONObject.NULL);
    }

    @NonNull
    private static String firstNonEmpty(@Nullable String first, @Nullable String second) {
        String a = first == null ? "" : first.trim();
        if (!a.isEmpty() && !"null".equalsIgnoreCase(a)) return a;
        String b = second == null ? "" : second.trim();
        return "null".equalsIgnoreCase(b) ? "" : b;
    }

    @NonNull
    private static String optionalSuffix(@NonNull JSONObject row, @NonNull String key) {
        String value = row.optString(key, "").trim();
        if (value.isEmpty() || "null".equalsIgnoreCase(value)) return "";
        return " - " + value;
    }

    private static double parseMoney(@Nullable String value) {
        if (value == null) return 0d;
        try {
            String clean = value.replace("$", "").replace("USD", "").replace(",", "").trim();
            return clean.isEmpty() ? 0d : Double.parseDouble(clean);
        } catch (Exception ignored) {
            return 0d;
        }
    }

    @NonNull
    private AlertDialog showLoadingDialog(@NonNull String message) {
        return new AlertDialog.Builder(requireContext())
                .setMessage(message)
                .setCancelable(false)
                .show();
    }

    private void showEmptyDialog(@NonNull String title, @NonNull String message) {
        new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void showErrorDialog(@NonNull String title, @NonNull String message) {
        new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    @NonNull
    private EditText buildDialogEditText(@NonNull String hint, boolean multiLine) {
        EditText input = new EditText(requireContext());
        input.setHint(hint);
        input.setSingleLine(!multiLine);
        input.setMinLines(multiLine ? 3 : 1);
        input.setTextColor(Color.parseColor("#111827"));
        input.setHintTextColor(Color.parseColor("#94A3B8"));
        input.setBackgroundResource(R.drawable.bg_input);
        input.setPadding(dp(14), 0, dp(14), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                multiLine ? dp(96) : dp(52)
        );
        lp.topMargin = dp(12);
        input.setLayoutParams(lp);
        return input;
    }

    private void loadCartFromManager() {
        if (cartManager == null) return;

        List<CartItem> rawItems = cartManager.getItems();
        if (rawItems == null) rawItems = new ArrayList<>();

        cartItems.clear();
        for (CartItem item : rawItems) {
            cartItems.add(WorkshopCartItem.fromCartItem(item));
        }

        refreshWorkspace();
    }

    private void refreshWorkspace() {
        List<WorkshopDisplayItem> display = buildDisplayItems();
        if (adapter != null) {
            adapter.submitList(display);
        }
        // Kolom item adalah bidang terluas di layar ini, dan pada bon baru ia
        // kosong seluruhnya. Tanpa keadaan kosong, tidak ada satu kata pun di
        // sana yang memberi tahu apa yang harus ditekan berikutnya.
        if (workshopItemsEmpty != null) {
            workshopItemsEmpty.setVisibility(display.isEmpty() ? View.VISIBLE : View.GONE);
        }
        updateSummary();
    }

    private List<WorkshopDisplayItem> buildDisplayItems() {
        List<WorkshopDisplayItem> result = new ArrayList<>();

        List<WorkshopCartItem> services = new ArrayList<>();
        List<WorkshopCartItem> parts = new ArrayList<>();
        List<WorkshopCartItem> products = new ArrayList<>();

        for (WorkshopCartItem item : cartItems) {
            if (TextUtils.equals(item.getItemType(), WorkshopCartItem.TYPE_SERVICE)) {
                services.add(item);
            } else if (TextUtils.equals(item.getItemType(), WorkshopCartItem.TYPE_PART)) {
                parts.add(item);
            } else {
                products.add(item);
            }
        }

        if (!services.isEmpty()) {
            result.add(WorkshopDisplayItem.createHeader(getString(R.string.label_service)));
            for (WorkshopCartItem item : services) {
                result.add(WorkshopDisplayItem.createItem(item));
            }
        }

        if (!parts.isEmpty()) {
            result.add(WorkshopDisplayItem.createHeader(getString(R.string.label_part)));
            for (WorkshopCartItem item : parts) {
                result.add(WorkshopDisplayItem.createItem(item));
            }
        }

        if (!products.isEmpty()) {
            result.add(WorkshopDisplayItem.createHeader(getString(R.string.label_products)));
            for (WorkshopCartItem item : products) {
                result.add(WorkshopDisplayItem.createItem(item));
            }
        }

        return result;
    }

    private void updateSummary() {
        double serviceTotal = 0;
        double partsTotal = 0;
        double productTotal = 0;

        for (WorkshopCartItem item : cartItems) {
            switch (item.getItemType()) {
                case WorkshopCartItem.TYPE_SERVICE:
                    serviceTotal += item.getLineTotal();
                    break;
                case WorkshopCartItem.TYPE_PART:
                    partsTotal += item.getLineTotal();
                    break;
                case WorkshopCartItem.TYPE_PRODUCT:
                default:
                    productTotal += item.getLineTotal();
                    break;
            }
        }

        double grandTotal = serviceTotal + partsTotal + productTotal;

        if (txtServiceTotal != null) txtServiceTotal.setText(formatMoney(serviceTotal));
        if (txtPartsTotal != null) txtPartsTotal.setText(formatMoney(partsTotal));
        if (txtProductTotal != null) txtProductTotal.setText(formatMoney(productTotal));
        if (txtGrandTotal != null) txtGrandTotal.setText(formatMoney(grandTotal));
    }

    @NonNull
    private Money getCartGrandTotalMoney() {
        Money total = Money.zero();
        for (WorkshopCartItem item : cartItems) {
            if (item == null) continue;
            total = total.plus(Money.ofDouble(item.getPrice()).times(item.getQuantity()));
        }
        return total;
    }

    /**
     * @deprecated pakai {@link #getCartGrandTotalMoney()}. Penjumlahannya sudah
     * eksak; konversi ke double hanya di akhir.
     */
    @Deprecated
    private double getCartGrandTotal() {
        return getCartGrandTotalMoney().toDouble();
    }

    private void handleCheckout() {
        if (checkoutSubmitting) {
            Toast.makeText(requireContext(), "Checkout sedang diproses...", Toast.LENGTH_SHORT).show();
            return;
        }

        if (cartItems.isEmpty()) {
            Toast.makeText(requireContext(), "Workspace masih kosong", Toast.LENGTH_SHORT).show();
            return;
        }

        ensureCheckoutConfigLoaded(this::openCheckoutDialog);
    }

    private void ensureCheckoutConfigLoaded(@NonNull Runnable onReady) {
        if (checkoutConfigLoaded && !checkoutPaymentMethods.isEmpty()) {
            onReady.run();
            return;
        }

        if (checkoutConfigLoading) {
            Toast.makeText(requireContext(), "Sedang memuat metode pembayaran...", Toast.LENGTH_SHORT).show();
            return;
        }

        if (sessionManager == null) {
            Toast.makeText(requireContext(), "Session belum siap", Toast.LENGTH_SHORT).show();
            return;
        }

        String token = sessionManager.getToken();
        if (token == null || token.trim().isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.error_session_expired), Toast.LENGTH_SHORT).show();
            return;
        }

        checkoutConfigLoading = true;
        checkoutPaymentMethods.clear();
        checkoutBankAccounts.clear();

        MasterDataRepository masterDataRepository = new MasterDataRepository(requireContext());
        final boolean[] readyOpened = {false};

        masterDataRepository.loadPaymentConfigRoomFirst(token, new MasterDataRepository.PaymentConfigCallback() {
            @Override
            public void onLocalPaymentConfig(@NonNull List<PaymentMethodItem> paymentMethods,
                                             @NonNull List<BankAccountItem> bankAccounts) {
                if (!isAdded()) return;
                applyCheckoutConfig(paymentMethods, bankAccounts);
                if (!checkoutPaymentMethods.isEmpty()) {
                    checkoutConfigLoading = false;
                    checkoutConfigLoaded = true;
                    readyOpened[0] = true;
                    onReady.run();
                }
            }

            @Override
            public void onRemotePaymentConfig(@NonNull List<PaymentMethodItem> paymentMethods,
                                              @NonNull List<BankAccountItem> bankAccounts) {
                if (!isAdded()) return;
                checkoutOfflineNoticeShown = false;
                checkoutNoLocalDataNoticeShown = false;
                checkoutConfigLoading = false;
                checkoutConfigLoaded = true;
                applyCheckoutConfig(paymentMethods, bankAccounts);

                if (checkoutPaymentMethods.isEmpty()) {
                    Toast.makeText(requireContext(),
                            "Metode pembayaran aktif tidak tersedia",
                            Toast.LENGTH_LONG).show();
                    return;
                }

                if (!readyOpened[0]) {
                    readyOpened[0] = true;
                    onReady.run();
                }
            }

            @Override
            public void onNoInternet(@NonNull List<PaymentMethodItem> paymentMethods,
                                     @NonNull List<BankAccountItem> bankAccounts) {
                if (!isAdded()) return;
                checkoutConfigLoading = false;
                checkoutConfigLoaded = !checkoutPaymentMethods.isEmpty();
                if (checkoutPaymentMethods.isEmpty() && paymentMethods.isEmpty()) {
                    showCheckoutNoLocalDataNoticeOnce();
                    return;
                }
                if (!checkoutOfflineNoticeShown) {
                    checkoutOfflineNoticeShown = true;
                    Toast.makeText(requireContext(), MasterDataRepository.MESSAGE_NO_INTERNET_SHOWING_LOCAL, Toast.LENGTH_SHORT).show();
                }
                if (!readyOpened[0] && !checkoutPaymentMethods.isEmpty()) {
                    readyOpened[0] = true;
                    onReady.run();
                }
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                if (!isAdded()) return;

                checkoutConfigLoading = false;
                checkoutConfigLoaded = !checkoutPaymentMethods.isEmpty();

                if (checkoutPaymentMethods.isEmpty()) {
                    ErrorHandler.handleApiError(requireContext(),
                            "Gagal memuat metode pembayaran: " + message);
                }
            }
        });
    }

    private void showCheckoutNoLocalDataNoticeOnce() {
        if (checkoutNoLocalDataNoticeShown) return;
        checkoutNoLocalDataNoticeShown = true;
        Toast.makeText(requireContext(), MasterDataRepository.MESSAGE_NO_LOCAL_POS_DATA, Toast.LENGTH_SHORT).show();
    }

    private void applyCheckoutConfig(@NonNull List<PaymentMethodItem> paymentMethods,
                                     @NonNull List<BankAccountItem> bankAccounts) {
        checkoutPaymentMethods.clear();
        for (PaymentMethodItem item : paymentMethods) {
            if (item != null && item.is_active) {
                checkoutPaymentMethods.add(item);
            }
        }

        checkoutBankAccounts.clear();
        for (BankAccountItem item : bankAccounts) {
            if (item != null && item.is_active) {
                checkoutBankAccounts.add(item);
            }
        }
    }

    private void openCheckoutDialog() {
        // Draf yang disimpan sebelum penjagaan di atas ada masih bisa memuat
        // paket servis. Menghadangnya di sini menghemat seluruh pengisian
        // dialog pembayaran yang ujungnya pasti ditolak server.
        WorkshopCartItem stuckPackage = firstServicePackageInCart();
        if (stuckPackage != null) {
            showServicePackageUnsupported(stuckPackage.getName(), stuckPackage.getCartKey());
            return;
        }

        double total = getCartGrandTotal();

        boolean needTable = false;
        boolean needDelivery = false;

        NativeCheckoutDialogFragment dialog =
                NativeCheckoutDialogFragment.newInstance(total, needTable, needDelivery);

        List<NativeCheckoutDialogFragment.CustomerOption> customerOptions = new ArrayList<>();
        customerOptions.add(new NativeCheckoutDialogFragment.CustomerOption(
                0,
                getString(R.string.workshop_walk_in_customer),
                0L
        ));
        if (header.customerId > 0) {
            customerOptions.add(new NativeCheckoutDialogFragment.CustomerOption(
                    header.customerId,
                    safeText(header.customerName, "Customer #" + header.customerId),
                    0L
            ));
            dialog.setPreselectedCustomer(
                    header.customerId,
                    safeText(header.customerName, "Customer #" + header.customerId),
                    0L
            );
        }
        dialog.setCustomerOptions(customerOptions);

        List<NativeCheckoutDialogFragment.PaymentMethodOption> paymentOptions = new ArrayList<>();
        for (PaymentMethodItem pm : checkoutPaymentMethods) {
            if (pm == null || !pm.is_active) continue;

            paymentOptions.add(new NativeCheckoutDialogFragment.PaymentMethodOption(
                    pm.id,
                    safeText(pm.code, ""),
                    safeText(pm.name, "Payment"),
                    pm.requires_bank_account
            ));
        }

        List<NativeCheckoutDialogFragment.BankAccountOption> bankOptions = new ArrayList<>();
        for (BankAccountItem ba : checkoutBankAccounts) {
            if (ba == null || !ba.is_active) continue;

            String label;
            if (!TextUtils.isEmpty(ba.account_number)) {
                label = safeText(ba.bank_name, "Bank") + " - "
                        + safeText(ba.name, "Account") + " ("
                        + safeText(ba.account_number, "-") + ")";
            } else {
                label = safeText(ba.bank_name, "Bank") + " - "
                        + safeText(ba.name, "Account");
            }

            bankOptions.add(new NativeCheckoutDialogFragment.BankAccountOption(
                    ba.id,
                    label
            ));
        }

        dialog.setPaymentOptions(paymentOptions);
        dialog.setBankOptions(bankOptions);

        dialog.setBankListener(new NativeCheckoutDialogFragment.BankListener() {
            @Override
            public void onConfirmBank(@NonNull NativeCheckoutDialogFragment.BankCheckoutResult result) {
                handleCheckoutBank(result);
            }
        });

        dialog.show(getParentFragmentManager(), "NativeCheckoutDialog");
    }

    private void handleCheckoutBank(@NonNull NativeCheckoutDialogFragment.BankCheckoutResult result) {
        if (orderRepository == null || sessionManager == null) {
            Toast.makeText(requireContext(), "Order repository belum siap", Toast.LENGTH_SHORT).show();
            return;
        }

        Context appCtx = requireContext().getApplicationContext();
        String token = sessionManager.getToken();
        if (token == null || token.trim().isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.error_session_expired), Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            checkoutSubmitting = true;
            setCheckoutLoading(true);

            JSONObject payload = buildWorkshopOrderPayload(
                    result.paymentMethodCode,
                    result.cashReceived,
                    result.changeAmount,
                    result.tableNumber,
                    result.deliveryAddress,
                    result.deliveryFee,
                    result.paymentMethodId,
                    result.bankAccountId,
                    result.bankAccountLabel,
                    result.referenceNumber,
                    result.paymentNote,
                    result.customerId,
                    result.customerName,
                    getSelectedVehicleTypeCode(),
                    result.splitPayments
            );
            List<WorkshopCartItem> receiptItems = new ArrayList<>(cartItems);
            final long draftIdForKey = activeDraftId;
            String heldClientOrderId = pendingWorkshopClientOrderIds.get(draftIdForKey);
            if (heldClientOrderId == null) {
                heldClientOrderId = OfflineOrderRepository.newClientOrderId(appCtx);
                pendingWorkshopClientOrderIds.put(draftIdForKey, heldClientOrderId);
            }
            String clientOrderId = heldClientOrderId;
            OfflineOrderRepository.ensureClientOrderId(payload, clientOrderId);
            String localOrderId = clientOrderId;
            Log.i(TAG, "Workshop order submit client_order_id=" + clientOrderId);

            // WRITE-AHEAD: simpan dulu ke Room, baru kirim.
            final String writeAheadLocalOrderId = localOrderId;
            new OfflineOrderRepository(appCtx).savePendingOrder(
                    writeAheadLocalOrderId,
                    payload,
                    POS_TYPE_WORKSHOP,
                    new OfflineOrderRepository.SaveCallback() {
                        @Override
                        public void onSuccess(@NonNull String savedLocalOrderId, boolean inserted) {
                            Log.i(TAG, "Workshop order write-ahead saved localOrderId=" + savedLocalOrderId
                                    + " inserted=" + inserted);
                            sendWorkshopOrderAfterWriteAhead(savedLocalOrderId, payload, token, appCtx,
                                    result, receiptItems, draftIdForKey);
                        }

                        @Override
                        public void onError(@NonNull String message) {
                            checkoutSubmitting = false;
                            Log.e(TAG, "Workshop order write-ahead FAILED, submit dibatalkan: " + message);
                            if (isAdded()) {
                                setCheckoutLoading(false);
                                Toast.makeText(requireContext(),
                                        "Failed to save local order: " + message,
                                        Toast.LENGTH_LONG).show();
                            }
                        }
                    }
            );

        } catch (Exception e) {
            checkoutSubmitting = false;
            if (isAdded()) {
                setCheckoutLoading(false);
                ErrorHandler.handleApiError(requireContext(), "Error checkout: " + e.getMessage());
            } else {
                Log.e(TAG, "Workshop checkout error after detach", e);
            }
        }
    }

    private void sendWorkshopOrderAfterWriteAhead(@NonNull String localOrderId,
                                                  @NonNull JSONObject payload,
                                                  @NonNull String token,
                                                  @NonNull Context appCtx,
                                                  @NonNull NativeCheckoutDialogFragment.BankCheckoutResult result,
                                                  @NonNull List<WorkshopCartItem> receiptItems,
                                                  long draftIdForKey) {
        if (orderRepository == null) {
            checkoutSubmitting = false;
            Log.e(TAG, "orderRepository null setelah write-ahead");
            return;
        }
        orderRepository.createOrder(token, payload, new OrderRepository.CreateCallback() {
                @Override
                public void onSuccess(@NonNull JSONObject response) {
                    checkoutSubmitting = false;
                    new OfflineOrderRepository(appCtx).markWriteAheadSynced(localOrderId);
                    // Order sudah tersimpan di server: checkout berikutnya harus pakai kunci baru.
                    // Ditaruh di sini, bukan di onCheckoutSubmitSuccess(), supaya cabang
                    // "fragment sudah detach" di bawah juga ikut terbersihkan.
                    pendingWorkshopClientOrderIds.remove(draftIdForKey);
                    new OfflineOrderRepository(appCtx).syncPendingOrders(token);
                    if (isAdded()) {
                        setCheckoutLoading(false);
                        onCheckoutSubmitSuccess(response, result, receiptItems);
                    } else {
                        Log.i(TAG, "checkout cleanup started pos=workshop reason=online_success_detached");
                        if (posDraftRepository != null && activeDraftId > 0L) {
                            completeActiveWorkshopDraftCheckout();
                        }
                        if (cartManager != null) {
                            cartManager.clear();
                            Log.i(TAG, "cart cleanup success pos=workshop reason=online_success_detached");
                        }
                    }
                }

                @Override
                public void onError(int statusCode, @NonNull String message) {
                    checkoutSubmitting = false;
                    if (isAdded()) {
                        setCheckoutLoading(false);
                    }

                    if (ErrorHandler.isDeviceTimeValidationError(statusCode, message)) {
                        new OfflineOrderRepository(appCtx)
                                .markWriteAheadNeedsReview(localOrderId, "device_time rejected");
                        if (isAdded()) {
                            ErrorHandler.showDeviceTimeDialog(requireContext());
                        } else {
                            Log.w(TAG, "Workshop checkout device time validation failed after detach: " + message);
                        }
                        return;
                    }

                    if (OfflineOrderRepository.shouldSaveOffline(appCtx, statusCode, message)) {
                        // Sudah tersimpan PENDING_SYNC oleh write-ahead.
                        finishWorkshopAfterOfflineSave(localOrderId, payload, result, receiptItems, appCtx, draftIdForKey);
                        return;
                    }

                    new OfflineOrderRepository(appCtx).markWriteAheadFailed(localOrderId, "HTTP " + statusCode);
                    Log.i(TAG, "cart kept because server rejected pos=workshop statusCode=" + statusCode
                            + " (order tersimpan lokal)");
                    if (isAdded()) {
                        if (statusCode == 400
                                && message != null
                                && message.toLowerCase(Locale.US).contains("vehicle_type")) {
                            Toast.makeText(requireContext(),
                                    "Vehicle Type tidak valid. Pilih Car atau Motorcycle.",
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        ErrorHandler.handleApiError(requireContext(), "Gagal simpan transaksi: " + message);
                    } else {
                        Log.e(TAG, "Workshop checkout failed after detach: " + message);
                    }
                }
            });
    }

    /**
     * Order sudah tersimpan lokal oleh write-ahead; ini hanya menutup transaksi
     * di layar (cetak struk offline, bersihkan keranjang).
     */
    private void finishWorkshopAfterOfflineSave(@NonNull String localOrderId,
                                                @NonNull JSONObject payload,
                                                @NonNull NativeCheckoutDialogFragment.BankCheckoutResult result,
                                                @NonNull List<WorkshopCartItem> receiptItems,
                                                @NonNull Context appCtx,
                                                long draftIdForKey) {
        checkoutSubmitting = false;
        pendingWorkshopClientOrderIds.remove(draftIdForKey);
        if (isAdded()) {
            setCheckoutLoading(false);
        }
        tryAutoPrintOfflineWorkshopReceipt(
                appCtx,
                receiptItems,
                result,
                offlineReceiptNumber(localOrderId, payload),
                payload.optString("device_time", ""),
                printResult -> showOfflineReceiptResult(appCtx, printResult)
        );
        if (isAdded()) {
            Toast.makeText(requireContext(),
                    OfflineOrderRepository.MESSAGE_ORDER_SAVED_LOCALLY,
                    Toast.LENGTH_LONG).show();
            completeWorkshopCheckoutLocallyAfterOfflineSave(result.paymentMethodCode);
        } else if (cartManager != null) {
            Log.i(TAG, "checkout cleanup started pos=workshop reason=offline_save_success_detached");
            if (posDraftRepository != null && activeDraftId > 0L) {
                completeActiveWorkshopDraftCheckout();
            }
            cartManager.clear();
            Log.i(TAG, "cart cleanup success pos=workshop reason=offline_save_success_detached");
        }
    }

    private void completeWorkshopCheckoutLocallyAfterOfflineSave(@NonNull String paymentMethodCode) {
        Log.i(TAG, "checkout cleanup started pos=workshop reason=offline_save_success");
        loadingDraftFromRoom = true;

        header.status = "Paid";
        header.vehicleName = "-";
        header.plateNumber = "-";
        header.customerId = 0;
        header.customerName = "Walk-in Customer";
        clearSelectedWorkshopMetadata();
        updateHeaderUI();
        completeActiveWorkshopDraftCheckout();
        if (cartManager != null) {
            try {
                cartManager.clear();
                Log.i(TAG, "cart cleanup success pos=workshop reason=offline_save_success");
            } catch (Exception e) {
                Log.e(TAG, "cleanup failed with error pos=workshop cart reason=offline_save_success", e);
            }
        }

    }

    @NonNull
    private JSONObject buildWorkshopOrderPayload(@NonNull String paymentMethod,
                                                 double cashReceived,
                                                 double changeAmount,
                                                 @NonNull String tableNumber,
                                                 @NonNull String deliveryAddress,
                                                 double deliveryFee,
                                                 @Nullable Integer paymentMethodId,
                                                 @Nullable Integer bankAccountId,
                                                 @Nullable String bankAccountLabel,
                                                 @Nullable String referenceNumber,
                                                 @Nullable String paymentNote,
                                                 @Nullable Integer selectedCustomerId,
                                                 @Nullable String selectedCustomerName,
                                                 @Nullable String selectedVehicleTypeCode,
                                                 @NonNull java.util.List<NativeCheckoutDialogFragment.SplitPayment> splitPayments) throws Exception {

        Money subtotalMoney = getCartGrandTotalMoney();
        OrderTotals workshopTotals = OrderTotals.of(
                subtotalMoney,
                Money.zero(),
                Money.ofDouble(deliveryFee),
                sessionManager != null ? sessionManager.getTaxPercent() : java.math.BigDecimal.ZERO);
        Money discountMoney = workshopTotals.discount();
        Money taxMoney = workshopTotals.tax();
        Money totalMoney = workshopTotals.total();

        double subtotal = subtotalMoney.toDouble();
        double total = totalMoney.toDouble();

        JSONObject payload = new JSONObject();
        payload.put("device_time", currentDeviceTimeIso());

        String effectiveCustomerName = !TextUtils.isEmpty(selectedCustomerName)
                ? selectedCustomerName
                : safeText(header.customerName, "Walk-in Customer");
        if (selectedCustomerId != null && selectedCustomerId > 0) {
            payload.put("customer", selectedCustomerId);
            payload.put("customer_id", selectedCustomerId);
        } else {
            payload.put("customer", JSONObject.NULL);
            payload.put("customer_id", JSONObject.NULL);
        }

        String vehicleTypeCode = normalizeVehicleTypeCode(selectedVehicleTypeCode);
        payload.put("vehicle_type", vehicleTypeCode);
        putNullableLong(payload, "vehicle_id", selectedVehicleId);
        putNullableLong(payload, "vehicle", selectedVehicleId);
        putNullableLong(payload, "mechanic_id", selectedMechanicId);
        putNullableLong(payload, "mechanic", selectedMechanicId);
        putNullableLong(payload, "booking_id", selectedBookingId);
        putNullableLong(payload, "booking", selectedBookingId);
        putNullableLong(payload, "work_order_id", selectedWorkOrderId);
        putNullableLong(payload, "work_order", selectedWorkOrderId);

        payload.put("payment_method", paymentMethod);
        // String desimal, bukan double: JSONObject akan menulis ekspansi biner.
        payload.put("subtotal", subtotalMoney.toPlainString());
        payload.put("discount", discountMoney.toPlainString());
        payload.put("tax", taxMoney.toPlainString());
        payload.put("total", totalMoney.toPlainString());

        StringBuilder notes = new StringBuilder();
        notes.append("Workshop order");
        notes.append(" | Customer: ").append(safeText(effectiveCustomerName, "Walk-in Customer"));
        notes.append(" | Vehicle Type: ").append(vehicleTypeCode);
        notes.append(" | Vehicle: ").append(safeText(header.vehicleName, "-"));
        notes.append(" | Plate: ").append(safeText(header.plateNumber, "-"));
        if (selectedMechanicId != null && selectedMechanicId > 0) {
            notes.append(" | Mechanic: ").append(safeText(selectedMechanicName, String.valueOf(selectedMechanicId)));
        }
        if (selectedBookingId != null && selectedBookingId > 0) {
            notes.append(" | Booking: ").append(safeText(selectedBookingInfo, String.valueOf(selectedBookingId)));
        }
        if (selectedWorkOrderId != null && selectedWorkOrderId > 0) {
            notes.append(" | Work Order: ").append(safeText(selectedWorkOrderInfo, String.valueOf(selectedWorkOrderId)));
        }

        if (!TextUtils.isEmpty(tableNumber)) {
            notes.append(" | Table: ").append(tableNumber);
        }

        if (!TextUtils.isEmpty(deliveryAddress)) {
            notes.append(" | Delivery: ").append(deliveryAddress);
        }

        if (deliveryFee > 0) {
            notes.append(" | Delivery Fee: ").append(formatMoney(deliveryFee));
        }

        if (!TextUtils.isEmpty(bankAccountLabel)) {
            notes.append(" | Bank: ").append(bankAccountLabel);
        }

        if (!TextUtils.isEmpty(referenceNumber)) {
            notes.append(" | Ref: ").append(referenceNumber);
        }

        if (!TextUtils.isEmpty(paymentNote)) {
            notes.append(" | Payment Note: ").append(paymentNote);
        }

        notes.append(" | Cash: ").append(formatMoney(cashReceived));
        notes.append(" | Change: ").append(formatMoney(changeAmount));

        payload.put("notes", notes.toString());

        JSONArray itemsArray = new JSONArray();
        for (WorkshopCartItem item : cartItems) {
            String backendItemType = toBackendItemType(item.getItemType());
            Log.d(TAG, "Workshop checkout item productId=" + item.getProductId()
                    + ", name=" + item.getName()
                    + ", androidItemType=" + item.getItemType()
                    + ", backendItemType=" + backendItemType
                    + ", quantity=" + item.getQuantity()
                    + ", price=" + item.getPrice());

            // Standard shape for every item, service packages included -
            // a package's cart item always carries a real productId by
            // the time it can reach checkout (see addServicePackageToCart
            // and firstServicePackageInCart's guard above). No separate
            // service_package_id field is sent: the backend's OrderItem
            // has nowhere to put it, and selling a package is, by design,
            // indistinguishable from selling any other product-backed
            // item once it's in the cart.
            JSONObject itemObj = new JSONObject();
            itemObj.put("product", item.getProductId());
            itemObj.put("item_type", backendItemType);
            itemObj.put("name", item.getName());
            itemObj.put("quantity", item.getQuantity());
            // Server menghitung ulang total dari harga baris ini.
            Money unitPrice = Money.ofDouble(item.getPrice());
            itemObj.put("price", unitPrice.toPlainString());
            itemObj.put("subtotal", unitPrice.times(item.getQuantity()).toPlainString());
            itemsArray.put(itemObj);
        }
        payload.put("items", itemsArray);

        JSONArray paymentsArray = new JSONArray();
        JSONObject paymentObj = new JSONObject();

        if (paymentMethodId != null && paymentMethodId > 0) {
            paymentObj.put("payment_method_id", paymentMethodId);
        } else {
            paymentObj.put("payment_method_id", JSONObject.NULL);
        }

        paymentObj.put("method_code", paymentMethod);
        Money splitTotal = Money.zero();
        for (NativeCheckoutDialogFragment.SplitPayment sp : splitPayments) {
            splitTotal = splitTotal.plus(sp.amount);
        }
        // Metode utama menanggung sisa setelah pembayaran terbagi.
        paymentObj.put("amount", totalMoney.minus(splitTotal).toPlainString());

        if (bankAccountId != null && bankAccountId > 0) {
            paymentObj.put("bank_account_id", bankAccountId);
        }

        paymentObj.put("reference_number", referenceNumber != null ? referenceNumber : "");
        paymentObj.put("note", paymentNote != null ? paymentNote : "");

        paymentsArray.put(paymentObj);

        for (NativeCheckoutDialogFragment.SplitPayment sp : splitPayments) {
            JSONObject extra = new JSONObject();
            extra.put("payment_method_id", sp.paymentMethodId != null ? sp.paymentMethodId : JSONObject.NULL);
            extra.put("method_code", sp.methodCode);
            extra.put("amount", sp.amount.toPlainString());
            extra.put("reference_number", "");
            extra.put("note", "");
            paymentsArray.put(extra);
        }

        payload.put("payments", paymentsArray);

        return payload;
    }

    @NonNull
    private static String toBackendItemType(@Nullable String androidItemType) {
        if (androidItemType == null) return "product";

        String value = androidItemType.trim().toUpperCase(Locale.US);
        if (value.isEmpty()) return "product";

        switch (value) {
            case "MENU":
                return "menu";
            case "SERVICE":
            case "SERVICE_PACKAGE":
            case "SERVICEPACKAGE":
            case "PACKAGE":
                return "service";
            case "SPAREPART":
            case "PART":
                return "sparepart";
            case "PRODUCT":
            default:
                return "product";
        }
    }

    @NonNull
    private static String currentDeviceTimeIso() {
        return new java.text.SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                Locale.US
        ).format(new java.util.Date());
    }

    private void onCheckoutSubmitSuccess(@NonNull JSONObject response,
                                         @NonNull NativeCheckoutDialogFragment.BankCheckoutResult result,
                                         @NonNull List<WorkshopCartItem> receiptItems) {
        String invoiceNumber = response.optString("invoice_number", "");
        if (TextUtils.isEmpty(invoiceNumber)) {
            invoiceNumber = response.optString("invoice", "");
        }
        if (TextUtils.isEmpty(invoiceNumber)) {
            invoiceNumber = "INV-" + System.currentTimeMillis();
        }

        tryAutoPrintWorkshopReceipt(receiptItems, result, invoiceNumber);

        Log.i(TAG, "checkout cleanup started pos=workshop reason=online_success");
        loadingDraftFromRoom = true;

        header.status = "Paid";
        header.vehicleName = "-";
        header.plateNumber = "-";
        header.customerId = 0;
        header.customerName = "Walk-in Customer";
        clearSelectedWorkshopMetadata();
        updateHeaderUI();

        completeActiveWorkshopDraftCheckout();
        if (cartManager != null) {
            try {
                cartManager.clear();
                Log.i(TAG, "cart cleanup success pos=workshop reason=online_success");
            } catch (Exception e) {
                Log.e(TAG, "cleanup failed with error pos=workshop cart reason=online_success", e);
            }
        }

        Toast.makeText(
                requireContext(),
                "Transaksi workshop berhasil disimpan (" + result.paymentMethodCode + ")",
                Toast.LENGTH_LONG
        ).show();
    }

    private void clearSelectedWorkshopMetadata() {
        selectedCustomerId = null;
        selectedVehicleId = null;
        selectedMechanicId = null;
        selectedBookingId = null;
        selectedWorkOrderId = null;
        selectedVehicleType = "CAR";
        selectedVehicleName = null;
        selectedPlateNumber = null;
        selectedMechanicName = null;
        selectedBookingInfo = null;
        selectedWorkOrderInfo = null;
        header.vehicleTypeCode = "CAR";
    }

    private void tryAutoPrintWorkshopReceipt(@NonNull List<WorkshopCartItem> receiptItems,
                                             @NonNull NativeCheckoutDialogFragment.BankCheckoutResult result,
                                             @NonNull String invoiceNumber) {
        if (!isAdded()) return;

        Context appCtx = requireContext().getApplicationContext();
        tryPrintWorkshopReceipt(appCtx, receiptItems, result, invoiceNumber, "", "", null);
    }

    private void tryAutoPrintOfflineWorkshopReceipt(@NonNull Context appCtx,
                                                    @NonNull List<WorkshopCartItem> receiptItems,
                                                    @NonNull NativeCheckoutDialogFragment.BankCheckoutResult result,
                                                    @NonNull String receiptNumber,
                                                    @NonNull String deviceTime,
                                                    @NonNull ReceiptPrintCallback callback) {
        Log.i(TAG, "Offline receipt print started order=" + receiptNumber);
        tryPrintWorkshopReceipt(
                appCtx,
                receiptItems,
                result,
                receiptNumber,
                "OFFLINE / PENDING SYNC",
                deviceTime,
                callback
        );
    }

    private void tryPrintWorkshopReceipt(@NonNull Context appCtx,
                                         @NonNull List<WorkshopCartItem> receiptItems,
                                         @NonNull NativeCheckoutDialogFragment.BankCheckoutResult result,
                                         @NonNull String invoiceNumber,
                                         @NonNull String receiptStatus,
                                         @NonNull String deviceTime,
                                         @Nullable ReceiptPrintCallback callback) {
        String receiptCustomerName = safeText(result.customerName, safeText(header.customerName, ""));
        String receiptVehicleName = safeText(header.vehicleName, "");
        String receiptPlateNumber = safeText(header.plateNumber, "");

        if (!com.valdker.pos.print.PrinterPrefs.isAutoPrintEnabled(appCtx)) {
            Log.i(TAG, "PRINTER: workshop auto print disabled -> skip printing");
            if (callback != null) {
                callback.onComplete(com.valdker.pos.print.BluetoothPrinterManager.PrintResult.SKIPPED);
            } else if (isAdded()) {
                Toast.makeText(requireContext(),
                        "Receipt printing skipped because auto-print is disabled.",
                        Toast.LENGTH_LONG).show();
            }
            return;
        }

        if (!com.valdker.pos.print.PrinterService.hasBtPermission(appCtx)) {
            Log.w(TAG, "PRINTER: Bluetooth permission not granted -> skip workshop receipt");
            if (callback == null && isAdded()) {
                Toast.makeText(
                        requireContext(),
                        "Bluetooth permission is required to connect printer.",
                        Toast.LENGTH_LONG
                ).show();
            } else if (callback != null) {
                callback.onComplete(com.valdker.pos.print.BluetoothPrinterManager.PrintResult.FAILED);
            }
            return;
        }

        String mac = com.valdker.pos.print.PrinterPrefs.getMac(appCtx);
        if (TextUtils.isEmpty(mac)) {
            Log.w(TAG, "PRINTER: no printer selected -> skip workshop receipt");
            if (callback == null && isAdded()) {
                Toast.makeText(
                        requireContext(),
                        "Printer not connected. Please select printer.",
                        Toast.LENGTH_LONG
                ).show();
            } else if (callback != null) {
                callback.onComplete(com.valdker.pos.print.BluetoothPrinterManager.PrintResult.FAILED);
            }
            return;
        }

        String fallbackReceipt = buildWorkshopReceipt(
                safeText(sessionManager != null ? sessionManager.getShopName() : "", "VALDKER POS"),
                safeText(sessionManager != null ? sessionManager.getShopAddress() : "", ""),
                "",
                receiptItems,
                result,
                invoiceNumber,
                receiptStatus,
                deviceTime,
                receiptCustomerName,
                receiptVehicleName,
                receiptPlateNumber
        );

        String token = sessionManager != null ? sessionManager.getToken() : "";
        AtomicBoolean receiptPrinted = new AtomicBoolean(false);
        ShopRepository.getBestShopProfileForReceipt(appCtx, token, new ShopRepository.Callback() {
            @Override
            public void onSuccess(@NonNull Shop shop) {
                String receipt = buildWorkshopReceipt(
                        safeText(shop.name, "VALDKER POS"),
                        safeText(shop.address, ""),
                        safeText(shop.phone, ""),
                        receiptItems,
                        result,
                        invoiceNumber,
                        receiptStatus,
                        deviceTime,
                        receiptCustomerName,
                        receiptVehicleName,
                        receiptPlateNumber
                );
                printWorkshopReceiptOnce(appCtx, receipt, receiptPrinted, callback);
            }

            @Override
            public void onEmpty() {
                printWorkshopReceiptOnce(appCtx, fallbackReceipt, receiptPrinted, callback);
            }

            @Override
            public void onError(@NonNull String message) {
                Log.w(TAG, "PRINTER: failed to load shop for workshop receipt: " + message);
                printWorkshopReceiptOnce(appCtx, fallbackReceipt, receiptPrinted, callback);
            }
        });
    }

    private void printWorkshopReceiptOnce(@NonNull Context appCtx,
                                          @NonNull String receipt,
                                          @NonNull AtomicBoolean printed,
                                          @Nullable ReceiptPrintCallback callback) {
        if (!printed.compareAndSet(false, true)) {
            Log.w(TAG, callback != null
                    ? "Offline receipt print skipped: already printed"
                    : "Receipt print skipped: already printed");
            return;
        }
        printWorkshopReceiptBestEffort(appCtx, receipt, callback);
    }

    private void printWorkshopReceiptBestEffort(@NonNull Context appCtx,
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
                            Log.i(TAG, "PRINTER: workshop receipt print success");
                        }
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        if (callback != null) {
                            Log.e(TAG, "Offline receipt print failed: " + message);
                            callback.onComplete(com.valdker.pos.print.BluetoothPrinterManager.PrintResult.FAILED);
                        } else {
                            Log.e(TAG, "PRINTER: workshop receipt print failed: " + message);
                        }
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> showWorkshopPrintRetry(receipt));
                    }

                    @Override
                    public void onSkipped(@NonNull String message) {
                        Log.w(TAG, "PRINTER: workshop receipt print skipped: " + message);
                        if (callback != null) {
                            Log.w(TAG, "Offline receipt print skipped: " + message);
                            callback.onComplete(com.valdker.pos.print.BluetoothPrinterManager.PrintResult.SKIPPED);
                        }
                    }

                    @Override
                    public void onTimeout(@NonNull String message) {
                        if (callback != null) {
                            Log.e(TAG, "Offline receipt print timed out: " + message);
                            callback.onComplete(com.valdker.pos.print.BluetoothPrinterManager.PrintResult.TIMEOUT);
                        } else {
                            Log.e(TAG, "PRINTER: workshop receipt print timed out: " + message);
                        }
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> showWorkshopPrintRetry(receipt));
                    }
                }
        );
    }

    private void showWorkshopPrintRetry(@NonNull String receipt) {
        if (!isAdded()) return;

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setMessage("Transaction saved, but receipt failed to print. Retry print?")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(getString(R.string.action_retry), null)
                .show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            v.setEnabled(false);
            printWorkshopReceiptBestEffort(requireContext().getApplicationContext(), receipt, null);
            dialog.dismiss();
        });
    }

    /**
     * Struk kasir bengkel.
     *
     * <p>Disusun lewat ReceiptBuilder, sama seperti struk retail, restoran,
     * dan cetak ulang. Versi lama menyusun barisnya sendiri dengan
     * String.format("$%.2f", double) - satu-satunya jalur struk yang belum
     * ikut migrasi ke Money - dan menghitung totalnya sendiri sebagai
     * subtotal + ongkir, sehingga diskon dan pajak yang sudah ditagihkan
     * tidak pernah muncul di kertas maupun ikut dihitung.
     */
    @NonNull
    private String buildWorkshopReceipt(@NonNull String shopName,
                                        @NonNull String shopAddress,
                                        @NonNull String shopPhone,
                                        @NonNull List<WorkshopCartItem> items,
                                        @NonNull NativeCheckoutDialogFragment.BankCheckoutResult result,
                                        @NonNull String invoiceNumber,
                                        @NonNull String receiptStatus,
                                        @NonNull String deviceTime,
                                        @NonNull String customerName,
                                        @NonNull String vehicleName,
                                        @NonNull String plateNumber) {
        ReceiptContent content = new ReceiptContent();

        content.shopName = shopName;
        content.shopAddress = shopAddress;
        content.shopPhone = shopPhone;
        content.statusBanner = receiptStatus;
        content.orderNumber = invoiceNumber;
        content.cashier = sessionManager != null ? safeText(sessionManager.getUsername(), "") : "";
        content.deviceTime = deviceTime.trim();

        String stamp = content.deviceTime.isEmpty() ? currentDeviceTimeIso() : content.deviceTime;
        content.date = ReceiptPayloadReader.isoDate(stamp);
        content.time = ReceiptPayloadReader.isoTime(stamp);

        String customer = safeText(customerName, "");
        if (!"Walk-in Customer".equalsIgnoreCase(customer)) {
            content.customer = customer;
        }
        String vehicle = safeText(vehicleName, "");
        if (!"-".equals(vehicle)) content.vehicleName = vehicle;
        String plate = safeText(plateNumber, "");
        if (!"-".equals(plate)) content.plateNumber = plate;

        Money subtotal = Money.zero();
        for (WorkshopCartItem item : items) {
            if (item == null) continue;
            ReceiptContent.Item line = new ReceiptContent.Item();
            line.name = safeText(item.getName(), "Item");
            line.qty = Math.max(0, item.getQuantity());
            line.unitPrice = Money.ofDouble(item.getPrice()).orZeroIfNegative();
            line.lineTotal = Money.ofDouble(item.getLineTotal()).orZeroIfNegative();
            line.typeLabel = workshopItemTypeLabel(item);
            content.items.add(line);
            subtotal = subtotal.plus(line.lineTotal);
        }

        // Nominal resmi datang dari hasil checkout, bukan dihitung ulang di
        // sini: itulah angka yang ditagihkan ke pelanggan dan dikirim ke
        // server. Subtotal hasil penjumlahan baris hanya dipakai kalau dialog
        // tidak mengirimkannya.
        content.subtotal = result.subtotalMoney.isPositive() ? result.subtotalMoney : subtotal;
        content.discount = result.discountMoney;
        content.tax = result.taxMoney;
        content.deliveryFee = result.deliveryFeeMoney;
        content.total = result.totalAmountMoney.isPositive()
                ? result.totalAmountMoney
                : content.subtotal.plus(result.deliveryFeeMoney);
        content.paid = result.cashReceivedMoney;
        content.change = result.changeAmountMoney;

        Money splitTotal = Money.zero();
        for (NativeCheckoutDialogFragment.SplitPayment sp : result.splitPayments) {
            splitTotal = splitTotal.plus(sp.amount);
        }
        content.addPayment(safeText(result.paymentMethodCode, "-"),
                content.total.minus(splitTotal));
        for (NativeCheckoutDialogFragment.SplitPayment sp : result.splitPayments) {
            content.addPayment(sp.methodCode, sp.amount);
        }

        content.footerNote = getString(R.string.receipt_thank_you);

        return ReceiptBuilder.build(content);
    }

    @NonNull
    private String workshopItemTypeLabel(@NonNull WorkshopCartItem item) {
        if (item.isServicePackage()) return "Package";
        String normalized = WorkshopCartItem.normalizeItemType(item.getItemType());
        if (WorkshopCartItem.TYPE_SERVICE.equals(normalized)) return "Service";
        if (WorkshopCartItem.TYPE_PART.equals(normalized)) return "Sparepart";
        return "Product";
    }

    private void setCheckoutLoading(boolean loading) {
        if (btnCheckout != null) {
            btnCheckout.setEnabled(!loading);
            btnCheckout.setText(loading ? "Processing..." : "Checkout");
        }

        if (btnAddService != null) btnAddService.setEnabled(!loading);
        if (btnAddPackage != null) btnAddPackage.setEnabled(!loading);
        if (btnAddPart != null) btnAddPart.setEnabled(!loading);
        if (btnAddProduct != null) btnAddProduct.setEnabled(!loading);
        if (btnSelectCustomer != null) btnSelectCustomer.setEnabled(!loading);
        if (btnSelectMechanic != null) btnSelectMechanic.setEnabled(!loading);
        if (btnSelectWorkOrder != null) btnSelectWorkOrder.setEnabled(!loading);
        if (btnSelectBooking != null) btnSelectBooking.setEnabled(!loading);
        if (!loading) applyWorkshopModuleVisibility();
    }

    private void syncPendingOrdersIfOnline() {
        if (!isAdded() || sessionManager == null) return;
        new OfflineOrderRepository(requireContext()).syncPendingOrders(sessionManager.getToken());
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

    @NonNull
    private String formatMoney(double value) {
        return String.format(Locale.US, "$%.2f", value);
    }

    @Override
    public void onIncreaseQty(WorkshopCartItem item) {
        if (cartManager == null || item == null) return;

        int nextQty = item.getQuantity() + 1;
        cartManager.setQtyByCartKey(item.getCartKey(), nextQty);
    }

    @Override
    public void onDecreaseQty(WorkshopCartItem item) {
        if (cartManager == null || item == null) return;

        int nextQty = item.getQuantity() - 1;
        if (nextQty <= 0) {
            cartManager.removeByCartKey(item.getCartKey());
        } else {
            cartManager.setQtyByCartKey(item.getCartKey(), nextQty);
        }
    }

    @Override
    public void onRemoveItem(WorkshopCartItem item) {
        if (cartManager == null || item == null) return;
        cartManager.removeByCartKey(item.getCartKey());
    }

    @Override
    public void onCartChanged() {
        if (!isAdded()) return;
        loadCartFromManager();
        if (!loadingDraftFromRoom) {
            persistWorkshopCartToActiveDraft();
        }
    }

}
