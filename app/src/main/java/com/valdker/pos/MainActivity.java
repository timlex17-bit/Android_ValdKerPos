package com.valdker.pos;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;
import com.valdker.pos.utils.ErrorHandler;
import com.valdker.pos.utils.NetworkUtils;
import com.valdker.pos.utils.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.bumptech.glide.Glide;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.zxing.integration.android.IntentIntegrator;
import com.valdker.pos.adapters.CategoryAdapter;
import com.valdker.pos.auth.AuthEvents;
import com.valdker.pos.cart.CartManager;
import com.valdker.pos.drafts.PosDraftEntity;
import com.valdker.pos.drafts.PosDraftItemEntity;
import com.valdker.pos.drafts.PosDraftMapper;
import com.valdker.pos.drafts.PosDraftRepository;
import com.valdker.pos.drafts.PosDraftSnapshot;
import com.valdker.pos.models.CartItem;
import com.valdker.pos.models.Category;
import com.valdker.pos.models.Customer;
import com.valdker.pos.models.Shop;
import com.valdker.pos.network.ApiClient;
import com.valdker.pos.network.ApiConfig;
import com.valdker.pos.repositories.CheckoutConfigRepository;
import com.valdker.pos.repositories.CustomerRepository;
import com.valdker.pos.repositories.MasterDataRepository;
import com.valdker.pos.repositories.OfflineOrderRepository;
import com.valdker.pos.repositories.ShiftRepository;
import com.valdker.pos.repositories.ShopRepository;
import com.valdker.pos.ui.CartFragment;
import com.valdker.pos.ui.ProductsFragment;
import com.valdker.pos.ui.checkout.BankAccountItem;
import com.valdker.pos.ui.checkout.NativeCheckoutDialogFragment;
import com.valdker.pos.ui.checkout.PaymentMethodItem;
import com.valdker.pos.ui.offlineorders.PendingOrdersActivity;
import com.valdker.pos.ui.retail.RetailCartItem;
import com.valdker.pos.ui.retail.RetailPOSFragment;
import com.valdker.pos.ui.retail.RetailProductItem;
import com.valdker.pos.ui.shift.ShiftOpenDialogFragment;
import com.valdker.pos.workshop.WorkshopPOSFragment;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings("deprecation")
public class MainActivity extends AppCompatActivity
        implements WorkshopPOSFragment.WorkshopHostActions,
        RetailPOSFragment.RetailHostActions,
        CartFragment.DraftLifecycleHost {

    private interface ReceiptPrintCallback {
        void onComplete(@NonNull com.valdker.pos.print.BluetoothPrinterManager.PrintResult result);
    }

    private static final String TAG = "MAIN_NATIVE";
    private static final String TAG_SHIFT_DIALOG = "SHIFT_OPEN_DIALOG";
    private static final String TAG_BARCODE_DIALOG = "BARCODE_DIALOG";
    private static final String TAG_RETAIL_ORDER_CREATE = "RETAIL_ORDER_CREATE";
    private static final String TAG_NATIVE_CHECKOUT_DIALOG = "native_checkout";
    private static final String POS_TYPE_RESTAURANT = "restaurant";
    private static final String POS_TYPE_RETAIL = "retail";

    private static final int CAMERA_REQUEST = 100;
    private static final long BARCODE_DEBOUNCE_MS = 700L;

    private volatile boolean barcodeDispatchRunning = false;
    @Nullable
    private String pendingBarcode = null;
    @Nullable
    private String lastBarcodeInput = null;
    private long lastBarcodeInputAt = 0L;

    private String businessType = "retail";
    private boolean useGridPosLayout = false;
    private boolean showProductImagesInPos = false;
    private boolean enableBarcodeScan = true;
    private boolean enableDineIn = false;
    private boolean enableTakeaway = false;
    private boolean enableDelivery = false;
    private boolean enableTableNumber = false;
    private boolean enableSplitPayment = false;

    private volatile boolean shiftGateRunning = false;
    private volatile boolean isCheckingShift = false;
    private volatile boolean shiftGateAlreadyPassed = false;
    private volatile boolean isShiftDialogShowing = false;

    private volatile boolean closeShiftFlowRunning = false;
    private volatile boolean logoutFlowRunning = false;
    private volatile boolean retailOrderSubmitting = false;
    private volatile boolean retailCheckoutDialogOpening = false;

    // Satu kunci idempotensi per draft, dipertahankan lintas percobaan supaya
    // retry manual memakai kunci yang sama dan dedup di server bisa bekerja.
    // Harus berupa peta, bukan satu slot: kasir bisa berpindah draft lalu
    // kembali, dan draft yang ditinggalkan wajib menemukan kunci lamanya utuh.
    // Entri dibuang saat draft itu benar-benar tersimpan.
    private final Map<Long, String> pendingRetailClientOrderIds = new ConcurrentHashMap<>();

    // Idem untuk jalur restoran/legacy. Dipegang di sini, bukan di CartFragment,
    // karena fragment itu dibuat ulang tiap kali overlay keranjang dibuka.
    private final Map<Long, String> pendingCartClientOrderIds = new ConcurrentHashMap<>();

    private volatile boolean categoriesAppliedFromOnline = false;

    private TextView tvCartBadge;
    private PopupWindow userPopup;

    private View btnBarcode;
    private EditText etSearch;
    private String allIconUrl = null;
    private CategoryAdapter categoryAdapter;
    private final List<Category> categoryList = new ArrayList<>();

    private Chip chipDraftA;
    private Chip chipDraftB;
    private Chip chipDraftC;
    private Chip chipAddDraft;
    private ChipGroup chipGroupDrafts;
    @Nullable
    private PosDraftRepository posDraftRepository;
    private final ExecutorService draftExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<PosDraftEntity> posDrafts = new ArrayList<>();
    private final Map<Long, Integer> draftItemCounts = new HashMap<>();
    private long activeDraftId = 0L;
    private boolean loadingDraftFromRoom = false;
    private boolean initialDraftLoadPending = true;

    private SessionManager session;
    private String cachedUsername = "admin";
    private String cachedRole = "cashier";

    private ImageView imgLogo;
    private TextView tvShopAddress;

    private final CartManager.Listener cartListener = this::onMainCartChanged;

    private boolean isActivityAlive() {
        return !isFinishing() && !isDestroyed();
    }

    private void safeUi(@NonNull Runnable r) {
        if (!isActivityAlive()) return;
        runOnUiThread(() -> {
            if (!isActivityAlive()) return;
            r.run();
        });
    }

    @NonNull
    private String safeTrim(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    private String safe(@Nullable String v, @NonNull String fallback) {
        if (v == null) return fallback;
        String s = v.trim();
        return s.isEmpty() ? fallback : s;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private boolean isWorkshopBusiness() {
        return "workshop".equalsIgnoreCase(safeTrim(businessType));
    }

    private boolean isRestaurantBusiness() {
        return "restaurant".equalsIgnoreCase(safeTrim(businessType));
    }

    private boolean isRetailBusiness() {
        String bt = safeTrim(businessType).toLowerCase();
        return bt.isEmpty() || "retail".equals(bt);
    }

    private final BroadcastReceiver logoutReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.w(TAG, "Force logout received. Clearing session and returning to Login.");
            session.clearShift();
            session.clear();
            goToLogin();
        }
    };

    private void ensureShiftOpenOrBlock() {
        if (BuildConfig.DEBUG) Log.d(TAG, "SHIFT_GATE: ensureShiftOpenOrBlock called");

        if (shiftGateAlreadyPassed) {
            if (BuildConfig.DEBUG) Log.d(TAG, "SHIFT_GATE: skip because gate already passed");
            return;
        }

        if (isCheckingShift || shiftGateRunning) {
            if (BuildConfig.DEBUG) Log.d(TAG, "SHIFT_GATE: skip because checking already in progress");
            return;
        }

        final SessionManager sm = session;
        final ShiftRepository repo = new ShiftRepository(MainActivity.this, sm);

        if (isLocalShiftOpen(sm)) {
            shiftGateAlreadyPassed = true;
            if (BuildConfig.DEBUG) Log.d(TAG, "SHIFT_GATE: skip because local shift is already open");
            return;
        }

        if (isShiftDialogShowing) {
            if (BuildConfig.DEBUG) Log.d(TAG, "SHIFT_GATE: skip because dialog already showing");
            return;
        }

        if (!isNetworkAvailable()) {
            safeUi(() -> ErrorHandler.showNoInternet(MainActivity.this, null));
            return;
        }

        shiftGateRunning = true;
        isCheckingShift = true;

        safeUi(() -> {
            if (BuildConfig.DEBUG) Log.d(TAG, "SHIFT_GATE: checking backend current shift...");

            final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            final Runnable timeout = () -> {
                if (BuildConfig.DEBUG) Log.d(TAG, "SHIFT_GATE: backend current shift check timeout");
                isCheckingShift = false;
                shiftGateRunning = false;
                showShiftDialogOnce(repo, sm);
            };

            h.postDelayed(timeout, 3500);

            repo.getCurrent(new ShiftRepository.CurrentCallback() {
                @Override
                public void onSuccess(boolean open, com.valdker.pos.models.Shift shift) {
                    h.removeCallbacks(timeout);
                    isCheckingShift = false;

                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "SHIFT_GATE: backend shift open=" + open
                                + " id=" + (shift != null ? shift.id : 0));
                    }

                    if (open && shift != null && shift.id > 0) {
                        persistOpenShift(sm, shift);
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "SHIFT_GATE: current shift found, saved locally id=" + shift.id);
                        }
                        shiftGateRunning = false;
                        return;
                    }

                    shiftGateRunning = false;
                    showShiftDialogOnce(repo, sm);
                }

                @Override
                public void onError(@NonNull String message) {
                    h.removeCallbacks(timeout);
                    if (BuildConfig.DEBUG) Log.d(TAG, "SHIFT_GATE: backend current shift check error: " + message);
                    isCheckingShift = false;
                    shiftGateRunning = false;
                    showShiftDialogOnce(repo, sm);
                }
            });
        });
    }

    private void showShiftDialogOnce(@NonNull ShiftRepository repo, @NonNull SessionManager sm) {
        if (shiftGateAlreadyPassed || isLocalShiftOpen(sm)) {
            shiftGateAlreadyPassed = true;
            if (BuildConfig.DEBUG) Log.d(TAG, "SHIFT_GATE: skip dialog because shift is already open");
            return;
        }

        if (!isActivityAlive()) {
            isShiftDialogShowing = false;
            return;
        }

        Fragment existing = getSupportFragmentManager().findFragmentByTag(TAG_SHIFT_DIALOG);
        if (existing != null) {
            if (BuildConfig.DEBUG) Log.d(TAG, "SHIFT_GATE: dialog already shown (fragment exists)");
            isShiftDialogShowing = true;
            return;
        }

        if (isShiftDialogShowing) {
            if (BuildConfig.DEBUG) Log.d(TAG, "SHIFT_GATE: skip because dialog already showing");
            return;
        }

        isShiftDialogShowing = true;
        if (BuildConfig.DEBUG) Log.d(TAG, "SHIFT_GATE: showing Open Shift dialog");

        ShiftOpenDialogFragment dlg = new ShiftOpenDialogFragment();
        dlg.setCancelable(false);

        dlg.setListener((openingCash, note) ->
                safeUi(() -> doOpenShift(openingCash, note, repo, sm, dlg))
        );

        dlg.show(getSupportFragmentManager(), TAG_SHIFT_DIALOG);
    }

    private void doOpenShift(@NonNull String openingCash,
                             @NonNull String note,
                             @NonNull ShiftRepository repo,
                             @NonNull SessionManager sm,
                             @NonNull ShiftOpenDialogFragment dlg) {

        if (!isNetworkAvailable()) {
            isShiftDialogShowing = false;
            ErrorHandler.showNoInternet(MainActivity.this, null);
            return;
        }

        repo.openShift(openingCash, note, new ShiftRepository.OpenCallback() {

            @Override
            public void onSuccess(@NonNull com.valdker.pos.models.Shift shift) {
                persistOpenShift(sm, shift);
                if (BuildConfig.DEBUG) Log.d(TAG, "SHIFT_GATE: open shift success, saved locally id=" + shift.id);

                safeUi(() -> {
                    isShiftDialogShowing = false;
                    dlg.dismissAllowingStateLoss();
                });
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                Log.e(TAG, "SHIFT_GATE open error: " + statusCode + " " + message);

                if (statusCode == 409) {
                    repo.getCurrent(new ShiftRepository.CurrentCallback() {
                        @Override
                        public void onSuccess(boolean open, com.valdker.pos.models.Shift shift) {
                            if (open && shift != null && shift.id > 0) {
                                persistOpenShift(sm, shift);
                                if (BuildConfig.DEBUG) {
                                    Log.d(TAG, "SHIFT_GATE: 409 current shift found, saved locally id=" + shift.id);
                                }
                                safeUi(() -> {
                                    isShiftDialogShowing = false;
                                    dlg.dismissAllowingStateLoss();
                                });
                            } else {
                                safeUi(() -> Toast.makeText(
                                        MainActivity.this,
                                        getString(R.string.msg_shift_open_load_failed),
                                        Toast.LENGTH_LONG
                                ).show());
                            }
                        }

                        @Override
                        public void onError(@NonNull String msg) {
                            isShiftDialogShowing = false;
                            safeUi(() -> ErrorHandler.handleApiError(MainActivity.this, msg));
                        }
                    });
                    return;
                }

                isShiftDialogShowing = false;
                safeUi(() -> ErrorHandler.handleApiError(MainActivity.this, message));
            }
        });
    }

    private void persistOpenShift(@NonNull SessionManager sm, @NonNull com.valdker.pos.models.Shift shift) {
        String openingCash = (shift.opening_cash == null || shift.opening_cash.trim().isEmpty())
                ? "0.00"
                : shift.opening_cash;
        sm.saveOpenShift(shift.id, shift.status, openingCash);
        shiftGateAlreadyPassed = true;
        isCheckingShift = false;
        shiftGateRunning = false;
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "SHIFT_GATE: saving shift id=" + shift.id
                    + " status=" + shift.status
                    + " opening_cash=" + openingCash);
        }
    }

    private boolean isLocalShiftOpen(@NonNull SessionManager sm) {
        boolean sessionOpen = false;
        int sid = 0;
        String status = "";

        try {
            sessionOpen = sm.isShiftOpen();
            sid = sm.getShiftId();
            status = sm.getShiftStatus();
        } catch (Exception ignored) {
        }

        boolean open = sid > 0 && (sessionOpen || "OPEN".equalsIgnoreCase(status != null ? status.trim() : ""));

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "SHIFT_GATE: local shift open=" + open
                    + " flag=" + sessionOpen
                    + " id=" + sid
                    + " status=" + status);
        }

        return open;
    }

    private boolean isNetworkAvailable() {
        return NetworkUtils.isNetworkAvailable(this);
    }

    private void loadBusinessConfig() {
        businessType = session.getBusinessType();
        businessType = safeTrim(businessType).isEmpty() ? "retail" : safeTrim(businessType);

        useGridPosLayout = session.useGridPosLayout();
        showProductImagesInPos = session.showProductImagesInPos();
        enableBarcodeScan = session.enableBarcodeScan();
        enableDineIn = session.enableDineIn();
        enableTakeaway = session.enableTakeaway();
        enableDelivery = session.enableDelivery();
        enableTableNumber = session.enableTableNumber();
        enableSplitPayment = session.enableSplitPayment();

        Log.i(TAG, "BUSINESS_CONFIG"
                + " type=" + businessType
                + " useGrid=" + useGridPosLayout
                + " showImages=" + showProductImagesInPos
                + " barcode=" + enableBarcodeScan
                + " dineIn=" + enableDineIn
                + " takeaway=" + enableTakeaway
                + " delivery=" + enableDelivery
                + " tableNumber=" + enableTableNumber
                + " splitPayment=" + enableSplitPayment);
    }

    private void loadShopHeader() {
        final String token = session.getToken();

        ShopRepository.fetchFirstShop(this, token, new ShopRepository.Callback() {

            @Override
            public void onSuccess(Shop shop) {
                if (shop == null) return;

                allIconUrl = forceHttps(shop.allCategoryIconUrl);

                if (categoryAdapter != null && !categoryList.isEmpty()) {
                    categoryList.get(0).iconUrl = allIconUrl;
                    categoryAdapter.setData(categoryList);
                }

                TextView tvBrand = findViewById(R.id.tvBrand);
                if (tvBrand != null) {
                    String name = (shop.name != null && !shop.name.trim().isEmpty())
                            ? shop.name.trim()
                            : "—";
                    tvBrand.setText(name);
                }

                if (tvShopAddress != null) {
                    String address = (shop.address != null) ? shop.address.trim() : "";
                    tvShopAddress.setText(address.isEmpty() ? "—" : address);
                }

                if (imgLogo == null) return;

                String logoUrl = forceHttps(shop.logoUrl);
                if (logoUrl == null || logoUrl.trim().isEmpty()) {
                    imgLogo.setImageResource(R.drawable.bg_logo_circle);
                    return;
                }

                Glide.with(MainActivity.this)
                        .load(logoUrl)
                        .circleCrop()
                        .placeholder(R.drawable.bg_logo_circle)
                        .error(R.drawable.bg_logo_circle)
                        .into(imgLogo);
            }

            @Override
            public void onEmpty() {
                TextView tvBrand = findViewById(R.id.tvBrand);
                if (tvBrand != null) tvBrand.setText("—");
                if (tvShopAddress != null) tvShopAddress.setText("—");
                if (imgLogo != null) imgLogo.setImageResource(R.drawable.bg_logo_circle);
            }

            @Override
            public void onError(String message) {
                if (tvShopAddress != null) tvShopAddress.setText("—");
                if (imgLogo != null) imgLogo.setImageResource(R.drawable.bg_logo_circle);
            }
        });
    }

    private String forceHttps(String url) {
        if (url == null) return null;
        url = url.trim();
        if (url.startsWith("http://")) return "https://" + url.substring("http://".length());
        return url;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        applySavedLanguage();
        super.onCreate(savedInstanceState);
        setupPosSystemBars();

        session = new SessionManager(this);

        if (!session.isLoggedIn()) {
            goToLogin();
            return;
        }

        loadBusinessConfig();
        setContentView(R.layout.activity_main);
        applyPosSystemBarInsets();

        tvCartBadge = findViewById(R.id.tvCartBadge);
        btnBarcode = findViewById(R.id.btnBarcode);

        View vSearch = findViewById(R.id.tvSearchHint);
        if (vSearch instanceof EditText) {
            etSearch = (EditText) vSearch;
        }

        imgLogo = findViewById(R.id.imgLogo);
        tvShopAddress = findViewById(R.id.tvShopAddress);
        chipDraftA = findViewById(R.id.chipDraftA);
        chipDraftB = findViewById(R.id.chipDraftB);
        chipDraftC = findViewById(R.id.chipDraftC);
        chipAddDraft = findViewById(R.id.chipAddDraft);
        chipGroupDrafts = findViewById(R.id.chipGroupDrafts);

        cachedUsername = safe(session.getUsername(), "admin");
        cachedRole = safe(session.getRole(), "cashier");

        applyBusinessTypeUi();
        setupSearchBox();
        setupNativeButtons();

        if (!isWorkshopBusiness() && !isRetailBusiness()) {
            setupDraftChips();
            initDraftStorage();
            setupCategories();
        }

        ensureDefaultFragment();
        setupBackHandling();

        getSupportFragmentManager().addOnBackStackChangedListener(this::hideOverlayIfNoOverlayFragments);

        loadShopHeader();
        refreshCartBadge();

        ensureShiftOpenOrBlock();
        syncPendingOrdersIfOnline();
    }

    private void setupPosSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

        View decor = getWindow().getDecorView();
        int flags = decor.getSystemUiVisibility();
        flags &= ~View.SYSTEM_UI_FLAG_FULLSCREEN;
        flags &= ~View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        flags &= ~View.SYSTEM_UI_FLAG_IMMERSIVE;
        flags &= ~View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        flags &= ~View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        flags &= ~View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        flags &= ~View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        decor.setSystemUiVisibility(flags);

        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.status_bar_green));
        getWindow().setNavigationBarColor(Color.WHITE);

        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), decor);

        if (controller != null) {
            controller.show(WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.navigationBars());
            controller.setAppearanceLightStatusBars(true);
            controller.setAppearanceLightNavigationBars(true);
        }

        if (BuildConfig.DEBUG) {
            Log.d("STATUS_BAR_THEME", "screen=MainActivity color=status_bar_green icons=dark");
            Log.d(TAG, "STATUS_BAR: visible=true, color=status_bar_green, lightIcons=false");
        }
    }

    private void applyPosSystemBarInsets() {
        View root = findViewById(R.id.root);
        View statusBarScrim = findViewById(R.id.statusBarScrim);
        View nativeHeader = findViewById(R.id.nativeHeader);
        View fragmentContainer = findViewById(R.id.fragmentContainer);
        View bottomCategoryBar = findViewById(R.id.bottomCategoryBar);
        View overlayContainer = findViewById(R.id.overlayContainer);

        if (root == null || nativeHeader == null) return;

        final int rootStartLeft = root.getPaddingLeft();
        final int rootStartTop = root.getPaddingTop();
        final int rootStartRight = root.getPaddingRight();
        final int rootStartBottom = root.getPaddingBottom();

        final ViewGroup.MarginLayoutParams headerLp =
                nativeHeader.getLayoutParams() instanceof ViewGroup.MarginLayoutParams
                        ? (ViewGroup.MarginLayoutParams) nativeHeader.getLayoutParams()
                        : null;
        final int headerStartTopMargin = headerLp != null ? headerLp.topMargin : 0;

        final int fragmentStartBottomPadding =
                fragmentContainer != null ? fragmentContainer.getPaddingBottom() : 0;
        final ViewGroup.MarginLayoutParams bottomBarLp =
                bottomCategoryBar != null
                        && bottomCategoryBar.getLayoutParams() instanceof ViewGroup.MarginLayoutParams
                        ? (ViewGroup.MarginLayoutParams) bottomCategoryBar.getLayoutParams()
                        : null;
        final int bottomBarStartBottomMargin = bottomBarLp != null ? bottomBarLp.bottomMargin : 0;
        final int overlayStartBottomPadding =
                overlayContainer != null ? overlayContainer.getPaddingBottom() : 0;
        final int overlayStartTopPadding =
                overlayContainer != null ? overlayContainer.getPaddingTop() : 0;

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            Insets navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars());

            v.setPadding(
                    rootStartLeft,
                    rootStartTop,
                    rootStartRight,
                    rootStartBottom
            );

            if (headerLp != null) {
                headerLp.topMargin = headerStartTopMargin + statusBars.top;
                nativeHeader.setLayoutParams(headerLp);
            }

            if (statusBarScrim != null) {
                ViewGroup.LayoutParams scrimLp = statusBarScrim.getLayoutParams();
                if (scrimLp != null && scrimLp.height != statusBars.top) {
                    scrimLp.height = statusBars.top;
                    statusBarScrim.setLayoutParams(scrimLp);
                }
            }

            if (fragmentContainer != null) {
                fragmentContainer.setPadding(
                        fragmentContainer.getPaddingLeft(),
                        fragmentContainer.getPaddingTop(),
                        fragmentContainer.getPaddingRight(),
                        fragmentStartBottomPadding
                );
            }

            if (bottomBarLp != null && bottomCategoryBar != null
                    && bottomCategoryBar.getVisibility() != View.GONE) {
                bottomBarLp.bottomMargin = bottomBarStartBottomMargin + navigationBars.bottom;
                bottomCategoryBar.setLayoutParams(bottomBarLp);
            }

            if (overlayContainer != null) {
                overlayContainer.setPadding(
                        overlayContainer.getPaddingLeft(),
                        overlayStartTopPadding + statusBars.top,
                        overlayContainer.getPaddingRight(),
                        overlayStartBottomPadding + navigationBars.bottom
                );
            }

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "POS_INSETS: statusTop=" + statusBars.top
                        + " navBottom=" + navigationBars.bottom
                        + " headerTopMargin=" + (headerStartTopMargin + statusBars.top)
                        + " rootPaddingTop=" + rootStartTop
                        + " bottomBarMargin=" + (bottomBarStartBottomMargin + navigationBars.bottom));
            }

            return insets;
        });

        ViewCompat.requestApplyInsets(root);
    }

    private void applySavedLanguage() {
        SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        String languageCode = prefs.getString("app_language", "tet");

        Locale locale;
        switch (languageCode) {
            case "en":
                locale = Locale.ENGLISH;
                break;
            case "id":
                locale = new Locale("id");
                break;
            case "zh":
                locale = Locale.SIMPLIFIED_CHINESE;
                break;
            case "tet":
            default:
                locale = new Locale("tet");
                break;
        }

        Locale.setDefault(locale);

        Resources resources = getResources();
        Configuration config = new Configuration(resources.getConfiguration());
        config.setLocale(locale);
        resources.updateConfiguration(config, resources.getDisplayMetrics());
    }

    private void applyBusinessTypeUi() {
        View nativeHeader = findViewById(R.id.nativeHeader);
        View bottomCategoryBar = findViewById(R.id.bottomCategoryBar);
        View btnBarcodeView = findViewById(R.id.btnBarcode);
        View btnCartView = findViewById(R.id.btnCart);
        View rvCategories = findViewById(R.id.rvCategories);
        View searchView = findViewById(R.id.tvSearchHint);
        View fragmentContainer = findViewById(R.id.fragmentContainer);

        if (fragmentContainer == null) return;

        androidx.constraintlayout.widget.ConstraintLayout.LayoutParams lp =
                (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams)
                        fragmentContainer.getLayoutParams();

        if (isWorkshopBusiness() || isRetailBusiness()) {
            if (nativeHeader != null) nativeHeader.setVisibility(View.GONE);
            if (bottomCategoryBar != null) bottomCategoryBar.setVisibility(View.GONE);
            if (btnBarcodeView != null) btnBarcodeView.setVisibility(View.GONE);
            if (btnCartView != null) btnCartView.setVisibility(View.GONE);
            if (rvCategories != null) rvCategories.setVisibility(View.GONE);
            if (searchView != null) searchView.setVisibility(View.GONE);

            lp.topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
            lp.topToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET;

            lp.bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
            lp.bottomToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET;

            fragmentContainer.setLayoutParams(lp);
            return;
        }

        if (nativeHeader != null) nativeHeader.setVisibility(View.VISIBLE);
        if (bottomCategoryBar != null) bottomCategoryBar.setVisibility(View.VISIBLE);
        if (btnBarcodeView != null) btnBarcodeView.setVisibility(enableBarcodeScan ? View.VISIBLE : View.GONE);
        if (btnCartView != null) btnCartView.setVisibility(View.VISIBLE);
        if (rvCategories != null) rvCategories.setVisibility(View.VISIBLE);
        if (searchView != null) searchView.setVisibility(View.VISIBLE);

        lp.topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET;
        lp.topToBottom = R.id.nativeHeader;

        lp.bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET;
        lp.bottomToTop = R.id.bottomCategoryBar;

        fragmentContainer.setLayoutParams(lp);
    }

    private void setupSearchBox() {
        if (etSearch == null) return;

        etSearch.setOnEditorActionListener((v, actionId, event) -> {
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

            Fragment f = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);

            if (f instanceof RetailPOSFragment) {
                Log.i(TAG, "SCANNER: input received barcode=" + keyword);
                ((RetailPOSFragment) f).onManualSearch(keyword);
            } else if (f instanceof ProductsFragment) {
                Log.i(TAG, "SCANNER: input received barcode=" + keyword);
                ((ProductsFragment) f).onManualSearch(keyword);
            }

            v.setText("");
            v.requestFocus();
            return true;
        });
    }

    private void sendBarcodeToActivePosFragment(@NonNull String barcode) {
        String clean = safeTrim(barcode);
        if (clean.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (clean.equals(lastBarcodeInput) && now - lastBarcodeInputAt < BARCODE_DEBOUNCE_MS) {
            Log.i(TAG, "SCANNER: duplicate ignored barcode=" + clean);
            return;
        }
        lastBarcodeInput = clean;
        lastBarcodeInputAt = now;
        Log.i(TAG, "SCANNER: input received barcode=" + clean);

        pendingBarcode = clean;
        dispatchPendingBarcodeToActivePosFragment();
    }

    private void dispatchPendingBarcodeToActivePosFragment() {
        final String barcode = pendingBarcode;
        if (barcode == null || barcode.trim().isEmpty()) {
            barcodeDispatchRunning = false;
            return;
        }

        if (barcodeDispatchRunning) {
            return;
        }

        barcodeDispatchRunning = true;

        final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        final int[] tries = {0};
        final int maxTry = 12;

        Runnable task = new Runnable() {
            @Override
            public void run() {
                if (!isActivityAlive()) {
                    barcodeDispatchRunning = false;
                    return;
                }

                Fragment f = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);

                try {
                    if (f instanceof com.valdker.pos.ui.retail.RetailPOSFragment && f.isAdded() && f.getView() != null) {
                        ((com.valdker.pos.ui.retail.RetailPOSFragment) f).onBarcodeScanned(barcode);
                        pendingBarcode = null;
                        barcodeDispatchRunning = false;
                        return;
                    }

                    if (f instanceof ProductsFragment && f.isAdded() && f.getView() != null) {
                        ((ProductsFragment) f).onBarcodeScanned(barcode);
                        pendingBarcode = null;
                        barcodeDispatchRunning = false;
                        return;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed dispatch barcode: " + e.getMessage(), e);
                    Toast.makeText(MainActivity.this, getString(R.string.msg_failed_process_barcode), Toast.LENGTH_SHORT).show();
                    barcodeDispatchRunning = false;
                    return;
                }

                tries[0]++;
                if (tries[0] < maxTry) {
                    handler.postDelayed(this, 100);
                } else {
                    barcodeDispatchRunning = false;
                    Toast.makeText(MainActivity.this, getString(R.string.msg_pos_screen_not_ready), Toast.LENGTH_SHORT).show();
                }
            }
        };

        handler.post(task);
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{android.Manifest.permission.CAMERA},
                    CAMERA_REQUEST
            );

        } else {
            startBarcodeScanner();
        }
    }

    private void startBarcodeScanner() {
        Fragment existing = getSupportFragmentManager().findFragmentByTag(TAG_BARCODE_DIALOG);
        if (existing != null) {
            return;
        }

        BarcodeScannerDialogFragment dlg = new BarcodeScannerDialogFragment();

        dlg.setListener(barcode -> {
            String clean = safeTrim(barcode);
            if (clean.isEmpty()) return;

            safeUi(() -> sendBarcodeToActivePosFragment(clean));
        });

        dlg.setCancelable(true);
        dlg.show(getSupportFragmentManager(), TAG_BARCODE_DIALOG);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_REQUEST) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                startBarcodeScanner();

            } else {
                Toast.makeText(
                        this,
                        getString(R.string.msg_camera_permission_barcode),
                        Toast.LENGTH_LONG
                ).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        com.google.zxing.integration.android.IntentResult result =
                IntentIntegrator.parseActivityResult(requestCode, resultCode, data);

        if (result != null) {
            if (result.getContents() != null) {
                String barcode = result.getContents();
                sendBarcodeToActivePosFragment(barcode);
            } else {
                Toast.makeText(this, getString(R.string.msg_scan_cancelled), Toast.LENGTH_SHORT).show();
            }
            return;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onStart() {
        super.onStart();

        IntentFilter filter = new IntentFilter(AuthEvents.ACTION_FORCE_LOGOUT);
        ContextCompat.registerReceiver(
                this,
                logoutReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
        );

        CartManager.getInstance(this).addListener(cartListener);

        refreshCartBadge();
        loadShopHeader();

        ensureShiftOpenOrBlock();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupPosSystemBars();

        View root = findViewById(R.id.root);
        if (root != null) {
            ViewCompat.requestApplyInsets(root);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        try {
            unregisterReceiver(logoutReceiver);
        } catch (Exception ignored) {
        }

        CartManager.getInstance(this).removeListener(cartListener);
    }

    private void ensureDefaultFragment() {
        Fragment current = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);

        if (isWorkshopBusiness()) {
            if (!(current instanceof WorkshopPOSFragment)) {
                openMainFragment(WorkshopPOSFragment.newInstance(), "workshop_pos");
            }
            return;
        }

        if (isRetailBusiness()) {
            if (!(current instanceof RetailPOSFragment)) {
                openMainFragment(
                        RetailPOSFragment.newInstance("retail", false, false),
                        "retail_pos"
                );
            }
            return;
        }

        if (!(current instanceof ProductsFragment)) {
            ProductsFragment fragment = ProductsFragment.newInstance(
                    businessType,
                    useGridPosLayout,
                    showProductImagesInPos
            );
            openMainFragment(fragment, "pos");
        }
    }

    private void openMainFragment(@NonNull Fragment fragment, @NonNull String tag) {
        if (!isActivityAlive()) return;

        Fragment current = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
        if (current != null && current.getClass().equals(fragment.getClass())) {
            return;
        }

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment, tag)
                .commit();
    }

    private void setupNativeButtons() {
        View btnCart = findViewById(R.id.btnCart);
        if (btnCart != null) {
            btnCart.setOnClickListener(v -> openCartOverlay());
        }

        if (btnBarcode != null) {
            btnBarcode.setOnClickListener(v -> onBarcodeClick());
        }

        if (imgLogo != null) {
            imgLogo.setClickable(true);
            imgLogo.setFocusable(true);
            imgLogo.setOnClickListener(this::showUserMenu);
        }
    }

    private void setupDraftChips() {
        if (chipDraftA == null && chipDraftB == null && chipDraftC == null && chipAddDraft == null) {
            return;
        }

        if (chipDraftA != null) {
            chipDraftA.setOnClickListener(v -> activateDraftByName("A"));
        }
        if (chipDraftB != null) {
            chipDraftB.setOnClickListener(v -> activateDraftByName("B"));
        }
        if (chipDraftC != null) {
            chipDraftC.setOnClickListener(v -> activateDraftByName("C"));
        }
        if (chipAddDraft != null) {
            styleAddDraftChip(chipAddDraft);
            chipAddDraft.setText("+");
            chipAddDraft.setOnClickListener(v -> createAndActivateDraft());
        }
    }

    private void initDraftStorage() {
        posDraftRepository = new PosDraftRepository(this);
        loadDraftsFromRoom(true, null);
    }

    private void loadDraftsFromRoom(boolean loadItems, @Nullable String toastMessage) {
        PosDraftRepository repository = posDraftRepository;
        if (repository == null) return;

        draftExecutor.execute(() -> {
            try {
                PosDraftSnapshot snapshot = repository.loadSnapshot(POS_TYPE_RESTAURANT, loadItems);
                mainHandler.post(() -> {
                    if (!isActivityAlive()) return;
                    applyDraftSnapshot(snapshot, loadItems);
                    if (!TextUtils.isEmpty(toastMessage)) {
                        Toast.makeText(MainActivity.this, toastMessage, Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to load main POS drafts", e);
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
            if (initialDraftLoadPending) {
                initialDraftLoadPending = false;
                if ((snapshot.items == null || snapshot.items.isEmpty())
                        && CartManager.getInstance(this).getTotalQty() > 0) {
                    persistCurrentCartToActiveDraft();
                    return;
                }
            }
            loadCartFromDraftItems(snapshot.items);
        }
    }

    private void renderDraftChips() {
        if (chipGroupDrafts == null) return;

        chipGroupDrafts.removeAllViews();
        for (int i = 0; i < posDrafts.size(); i++) {
            PosDraftEntity draft = posDrafts.get(i);
            if (draft == null) continue;

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
            styleAddDraftChip(chipAddDraft);
            chipAddDraft.setOnClickListener(v -> createAndActivateDraft());
        }
    }

    @NonNull
    private Chip createDraftChip() {
        Chip chip = new Chip(this);
        chip.setCheckable(true);
        chip.setClickable(true);
        chip.setSingleLine(true);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        chip.setTextSize(12f);
        chip.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        chip.setChipMinHeight(dp(32));
        chip.setMinHeight(dp(32));
        chip.setHeight(dp(32));
        chip.setChipCornerRadius(dp(16));
        chip.setChipStrokeWidth(dp(1));
        chip.setCheckedIconVisible(false);
        chip.setEnsureMinTouchTargetSize(false);
        return chip;
    }

    private void applyDraftChipStyle(@NonNull Chip chip, boolean active, @NonNull String name, int count) {
        chip.setChecked(active);
        chip.setText((active ? "\u25CF " : "") + safe(name, "A") + " \u2022 " + Math.max(0, count));
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
        chip.setEnsureMinTouchTargetSize(false);
    }

    private void activateDraftByName(@NonNull String name) {
        for (PosDraftEntity draft : posDrafts) {
            if (draft != null && name.equalsIgnoreCase(safeTrim(draft.name))) {
                activateDraft(draft.id, "Draft " + draft.name + " aktif");
                return;
            }
        }
    }

    private void activateDraft(long draftId, @NonNull String toastMessage) {
        PosDraftRepository repository = posDraftRepository;
        if (repository == null || draftId <= 0L || draftId == activeDraftId) return;

        final long oldDraftId = activeDraftId;
        final List<CartItem> currentItems = CartManager.getInstance(this).getItems();

        draftExecutor.execute(() -> {
            try {
                PosDraftSnapshot snapshot = repository.activateDraftSavingCurrent(
                        POS_TYPE_RESTAURANT,
                        oldDraftId,
                        draftId,
                        currentItems
                );
                mainHandler.post(() -> {
                    if (!isActivityAlive()) return;
                    applyDraftSnapshot(snapshot, true);
                    Toast.makeText(MainActivity.this, toastMessage, Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to activate main POS draft", e);
            }
        });
    }

    private void createAndActivateDraft() {
        PosDraftRepository repository = posDraftRepository;
        if (repository == null) return;

        final long oldDraftId = activeDraftId;
        final List<CartItem> currentItems = CartManager.getInstance(this).getItems();

        draftExecutor.execute(() -> {
            try {
                PosDraftRepository.CreatedDraftResult result = repository.createAndActivateDraftSavingCurrent(
                        POS_TYPE_RESTAURANT,
                        oldDraftId,
                        currentItems
                );
                mainHandler.post(() -> {
                    if (!isActivityAlive()) return;
                    applyDraftSnapshot(result.snapshot, true);
                    Toast.makeText(MainActivity.this, "Draft " + result.draftName + " aktif", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to create main POS draft", e);
            }
        });
    }

    private void loadCartFromDraftItems(@Nullable List<PosDraftItemEntity> items) {
        loadingDraftFromRoom = true;
        CartManager cart = CartManager.getInstance(this);
        cart.clear();

        if (items != null) {
            for (PosDraftItemEntity item : items) {
                CartItem cartItem = toCartItem(item);
                if (cartItem != null) {
                    cart.add(cartItem);
                }
            }
        }

        refreshCartBadge();
        mainHandler.post(() -> loadingDraftFromRoom = false);
    }

    @Nullable
    private CartItem toCartItem(@Nullable PosDraftItemEntity item) {
        return PosDraftMapper.toCartItem(item);
    }

    private void onMainCartChanged() {
        refreshCartBadge();

        if (isWorkshopBusiness() || isRetailBusiness()) return;
        if (loadingDraftFromRoom) return;

        persistCurrentCartToActiveDraft();
    }

    private void persistCurrentCartToActiveDraft() {
        PosDraftRepository repository = posDraftRepository;
        long draftId = activeDraftId;
        if (repository == null || draftId <= 0L) return;

        final List<CartItem> items = CartManager.getInstance(this).getItems();
        draftExecutor.execute(() -> {
            try {
                repository.replaceDraftItems(draftId, items);
                PosDraftSnapshot snapshot = repository.loadSnapshot(POS_TYPE_RESTAURANT, false);
                mainHandler.post(() -> {
                    if (!isActivityAlive()) return;
                    applyDraftSnapshot(snapshot, false);
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to persist main POS draft", e);
            }
        });
    }

    /**
     * Draft restoran/legacy dimiliki activity ini, bukan CartFragment, jadi
     * kunci idempotensinya juga ditahan di sini agar selamat dari pembuatan
     * ulang fragment keranjang.
     */
    @NonNull
    @Override
    public String obtainClientOrderIdForActiveDraft() {
        final long draftId = activeDraftId;
        String held = pendingCartClientOrderIds.get(draftId);
        if (held == null) {
            held = OfflineOrderRepository.newClientOrderId(getApplicationContext());
            pendingCartClientOrderIds.put(draftId, held);
        }
        return held;
    }

    @Override
    public void onCartOrderFinished() {
        pendingCartClientOrderIds.remove(activeDraftId);
        if (isWorkshopBusiness() || isRetailBusiness()) {
            CartManager.getInstance(this).clear();
            Log.i(TAG, "cart cleanup success pos=" + safeTrim(businessType) + " reason=cart_order_finished");
            return;
        }

        cleanupActiveMainDraftAfterCheckout("cart_order_finished");
    }

    private void deleteActiveDraftAndStartFresh() {
        cleanupActiveMainDraftAfterCheckout("delete_active_draft");
    }

    private void cleanupActiveMainDraftAfterCheckout(@NonNull String reason) {
        PosDraftRepository repository = posDraftRepository;
        long draftId = activeDraftId;
        Context appCtx = getApplicationContext();

        Log.i(TAG, "checkout cleanup started pos=restaurant draftId=" + draftId + " reason=" + reason);

        loadingDraftFromRoom = true;

        if (repository == null || draftId <= 0L) {
            CartManager.getInstance(appCtx).clear();
            refreshCartBadge();
            Log.i(TAG, "draft cleanup success pos=restaurant draftId=" + draftId + " reason=no_active_draft");
            Log.i(TAG, "cart cleanup success pos=restaurant reason=" + reason);
            mainHandler.post(() -> loadingDraftFromRoom = false);
            return;
        }

        draftExecutor.execute(() -> {
            try {
                PosDraftSnapshot snapshot = repository.cleanupActiveDraftAfterCheckout(POS_TYPE_RESTAURANT, draftId);
                Log.i(TAG, "draft cleanup success pos=restaurant draftId=" + draftId + " reason=" + reason);
                mainHandler.post(() -> {
                    CartManager.getInstance(appCtx).clear();
                    Log.i(TAG, "cart cleanup success pos=restaurant reason=" + reason);
                    if (isActivityAlive()) {
                        refreshCartBadge();
                        applyDraftSnapshot(snapshot, true);
                    } else {
                        loadingDraftFromRoom = false;
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "cleanup failed with error pos=restaurant draftId=" + draftId + " reason=" + reason, e);
                mainHandler.post(() -> loadingDraftFromRoom = false);
            }
        });
    }

    private void openRetailNativeCheckout() {
        Fragment f = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
        if (!(f instanceof RetailPOSFragment)) {
            Toast.makeText(this, getString(R.string.msg_retail_pos_not_ready), Toast.LENGTH_SHORT).show();
            return;
        }

        RetailPOSFragment retailFragment = (RetailPOSFragment) f;
        int totalItems = retailFragment.getTotalScannedQty();
        double totalAmount = retailFragment.getGrandTotalAmount();

        if (totalItems <= 0 || totalAmount <= 0) {
            Toast.makeText(this, getString(R.string.msg_no_items_scanned), Toast.LENGTH_SHORT).show();
            return;
        }

        final String token = session != null ? safeTrim(session.getToken()) : "";
        if (token.isEmpty()) {
            Toast.makeText(this, getString(R.string.msg_token_missing_login_again), Toast.LENGTH_LONG).show();
            return;
        }

        if (retailCheckoutDialogOpening
                || getSupportFragmentManager().findFragmentByTag(TAG_NATIVE_CHECKOUT_DIALOG) != null) {
            return;
        }
        retailCheckoutDialogOpening = true;

        boolean needTable = enableTableNumber;
        boolean needDelivery = enableDelivery;

        NativeCheckoutDialogFragment dialog =
                NativeCheckoutDialogFragment.newInstance(totalAmount, needTable, needDelivery);
        dialog.setOnDismissCallback(() -> retailCheckoutDialogOpening = false);

        List<NativeCheckoutDialogFragment.CheckoutItem> checkoutItems = new ArrayList<>();
        for (RetailProductItem item : retailFragment.getScannedProducts()) {
            if (item == null) continue;

            int qty = retailFragment.getQtyForProduct(item.id);
            if (qty <= 0) continue;

            double unitPrice = item.price;
            double lineTotal = unitPrice * qty;

            checkoutItems.add(new NativeCheckoutDialogFragment.CheckoutItem(
                    item.id,
                    cleanDisplayName(item.name),
                    qty,
                    unitPrice,
                    lineTotal
            ));
        }

        dialog.setCheckoutItems(checkoutItems);

        final Context appCtx = getApplicationContext();
        MasterDataRepository masterDataRepository = new MasterDataRepository(appCtx);
        final boolean[] dialogShown = {false};
        final boolean[] offlineNoticeShown = {false};
        final boolean[] noLocalDataNoticeShown = {false};

        masterDataRepository.loadCheckoutDataRoomFirst(token, new MasterDataRepository.CheckoutDataCallback() {
            @Override
            public void onLocalCheckoutData(@NonNull List<Customer> customers,
                                            @NonNull List<PaymentMethodItem> paymentItems,
                                            @NonNull List<BankAccountItem> bankItems) {
                if (!isActivityAlive()) return;
                if (!paymentItems.isEmpty()) {
                    showOrUpdateRetailCheckoutDialog(dialog, dialogShown, customers, paymentItems, bankItems);
                }
            }

            @Override
            public void onRemoteCheckoutData(@NonNull List<Customer> customers,
                                             @NonNull List<PaymentMethodItem> paymentItems,
                                             @NonNull List<BankAccountItem> bankItems) {
                if (!isActivityAlive()) return;
                showOrUpdateRetailCheckoutDialog(dialog, dialogShown, customers, paymentItems, bankItems);
            }

            @Override
            public void onNoInternet(@NonNull List<Customer> customers,
                                     @NonNull List<PaymentMethodItem> paymentItems,
                                     @NonNull List<BankAccountItem> bankItems) {
                if (!isActivityAlive()) return;
                if (paymentItems.isEmpty()) {
                    if (!noLocalDataNoticeShown[0]) {
                        noLocalDataNoticeShown[0] = true;
                        Toast.makeText(MainActivity.this, MasterDataRepository.MESSAGE_NO_LOCAL_POS_DATA, Toast.LENGTH_SHORT).show();
                    }
                    retailCheckoutDialogOpening = false;
                    return;
                }
                if (!offlineNoticeShown[0]) {
                    offlineNoticeShown[0] = true;
                    Toast.makeText(MainActivity.this, MasterDataRepository.MESSAGE_NO_INTERNET_SHOWING_LOCAL, Toast.LENGTH_SHORT).show();
                }
                if (!dialogShown[0]) {
                    showOrUpdateRetailCheckoutDialog(dialog, dialogShown, customers, paymentItems, bankItems);
                }
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                if (!isActivityAlive()) return;
                if (!dialogShown[0]) {
                    retailCheckoutDialogOpening = false;
                    ErrorHandler.handleApiError(MainActivity.this,
                            getString(R.string.msg_failed_load_payment_methods, message));
                }
            }
        });
    }

    private void showOrUpdateRetailCheckoutDialog(@NonNull NativeCheckoutDialogFragment dialog,
                                                  @NonNull boolean[] dialogShown,
                                                  @NonNull List<Customer> customers,
                                                  @NonNull List<PaymentMethodItem> paymentItems,
                                                  @NonNull List<BankAccountItem> bankItems) {
        dialog.setCustomerOptions(toCustomerOptions(customers));
        dialog.setPaymentOptions(toPaymentOptions(paymentItems));
        dialog.setBankOptions(toBankOptions(bankItems));
        dialog.setBankListener(result -> submitRetailOrder(result));

        if (!dialogShown[0]) {
            dialogShown[0] = true;
            showRetailCheckoutDialog(dialog);
        }
    }

    @NonNull
    private List<NativeCheckoutDialogFragment.CustomerOption> toCustomerOptions(@NonNull List<Customer> customers) {
        List<NativeCheckoutDialogFragment.CustomerOption> options = new ArrayList<>();
        options.add(new NativeCheckoutDialogFragment.CustomerOption(
                0,
                getString(R.string.workshop_walk_in_customer),
                0L
        ));
        for (Customer c : customers) {
            if (c == null) continue;
            options.add(new NativeCheckoutDialogFragment.CustomerOption(
                    c.id,
                    c.name != null && !c.name.trim().isEmpty()
                            ? c.name
                            : getString(R.string.customer_fallback_name_format, c.id),
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
            String label =
                    (item.bank_name != null ? item.bank_name : "") +
                            " - " +
                            (item.name != null ? item.name : "");
            options.add(new NativeCheckoutDialogFragment.BankAccountOption(item.id, label));
        }
        return options;
    }

    private void showRetailCheckoutDialog(@NonNull NativeCheckoutDialogFragment dialog) {
        if (!isActivityAlive() || getSupportFragmentManager().isStateSaved()) {
            retailCheckoutDialogOpening = false;
            return;
        }

        if (getSupportFragmentManager().findFragmentByTag(TAG_NATIVE_CHECKOUT_DIALOG) != null) {
            retailCheckoutDialogOpening = false;
            return;
        }

        dialog.show(getSupportFragmentManager(), TAG_NATIVE_CHECKOUT_DIALOG);
    }

    @NonNull
    private String cleanDisplayName(@Nullable String name) {
        String clean = safeTrim(name);
        if (clean.isEmpty()) return "-";
        return clean.replaceAll("\\sx\\d+$", "").trim();
    }

    private void submitRetailOrder(@NonNull NativeCheckoutDialogFragment.BankCheckoutResult result) {
        if (retailOrderSubmitting) {
            Toast.makeText(this, getString(R.string.msg_order_submitting), Toast.LENGTH_SHORT).show();
            return;
        }

        final String token = session != null ? safeTrim(session.getToken()) : "";
        if (token.isEmpty()) {
            Toast.makeText(this, getString(R.string.msg_token_missing_login_again), Toast.LENGTH_LONG).show();
            return;
        }

        final JSONObject payload;
        try {
            payload = buildRetailOrderPayload(result);
            Log.d(TAG, "Retail order payload ready items=" + result.items.size()
                    + " total=" + result.totalAmount
                    + " device_time=" + payload.optString("device_time", ""));
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.msg_failed_build_order_payload, e.getMessage()), Toast.LENGTH_LONG).show();
            return;
        }

        retailOrderSubmitting = true;
        final long draftIdForKey = resolveRetailActiveDraftId();
        String heldClientOrderId = pendingRetailClientOrderIds.get(draftIdForKey);
        if (heldClientOrderId == null) {
            heldClientOrderId = OfflineOrderRepository.newClientOrderId(getApplicationContext());
            pendingRetailClientOrderIds.put(draftIdForKey, heldClientOrderId);
        }
        final String clientOrderId = heldClientOrderId;
        OfflineOrderRepository.ensureClientOrderId(payload, clientOrderId);
        final String localOrderId = clientOrderId;
        Log.i(TAG, "Retail order submit client_order_id=" + clientOrderId);

        // WRITE-AHEAD: order ditulis ke Room SEBELUM request dikirim. Kalau
        // aplikasi mati di antara tap bayar dan callback, penjualannya tetap ada
        // di perangkat dan ikut sync berikutnya. Sebelumnya penulisan baru
        // terjadi setelah request gagal, sehingga order bisa lenyap sama sekali.
        new OfflineOrderRepository(getApplicationContext()).savePendingOrder(
                localOrderId,
                payload,
                "retail",
                new OfflineOrderRepository.SaveCallback() {
                    @Override
                    public void onSuccess(@NonNull String savedLocalOrderId, boolean inserted) {
                        Log.i(TAG, "Retail order write-ahead saved localOrderId=" + savedLocalOrderId
                                + " inserted=" + inserted);
                        sendRetailOrderAfterWriteAhead(savedLocalOrderId, payload, result, token);
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        // Tidak bisa menulis dulu berarti tidak boleh mengirim:
                        // order yang terkirim tanpa jejak lokal persis bug yang
                        // sedang diperbaiki di sini.
                        retailOrderSubmitting = false;
                        Log.e(TAG, "Retail order write-ahead FAILED, submit dibatalkan: " + message);
                        Toast.makeText(MainActivity.this,
                                "Failed to save local order: " + message,
                                Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    private void sendRetailOrderAfterWriteAhead(@NonNull String localOrderId,
                                                @NonNull JSONObject payload,
                                                @NonNull NativeCheckoutDialogFragment.BankCheckoutResult result,
                                                @NonNull String token) {
        if (!isNetworkAvailable()) {
            // Sudah tersimpan sebagai PENDING_SYNC; tinggal tutup transaksinya.
            finishRetailAfterOfflineSave(localOrderId, payload, result);
            return;
        }

        final String url = ApiConfig.url(session, "api/orders/");

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.POST,
                url,
                payload,
                response -> {
                    retailOrderSubmitting = false;
                    new OfflineOrderRepository(getApplicationContext())
                            .markWriteAheadSynced(localOrderId);
                    syncPendingOrdersIfOnline();

                    String invoice = response.optString("invoice_number", "");
                    if (invoice.isEmpty()) {
                        invoice = response.optString("invoice_id", "");
                    }
                    if (invoice.isEmpty()) {
                        invoice = response.optString("id", "");
                    }
                    if (invoice.isEmpty()) {
                        invoice = "INV-" + System.currentTimeMillis();
                    }

                    String message = invoice.isEmpty()
                            ? getString(R.string.msg_retail_order_saved)
                            : getString(R.string.msg_retail_order_saved_invoice, invoice);

                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                    Log.d(TAG, "Retail order success invoice=" + invoice);
                    tryAutoPrintRetailReceipt(result, invoice);
                    cleanupRetailAfterCheckout("online_success");
                },
                error -> {
                    retailOrderSubmitting = false;
                    String detail = extractVolleyErrorMessage(error);
                    int statusCode = error != null && error.networkResponse != null
                            ? error.networkResponse.statusCode
                            : -1;
                    String body = extractVolleyErrorBody(error);
                    Log.e(TAG, "Retail order failed status_code=" + statusCode
                            + " detail=" + safeLogDetail(body), error);
                    OfflineOrderRepository repo =
                            new OfflineOrderRepository(getApplicationContext());

                    if (ErrorHandler.isDeviceTimeValidationError(statusCode, body)
                            || ErrorHandler.isDeviceTimeValidationError(statusCode, detail)) {
                        // Jangan biarkan sync otomatis mengirim ulang: payload
                        // offline melewati pemeriksaan jam di backend, jadi retry
                        // justru akan meloloskan order yang barusan ditolak.
                        repo.markWriteAheadNeedsReview(localOrderId, "device_time rejected");
                        ErrorHandler.showDeviceTimeDialog(MainActivity.this);
                        return;
                    }
                    if (OfflineOrderRepository.shouldSaveOffline(MainActivity.this, error)) {
                        // Sudah tersimpan PENDING_SYNC oleh write-ahead.
                        finishRetailAfterOfflineSave(localOrderId, payload, result);
                        return;
                    }
                    repo.markWriteAheadFailed(localOrderId, "HTTP " + statusCode);
                    Log.i(TAG, "cart kept because server rejected pos=retail statusCode=" + statusCode
                            + " (order tersimpan lokal, akan diulang oleh sync)");
                    ErrorHandler.handleApiError(MainActivity.this, error);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> h = new HashMap<>();
                h.put("Accept", "application/json");
                h.put("Content-Type", "application/json");
                h.put("Authorization", "Token " + token);
                return h;
            }
        };

        req.setTag(TAG_RETAIL_ORDER_CREATE);
        ApiClient.getInstance(this).add(req);
    }

    /**
     * Order sudah tersimpan lokal oleh write-ahead; ini hanya menutup transaksi
     * di layar (cetak struk offline, bersihkan keranjang).
     */
    private void finishRetailAfterOfflineSave(@NonNull String localOrderId,
                                              @NonNull JSONObject payload,
                                              @NonNull NativeCheckoutDialogFragment.BankCheckoutResult result) {
        retailOrderSubmitting = false;
        tryAutoPrintOfflineRetailReceipt(
                result,
                offlineReceiptNumber(localOrderId, payload),
                payload.optString("device_time", ""),
                printResult -> showOfflineReceiptResult(printResult)
        );
        Toast.makeText(MainActivity.this,
                OfflineOrderRepository.MESSAGE_ORDER_SAVED_LOCALLY,
                Toast.LENGTH_LONG).show();
        cleanupRetailAfterCheckout("offline_save_success");
    }

    /**
     * Draft retail dimiliki RetailPOSFragment, bukan activity ini.
     * Pola pengambilannya sama dengan clearAfterCheckout() di bawah.
     */
    private long resolveRetailActiveDraftId() {
        Fragment current = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
        if (current instanceof RetailPOSFragment) {
            return ((RetailPOSFragment) current).getActiveDraftId();
        }
        return 0L;
    }

    private void cleanupRetailAfterCheckout(@NonNull String reason) {
        pendingRetailClientOrderIds.remove(resolveRetailActiveDraftId());
        Log.i(TAG, "checkout cleanup started pos=retail reason=" + reason);
        Fragment current = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
        if (current instanceof RetailPOSFragment) {
            ((RetailPOSFragment) current).clearAfterCheckout();
            Log.i(TAG, "draft cleanup success pos=retail reason=" + reason + " via=attached_fragment_scheduled");
            return;
        }

        Context appCtx = getApplicationContext();
        PosDraftRepository repository = posDraftRepository != null
                ? posDraftRepository
                : new PosDraftRepository(appCtx);

        draftExecutor.execute(() -> {
            try {
                PosDraftSnapshot currentSnapshot = repository.loadSnapshot(POS_TYPE_RETAIL, false);
                long draftId = currentSnapshot.activeDraft != null ? currentSnapshot.activeDraft.id : 0L;
                if (draftId > 0L) {
                    repository.cleanupActiveDraftAfterCheckout(POS_TYPE_RETAIL, draftId);
                    Log.i(TAG, "draft cleanup success pos=retail draftId=" + draftId + " reason=" + reason);
                } else {
                    repository.loadSnapshot(POS_TYPE_RETAIL, true);
                    Log.i(TAG, "draft cleanup success pos=retail reason=no_active_draft");
                }
                Log.i(TAG, "cart cleanup success pos=retail reason=" + reason + " via=repository_fallback");
            } catch (Exception e) {
                Log.e(TAG, "cleanup failed with error pos=retail reason=" + reason, e);
            }
        });
    }

    private void syncPendingOrdersIfOnline() {
        String token = session != null ? safeTrim(session.getToken()) : "";
        new OfflineOrderRepository(getApplicationContext()).syncPendingOrders(token);
    }

    @NonNull
    private String offlineReceiptNumber(@NonNull String localOrderId, @NonNull JSONObject payload) {
        String clientOrderId = payload.optString("client_order_id", "");
        if (clientOrderId != null && !clientOrderId.trim().isEmpty()) {
            return clientOrderId.trim();
        }
        return localOrderId.trim().isEmpty() ? "OFFLINE-" + System.currentTimeMillis() : localOrderId.trim();
    }

    private void showOfflineReceiptResult(@NonNull com.valdker.pos.print.BluetoothPrinterManager.PrintResult result) {
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
        safeUi(() -> Toast.makeText(
                MainActivity.this,
                message,
                Toast.LENGTH_LONG
        ).show());
    }

    private void tryAutoPrintRetailReceipt(@NonNull NativeCheckoutDialogFragment.BankCheckoutResult result,
                                           @NonNull String invoiceNumber) {
        tryPrintRetailReceipt(result, invoiceNumber, "", "", null);
    }

    private void tryAutoPrintOfflineRetailReceipt(@NonNull NativeCheckoutDialogFragment.BankCheckoutResult result,
                                                  @NonNull String receiptNumber,
                                                  @NonNull String deviceTime,
                                                  @NonNull ReceiptPrintCallback callback) {
        Log.i(TAG, "Offline receipt print started order=" + receiptNumber);
        tryPrintRetailReceipt(result, receiptNumber, "OFFLINE / PENDING SYNC", deviceTime, callback);
    }

    private void tryPrintRetailReceipt(@NonNull NativeCheckoutDialogFragment.BankCheckoutResult result,
                                       @NonNull String invoiceNumber,
                                       @NonNull String receiptStatus,
                                       @NonNull String deviceTime,
                                       @Nullable ReceiptPrintCallback callback) {
        final Context appCtx = getApplicationContext();

        if (!com.valdker.pos.print.PrinterPrefs.isAutoPrintEnabled(appCtx)) {
            Log.i(TAG, "PRINTER: auto print disabled -> skip printing");
            if (callback != null) {
                callback.onComplete(com.valdker.pos.print.BluetoothPrinterManager.PrintResult.SKIPPED);
            } else {
                Toast.makeText(appCtx,
                        "Receipt printing skipped because auto-print is disabled.",
                        Toast.LENGTH_LONG).show();
            }
            return;
        }

        if (!com.valdker.pos.print.PrinterService.hasBtPermission(appCtx)) {
            Log.w(TAG, "PRINTER: Bluetooth permission not granted -> skip auto print");
            if (callback == null) {
                Toast.makeText(appCtx,
                        "Bluetooth permission is required to connect printer.",
                        Toast.LENGTH_LONG).show();
            } else {
                callback.onComplete(com.valdker.pos.print.BluetoothPrinterManager.PrintResult.FAILED);
            }
            return;
        }

        String mac = com.valdker.pos.print.PrinterPrefs.getMac(appCtx);
        if (mac == null || mac.trim().isEmpty()) {
            Log.w(TAG, "PRINTER: no printer selected -> skip auto print");
            if (callback == null) {
                Toast.makeText(appCtx,
                        "Printer not connected. Please select printer.",
                        Toast.LENGTH_LONG).show();
            } else {
                callback.onComplete(com.valdker.pos.print.BluetoothPrinterManager.PrintResult.FAILED);
            }
            return;
        }

        final String fallbackReceipt = buildRetailReceipt(
                "VALDKER POS",
                "",
                "",
                result,
                invoiceNumber,
                receiptStatus,
                deviceTime
        );

        String token = session != null ? safeTrim(session.getToken()) : "";
        AtomicBoolean receiptPrinted = new AtomicBoolean(false);
        ShopRepository.getBestShopProfileForReceipt(appCtx, token, new ShopRepository.Callback() {
            @Override
            public void onSuccess(Shop shop) {
                String shopName = shop != null && !safeTrim(shop.name).isEmpty()
                        ? safeTrim(shop.name)
                        : "VALDKER POS";
                String shopAddress = shop != null ? safeTrim(shop.address) : "";
                String shopPhone = shop != null ? safeTrim(shop.phone) : "";

                printRetailReceiptOnce(buildRetailReceipt(
                        shopName,
                        shopAddress,
                        shopPhone,
                        result,
                        invoiceNumber,
                        receiptStatus,
                        deviceTime
                ), receiptPrinted, callback);
            }

            @Override
            public void onEmpty() {
                printRetailReceiptOnce(fallbackReceipt, receiptPrinted, callback);
            }

            @Override
            public void onError(@NonNull String message) {
                printRetailReceiptOnce(fallbackReceipt, receiptPrinted, callback);
            }
        });
    }

    private void printRetailReceiptOnce(@NonNull String receipt,
                                        @NonNull AtomicBoolean printed,
                                        @Nullable ReceiptPrintCallback callback) {
        if (!printed.compareAndSet(false, true)) {
            Log.w(TAG, callback != null
                    ? "Offline receipt print skipped: already printed"
                    : "Receipt print skipped: already printed");
            return;
        }
        printRetailReceiptBestEffort(receipt, callback);
    }

    private void printRetailReceiptBestEffort(@NonNull String receipt,
                                              @Nullable ReceiptPrintCallback callback) {
        com.valdker.pos.print.PrinterService.printTextAsync(
                getApplicationContext(),
                receipt,
                new com.valdker.pos.print.BluetoothPrinterManager.PrintCallback() {
                    @Override
                    public void onSuccess() {
                        if (callback != null) {
                            Log.i(TAG, "Offline receipt print success");
                            callback.onComplete(com.valdker.pos.print.BluetoothPrinterManager.PrintResult.SUCCESS);
                        } else {
                            Log.i(TAG, "PRINTER: retail receipt print success");
                        }
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        if (callback != null) {
                            Log.e(TAG, "Offline receipt print failed: " + message);
                            callback.onComplete(com.valdker.pos.print.BluetoothPrinterManager.PrintResult.FAILED);
                        } else {
                            Log.e(TAG, "PRINTER: retail receipt print failed: " + message);
                        }
                        safeUi(() -> showRetailPrintRetry(receipt, message));
                    }

                    @Override
                    public void onSkipped(@NonNull String message) {
                        Log.w(TAG, "PRINTER: retail receipt print skipped: " + message);
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
                            Log.e(TAG, "PRINTER: retail receipt print timed out: " + message);
                        }
                        safeUi(() -> showRetailPrintRetry(receipt, message));
                    }
                }
        );
    }

    private void showRetailPrintRetry(@NonNull String receipt, @NonNull String message) {
        if (!isActivityAlive()) return;

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setMessage("Transaction saved, but receipt failed to print. Retry print?")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Retry", null)
                .show();
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            v.setEnabled(false);
            printRetailReceiptBestEffort(receipt, null);
            dialog.dismiss();
        });
    }

    @NonNull
    private String buildRetailReceipt(@NonNull String shopName,
                                      @NonNull String shopAddress,
                                      @NonNull String shopPhone,
                                      @NonNull NativeCheckoutDialogFragment.BankCheckoutResult result,
                                      @NonNull String invoiceNumber,
                                      @NonNull String receiptStatus,
                                      @NonNull String deviceTime) {
        java.text.SimpleDateFormat dfDate = new java.text.SimpleDateFormat("dd/MM/yy", Locale.US);
        java.text.SimpleDateFormat dfTime = new java.text.SimpleDateFormat("HH:mm", Locale.US);

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
        if (!safeTrim(cachedUsername).isEmpty()) {
            sb.append("[L]Cashier:[R]").append(safeTrim(cachedUsername)).append("\n");
        }
        if (!safeTrim(result.customerName).isEmpty()
                && !"Walk-in Customer".equalsIgnoreCase(safeTrim(result.customerName))) {
            sb.append("[L]Customer:[R]").append(safeTrim(result.customerName)).append("\n");
        }
        sb.append("[L]Date:[R]").append(dfDate.format(new java.util.Date())).append("\n");
        sb.append("[L]Time:[R]").append(dfTime.format(new java.util.Date())).append("\n");
        if (!deviceTime.trim().isEmpty()) {
            sb.append("[L]Device Time:[R]").append(deviceTime.trim()).append("\n");
        }
        sb.append("[C]--------------------------------\n");

        for (NativeCheckoutDialogFragment.CheckoutItem item : result.items) {
            if (item == null) continue;
            sb.append("[L]<b>").append(safeTrim(item.productName)).append("</b>[R]<b>")
                    .append("$").append(item.lineTotalMoney.toPlainString())
                    .append("</b>\n");
            sb.append("[L]").append(Math.max(0, item.quantity))
                    .append(" x ")
                    .append("$").append(item.unitPriceMoney.toPlainString())
                    .append("\n\n");
        }

        sb.append("[C]--------------------------------\n");
        sb.append("[L]Subtotal[R]").append("$").append(result.subtotalMoney.toPlainString()).append("\n");
        sb.append("[L]Discount[R]$0.00\n");
        sb.append("[L]VAT / Tax[R]$0.00\n");
        if (result.deliveryFee > 0) {
            sb.append("[L]Delivery Fee[R]").append("$").append(result.deliveryFeeMoney.toPlainString()).append("\n");
        }
        sb.append("[C]--------------------------------\n");
        sb.append("[L]<b>Total</b>[R]<b>").append("$").append(result.totalAmountMoney.toPlainString()).append("</b>\n");
        sb.append("[L]Payment[R]").append(safeTrim(result.paymentMethodCode)).append("\n");
        if (result.cashReceived > 0) {
            sb.append("[L]Paid[R]").append("$").append(result.cashReceivedMoney.toPlainString()).append("\n");
            sb.append("[L]Change[R]").append("$").append(result.changeAmountMoney.toPlainString()).append("\n");
        }
        sb.append("[C]--------------------------------\n");
        sb.append("[C]Thank you for your purchase\n");
        sb.append("[C]").append(shopName).append("\n\n\n");

        return sb.toString();
    }

    @NonNull
    private JSONObject buildRetailOrderPayload(@NonNull NativeCheckoutDialogFragment.BankCheckoutResult result) throws Exception {
        if (result.paymentMethodId == null || result.paymentMethodId <= 0) {
            throw new IllegalStateException(getString(R.string.msg_payment_method_missing));
        }

        JSONObject body = new JSONObject();
        body.put("device_time", currentDeviceTimeIso());

        if (result.customerId != null && result.customerId > 0) {
            body.put("customer", result.customerId);
        }

        body.put("payment_method", result.paymentMethodId);

        if (result.bankAccountId != null && result.bankAccountId > 0) {
            body.put("bank_account", result.bankAccountId);
        }

        if (!safeTrim(result.referenceNumber).isEmpty()) {
            body.put("reference_number", safeTrim(result.referenceNumber));
        }

        if (!safeTrim(result.paymentNote).isEmpty()) {
            body.put("note", safeTrim(result.paymentNote));
        }

        if (!safeTrim(result.tableNumber).isEmpty()) {
            body.put("table_number", safeTrim(result.tableNumber));
        }

        if (!safeTrim(result.deliveryAddress).isEmpty()) {
            body.put("delivery_address", safeTrim(result.deliveryAddress));
        }

        // Semua nilai uang dikirim sebagai string desimal. Menaruh double ke
        // JSONObject menuliskan ekspansi binernya apa adanya, mis.
        // 0.30000000000000004.
        if (result.deliveryFeeMoney.isPositive()) {
            body.put("delivery_fee", result.deliveryFeeMoney.toPlainString());
        }

        if (result.cashReceivedMoney.isPositive()) {
            body.put("cash_received", result.cashReceivedMoney.toPlainString());
        }

        if (result.changeAmountMoney.isPositive()) {
            body.put("change_amount", result.changeAmountMoney.toPlainString());
        }

        JSONArray items = new JSONArray();
        for (NativeCheckoutDialogFragment.CheckoutItem item : result.items) {
            if (item == null) continue;
            if (item.productId <= 0) continue;
            if (item.quantity <= 0) continue;

            JSONObject line = new JSONObject();
            line.put("product", item.productId);
            line.put("quantity", item.quantity);
            // Server menghitung ulang subtotal dan total dari harga baris ini,
            // jadi presisinya di sinilah yang menentukan angka di server.
            line.put("price", item.unitPriceMoney.toPlainString());
            items.put(line);
        }
        body.put("items", items);

        JSONArray payments = new JSONArray();

        JSONObject payment = new JSONObject();
        payment.put("payment_method_id", result.paymentMethodId);

        if (result.bankAccountId != null && result.bankAccountId > 0) {
            payment.put("bank_account_id", result.bankAccountId);
        } else {
            payment.put("bank_account_id", JSONObject.NULL);
        }

        if (!safeTrim(result.referenceNumber).isEmpty()) {
            payment.put("reference_number", safeTrim(result.referenceNumber));
        }

        if (!safeTrim(result.paymentNote).isEmpty()) {
            payment.put("note", safeTrim(result.paymentNote));
        }

        payment.put("amount", result.totalAmountMoney.toPlainString());
        payments.put(payment);
        body.put("payments", payments);

        return body;
    }

    @NonNull
    private static String currentDeviceTimeIso() {
        return new java.text.SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                Locale.US
        ).format(new java.util.Date());
    }

    @NonNull
    private String extractVolleyErrorMessage(@Nullable com.android.volley.VolleyError error) {
        if (error == null) {
            return getString(R.string.msg_unknown_error);
        }

        int statusCode = error.networkResponse != null ? error.networkResponse.statusCode : -1;
        String body = extractVolleyErrorBody(error);

        if (!body.trim().isEmpty()) {
            return getString(R.string.msg_submit_order_failed_body, statusCode, body);
        }

        if (statusCode > 0) {
            return getString(R.string.msg_submit_order_failed_code, statusCode);
        }

        return getString(R.string.msg_submit_order_failed);
    }

    @NonNull
    private String extractVolleyErrorBody(@Nullable com.android.volley.VolleyError error) {
        if (error == null) return "";

        try {
            if (error.networkResponse != null && error.networkResponse.data != null) {
                return new String(error.networkResponse.data, StandardCharsets.UTF_8).trim();
            }
            if (error.getMessage() != null) {
                return error.getMessage().trim();
            }
        } catch (Exception ignored) {
        }

        return "";
    }

    @NonNull
    private String safeLogDetail(@Nullable String value) {
        if (value == null) return "";
        String clean = value.replace('\n', ' ').replace('\r', ' ').trim();
        String lower = clean.toLowerCase(Locale.US);
        if (lower.startsWith("<!doctype") || lower.startsWith("<html") || lower.contains("<body")) {
            return "<html omitted>";
        }
        return clean.length() > 180 ? clean.substring(0, 180).trim() + "..." : clean;
    }

    private void onBarcodeClick() {
        if (!enableBarcodeScan) {
            Toast.makeText(this, getString(R.string.msg_barcode_disabled_business), Toast.LENGTH_SHORT).show();
            return;
        }

        if (isWorkshopBusiness()) {
            Toast.makeText(this, getString(R.string.msg_barcode_hidden_workshop), Toast.LENGTH_SHORT).show();
            return;
        }

        if (session != null && !session.isShiftOpen()) {
            Toast.makeText(this, getString(R.string.msg_open_shift_first), Toast.LENGTH_SHORT).show();
            ensureShiftOpenOrBlock();
            return;
        }

        checkCameraPermission();
    }

    private void openCartOverlay() {
        if (isWorkshopBusiness()) {
            Toast.makeText(this, getString(R.string.msg_workshop_main_screen), Toast.LENGTH_SHORT).show();
            return;
        }

        View overlay = findViewById(R.id.overlayContainer);
        if (overlay != null) {
            overlay.setVisibility(View.VISIBLE);
            overlay.bringToFront();
            overlay.requestLayout();
            overlay.invalidate();
        }

        Fragment overlayFragment = new com.valdker.pos.ui.CartFragment();

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.overlayContainer, overlayFragment)
                .addToBackStack("cart_overlay")
                .commit();
    }

    private void hideOverlayIfNoOverlayFragments() {
        View overlay = findViewById(R.id.overlayContainer);
        if (overlay == null) return;

        boolean hasOverlay = false;
        for (int i = 0; i < getSupportFragmentManager().getBackStackEntryCount(); i++) {
            String name = getSupportFragmentManager().getBackStackEntryAt(i).getName();
            if ("cart_overlay".equals(name)) {
                hasOverlay = true;
                break;
            }
        }
        overlay.setVisibility(hasOverlay ? View.VISIBLE : View.GONE);
    }

    private void setupCategories() {
        RecyclerView rv = findViewById(R.id.rvCategories);
        if (rv == null) return;

        rv.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        rv.setHasFixedSize(true);

        categoryAdapter = new CategoryAdapter();
        rv.setAdapter(categoryAdapter);

        categoryAdapter.setListener(category -> {
            Log.i(TAG, "Selected category id=" + category.id + " name=" + safe(category.name, ""));
            Fragment f = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
            if (f instanceof ProductsFragment) {
                String filter = (category.id == -1) ? "all" : String.valueOf(category.id);
                ((ProductsFragment) f).setCategoryFilter(filter);
            }
        });

        loadCategoriesRoomFirst();
    }

    private void applyCategoriesToUI(@NonNull List<Category> list, @NonNull String source) {
        int incoming = list.size();

        if (incoming == 0 && categoriesAppliedFromOnline) {
            return;
        }

        categoryList.clear();
        categoryList.add(new Category(-1, getString(R.string.category_all), null));

        for (Category c : list) {
            if (c == null) continue;
            if (c.id == -1) continue;
            if (c.name == null || c.name.trim().isEmpty()) continue;
            categoryList.add(c);
        }

        if (categoryAdapter != null) {
            categoryAdapter.setData(categoryList);
        }

        Log.i(TAG, "Categories applied size=" + categoryList.size() + " src=" + source);
    }

    private void loadCategoriesNoRoom() {
        applyCategoriesToUI(new ArrayList<>(), "BOOT_ALL_ONLY");

        final String token = session != null ? session.getToken() : null;
        if (token == null || token.trim().isEmpty()) {
            Log.w(TAG, "loadCategoriesNoRoom: token empty -> keep only All");
            return;
        }

        final String url = ApiConfig.url(session, "api/categories/");

        Log.i(TAG, "loadCategoriesNoRoom: fetching " + url);

        StringRequest req = new StringRequest(
                Request.Method.GET,
                url,
                response -> {
                    try {
                        JSONArray res = extractResultsArray(response);
                        List<Category> out = new ArrayList<>();

                        for (int i = 0; i < res.length(); i++) {
                            JSONObject o = res.optJSONObject(i);
                            if (o == null) continue;

                            Category c = new Category();
                            c.id = o.optInt("id", 0);
                            c.name = o.optString("name", "");
                            c.iconUrl = o.optString("icon_url", "");

                            if (c.id <= 0) continue;
                            if (c.name == null || c.name.trim().isEmpty()) continue;

                            out.add(c);
                        }

                        categoriesAppliedFromOnline = true;
                        applyCategoriesToUI(out, "ONLINE_API");

                        Log.i(TAG, "loadCategoriesNoRoom: success count=" + out.size());

                    } catch (Exception e) {
                        Log.e(TAG, "loadCategoriesNoRoom: parse error " + e.getMessage(), e);
                    }
                },
                err -> {
                    int code = (err.networkResponse != null) ? err.networkResponse.statusCode : -1;
                    String body = "";

                    try {
                        if (err.networkResponse != null && err.networkResponse.data != null) {
                            body = new String(err.networkResponse.data, StandardCharsets.UTF_8);
                        } else if (err.getMessage() != null) {
                            body = err.getMessage();
                        }
                    } catch (Exception ignored) {
                    }

                    Log.w(TAG, "loadCategoriesNoRoom: failed code=" + code
                            + " detail=" + safeLogDetail(body));
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> h = new HashMap<>();
                h.put("Accept", "application/json");
                h.put("Authorization", "Token " + token.trim());
                return h;
            }
        };

        req.setTag("CATEGORIES_MAIN");
        ApiClient.getInstance(this).add(req);
    }

    private void loadCategoriesRoomFirst() {
        final String token = session != null ? session.getToken() : null;
        if (token == null || token.trim().isEmpty()) {
            Log.w(TAG, "loadCategoriesRoomFirst: token empty");
            applyCategoriesToUI(new ArrayList<>(), "TOKEN_EMPTY_ALL_ONLY");
            return;
        }

        MasterDataRepository masterDataRepository = new MasterDataRepository(getApplicationContext());
        masterDataRepository.loadCategoriesRoomFirst(token, new MasterDataRepository.CategoriesCallback() {
            @Override
            public void onLocalCategories(@NonNull List<Category> categories) {
                if (!isActivityAlive()) return;
                Log.i(TAG, "Room category count=" + categories.size());
                applyCategoriesToUI(categories, "ROOM");
            }

            @Override
            public void onRemoteCategories(@NonNull List<Category> categories) {
                if (!isActivityAlive()) return;
                categoriesAppliedFromOnline = true;
                Log.i(TAG, "API category count=" + categories.size());
                applyCategoriesToUI(categories, "ONLINE_API_ROOM_SYNCED");
            }

            @Override
            public void onNoInternet(@NonNull List<Category> localCategories) {
                if (!isActivityAlive()) return;
                if (localCategories.isEmpty()) {
                    Toast.makeText(MainActivity.this,
                            MasterDataRepository.MESSAGE_NO_LOCAL_CATEGORY_DATA,
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                Toast.makeText(MainActivity.this,
                        MasterDataRepository.MESSAGE_NO_INTERNET_SHOWING_LOCAL,
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(@NonNull String message) {
                if (!isActivityAlive()) return;
                Log.w(TAG, "loadCategoriesRoomFirst: API failed detail=" + safeLogDetail(message));
            }
        });
    }

    private static JSONArray extractResultsArray(String response) throws Exception {
        Object parsed = new JSONTokener(response == null ? "[]" : response).nextValue();

        if (parsed instanceof JSONArray) {
            return (JSONArray) parsed;
        }

        if (parsed instanceof JSONObject) {
            JSONArray results = ((JSONObject) parsed).optJSONArray("results");
            return results != null ? results : new JSONArray();
        }

        return new JSONArray();
    }

    private void setupBackHandling() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (userPopup != null && userPopup.isShowing()) {
                    userPopup.dismiss();
                    return;
                }

                if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                    getSupportFragmentManager().popBackStack();
                    hideOverlayIfNoOverlayFragments();
                    return;
                }

                finish();
            }
        });
    }

    private void showUserMenu(View anchor) {
        View content = LayoutInflater.from(this).inflate(R.layout.popup_user_menu, null);

        ((TextView) content.findViewById(R.id.tvUserName)).setText(cachedUsername);
        ((TextView) content.findViewById(R.id.tvUserRole)).setText(cachedRole);

        View btnOfflineOrders = content.findViewById(R.id.btnOfflineOrders);
        View btnPrivacy = content.findViewById(R.id.btnPrivacy);
        View btnCloseShift = content.findViewById(R.id.btnCloseShift);
        View btnLogout = content.findViewById(R.id.btnLogout);

        boolean canCloseShift = session != null
                && session.isShiftOpen()
                && session.getShiftId() > 0;
        if (btnCloseShift != null) {
            btnCloseShift.setEnabled(canCloseShift);
            btnCloseShift.setAlpha(canCloseShift ? 1f : 0.45f);
        }

        if (btnOfflineOrders != null) {
            btnOfflineOrders.setOnClickListener(v -> {
                if (userPopup != null && userPopup.isShowing()) userPopup.dismiss();
                openOfflineOrdersFromUserMenu();
            });
        }

        if (btnPrivacy != null) {
            btnPrivacy.setOnClickListener(v -> {
                if (userPopup != null && userPopup.isShowing()) userPopup.dismiss();
                openPrivacyPolicyFromUserMenu();
            });
        }

        if (btnCloseShift != null) {
            btnCloseShift.setOnClickListener(v -> {
                if (userPopup != null && userPopup.isShowing()) userPopup.dismiss();
                requestCloseShiftFromUserMenu();
            });
        }

        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> {
                if (userPopup != null && userPopup.isShowing()) userPopup.dismiss();
                requestLogoutFromUserMenu();
            });
        }

        userPopup = new PopupWindow(content, dp(260), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        userPopup.setOutsideTouchable(true);
        userPopup.setBackgroundDrawable(new ColorDrawable(0));
        userPopup.showAsDropDown(anchor, -dp(200), dp(10));
    }

    @Override
    public void openOfflineOrdersFromUserMenu() {
        startActivity(new Intent(this, PendingOrdersActivity.class));
    }

    @Override
    public void openPrivacyPolicyFromUserMenu() {
        Toast.makeText(this, getString(R.string.msg_privacy_policy_clicked), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void requestCloseShiftFromUserMenu() {
        closeShiftOnly();
    }

    @Override
    public void requestLogoutFromUserMenu() {
        logout();
    }

    private void closeShiftOnly() {
        if (userPopup != null && userPopup.isShowing()) userPopup.dismiss();
        if (closeShiftFlowRunning) return;
        closeShiftFlowRunning = true;

        final int shopId = resolveShopIdSafe();
        final ShiftRepository repo = new ShiftRepository(MainActivity.this, session);

        if (!isNetworkAvailable()) {
            closeShiftFlowRunning = false;
            safeUi(() -> ErrorHandler.showNoInternet(MainActivity.this, null));
            return;
        }

        repo.getCurrent(shopId, new ShiftRepository.CurrentCallback() {
            @Override
            public void onSuccess(boolean open, com.valdker.pos.models.Shift shift) {
                closeShiftFlowRunning = false;
                if (open && shift != null) {
                    safeUi(() -> showCloseShiftDialog(shopId, repo, false));
                } else {
                    safeUi(() -> Toast.makeText(MainActivity.this,
                            getString(R.string.msg_no_open_shift),
                            Toast.LENGTH_SHORT).show());
                }
            }

            @Override
            public void onError(@NonNull String message) {
                closeShiftFlowRunning = false;
                safeUi(() -> ErrorHandler.handleApiError(MainActivity.this, message));
            }
        });
    }

    private void logout() {
        if (userPopup != null && userPopup.isShowing()) userPopup.dismiss();
        logoutWithShiftClose();
    }

    private int resolveShopIdSafe() {
        int id = session.getShopId();
        return id > 0 ? id : 1;
    }

    private void logoutWithShiftClose() {
        if (logoutFlowRunning) return;
        logoutFlowRunning = true;

        final int shopId = resolveShopIdSafe();
        final ShiftRepository repo = new ShiftRepository(MainActivity.this, session);

        repo.getCurrent(shopId, new ShiftRepository.CurrentCallback() {
            @Override
            public void onSuccess(boolean open, com.valdker.pos.models.Shift shift) {
                if (!open || shift == null) {
                    logoutFlowRunning = false;
                    doLogoutNow();
                    return;
                }
                safeUi(() -> showCloseShiftDialog(shopId, repo, true));
            }

            @Override
            public void onError(@NonNull String message) {
                logoutFlowRunning = false;
                safeUi(() -> ErrorHandler.handleApiError(MainActivity.this, message));
            }
        });
    }

    private void showCloseShiftDialog(int shopId, @NonNull ShiftRepository repo, boolean logoutAfter) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_close_shift, null);
        EditText etClosing = v.findViewById(R.id.etClosingCash);
        EditText etNote = v.findViewById(R.id.etNote);

        String okText = logoutAfter ? getString(R.string.action_close_shift_logout) : getString(R.string.action_close_shift);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.title_closing_cash))
                .setView(v)
                .setCancelable(false)
                .setNegativeButton(getString(R.string.action_cancel), (d, w) -> {
                    logoutFlowRunning = false;
                    closeShiftFlowRunning = false;
                    d.dismiss();
                })
                .setPositiveButton(okText, null)
                .create();

        dialog.setOnShowListener(dlg -> {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(btn -> {
                String closingCash = (etClosing.getText() != null) ? etClosing.getText().toString().trim() : "";
                String note = (etNote.getText() != null) ? etNote.getText().toString().trim() : "";

                if (closingCash.isEmpty()) {
                    etClosing.setError(getString(R.string.msg_closing_cash_required));
                    etClosing.requestFocus();
                    return;
                }

                if (!isValidMoneyAmount(closingCash)) {
                    etClosing.setError(getString(R.string.msg_invalid_money_amount));
                    etClosing.requestFocus();
                    return;
                }

                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setEnabled(false);

                repo.closeShift(shopId, closingCash, note, new ShiftRepository.SimpleCallback() {
                    @Override
                    public void onSuccess() {
                        safeUi(() -> {
                            dialog.dismiss();
                            session.clearShift();

                            logoutFlowRunning = false;
                            closeShiftFlowRunning = false;

                            if (logoutAfter) {
                                doLogoutNow();
                            } else {
                                CartManager.getInstance(MainActivity.this).clear();
                                Toast.makeText(
                                        MainActivity.this,
                                        getString(R.string.msg_shift_closed_open_new),
                                        Toast.LENGTH_LONG
                                ).show();

                                shiftGateRunning = false;
                                isCheckingShift = false;
                                shiftGateAlreadyPassed = false;
                                isShiftDialogShowing = false;
                                if (BuildConfig.DEBUG) {
                                    Log.d(TAG, "SHIFT_GATE: close shift success, local shift cleared");
                                }
                                ensureShiftOpenOrBlock();
                            }
                        });
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        safeUi(() -> {
                            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                            ErrorHandler.handleApiError(MainActivity.this, message);
                        });
                    }
                });
            });
        });

        dialog.show();
    }

    private boolean isValidMoneyAmount(@NonNull String value) {
        try {
            BigDecimal amount = new BigDecimal(value.trim());
            return amount.compareTo(BigDecimal.ZERO) >= 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void doLogoutNow() {
        session.clearShift();
        session.clear();
        CartManager.getInstance(this).clear();
        goToLogin();
    }

    private void goToLogin() {
        if (userPopup != null && userPopup.isShowing()) userPopup.dismiss();

        Intent i = new Intent(this, LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private void refreshCartBadge() {
        int count = CartManager.getInstance(this).getTotalQty();
        setCartCount(count);
    }

    public void setCartCount(int count) {
        if (tvCartBadge == null) return;
        tvCartBadge.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        tvCartBadge.setText(String.valueOf(count));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (userPopup != null && userPopup.isShowing()) {
            userPopup.dismiss();
        }

        draftExecutor.shutdownNow();

        ApiClient.getInstance(this).cancelAll("CATEGORIES_MAIN");
        ApiClient.getInstance(this).cancelAll("CAT_CACHE_REPO");
        ApiClient.getInstance(this).cancelAll("SHIFT");
        ApiClient.getInstance(this).cancelAll(TAG_RETAIL_ORDER_CREATE);
    }

    @Override
    public void onRetailBarcodeClick() {
        onBarcodeClick();
    }

    @Override
    public void onRetailCartClick() {
        openRetailNativeCheckout();
    }

    @Override
    public void onRetailUserMenuClick(@NonNull View anchor) {
        showUserMenu(anchor);
    }

    @Override
    public void onRetailAddToCartRequested(@NonNull RetailCartItem item) {
        Log.d(TAG, "Retail local scan item: " + item.name);
    }

}
