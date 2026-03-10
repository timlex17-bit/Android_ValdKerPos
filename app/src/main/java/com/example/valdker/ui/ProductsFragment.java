package com.example.valdker.ui;

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
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.valdker.R;
import com.example.valdker.SessionManager;
import com.example.valdker.adapters.ProductAdapter;
import com.example.valdker.cart.CartManager;
import com.example.valdker.models.Product;
import com.example.valdker.repositories.ProductRepository;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class ProductsFragment extends Fragment {

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

    public ProductsFragment() {
        super(R.layout.fragment_products);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        session = new SessionManager(requireContext());
        repo = new ProductRepository(requireContext());

        swipe = view.findViewById(R.id.swipeRefresh);
        rv = view.findViewById(R.id.rvProducts);
        progress = view.findViewById(R.id.progress);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        fabAddProduct = view.findViewById(R.id.fabAddProduct);

        if (rv != null) {
            int span = getResources().getInteger(R.integer.product_grid_span);
            rv.setLayoutManager(new GridLayoutManager(requireContext(), span));
            rv.setHasFixedSize(true);
        }

        adapter = new ProductAdapter(items, new ProductAdapter.Listener() {
            @Override
            public void onAdd(Product p) {
                CartManager cart = CartManager.getInstance(requireContext());
                cart.add(p, 1);
                safeToast("Added to cart (" + cart.getTotalQty() + ")");
            }

            @Override
            public void onClick(Product p) {
                safeToast(p.name != null ? p.name : "Product");
            }
        });

        if (rv != null) rv.setAdapter(adapter);

        if (swipe != null) swipe.setOnRefreshListener(() -> loadProducts(true));

        applyListAndFabInsets();
        loadProducts(false);
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

                if (items.isEmpty()) showEmpty("No products");
                else showList();

                stopRefreshing();
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {

                if (!isAdded() || getView() == null) return;

                if (items.isEmpty()) showEmpty("Failed to load products");
                else showList();

                safeToast(message);
                stopRefreshing();
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

    private void showList() {
        if (progress != null) progress.setVisibility(View.GONE);
        if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
        if (rv != null) rv.setVisibility(View.VISIBLE);
    }

    public void onBarcodeScanned(@NonNull String barcode) {

        if (!isAdded()) return;

        String clean = barcode.trim();
        if (clean.isEmpty()) {
            safeToast("Barcode empty");
            return;
        }

        Product found = findProductByBarcodeInList(items, clean);
        if (found != null) {
            addProductToCart(found);
            return;
        }

        found = findProductByBarcodeInList(allProductsCache, clean);
        if (found != null) {
            addProductToCart(found);
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

                if (found != null) addProductToCart(found);
                else safeToast("Product not found: " + barcode);
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {

                if (!isAdded()) return;

                allProductsCacheLoading = false;
                safeToast("Barcode lookup failed: " + message);
            }
        });
    }

    @Nullable
    private Product findProductByBarcodeInList(@Nullable List<Product> list,
                                               @NonNull String barcode) {

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

        } catch (Exception ignored) {
            safeToast("Failed to add item to cart");
        }
    }

    @NonNull
    private String safeTrim(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}