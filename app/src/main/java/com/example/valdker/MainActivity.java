package com.example.valdker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.valdker.adapters.CategoryAdapter;
import com.example.valdker.auth.AuthEvents;
import com.example.valdker.cart.CartManager;
import com.example.valdker.models.Category;
import com.example.valdker.models.Shop;
import com.example.valdker.network.ApiClient;
import com.example.valdker.offline.db.entities.ShiftEntity;
import com.example.valdker.offline.repositories.CategoryCacheRepository;
import com.example.valdker.repositories.ShiftRepository;
import com.example.valdker.repositories.ShopRepository;
import com.example.valdker.ui.ProductsFragment;
import com.example.valdker.ui.shift.ShiftOpenDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("deprecation")
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MAIN_NATIVE";
    private static final String TAG_SHIFT_DIALOG = "SHIFT_OPEN_DIALOG";

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

    private String allIconUrl = null;
    private CategoryAdapter categoryAdapter;
    private final List<Category> categoryList = new ArrayList<>();

    private SessionManager session;
    private String cachedUsername = "admin";
    private String cachedRole = "cashier";

    private ImageView imgShopLogo;
    private TextView tvShopAddress;

    private final CartManager.Listener cartListener = this::refreshCartBadge;

    // =============================
    // SAFETY HELPERS
    // =============================
    private boolean isActivityAlive() {
        return !isFinishing() && !isDestroyed();
    }

    private void safeUi(Runnable r) {
        if (!isActivityAlive()) return;
        runOnUiThread(() -> {
            if (!isActivityAlive()) return;
            r.run();
        });
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
    // SHIFT GATE (OPEN) - OFFLINE SAFE + AUTO SYNC ONLINE
    // ============================================================

    /**
     * Ensures shift is open before allowing POS usage.
     * OFFLINE: if no local active shift -> show dialog
     * ONLINE: check Room first, then /shifts/current/; also auto-sync offline-open shift to server
     */
    private void ensureShiftOpenOrBlock() {
        if (shiftGateRunning) return;
        shiftGateRunning = true;

        final SessionManager sm = session;
        final ShiftRepository repo = new ShiftRepository(MainActivity.this, sm);

        // ✅ 0) IMPORTANT: validate session vs Room BEFORE skipping
        validateShiftStateBeforeSkip(sm);

        // ✅ 1) If session says OPEN, still DO a best-effort sync (if needed), then skip
        try {
            boolean sessionOpen = sm.isShiftOpen();
            long sid = sm.getShiftId();
            long localId = sm.getShiftLocalId();

            if (sessionOpen && (sid > 0 || localId > 0)) {
                Log.i(TAG, "SHIFT_GATE: session says OPEN (sid=" + sid + ", localId=" + localId + ")");
                shiftGateRunning = false;

                // ✅ If local shift exists but server id missing, try sync when online
                if (isNetworkAvailable() && sid <= 0 && localId > 0) {
                    new Thread(() -> reconcileOfflineShiftToServer(repo, sm)).start();
                }
                return;
            }
        } catch (Exception ignored) {}

        // 2) Always check Room active shift first (works for offline & online).
        new Thread(() -> {
            try {
                com.example.valdker.offline.repo.ShiftRepository offRepo =
                        new com.example.valdker.offline.repo.ShiftRepository(getApplicationContext());

                ShiftEntity active = offRepo.getActiveShift();
                if (active != null) {
                    sm.setShiftOpen(true);
                    sm.setShiftLocalId(active.id);

                    Log.i(TAG, "SHIFT_GATE: Room says active shift localId=" + active.id + " -> skip dialog");
                    shiftGateRunning = false;

                    // ✅ If online and server id missing, try reconcile to server
                    if (isNetworkAvailable() && sm.getShiftId() <= 0) {
                        new Thread(() -> reconcileOfflineShiftToServer(repo, sm)).start();
                    }
                    return;
                }
            } catch (Exception e) {
                Log.e(TAG, "SHIFT_GATE: Room active check failed: " + e.getMessage());
            }

            // 3) If OFFLINE and no local active shift -> show dialog ONCE
            if (!isNetworkAvailable()) {
                Log.i(TAG, "SHIFT_GATE: offline and no active shift -> show shift dialog");
                shiftGateRunning = false;
                safeUi(() -> showShiftDialogOnce(repo, sm));
                return;
            }

            // 4) ONLINE: call /shifts/current/ with timeout fallback
            safeUi(() -> {
                Log.i(TAG, "SHIFT_GATE: checking /shifts/current/ (ONLINE) ...");

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

                            // Mirror to offline (best effort)
                            new Thread(() -> mirrorOpenShiftOffline(sm)).start();
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

        }).start();
    }

    /**
     * ✅ CRITICAL FIX:
     * If session says OPEN because of old localId, verify Room actually has active shift.
     * If not -> reset session shift so dialog can show.
     */
    private void validateShiftStateBeforeSkip(@NonNull SessionManager sm) {
        try {
            boolean sessionOpen = sm.isShiftOpen();
            long sid = sm.getShiftId();
            long localId = sm.getShiftLocalId();

            if (!sessionOpen) return;

            if (sid > 0) return; // server has shift id -> ok

            if (localId > 0) {
                com.example.valdker.offline.repo.ShiftRepository offRepo =
                        new com.example.valdker.offline.repo.ShiftRepository(getApplicationContext());

                ShiftEntity active = offRepo.getActiveShift();

                if (active == null) {
                    Log.w(TAG, "SHIFT_GATE: session stale (localId=" + localId + ") but Room has no active shift -> reset");
                    sm.clearShift();
                } else {
                    sm.setShiftOpen(true);
                    sm.setShiftLocalId(active.id);
                    Log.i(TAG, "SHIFT_GATE: Room active shift confirmed localId=" + active.id);
                }
            } else {
                Log.w(TAG, "SHIFT_GATE: session says OPEN but sid/localId empty -> reset");
                sm.clearShift();
            }

        } catch (Exception e) {
            Log.e(TAG, "SHIFT_GATE: validateShiftStateBeforeSkip error: " + e.getMessage());
            try { sm.clearShift(); } catch (Exception ignored) {}
        }
    }

    /**
     * ✅ Auto-sync: If user opened shift OFFLINE (Room active) but server has no shift open,
     * then open it on server when internet is back.
     *
     * Conditions:
     * - session isShiftOpen true
     * - localId > 0
     * - sid == 0
     * - network available
     */
    private void reconcileOfflineShiftToServer(@NonNull ShiftRepository repo, @NonNull SessionManager sm) {
        try {
            if (!isNetworkAvailable()) return;

            boolean open = sm.isShiftOpen();
            long sid = sm.getShiftId();
            long localId = sm.getShiftLocalId();

            if (!open || localId <= 0 || sid > 0) return;

            Log.i(TAG, "SHIFT_SYNC: try reconcile offline->server (localId=" + localId + ") ...");

            // 1) Check server current first
            repo.getCurrent(new ShiftRepository.CurrentCallback() {
                @Override
                public void onSuccess(boolean open, com.example.valdker.models.Shift shift) {
                    if (open && shift != null) {
                        sm.setShiftOpen(true);
                        sm.setShiftId(shift.id);
                        sm.setOpeningCash(
                                (shift.opening_cash == null || shift.opening_cash.trim().isEmpty())
                                        ? sm.getOpeningCash()
                                        : shift.opening_cash
                        );

                        Log.i(TAG, "SHIFT_SYNC: server already open, sid=" + shift.id);
                        return;
                    }

                    // 2) Server not open -> open shift using stored openingCash
                    String tmpCash = sm.getOpeningCash();
                    if (tmpCash == null || tmpCash.trim().isEmpty()) tmpCash = "0.00";

                    final String openingCash = tmpCash;

                    Log.i(TAG, "SHIFT_SYNC: server not open -> opening on server with cash=" + openingCash);

                    repo.openShift(openingCash, "OFFLINE_SYNC", new ShiftRepository.OpenCallback() {
                        @Override
                        public void onSuccess(@NonNull com.example.valdker.models.Shift shift) {
                            sm.setShiftOpen(true);
                            sm.setShiftId(shift.id);
                            sm.setOpeningCash(
                                    (shift.opening_cash == null || shift.opening_cash.trim().isEmpty())
                                            ? openingCash
                                            : shift.opening_cash
                            );
                            Log.i(TAG, "SHIFT_SYNC: opened on server OK sid=" + shift.id);
                        }

                        @Override
                        public void onError(int statusCode, @NonNull String message) {
                            Log.e(TAG, "SHIFT_SYNC: openShift server failed " + statusCode + " " + message);
                        }
                    });
                }

                @Override
                public void onError(@NonNull String message) {
                    Log.e(TAG, "SHIFT_SYNC: getCurrent failed: " + message);
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "SHIFT_SYNC: reconcile error: " + e.getMessage());
        }
    }

    /**
     * Show shift dialog safely (only once).
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

        dlg.setListener((openingCash, note) -> {

            // Re-check: if shift became active in Room between dialog open and submit
            new Thread(() -> {
                try {
                    com.example.valdker.offline.repo.ShiftRepository offRepo =
                            new com.example.valdker.offline.repo.ShiftRepository(getApplicationContext());
                    ShiftEntity active = offRepo.getActiveShift();
                    if (active != null) {
                        sm.setShiftOpen(true);
                        sm.setShiftLocalId(active.id);

                        Log.i(TAG, "SHIFT_GATE: submit skipped; Room already has active shift localId=" + active.id);

                        safeUi(() -> {
                            shiftDialogShowing = false;
                            dlg.dismissAllowingStateLoss();
                            Toast.makeText(MainActivity.this, "Shift already open (OFFLINE)", Toast.LENGTH_SHORT).show();

                            // if online and server id missing, try reconcile
                            if (isNetworkAvailable() && sm.getShiftId() <= 0) {
                                new Thread(() -> reconcileOfflineShiftToServer(repo, sm)).start();
                            }
                        });
                        return;
                    }
                } catch (Exception ignored) {}

                // proceed normal open
                safeUi(() -> doOpenShift(openingCash, note, repo, sm, dlg));

            }).start();
        });

        dlg.show(getSupportFragmentManager(), TAG_SHIFT_DIALOG);
    }

    private void doOpenShift(@NonNull String openingCash,
                             @NonNull String note,
                             @NonNull ShiftRepository repo,
                             @NonNull SessionManager sm,
                             @NonNull ShiftOpenDialogFragment dlg) {

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

                // Mirror: open offline shift
                new Thread(() -> mirrorOpenShiftOffline(sm)).start();

                safeUi(() -> dlg.dismissAllowingStateLoss());
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                Log.e(TAG, "SHIFT_GATE open error: " + statusCode + " " + message);

                // 409 means shift already open on server
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

                                new Thread(() -> mirrorOpenShiftOffline(sm)).start();
                            } else {
                                fallbackOpenShiftOffline(openingCash, sm, repo, dlg);
                            }
                        }

                        @Override
                        public void onError(@NonNull String msg) {
                            Log.e(TAG, "SHIFT_GATE: 409 recheck failed: " + msg);
                            fallbackOpenShiftOffline(openingCash, sm, repo, dlg);
                        }
                    });
                    return;
                }

                boolean looksOffline =
                        statusCode <= 0 ||
                                statusCode >= 500 ||
                                message.toLowerCase().contains("timeout") ||
                                message.toLowerCase().contains("unknownhost") ||
                                message.toLowerCase().contains("unable") ||
                                message.toLowerCase().contains("network") ||
                                message.toLowerCase().contains("failed");

                if (looksOffline) {
                    fallbackOpenShiftOffline(openingCash, sm, repo, dlg);
                    return;
                }

                // Other errors -> keep dialog visible for retry
                shiftDialogShowing = false;
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
            }

            private void fallbackOpenShiftOffline(@NonNull String openingCash,
                                                  @NonNull SessionManager sm,
                                                  @NonNull ShiftRepository repo,
                                                  @NonNull ShiftOpenDialogFragment dlg) {

                new Thread(() -> {
                    try {
                        double cashOffline;
                        try {
                            cashOffline = Double.parseDouble(openingCash.trim().isEmpty() ? "0" : openingCash.trim());
                        } catch (Exception e) {
                            cashOffline = 0.0;
                        }

                        com.example.valdker.offline.repo.ShiftRepository offRepo =
                                new com.example.valdker.offline.repo.ShiftRepository(getApplicationContext());

                        ShiftEntity active = offRepo.getActiveShift();
                        if (active == null) {
                            active = offRepo.openShift(cashOffline);
                            Log.i(TAG, "SHIFT_GATE: opened OFFLINE localId=" + active.id);
                        } else {
                            Log.i(TAG, "SHIFT_GATE: OFFLINE shift already active localId=" + active.id);
                        }

                        sm.setShiftOpen(true);
                        sm.setShiftLocalId(active.id);
                        sm.setShiftId(0); // important: server id unknown
                        sm.setOpeningCash(String.format(Locale.US, "%.2f", cashOffline));

                        safeUi(() -> {
                            shiftDialogShowing = false;
                            dlg.dismissAllowingStateLoss();
                            Toast.makeText(MainActivity.this, "Shift opened OFFLINE", Toast.LENGTH_LONG).show();

                            // ✅ if network returns later, auto-sync will happen via onStart/ensureShiftOpenOrBlock
                        });

                    } catch (Exception ex) {
                        safeUi(() -> {
                            shiftDialogShowing = false;
                            Toast.makeText(MainActivity.this,
                                    "Open shift offline failed: " + ex.getMessage(),
                                    Toast.LENGTH_LONG).show();

                            showShiftDialogOnce(repo, sm);
                        });
                    }
                }).start();
            }
        });
    }

    /**
     * Best-effort mirror: open offline shift after ONLINE open.
     */
    private void mirrorOpenShiftOffline(@NonNull SessionManager sm) {
        try {
            double cashOffline;
            try {
                cashOffline = Double.parseDouble(sm.getOpeningCash());
            } catch (Exception e) {
                cashOffline = 0.0;
            }

            com.example.valdker.offline.repo.ShiftRepository offRepo =
                    new com.example.valdker.offline.repo.ShiftRepository(getApplicationContext());

            ShiftEntity active = offRepo.getActiveShift();
            if (active == null) {
                ShiftEntity s = offRepo.openShift(cashOffline);
                sm.setShiftLocalId(s.id);
                Log.i(TAG, "SHIFT_GATE: mirrored OFFLINE shift opened localId=" + s.id);
            } else {
                sm.setShiftLocalId(active.id);
                Log.i(TAG, "SHIFT_GATE: OFFLINE shift already active localId=" + active.id);
            }
        } catch (Exception ex) {
            Log.e(TAG, "SHIFT_GATE: mirror offline open failed: " + ex.getMessage());
        }
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

                if (imgShopLogo == null) return;

                String logoUrl = forceHttps(shop.logoUrl);
                if (logoUrl == null || logoUrl.trim().isEmpty()) {
                    imgShopLogo.setImageResource(R.drawable.bg_logo_circle);
                    return;
                }

                Glide.with(MainActivity.this)
                        .load(logoUrl)
                        .circleCrop()
                        .placeholder(R.drawable.bg_logo_circle)
                        .error(R.drawable.bg_logo_circle)
                        .into(imgShopLogo);
            }

            @Override
            public void onEmpty() {
                TextView tvBrand = findViewById(R.id.tvBrand);
                if (tvBrand != null) tvBrand.setText("—");
                if (tvShopAddress != null) tvShopAddress.setText("—");
                if (imgShopLogo != null) imgShopLogo.setImageResource(R.drawable.bg_logo_circle);
            }

            @Override
            public void onError(String message) {
                if (tvShopAddress != null) tvShopAddress.setText("—");
                if (imgShopLogo != null) imgShopLogo.setImageResource(R.drawable.bg_logo_circle);
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

        setContentView(R.layout.activity_main);

        tvCartBadge = findViewById(R.id.tvCartBadge);
        btnUser = findViewById(R.id.btnUser);

        imgShopLogo = findViewById(R.id.imgShopLogo);
        tvShopAddress = findViewById(R.id.tvShopAddress);

        cachedUsername = safe(session.getUsername(), "admin");
        cachedRole = safe(session.getRole(), "cashier");

        setupNativeButtons();
        setupCategories();
        ensureDefaultFragment();
        setupBackHandling();

        loadShopHeader();
        refreshCartBadge();

        ensureShiftOpenOrBlock();
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

        // ✅ When returning to app, re-check and also auto-sync if needed
        ensureShiftOpenOrBlock();
    }

    @Override
    protected void onStop() {
        super.onStop();

        try {
            unregisterReceiver(logoutReceiver);
        } catch (Exception ignored) {}

        CartManager.getInstance(this).removeListener(cartListener);
    }

    private void ensureDefaultFragment() {
        if (getSupportFragmentManager().findFragmentById(R.id.fragmentContainer) == null) {
            openMainFragment(new ProductsFragment(), "pos");
        }
    }

    private void openMainFragment(@NonNull Fragment fragment, @NonNull String tag) {
        Fragment current = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
        if (current != null && current.getClass() == fragment.getClass()) {
            return;
        }

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment, tag)
                .commit();
    }

    private void setupNativeButtons() {
        View btnCart = findViewById(R.id.btnCart);
        if (btnCart != null) btnCart.setOnClickListener(v -> openCartOverlay());

        if (btnUser != null) btnUser.setOnClickListener(this::showUserMenu);
    }

    private void openCartOverlay() {
        View overlay = findViewById(R.id.overlayContainer);
        if (overlay != null) {
            overlay.setVisibility(View.VISIBLE);
            overlay.bringToFront();
            overlay.requestLayout();
            overlay.invalidate();
        }

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.overlayContainer, new com.example.valdker.ui.CartFragment())
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
    // CATEGORIES (OFFLINE-FIRST)
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

        loadCategoriesOfflineFirst();
    }

    private void applyCategoriesToUI(@NonNull List<Category> list, @NonNull String source) {
        int incoming = (list == null) ? 0 : list.size();

        if (incoming == 0 && categoriesAppliedFromOnline) {
            return;
        }

        categoryList.clear();
        categoryList.add(new Category(-1, "All", allIconUrl));

        if (list != null) {
            for (Category c : list) {
                if (c == null) continue;
                if (c.id == -1) continue;
                if (c.name == null || c.name.trim().isEmpty()) continue;
                categoryList.add(c);
            }
        }

        if (categoryAdapter != null) {
            categoryAdapter.setData(categoryList);
        }

        Log.i(TAG, "Categories applied size=" + categoryList.size() + " src=" + source);
    }

    private void loadCategoriesOfflineFirst() {

        CategoryCacheRepository.loadFromRoom(this, new CategoryCacheRepository.ListCallback() {
            @Override
            public void onSuccess(@NonNull List<Category> cached) {
                safeUi(() -> applyCategoriesToUI(cached, "ROOM_FIRST"));
            }

            @Override
            public void onError(@NonNull String message) {
                Log.w(TAG, "Room categories error: " + message);
            }
        });

        if (!isNetworkAvailable()) {
            return;
        }

        CategoryCacheRepository.syncOnlineAndCache(this, new CategoryCacheRepository.ListCallback() {
            @Override
            public void onSuccess(@NonNull List<Category> online) {
                categoriesAppliedFromOnline = (online != null && !online.isEmpty());
                safeUi(() -> applyCategoriesToUI(online, "ONLINE"));
            }

            @Override
            public void onError(@NonNull String message) {
                Log.e(TAG, "Categories sync error: " + message);
            }
        });
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

        content.findViewById(R.id.btnLogout).setOnClickListener(v -> logout());

        View btnCloseShift = content.findViewById(R.id.btnCloseShift);
        if (btnCloseShift != null) {
            btnCloseShift.setOnClickListener(v -> closeShiftOnly());
        }

        userPopup = new PopupWindow(content, dp(260), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        userPopup.setOutsideTouchable(true);
        userPopup.setBackgroundDrawable(new ColorDrawable(0));
        userPopup.showAsDropDown(anchor, -dp(200), dp(10));
    }

    // ============================================================
    // SHIFT CLOSE (ONLINE FIRST, FALLBACK OFFLINE)
    // ============================================================
    private void closeShiftOnly() {
        if (userPopup != null && userPopup.isShowing()) userPopup.dismiss();
        if (closeShiftFlowRunning) return;
        closeShiftFlowRunning = true;

        final int shopId = resolveShopIdSafe();
        final ShiftRepository repo = new ShiftRepository(MainActivity.this, session);

        // If online, prefer closing ONLINE shift (server truth)
        if (isNetworkAvailable()) {
            repo.getCurrent(shopId, new ShiftRepository.CurrentCallback() {
                @Override
                public void onSuccess(boolean open, com.example.valdker.models.Shift shift) {
                    if (open && shift != null) {
                        safeUi(() -> showCloseShiftDialog(shopId, repo, false));
                        return;
                    }
                    // server says no open shift -> close offline if exists
                    checkAndCloseOfflineIfAny();
                }

                @Override
                public void onError(@NonNull String message) {
                    // fallback offline only for network-like errors
                    if (isNetworkError(message)) {
                        checkAndCloseOfflineIfAny();
                    } else {
                        closeShiftFlowRunning = false;
                        safeUi(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show());
                    }
                }
            });
            return;
        }

        // offline: close offline if exists
        checkAndCloseOfflineIfAny();
    }

    private void checkAndCloseOfflineIfAny() {
        new Thread(() -> {
            try {
                com.example.valdker.offline.repo.ShiftRepository offRepo =
                        new com.example.valdker.offline.repo.ShiftRepository(getApplicationContext());

                ShiftEntity active = offRepo.getActiveShift();

                safeUi(() -> {
                    closeShiftFlowRunning = false;
                    if (active != null) {
                        showCloseShiftOfflineDialog(offRepo, active);
                    } else {
                        Toast.makeText(MainActivity.this, "Tidak ada shift yang buka.", Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception e) {
                safeUi(() -> {
                    closeShiftFlowRunning = false;
                    Toast.makeText(MainActivity.this, "Offline check error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private boolean isNetworkError(String msg) {
        if (msg == null) return true;
        String m = msg.toLowerCase();
        return m.contains("timeout")
                || m.contains("unknownhost")
                || m.contains("unable")
                || m.contains("failed to connect")
                || m.contains("network")
                || m.contains("connection")
                || m.contains("ssl")
                || m.contains("socket");
    }

    private void showCloseShiftOfflineDialog(
            @NonNull com.example.valdker.offline.repo.ShiftRepository offRepo,
            @NonNull ShiftEntity active
    ) {
        View v = LayoutInflater.from(MainActivity.this)
                .inflate(R.layout.dialog_close_shift, null);

        EditText etClosing = v.findViewById(R.id.etClosingCash);

        new MaterialAlertDialogBuilder(MainActivity.this)
                .setTitle("Closing Cash (OFFLINE)")
                .setView(v)
                .setCancelable(false)
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .setPositiveButton("Close Shift", (d, w) -> {

                    double closingVal = 0.0;
                    try {
                        closingVal = Double.parseDouble(etClosing.getText().toString().trim());
                    } catch (Exception ignored) {}

                    final double fClosing = closingVal;
                    final long fShiftId = active.id;

                    new Thread(() -> {
                        try {
                            offRepo.closeShift(fShiftId, fClosing);

                            session.clearShift();

                            safeUi(() -> {
                                Toast.makeText(MainActivity.this, "Shift closed OFFLINE", Toast.LENGTH_LONG).show();
                                shiftGateRunning = false;
                                shiftDialogShowing = false;
                                ensureShiftOpenOrBlock();
                            });

                        } catch (Exception ex) {
                            safeUi(() -> Toast.makeText(MainActivity.this,
                                    "Close shift failed: " + ex.getMessage(),
                                    Toast.LENGTH_LONG).show());
                        }
                    }).start();
                })
                .show();
    }

    // ============================================================
    // LOGOUT + CLOSE SHIFT ONLINE (existing)
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
                    etClosing.setError("Taka osan obrigatóriu");
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
                                Toast.makeText(MainActivity.this,
                                        "Turnu taka ona. Favor loke turnu atu hahú halo tranzasaun.",
                                        Toast.LENGTH_LONG).show();

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

    // ============================================================
    // UI HELPERS
    // ============================================================
    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private String safe(String v, String fallback) {
        if (v == null) return fallback;
        String s = v.trim();
        return s.isEmpty() ? fallback : s;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (userPopup != null && userPopup.isShowing()) userPopup.dismiss();

        ApiClient.getInstance(this).cancelAll("CAT_CACHE_REPO");
        ApiClient.getInstance(this).cancelAll("SHIFT");
    }
}