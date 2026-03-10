package com.example.valdker.ui;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.valdker.R;
import com.example.valdker.SessionManager;
import com.example.valdker.adapters.ProductManageAdapter;
import com.example.valdker.models.Product;
import com.example.valdker.repositories.ProductRepository;
import com.example.valdker.ui.customers.ConfirmDeleteDialog;
import com.example.valdker.utils.InsetsHelper;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class ProductsManageFragment extends Fragment {

    private static final String TAG = "PRODUCTS_MANAGE";

    private SwipeRefreshLayout swipe;
    private RecyclerView rv;
    private ProgressBar progress;
    private TextView tvEmpty;
    private FloatingActionButton fabAdd;
    private View rootManage;

    private final List<Product> items = new ArrayList<>();
    private ProductManageAdapter adapter;

    private SessionManager session;
    private ProductRepository repo;

    private boolean insetsApplied = false;

    public ProductsManageFragment() {
        super(R.layout.fragment_products_manage);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        session = new SessionManager(requireContext());
        repo = new ProductRepository(requireContext());

        swipe = view.findViewById(R.id.swipeRefreshManage);
        rv = view.findViewById(R.id.rvProductsManage);
        progress = view.findViewById(R.id.progressManage);
        tvEmpty = view.findViewById(R.id.tvEmptyManage);
        fabAdd = view.findViewById(R.id.fabAddProduct);
        rootManage = view.findViewById(R.id.rootManage);

        setupRecycler();
        setupAdapter();
        setupActions();
        applyInsetsOnce();

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
                ProductFormDialog
                        .newEdit(p, saved -> loadProducts(false))
                        .show(getParentFragmentManager(), "edit_product");
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
        if (swipe != null) swipe.setOnRefreshListener(() -> loadProducts(true));

        if (fabAdd != null) {
            fabAdd.setOnClickListener(v -> ProductFormDialog
                    .newAdd(saved -> loadProducts(false))
                    .show(getParentFragmentManager(), "add_product"));
        }
    }

    private void applyInsetsOnce() {
        if (insetsApplied) return;
        if (rootManage == null || rv == null || fabAdd == null) return;
        insetsApplied = true;

        InsetsHelper.applyFabMarginInsets(fabAdd, 16, TAG);
        InsetsHelper.applyRecyclerBottomInsetsWithFab(rootManage, rv, fabAdd, 32, TAG);
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

        if (!fromSwipe && swipe != null && !swipe.isRefreshing()) showLoading();

        repo.fetchProducts(token, "all", new ProductRepository.Callback() {
            @Override
            public void onSuccess(@NonNull List<Product> products) {
                if (!isAdded()) return;

                Log.i(TAG, "fetchProducts SUCCESS: " + products.size());

                items.clear();
                items.addAll(products);

                if (adapter != null) adapter.notifyDataSetChanged();

                if (items.isEmpty()) showEmpty("No products");
                else showList();

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

    private void doDelete(@NonNull Product p) {
        String token = session.getToken();
        if (token == null || token.trim().isEmpty()) {
            toast("Token is missing.");
            return;
        }

        showLoading();

        repo.deleteProduct(token, p.id, new ProductRepository.DeleteCallback() {
            @Override
            public void onSuccess() {
                if (!isAdded()) return;

                toast("Deleted: " + (p.name != null ? p.name : "Product"));

                // ✅ remove dari list lokal
                int pos = -1;
                for (int i = 0; i < items.size(); i++) {
                    if (items.get(i).id != null && items.get(i).id.equals(p.id)) {
                        pos = i; break;
                    }
                }
                if (pos >= 0) {
                    items.remove(pos);
                    if (adapter != null) adapter.notifyItemRemoved(pos);
                } else {
                    if (adapter != null) adapter.notifyDataSetChanged();
                }

                if (items.isEmpty()) showEmpty("No products");
                else showList();
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                if (!isAdded()) return;

                if (statusCode == 404) {
                    toast("Already deleted");
                    int pos = -1;
                    for (int i = 0; i < items.size(); i++) {
                        if (items.get(i).id != null && items.get(i).id.equals(p.id)) {
                            pos = i; break;
                        }
                    }
                    if (pos >= 0) {
                        items.remove(pos);
                        if (adapter != null) adapter.notifyItemRemoved(pos);
                    } else if (adapter != null) {
                        adapter.notifyDataSetChanged();
                    }

                    if (items.isEmpty()) showEmpty("No products");
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        swipe = null;
        rv = null;
        progress = null;
        tvEmpty = null;
        fabAdd = null;
        rootManage = null;

        insetsApplied = false;
    }
}