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
import com.example.valdker.adapters.ProductManageAdapter;
import com.example.valdker.models.Product;
import com.example.valdker.repositories.ProductRepository;
import com.example.valdker.ui.customers.ConfirmDeleteDialog;
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

    // ✅ NEW: root container to receive window insets
    private View rootManage;

    private final List<Product> items = new ArrayList<>();
    private ProductManageAdapter adapter;

    private SessionManager session;
    private ProductRepository repo;

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

        // ✅ NEW
        rootManage = view.findViewById(R.id.rootManage);

        GridLayoutManager glm = new GridLayoutManager(requireContext(), 2); // tablet manage: 2 kolom rapi
        rv.setLayoutManager(glm);

        adapter = new ProductManageAdapter(items, new ProductManageAdapter.Listener() {
            @Override
            public void onEdit(@NonNull Product p) {
                ProductFormDialog.newEdit(p, saved -> loadProducts(false))
                        .show(getParentFragmentManager(), "edit_product");
            }

            @Override
            public void onDelete(@NonNull Product p) {
                ConfirmDeleteDialog.show(
                        requireContext(),
                        "Delete product",
                        "Delete \"" + p.name + "\" ?",
                        () -> doDelete(p)
                );
            }
        });

        rv.setAdapter(adapter);

        swipe.setOnRefreshListener(() -> loadProducts(true));

        fabAdd.setOnClickListener(v -> {
            ProductFormDialog.newAdd(saved -> loadProducts(false))
                    .show(getParentFragmentManager(), "add_product");
        });

        // ✅ FIX: keep FAB above system navigation bar (edge-to-edge safe)
        applyFabInsets();

        loadProducts(false);
    }

    // ✅ NEW: apply bottom/right insets to FAB using rootManage (more reliable)
    private void applyFabInsets() {
        if (rootManage == null || fabAdd == null) return;

        final int base = dp(16);

        ViewCompat.setOnApplyWindowInsetsListener(rootManage, (v, insets) -> {
            int bottomInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            int rightInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).right;

            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) fabAdd.getLayoutParams();
            lp.bottomMargin = base + bottomInset;
            lp.rightMargin = base + rightInset;
            fabAdd.setLayoutParams(lp);

            return insets;
        });

        ViewCompat.requestApplyInsets(rootManage);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void loadProducts(boolean fromSwipe) {
        if (!isAdded() || getView() == null) return;

        String token = session.getToken();
        if (token == null || token.trim().isEmpty()) {
            toast("Token empty. Please login again.");
            showEmpty("Token empty");
            stopRefreshing();
            return;
        }

        if (!fromSwipe && !swipe.isRefreshing()) showLoading();

        repo.fetchProducts(token, "all", new ProductRepository.Callback() {
            @Override
            public void onSuccess(@NonNull List<Product> products) {
                if (!isAdded() || getView() == null) return;

                Log.i(TAG, "fetchProducts SUCCESS: " + products.size());

                items.clear();
                items.addAll(products);
                adapter.notifyDataSetChanged();

                if (items.isEmpty()) showEmpty("No products");
                else showList();

                stopRefreshing();
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                if (!isAdded() || getView() == null) return;
                Log.e(TAG, "fetchProducts ERROR: " + statusCode + " / " + message);
                showEmpty("Error " + statusCode);
                stopRefreshing();
            }
        });
    }

    private void doDelete(@NonNull Product p) {
        String token = session.getToken();
        if (token == null || token.trim().isEmpty()) {
            toast("Token empty");
            return;
        }

        showLoading();

        repo.deleteProduct(token, p.id, new ProductRepository.DeleteCallback() {
            @Override
            public void onSuccess() {
                toast("Deleted: " + p.name);
                loadProducts(false);
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                toast("Delete failed: " + statusCode);
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
}
