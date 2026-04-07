package com.valdker.pos.ui;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.valdker.pos.R;
import com.valdker.pos.SessionManager;
import com.valdker.pos.adapters.ProductAdapter;
import com.valdker.pos.cart.CartManager;
import com.valdker.pos.models.Product;
import com.valdker.pos.repositories.ProductRepository;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ProductsFragment extends Fragment {

    private static final String ARG_BUSINESS_TYPE = "business_type";
    private static final String ARG_USE_GRID = "use_grid_pos_layout";
    private static final String ARG_SHOW_IMAGES = "show_product_images_in_pos";

    private String currentCategoryId = "all";

    private SwipeRefreshLayout swipe;
    private RecyclerView rv;
    private ProgressBar progress;
    private TextView tvEmpty;
    private FloatingActionButton fabAddProduct;

    private final List<Product> items = new ArrayList<>();
    private final List<Product> allProductsCache = new ArrayList<>();

    private ProductAdapter adapter;

    private SessionManager session;
    private ProductRepository repo;

    private boolean allProductsCacheLoaded = false;
    private boolean allProductsCacheLoading = false;

    private String businessType = "retail";
    private boolean useGridPosLayout = false;
    private boolean showProductImagesInPos = false;

    public ProductsFragment() {
        super(R.layout.fragment_products);
    }

    public static ProductsFragment newInstance(String businessType, boolean useGrid, boolean showImages) {
        ProductsFragment f = new ProductsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_BUSINESS_TYPE, businessType);
        args.putBoolean(ARG_USE_GRID, useGrid);
        args.putBoolean(ARG_SHOW_IMAGES, showImages);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        readBusinessArgs();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        session = new SessionManager(requireContext());
        repo = new ProductRepository(requireContext());

        applyBusinessFallbackFromSessionIfNeeded();

        swipe = view.findViewById(R.id.swipeRefresh);
        rv = view.findViewById(R.id.rvProducts);
        progress = view.findViewById(R.id.progress);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        fabAddProduct = view.findViewById(R.id.fabAddProduct);

        setupRecyclerView();

        adapter = buildAdapter();
        if (rv != null) rv.setAdapter(adapter);

        if (swipe != null) {
            swipe.setOnRefreshListener(() -> {
                if (isRetailOrWorkshop()) {
                    stopRefreshing();
                    if (items.isEmpty()) {
                        showRetailWorkshopEmptyState();
                    } else {
                        showList();
                    }
                } else {
                    loadProducts(false);
                }
            });
        }

        applyListAndFabInsets();
        applyBusinessUiRules();

        if (isRetailOrWorkshop()) {
            clearVisibleProducts();
            showRetailWorkshopEmptyState();
            preloadAllProductsCacheSilently();
        } else {
            loadProducts(false);
        }
    }

    private void readBusinessArgs() {
        Bundle args = getArguments();
        if (args == null) return;

        businessType = safeLower(args.getString(ARG_BUSINESS_TYPE, "retail"));
        useGridPosLayout = args.getBoolean(ARG_USE_GRID, false);
        showProductImagesInPos = args.getBoolean(ARG_SHOW_IMAGES, false);
    }

    private void applyBusinessFallbackFromSessionIfNeeded() {
        if (session == null) return;

        // Session jadi source of truth utama
        businessType = safeLower(session.getBusinessType());
        useGridPosLayout = session.useGridPosLayout();
        showProductImagesInPos = session.showProductImagesInPos();

        // Safety defaults untuk non-restaurant
        if (!"restaurant".equals(businessType)) {
            useGridPosLayout = false;
            showProductImagesInPos = false;
        }

        android.util.Log.i(
                "PRODUCT_UI",
                "Resolved config from session:"
                        + " businessType=" + businessType
                        + " useGrid=" + useGridPosLayout
                        + " showImages=" + showProductImagesInPos
        );
    }

    private boolean isRetailOrWorkshop() {
        return "retail".equals(businessType) || "workshop".equals(businessType);
    }

    private void setupRecyclerView() {
        if (rv == null) return;

        if ("restaurant".equals(businessType) && useGridPosLayout) {
            int span = 2; // test hardcode
            android.util.Log.i("PRODUCT_UI", "Using GRID layout, span=" + span + ", businessType=" + businessType);
            rv.setLayoutManager(new GridLayoutManager(requireContext(), span));
        } else {
            android.util.Log.i("PRODUCT_UI", "Using LINEAR layout, businessType=" + businessType);
            rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        }

        rv.setHasFixedSize(true);
    }

    private ProductAdapter buildAdapter() {
        return new ProductAdapter(
                items,
                new ProductAdapter.Listener() {
                    @Override
                    public void onAdd(Product p) {
                        CartManager cart = CartManager.getInstance(requireContext());
                        cart.add(p, 1);
                        safeToast("Added to cart (" + cart.getTotalQty() + ")");
                    }

                    @Override
                    public void onClick(Product p) {
                        if (isRetailOrWorkshop()) {
                            CartManager cart = CartManager.getInstance(requireContext());
                            cart.add(p, 1);
                            safeToast((p.name != null ? p.name : "Product") + " added to cart");
                        } else {
                            CartManager cart = CartManager.getInstance(requireContext());
                            cart.add(p, 1);
                            safeToast((p.name != null ? p.name : "Product") + " added to cart");
                        }
                    }
                },
                useGridPosLayout,
                showProductImagesInPos,
                businessType
        );
    }

    private void applyBusinessUiRules() {
        if (fabAddProduct != null) {
            fabAddProduct.setVisibility(View.GONE);
        }
    }

    private void applyListAndFabInsets() {
        if (rv == null) return;

        final int baseListBottom = dp(96);
        final int baseFabMargin = dp(16);

        View root = getView();
        if (root == null) root = rv;

        rv.setClipToPadding(false);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            int navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            int navRight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).right;

            rv.setPadding(
                    rv.getPaddingLeft(),
                    rv.getPaddingTop(),
                    rv.getPaddingRight(),
                    baseListBottom + navBottom
            );

            if (fabAddProduct != null) {
                ViewGroup.MarginLayoutParams lp =
                        (ViewGroup.MarginLayoutParams) fabAddProduct.getLayoutParams();

                lp.bottomMargin = baseFabMargin + navBottom;
                lp.rightMargin = baseFabMargin + navRight;
                fabAddProduct.setLayoutParams(lp);
            }

            return insets;
        });

        ViewCompat.requestApplyInsets(root);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    public void setCategoryFilter(@Nullable String categoryId) {
        currentCategoryId =
                (categoryId == null || categoryId.trim().isEmpty())
                        ? "all"
                        : categoryId.trim();

        if (isRetailOrWorkshop()) {
            return;
        }

        loadProducts(false);
    }

    private void loadProducts(boolean fromSwipe) {
        if (!isAdded() || getView() == null) return;

        String token = session.getToken();

        if (token == null || token.trim().isEmpty()) {
            safeToast("Token is missing. Please login again.");
            showEmpty("Token is missing");
            stopRefreshing();
            return;
        }

        if (!fromSwipe && swipe != null && !swipe.isRefreshing()) {
            showLoading();
        }

        repo.fetchProducts(token, currentCategoryId, new ProductRepository.Callback() {

            @Override
            public void onSuccess(@NonNull List<Product> products) {
                if (!isAdded() || getView() == null) return;

                items.clear();
                items.addAll(products);

                if (adapter != null) adapter.notifyDataSetChanged();

                if (items.isEmpty()) {
                    if (isRetailOrWorkshop()) {
                        showRetailWorkshopEmptyState();
                    } else {
                        showEmpty("No products");
                    }
                } else {
                    showList();
                }

                stopRefreshing();
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                if (!isAdded() || getView() == null) return;

                if (items.isEmpty()) {
                    if (isRetailOrWorkshop()) {
                        showRetailWorkshopEmptyState();
                    } else {
                        showEmpty("Failed to load products");
                    }
                } else {
                    showList();
                }

                safeToast(message);
                stopRefreshing();
            }
        });
    }

    private void preloadAllProductsCacheSilently() {
        if (!isRetailOrWorkshop()) return;
        if (allProductsCacheLoaded || allProductsCacheLoading) return;

        String token = session != null ? session.getToken() : null;
        if (token == null || token.trim().isEmpty()) return;

        allProductsCacheLoading = true;

        repo.fetchProducts(token, "all", new ProductRepository.Callback() {
            @Override
            public void onSuccess(@NonNull List<Product> products) {
                allProductsCacheLoading = false;
                allProductsCacheLoaded = true;

                allProductsCache.clear();
                allProductsCache.addAll(products);
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                allProductsCacheLoading = false;
            }
        });
    }

    private void stopRefreshing() {
        if (swipe != null) swipe.setRefreshing(false);
    }

    private void safeToast(@NonNull String msg) {
        if (!isAdded()) return;
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
    }

    private void showLoading() {
        if (progress != null) progress.setVisibility(View.VISIBLE);
        if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
        if (rv != null) rv.setVisibility(View.GONE);
    }

    private void showEmpty(@NonNull String msg) {
        if (progress != null) progress.setVisibility(View.GONE);

        if (tvEmpty != null) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText(msg);
        }

        if (rv != null) rv.setVisibility(View.GONE);
    }

    private void showRetailWorkshopEmptyState() {
        showEmpty("Scan barcode untuk mencari produk\natau gunakan pencarian manual");
    }

    private void showList() {
        if (progress != null) progress.setVisibility(View.GONE);
        if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
        if (rv != null) rv.setVisibility(View.VISIBLE);
    }

    private void clearVisibleProducts() {
        items.clear();
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    public void onBarcodeScanned(@NonNull String barcode) {
        if (!isAdded()) return;

        if (!session.enableBarcodeScan()) {
            safeToast("Barcode scan disabled for this shop.");
            return;
        }

        String clean = barcode.trim();
        if (clean.isEmpty()) {
            safeToast("Barcode empty");
            return;
        }

        Product found = findProductByBarcodeInList(items, clean);
        if (found != null) {
            addProductToCart(found);
            showSingleProductResult(found);
            return;
        }

        found = findProductByBarcodeInList(allProductsCache, clean);
        if (found != null) {
            addProductToCart(found);
            showSingleProductResult(found);
            return;
        }

        if (!allProductsCacheLoaded && !allProductsCacheLoading) {
            loadAllProductsCacheAndAdd(clean);
            return;
        }

        if (allProductsCacheLoading) {
            safeToast("Searching product...");
            return;
        }

        safeToast("Product not found: " + clean);
    }

    private void loadAllProductsCacheAndAdd(@NonNull String barcode) {
        String token = session != null ? session.getToken() : null;

        if (token == null || token.trim().isEmpty()) {
            safeToast("Token empty. Please login again.");
            return;
        }

        allProductsCacheLoading = true;

        repo.fetchProducts(token, "all", new ProductRepository.Callback() {
            @Override
            public void onSuccess(@NonNull List<Product> products) {
                if (!isAdded()) return;

                allProductsCacheLoading = false;
                allProductsCacheLoaded = true;

                allProductsCache.clear();
                allProductsCache.addAll(products);

                Product found = findProductByBarcodeInList(allProductsCache, barcode);

                if (found != null) {
                    addProductToCart(found);
                    showSingleProductResult(found);
                } else {
                    safeToast("Product not found: " + barcode);
                }
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                if (!isAdded()) return;

                allProductsCacheLoading = false;
                safeToast("Barcode lookup failed: " + message);
            }
        });
    }

    private void showSingleProductResult(@NonNull Product product) {
        items.clear();
        items.add(product);

        if (adapter != null) adapter.notifyDataSetChanged();
        showList();
    }

    @Nullable
    private Product findProductByBarcodeInList(@Nullable List<Product> list, @NonNull String barcode) {
        if (list == null || list.isEmpty()) return null;

        for (Product p : list) {
            if (p == null) continue;

            String pBarcode = safeTrim(p.barcode);
            String pSku = safeTrim(p.sku);

            if (barcode.equals(pBarcode) || barcode.equals(pSku)) {
                return p;
            }
        }

        return null;
    }

    private void addProductToCart(@NonNull Product product) {
        try {
            CartManager cart = CartManager.getInstance(requireContext());
            cart.add(product, 1);
            safeToast((product.name != null ? product.name : "Product") + " added to cart");
        } catch (Exception e) {
            safeToast("Failed to add item to cart");
        }
    }

    public void onManualSearch(@NonNull String query) {
        String clean = safeTrim(query);

        if (clean.isEmpty()) {
            clearVisibleProducts();
            if (isRetailOrWorkshop()) {
                showRetailWorkshopEmptyState();
            } else {
                loadProducts(false);
            }
            return;
        }

        if (!allProductsCacheLoaded) {
            preloadAllProductsCacheSilently();
            safeToast("Preparing search data...");
            return;
        }

        List<Product> results = new ArrayList<>();
        String keyword = clean.toLowerCase(Locale.US);

        for (Product p : allProductsCache) {
            if (p == null) continue;

            String name = safeTrim(p.name).toLowerCase(Locale.US);
            String barcode = safeTrim(p.barcode).toLowerCase(Locale.US);
            String sku = safeTrim(p.sku).toLowerCase(Locale.US);

            if (name.contains(keyword) || barcode.contains(keyword) || sku.contains(keyword)) {
                results.add(p);
            }
        }

        items.clear();
        items.addAll(results);

        if (adapter != null) adapter.notifyDataSetChanged();

        if (items.isEmpty()) {
            showEmpty("Produk tidak ditemukan");
        } else {
            showList();
        }
    }

    @NonNull
    private String safeTrim(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    @NonNull
    private String safeLower(@Nullable String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
    }
}