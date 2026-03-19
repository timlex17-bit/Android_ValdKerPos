package com.example.valdker.ui.productreturns;

import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.valdker.R;
import com.example.valdker.models.CustomerLite;
import com.example.valdker.models.OrderLite;
import com.example.valdker.models.ProductLite;
import com.example.valdker.models.ProductReturn;
import com.example.valdker.repositories.LiteRepository;
import com.example.valdker.repositories.ProductReturnRepository;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class ProductReturnsFragment extends Fragment {

    private static final String TAG = "ProductReturnsFragment";
    private static final long CLICK_GUARD_MS = 700L;

    private SwipeRefreshLayout swipe;
    private ProgressBar progress;
    private TextView tvEmpty;
    private RecyclerView rv;
    private FloatingActionButton fabAdd;

    private final List<OrderLite> ordersLite = new ArrayList<>();
    private final List<CustomerLite> customersLite = new ArrayList<>();
    private final List<ProductLite> productsLite = new ArrayList<>();

    private final List<ProductReturn> data = new ArrayList<>();
    private ProductReturnAdapter adapter;

    private boolean isLoadingList = false;
    private boolean isPreloadingLite = false;
    private boolean isDialogOpening = false;

    private long lastFabClickAt = 0L;
    private long lastRowClickAt = 0L;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_product_returns, container, false);

        swipe = v.findViewById(R.id.swipe);
        progress = v.findViewById(R.id.progress);
        tvEmpty = v.findViewById(R.id.tvEmpty);
        rv = v.findViewById(R.id.rv);
        fabAdd = v.findViewById(R.id.fabAdd);

        // Jangan pakai InsetsHelper.applyFabMarginInsets(...)
        // supaya posisi FAB tetap persis di bawah seperti fragment lain

        adapter = new ProductReturnAdapter(data, item -> {
            if (!canRunRowAction()) return;
            if (!isAdded()) return;

            Intent i = new Intent(requireContext(), ProductReturnDetailActivity.class);
            i.putExtra(ProductReturnDetailActivity.EXTRA_DATA, item);
            startActivity(i);
        });

        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);

        fabAdd.setOnClickListener(view -> {
            if (!isAdded()) return;
            if (isRapidFabClick()) return;
            if (isDialogOpening) return;

            openAddDialog();
        });

        swipe.setOnRefreshListener(() -> {
            if (isLoadingList) {
                swipe.setRefreshing(false);
                return;
            }
            load();
        });

        fabAdd.post(() -> {
            if (fabAdd == null) return;
            fabAdd.bringToFront();
            fabAdd.setElevation(100f);
            fabAdd.setTranslationZ(100f);
        });

        load();
        preloadLiteData();

        return v;
    }

    private boolean isRapidFabClick() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastFabClickAt < CLICK_GUARD_MS) {
            return true;
        }
        lastFabClickAt = now;
        return false;
    }

    private boolean canRunRowAction() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastRowClickAt < CLICK_GUARD_MS) {
            return false;
        }
        lastRowClickAt = now;
        return true;
    }

    private void openAddDialog() {
        if (!isAdded()) return;
        if (isDialogOpening) return;
        if (isStateSaved()) return;

        if (ordersLite.isEmpty() || customersLite.isEmpty() || productsLite.isEmpty()) {
            Toast.makeText(requireContext(), "Loading spinner data...", Toast.LENGTH_SHORT).show();
            preloadLiteData();
            return;
        }

        isDialogOpening = true;
        setFabEnabled(false);

        ProductReturnAddDialog dlg = new ProductReturnAddDialog(
                ordersLite,
                this::fetchData
        );

        getChildFragmentManager().registerFragmentLifecycleCallbacks(
                new androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks() {
                    @Override
                    public void onFragmentViewDestroyed(@NonNull androidx.fragment.app.FragmentManager fm,
                                                        @NonNull androidx.fragment.app.Fragment f) {
                        if (f == dlg) {
                            isDialogOpening = false;
                            setFabEnabled(true);
                            fm.unregisterFragmentLifecycleCallbacks(this);
                        }
                    }
                }, false
        );

        dlg.show(getChildFragmentManager(), "add_return");
    }

    private void fetchData() {
        isDialogOpening = false;
        setFabEnabled(true);
        load();
    }

    private void preloadLiteData() {
        if (!isAdded()) return;
        if (isPreloadingLite) return;

        isPreloadingLite = true;

        final int[] doneCount = {0};

        LiteRepository.fetchOrdersLite(requireContext(), new LiteRepository.LiteCallback<OrderLite>() {
            @Override
            public void onSuccess(@NonNull List<OrderLite> items) {
                if (!isAdded()) return;
                ordersLite.clear();
                ordersLite.addAll(items);
                doneCount[0]++;
                finishPreloadIfDone(doneCount[0]);
            }

            @Override
            public void onError(@NonNull String message) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                doneCount[0]++;
                finishPreloadIfDone(doneCount[0]);
            }
        });

        LiteRepository.fetchCustomersLite(requireContext(), new LiteRepository.LiteCallback<CustomerLite>() {
            @Override
            public void onSuccess(@NonNull List<CustomerLite> items) {
                if (!isAdded()) return;
                customersLite.clear();
                customersLite.addAll(items);
                doneCount[0]++;
                finishPreloadIfDone(doneCount[0]);
            }

            @Override
            public void onError(@NonNull String message) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                doneCount[0]++;
                finishPreloadIfDone(doneCount[0]);
            }
        });

        LiteRepository.fetchProductsLite(requireContext(), new LiteRepository.LiteCallback<ProductLite>() {
            @Override
            public void onSuccess(@NonNull List<ProductLite> items) {
                if (!isAdded()) return;
                productsLite.clear();
                productsLite.addAll(items);
                doneCount[0]++;
                finishPreloadIfDone(doneCount[0]);
            }

            @Override
            public void onError(@NonNull String message) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                doneCount[0]++;
                finishPreloadIfDone(doneCount[0]);
            }
        });
    }

    private void finishPreloadIfDone(int doneCount) {
        if (doneCount >= 3) {
            isPreloadingLite = false;
        }
    }

    private void load() {
        if (!isAdded()) return;
        if (isLoadingList) return;

        isLoadingList = true;
        showLoading(true);

        ProductReturnRepository.fetchAll(requireContext(), new ProductReturnRepository.ListCallback() {
            @Override
            public void onSuccess(@NonNull List<ProductReturn> items) {
                if (!isAdded()) return;

                isLoadingList = false;
                data.clear();
                data.addAll(items);
                adapter.notifyDataSetChanged();

                showLoading(false);
                updateEmpty();
            }

            @Override
            public void onError(@NonNull String message) {
                if (!isAdded()) return;

                isLoadingList = false;
                showLoading(false);
                tvEmpty.setText(message);
                tvEmpty.setVisibility(View.VISIBLE);
            }
        });
    }

    private void showLoading(boolean loading) {
        if (swipe != null) {
            swipe.setRefreshing(false);
        }
        if (progress != null) {
            progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
    }

    private void updateEmpty() {
        if (tvEmpty == null) return;

        if (data.isEmpty()) {
            tvEmpty.setText("No product returns yet.");
            tvEmpty.setVisibility(View.VISIBLE);
        } else {
            tvEmpty.setVisibility(View.GONE);
        }
    }

    private void setFabEnabled(boolean enabled) {
        if (fabAdd == null) return;
        fabAdd.setEnabled(enabled);
        fabAdd.setAlpha(enabled ? 1f : 0.65f);
    }
}