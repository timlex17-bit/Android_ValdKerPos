package com.example.valdker.ui.productreturns;

import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedDispatcher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.valdker.R;
import com.example.valdker.base.BaseFragment;
import com.example.valdker.models.CustomerLite;
import com.example.valdker.models.OrderLite;
import com.example.valdker.models.ProductLite;
import com.example.valdker.models.ProductReturn;
import com.example.valdker.repositories.LiteRepository;
import com.example.valdker.repositories.ProductReturnRepository;
import com.example.valdker.utils.InsetsHelper;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class ProductReturnsFragment extends BaseFragment {

    private static final long CLICK_GUARD_MS = 700L;
    private static final String TAG = "PRODUCT_RETURNS";

    private SwipeRefreshLayout swipe;
    private ProgressBar progress;
    private TextView tvEmpty;
    private RecyclerView rv;
    private FloatingActionButton fabAdd;
    private ImageView btnBack;
    private ImageView ivHeaderAction;

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
    private long lastRefreshClickAt = 0L;

    public ProductReturnsFragment() {
        super(R.layout.fragment_product_returns);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        applyTopInset(view.findViewById(R.id.topBar));

        bindViews(view);
        applyInsets(view);
        setupHeader();
        setupRecycler();
        setupFab();
        setupSwipe();

        load();
        preloadLiteData();
    }

    private void bindViews(@NonNull View view) {
        swipe = view.findViewById(R.id.swipe);
        progress = view.findViewById(R.id.progress);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        rv = view.findViewById(R.id.rv);
        fabAdd = view.findViewById(R.id.fabAdd);
        btnBack = view.findViewById(R.id.btnBack);
        ivHeaderAction = view.findViewById(R.id.ivHeaderAction);
    }

    private void applyInsets(@NonNull View root) {
        InsetsHelper.applyRecyclerBottomInsets(root, rv, TAG);
        applyFabBottomInset(fabAdd, 56);
    }

    private void setupHeader() {
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                if (!isAdded()) return;
                OnBackPressedDispatcher dispatcher = requireActivity().getOnBackPressedDispatcher();
                dispatcher.onBackPressed();
            });
        }

        if (ivHeaderAction != null) {
            ivHeaderAction.setOnClickListener(v -> {
                if (!isAdded()) return;
                if (isRapidRefreshClick()) return;
                if (isLoadingList) return;

                if (swipe != null && !swipe.isRefreshing()) {
                    swipe.setRefreshing(true);
                }
                load();
            });
        }
    }

    private void setupRecycler() {
        if (rv == null) return;

        adapter = new ProductReturnAdapter(data, item -> {
            if (!canRunRowAction()) return;
            if (!isAdded()) return;

            Intent i = new Intent(requireContext(), ProductReturnDetailActivity.class);
            i.putExtra(ProductReturnDetailActivity.EXTRA_DATA, item);
            startActivity(i);
        });

        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);
        rv.setHasFixedSize(false);
        rv.setClipToPadding(false);
    }

    private void setupFab() {
        if (fabAdd == null) return;

        setFabEnabled(true);

        fabAdd.post(() -> {
            if (fabAdd == null) return;
            fabAdd.bringToFront();
            fabAdd.setElevation(100f);
            fabAdd.setTranslationZ(100f);
        });

        fabAdd.setOnClickListener(v -> {
            if (!isAdded()) return;
            if (isRapidFabClick()) return;
            if (isDialogOpening) return;
            if (isLoadingList) return;

            openAddDialog();
        });
    }

    private void setupSwipe() {
        if (swipe == null) return;

        swipe.setOnRefreshListener(() -> {
            if (isLoadingList) {
                swipe.setRefreshing(false);
                return;
            }
            load();
        });
    }

    private boolean isRapidFabClick() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastFabClickAt < CLICK_GUARD_MS) {
            return true;
        }
        lastFabClickAt = now;
        return false;
    }

    private boolean isRapidRefreshClick() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastRefreshClickAt < CLICK_GUARD_MS) {
            return true;
        }
        lastRefreshClickAt = now;
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
            toast("Loading spinner data...");
            preloadLiteData();
            return;
        }

        isDialogOpening = true;
        setFabEnabled(false);

        ProductReturnAddDialog dialog = new ProductReturnAddDialog(
                ordersLite,
                this::fetchData
        );

        getChildFragmentManager().registerFragmentLifecycleCallbacks(
                new FragmentManager.FragmentLifecycleCallbacks() {
                    @Override
                    public void onFragmentViewDestroyed(@NonNull FragmentManager fm,
                                                        @NonNull androidx.fragment.app.Fragment f) {
                        if (f == dialog) {
                            isDialogOpening = false;
                            setFabEnabled(true);
                            fm.unregisterFragmentLifecycleCallbacks(this);
                        }
                    }
                },
                false
        );

        dialog.show(getChildFragmentManager(), "add_return");
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
                toast(message);
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
                toast(message);
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
                toast(message);
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
                showLoading(false);

                data.clear();
                data.addAll(items);

                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }

                updateEmptyDefault();
            }

            @Override
            public void onError(@NonNull String message) {
                if (!isAdded()) return;

                isLoadingList = false;
                showLoading(false);

                data.clear();
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }

                if (tvEmpty != null) {
                    tvEmpty.setText(message == null || message.trim().isEmpty()
                            ? "Failed to load product returns."
                            : message);
                    tvEmpty.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void showLoading(boolean loading) {
        if (loading) {
            if (progress != null) {
                progress.setVisibility(View.VISIBLE);
            }
            if (tvEmpty != null) {
                tvEmpty.setVisibility(View.GONE);
            }
        } else {
            if (progress != null) {
                progress.setVisibility(View.GONE);
            }
            if (swipe != null) {
                swipe.setRefreshing(false);
            }
        }

        setFabEnabled(!loading);

        if (ivHeaderAction != null) {
            boolean enabled = !loading;
            ivHeaderAction.setEnabled(enabled);
            ivHeaderAction.setAlpha(enabled ? 1f : 0.5f);
        }
    }

    private void updateEmptyDefault() {
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
        boolean finalEnabled = enabled && !isDialogOpening && !isLoadingList;
        fabAdd.setEnabled(finalEnabled);
        fabAdd.setAlpha(finalEnabled ? 1f : 0.65f);
    }

    private void toast(@NonNull String message) {
        if (!isAdded()) return;
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        if (swipe != null) {
            swipe.setOnRefreshListener(null);
        }

        if (rv != null) {
            rv.setAdapter(null);
        }

        swipe = null;
        progress = null;
        tvEmpty = null;
        rv = null;
        fabAdd = null;
        btnBack = null;
        ivHeaderAction = null;
        adapter = null;

        isLoadingList = false;
        isPreloadingLite = false;
        isDialogOpening = false;

        super.onDestroyView();
    }
}