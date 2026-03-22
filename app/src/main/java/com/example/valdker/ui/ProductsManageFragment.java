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
    private static final long FAB_CLICK_DELAY_MS = 700L;

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
    private boolean isDialogOpening = false;
    private boolean isDeleteRunning = false;

    private long lastFabClickTime = 0L;
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

        setupRecycler();
        setupAdapter();
        setupActions();
        applyInsets();

        loadProducts(false);
    }

    private void setupRecycler() {
        if (rv == null) return;
        rv.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        rv.setHasFixedSize(true);
        rv.setClipToPadding(false);
    }

    private void setupAdapter() {
        adapter = new ProductManageAdapter(items, new ProductManageAdapter.Listener() {
            @Override
            public void onEdit(@NonNull Product p) {
                if (!isAdded()) return;
                if (isStateSaved()) return;
                if (isDialogOpening) return;

                if (getParentFragmentManager().findFragmentByTag(TAG_EDIT_PRODUCT) != null) {
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

            @Override
            public void onDelete(@NonNull Product p) {
                ConfirmDeleteDialog.show(
                        requireContext(),
                        "Delete product",
                        "Delete \"" + (p.name != null ? p.name : "this product") + "\"?",
                        () -> doDelete(p)
                );
            }
        });

        if (rv != null) rv.setAdapter(adapter);
    }

    private void setupActions() {
        if (swipe != null) {
            swipe.setOnRefreshListener(() -> loadProducts(true));
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
            ivHeaderAction.setOnClickListener(v -> loadProducts(false));
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
        if (rootManage == null || rv == null) return;
        InsetsHelper.applyRecyclerBottomInsets(rootManage, rv, TAG);
    }

    private void openAddProductDialogSafely() {
        if (!isAdded()) return;
        if (isStateSaved()) return;
        if (isDialogOpening) return;

        long now = SystemClock.elapsedRealtime();
        if (now - lastFabClickTime < FAB_CLICK_DELAY_MS) {
            return;
        }
        lastFabClickTime = now;

        if (getParentFragmentManager().findFragmentByTag(TAG_ADD_PRODUCT) != null) {
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

    private void loadProducts(boolean fromSwipe) {
        if (!isAdded()) return;

        String token = session.getToken();
        if (token == null || token.trim().isEmpty()) {
            toast("Token is missing. Please login again.");
            showEmpty("Token is missing");
            stopRefreshing();
            return;
        }

        if (!fromSwipe && swipe != null && !swipe.isRefreshing()) {
            showLoading();
        }

        repo.fetchProducts(token, "all", new ProductRepository.Callback() {
            @Override
            public void onSuccess(@NonNull List<Product> products) {
                if (!isAdded()) return;

                Log.i(TAG, "fetchProducts SUCCESS: " + products.size());

                allItems.clear();
                allItems.addAll(products);
                applyFilter();

                stopRefreshing();
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                if (!isAdded()) return;

                Log.e(TAG, "fetchProducts ERROR: " + statusCode + " / " + message);

                if (items.isEmpty()) showEmpty("Failed to load products");
                else showList();

                toast(message);
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
                String name = p.name == null ? "" : p.name.toLowerCase(Locale.US);
                String sku = p.sku == null ? "" : p.sku.toLowerCase(Locale.US);
                String barcode = p.barcode == null ? "" : p.barcode.toLowerCase(Locale.US);
                String category = p.categoryName == null ? "" : p.categoryName.toLowerCase(Locale.US);
                String unit = p.unitName == null ? "" : p.unitName.toLowerCase(Locale.US);
                String supplier = p.supplierName == null ? "" : p.supplierName.toLowerCase(Locale.US);

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

        if (adapter != null) adapter.notifyDataSetChanged();

        if (items.isEmpty()) {
            showEmpty(TextUtils.isEmpty(currentQuery) ? "No products" : "No matching products");
        } else {
            showList();
        }
    }

    private void doDelete(@NonNull Product p) {
        if (!isAdded()) return;
        if (isDeleteRunning) return;

        String token = session.getToken();
        if (token == null || token.trim().isEmpty()) {
            toast("Token is missing.");
            return;
        }

        isDeleteRunning = true;
        showLoading();

        repo.deleteProduct(token, p.id, new ProductRepository.DeleteCallback() {
            @Override
            public void onSuccess() {
                if (!isAdded()) return;

                isDeleteRunning = false;
                toast("Deleted: " + (p.name != null ? p.name : "Product"));

                int pos = -1;
                for (int i = 0; i < items.size(); i++) {
                    if (items.get(i).id != null && items.get(i).id.equals(p.id)) {
                        pos = i;
                        break;
                    }
                }

                if (pos >= 0) {
                    items.remove(pos);
                    if (adapter != null) adapter.notifyItemRemoved(pos);
                } else {
                    if (adapter != null) adapter.notifyDataSetChanged();
                }

                for (int i = 0; i < allItems.size(); i++) {
                    if (allItems.get(i).id != null && allItems.get(i).id.equals(p.id)) {
                        allItems.remove(i);
                        break;
                    }
                }

                if (items.isEmpty()) showEmpty(TextUtils.isEmpty(currentQuery) ? "No products" : "No matching products");
                else showList();
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                if (!isAdded()) return;

                isDeleteRunning = false;

                if (statusCode == 404) {
                    toast("Already deleted");

                    int pos = -1;
                    for (int i = 0; i < items.size(); i++) {
                        if (items.get(i).id != null && items.get(i).id.equals(p.id)) {
                            pos = i;
                            break;
                        }
                    }

                    if (pos >= 0) {
                        items.remove(pos);
                        if (adapter != null) adapter.notifyItemRemoved(pos);
                    } else if (adapter != null) {
                        adapter.notifyDataSetChanged();
                    }

                    for (int i = 0; i < allItems.size(); i++) {
                        if (allItems.get(i).id != null && allItems.get(i).id.equals(p.id)) {
                            allItems.remove(i);
                            break;
                        }
                    }

                    if (items.isEmpty()) showEmpty(TextUtils.isEmpty(currentQuery) ? "No products" : "No matching products");
                    else showList();
                    return;
                }

                toast("Delete failed: " + message);
                showList();
            }
        });
    }

    private void stopRefreshing() {
        if (swipe != null) swipe.setRefreshing(false);
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
        fabAdd.setEnabled(enabled && !isDialogOpening);
        fabAdd.setAlpha((enabled && !isDialogOpening) ? 1f : 0.65f);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
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