package com.valdker.pos.ui.stockadjustments;

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

import com.android.volley.Request;
import com.android.volley.toolbox.JsonArrayRequest;
import com.valdker.pos.R;
import com.valdker.pos.SessionManager;
import com.valdker.pos.base.BaseFragment;
import com.valdker.pos.models.StockAdjustment;
import com.valdker.pos.network.ApiClient;
import com.valdker.pos.network.ApiConfig;
import com.valdker.pos.repositories.StockAdjustmentRepository;
import com.valdker.pos.utils.InsetsHelper;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StockAdjustmentsFragment extends BaseFragment {

    private static final String TAG = "STOCK_ADJUSTMENTS";
    private static final String TAG_ADD_DIALOG = "add_stock_adjustment";
    private static final long CLICK_GUARD_MS = 700L;

    private SwipeRefreshLayout swipe;
    private ProgressBar progress;
    private TextView tvEmpty;
    private RecyclerView rv;
    private FloatingActionButton fab;
    private ImageView btnBack;
    private ImageView ivHeaderAction;

    private StockAdjustmentsAdapter adapter;
    private final List<StockAdjustment> data = new ArrayList<>();

    private JSONArray productsJson = null;
    private boolean productsLoaded = false;
    private boolean isLoadingList = false;
    private boolean isLoadingProducts = false;
    private boolean isAddDialogShowing = false;

    private long lastFabClickTime = 0L;
    private long lastRowClickTime = 0L;
    private long lastRefreshClickTime = 0L;

    public StockAdjustmentsFragment() {
        super(R.layout.fragment_stock_adjustments);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        applyTopInset(view.findViewById(R.id.topBar));

        bindViews(view);
        applyInsets(view);
        setupHeader();
        setupRecycler();
        setupSwipe();
        setupFab();

        loadProducts();
        load();
    }

    private void bindViews(@NonNull View view) {
        swipe = view.findViewById(R.id.swipe);
        progress = view.findViewById(R.id.progress);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        rv = view.findViewById(R.id.rv);
        fab = view.findViewById(R.id.fabAdd);
        btnBack = view.findViewById(R.id.btnBack);
        ivHeaderAction = view.findViewById(R.id.ivHeaderAction);
    }

    private void applyInsets(@NonNull View root) {
        InsetsHelper.applyRecyclerBottomInsets(root, rv, TAG);
        applyFabBottomInset(fab, 56);
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

        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setHasFixedSize(false);
        rv.setClipToPadding(false);

        adapter = new StockAdjustmentsAdapter(data, item -> {
            if (!canRunRowClick()) return;
            if (!isAdded()) return;
            StockAdjustmentDetailActivity.open(requireContext(), item);
        });

        rv.setAdapter(adapter);
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

    private void setupFab() {
        if (fab == null) return;

        setFabEnabled(false);

        fab.post(() -> {
            if (fab == null) return;
            fab.bringToFront();
            fab.setElevation(100f);
            fab.setTranslationZ(100f);
        });

        fab.setOnClickListener(v -> openAddDialogSafely());
    }

    private boolean isRapidRefreshClick() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastRefreshClickTime < CLICK_GUARD_MS) {
            return true;
        }
        lastRefreshClickTime = now;
        return false;
    }

    private boolean canRunRowClick() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastRowClickTime < CLICK_GUARD_MS) {
            return false;
        }
        lastRowClickTime = now;
        return true;
    }

    private void openAddDialogSafely() {
        if (!isAdded()) return;
        if (isAddDialogShowing) return;
        if (isLoadingList) return;

        long now = SystemClock.elapsedRealtime();
        if (now - lastFabClickTime < CLICK_GUARD_MS) {
            return;
        }
        lastFabClickTime = now;

        if (!productsLoaded || productsJson == null || productsJson.length() == 0) {
            toast("Product list not loaded yet");
            if (!isLoadingProducts) {
                loadProducts();
            }
            return;
        }

        if (isStateSaved()) return;

        FragmentManager fm = getChildFragmentManager();
        if (fm.findFragmentByTag(TAG_ADD_DIALOG) != null) {
            return;
        }

        isAddDialogShowing = true;
        setFabEnabled(false);

        StockAdjustmentFormDialog dialog =
                StockAdjustmentFormDialog.create(productsJson, this::reloadAfterDialog);

        fm.registerFragmentLifecycleCallbacks(new FragmentManager.FragmentLifecycleCallbacks() {
            @Override
            public void onFragmentViewDestroyed(@NonNull FragmentManager fragmentManager,
                                                @NonNull androidx.fragment.app.Fragment fragment) {
                if (fragment == dialog) {
                    isAddDialogShowing = false;
                    setFabEnabled(productsLoaded);
                    fragmentManager.unregisterFragmentLifecycleCallbacks(this);
                }
            }
        }, false);

        dialog.show(fm, TAG_ADD_DIALOG);
    }

    private void reloadAfterDialog() {
        isAddDialogShowing = false;
        setFabEnabled(productsLoaded);
        load();
    }

    private void load() {
        if (!isAdded()) return;
        if (isLoadingList) return;

        isLoadingList = true;
        showListLoading(true);

        StockAdjustmentRepository.fetch(requireContext(), new StockAdjustmentRepository.ListCallback() {
            @Override
            public void onSuccess(List<StockAdjustment> list) {
                if (!isAdded()) return;

                isLoadingList = false;
                showListLoading(false);

                data.clear();
                if (list != null) {
                    data.addAll(list);
                }

                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }

                if (tvEmpty != null) {
                    tvEmpty.setText("No stock adjustments");
                    tvEmpty.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
                }
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;

                isLoadingList = false;
                showListLoading(false);

                toast(message == null ? "Failed to load stock adjustments" : message);

                if (tvEmpty != null) {
                    tvEmpty.setText("No stock adjustments");
                    tvEmpty.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
                }
            }
        });
    }

    private void loadProducts() {
        if (!isAdded()) return;
        if (isLoadingProducts) return;

        isLoadingProducts = true;

        SessionManager sm = new SessionManager(requireContext());
        String url = ApiConfig.url(sm, "api/products/?track_stock=true");

        JsonArrayRequest req = new JsonArrayRequest(
                Request.Method.GET,
                url,
                null,
                response -> {
                    if (!isAdded()) return;

                    isLoadingProducts = false;
                    productsJson = response;
                    productsLoaded = productsJson != null && productsJson.length() > 0;

                    if (!isAddDialogShowing) {
                        setFabEnabled(productsLoaded);
                    }

                    if (!productsLoaded) {
                        toast("Products are empty");
                    }
                },
                error -> {
                    if (!isAdded()) return;

                    isLoadingProducts = false;
                    productsLoaded = false;
                    productsJson = null;
                    setFabEnabled(false);

                    String msg = "Failed load products";
                    if (error != null && error.networkResponse != null) {
                        msg += " (" + error.networkResponse.statusCode + ")";
                    }
                    toast(msg);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                SessionManager sessionManager = new SessionManager(requireContext());
                String token = sessionManager.getToken();

                Map<String, String> headers = new HashMap<>();
                headers.put("Accept", "application/json");
                if (token != null && !token.trim().isEmpty()) {
                    headers.put("Authorization", "Token " + token.trim());
                }
                return headers;
            }
        };

        req.setShouldCache(false);
        ApiClient.getInstance(requireContext()).add(req);
    }

    private void showListLoading(boolean loading) {
        if (loading) {
            if (tvEmpty != null) {
                tvEmpty.setVisibility(View.GONE);
            }
            if (swipe != null && !swipe.isRefreshing() && progress != null) {
                progress.setVisibility(View.VISIBLE);
            }
        } else {
            if (progress != null) {
                progress.setVisibility(View.GONE);
            }
            if (swipe != null) {
                swipe.setRefreshing(false);
            }
        }

        if (ivHeaderAction != null) {
            boolean enabled = !loading;
            ivHeaderAction.setEnabled(enabled);
            ivHeaderAction.setAlpha(enabled ? 1f : 0.5f);
        }

        setFabEnabled(productsLoaded);
    }

    private void setFabEnabled(boolean enabled) {
        if (fab == null) return;
        boolean finalEnabled = enabled && !isAddDialogShowing && !isLoadingList && !isLoadingProducts;
        fab.setEnabled(finalEnabled);
        fab.setAlpha(finalEnabled ? 1f : 0.4f);
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
        fab = null;
        btnBack = null;
        ivHeaderAction = null;
        adapter = null;

        isAddDialogShowing = false;
        isLoadingList = false;
        isLoadingProducts = false;

        super.onDestroyView();
    }
}