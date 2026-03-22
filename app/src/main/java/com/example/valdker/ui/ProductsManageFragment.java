package com.example.valdker.ui;

import android.os.Bundle;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.valdker.R;
import com.example.valdker.SessionManager;
import com.example.valdker.adapters.ProductManageAdapter;
import com.example.valdker.base.BaseFragment;
import com.example.valdker.models.Product;
import com.example.valdker.repositories.ProductRepository;
import com.example.valdker.ui.customers.ConfirmDeleteDialog;
import com.example.valdker.utils.InsetsHelper;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ProductsManageFragment extends BaseFragment {

    private static final String TAG = "PRODUCTS_MANAGE";
    private static final String TAG_ADD_PRODUCT = "add_product";
    private static final String TAG_EDIT_PRODUCT = "edit_product";
    private static final long CLICK_GUARD_MS = 700L;

    private SwipeRefreshLayout swipe;
    private RecyclerView rv;
    private ProgressBar progress;
    private TextView tvEmpty;
    private TextView tvTitle;
    private FloatingActionButton fabAdd;
    private EditText etSearchManage;
    private ImageView btnBack;
    private ImageView ivHeaderAction;
    private View rootManage;

    private final List<Product> items = new ArrayList<>();
    private final List<Product> allItems = new ArrayList<>();
    private ProductManageAdapter adapter;

    private SessionManager session;
    private ProductRepository repo;

    private boolean isLoading = false;
    private boolean isDialogOpening = false;
    private boolean isDeleteRunning = false;

    private long lastFabClickTime = 0L;
    private long lastRowActionTime = 0L;
    private String currentQuery = "";

    public ProductsManageFragment() {
        super(R.layout.fragment_products_manage);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        applyTopInset(view.findViewById(R.id.topBar));

        session = new SessionManager(requireContext());
        repo = new ProductRepository(requireContext());

        swipe = view.findViewById(R.id.swipeRefreshManage);
        rv = view.findViewById(R.id.rvProductsManage);
        progress = view.findViewById(R.id.progressManage);
        tvEmpty = view.findViewById(R.id.tvEmptyManage);
        fabAdd = view.findViewById(R.id.fabAddProduct);
        rootManage = view.findViewById(R.id.rootManage);

        tvTitle = view.findViewById(R.id.tvTitleManage);
        etSearchManage = view.findViewById(R.id.etSearchManage);
        btnBack = view.findViewById(R.id.btnBack);
        ivHeaderAction = view.findViewById(R.id.ivHeaderAction);

        if (tvTitle != null) {
            tvTitle.setText("Manage Products");
        }

        if (swipe == null) Log.w(TAG, "swipeRefreshManage not found.");
        if (rv == null) Log.w(TAG, "rvProductsManage not found.");
        if (progress == null) Log.w(TAG, "progressManage not found.");
        if (tvEmpty == null) Log.w(TAG, "tvEmptyManage not found.");
        if (fabAdd == null) Log.w(TAG, "fabAddProduct not found.");

        setupRecycler();
        setupAdapter();
        setupActions();
        applyInsets();

        loadProducts(false);
    }

    private void setupRecycler() {
        if (rv == null) return;
        rv.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        rv.setHasFixedSize(false);
        rv.setClipToPadding(false);
    }

    private void setupAdapter() {
        adapter = new ProductManageAdapter(items, new ProductManageAdapter.Listener() {
            @Override
            public void onEdit(@NonNull Product p) {
                if (!canRunRowAction()) return;
                openEditProductDialogSafely(p);
            }

            @Override
            public void onDelete(@NonNull Product p) {
                if (!canRunRowAction()) return;
                confirmDeleteSafely(p);
            }
        });

        if (rv != null) {
            rv.setAdapter(adapter);
        }
    }

    private void setupActions() {
        if (swipe != null) {
            swipe.setOnRefreshListener(() -> {
                if (isLoading) {
                    stopRefreshing();
                    return;
                }
                loadProducts(true);
            });
        }

        if (fabAdd != null) {
            fabAdd.setOnClickListener(v -> openAddProductDialogSafely());

            fabAdd.post(() -> {
                if (fabAdd == null) return;
                fabAdd.bringToFront();
                fabAdd.setElevation(100f);
                fabAdd.setTranslationZ(100f);
            });
        }

        if (btnBack != null) {
            btnBack.setOnClickListener(v ->
                    requireActivity().getOnBackPressedDispatcher().onBackPressed()
            );
        }

        if (ivHeaderAction != null) {
            ivHeaderAction.setOnClickListener(v -> {
                if (isLoading) {
                    stopRefreshing();
                    return;
                }
                loadProducts(false);
            });
        }

        if (etSearchManage != null) {
            etSearchManage.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    currentQuery = s == null ? "" : s.toString().trim();
                    applyFilter();
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });
        }
    }

    private void applyInsets() {
        InsetsHelper.applyRecyclerBottomInsets(rootManage != null ? rootManage : requireView(), rv, TAG);
        applyFabBottomInset(fabAdd, 56);
    }

    private boolean isRapidFabClick() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastFabClickTime < CLICK_GUARD_MS) {
            Log.d(TAG, "FAB click ignored: too fast");
            return true;
        }
        lastFabClickTime = now;
        return false;
    }

    private boolean canRunRowAction() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastRowActionTime < CLICK_GUARD_MS) {
            Log.d(TAG, "Row action ignored: too fast");
            return false;
        }
        lastRowActionTime = now;
        return true;
    }

    private void openAddProductDialogSafely() {
        if (!isAdded()) return;
        if (isRapidFabClick()) return;
        if (isStateSaved()) return;
        if (isDialogOpening) return;

        if (getParentFragmentManager().findFragmentByTag(TAG_ADD_PRODUCT) != null) {
            Log.d(TAG, "Add product dialog already showing");
            return;
        }

        isDialogOpening = true;
        setFabEnabled(false);

        ProductFormDialog dialog = ProductFormDialog.newAdd(saved -> {
            isDialogOpening = false;
            setFabEnabled(true);
            loadProducts(false);
        });

        getParentFragmentManager().registerFragmentLifecycleCallbacks(
                new androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks() {
                    @Override
                    public void onFragmentViewDestroyed(@NonNull androidx.fragment.app.FragmentManager fm,
                                                        @NonNull androidx.fragment.app.Fragment f) {
                        if (f == dialog) {
                            isDialogOpening = false;
                            setFabEnabled(true);
                            fm.unregisterFragmentLifecycleCallbacks(this);
                        }
                    }
                }, false
        );

        dialog.show(getParentFragmentManager(), TAG_ADD_PRODUCT);
    }

    private void openEditProductDialogSafely(@NonNull Product p) {
        if (!isAdded()) return;
        if (isStateSaved()) return;
        if (isDialogOpening) return;

        if (getParentFragmentManager().findFragmentByTag(TAG_EDIT_PRODUCT) != null) {
            Log.d(TAG, "Edit product dialog already showing");
            return;
        }

        isDialogOpening = true;
        setFabEnabled(false);

        ProductFormDialog dialog = ProductFormDialog.newEdit(p, saved -> {
            isDialogOpening = false;
            setFabEnabled(true);
            loadProducts(false);
        });

        getParentFragmentManager().registerFragmentLifecycleCallbacks(
                new androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks() {
                    @Override
                    public void onFragmentViewDestroyed(@NonNull androidx.fragment.app.FragmentManager fm,
                                                        @NonNull androidx.fragment.app.Fragment f) {
                        if (f == dialog) {
                            isDialogOpening = false;
                            setFabEnabled(true);
                            fm.unregisterFragmentLifecycleCallbacks(this);
                        }
                    }
                }, false
        );

        dialog.show(getParentFragmentManager(), TAG_EDIT_PRODUCT);
    }

    private void confirmDeleteSafely(@NonNull Product p) {
        if (!isAdded()) return;
        if (isDeleteRunning) return;

        ConfirmDeleteDialog.show(
                requireContext(),
                "Delete product",
                "Delete \"" + safeText(p.name, "this product") + "\"?",
                () -> {
                    if (isDeleteRunning) return;
                    doDelete(p);
                }
        );
    }

    private void loadProducts(boolean fromSwipe) {
        if (!isAdded()) return;

        if (isLoading) {
            stopRefreshing();
            return;
        }

        if (session == null || repo == null) {
            Log.e(TAG, "Session/Repo not initialized.");
            stopRefreshing();
            return;
        }

        String token = session.getToken();
        if (token == null || token.trim().isEmpty()) {
            showEmpty("Token is missing");
            stopRefreshing();
            return;
        }

        isLoading = true;

        if (!fromSwipe && swipe != null && !swipe.isRefreshing()) {
            showLoading();
        }

        repo.fetchProducts(token, "all", new ProductRepository.Callback() {
            @Override
            public void onSuccess(@NonNull List<Product> products) {
                isLoading = false;
                if (!isAdded()) return;

                Log.i(TAG, "fetchProducts SUCCESS: " + products.size());

                allItems.clear();
                allItems.addAll(products);
                applyFilter();

                stopRefreshing();
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                isLoading = false;
                if (!isAdded()) return;

                Log.e(TAG, "fetchProducts ERROR: " + statusCode + " / " + message);

                if (items.isEmpty()) {
                    showEmpty("Failed to load products");
                } else {
                    showList();
                }

                toast("Failed to load products");
                stopRefreshing();
            }
        });
    }

    private void applyFilter() {
        items.clear();

        if (TextUtils.isEmpty(currentQuery)) {
            items.addAll(allItems);
        } else {
            String q = currentQuery.toLowerCase(Locale.US);

            for (Product p : allItems) {
                String name = safeLower(p.name);
                String sku = safeLower(p.sku);
                String barcode = safeLower(p.barcode);
                String category = safeLower(p.categoryName);
                String unit = safeLower(p.unitName);
                String supplier = safeLower(p.supplierName);

                if (name.contains(q)
                        || sku.contains(q)
                        || barcode.contains(q)
                        || category.contains(q)
                        || unit.contains(q)
                        || supplier.contains(q)) {
                    items.add(p);
                }
            }
        }

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }

        if (items.isEmpty()) {
            showEmpty(TextUtils.isEmpty(currentQuery) ? "No products" : "No matching products");
        } else {
            showList();
        }
    }

    private void doDelete(@NonNull Product p) {
        if (!isAdded()) return;
        if (isDeleteRunning) return;

        String token = session != null ? session.getToken() : null;
        if (token == null || token.trim().isEmpty()) {
            toast("Token is missing");
            return;
        }

        isDeleteRunning = true;
        showLoading();

        repo.deleteProduct(token, p.id, new ProductRepository.DeleteCallback() {
            @Override
            public void onSuccess() {
                isDeleteRunning = false;
                if (!isAdded()) return;

                toast("Deleted: " + safeText(p.name, "Product"));

                removeItemById(p.id, allItems);
                removeItemById(p.id, items);

                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }

                if (items.isEmpty()) {
                    showEmpty(TextUtils.isEmpty(currentQuery) ? "No products" : "No matching products");
                } else {
                    showList();
                }
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                isDeleteRunning = false;
                if (!isAdded()) return;

                if (statusCode == 404) {
                    toast("Already deleted");

                    removeItemById(p.id, allItems);
                    removeItemById(p.id, items);

                    if (adapter != null) {
                        adapter.notifyDataSetChanged();
                    }

                    if (items.isEmpty()) {
                        showEmpty(TextUtils.isEmpty(currentQuery) ? "No products" : "No matching products");
                    } else {
                        showList();
                    }
                    return;
                }

                toast("Delete failed");
                if (!items.isEmpty()) {
                    showList();
                } else {
                    showEmpty(TextUtils.isEmpty(currentQuery) ? "No products" : "No matching products");
                }
            }
        });
    }

    private void removeItemById(@Nullable String id, @NonNull List<Product> target) {
        if (id == null) return;

        String safeId = id.trim();

        for (int i = 0; i < target.size(); i++) {
            String itemId = target.get(i).id;

            if (itemId != null && safeId.equals(itemId.trim())) {
                target.remove(i);
                return;
            }
        }
    }

    private void stopRefreshing() {
        if (swipe != null) {
            swipe.setRefreshing(false);
        }
    }

    private void toast(@NonNull String msg) {
        if (!isAdded()) return;
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
    }

    private void showLoading() {
        if (progress != null) progress.setVisibility(View.VISIBLE);
        if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
        if (rv != null) rv.setVisibility(View.GONE);
        setFabEnabled(false);
    }

    private void showEmpty(@NonNull String msg) {
        if (progress != null) progress.setVisibility(View.GONE);
        if (tvEmpty != null) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText(msg);
        }
        if (rv != null) rv.setVisibility(View.GONE);
        setFabEnabled(true);
    }

    private void showList() {
        if (progress != null) progress.setVisibility(View.GONE);
        if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
        if (rv != null) rv.setVisibility(View.VISIBLE);
        setFabEnabled(true);
    }

    private void setFabEnabled(boolean enabled) {
        if (fabAdd == null) return;
        boolean finalEnabled = enabled && !isDialogOpening && !isLoading && !isDeleteRunning;
        fabAdd.setEnabled(finalEnabled);
        fabAdd.setAlpha(finalEnabled ? 1f : 0.65f);
    }

    @NonNull
    private String safeLower(@Nullable String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
    }

    @NonNull
    private String safeText(@Nullable String value, @NonNull String fallback) {
        if (value == null) return fallback;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        isLoading = false;
        isDialogOpening = false;
        isDeleteRunning = false;

        swipe = null;
        rv = null;
        progress = null;
        tvEmpty = null;
        tvTitle = null;
        fabAdd = null;
        etSearchManage = null;
        btnBack = null;
        ivHeaderAction = null;
        rootManage = null;
    }
}