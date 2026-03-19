package com.example.valdker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonArrayRequest;
import com.bumptech.glide.Glide;
import com.example.valdker.adapters.CategoryAdapter;
import com.example.valdker.auth.AuthEvents;
import com.example.valdker.cart.CartManager;
import com.example.valdker.models.Category;
import com.example.valdker.models.Shop;
import com.example.valdker.network.ApiClient;
import com.example.valdker.network.ApiConfig;
import com.example.valdker.repositories.ShiftRepository;
import com.example.valdker.repositories.ShopRepository;
import com.example.valdker.ui.ProductsFragment;
import com.example.valdker.ui.shift.ShiftOpenDialogFragment;
import com.example.valdker.workshop.WorkshopPOSFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.zxing.integration.android.IntentIntegrator;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("deprecation")
public class MainActivity extends AppCompatActivity
        implements WorkshopPOSFragment.WorkshopHostActions {

    private static final String TAG = "MAIN_NATIVE";
    private static final String TAG_SHIFT_DIALOG = "SHIFT_OPEN_DIALOG";
    private static final String TAG_BARCODE_DIALOG = "BARCODE_DIALOG";

    private static final int CAMERA_REQUEST = 100;

    private volatile boolean barcodeDispatchRunning = false;
    @Nullable
    private String pendingBarcode = null;

    private String businessType = "retail";
    private boolean useGridPosLayout = false;
    private boolean showProductImagesInPos = false;
    private boolean enableBarcodeScan = true;
    private boolean enableDineIn = false;
    private boolean enableTakeaway = false;
    private boolean enableDelivery = false;
    private boolean enableTableNumber = false;
    private boolean enableSplitPayment = false;

    // -----------------------------
    // SHIFT GATE FLAGS
    // -----------------------------
    private volatile boolean shiftGateRunning = false;
    private volatile boolean shiftDialogShowing = false;

    private volatile boolean closeShiftFlowRunning = false;
    private volatile boolean logoutFlowRunning = false;

    private volatile boolean categoriesAppliedFromOnline = false;

    private TextView tvCartBadge;
    private PopupWindow userPopup;
    private ImageButton btnUser;

    private View btnBarcode;
    private EditText etSearch;
    private String allIconUrl = null;
    private CategoryAdapter categoryAdapter;
    private final List<Category> categoryList = new ArrayList<>();

    private SessionManager session;
    private String cachedUsername = "admin";
    private String cachedRole = "cashier";

    private ImageView imgLogo;
    private TextView tvShopAddress;

    private final CartManager.Listener cartListener = this::refreshCartBadge;

    // =============================
    // SAFETY HELPERS
    // =============================
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

    // =============================
    // BUSINESS TYPE HELPERS
    // =============================
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

    // =============================
    // LOGOUT RECEIVER
    // =============================
    private final BroadcastReceiver logoutReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.w(TAG, "Force logout received. Clearing session and returning to Login.");
            session.clearShift();
            session.clear();
            goToLogin();
        }
    };

    // ============================================================
    // SHIFT GATE (OPEN) - ONLINE ONLY (NO ROOM / OFFLINE)
    // ============================================================

    /**
     * Ensures shift is open before allowing POS usage.
     * ONLINE ONLY: check /shifts/current/; if not open -> show dialog.
     * OFFLINE: show message + show dialog (but open will fail without internet).
     */
    private void ensureShiftOpenOrBlock() {
        if (shiftGateRunning) return;
        shiftGateRunning = true;

        final SessionManager sm = session;
        final ShiftRepository repo = new ShiftRepository(MainActivity.this, sm);

        try {
            boolean sessionOpen = sm.isShiftOpen();
            long sid = sm.getShiftId();
            if (sessionOpen && sid > 0) {
                Log.i(TAG, "SHIFT_GATE: session says OPEN (sid=" + sid + ") -> skip");
                shiftGateRunning = false;
                return;
            }
        } catch (Exception ignored) {
        }

        if (!isNetworkAvailable()) {
            shiftGateRunning = false;
            safeUi(() -> {
                Toast.makeText(
                        MainActivity.this,
                        "Internet is required to check/open a shift.",
                        Toast.LENGTH_LONG
                ).show();
                showShiftDialogOnce(repo, sm);
            });
            return;
        }

        safeUi(() -> {
            Log.i(TAG, "SHIFT_GATE: checking /shifts/current/ (ONLINE ONLY) ...");

            final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            final Runnable timeout = () -> {
                Log.w(TAG, "SHIFT_GATE: getCurrent timeout -> show dialog");
                shiftGateRunning = false;
                showShiftDialogOnce(repo, sm);
            };

            h.postDelayed(timeout, 3500);

            repo.getCurrent(new ShiftRepository.CurrentCallback() {
                @Override
                public void onSuccess(boolean open, com.example.valdker.models.Shift shift) {
                    h.removeCallbacks(timeout);

                    Log.i(TAG, "SHIFT_GATE: getCurrent success open=" + open
                            + " shift=" + (shift != null ? shift.id : "null"));

                    if (open && shift != null) {
                        sm.setShiftOpen(true);
                        sm.setShiftId(shift.id);
                        sm.setOpeningCash(
                                (shift.opening_cash == null || shift.opening_cash.trim().isEmpty())
                                        ? "0.00"
                                        : shift.opening_cash
                        );

                        Log.i(TAG, "SHIFT_GATE: already OPEN (ONLINE) id=" + shift.id);
                        shiftGateRunning = false;
                        return;
                    }

                    shiftGateRunning = false;
                    showShiftDialogOnce(repo, sm);
                }

                @Override
                public void onError(@NonNull String message) {
                    h.removeCallbacks(timeout);
                    Log.e(TAG, "SHIFT_GATE: getCurrent error: " + message);

                    shiftGateRunning = false;
                    showShiftDialogOnce(repo, sm);
                }
            });
        });
    }

    /**
     * Shows the shift dialog safely (only once).
     */
    private void showShiftDialogOnce(@NonNull ShiftRepository repo, @NonNull SessionManager sm) {
        if (!isActivityAlive()) {
            shiftDialogShowing = false;
            return;
        }

        Fragment existing = getSupportFragmentManager().findFragmentByTag(TAG_SHIFT_DIALOG);
        if (existing != null) {
            Log.i(TAG, "SHIFT_GATE: dialog already shown (fragment exists) -> skip");
            shiftDialogShowing = true;
            return;
        }

        if (shiftDialogShowing) {
            Log.i(TAG, "SHIFT_GATE: dialog already showing (flag) -> skip");
            return;
        }

        shiftDialogShowing = true;
        Log.i(TAG, "SHIFT_GATE: showing ShiftOpenDialog...");

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
            shiftDialogShowing = false;
            Toast.makeText(MainActivity.this, "Internet is required to open a shift.", Toast.LENGTH_LONG).show();
            return;
        }

        repo.openShift(openingCash, note, new ShiftRepository.OpenCallback() {

            @Override
            public void onSuccess(@NonNull com.example.valdker.models.Shift shift) {
                shiftDialogShowing = false;

                Log.i(TAG, "SHIFT_GATE: opened OK (ONLINE) id=" + shift.id);

                sm.setShiftOpen(true);
                sm.setShiftId(shift.id);
                sm.setOpeningCash(
                        (shift.opening_cash == null || shift.opening_cash.trim().isEmpty())
                                ? "0.00"
                                : shift.opening_cash
                );

                safeUi(() -> dlg.dismissAllowingStateLoss());
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                Log.e(TAG, "SHIFT_GATE open error: " + statusCode + " " + message);

                if (statusCode == 409) {
                    repo.getCurrent(new ShiftRepository.CurrentCallback() {
                        @Override
                        public void onSuccess(boolean open, com.example.valdker.models.Shift shift) {
                            shiftDialogShowing = false;
                            safeUi(() -> dlg.dismissAllowingStateLoss());

                            if (open && shift != null) {
                                sm.setShiftOpen(true);
                                sm.setShiftId(shift.id);
                                sm.setOpeningCash(
                                        (shift.opening_cash == null || shift.opening_cash.trim().isEmpty())
                                                ? "0.00"
                                                : shift.opening_cash
                                );
                                Log.i(TAG, "SHIFT_GATE: 409 but current says OPEN id=" + shift.id);
                            } else {
                                safeUi(() -> Toast.makeText(
                                        MainActivity.this,
                                        "Shift is already open, but failed to load shift details.",
                                        Toast.LENGTH_LONG
                                ).show());
                            }
                        }

                        @Override
                        public void onError(@NonNull String msg) {
                            shiftDialogShowing = false;
                            safeUi(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show());
                        }
                    });
                    return;
                }

                shiftDialogShowing = false;
                safeUi(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show());
            }
        });
    }

    private boolean isNetworkAvailable() {
        try {
            android.net.ConnectivityManager cm =
                    (android.net.ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                android.net.Network nw = cm.getActiveNetwork();
                if (nw == null) return false;
                android.net.NetworkCapabilities caps = cm.getNetworkCapabilities(nw);
                return caps != null && (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
                        || caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)
                        || caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET));
            } else {
                android.net.NetworkInfo ni = cm.getActiveNetworkInfo();
                return ni != null && ni.isConnected();
            }
        } catch (Exception e) {
            return false;
        }
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

    // ------------------------------------------------------------
    // SHOP HEADER
    // ------------------------------------------------------------
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
        super.onCreate(savedInstanceState);

        session = new SessionManager(this);

        if (!session.isLoggedIn()) {
            goToLogin();
            return;
        }

        loadBusinessConfig();
        setContentView(R.layout.activity_main);

        tvCartBadge = findViewById(R.id.tvCartBadge);
        btnUser = findViewById(R.id.btnUser);
        btnBarcode = findViewById(R.id.btnBarcode);

        View vSearch = findViewById(R.id.tvSearchHint);
        if (vSearch instanceof EditText) {
            etSearch = (EditText) vSearch;
        }

        imgLogo = findViewById(R.id.imgLogo);
        tvShopAddress = findViewById(R.id.tvShopAddress);

        cachedUsername = safe(session.getUsername(), "admin");
        cachedRole = safe(session.getRole(), "cashier");

        applyBusinessTypeUi();
        setupSearchBox();
        setupNativeButtons();

        if (!isWorkshopBusiness()) {
            setupCategories();
        }

        ensureDefaultFragment();
        setupBackHandling();

        getSupportFragmentManager().addOnBackStackChangedListener(this::hideOverlayIfNoOverlayFragments);

        loadShopHeader();
        refreshCartBadge();

        ensureShiftOpenOrBlock();
    }

    private void applyBusinessTypeUi() {
        View nativeHeader = findViewById(R.id.nativeHeader);
        View btnBarcodeView = findViewById(R.id.btnBarcode);
        View btnCartView = findViewById(R.id.btnCart);
        View rvCategories = findViewById(R.id.rvCategories);
        View searchView = findViewById(R.id.tvSearchHint);

        androidx.constraintlayout.widget.ConstraintLayout.LayoutParams lp =
                (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams)
                        findViewById(R.id.fragmentContainer).getLayoutParams();

        if (isWorkshopBusiness()) {
            if (nativeHeader != null) nativeHeader.setVisibility(View.GONE);
            if (btnBarcodeView != null) btnBarcodeView.setVisibility(View.GONE);
            if (btnCartView != null) btnCartView.setVisibility(View.GONE);
            if (rvCategories != null) rvCategories.setVisibility(View.GONE);
            if (searchView != null) searchView.setVisibility(View.GONE);

            lp.topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
            lp.topToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET;
            findViewById(R.id.fragmentContainer).setLayoutParams(lp);
            return;
        }

        if (nativeHeader != null) nativeHeader.setVisibility(View.VISIBLE);
        if (btnBarcodeView != null) btnBarcodeView.setVisibility(enableBarcodeScan ? View.VISIBLE : View.GONE);
        if (btnCartView != null) btnCartView.setVisibility(View.VISIBLE);
        if (rvCategories != null) rvCategories.setVisibility(View.VISIBLE);
        if (searchView != null) searchView.setVisibility(View.VISIBLE);

        lp.topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET;
        lp.topToBottom = R.id.nativeHeader;
        findViewById(R.id.fragmentContainer).setLayoutParams(lp);
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
            if (f instanceof ProductsFragment) {
                if ("retail".equalsIgnoreCase(businessType) || "workshop".equalsIgnoreCase(businessType)) {
                    ((ProductsFragment) f).onManualSearch(keyword);
                } else {
                    sendBarcodeToProductsFragment(keyword);
                }
            }

            return true;
        });
    }

    private void sendBarcodeToProductsFragment(@NonNull String barcode) {
        String clean = safeTrim(barcode);
        if (clean.isEmpty()) {
            return;
        }

        pendingBarcode = clean;
        dispatchPendingBarcodeToProductsFragment();
    }

    private void dispatchPendingBarcodeToProductsFragment() {
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

                if (f instanceof ProductsFragment && f.isAdded() && f.getView() != null) {
                    try {
                        ((ProductsFragment) f).onBarcodeScanned(barcode);
                        pendingBarcode = null;
                    } catch (Exception e) {
                        Log.e(TAG, "Failed dispatch barcode: " + e.getMessage(), e);
                        Toast.makeText(MainActivity.this, "Failed to process barcode", Toast.LENGTH_SHORT).show();
                    } finally {
                        barcodeDispatchRunning = false;
                    }
                    return;
                }

                tries[0]++;
                if (tries[0] < maxTry) {
                    handler.postDelayed(this, 100);
                } else {
                    barcodeDispatchRunning = false;
                    Toast.makeText(MainActivity.this, "Products screen not ready.", Toast.LENGTH_SHORT).show();
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

            safeUi(() -> sendBarcodeToProductsFragment(clean));
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
                        "Camera permission required to scan barcode",
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
                sendBarcodeToProductsFragment(barcode);
            } else {
                Toast.makeText(this, "Scan cancelled", Toast.LENGTH_SHORT).show();
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

        if (btnUser != null) {
            btnUser.setOnClickListener(this::showUserMenu);
        }
    }

    private void onBarcodeClick() {
        if (!enableBarcodeScan) {
            Toast.makeText(this, "Barcode scan is disabled for this business type.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isWorkshopBusiness()) {
            Toast.makeText(this, "Barcode shortcut is hidden for Workshop POS.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (session != null && !session.isShiftOpen()) {
            Toast.makeText(this, "Open shift first.", Toast.LENGTH_SHORT).show();
            ensureShiftOpenOrBlock();
            return;
        }

        checkCameraPermission();
    }

    private void openCartOverlay() {
        if (isWorkshopBusiness()) {
            Toast.makeText(this, "Workshop workspace is already the main POS screen.", Toast.LENGTH_SHORT).show();
            return;
        }

        View overlay = findViewById(R.id.overlayContainer);
        if (overlay != null) {
            overlay.setVisibility(View.VISIBLE);
            overlay.bringToFront();
            overlay.requestLayout();
            overlay.invalidate();
        }

        Fragment overlayFragment = new com.example.valdker.ui.CartFragment();

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

    // ============================================================
    // CATEGORIES (NO ROOM / OFFLINE)
    // ============================================================
    private void setupCategories() {
        RecyclerView rv = findViewById(R.id.rvCategories);
        if (rv == null) return;

        rv.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        rv.setHasFixedSize(true);

        categoryAdapter = new CategoryAdapter();
        rv.setAdapter(categoryAdapter);

        categoryAdapter.setListener(category -> {
            Fragment f = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
            if (f instanceof ProductsFragment) {
                String filter = (category.id == -1) ? "all" : String.valueOf(category.id);
                ((ProductsFragment) f).setCategoryFilter(filter);
            }
        });

        loadCategoriesNoRoom();
    }

    private void applyCategoriesToUI(@NonNull List<Category> list, @NonNull String source) {
        int incoming = list.size();

        if (incoming == 0 && categoriesAppliedFromOnline) {
            return;
        }

        categoryList.clear();
        categoryList.add(new Category(-1, "All", null));

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

    /**
     * Room/offline category cache has been removed.
     * If you want categories from API later, plug it in here.
     */
    private void loadCategoriesNoRoom() {
        applyCategoriesToUI(new ArrayList<>(), "BOOT_ALL_ONLY");

        final String token = session != null ? session.getToken() : null;
        if (token == null || token.trim().isEmpty()) {
            Log.w(TAG, "loadCategoriesNoRoom: token empty -> keep only All");
            return;
        }

        final String url = ApiConfig.url(session, "api/categories/");

        Log.i(TAG, "loadCategoriesNoRoom: fetching " + url);

        JsonArrayRequest req = new JsonArrayRequest(
                Request.Method.GET,
                url,
                null,
                (JSONArray res) -> {
                    try {
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

                    Log.w(TAG, "loadCategoriesNoRoom: failed code=" + code + " body=" + body);
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

        View btnChangePassword = content.findViewById(R.id.btnChangePassword);
        View btnPrivacy = content.findViewById(R.id.btnPrivacy);
        View btnCloseShift = content.findViewById(R.id.btnCloseShift);
        View btnLogout = content.findViewById(R.id.btnLogout);

        if (btnChangePassword != null) {
            btnChangePassword.setOnClickListener(v -> {
                if (userPopup != null && userPopup.isShowing()) userPopup.dismiss();
                openChangePasswordFromUserMenu();
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
    public void openChangePasswordFromUserMenu() {
        Toast.makeText(this, "Change Password clicked", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void openPrivacyPolicyFromUserMenu() {
        Toast.makeText(this, "Privacy Policy clicked", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void requestCloseShiftFromUserMenu() {
        closeShiftOnly();
    }

    @Override
    public void requestLogoutFromUserMenu() {
        logout();
    }

    // ============================================================
    // SHIFT CLOSE (ONLINE ONLY)
    // ============================================================
    private void closeShiftOnly() {
        if (userPopup != null && userPopup.isShowing()) userPopup.dismiss();
        if (closeShiftFlowRunning) return;
        closeShiftFlowRunning = true;

        final int shopId = resolveShopIdSafe();
        final ShiftRepository repo = new ShiftRepository(MainActivity.this, session);

        if (!isNetworkAvailable()) {
            closeShiftFlowRunning = false;
            safeUi(() -> Toast.makeText(
                    MainActivity.this,
                    "Internet is required to close a shift.",
                    Toast.LENGTH_LONG
            ).show());
            return;
        }

        repo.getCurrent(shopId, new ShiftRepository.CurrentCallback() {
            @Override
            public void onSuccess(boolean open, com.example.valdker.models.Shift shift) {
                closeShiftFlowRunning = false;
                if (open && shift != null) {
                    safeUi(() -> showCloseShiftDialog(shopId, repo, false));
                } else {
                    safeUi(() -> Toast.makeText(MainActivity.this,
                            "There is no open shift.",
                            Toast.LENGTH_SHORT).show());
                }
            }

            @Override
            public void onError(@NonNull String message) {
                closeShiftFlowRunning = false;
                safeUi(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show());
            }
        });
    }

    // ============================================================
    // LOGOUT + CLOSE SHIFT ONLINE
    // ============================================================
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
            public void onSuccess(boolean open, com.example.valdker.models.Shift shift) {
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
                safeUi(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show());
            }
        });
    }

    private void showCloseShiftDialog(int shopId, @NonNull ShiftRepository repo, boolean logoutAfter) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_close_shift, null);
        EditText etClosing = v.findViewById(R.id.etClosingCash);
        EditText etNote = v.findViewById(R.id.etNote);

        String okText = logoutAfter ? "Close Shift & Logout" : "Close Shift";

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Closing Cash")
                .setView(v)
                .setCancelable(false)
                .setNegativeButton("Cancel", (d, w) -> {
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
                    etClosing.setError("Closing cash is required.");
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
                                        "Shift closed. Please open a new shift before making transactions.",
                                        Toast.LENGTH_LONG
                                ).show();

                                shiftGateRunning = false;
                                shiftDialogShowing = false;
                                ensureShiftOpenOrBlock();
                            }
                        });
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        safeUi(() -> {
                            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                            Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                        });
                    }
                });
            });
        });

        dialog.show();
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

    // ============================================================
    // CART BADGE
    // ============================================================
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

        ApiClient.getInstance(this).cancelAll("CATEGORIES_MAIN");
        ApiClient.getInstance(this).cancelAll("CAT_CACHE_REPO");
        ApiClient.getInstance(this).cancelAll("SHIFT");
    }
}