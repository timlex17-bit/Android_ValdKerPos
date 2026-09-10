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
    private Chip chipDraftA;
    private Chip chipDraftB;
    private Chip chipDraftC;
    private Chip chipAddDraft;
    private ChipGroup chipGroupDrafts;

    private TextView txtServiceTotal;
    private TextView txtPartsTotal;
    private TextView txtProductTotal;
    private TextView txtGrandTotal;

    private RecyclerView recyclerWorkspace;
    private MaterialButton btnSelectCustomer;
    private MaterialButton btnSelectMechanic;
    private MaterialButton btnSelectWorkOrder;
    private MaterialButton btnSelectBooking;
    private MaterialButton btnAddService;
    private MaterialButton btnAddPackage;
    private MaterialButton btnAddPart;
    private MaterialButton btnAddProduct;
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

    private void applyWorkshopSystemBars(@NonNull View root) {
        if (!isAdded()) return;

        Window window = requireActivity().getWindow();
        View decorView = window.getDecorView();

        // Hilangkan efek fullscreen / immersive jika Activity sebelumnya mengaktifkannya.
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        int flags = decorView.getSystemUiVisibility();
        flags &= ~View.SYSTEM_UI_FLAG_FULLSCREEN;
        flags &= ~View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        flags &= ~View.SYSTEM_UI_FLAG_IMMERSIVE;
        flags &= ~View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        flags &= ~View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        flags &= ~View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        decorView.setSystemUiVisibility(flags);

        // Kita handle padding status bar secara manual agar aman di semua device.
        WindowCompat.setDecorFitsSystemWindows(window, false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(Color.parseColor("#22C55E"));
            window.setNavigationBarColor(Color.WHITE);
        }

        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(window, decorView);

        controller.show(WindowInsetsCompat.Type.statusBars());
        controller.show(WindowInsetsCompat.Type.navigationBars());

        // Status bar hijau, icon putih.
        controller.setAppearanceLightStatusBars(false);

        // Navigation bar putih, icon gelap.
        controller.setAppearanceLightNavigationBars(true);

        final int baseLeft = root.getPaddingLeft();
        final int baseTop = root.getPaddingTop();
        final int baseRight = root.getPaddingRight();
        final int baseBottom = root.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            int statusTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;

            v.setPadding(
                    baseLeft,
                    Math.max(baseTop, 8) + statusTop,
                    baseRight,
                    Math.max(baseBottom, 8) + navBottom
            );

            return insets;
        });

        ViewCompat.requestApplyInsets(root);
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
        tvBrand = view.findViewById(R.id.tvBrand);
        tvShopAddress = view.findViewById(R.id.tvShopAddress);
        chipDraftA = view.findViewById(R.id.chipDraftA);
        chipDraftB = view.findViewById(R.id.chipDraftB);
        chipDraftC = view.findViewById(R.id.chipDraftC);
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

    private void setupDraftChips() {
        if (chipDraftA == null && chipDraftB == null && chipDraftC == null && chipAddDraft == null) {
            return;
        }

        if (chipDraftA != null) {
            chipDraftA.setOnClickListener(v -> setActiveDraftChip(chipDraftA, "Draft A aktif", true));
        }
        if (chipDraftB != null) {
            chipDraftB.setOnClickListener(v -> setActiveDraftChip(chipDraftB, "Draft B aktif", true));
        }
        if (chipDraftC != null) {
            chipDraftC.setOnClickListener(v -> setActiveDraftChip(chipDraftC, "Draft Walk-in aktif", true));
        }
        if (chipAddDraft != null) {
            styleAddDraftChip(chipAddDraft);
            chipAddDraft.setOnClickListener(v -> createAndActivateDraft());
        }

        setActiveDraftChip(chipDraftA, "", false);
    }

    private void initDraftStorage() {
        if (!isAdded()) return;
        posDraftRepository = new PosDraftRepository(requireContext());
        loadDraftsFromRoom(true, null);
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
        if (chipGroupDrafts == null) {
            PosDraftEntity active = findActiveDraft();
            setActiveDraftChip(chipDraftForName(active != null ? active.name : "A"), "", false);
            return;
        }

        chipGroupDrafts.removeAllViews();
        for (int i = 0; i < posDrafts.size(); i++) {
            PosDraftEntity draft = posDrafts.get(i);
            Chip chip = createDraftChip();
            if (i == 0) chip.setId(R.id.chipDraftA);
            else if (i == 1) chip.setId(R.id.chipDraftB);
            else if (i == 2) chip.setId(R.id.chipDraftC);

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

    @NonNull
    private Chip createDraftChip() {
        Chip chip = new Chip(requireContext());
        chip.setCheckable(true);
        chip.setClickable(true);
        chip.setSingleLine(true);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        chip.setTextSize(12f);
        chip.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        chip.setChipMinHeight(dp(34));
        chip.setMinHeight(dp(34));
        chip.setHeight(dp(34));
        chip.setChipCornerRadius(dp(17));
        chip.setChipStrokeWidth(dp(1));
        chip.setCheckedIconVisible(false);
        chip.setEnsureMinTouchTargetSize(false);
        return chip;
    }

    private void applyDraftChipStyle(@NonNull Chip chip, boolean active, @NonNull String name, int count) {
        chip.setChecked(active);
        chip.setText((active ? "\u25CF " : "") + name + " \u2022 " + Math.max(0, count));
        chip.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor(active ? "#DCFCE7" : "#FFFFFF")));
        chip.setChipStrokeColor(ColorStateList.valueOf(Color.parseColor(active ? "#86EFAC" : "#E2E8F0")));
        chip.setTextColor(Color.parseColor(active ? "#166534" : "#334155"));
        chip.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    }

    @Nullable
    private PosDraftEntity findActiveDraft() {
        for (PosDraftEntity draft : posDrafts) {
            if (draft != null && draft.id == activeDraftId) return draft;
        }
        return null;
    }

    @Nullable
    private Chip chipDraftForName(@NonNull String name) {
        if ("A".equalsIgnoreCase(name)) return chipDraftA;
        if ("B".equalsIgnoreCase(name)) return chipDraftB;
        if ("C".equalsIgnoreCase(name) || name.toLowerCase(Locale.US).contains("walk")) return chipDraftC;
        return chipDraftA;
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

    private void setActiveDraftChip(@Nullable Chip activeChip,
                                    @NonNull String toastMessage,
                                    boolean showToast) {
        setDraftChipState(chipDraftA, activeChip == chipDraftA, "A \u2022 3");
        setDraftChipState(chipDraftB, activeChip == chipDraftB, "B \u2022 12");
        setDraftChipState(chipDraftC, activeChip == chipDraftC, "Walk-in \u2022 1");

        if (showToast && isAdded() && !TextUtils.isEmpty(toastMessage)) {
            Toast.makeText(requireContext(), toastMessage, Toast.LENGTH_SHORT).show();
        }
    }

    private void setDraftChipState(@Nullable Chip chip, boolean active, @NonNull String label) {
        if (chip == null) return;

        chip.setChecked(active);
        chip.setText(active ? "\u25CF " + label : label);
        chip.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor(active ? "#DCFCE7" : "#FFFFFF")));
        chip.setChipStrokeColor(ColorStateList.valueOf(Color.parseColor(active ? "#86EFAC" : "#E2E8F0")));
        chip.setTextColor(Color.parseColor(active ? "#166534" : "#334155"));
        chip.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    }

    private void styleAddDraftChip(@NonNull Chip chip) {
        chip.setChecked(false);
        chip.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#22C55E")));
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
            Toast.makeText(requireContext(), "Token login tidak ditemukan", Toast.LENGTH_SHORT).show();
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
                .setNegativeButton("Batal", null)
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

    private void openWorkshopInfoDialog() {
        if (!isAdded()) return;

        WorkshopSelectionDraft draft = currentWorkshopSelectionDraft();

        ScrollView scrollView = new ScrollView(requireContext());
        scrollView.setFillViewport(false);

        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        container.setPadding(pad, dp(6), pad, dp(8));
        scrollView.addView(container, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        container.addView(buildDialogSectionTitle(getString(R.string.label_workshop_job_info)));

        TextView customerValue = buildDialogValueText(draft.customerName);
        addPickerRow(container, getString(R.string.label_customer), customerValue, getString(R.string.action_select),
                () -> openCustomerPickerForDialog(draft, customerValue));

        Spinner vehicleTypeSpinner = new Spinner(requireContext());
        ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(
                requireContext(),
                R.array.vehicle_types,
                android.R.layout.simple_spinner_item
        );
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        vehicleTypeSpinner.setAdapter(spinnerAdapter);
        vehicleTypeSpinner.setSelection("MOTORCYCLE".equals(normalizeVehicleTypeCode(draft.vehicleTypeCode)) ? 1 : 0);
        addSpinnerField(container, getString(R.string.label_vehicle_type), vehicleTypeSpinner);

        EditText vehicleInput = buildDialogEditText(getString(R.string.hint_vehicle_name), false);
        vehicleInput.setText(draft.vehicleName);
        EditText plateInput = buildDialogEditText(getString(R.string.hint_plate_number), false);
        plateInput.setText(draft.plateNumber);

        addInputWithPickerRow(container, getString(R.string.label_vehicle), vehicleInput,
                () -> openVehiclePickerForDialog(draft, vehicleInput, plateInput, vehicleTypeSpinner));

        addInputField(container, getString(R.string.label_plate), plateInput);

        container.addView(buildDialogSectionTitle(getString(R.string.workshop_summary_prefix)));

        TextView mechanicValue = buildDialogValueText(displayOrDash(draft.mechanicName));
        addPickerRow(container, getString(R.string.label_mechanic), mechanicValue, getString(R.string.action_select),
                () -> openMechanicPickerForDialog(draft, mechanicValue));

        TextView workOrderValue = buildDialogValueText(displayOrDash(draft.workOrderInfo));
        addPickerRow(container, getString(R.string.label_work_order), workOrderValue, getString(R.string.action_select),
                () -> openWorkOrderPickerForDialog(draft, workOrderValue));

        TextView bookingValue = buildDialogValueText(displayOrDash(draft.bookingInfo));
        addPickerRow(container, getString(R.string.label_booking), bookingValue, getString(R.string.action_select),
                () -> openBookingPickerForDialog(draft, bookingValue));

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialog_workshop_info_title)
                .setView(scrollView)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_save, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            draft.vehicleTypeCode = getVehicleTypeCodeFromSpinner(vehicleTypeSpinner);
            draft.vehicleName = cleanDash(vehicleInput.getText() != null ? vehicleInput.getText().toString() : "");
            draft.plateNumber = cleanDash(plateInput.getText() != null ? plateInput.getText().toString() : "");
            applyWorkshopSelectionDraft(draft);
            dialog.dismiss();
        }));
        dialog.show();
        styleWorkshopInfoDialog(dialog);
    }

    private TextView buildDialogSectionTitle(@NonNull String text) {
        TextView label = new TextView(requireContext());
        label.setText(text);
        label.setTextColor(Color.parseColor("#64748B"));
        label.setTextSize(11f);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setAllCaps(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.topMargin = dp(12);
        lp.bottomMargin = dp(2);
        label.setLayoutParams(lp);
        return label;
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

    private TextView buildDialogValueText(@NonNull String text) {
        TextView value = new TextView(requireContext());
        value.setText(text);
        value.setTextColor(Color.parseColor("#0F172A"));
        value.setTextSize(13f);
        value.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        value.setSingleLine(true);
        value.setEllipsize(TextUtils.TruncateAt.END);
        value.setGravity(android.view.Gravity.CENTER_VERTICAL);
        return value;
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
        card.setRadius(dp(10));
        card.setStrokeColor(Color.parseColor("#E2E8F0"));
        card.setStrokeWidth(dp(1));
        card.setContentPadding(dp(12), dp(8), dp(10), dp(8));
        return card;
    }

    private void addPickerRow(@NonNull LinearLayout container,
                              @NonNull String label,
                              @NonNull TextView value,
                              @NonNull String action,
                              @NonNull Runnable onClick) {
        MaterialCardView card = buildDialogFieldCard();

        LinearLayout body = new LinearLayout(requireContext());
        body.setOrientation(LinearLayout.VERTICAL);
        body.addView(buildDialogFieldLabel(label));

        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        rowLp.topMargin = dp(2);

        MaterialButton button = buildSmallDialogButton(action);
        button.setOnClickListener(v -> onClick.run());
        value.setOnClickListener(v -> onClick.run());
        row.setOnClickListener(v -> onClick.run());
        row.addView(value, new LinearLayout.LayoutParams(0, dp(34), 1f));
        LinearLayout.LayoutParams buttonLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(34)
        );
        buttonLp.leftMargin = dp(8);
        row.addView(button, buttonLp);
        body.addView(row, rowLp);

        card.setOnClickListener(v -> onClick.run());
        card.addView(body, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        container.addView(card, dialogCardLayoutParams());
    }

    private void addInputWithPickerRow(@NonNull LinearLayout container,
                                       @NonNull String label,
                                       @NonNull EditText input,
                                       @NonNull Runnable onClick) {
        MaterialCardView card = buildDialogFieldCard();

        LinearLayout body = new LinearLayout(requireContext());
        body.setOrientation(LinearLayout.VERTICAL);
        body.addView(buildDialogFieldLabel(label));

        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        input.setBackgroundColor(Color.TRANSPARENT);
        input.setPadding(0, 0, 0, 0);
        row.addView(input, new LinearLayout.LayoutParams(0, dp(38), 1f));

        MaterialButton button = buildSmallDialogButton(getString(R.string.action_select));
        button.setOnClickListener(v -> onClick.run());
        LinearLayout.LayoutParams buttonLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(34)
        );
        buttonLp.leftMargin = dp(8);
        row.addView(button, buttonLp);

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        rowLp.topMargin = dp(2);
        body.addView(row, rowLp);

        card.addView(body, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        container.addView(card, dialogCardLayoutParams());
    }

    private void addInputField(@NonNull LinearLayout container,
                               @NonNull String label,
                               @NonNull EditText input) {
        MaterialCardView card = buildDialogFieldCard();

        LinearLayout body = new LinearLayout(requireContext());
        body.setOrientation(LinearLayout.VERTICAL);
        body.addView(buildDialogFieldLabel(label));

        input.setBackgroundColor(Color.TRANSPARENT);
        input.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(38)
        );
        inputLp.topMargin = dp(2);
        body.addView(input, inputLp);

        card.addView(body, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        container.addView(card, dialogCardLayoutParams());
    }

    private void addSpinnerField(@NonNull LinearLayout container,
                                 @NonNull String label,
                                 @NonNull Spinner spinner) {
        MaterialCardView card = buildDialogFieldCard();

        LinearLayout body = new LinearLayout(requireContext());
        body.setOrientation(LinearLayout.VERTICAL);
        body.addView(buildDialogFieldLabel(label));

        LinearLayout.LayoutParams spinnerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(38)
        );
        spinnerLp.topMargin = dp(2);
        body.addView(spinner, spinnerLp);

        card.addView(body, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        container.addView(card, dialogCardLayoutParams());
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
        button.setTextColor(Color.parseColor("#16A34A"));
        button.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#86EFAC")));
        button.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#ECFDF3")));
        button.setCornerRadius(dp(12));
        return button;
    }

    private void styleWorkshopInfoDialog(@NonNull AlertDialog dialog) {
        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor("#16A34A"));
        }
        if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.parseColor("#64748B"));
        }

        Window window = dialog.getWindow();
        if (window != null) {
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.94f);
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
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
                                            @NonNull Spinner vehicleTypeSpinner) {
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
                        .setTitle("Pilih Vehicle")
                        .setItems(labels, (dialog, which) -> {
                            JSONObject row = rows.get(which);
                            draft.vehicleId = longOrNull(row.opt("id"));
                            draft.vehicleTypeCode = normalizeVehicleTypeCode(firstNonEmpty(row.optString("vehicle_type", ""), row.optString("type", "")));
                            draft.vehicleName = buildVehicleName(row);
                            draft.plateNumber = firstNonEmpty(row.optString("plate_number", ""), row.optString("plate", ""));
                            vehicleInput.setText(draft.vehicleName);
                            plateInput.setText(draft.plateNumber);
                            vehicleTypeSpinner.setSelection("MOTORCYCLE".equals(draft.vehicleTypeCode) ? 1 : 0);
                        })
                        .setNegativeButton("Batal", null)
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
                        .setTitle("Pilih Mechanic")
                        .setItems(labels, (dialog, which) -> {
                            JSONObject selected = rows.get(which);
                            draft.mechanicId = longOrNull(selected.opt("id"));
                            draft.mechanicName = firstNonEmpty(selected.optString("name", ""), labels[which]);
                            mechanicValue.setText(displayOrDash(draft.mechanicName));
                        })
                        .setNegativeButton("Batal", null)
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
                        .setTitle("Pilih Work Order")
                        .setItems(labels, (dialog, which) -> {
                            JSONObject selected = rows.get(which);
                            draft.workOrderId = longOrNull(selected.opt("id"));
                            draft.workOrderInfo = labels[which];
                            workOrderValue.setText(displayOrDash(draft.workOrderInfo));
                        })
                        .setNegativeButton("Batal", null)
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
                        .setTitle("Pilih Booking")
                        .setItems(labels, (dialog, which) -> {
                            JSONObject selected = rows.get(which);
                            draft.bookingId = longOrNull(selected.opt("id"));
                            draft.bookingInfo = labels[which];
                            bookingValue.setText(displayOrDash(draft.bookingInfo));
                        })
                        .setNegativeButton("Batal", null)
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
                        .setTitle("Pilih Vehicle")
                        .setItems(labels, (dialog, which) -> applySelectedVehicle(rows.get(which)))
                        .setNegativeButton("Batal", null)
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
                        .setTitle("Pilih Mechanic")
                        .setItems(labels, (dialog, which) -> {
                            JSONObject selected = rows.get(which);
                            selectedMechanicId = longOrNull(selected.opt("id"));
                            selectedMechanicName = firstNonEmpty(selected.optString("name", ""), labels[which]);
                            updateHeaderUI();
                        })
                        .setNegativeButton("Batal", null)
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
                        .setTitle("Pilih Booking")
                        .setItems(labels, (dialog, which) -> {
                            JSONObject selected = rows.get(which);
                            selectedBookingId = longOrNull(selected.opt("id"));
                            selectedBookingInfo = labels[which];
                            updateHeaderUI();
                        })
                        .setNegativeButton("Batal", null)
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
                        .setTitle("Pilih Work Order")
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
                        .setNegativeButton("Batal", null)
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
                .setNegativeButton("Batal", null)
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
                        .setNegativeButton("Batal", null)
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
        CartItem existing = findExistingServicePackageCartItem(item.id);
        if (existing != null) {
            cartManager.setQtyByCartKey(existing.cartKey, existing.qty + 1);
        } else {
            CartItem cartItem = new CartItem();
            cartItem.productId = item.id;
            cartItem.servicePackageId = item.id;
            cartItem.shopId = sessionManager != null ? sessionManager.getShopId() : 0;
            cartItem.name = name;
            cartItem.price = parseMoney(item.price);
            cartItem.imageUrl = "";
            cartItem.qty = 1;
            cartItem.orderType = "";
            cartItem.itemType = CartItem.ITEM_TYPE_SERVICE;
            cartManager.add(cartItem);
        }

        Toast.makeText(requireContext(), name + " ditambahkan", Toast.LENGTH_SHORT).show();
        loadCartFromManager();
        persistWorkshopCartToActiveDraft();
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
        if (adapter != null) {
            adapter.submitList(buildDisplayItems());
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

    private double getCartGrandTotal() {
        double total = 0.0;
        for (WorkshopCartItem item : cartItems) {
            total += item.getLineTotal();
        }
        return total;
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
            Toast.makeText(requireContext(), "Token login tidak ditemukan", Toast.LENGTH_SHORT).show();
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
            Toast.makeText(requireContext(), "Token login tidak ditemukan", Toast.LENGTH_SHORT).show();
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
                    getSelectedVehicleTypeCode()
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

            orderRepository.createOrder(token, payload, new OrderRepository.CreateCallback() {
                @Override
                public void onSuccess(@NonNull JSONObject response) {
                    checkoutSubmitting = false;
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
                        if (isAdded()) {
                            ErrorHandler.showDeviceTimeDialog(requireContext());
                        } else {
                            Log.w(TAG, "Workshop checkout device time validation failed after detach: " + message);
                        }
                        return;
                    }

                    if (OfflineOrderRepository.shouldSaveOffline(appCtx, statusCode, message)) {
                        saveWorkshopOrderOffline(localOrderId, payload, result, receiptItems, appCtx, draftIdForKey);
                        return;
                    }

                    Log.i(TAG, "cleanup skipped because save/submit failed pos=workshop statusCode=" + statusCode);
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

        } catch (Exception e) {
            checkoutSubmitting = false;
            setCheckoutLoading(false);
            ErrorHandler.handleApiError(requireContext(), "Error checkout: " + e.getMessage());
        }
    }

    private void saveWorkshopOrderOffline(@NonNull String localOrderId,
                                          @NonNull JSONObject payload,
                                          @NonNull NativeCheckoutDialogFragment.BankCheckoutResult result,
                                          @NonNull List<WorkshopCartItem> receiptItems,
                                          @NonNull Context appCtx,
                                          long draftIdForKey) {
        new OfflineOrderRepository(appCtx).savePendingOrder(
                localOrderId,
                payload,
                POS_TYPE_WORKSHOP,
                new OfflineOrderRepository.SaveCallback() {
                    @Override
                    public void onSuccess(@NonNull String savedLocalOrderId, boolean inserted) {
                        checkoutSubmitting = false;
                        // Kunci sudah tersimpan di Room dan akan dipakai ulang oleh sync
                        // otomatis, jadi aman dibuang dari memori di sini.
                        pendingWorkshopClientOrderIds.remove(draftIdForKey);
                        if (isAdded()) {
                            setCheckoutLoading(false);
                        }
                        tryAutoPrintOfflineWorkshopReceipt(
                                appCtx,
                                receiptItems,
                                result,
                                offlineReceiptNumber(savedLocalOrderId, payload),
                                payload.optString("device_time", ""),
                                result -> showOfflineReceiptResult(appCtx, result)
                        );
                        if (isAdded()) {
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

                    @Override
                    public void onError(@NonNull String message) {
                        checkoutSubmitting = false;
                        Log.i(TAG, "cleanup skipped because save/submit failed pos=workshop local_save_error=" + message);
                        if (isAdded()) {
                            setCheckoutLoading(false);
                            Toast.makeText(requireContext(),
                                    "Failed to save local order: " + message,
                                    Toast.LENGTH_LONG).show();
                        } else {
                            Log.e(TAG, "Failed to save detached workshop order locally: " + message);
                        }
                    }
                }
        );
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
                                                 @Nullable String selectedVehicleTypeCode) throws Exception {

        double subtotal = getCartGrandTotal();
        double discount = 0.0;
        double tax = 0.0;
        double total = subtotal + deliveryFee;

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
        payload.put("subtotal", subtotal);
        payload.put("discount", discount);
        payload.put("tax", tax);
        payload.put("total", total);

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

            JSONObject itemObj = new JSONObject();
            if (item.isServicePackage()) {
                itemObj.put("product", JSONObject.NULL);
                itemObj.put("service_package_id", item.getServicePackageId() > 0
                        ? item.getServicePackageId()
                        : item.getProductId());
            } else {
                itemObj.put("product", item.getProductId());
            }
            itemObj.put("item_type", backendItemType);
            itemObj.put("name", item.getName());
            itemObj.put("quantity", item.getQuantity());
            itemObj.put("price", item.getPrice());
            itemObj.put("subtotal", item.getLineTotal());
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
        paymentObj.put("amount", total);

        if (bankAccountId != null && bankAccountId > 0) {
            paymentObj.put("bank_account_id", bankAccountId);
        }

        paymentObj.put("reference_number", referenceNumber != null ? referenceNumber : "");
        paymentObj.put("note", paymentNote != null ? paymentNote : "");

        paymentsArray.put(paymentObj);
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
                .setPositiveButton("Retry", null)
                .show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            v.setEnabled(false);
            printWorkshopReceiptBestEffort(requireContext().getApplicationContext(), receipt, null);
            dialog.dismiss();
        });
    }

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
        java.text.SimpleDateFormat dfDate = new java.text.SimpleDateFormat("dd/MM/yy", Locale.US);
        java.text.SimpleDateFormat dfTime = new java.text.SimpleDateFormat("HH:mm", Locale.US);
        String date = dfDate.format(new java.util.Date());
        String time = dfTime.format(new java.util.Date());
        String cashier = sessionManager != null ? safeText(sessionManager.getUsername(), "") : "";

        double subtotal = 0.0;
        for (WorkshopCartItem item : items) {
            if (item != null) {
                subtotal += Math.max(0, item.getLineTotal());
            }
        }
        double deliveryFee = Math.max(0, result.deliveryFee);
        double total = subtotal + deliveryFee;

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
        if (!safeText(customerName, "").isEmpty()
                && !"Walk-in Customer".equalsIgnoreCase(safeText(customerName, ""))) {
            sb.append("[L]Customer:[R]").append(customerName.trim()).append("\n");
        }
        if (!safeText(vehicleName, "").isEmpty() && !"-".equals(vehicleName.trim())) {
            sb.append("[L]Vehicle:[R]").append(vehicleName.trim()).append("\n");
        }
        if (!safeText(plateNumber, "").isEmpty() && !"-".equals(plateNumber.trim())) {
            sb.append("[L]Plate:[R]").append(plateNumber.trim()).append("\n");
        }
        sb.append("[L]Date:[R]").append(date).append("\n");
        sb.append("[L]Time:[R]").append(time).append("\n");
        if (!deviceTime.trim().isEmpty()) {
            sb.append("[L]Device Time:[R]").append(deviceTime.trim()).append("\n");
        }
        sb.append("[C]--------------------------------\n");

        for (WorkshopCartItem item : items) {
            if (item == null) continue;

            String name = safeText(item.getName(), "Item");
            int qty = Math.max(0, item.getQuantity());
            double price = Math.max(0, item.getPrice());
            double line = Math.max(0, item.getLineTotal());

            sb.append("[L]<b>").append(name).append("</b>[R]<b>")
                    .append(String.format(Locale.US, "$%.2f", line))
                    .append("</b>\n");
            sb.append("[L]").append(workshopItemTypeLabel(item)).append("\n");
            sb.append("[L]").append(qty)
                    .append(" x ")
                    .append(String.format(Locale.US, "$%.2f", price))
                    .append("\n\n");
        }

        sb.append("[C]--------------------------------\n");
        sb.append("[L]Subtotal[R]").append(String.format(Locale.US, "$%.2f", subtotal)).append("\n");
        sb.append("[L]Discount[R]").append(String.format(Locale.US, "$%.2f", 0.00)).append("\n");
        sb.append("[L]VAT / Tax[R]").append(String.format(Locale.US, "$%.2f", 0.00)).append("\n");
        if (deliveryFee > 0) {
            sb.append("[L]Delivery Fee[R]").append(String.format(Locale.US, "$%.2f", deliveryFee)).append("\n");
        }
        sb.append("[C]--------------------------------\n");
        sb.append("[L]<b>Total</b>[R]<b>").append(String.format(Locale.US, "$%.2f", total)).append("</b>\n");
        sb.append("[L]Payment[R]").append(safeText(result.paymentMethodCode, "-")).append("\n");
        if (result.cashReceived > 0) {
            sb.append("[L]Paid[R]").append(String.format(Locale.US, "$%.2f", result.cashReceived)).append("\n");
            sb.append("[L]Change[R]").append(String.format(Locale.US, "$%.2f", result.changeAmount)).append("\n");
        }
        sb.append("[C]--------------------------------\n");
        sb.append("[C]Obrigado ba order ona iha!\n");
        sb.append("[C]").append(shopName).append("\n\n\n");

        return sb.toString();
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
