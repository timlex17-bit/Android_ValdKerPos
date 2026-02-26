package com.example.valdker.ui;

import android.os.Bundle;
import android.util.Log;
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

// ✅ OFFLINE ROOM
import com.example.valdker.offline.db.DbProvider;
import com.example.valdker.offline.db.entities.ProductEntity;

import java.util.ArrayList;
import java.util.List;

public class ProductsFragment extends Fragment {

    private static final String TAG = "PRODUCTS_POS";

    private String currentCategoryId = "all";

    private SwipeRefreshLayout swipe;
    private RecyclerView rv;
    private ProgressBar progress;
    private TextView tvEmpty;

    private FloatingActionButton fabAddProduct;

    private final List<Product> items = new ArrayList<>();
    private ProductAdapter adapter;

    private SessionManager session;
    private ProductRepository repo;

    public ProductsFragment() {
        super(R.layout.fragment_products);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Log.i(TAG, "onViewCreated()");

        session = new SessionManager(requireContext());
        repo = new ProductRepository(requireContext());

        swipe = view.findViewById(R.id.swipeRefresh);
        rv = view.findViewById(R.id.rvProducts);
        progress = view.findViewById(R.id.progress);
        tvEmpty = view.findViewById(R.id.tvEmpty);

        fabAddProduct = view.findViewById(R.id.fabAddProduct);

        GridLayoutManager glm = new GridLayoutManager(requireContext(), 3);
        rv.setLayoutManager(glm);

        adapter = new ProductAdapter(items, new ProductAdapter.Listener() {
            @Override
            public void onAdd(Product p) {
                CartManager cart = CartManager.getInstance(requireContext());
                cart.add(p, 1);
                safeToast("Added to cart (" + cart.getTotalQty() + ")");
            }

            @Override
            public void onClick(Product p) {
                safeToast(p.name);
            }
        });

        rv.setAdapter(adapter);

        swipe.setOnRefreshListener(() -> loadProducts(true));

        applyListAndFabInsets();

        debugRoomCount("onViewCreated");

        // ✅ Offline-first: tampilkan cache dulu (kalau ada)
        loadProductsFromRoom(false);

        // ✅ lalu online fetch untuk update & cache
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

            int left = rv.getPaddingLeft();
            int top = rv.getPaddingTop();
            int right = rv.getPaddingRight();
            rv.setPadding(left, top, right, baseListBottom + navBottom);

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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    public void setCategoryFilter(@Nullable String categoryId) {
        currentCategoryId = (categoryId == null || categoryId.trim().isEmpty())
                ? "all"
                : categoryId.trim();

        Log.i(TAG, "setCategoryFilter(): " + currentCategoryId);

        loadProductsFromRoom(true);
        loadProducts(false);
    }

    private void loadProducts(boolean fromSwipe) {
        if (!isAdded() || getView() == null) return;

        String token = session.getToken();
        if (token == null || token.trim().isEmpty()) {
            safeToast("Token empty. Please login again.");
            showEmpty("Token empty");
            stopRefreshing();
            return;
        }

        if (!fromSwipe && swipe != null && !swipe.isRefreshing()) showLoading();

        final String cat = currentCategoryId;

        repo.fetchProducts(token, cat, new ProductRepository.Callback() {
            @Override
            public void onSuccess(@NonNull List<Product> products) {
                if (!isAdded() || getView() == null) return;

                Log.i(TAG, "fetchProducts SUCCESS: cat=" + cat + " count=" + products.size());

                items.clear();
                items.addAll(products);
                adapter.notifyDataSetChanged();

                if (items.isEmpty()) showEmpty("No products");
                else showList();

                // ✅ cache (TERMASUK GAMBAR)
                cacheProductsToRoom(products);

                stopRefreshing();
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                if (!isAdded() || getView() == null) return;

                Log.e(TAG, "fetchProducts ERROR: cat=" + cat + " " + statusCode + " / " + message);

                debugRoomCount("onError(" + statusCode + ")");

                // ✅ fallback offline
                loadProductsFromRoom(true);

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

    // =========================================================
    // OFFLINE: Load from Room
    // =========================================================
    private void loadProductsFromRoom(boolean showToastIfUsingCache) {
        if (!isAdded()) return;

        final String cat = currentCategoryId;

        DbProvider.executor().execute(() -> {
            try {
                int count = DbProvider.get(requireContext()).productDao().countAll();
                Log.i(TAG, "Room products count=" + count);

                List<ProductEntity> cached = DbProvider.get(requireContext()).productDao().getAllActive();
                Log.i(TAG, "Room getAllActive size=" + (cached == null ? 0 : cached.size()));

                // ✅ filter category by categoryId (Product model pakai categoryId)
                if (cached != null && !cached.isEmpty()
                        && cat != null && !"all".equalsIgnoreCase(cat)) {
                    List<ProductEntity> filtered = new ArrayList<>();
                    for (ProductEntity e : cached) {
                        if (e != null && e.categoryServerId != null && e.categoryServerId.equals(cat)) {
                            filtered.add(e);
                        }
                    }
                    cached = filtered;
                }

                final List<Product> mapped = mapEntitiesToProducts(cached);

                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    if (!isAdded() || getView() == null) return;

                    if (mapped != null && !mapped.isEmpty()) {
                        items.clear();
                        items.addAll(mapped);
                        adapter.notifyDataSetChanged();
                        showList();

                        if (showToastIfUsingCache) {
                            safeToast("Offline mode: cached products (" + mapped.size() + ")");
                        }
                    } else {
                        showEmpty("No cached products (Room kosong)");
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "loadProductsFromRoom error", e);
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> showEmpty("Offline cache error"));
            }
        });
    }

    // =========================================================
    // OFFLINE: Cache to Room (INCLUDE IMAGE)
    // =========================================================
    private void cacheProductsToRoom(@NonNull List<Product> products) {
        if (!isAdded()) return;

        DbProvider.executor().execute(() -> {
            try {
                List<ProductEntity> entities = new ArrayList<>();

                for (Product p : products) {
                    ProductEntity e = mapProductToEntityStrict(p);
                    if (e != null) entities.add(e);
                }

                if (!entities.isEmpty()) {
                    DbProvider.get(requireContext()).productDao().upsertAll(entities);
                    int count = DbProvider.get(requireContext()).productDao().countAll();
                    Log.i(TAG, "Cached to Room: " + entities.size() + " | total now=" + count);
                } else {
                    Log.w(TAG, "cacheProductsToRoom: entities EMPTY (mapping gagal?)");
                }
            } catch (Exception ex) {
                Log.e(TAG, "cacheProductsToRoom error", ex);
            }
        });
    }

    @Nullable
    private ProductEntity mapProductToEntityStrict(@NonNull Product p) {
        try {
            if (p.id == null) return null;

            String sid = String.valueOf(p.id).trim();
            if (sid.isEmpty() || "null".equalsIgnoreCase(sid)) return null;

            ProductEntity e = new ProductEntity();
            e.serverId = sid;

            e.name = p.name;
            e.barcode = p.barcode;
            e.sku = p.sku;
            e.price = p.price;
            e.stock = p.stock;

            // ✅ image: pilih yang ada isinya (imageUrl atau image_url)
            String img = (p.imageUrl != null && !p.imageUrl.trim().isEmpty()) ? p.imageUrl : null;
            if (img == null || "null".equalsIgnoreCase(img)) {
                img = (p.image_url != null && !p.image_url.trim().isEmpty()) ? p.image_url : null;
            }
            e.imageUrl = img;

            // ✅ category cache untuk filter offline
            e.categoryServerId = p.categoryId;

            e.isActive = true;
            return e;

        } catch (Exception ex) {
            return null;
        }
    }

    // =========================================================
    // Mapping Entity -> Model Product (untuk adapter existing)
    // =========================================================
    @NonNull
    private List<Product> mapEntitiesToProducts(@Nullable List<ProductEntity> cached) {
        List<Product> out = new ArrayList<>();
        if (cached == null) return out;

        for (ProductEntity e : cached) {
            if (e == null) continue;

            Product p = new Product();

            p.id = e.serverId;
            p.name = e.name;

            p.barcode = e.barcode;
            p.sku = e.sku;
            p.price = e.price;
            p.stock = e.stock;

            // ✅ image untuk ProductAdapter (dia cari imageUrl & image_url)
            p.imageUrl = e.imageUrl;
            p.image_url = e.imageUrl;

            // ✅ category (biar konsisten)
            p.categoryId = e.categoryServerId;

            out.add(p);
        }

        return out;
    }

    // =========================================================
    // Debug helper: print Room count
    // =========================================================
    private void debugRoomCount(@NonNull String from) {
        if (!isAdded()) return;

        DbProvider.executor().execute(() -> {
            try {
                int count = DbProvider.get(requireContext()).productDao().countAll();
                Log.i(TAG, "DEBUG RoomCount [" + from + "] = " + count);
            } catch (Exception e) {
                Log.e(TAG, "DEBUG RoomCount error", e);
            }
        });
    }
}