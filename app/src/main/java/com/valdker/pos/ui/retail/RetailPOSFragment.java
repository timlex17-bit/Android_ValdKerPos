package com.valdker.pos.ui.retail;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.valdker.pos.utils.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.toolbox.StringRequest;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.valdker.pos.R;
import com.valdker.pos.money.Money;
import com.valdker.pos.SessionManager;
import com.valdker.pos.drafts.PosDraftEntity;
import com.valdker.pos.drafts.PosDraftItemEntity;
import com.valdker.pos.drafts.PosDraftRepository;
import com.valdker.pos.drafts.PosDraftSnapshot;
import com.valdker.pos.models.Product;
import com.valdker.pos.models.Shop;
import com.valdker.pos.network.ApiClient;
import com.valdker.pos.network.ApiConfig;
import com.valdker.pos.repositories.MasterDataRepository;
import com.valdker.pos.repositories.ShopRepository;

import android.graphics.Color;
import android.os.Build;
import android.view.Window;
import android.view.WindowManager;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RetailPOSFragment extends Fragment {

    public interface RetailHostActions {
        void onRetailBarcodeClick();
        void onRetailCartClick();
        void onRetailUserMenuClick(@NonNull View anchor);
        void onRetailAddToCartRequested(@NonNull RetailCartItem item);
    }

    private static final String ARG_BUSINESS_TYPE = "business_type";
    private static final String ARG_USE_GRID = "use_grid";
    private static final String ARG_SHOW_IMAGES = "show_images";

    private static final String TAG_PRODUCTS = "RETAIL_PRODUCTS";
    private static final String TAG = "RETAIL_POS";
    private static final long SCANNER_DEBOUNCE_MS = 700L;
    private static final String POS_TYPE_RETAIL = "retail";

    @Nullable
    private RetailHostActions host;

    private SessionManager session;
    private MasterDataRepository masterDataRepository;

    private String businessType = "retail";
    private boolean useGridPosLayout = false;
    private boolean showProductImagesInPos = false;

    // Header
    private ImageView imgLogo;
    private TextView tvBrand;
    private TextView tvShopAddress;
    private ImageButton btnHeaderBarcode;
    private EditText etSearchHint;
    private Chip chipDraftA;
    private Chip chipDraftB;
    private Chip chipDraftC;
    private Chip chipAddDraft;
    private ChipGroup chipGroupDrafts;

    // Content
    private RecyclerView rvProducts;
    private TextView tvEmptyState;
    private ProgressBar progressProducts;
    private TextView txtGrandTotal;
    private TextView txtItemCount;
    private TextView txtSectionSubtitle;
    private MaterialButton btnCheckout;

    private RetailProductAdapter productAdapter;
    @Nullable
    private PosDraftRepository posDraftRepository;
    private final ExecutorService draftExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<PosDraftEntity> posDrafts = new ArrayList<>();
    private final Map<Long, Integer> draftItemCounts = new HashMap<>();
    private long activeDraftId = 0L;

    /** Semua produk dari API, hanya untuk pencarian barcode */
    private final List<RetailProductItem> allProducts = new ArrayList<>();

    /** Hanya 1 baris per produk */
    private final List<RetailProductItem> scannedProducts = new ArrayList<>();

    /** key = productId, value = qty hasil scan */
    private final Map<Long, Integer> scannedQtyMap = new HashMap<>();

    /** simpan nama asli supaya bisa tampil "Nama x2" */
    private final Map<Long, String> baseNameMap = new HashMap<>();
    @Nullable
    private String lastScannedBarcode = null;
    private long lastScannedAt = 0L;
    private boolean offlineNoticeShown = false;
    private boolean noLocalDataNoticeShown = false;

    public static RetailPOSFragment newInstance(
            @NonNull String businessType,
            boolean useGridPosLayout,
            boolean showProductImagesInPos
    ) {
        RetailPOSFragment fragment = new RetailPOSFragment();
        Bundle args = new Bundle();
        args.putString(ARG_BUSINESS_TYPE, businessType);
        args.putBoolean(ARG_USE_GRID, useGridPosLayout);
        args.putBoolean(ARG_SHOW_IMAGES, showProductImagesInPos);
        fragment.setArguments(args);
        return fragment;
    }

    /**
     * Draft yang sedang aktif. Dipakai MainActivity untuk mengikat
     * client_order_id ke satu draft, supaya kunci yang ditahan lintas retry
     * tidak pernah terbawa ke keranjang draft lain.
     */
    public long getActiveDraftId() {
        return activeDraftId;
    }

    public void clearAfterCheckout() {
        try {
            Log.i(TAG, "checkout cleanup started pos=retail");
            completeActiveRetailDraftCheckout();
        } catch (Exception e) {
            Log.e(TAG, "cleanup failed with error pos=retail", e);
        }
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof RetailHostActions) {
            host = (RetailHostActions) context;
        } else {
            throw new IllegalStateException("Host activity must implement RetailHostActions");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        host = null;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        session = new SessionManager(requireContext());
        masterDataRepository = new MasterDataRepository(requireContext());

        Bundle args = getArguments();
        if (args != null) {
            businessType = args.getString(ARG_BUSINESS_TYPE, "retail");
            useGridPosLayout = args.getBoolean(ARG_USE_GRID, false);
            showProductImagesInPos = args.getBoolean(ARG_SHOW_IMAGES, false);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_retail_pos, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        applyRetailSystemBars(view);

        bindViews(view);
        setupDraftChips();
        setupHeaderActions();
        setupSearchBox();
        setupProductRecycler();
        setupClearDraftButton();
        setupCheckoutButton();
        initDraftStorage();
        loadShopHeader();
        loadProducts();

        updateSummary();
    }

    private void applyRetailSystemBars(@NonNull View root) {
        if (!isAdded()) return;

        Window window = requireActivity().getWindow();
        View decorView = window.getDecorView();

        // Pastikan status bar dan navigation bar tidak disembunyikan oleh fullscreen/immersive flag.
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        int flags = decorView.getSystemUiVisibility();
        flags &= ~View.SYSTEM_UI_FLAG_FULLSCREEN;
        flags &= ~View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        flags &= ~View.SYSTEM_UI_FLAG_IMMERSIVE;
        flags &= ~View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        flags &= ~View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        flags &= ~View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        decorView.setSystemUiVisibility(flags);

        // Biarkan fragment handle inset sendiri supaya aman di semua device.
        WindowCompat.setDecorFitsSystemWindows(window, false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(Color.parseColor("#22C55E"));
            window.setNavigationBarColor(Color.WHITE);
        }

        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(window, decorView);

        controller.show(WindowInsetsCompat.Type.statusBars());
        controller.show(WindowInsetsCompat.Type.navigationBars());

        // Status bar hijau, jadi icon harus putih.
        controller.setAppearanceLightStatusBars(false);

        // Navigation bar putih, jadi icon sebaiknya gelap.
        controller.setAppearanceLightNavigationBars(true);

        final int baseLeft = root.getPaddingLeft();
        final int baseTop = root.getPaddingTop();
        final int baseRight = root.getPaddingRight();
        final int baseBottom = root.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            int statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int navigationBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;

            v.setPadding(
                    baseLeft,
                    baseTop + statusBarTop,
                    baseRight,
                    baseBottom + navigationBottom
            );

            return insets;
        });

        ViewCompat.requestApplyInsets(root);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ApiClient.getInstance(requireContext()).cancelAll(TAG_PRODUCTS);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        draftExecutor.shutdownNow();
    }

    private void bindViews(@NonNull View root) {
        imgLogo = root.findViewById(R.id.imgLogo);
        tvBrand = root.findViewById(R.id.tvBrand);
        tvShopAddress = root.findViewById(R.id.tvShopAddress);
        btnHeaderBarcode = root.findViewById(R.id.btnHeaderBarcode);
        etSearchHint = root.findViewById(R.id.tvSearchHint);
        chipDraftA = root.findViewById(R.id.chipDraftA);
        chipDraftB = root.findViewById(R.id.chipDraftB);
        chipDraftC = root.findViewById(R.id.chipDraftC);
        chipAddDraft = root.findViewById(R.id.chipAddDraft);
        chipGroupDrafts = root.findViewById(R.id.chipGroupDrafts);

        rvProducts = root.findViewById(R.id.rvProducts);
        tvEmptyState = root.findViewById(R.id.tvEmptyState);
        progressProducts = root.findViewById(R.id.progressProducts);
        txtGrandTotal = root.findViewById(R.id.txtGrandTotal);
        txtItemCount = root.findViewById(R.id.txtItemCount);
        txtSectionSubtitle = root.findViewById(R.id.txtSectionSubtitle);
        btnCheckout = root.findViewById(R.id.btnCheckout);
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
            chipAddDraft.setText("+");
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
                PosDraftSnapshot snapshot = repository.loadSnapshot(POS_TYPE_RETAIL, loadItems);
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    applyDraftSnapshot(snapshot, loadItems);
                    if (!TextUtils.isEmpty(toastMessage)) {
                        Toast.makeText(requireContext(), toastMessage, Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to load POS drafts", e);
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
            loadRetailDraftItems(snapshot.items);
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
            chipAddDraft.setMinWidth(dp(42));
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
                PosDraftSnapshot snapshot = repository.activateDraft(POS_TYPE_RETAIL, draftId, true);
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    applyDraftSnapshot(snapshot, true);
                    Toast.makeText(requireContext(), toastMessage, Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to activate retail draft", e);
            }
        });
    }

    private void createAndActivateDraft() {
        PosDraftRepository repository = posDraftRepository;
        if (repository == null) return;

        draftExecutor.execute(() -> {
            try {
                PosDraftRepository.CreatedDraftResult result = repository.createAndActivateDraft(POS_TYPE_RETAIL);
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    applyDraftSnapshot(result.snapshot, true);
                    Toast.makeText(requireContext(), "Draft " + result.draftName + " aktif", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to create retail draft", e);
            }
        });
    }

    private void loadRetailDraftItems(@Nullable List<PosDraftItemEntity> items) {
        scannedProducts.clear();
        scannedQtyMap.clear();
        baseNameMap.clear();

        if (items != null) {
            for (PosDraftItemEntity item : items) {
                if (item == null || item.quantity <= 0) continue;
                long productId = parseLong(item.productId);
                if (productId <= 0L) continue;

                RetailProductItem product = createRetailItemFromDraft(item, productId);
                scannedProducts.add(product);
                scannedQtyMap.put(productId, Math.max(1, item.quantity));
                baseNameMap.put(productId, safe(item.productName));
            }
        }

        if (productAdapter != null) {
            productAdapter.setData(scannedProducts);
        }
        if (scannedProducts.isEmpty()) {
            showEmptyState("Scan barcode to add product");
        } else {
            hideEmptyState();
        }
        updateSummary();
    }

    @NonNull
    private RetailProductItem createRetailItemFromDraft(@NonNull PosDraftItemEntity item, long productId) {
        RetailProductItem catalog = findCatalogProductById(productId);
        RetailProductItem product = new RetailProductItem();
        if (catalog != null) {
            product.sku = catalog.sku;
            product.imageUrl = catalog.imageUrl;
            product.stock = catalog.stock;
            product.categoryId = catalog.categoryId;
            product.categoryName = catalog.categoryName;
            product.trackStock = catalog.trackStock;
            product.active = catalog.active;
            product.description = catalog.description;
        }
        product.id = productId;
        product.name = safe(item.productName);
        product.barcode = item.barcode != null ? item.barcode : "";
        product.price = item.price;
        return product;
    }

    @Nullable
    private RetailProductItem findCatalogProductById(long productId) {
        for (RetailProductItem item : allProducts) {
            if (item != null && item.id == productId) return item;
        }
        return null;
    }

    private long parseLong(@Nullable String value) {
        try {
            return value == null ? 0L : Long.parseLong(value.trim());
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void persistRetailDraftItem(@NonNull RetailProductItem item, int quantity) {
        PosDraftRepository repository = posDraftRepository;
        long draftId = activeDraftId;
        if (repository == null || draftId <= 0L || item.id <= 0L) return;

        int safeQty = Math.max(1, quantity);
        PosDraftItemEntity entity = new PosDraftItemEntity();
        entity.draftId = draftId;
        entity.productId = String.valueOf(item.id);
        entity.productName = getBaseName(item);
        entity.barcode = item.barcode;
        entity.quantity = safeQty;
        entity.price = item.price;
        entity.subtotal = item.price * safeQty;
        entity.itemType = "PRODUCT";
        entity.updatedAt = System.currentTimeMillis();

        draftExecutor.execute(() -> {
            try {
                repository.upsertDraftItem(entity);
                refreshDraftChipsFromRoom();
            } catch (Exception e) {
                Log.e(TAG, "Failed to save retail draft item", e);
            }
        });
    }

    private void refreshDraftChipsFromRoom() {
        PosDraftRepository repository = posDraftRepository;
        if (repository == null) return;
        try {
            PosDraftSnapshot snapshot = repository.loadSnapshot(POS_TYPE_RETAIL, false);
            mainHandler.post(() -> {
                if (!isAdded()) return;
                applyDraftSnapshot(snapshot, false);
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed to refresh retail draft chips", e);
        }
    }

    private void completeActiveRetailDraftCheckout() {
        PosDraftRepository repository = posDraftRepository;
        long draftId = activeDraftId;
        if (repository == null || draftId <= 0L) {
            loadRetailDraftItems(new ArrayList<>());
            Log.i(TAG, "draft cleanup success pos=retail draftId=" + draftId + " reason=no_active_draft");
            Log.i(TAG, "cart cleanup success pos=retail reason=no_active_draft");
            return;
        }

        draftExecutor.execute(() -> {
            try {
                PosDraftSnapshot snapshot = repository.cleanupActiveDraftAfterCheckout(POS_TYPE_RETAIL, draftId);
                Log.i(TAG, "draft cleanup success pos=retail draftId=" + draftId);
                mainHandler.post(() -> {
                    if (isAdded()) {
                        applyDraftSnapshot(snapshot, true);
                    }
                    Log.i(TAG, "cart cleanup success pos=retail");
                });
            } catch (Exception e) {
                Log.e(TAG, "cleanup failed with error pos=retail draftId=" + draftId, e);
            }
        });
    }

    private void confirmClearActiveDraftItems() {
        if (!isAdded()) return;
        if (scannedProducts.isEmpty()) {
            closeActiveEmptyDraft();
            return;
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Hapus semua item?")
                .setMessage("Semua produk dalam draft aktif akan dihapus.")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Hapus", (dialog, which) -> clearActiveDraftItems())
                .show();
    }

    private void clearActiveDraftItems() {
        PosDraftRepository repository = posDraftRepository;
        long draftId = activeDraftId;
        if (repository == null || draftId <= 0L) {
            loadRetailDraftItems(new ArrayList<>());
            return;
        }

        draftExecutor.execute(() -> {
            try {
                PosDraftSnapshot snapshot = repository.clearDraftItems(POS_TYPE_RETAIL, draftId);
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    applyDraftSnapshot(snapshot, true);
                    Toast.makeText(requireContext(), "Draft dikosongkan", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to clear retail draft items", e);
            }
        });
    }

    private void closeActiveEmptyDraft() {
        PosDraftRepository repository = posDraftRepository;
        long draftId = activeDraftId;
        if (repository == null || draftId <= 0L) {
            toast("Draft masih kosong");
            return;
        }

        draftExecutor.execute(() -> {
            try {
                PosDraftRepository.CloseDraftResult result =
                        repository.closeActiveDraftIfPossible(POS_TYPE_RETAIL, draftId);
                if (!result.closed) {
                    mainHandler.post(() -> {
                        if (isAdded()) toast("Draft masih kosong");
                    });
                    return;
                }

                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    applyDraftSnapshot(result.snapshot, true);
                    Toast.makeText(requireContext(), "Draft ditutup", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to close empty retail draft", e);
            }
        });
    }

    private void removeDisplayedItem(@NonNull RetailProductItem item, boolean showToast) {
        long productId = item.id;
        boolean removed = false;

        for (int i = scannedProducts.size() - 1; i >= 0; i--) {
            RetailProductItem current = scannedProducts.get(i);
            if (current != null && current.id == productId) {
                scannedProducts.remove(i);
                removed = true;
                break;
            }
        }

        if (!removed) {
            notifyCartChanged();
            return;
        }

        scannedQtyMap.remove(productId);
        baseNameMap.remove(productId);

        if (productAdapter != null) {
            productAdapter.setData(scannedProducts);
        }
        if (scannedProducts.isEmpty()) {
            showEmptyState("Scan barcode to add product");
        } else {
            hideEmptyState();
        }
        updateSummary();
        deleteRetailDraftItem(productId, showToast ? "Item dihapus" : null);
    }

    private void deleteRetailDraftItem(long productId, @Nullable String toastMessage) {
        PosDraftRepository repository = posDraftRepository;
        long draftId = activeDraftId;
        if (repository == null || draftId <= 0L || productId <= 0L) return;

        draftExecutor.execute(() -> {
            try {
                PosDraftSnapshot snapshot = repository.deleteDraftItem(
                        POS_TYPE_RETAIL,
                        draftId,
                        String.valueOf(productId),
                        "PRODUCT"
                );
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    applyDraftSnapshot(snapshot, false);
                    if (!TextUtils.isEmpty(toastMessage)) {
                        Toast.makeText(requireContext(), toastMessage, Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to delete retail draft item", e);
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

    private void setupHeaderActions() {
        if (btnHeaderBarcode != null) {
            btnHeaderBarcode.setOnClickListener(v -> {
                if (host != null) {
                    host.onRetailBarcodeClick();
                }
            });
        }

        if (imgLogo != null) {
            imgLogo.setClickable(true);
            imgLogo.setFocusable(true);
            imgLogo.setOnClickListener(this::openUserMenu);
        }
    }

    private void openUserMenu(@NonNull View anchor) {
        if (host != null) {
            host.onRetailUserMenuClick(anchor);
        }
    }

    private void setupSearchBox() {
        if (etSearchHint == null) return;

        etSearchHint.setOnEditorActionListener((v, actionId, event) -> {
            boolean isEnterKey = event != null
                    && event.getAction() == KeyEvent.ACTION_DOWN
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;

            boolean isSearchAction = actionId == EditorInfo.IME_ACTION_SEARCH
                    || actionId == EditorInfo.IME_ACTION_DONE;

            if (!isEnterKey && !isSearchAction) {
                return false;
            }

            String keyword = v.getText() != null ? v.getText().toString().trim() : "";
            if (keyword.isEmpty()) return true;

            onManualSearch(keyword);
            v.setText("");
            v.requestFocus();
            return true;
        });
    }

    private void setupProductRecycler() {
        if (rvProducts == null) return;

        rvProducts.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvProducts.setNestedScrollingEnabled(true);
        rvProducts.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        rvProducts.setHasFixedSize(false);

        productAdapter = new RetailProductAdapter();
        productAdapter.setShowImages(false);
        productAdapter.setListener(new RetailProductAdapter.Listener() {
            @Override
            public void onProductClick(@NonNull RetailProductItem item) {
                addProductToCart(item);
            }

            @Override
            public void onAddToCartClick(@NonNull RetailProductItem item) {
                addProductToCart(item);
            }

            @Override
            public int getQuantity(@NonNull RetailProductItem item) {
                return getScannedQty(item.id);
            }

            @Override
            public void onIncreaseQty(@NonNull RetailProductItem item) {
                increaseDisplayedQty(item);
            }

            @Override
            public void onDecreaseQty(@NonNull RetailProductItem item) {
                decreaseDisplayedQty(item);
            }
        });

        rvProducts.setAdapter(productAdapter);
        attachSwipeToDelete();
    }

    private void setupClearDraftButton() {
        if (txtItemCount == null) return;

        txtItemCount.setText(getString(R.string.action_clear_draft_items_symbol));
        txtItemCount.setOnClickListener(v -> confirmClearActiveDraftItems());
    }

    private void attachSwipeToDelete() {
        if (rvProducts == null) return;

        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(
                0,
                ItemTouchHelper.LEFT
        ) {
            @Override
            public boolean onMove(
                    @NonNull RecyclerView recyclerView,
                    @NonNull RecyclerView.ViewHolder viewHolder,
                    @NonNull RecyclerView.ViewHolder target
            ) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION) {
                    return;
                }
                RetailProductItem item = productAdapter != null ? productAdapter.getItemAt(position) : null;
                if (item == null) {
                    if (productAdapter != null) productAdapter.notifyItemChanged(position);
                    return;
                }
                removeDisplayedItem(item, true);
            }

            @Override
            public void onChildDraw(
                    @NonNull Canvas c,
                    @NonNull RecyclerView recyclerView,
                    @NonNull RecyclerView.ViewHolder viewHolder,
                    float dX,
                    float dY,
                    int actionState,
                    boolean isCurrentlyActive
            ) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX < 0) {
                    View itemView = viewHolder.itemView;
                    android.graphics.Paint paint = new android.graphics.Paint();
                    paint.setColor(Color.parseColor("#DC2626"));
                    c.drawRect(
                            itemView.getRight() + dX,
                            itemView.getTop(),
                            itemView.getRight(),
                            itemView.getBottom(),
                            paint
                    );

                    paint.setColor(Color.WHITE);
                    paint.setTypeface(Typeface.DEFAULT_BOLD);
                    paint.setTextSize(dp(14));
                    paint.setTextAlign(android.graphics.Paint.Align.CENTER);
                    float x = itemView.getRight() - dp(42);
                    float y = itemView.getTop()
                            + (itemView.getHeight() / 2f)
                            - ((paint.descent() + paint.ascent()) / 2f);
                    c.drawText("Hapus", x, y, paint);
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }
        };

        new ItemTouchHelper(callback).attachToRecyclerView(rvProducts);
    }

    private void setupCheckoutButton() {
        if (btnCheckout == null) return;

        btnCheckout.setEnabled(false);
        btnCheckout.setOnClickListener(v -> {
            if (host != null) {
                host.onRetailCartClick();
            }
        });
    }

    private void loadShopHeader() {
        final String token = session != null ? session.getToken() : null;

        if (token == null || token.trim().isEmpty()) {
            if (tvBrand != null) tvBrand.setText("Retail POS");
            if (tvShopAddress != null) tvShopAddress.setText("—");
            if (imgLogo != null) imgLogo.setImageResource(R.drawable.bg_logo_circle);
            return;
        }

        ShopRepository.fetchFirstShop(requireContext(), token, new ShopRepository.Callback() {
            @Override
            public void onSuccess(Shop shop) {
                if (!isAdded() || shop == null) return;

                String name = (shop.name != null && !shop.name.trim().isEmpty())
                        ? shop.name.trim()
                        : "Retail POS";

                String address = (shop.address != null && !shop.address.trim().isEmpty())
                        ? shop.address.trim()
                        : "—";

                if (tvBrand != null) tvBrand.setText(name);
                if (tvShopAddress != null) tvShopAddress.setText(address);

                String logoUrl = forceHttps(shop.logoUrl);
                if (imgLogo != null) {
                    if (logoUrl == null || logoUrl.trim().isEmpty()) {
                        imgLogo.setImageResource(R.drawable.bg_logo_circle);
                    } else {
                        Glide.with(requireContext())
                                .load(logoUrl)
                                .circleCrop()
                                .placeholder(R.drawable.bg_logo_circle)
                                .error(R.drawable.bg_logo_circle)
                                .into(imgLogo);
                    }
                }
            }

            @Override
            public void onEmpty() {
                if (!isAdded()) return;
                if (tvBrand != null) tvBrand.setText("Retail POS");
                if (tvShopAddress != null) tvShopAddress.setText("—");
                if (imgLogo != null) imgLogo.setImageResource(R.drawable.bg_logo_circle);
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                if (tvBrand != null) tvBrand.setText("Retail POS");
                if (tvShopAddress != null) tvShopAddress.setText("—");
                if (imgLogo != null) imgLogo.setImageResource(R.drawable.bg_logo_circle);
            }
        });
    }

    public void onManualSearch(@NonNull String keyword) {
        String clean = safe(keyword);
        if (clean.isEmpty()) return;

        RetailProductItem exactBarcode = findExactBarcodeMatch(clean);
        if (exactBarcode != null) {
            if (shouldIgnoreDuplicateScan(clean)) {
                return;
            }
            Log.i(TAG, "SCANNER: product lookup started");
            int qty = addOrIncrementScannedProduct(exactBarcode);
            addProductToCart(exactBarcode);
            Log.i(TAG, "SCANNER: product found and added");

            String baseName = getBaseName(exactBarcode);
            toast(qty > 1 ? baseName + " x" + qty : baseName + " added");

            if (etSearchHint != null) {
                etSearchHint.setText("");
                etSearchHint.requestFocus();
            }
            return;
        }

        List<RetailProductItem> matches = new ArrayList<>();
        String q = clean.toLowerCase(Locale.US);
        Log.i(TAG, "SCANNER: product lookup started");

        for (RetailProductItem item : allProducts) {
            if (item == null) continue;

            String name = safe(item.name).toLowerCase(Locale.US);
            if (name.contains(q)) {
                matches.add(item);
            }
        }

        if (matches.isEmpty()) {
            Log.i(TAG, "SCANNER: product not found");
            toast("Product not found");
            return;
        }

        if (matches.size() == 1) {
            RetailProductItem found = matches.get(0);
            int qty = addOrIncrementScannedProduct(found);
            addProductToCart(found);
            Log.i(TAG, "SCANNER: product found and added");

            String baseName = getBaseName(found);
            toast(qty > 1 ? baseName + " x" + qty : baseName + " added");

            if (etSearchHint != null) {
                etSearchHint.setText("");
                etSearchHint.requestFocus();
            }
            return;
        }

        toast("Found " + matches.size() + " products. Please refine search.");
    }

    public void onBarcodeScanned(@NonNull String barcode) {
        String clean = safe(barcode);
        if (clean.isEmpty()) return;

        RetailProductItem exact = findExactBarcodeMatch(clean);
        if (exact == null) {
            lookupBarcodeFromRoom(clean);
            return;
        }

        if (shouldIgnoreDuplicateScan(clean)) {
            return;
        }

        Log.i(TAG, "SCANNER: product lookup started");
        int qty = addOrIncrementScannedProduct(exact);
        addProductToCart(exact);
        Log.i(TAG, "SCANNER: product found and added");

        String baseName = getBaseName(exact);
        if (qty > 1) {
            toast(baseName + " x" + qty);
        } else {
            toast(baseName + " added");
        }
        if (etSearchHint != null) etSearchHint.requestFocus();
    }

    private void lookupBarcodeFromRoom(@NonNull String barcode) {
        if (masterDataRepository == null) {
            Log.i(TAG, "SCANNER: product not found");
            toast("Product not found");
            if (etSearchHint != null) etSearchHint.requestFocus();
            return;
        }

        masterDataRepository.findProductByBarcodeLocal(barcode, product -> {
            if (!isAdded()) return;

            if (product == null) {
                Log.i(TAG, "SCANNER: product not found");
                if (allProducts.isEmpty()) {
                    showNoLocalDataNoticeOnce();
                } else {
                    toast("Product not found");
                }
                if (etSearchHint != null) etSearchHint.requestFocus();
                return;
            }

            RetailProductItem item = toRetailProductItem(product);
            if (item.id <= 0 || !item.active) {
                Log.i(TAG, "SCANNER: product not found");
                toast("Product not found");
                if (etSearchHint != null) etSearchHint.requestFocus();
                return;
            }

            if (shouldIgnoreDuplicateScan(barcode)) {
                return;
            }

            int qty = addOrIncrementScannedProduct(item);
            addProductToCart(item);
            String baseName = getBaseName(item);
            toast(qty > 1 ? baseName + " x" + qty : baseName + " added");
            if (etSearchHint != null) etSearchHint.requestFocus();
        });
    }

    private boolean shouldIgnoreDuplicateScan(@NonNull String barcode) {
        long now = System.currentTimeMillis();
        if (barcode.equals(lastScannedBarcode) && now - lastScannedAt < SCANNER_DEBOUNCE_MS) {
            Log.i(TAG, "SCANNER: duplicate ignored barcode=" + barcode);
            return true;
        }
        lastScannedBarcode = barcode;
        lastScannedAt = now;
        Log.i(TAG, "SCANNER: input received barcode=" + barcode);
        return false;
    }

    @Nullable
    private RetailProductItem findExactBarcodeMatch(@NonNull String barcode) {
        for (RetailProductItem item : allProducts) {
            if (item != null && item.matchesBarcode(barcode)) {
                return item;
            }
        }
        return null;
    }

    private int addOrIncrementScannedProduct(@NonNull RetailProductItem item) {
        final long productId = item.id;

        if (productId <= 0) {
            scannedProducts.add(item);
            if (productAdapter != null) productAdapter.setData(scannedProducts);
            hideEmptyState();
            updateSummary();
            return 1;
        }

        RetailProductItem existing = findDisplayedProductById(productId);

        if (existing == null) {
            baseNameMap.put(productId, safe(item.name));
            scannedQtyMap.put(productId, 1);
            applyDisplayName(item, 1);
            scannedProducts.add(item);
        } else {
            int newQty = getScannedQty(productId) + 1;
            if (exceedsAvailableStock(existing, newQty)) {
                toast("Stok tidak cukup");
                return getScannedQty(productId);
            }
            scannedQtyMap.put(productId, newQty);
            applyDisplayName(existing, newQty);
            persistRetailDraftItem(existing, newQty);
        }

        if (productAdapter != null) {
            productAdapter.setData(scannedProducts);
        }

        hideEmptyState();
        updateSummary();
        if (existing == null) {
            persistRetailDraftItem(item, 1);
        }
        return getScannedQty(productId);
    }

    private void increaseDisplayedQty(@NonNull RetailProductItem item) {
        if (item.id <= 0) return;

        int nextQty = getScannedQty(item.id) + 1;
        if (exceedsAvailableStock(item, nextQty)) {
            toast("Stok tidak cukup");
            return;
        }

        scannedQtyMap.put(item.id, nextQty);
        applyDisplayName(item, nextQty);
        persistRetailDraftItem(item, nextQty);
        notifyCartChanged();
    }

    private void decreaseDisplayedQty(@NonNull RetailProductItem item) {
        if (item.id <= 0) return;

        int nextQty = Math.max(1, getScannedQty(item.id) - 1);
        scannedQtyMap.put(item.id, nextQty);
        applyDisplayName(item, nextQty);
        persistRetailDraftItem(item, nextQty);
        notifyCartChanged();
    }

    private boolean exceedsAvailableStock(@NonNull RetailProductItem item, int requestedQty) {
        int maxStock = (int) Math.floor(item.stock);
        return maxStock > 0 && requestedQty > maxStock;
    }

    private void notifyCartChanged() {
        if (productAdapter != null) {
            productAdapter.setData(scannedProducts);
        }
        updateSummary();
    }

    @Nullable
    private RetailProductItem findDisplayedProductById(long productId) {
        for (RetailProductItem item : scannedProducts) {
            if (item != null && item.id == productId) {
                return item;
            }
        }
        return null;
    }

    private int getScannedQty(long productId) {
        Integer qty = scannedQtyMap.get(productId);
        return qty == null ? 1 : Math.max(1, qty);
    }

    public int getQtyForProduct(long productId) {
        return getScannedQty(productId);
    }

    public int getQtyForProduct(int productId) {
        return getScannedQty(productId);
    }

    @NonNull
    private String getBaseName(@NonNull RetailProductItem item) {
        String base = baseNameMap.get(item.id);
        if (base == null || base.trim().isEmpty()) {
            base = safe(item.name);
            if (base.matches(".*\\sx\\d+$")) {
                base = base.replaceAll("\\sx\\d+$", "").trim();
            }
            baseNameMap.put(item.id, base);
        }
        return base;
    }

    private void applyDisplayName(@NonNull RetailProductItem item, int qty) {
        String base = getBaseName(item);
        item.name = base;
    }

    private void addProductToCart(@NonNull RetailProductItem item) {
        if (host == null) return;
        host.onRetailAddToCartRequested(RetailCartItem.fromProduct(item));
    }

    public int getTotalScannedQty() {
        int totalQty = 0;

        if (scannedProducts.isEmpty()) return 0;

        for (RetailProductItem item : scannedProducts) {
            if (item == null) continue;
            totalQty += getScannedQty(item.id);
        }
        return totalQty;
    }

    @NonNull
    public Money getGrandTotalMoney() {
        Money total = Money.zero();

        for (RetailProductItem item : scannedProducts) {
            if (item != null) {
                total = total.plus(Money.ofDouble(item.price).times(getScannedQty(item.id)));
            }
        }
        return total;
    }

    /**
     * @deprecated pakai {@link #getGrandTotalMoney()}; penjumlahannya sudah
     * eksak dan konversi hanya di akhir.
     */
    @Deprecated
    public double getGrandTotalAmount() {
        double total = getGrandTotalMoney().toDouble();

        return total;
    }

    public List<RetailProductItem> getScannedProducts() {
        return scannedProducts;
    }

    private void loadProducts() {
        showLoading(true);

        final String token = session.getToken();
        if (TextUtils.isEmpty(token)) {
            showLoading(false);
            showEmptyState("Token missing");
            return;
        }

        masterDataRepository.loadProductsRoomFirst(token, "all", new MasterDataRepository.ProductsCallback() {
            @Override
            public void onLocalProducts(@NonNull List<Product> products) {
                if (!isAdded()) return;
                applyCachedProducts(products);
                showLoading(false);
            }

            @Override
            public void onRemoteProducts(@NonNull List<Product> products) {
                if (!isAdded()) return;
                offlineNoticeShown = false;
                noLocalDataNoticeShown = false;
                applyCachedProducts(products);
                showLoading(false);
            }

            @Override
            public void onNoInternet(@NonNull List<Product> localProducts) {
                if (!isAdded()) return;
                showLoading(false);
                if (allProducts.isEmpty() && localProducts.isEmpty()) {
                    showNoLocalDataNoticeOnce();
                    showEmptyState(MasterDataRepository.MESSAGE_NO_LOCAL_POS_DATA);
                    return;
                }
                showOfflineNoticeOnce();
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                if (!isAdded()) return;
                showLoading(false);
                if (allProducts.isEmpty()) {
                    showEmptyState("Failed to load products");
                }
            }
        });
    }

    private void applyCachedProducts(@NonNull List<Product> products) {
        allProducts.clear();

        for (Product product : products) {
            RetailProductItem item = toRetailProductItem(product);
            if (item.id <= 0 || !item.active) continue;
            allProducts.add(item);
        }

        if (scannedProducts.isEmpty()) {
            showEmptyState("Scan barcode to add product");
        } else {
            hideEmptyState();
        }
        updateSummary();
    }

    @NonNull
    private RetailProductItem toRetailProductItem(@Nullable Product product) {
        RetailProductItem item = new RetailProductItem();
        if (product == null) return item;

        item.id = parseLong(product.id);
        item.name = safe(product.name);
        item.sku = safe(product.sku);
        item.barcode = safe(product.barcode);
        item.imageUrl = forceHttpsIfNeeded(product.imageUrl != null ? product.imageUrl : product.image_url);
        item.price = product.price;
        item.stock = product.stock;
        item.categoryId = parseLong(product.categoryId);
        item.categoryName = safe(product.categoryName);
        item.trackStock = product.trackStock;
        item.active = product.isActive;
        item.description = safe(product.description);
        return item;
    }

    @NonNull
    private String forceHttpsIfNeeded(@Nullable String url) {
        String clean = safe(url);
        return clean.startsWith("http://") ? forceHttps(clean) : clean;
    }

    private void showOfflineNoticeOnce() {
        if (offlineNoticeShown) return;
        offlineNoticeShown = true;
        toast(MasterDataRepository.MESSAGE_NO_INTERNET_SHOWING_LOCAL);
    }

    private void showNoLocalDataNoticeOnce() {
        if (noLocalDataNoticeShown) return;
        noLocalDataNoticeShown = true;
        toast(MasterDataRepository.MESSAGE_NO_LOCAL_POS_DATA);
    }

    private void updateSummary() {
        int itemCount = getTotalScannedQty();
        double total = getGrandTotalAmount();

        if (txtItemCount != null) {
            boolean canClearOrClose = itemCount > 0 || posDrafts.size() > 1;
            txtItemCount.setText(getString(R.string.action_clear_draft_items_symbol));
            txtItemCount.setEnabled(true);
            txtItemCount.setAlpha(canClearOrClose ? 1f : 0.35f);
        }

        if (txtGrandTotal != null) {
            txtGrandTotal.setText(getGrandTotalMoney().format());
        }

        if (txtSectionSubtitle != null) {
            txtSectionSubtitle.setText(
                    itemCount > 0
                            ? "Review scanned items before checkout"
                            : "Items will appear after barcode scan"
            );
        }

        if (btnCheckout != null) {
            btnCheckout.setEnabled(itemCount > 0);
            btnCheckout.setAlpha(itemCount > 0 ? 1f : 0.6f);
        }
    }

    private void showLoading(boolean loading) {
        if (progressProducts != null) {
            progressProducts.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (rvProducts != null) {
            rvProducts.setVisibility(loading ? View.INVISIBLE : View.VISIBLE);
        }
    }

    private void showEmptyState(@NonNull String message) {
        if (tvEmptyState != null) {
            tvEmptyState.setVisibility(View.VISIBLE);
            tvEmptyState.setText(message);
        }
        if (rvProducts != null) {
            rvProducts.setVisibility(View.GONE);
        }
    }

    private void hideEmptyState() {
        if (tvEmptyState != null) {
            tvEmptyState.setVisibility(View.GONE);
        }
        if (rvProducts != null && (progressProducts == null || progressProducts.getVisibility() != View.VISIBLE)) {
            rvProducts.setVisibility(View.VISIBLE);
        }
    }

    private void toast(@NonNull String message) {
        if (!isAdded()) return;
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    @NonNull
    private String safe(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    @Nullable
    private String forceHttps(@Nullable String url) {
        if (url == null) return null;
        String clean = url.trim();
        if (clean.startsWith("http://")) {
            return "https://" + clean.substring("http://".length());
        }
        return clean;
    }

}
