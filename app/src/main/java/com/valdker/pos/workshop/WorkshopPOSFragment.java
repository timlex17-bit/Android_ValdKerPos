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
import android.widget.PopupWindow;
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
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
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
import com.valdker.pos.models.Shop;
import com.valdker.pos.repositories.CheckoutConfigRepository;
import com.valdker.pos.repositories.MasterDataRepository;
import com.valdker.pos.repositories.OfflineOrderRepository;
import com.valdker.pos.repositories.OrderRepository;
import com.valdker.pos.repositories.ProductRepository;
import com.valdker.pos.repositories.ShopRepository;
import com.valdker.pos.shop.ShopEvents;
import com.valdker.pos.ui.checkout.BankAccountItem;
import com.valdker.pos.ui.checkout.NativeCheckoutDialogFragment;
import com.valdker.pos.ui.checkout.PaymentMethodItem;
import com.valdker.pos.utils.ErrorHandler;
import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Map;
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
        void openChangePasswordFromUserMenu();
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

    private final List<PaymentMethodItem> checkoutPaymentMethods = new ArrayList<>();
    private final List<BankAccountItem> checkoutBankAccounts = new ArrayList<>();
    private boolean checkoutConfigLoaded = false;
    private boolean checkoutConfigLoading = false;
    private boolean shopHeaderLoading = false;
    private long lastShopHeaderRefreshAt = 0L;

    private TextView txtCustomerName;
    private TextView txtVehicleName;
    private TextView txtPlateNumber;

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
    private MaterialButton btnAddService;
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

        txtServiceTotal = view.findViewById(R.id.txtServiceTotal);
        txtPartsTotal = view.findViewById(R.id.txtPartsTotal);
        txtProductTotal = view.findViewById(R.id.txtProductTotal);
        txtGrandTotal = view.findViewById(R.id.txtGrandTotal);

        recyclerWorkspace = view.findViewById(R.id.recyclerWorkspace);
        btnSelectCustomer = view.findViewById(R.id.btnSelectCustomer);
        btnAddService = view.findViewById(R.id.btnAddService);
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
        if (TextUtils.isEmpty(header.plateNumber)) {
            header.plateNumber = "-";
        }
        if (TextUtils.isEmpty(header.status)) {
            header.status = "Draft";
        }

        refreshShopHeaderFromSession();

        updateHeaderUI();
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
        if (txtCustomerName != null) {
            txtCustomerName.setText(safeText(header.customerName, "Walk-in Customer"));
        }
        if (txtVehicleName != null) {
            txtVehicleName.setText(safeText(header.vehicleName, "-"));
        }
        if (txtPlateNumber != null) {
            txtPlateNumber.setText(safeText(header.plateNumber, "-"));
        }
    }

    @NonNull
    private String safeText(@Nullable String value, @NonNull String fallback) {
        return TextUtils.isEmpty(value) ? fallback : value;
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
        btnSelectCustomer.setOnClickListener(v -> {
            if (!ensureShiftIsOpen()) return;
            openCustomerPicker();
        });

        btnAddService.setOnClickListener(v -> {
            if (!ensureShiftIsOpen()) return;
            openWorkshopItemPicker(WorkshopCartItem.TYPE_SERVICE);
        });

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

    private void showUserPopup(@NonNull View anchor) {
        Context context = requireContext();

        View popupView = LayoutInflater.from(context).inflate(R.layout.popup_user_menu, null, false);

        TextView tvUserName = popupView.findViewById(R.id.tvUserName);
        TextView tvUserRole = popupView.findViewById(R.id.tvUserRole);

        TextView btnChangePassword = popupView.findViewById(R.id.btnChangePassword);
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

        btnChangePassword.setOnClickListener(v -> {
            popupWindow.dismiss();
            if (hostActions != null) {
                hostActions.openChangePasswordFromUserMenu();
            } else {
                Toast.makeText(requireContext(), "Change Password unavailable", Toast.LENGTH_SHORT).show();
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
                stockText = " • Stock: " + p.stock;
            }

            labels[i] = safeText(p.name, "-") + " • " + formatMoney(p.price) + stockText;
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

    private void openCustomerPicker() {
        CustomerPickerDialog dialog = CustomerPickerDialog.newInstance();

        dialog.setListener(customer -> {
            if (customer.id > 0) {
                header.customerId = customer.id;
                header.customerName = customer.name;
            } else {
                header.customerId = 0;
                header.customerName = "Walk-in Customer";
            }

            updateHeaderUI();
            openVehicleInput();
        });

        dialog.show(getParentFragmentManager(), "customer_picker");
    }

    private void openVehicleInput() {
        VehicleInputDialog dialog = VehicleInputDialog.newInstance();

        dialog.setListener((vehicle, plate) -> {
            header.vehicleName = vehicle;
            header.plateNumber = plate;
            updateHeaderUI();
        });

        dialog.show(getParentFragmentManager(), "vehicle_input");
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
            result.add(WorkshopDisplayItem.createHeader("Services"));
            for (WorkshopCartItem item : services) {
                result.add(WorkshopDisplayItem.createItem(item));
            }
        }

        if (!parts.isEmpty()) {
            result.add(WorkshopDisplayItem.createHeader("Parts"));
            for (WorkshopCartItem item : parts) {
                result.add(WorkshopDisplayItem.createItem(item));
            }
        }

        if (!products.isEmpty()) {
            result.add(WorkshopDisplayItem.createHeader("Products"));
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
                    result.paymentNote
            );
            List<WorkshopCartItem> receiptItems = new ArrayList<>(cartItems);
            String clientOrderId = OfflineOrderRepository.newClientOrderId(appCtx);
            OfflineOrderRepository.ensureClientOrderId(payload, clientOrderId);
            String localOrderId = clientOrderId;
            Log.i(TAG, "Workshop order submit client_order_id=" + clientOrderId);

            orderRepository.createOrder(token, payload, new OrderRepository.CreateCallback() {
                @Override
                public void onSuccess(@NonNull JSONObject response) {
                    checkoutSubmitting = false;
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
                        saveWorkshopOrderOffline(localOrderId, payload, result, receiptItems, appCtx);
                        return;
                    }

                    Log.i(TAG, "cleanup skipped because save/submit failed pos=workshop statusCode=" + statusCode);
                    if (isAdded()) {
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
                                          @NonNull Context appCtx) {
        new OfflineOrderRepository(appCtx).savePendingOrder(
                localOrderId,
                payload,
                POS_TYPE_WORKSHOP,
                new OfflineOrderRepository.SaveCallback() {
                    @Override
                    public void onSuccess(@NonNull String savedLocalOrderId, boolean inserted) {
                        checkoutSubmitting = false;
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
                                                 @Nullable String paymentNote) throws Exception {

        double subtotal = getCartGrandTotal();
        double discount = 0.0;
        double tax = 0.0;
        double total = subtotal + deliveryFee;

        JSONObject payload = new JSONObject();
        payload.put("device_time", currentDeviceTimeIso());

        if (header.customerId > 0) {
            payload.put("customer", header.customerId);
        } else {
            payload.put("customer", JSONObject.NULL);
        }

        payload.put("payment_method", paymentMethod);
        payload.put("subtotal", subtotal);
        payload.put("discount", discount);
        payload.put("tax", tax);
        payload.put("total", total);

        StringBuilder notes = new StringBuilder();
        notes.append("Workshop order");
        notes.append(" | Customer: ").append(safeText(header.customerName, "Walk-in Customer"));
        notes.append(" | Vehicle: ").append(safeText(header.vehicleName, "-"));
        notes.append(" | Plate: ").append(safeText(header.plateNumber, "-"));

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
            itemObj.put("product", item.getProductId());
            itemObj.put("item_type", backendItemType);
            itemObj.put("quantity", item.getQuantity());
            itemObj.put("price", item.getPrice());
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
        String receiptCustomerName = safeText(header.customerName, "");
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
            sb.append("[L]").append(workshopItemTypeLabel(item.getItemType())).append("\n");
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
    private String workshopItemTypeLabel(@Nullable String itemType) {
        String normalized = WorkshopCartItem.normalizeItemType(itemType);
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
        if (btnAddPart != null) btnAddPart.setEnabled(!loading);
        if (btnAddProduct != null) btnAddProduct.setEnabled(!loading);
        if (btnSelectCustomer != null) btnSelectCustomer.setEnabled(!loading);
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
        if (cartManager == null) return;

        int nextQty = item.getQuantity() + 1;
        cartManager.setQty(item.getProductId(), item.getItemType(), nextQty);
    }

    @Override
    public void onDecreaseQty(WorkshopCartItem item) {
        if (cartManager == null) return;

        int nextQty = item.getQuantity() - 1;
        if (nextQty <= 0) {
            cartManager.remove(item.getProductId(), item.getItemType());
        } else {
            cartManager.setQty(item.getProductId(), item.getItemType(), nextQty);
        }
    }

    @Override
    public void onRemoveItem(WorkshopCartItem item) {
        if (cartManager == null) return;
        cartManager.remove(item.getProductId(), item.getItemType());
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
