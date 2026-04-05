package com.example.valdker.ui.dashboard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.valdker.utils.LocaleHelper;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.os.LocaleListCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.bumptech.glide.Glide;
import com.example.valdker.BuildConfig;
import com.example.valdker.LoginActivity;
import com.example.valdker.MainActivity;
import com.example.valdker.R;
import com.example.valdker.SessionManager;
import com.example.valdker.models.Shop;
import com.example.valdker.network.ApiClient;
import com.example.valdker.network.ApiConfig;
import com.example.valdker.repositories.ShopRepository;
import com.example.valdker.shop.ShopEvents;
import com.example.valdker.ui.BankAccountActivity;
import com.example.valdker.ui.expenses.ExpensesFragment;
import com.example.valdker.ui.inventorycount.InventoryCountsFragment;
import com.example.valdker.ui.orders.OrdersFragment;
import com.example.valdker.ui.ownerchat.OwnerChatActivity;
import com.example.valdker.ui.reports.ReportsFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;

import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HomeDashboardActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.applyAppLocale(newBase));
    }

    private static final String TAG = "DASHBOARD";

    private static final String TAG_SUMMARY = "dashboard_summary";
    private static final String TAG_SHIFTS = "dashboard_shifts";
    private static final String TAG_HEADER = "dashboard_header";

    private static final String BS_ORDERS = "orders";
    private static final String BS_CUSTOMERS = "customers";
    private static final String BS_PRODUCTS = "products_manage";
    private static final String BS_SUPPLIERS = "suppliers";
    private static final String BS_CATEGORIES = "categories";
    private static final String BS_UNITS = "units";
    private static final String BS_SETTINGS = "settings";
    private static final String BS_EXPENSES = "expenses";
    private static final String BS_REPORTS = "reports";
    private static final String BS_PRODUCT_RETURNS = "product_returns";
    private static final String BS_INVENTORY_COUNTS = "inventory_counts";
    private static final String BS_STOCK_ADJUSTMENTS = "stock_adjustments";
    private static final String BS_STOCK_MOVEMENTS = "stock_movements";
    private static final String BS_BANK_ACCOUNTS = "bank_accounts";
    private static final String BS_PURCHASES = "purchases";

    private static final String ENDPOINT_NET_INCOME_TODAY = "api/reports/net-income-today/";
    private static final String ENDPOINT_SHIFTS = "api/shifts/";

    private final NumberFormat usd = NumberFormat.getCurrencyInstance(Locale.US);

    private SessionManager session;

    private RecyclerView rvDashboard;
    private BottomNavigationView bottomNav;
    private View fragmentContainer;

    private TextView tvRevenueValue;
    private TextView tvExpenseValue;
    private TextView tvNetValue;
    private MaterialButton btnLogout;

    private View header;
    private View contentHost;

    private TextView tvHello;
    private TextView tvBrand;
    private ImageView imgLogo;

    private TextView tvSumValue;
    private TextView tvSumHint;
    private View layoutOwnerSummary;
    private View layoutManagerSummary;

    private View headerContent;
    private View fabAddCustomer;

    private final FragmentManager.OnBackStackChangedListener backStackChangedListener = () -> {
        if (hasOpenedFragment()) {
            showFragmentContainer();
        } else {
            showDashboardGrid();
        }
    };

    private final BroadcastReceiver shopUpdatedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            logd("Shop updated broadcast received. Refreshing header.");
            if (session != null && session.canViewReports()) {
                loadTodayNetIncome();
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        applySavedLanguageCompat();
        super.onCreate(savedInstanceState);

        session = new SessionManager(this);

        if (!session.isLoggedIn()) {
            logw("Not logged in. Redirecting to LoginActivity.");
            goToLogin();
            return;
        }

        if (isCashier()) {
            logw("Cashier detected. Redirecting to POS.");
            openPosAndFinish();
            return;
        }

        setContentView(R.layout.activity_home_dashboard);

        setupSystemBars();
        bindViews();

        getSupportFragmentManager().addOnBackStackChangedListener(backStackChangedListener);

        setupDashboardGrid();
        setupBottomNav();
        configureBottomNavItems();
        applyRoleDeviceUI();

        applyHeaderInsets();
        applyFabInsets();

        setupActions();
        showDashboardGrid();

        renderHeader();
        attachDashboardAdapter();

        enforceNavigationSecurity();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (hasOpenedFragment()) {
                    getSupportFragmentManager().popBackStack();
                    getSupportFragmentManager().executePendingTransactions();

                    if (hasOpenedFragment()) {
                        showFragmentContainer();
                    } else {
                        showDashboardGrid();
                    }
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private void applySavedLanguageCompat() {
        SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        String languageCode = prefs.getString("app_language", "id");
        LocaleListCompat locales = LocaleListCompat.forLanguageTags(languageCode);
        AppCompatDelegate.setApplicationLocales(locales);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            getSupportFragmentManager().removeOnBackStackChangedListener(backStackChangedListener);
        } catch (Exception e) {
            logw("removeOnBackStackChangedListener failed: " + e.getMessage());
        }
    }

    private void applyDashboardStatusBarMode() {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());

        if (controller != null) {
            controller.show(WindowInsetsCompat.Type.statusBars());
            controller.setAppearanceLightStatusBars(false);
        }

        getWindow().setStatusBarColor(Color.TRANSPARENT);
    }

    private void applyFragmentStatusBarMode() {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());

        if (controller != null) {
            controller.show(WindowInsetsCompat.Type.statusBars());
            controller.setAppearanceLightStatusBars(false);
        }

        getWindow().setStatusBarColor(Color.TRANSPARENT);
    }

    private void setupSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());

        if (controller != null) {
            controller.show(WindowInsetsCompat.Type.statusBars());
            controller.setAppearanceLightStatusBars(false);
        }

        getWindow().setStatusBarColor(Color.TRANSPARENT);
    }

    private void bindViews() {
        tvHello = findViewById(R.id.tvHello);
        tvBrand = findViewById(R.id.tvAppBrand);
        imgLogo = findViewById(R.id.imgAppLogo);

        btnLogout = findViewById(R.id.btnLogout);

        tvRevenueValue = findViewById(R.id.tvRevenueValue);
        tvExpenseValue = findViewById(R.id.tvExpenseValue);
        tvNetValue = findViewById(R.id.tvNetValue);

        layoutOwnerSummary = findViewById(R.id.layoutOwnerSummary);
        layoutManagerSummary = findViewById(R.id.layoutManagerSummary);

        header = findViewById(R.id.header);
        headerContent = findViewById(R.id.headerContent);
        contentHost = findViewById(R.id.contentHost);

        bottomNav = findViewById(R.id.bottomNav);
        fragmentContainer = findViewById(R.id.fragmentContainer);

        tvSumValue = findViewById(R.id.tvSumValue);
        tvSumHint = findViewById(R.id.tvSumHint);

        rvDashboard = findViewById(R.id.rvDashboard);
        fabAddCustomer = findViewById(R.id.fabAddCustomer);

        if (rvDashboard != null) {
            rvDashboard.setHasFixedSize(true);

            ViewCompat.setOnApplyWindowInsetsListener(rvDashboard, (v, insets) -> {
                int bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
                v.setPadding(
                        v.getPaddingLeft(),
                        v.getPaddingTop(),
                        v.getPaddingRight(),
                        bottom + dp(18)
                );
                return insets;
            });
        }
    }

    private void setupActions() {
        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> doLogout());
        }
    }

    private void attachDashboardAdapter() {
        if (rvDashboard == null) return;
        rvDashboard.setAdapter(new DashboardAdapter(buildMenu(), this::handleMenuClick));
    }

    private void setupDashboardGrid() {
        if (rvDashboard == null) return;
        rvDashboard.setLayoutManager(new GridLayoutManager(this, 2));
    }

    private void setupBottomNav() {
        if (bottomNav == null) return;

        if (!isOwnerDevice()) {
            bottomNav.setOnItemSelectedListener(null);
            return;
        }

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_home) {
                clearBackStackInclusive();
                getSupportFragmentManager().executePendingTransactions();
                showDashboardGrid();
                return true;
            }

            if (id == R.id.nav_reports) {
                openFragmentSafe(new ReportsFragment(), BS_REPORTS);
                return true;
            }

            if (id == R.id.nav_chat_ai) {
                startActivity(new Intent(HomeDashboardActivity.this, OwnerChatActivity.class));
                return false;
            }

            if (id == R.id.nav_pos) {
                openPos();
                return true;
            }

            if (id == R.id.nav_settings) {
                openFragmentSafe(new com.example.valdker.ui.settings.SettingsFragment(), BS_SETTINGS);
                return true;
            }

            return false;
        });
    }

    private void configureBottomNavItems() {
        if (bottomNav == null) return;

        if (isOwner()) {
            bottomNav.getMenu().findItem(R.id.nav_pos).setVisible(false);
        } else {
            bottomNav.getMenu().findItem(R.id.nav_pos).setVisible(true);
        }
    }

    private boolean isManager() {
        String role = safeLower(session.getRole());
        return "manager".equals(role);
    }

    private void applyRoleDeviceUI() {
        if (bottomNav == null) return;
        bottomNav.setVisibility(isOwnerDevice() ? View.VISIBLE : View.GONE);
    }

    private void enforceNavigationSecurity() {
        if (isOwnerDevice()) return;

        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            clearBackStackInclusive();
        }

        showDashboardGrid();
    }

    private void clearBackStackInclusive() {
        try {
            getSupportFragmentManager().popBackStack(
                    null,
                    FragmentManager.POP_BACK_STACK_INCLUSIVE
            );
        } catch (Exception e) {
            logw("clearBackStackInclusive error: " + e.getMessage());
        }
    }

    private void renderHeader() {
        setHelloUser();
//        loadShopHeader();

        applySummaryVisibilityByRole();
        loadOpeningCashFromOpenShift();

        if (isOwner()) {
            loadOwnerSummary();
        }
    }

    private void setHelloUser() {
        if (tvHello == null) return;

        String username = session.getUsername();
        if (username == null) username = "";
        username = username.trim();
        if (username.isEmpty()) username = getString(R.string.label_user);

        tvHello.setText(getString(R.string.dashboard_welcome_user, username));
    }

    private void loadShopHeader() {
        String token = session.getToken();
        if (token == null || token.trim().isEmpty()) return;

        ShopRepository.fetchFirstShop(this, token, new ShopRepository.Callback() {
            @Override
            public void onSuccess(@NonNull Shop shop) {
                if (tvBrand != null) {
                    String title = (shop.name != null && !shop.name.trim().isEmpty())
                            ? shop.name.trim()
                            : getString(R.string.app_name);
                    tvBrand.setText(title);
                }

                if (imgLogo != null) {
                    if (shop.logoUrl != null && !shop.logoUrl.trim().isEmpty()) {
                        Glide.with(HomeDashboardActivity.this)
                                .load(shop.logoUrl.trim())
                                .into(imgLogo);
                    } else {
                        imgLogo.setImageResource(R.drawable.ic_store);
                    }
                }
            }

            @Override
            public void onEmpty() {
                // No shop data available.
            }

            @Override
            public void onError(@NonNull String message) {
                logw("loadShopHeader failed: " + message);
            }
        });
    }

    private void loadOwnerSummary() {
        String token = session.getToken();
        if (token == null || token.trim().isEmpty()) return;

        setSummaryLoading();

        String url = ApiConfig.url(session, ENDPOINT_NET_INCOME_TODAY);
        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                res -> {
                    double revenue = res.optDouble("sales", 0.0);
                    double expense = res.optDouble("expense", 0.0);
                    double net = res.optDouble("net_income", 0.0);

                    if (tvRevenueValue != null) tvRevenueValue.setText(usd.format(revenue));
                    if (tvExpenseValue != null) tvExpenseValue.setText(usd.format(expense));
                    if (tvNetValue != null) tvNetValue.setText(usd.format(net));
                },
                err -> {
                    logw("Owner summary error: " + err);
                    setSummaryUnavailable();
                }
        ) {
            @Override
            public java.util.Map<String, String> getHeaders() {
                java.util.Map<String, String> h = new java.util.HashMap<>();
                h.put("Authorization", "Token " + token);
                return h;
            }
        };

        req.setTag(TAG_SUMMARY);
        ApiClient.getInstance(this).add(req);
    }

    private void loadOpeningCashFromOpenShift() {
        String token = session.getToken();
        if (token == null || token.trim().isEmpty()) return;

        if (tvSumHint != null) tvSumHint.setText(R.string.label_opening_cash);
        if (tvSumValue != null) tvSumValue.setText("…");

        String shiftsUrl = ApiConfig.url(session, ENDPOINT_SHIFTS);
        com.android.volley.toolbox.JsonArrayRequest req =
                new com.android.volley.toolbox.JsonArrayRequest(
                        Request.Method.GET,
                        shiftsUrl,
                        null,
                        arr -> {
                            JSONObject openShift = null;
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject o = arr.optJSONObject(i);
                                if (o == null) continue;
                                String st = o.optString("status", "");
                                if ("OPEN".equalsIgnoreCase(st)) {
                                    openShift = o;
                                    break;
                                }
                            }

                            if (openShift == null) {
                                if (tvSumValue != null) tvSumValue.setText("$ 0.00");
                                return;
                            }

                            String openingStr = openShift.optString("opening_cash", "0.00");
                            double opening = 0.0;
                            try {
                                opening = Double.parseDouble(openingStr);
                            } catch (Exception ignored) {
                            }

                            if (tvSumValue != null) tvSumValue.setText(usd.format(opening));
                        },
                        err -> {
                            logw("shifts error: " + err);
                            if (tvSumValue != null) tvSumValue.setText("—");
                        }
                ) {
                    @Override
                    public java.util.Map<String, String> getHeaders() {
                        java.util.Map<String, String> h = new java.util.HashMap<>();
                        h.put("Authorization", "Token " + token);
                        return h;
                    }
                };

        req.setTag(TAG_SHIFTS);
        ApiClient.getInstance(this).add(req);
    }

    private void loadTodayNetIncome() {
        String token = session.getToken();
        if (token == null || token.trim().isEmpty()) {
            logw("No token. Cannot load net income.");
            return;
        }

        setSummaryLoading();

        String netIncomeUrl = ApiConfig.url(session, ENDPOINT_NET_INCOME_TODAY);
        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.GET,
                netIncomeUrl,
                null,
                (JSONObject res) -> {
                    logd("net-income response: " + res);

                    double sales = res.optDouble("sales", 0.0);
                    double expense = res.optDouble("expense", 0.0);
                    double net = res.optDouble("net_income", 0.0);

                    if (tvRevenueValue != null) tvRevenueValue.setText(usd.format(sales));
                    if (tvExpenseValue != null) tvExpenseValue.setText(usd.format(expense));
                    if (tvNetValue != null) tvNetValue.setText(usd.format(net));
                },
                err -> {
                    logw("net-income error: " + err);
                    setSummaryUnavailable();
                    Toast.makeText(
                            this,
                            getString(R.string.msg_owner_summary_not_accessible),
                            Toast.LENGTH_SHORT
                    ).show();
                }
        ) {
            @Override
            public java.util.Map<String, String> getHeaders() {
                java.util.Map<String, String> h = new java.util.HashMap<>();
                h.put("Authorization", "Token " + token);
                return h;
            }
        };

        req.setTag(TAG_SUMMARY);
        ApiClient.getInstance(this).add(req);
    }

    private void setSummaryLoading() {
        if (layoutOwnerSummary != null && layoutOwnerSummary.getVisibility() == View.VISIBLE) {
            if (tvRevenueValue != null) tvRevenueValue.setText("…");
            if (tvExpenseValue != null) tvExpenseValue.setText("…");
            if (tvNetValue != null) tvNetValue.setText("…");
        }
    }

    private void applySummaryVisibilityByRole() {
        if (isManager()) {
            if (layoutOwnerSummary != null) {
                layoutOwnerSummary.setVisibility(View.GONE);
            }
            if (layoutManagerSummary != null) {
                layoutManagerSummary.setVisibility(View.VISIBLE);
            }

            if (tvSumHint != null) {
                tvSumHint.setText(R.string.label_opening_cash);
            }
        } else {
            if (layoutOwnerSummary != null) {
                layoutOwnerSummary.setVisibility(View.VISIBLE);
            }
            if (layoutManagerSummary != null) {
                layoutManagerSummary.setVisibility(View.GONE);
            }
        }
    }

    private void setSummaryUnavailable() {
        if (layoutOwnerSummary != null && layoutOwnerSummary.getVisibility() == View.VISIBLE) {
            if (tvRevenueValue != null) tvRevenueValue.setText("—");
            if (tvExpenseValue != null) tvExpenseValue.setText("—");
            if (tvNetValue != null) tvNetValue.setText("—");
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(ShopEvents.ACTION_SHOP_UPDATED);
        ContextCompat.registerReceiver(
                this,
                shopUpdatedReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
        );
    }

    @Override
    protected void onStop() {
        super.onStop();

        ApiClient.getInstance(this).cancelAll(TAG_SUMMARY);
        ApiClient.getInstance(this).cancelAll(TAG_SHIFTS);
        ApiClient.getInstance(this).cancelAll(TAG_HEADER);

        try {
            unregisterReceiver(shopUpdatedReceiver);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        renderHeader();
        applyRoleDeviceUI();
        configureBottomNavItems();

        if (hasOpenedFragment()) {
            showFragmentContainer();
        } else {
            showDashboardGrid();
        }
    }

    private void handleMenuClick(@NonNull DashboardItem item) {
        logd("Menu clicked: " + item.title + " (id=" + item.id + ")");

        if (item.id == DashboardItem.ID_REPORTS && !session.canViewReports()) {
            Toast.makeText(this, getString(R.string.msg_permission_denied), Toast.LENGTH_SHORT).show();
            return;
        }

        if (item.id == DashboardItem.ID_SETTINGS && !session.canManageSettings()) {
            Toast.makeText(this, getString(R.string.msg_permission_denied), Toast.LENGTH_SHORT).show();
            return;
        }

        switch (item.id) {
            case DashboardItem.ID_POS:
                openPos();
                break;

            case DashboardItem.ID_ORDERS:
                openOrdersFragment();
                break;

            case DashboardItem.ID_CUSTOMERS:
                openFragmentSafe(new com.example.valdker.ui.customers.CustomersFragment(), BS_CUSTOMERS);
                break;

            case DashboardItem.ID_PRODUCTS:
                openFragmentSafe(new com.example.valdker.ui.ProductsManageFragment(), BS_PRODUCTS);
                break;

            case DashboardItem.ID_SUPPLIERS:
                openFragmentSafe(new com.example.valdker.ui.suppliers.SuppliersFragment(), BS_SUPPLIERS);
                break;

            case DashboardItem.ID_PURCHASES:
                openFragmentSafe(new com.example.valdker.ui.purchases.PurchasesFragment(), BS_PURCHASES);
                break;

            case DashboardItem.ID_CATEGORIES:
                openFragmentSafe(new com.example.valdker.ui.categories.CategoriesFragment(), BS_CATEGORIES);
                break;

            case DashboardItem.ID_UNITS:
                openFragmentSafe(new com.example.valdker.ui.units.UnitsFragment(), BS_UNITS);
                break;

            case DashboardItem.ID_SETTINGS:
                openFragmentSafe(new com.example.valdker.ui.settings.SettingsFragment(), BS_SETTINGS);
                break;

            case DashboardItem.ID_BANK_ACCOUNTS:
                startActivity(new Intent(this, BankAccountActivity.class));
                break;

            case DashboardItem.ID_EXPENSE:
                openFragmentSafe(new ExpensesFragment(), BS_EXPENSES);
                break;

            case DashboardItem.ID_REPORTS:
                openFragmentSafe(new ReportsFragment(), BS_REPORTS);
                break;

            case DashboardItem.ID_PRODUCT_RETURNS:
                openFragmentSafe(
                        new com.example.valdker.ui.productreturns.ProductReturnsFragment(),
                        BS_PRODUCT_RETURNS
                );
                break;

            case DashboardItem.ID_INVENTORY_COUNTS:
                openFragmentSafe(new InventoryCountsFragment(), BS_INVENTORY_COUNTS);
                break;

            case DashboardItem.ID_STOCK_ADJUSTMENTS:
                openFragmentSafe(
                        new com.example.valdker.ui.stockadjustments.StockAdjustmentsFragment(),
                        BS_STOCK_ADJUSTMENTS
                );
                break;

            case DashboardItem.ID_STOCK_MOVEMENTS:
                openFragmentSafe(
                        new com.example.valdker.ui.stockmovements.StockMovementsFragment(),
                        BS_STOCK_MOVEMENTS
                );
                break;
        }
    }

    private void openPos() {
        startActivity(new Intent(this, MainActivity.class));
    }

    private void openPosAndFinish() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void openOrdersFragment() {
        openFragmentSafe(new OrdersFragment(), BS_ORDERS);
    }

    private void openFragmentSafe(@NonNull Fragment fragment, @NonNull String backstackTag) {
        if (fragmentContainer == null) {
            Toast.makeText(this, getString(R.string.msg_ui_container_missing), Toast.LENGTH_SHORT).show();
            logw("Cannot open fragment. fragmentContainer is null.");
            return;
        }

        Fragment current = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
        if (current != null && current.getClass().equals(fragment.getClass())) {
            showFragmentContainer();
            return;
        }

        showFragmentContainer();

        getSupportFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.fragmentContainer, fragment)
                .addToBackStack(backstackTag)
                .commit();
    }

    private void showFragmentContainer() {
        updateHeaderVisibility(false);
        applyFragmentStatusBarMode();

        if (rvDashboard != null) {
            rvDashboard.setVisibility(View.GONE);
        }

        if (fragmentContainer != null) {
            fragmentContainer.setVisibility(View.VISIBLE);
        }

        if (bottomNav != null && isOwnerDevice()) {
            bottomNav.setVisibility(View.VISIBLE);
        }
    }

    private void showDashboardGrid() {
        updateHeaderVisibility(true);
        applyDashboardStatusBarMode();

        if (fragmentContainer != null) {
            fragmentContainer.setVisibility(View.GONE);
        }

        if (rvDashboard != null) {
            rvDashboard.setVisibility(View.VISIBLE);
        }

        if (bottomNav != null) {
            bottomNav.setVisibility(isOwnerDevice() ? View.VISIBLE : View.GONE);
        }
    }

    private void doLogout() {
        SessionManager sm = new SessionManager(this);
        sm.clearAuth();
        sm.clearShift();

        ApiClient.getInstance(this).cancelAll(TAG_SUMMARY);
        ApiClient.getInstance(this).cancelAll(TAG_SHIFTS);
        ApiClient.getInstance(this).cancelAll(TAG_HEADER);
        ApiClient.getInstance(this).cancelAll("ShopRepository");
        ApiClient.getInstance(this).cancelAll("ApiClient");

        Intent i = new Intent(this, LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private void goToLogin() {
        Intent i = new Intent(this, LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private void applyHeaderInsets() {
        if (headerContent == null) {
            logw("headerContent missing. Add android:id=\"@+id/headerContent\" in header inner layout.");
            return;
        }

        final int baseLeft = headerContent.getPaddingLeft();
        final int baseRight = headerContent.getPaddingRight();
        final int baseBottom = headerContent.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(headerContent, (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            v.setPadding(baseLeft, top, baseRight, baseBottom);
            return insets;
        });

        ViewCompat.requestApplyInsets(headerContent);
    }

    private void applyFabInsets() {
        if (fabAddCustomer == null) {
            logw("fabAddCustomer missing. Add FAB id @+id/fabAddCustomer in layout.");
            return;
        }

        final int baseMargin = dp(16);

        final View parent = (View) fabAddCustomer.getParent();
        final int parentPadBottom = parent != null ? parent.getPaddingBottom() : 0;
        final int parentPadRight = parent != null ? parent.getPaddingRight() : 0;

        ViewCompat.setOnApplyWindowInsetsListener(fabAddCustomer, (v, insets) -> {
            int bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            int rightInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).right;

            ViewGroup.LayoutParams p = v.getLayoutParams();
            if (p instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) p;
                lp.bottomMargin = baseMargin + parentPadBottom + bottomInset;
                lp.rightMargin = baseMargin + parentPadRight + rightInset;
                v.setLayoutParams(lp);
            }
            return insets;
        });

        ViewCompat.requestApplyInsets(fabAddCustomer);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private boolean isOwner() {
        return session.isShopOwner()
                || session.isPlatformAdmin()
                || session.isPlatformSuperuser();
    }

    private boolean isCashier() {
        String role = safeLower(session.getRole());
        return "cashier".equals(role) || session.isShopCashier();
    }

    private boolean isOwnerDevice() {
        return isOwner();
    }

    private String safeLower(@Nullable String s) {
        if (s == null) return "";
        return s.trim().toLowerCase(Locale.US);
    }

    private boolean hasOpenedFragment() {
        return getSupportFragmentManager().getBackStackEntryCount() > 0;
    }

    private void updateHeaderVisibility(boolean showHeader) {
        if (header != null) {
            header.setVisibility(showHeader ? View.VISIBLE : View.GONE);
        }

        if (contentHost != null) {
            ViewGroup.MarginLayoutParams lp =
                    (ViewGroup.MarginLayoutParams) contentHost.getLayoutParams();

            if (lp != null) {
                lp.topMargin = 0;
                contentHost.setLayoutParams(lp);
            }
        }
    }

    private List<DashboardItem> buildMenu() {
        boolean owner = isOwner();
        boolean manager = isManager();

        List<DashboardItem> out = new ArrayList<>();

        if (owner) {
            out.add(new DashboardItem(DashboardItem.ID_REPORTS, getString(R.string.menu_reports), getString(R.string.menu_reports_desc), R.drawable.ic_report));
            out.add(new DashboardItem(DashboardItem.ID_STOCK_MOVEMENTS, getString(R.string.menu_stock_movements), getString(R.string.menu_stock_movements_desc), R.drawable.ic_stockmovement));
            out.add(new DashboardItem(DashboardItem.ID_INVENTORY_COUNTS, getString(R.string.menu_inventory_counts), getString(R.string.menu_inventory_counts_desc), R.drawable.ic_report));
            out.add(new DashboardItem(DashboardItem.ID_SETTINGS, getString(R.string.menu_settings), getString(R.string.menu_settings_desc), R.drawable.ic_settings));
            out.add(new DashboardItem(DashboardItem.ID_BANK_ACCOUNTS, getString(R.string.menu_bank_accounts), getString(R.string.menu_bank_accounts_desc), R.drawable.ic_bank));
            out.add(new DashboardItem(DashboardItem.ID_ORDERS, getString(R.string.menu_orders), getString(R.string.menu_orders_desc), R.drawable.ic_receipt));
            out.add(new DashboardItem(DashboardItem.ID_EXPENSE, getString(R.string.menu_expenses), getString(R.string.menu_expenses_desc), R.drawable.ic_expense));
            out.add(new DashboardItem(DashboardItem.ID_CUSTOMERS, getString(R.string.menu_customers), getString(R.string.menu_customers_desc), R.drawable.ic_people));
            out.add(new DashboardItem(DashboardItem.ID_SUPPLIERS, getString(R.string.menu_suppliers), getString(R.string.menu_suppliers_desc), R.drawable.ic_store));
            out.add(new DashboardItem(DashboardItem.ID_PURCHASES, getString(R.string.menu_purchases), getString(R.string.menu_purchases_desc), R.drawable.ic_purchase));
            out.add(new DashboardItem(DashboardItem.ID_PRODUCTS, getString(R.string.menu_products), getString(R.string.menu_products_desc), R.drawable.ic_box));
            out.add(new DashboardItem(DashboardItem.ID_CATEGORIES, getString(R.string.menu_categories), getString(R.string.menu_categories_desc), R.drawable.ic_categories));
            out.add(new DashboardItem(DashboardItem.ID_UNITS, getString(R.string.menu_units), getString(R.string.menu_units_desc), R.drawable.ic_units));
            out.add(new DashboardItem(DashboardItem.ID_PRODUCT_RETURNS, getString(R.string.menu_product_returns), getString(R.string.menu_product_returns_desc), R.drawable.ic_return));
            out.add(new DashboardItem(DashboardItem.ID_STOCK_ADJUSTMENTS, getString(R.string.menu_stock_adjustments), getString(R.string.menu_stock_adjustments_desc), R.drawable.ic_report));
            return out;
        }

        if (manager) {
            out.add(new DashboardItem(DashboardItem.ID_CUSTOMERS, getString(R.string.menu_customers), getString(R.string.menu_customers_desc), R.drawable.ic_people));
            out.add(new DashboardItem(DashboardItem.ID_SUPPLIERS, getString(R.string.menu_suppliers), getString(R.string.menu_suppliers_desc), R.drawable.ic_store));
            out.add(new DashboardItem(DashboardItem.ID_PURCHASES, getString(R.string.menu_purchases), getString(R.string.menu_purchases_desc), R.drawable.ic_purchase));
            out.add(new DashboardItem(DashboardItem.ID_PRODUCTS, getString(R.string.menu_products), getString(R.string.menu_products_desc), R.drawable.ic_box));
            out.add(new DashboardItem(DashboardItem.ID_CATEGORIES, getString(R.string.menu_categories), getString(R.string.menu_categories_desc), R.drawable.ic_categories));
            out.add(new DashboardItem(DashboardItem.ID_UNITS, getString(R.string.menu_units), getString(R.string.menu_units_desc), R.drawable.ic_units));
            out.add(new DashboardItem(DashboardItem.ID_POS, getString(R.string.menu_pos), getString(R.string.menu_pos_desc), R.drawable.ic_pos));
            out.add(new DashboardItem(DashboardItem.ID_EXPENSE, getString(R.string.menu_expenses), getString(R.string.menu_expenses_desc), R.drawable.ic_expense));
            out.add(new DashboardItem(DashboardItem.ID_ORDERS, getString(R.string.menu_orders), getString(R.string.menu_orders_desc), R.drawable.ic_receipt));
            out.add(new DashboardItem(DashboardItem.ID_PRODUCT_RETURNS, getString(R.string.menu_product_returns), getString(R.string.menu_product_returns_desc), R.drawable.ic_return));
            out.add(new DashboardItem(DashboardItem.ID_INVENTORY_COUNTS, getString(R.string.menu_inventory_counts), getString(R.string.menu_inventory_counts_desc), R.drawable.ic_report));
            out.add(new DashboardItem(DashboardItem.ID_STOCK_ADJUSTMENTS, getString(R.string.menu_stock_adjustments), getString(R.string.menu_stock_adjustments_desc), R.drawable.ic_report));
            out.add(new DashboardItem(DashboardItem.ID_STOCK_MOVEMENTS, getString(R.string.menu_stock_movements), getString(R.string.menu_stock_movements_desc), R.drawable.ic_stockmovement));

            if (session.canViewReports()) {
                out.add(new DashboardItem(DashboardItem.ID_REPORTS, getString(R.string.menu_reports), getString(R.string.menu_reports_desc), R.drawable.ic_report));
            }

            return out;
        }

        out.add(new DashboardItem(DashboardItem.ID_POS, getString(R.string.menu_pos), getString(R.string.menu_pos_desc), R.drawable.ic_pos));
        out.add(new DashboardItem(DashboardItem.ID_ORDERS, getString(R.string.menu_orders), getString(R.string.menu_orders_desc), R.drawable.ic_receipt));
        out.add(new DashboardItem(DashboardItem.ID_CUSTOMERS, getString(R.string.menu_customers), getString(R.string.menu_customers_desc), R.drawable.ic_people));
        return out;
    }

    private static void logd(@NonNull String msg) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, msg);
        }
    }

    private static void logw(@NonNull String msg) {
        Log.w(TAG, msg);
    }
}