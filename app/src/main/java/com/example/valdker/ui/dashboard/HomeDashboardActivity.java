package com.example.valdker.ui.dashboard;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.bumptech.glide.Glide;
import com.example.valdker.LoginActivity;
import com.example.valdker.MainActivity;
import com.example.valdker.R;
import com.example.valdker.SessionManager;
import com.example.valdker.models.Shop;
import com.example.valdker.network.ApiClient;
import com.example.valdker.repositories.ShopRepository;
import com.example.valdker.shop.ShopEvents;
import com.example.valdker.ui.expenses.ExpensesFragment;
import com.example.valdker.ui.inventorycount.InventoryCountsFragment;
import com.example.valdker.ui.orders.OrdersFragment;
import com.example.valdker.ui.reports.ReportsFragment;
import com.example.valdker.ui.ownerchat.OwnerChatActivity;
import com.google.android.material.button.MaterialButton;

import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HomeDashboardActivity extends AppCompatActivity {

    private static final String TAG = "DASHBOARD";

    private static final String BS_ORDERS = "orders";
    private static final String BS_CUSTOMERS = "customers";
    private static final String BS_PRODUCTS = "products_manage";
    private static final String BS_SUPPLIERS = "suppliers";
    private static final String BS_CATEGORIES = "categories";
    private static final String BS_UNITS = "units";
    private static final String BS_SETTINGS = "settings";

    private static final String NET_INCOME_URL = "https://valdker.onrender.com/api/reports/net-income-today/";

    private SessionManager session;
    private View fabAddCustomer;

    private RecyclerView rvDashboard;
    private View fragmentContainer;

    private MaterialButton btnChat;

    private TextView tvHello;
    private TextView tvBrand;
    private ImageView imgLogo;

    private TextView tvSumValue;
    private TextView tvSumHint;

    private final NumberFormat usd = NumberFormat.getCurrencyInstance(Locale.US);

    private View headerContent;

    private final BroadcastReceiver shopUpdatedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "Shop updated broadcast received -> refreshing header");
            loadShopHeader();
            if (session != null && session.isAdmin()) {
                loadTodayNetIncome();
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        session = new SessionManager(this);

        setContentView(R.layout.activity_home_dashboard);

        if (!session.isLoggedIn()) {
            Log.w(TAG, "Not logged in. Redirecting to Login.");
            goToLogin();
            return;
        }

        // ✅ Cashier should not be here (double safety)
        String role = session.getRole() == null ? "" : session.getRole().trim().toLowerCase();
        if ("cashier".equals(role)) {
            Log.w(TAG, "Cashier detected -> redirect to POS");
            openPosAndFinish();
            return;
        }

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);

        setContentView(R.layout.activity_home_dashboard);

        bindViews();

        applyHeaderInsets();
        applyFabInsets();

        setupDashboardGrid();
        renderHeader();

        if (rvDashboard != null) {
            rvDashboard.setAdapter(new DashboardAdapter(buildMenu(), this::handleMenuClick));
        }

        // ✅ Chat button: ADMIN only
        if (btnChat != null) {
            if (session.isAdmin()) {
                btnChat.setVisibility(View.VISIBLE);
                btnChat.setOnClickListener(v -> startActivity(new Intent(HomeDashboardActivity.this, OwnerChatActivity.class)));
            } else {
                btnChat.setVisibility(View.GONE);
            }
        }
    }

    private void openFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .addToBackStack(null)
                .commit();
    }

    private void bindViews() {
        tvHello = findViewById(R.id.tvHello);
        tvBrand = findViewById(R.id.tvBrand);
        imgLogo = findViewById(R.id.imgLogo);

        tvSumValue = findViewById(R.id.tvSumValue);
        tvSumHint = findViewById(R.id.tvSumHint);

        rvDashboard = findViewById(R.id.rvDashboard);
        ViewCompat.setOnApplyWindowInsetsListener(rvDashboard, (v, insets) -> {
            int bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), bottom + dp(18));
            return insets;
        });

        fragmentContainer = findViewById(R.id.fragmentContainer);

        headerContent = findViewById(R.id.headerContent);

        fabAddCustomer = findViewById(R.id.fabAddCustomer);

        btnChat = findViewById(R.id.btnChat);

        if (rvDashboard != null) {
            rvDashboard.setHasFixedSize(true);
        }

        showDashboardGrid();
    }

    private void applyHeaderInsets() {
        if (headerContent == null) {
            Log.w(TAG, "headerContent missing. Add android:id=\"@+id/headerContent\" in header inner layout.");
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
            Log.w(TAG, "fabAddCustomer missing. Add FAB id @+id/fabAddCustomer in layout.");
            return;
        }

        final int baseMargin = dp(16);

        final View parent = (View) fabAddCustomer.getParent();
        final int parentPadBottom = parent != null ? parent.getPaddingBottom() : 0;
        final int parentPadRight = parent != null ? parent.getPaddingRight() : 0;

        ViewCompat.setOnApplyWindowInsetsListener(fabAddCustomer, (v, insets) -> {
            int bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            int rightInset  = insets.getInsets(WindowInsetsCompat.Type.systemBars()).right;

            ViewGroup.LayoutParams p = v.getLayoutParams();
            if (p instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) p;
                lp.bottomMargin = baseMargin + parentPadBottom + bottomInset;
                lp.rightMargin  = baseMargin + parentPadRight + rightInset;
                v.setLayoutParams(lp);
            }
            return insets;
        });

        ViewCompat.requestApplyInsets(fabAddCustomer);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void setupDashboardGrid() {
        if (rvDashboard == null) return;
        GridLayoutManager glm = new GridLayoutManager(this, 2);
        rvDashboard.setLayoutManager(glm);
    }

    private void renderHeader() {
        setHelloUser();
        loadShopHeader();

        // ✅ Today Net Income: ADMIN only (endpoint locked)
        if (session.isAdmin()) {
            loadTodayNetIncome();
        } else {
            if (tvSumValue != null) tvSumValue.setText("—");
            if (tvSumHint != null) tvSumHint.setText("Admin only");
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(ShopEvents.ACTION_SHOP_UPDATED);
        ContextCompat.registerReceiver(this, shopUpdatedReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onStop() {
        super.onStop();

        ApiClient.getInstance(this).cancelAll(TAG);

        try {
            unregisterReceiver(shopUpdatedReceiver);
        } catch (Exception ignored) {}
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderHeader();
    }

    private void handleMenuClick(@NonNull DashboardItem item) {
        Log.i(TAG, "Menu clicked: " + item.title + " (id=" + item.id + ")");

        // ✅ Guard sensitive modules on Android too (UX safety)
        if (!session.isAdmin()) {
            if (item.id == DashboardItem.ID_REPORTS || item.id == DashboardItem.ID_SETTINGS) {
                Toast.makeText(this, "Admin only", Toast.LENGTH_SHORT).show();
                return;
            }
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

            case DashboardItem.ID_CATEGORIES:
                openFragmentSafe(new com.example.valdker.ui.categories.CategoriesFragment(), BS_CATEGORIES);
                break;

            case DashboardItem.ID_UNITS:
                openFragmentSafe(new com.example.valdker.ui.units.UnitsFragment(), BS_UNITS);
                break;

            case DashboardItem.ID_SETTINGS:
                openFragmentSafe(new com.example.valdker.ui.settings.SettingsFragment(), BS_SETTINGS);
                break;

            case DashboardItem.ID_EXPENSE:
                openFragmentSafe(new ExpensesFragment(), BS_SETTINGS);
                break;

            case DashboardItem.ID_REPORTS:
                openFragmentSafe(new ReportsFragment(), BS_SETTINGS);
                break;

            case DashboardItem.ID_PRODUCT_RETURNS:
                openFragmentSafe(new com.example.valdker.ui.productreturns.ProductReturnsFragment(), "BS_PRODUCT_RETURNS");
                break;

            case DashboardItem.ID_INVENTORY_COUNTS:
                openFragmentSafe(new InventoryCountsFragment(), "BS_INVENTORY_COUNTS");
                break;

            case DashboardItem.ID_STOCK_ADJUSTMENTS:
                openFragmentSafe(new com.example.valdker.ui.stockadjustments.StockAdjustmentsFragment(), "BS_STOCK_ADJUSTMENTS");
                break;

            case DashboardItem.ID_STOCK_MOVEMENTS:
                openFragmentSafe(new com.example.valdker.ui.stockmovements.StockMovementsFragment(), "BS_STOCK_MOVEMENTS");
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
        showFragmentContainer();
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, new OrdersFragment())
                .addToBackStack(BS_ORDERS)
                .commit();
    }

    private void openFragmentSafe(@NonNull androidx.fragment.app.Fragment fragment, @NonNull String backstackTag) {
        if (fragmentContainer == null) {
            Toast.makeText(this, "UI container missing in layout", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Cannot open fragment. fragmentContainer is null.");
            return;
        }

        showFragmentContainer();

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .addToBackStack(backstackTag)
                .commit();
    }

    private void showFragmentContainer() {
        if (rvDashboard != null) rvDashboard.setVisibility(View.GONE);
        if (fragmentContainer != null) fragmentContainer.setVisibility(View.VISIBLE);
    }

    private void showDashboardGrid() {
        if (fragmentContainer != null) fragmentContainer.setVisibility(View.GONE);
        if (rvDashboard != null) rvDashboard.setVisibility(View.VISIBLE);
    }

    @SuppressLint("GestureBackNavigation")
    @Override
    public void onBackPressed() {
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            getSupportFragmentManager().popBackStack();
            showDashboardGrid();
            return;
        }
        super.onBackPressed();
    }

    private void setHelloUser() {
        if (tvHello == null) return;

        String username = session.getUsername();
        if (username == null) username = "";
        username = username.trim();
        if (username.isEmpty()) username = "User";

        String text = String.format(Locale.US, "Hello, %s", username);
        tvHello.setText(text);
    }

    private void loadShopHeader() {
        String token = session.getToken();
        if (token == null || token.trim().isEmpty()) return;

        ShopRepository.fetchFirstShop(this, token, new ShopRepository.Callback() {
            @Override
            public void onSuccess(@NonNull Shop shop) {
                if (tvBrand != null) {
                    String title = (shop.name != null && !shop.name.trim().isEmpty()) ? shop.name.trim() : "ValdKer POS";
                    tvBrand.setText(title);
                }

                if (imgLogo != null) {
                    if (shop.logoUrl != null && !shop.logoUrl.trim().isEmpty()) {
                        Glide.with(HomeDashboardActivity.this).load(shop.logoUrl.trim()).into(imgLogo);
                    } else {
                        imgLogo.setImageResource(R.drawable.ic_store);
                    }
                }
            }

            @Override
            public void onEmpty() {}

            @Override
            public void onError(@NonNull String message) {
                Log.w(TAG, "loadShopHeader failed: " + message);
            }
        });
    }

    private void loadTodayNetIncome() {
        if (tvSumValue == null) return;

        String token = session.getToken();
        if (token == null || token.trim().isEmpty()) return;

        tvSumValue.setText("…");
        if (tvSumHint != null) tvSumHint.setText("Updating from server…");

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.GET,
                NET_INCOME_URL,
                null,
                (JSONObject res) -> {
                    double net = res.optDouble("net_income", 0.0);
                    tvSumValue.setText(usd.format(net));
                    if (tvSumHint != null) tvSumHint.setText("Today Net Income (admin)");
                },
                (err) -> {
                    // If 403, show admin-only message instead of "0"
                    tvSumValue.setText("—");
                    if (tvSumHint != null) tvSumHint.setText("Admin only / no access");
                    Log.w(TAG, "loadTodayNetIncome error: " + err);
                }
        ) {
            @Override
            public java.util.Map<String, String> getHeaders() {
                java.util.Map<String, String> h = new java.util.HashMap<>();
                h.put("Authorization", "Token " + token);
                return h;
            }
        };

        req.setTag(TAG);
        ApiClient.getInstance(this).add(req);
    }

    private List<DashboardItem> buildMenu() {
        List<DashboardItem> out = new ArrayList<>();

        out.add(new DashboardItem(DashboardItem.ID_CUSTOMERS, "CUSTOMERS", "Jere no haree dadus kliente", R.drawable.ic_people));
        out.add(new DashboardItem(DashboardItem.ID_SUPPLIERS, "SUPPLIERS", "Lista no jere fornesdór", R.drawable.ic_store));
        out.add(new DashboardItem(DashboardItem.ID_PRODUCTS, "PRODUCTS", "Stok, presu no detallu produtu", R.drawable.ic_box));
        out.add(new DashboardItem(DashboardItem.ID_CATEGORIES, "CATEGORIES", "Jere kategoria produtu", R.drawable.ic_categories));
        out.add(new DashboardItem(DashboardItem.ID_UNITS, "UNITS", "Jere unidade produtu", R.drawable.ic_box));

        out.add(new DashboardItem(DashboardItem.ID_POS, "POS", "Halo tranzasaun fa'an agora", R.drawable.ic_pos));

        out.add(new DashboardItem(DashboardItem.ID_EXPENSE, "EXPENSE", "Rejistu gastu negósiu", R.drawable.ic_expense));
        out.add(new DashboardItem(DashboardItem.ID_ORDERS, "ALL ORDERS", "Haree istória tranzasaun", R.drawable.ic_receipt));

        // ✅ ADMIN-only modules: REPORT + SETTINGS
        if (session.isAdmin()) {
            out.add(new DashboardItem(DashboardItem.ID_REPORTS, "REPORT", "Analiza rendimentu negósiu", R.drawable.ic_report));
            out.add(new DashboardItem(DashboardItem.ID_SETTINGS, "SETTINGS", "Atu regula aplikasaun", R.drawable.ic_settings));
        }

        out.add(new DashboardItem(DashboardItem.ID_PRODUCT_RETURNS, "PRODUCT RETURNS", "Return produtu (coming soon)", R.drawable.ic_return));
        out.add(new DashboardItem(DashboardItem.ID_INVENTORY_COUNTS, "INVENTORY COUNTS", "Stock Opname / Kontajen stok", R.drawable.ic_report));
        out.add(new DashboardItem(DashboardItem.ID_STOCK_ADJUSTMENTS, "STOCK ADJUSTMENTS", "Ajusta stok manual", R.drawable.ic_report));
        out.add(new DashboardItem(DashboardItem.ID_STOCK_MOVEMENTS, "STOCK MOVEMENTS", "História movimentu stok", R.drawable.ic_stockmovement));

        return out;
    }

    private void goToLogin() {
        Intent i = new Intent(this, LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}