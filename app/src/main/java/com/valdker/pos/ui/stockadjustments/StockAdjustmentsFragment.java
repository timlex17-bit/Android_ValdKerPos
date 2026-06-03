package com.valdker.pos.ui.stockadjustments;

import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.valdker.pos.utils.Toast;

import androidx.activity.OnBackPressedDispatcher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.android.volley.Request;
import com.android.volley.toolbox.StringRequest;
import com.valdker.pos.R;
import com.valdker.pos.SessionManager;
import com.valdker.pos.base.BaseFragment;
import com.valdker.pos.models.StockAdjustment;
import com.valdker.pos.network.ApiClient;
import com.valdker.pos.network.ApiConfig;
import com.valdker.pos.repositories.InventoryOperationCacheRepository;
import com.valdker.pos.repositories.StockAdjustmentRepository;
import com.valdker.pos.utils.InsetsHelper;
import com.valdker.pos.utils.NetworkUtils;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.widget.EditText;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StockAdjustmentsFragment extends BaseFragment {

    private static final String TAG = "STOCK_ADJUSTMENTS";
    private static final String TAG_ADD_DIALOG = "add_stock_adjustment";
    private static final long CLICK_GUARD_MS = 700L;
    private EditText etSearchStockAdjustment;
    private final List<StockAdjustment> allData = new ArrayList<>();
    private String currentQuery = "";
    private SwipeRefreshLayout swipe;
    private ProgressBar progress;
    private View emptyState;
    private TextView tvEmpty;
    private TextView tvEmptySub;
    private RecyclerView rv;
    private FloatingActionButton fab;
    private ImageView btnBack;
    private ImageView ivHeaderAction;

    private StockAdjustmentsAdapter adapter;
    private InventoryOperationCacheRepository cacheRepository;
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
        cacheRepository = new InventoryOperationCacheRepository(requireContext());
        applyInsets(view);
        setupHeader();
        setupRecycler();
        setupSwipe();
        setupFab();
        setupSearch();

        loadProducts();
        load();
    }

    private void bindViews(@NonNull View view) {
        swipe = view.findViewById(R.id.swipe);
        progress = view.findViewById(R.id.progress);
        emptyState = view.findViewById(R.id.emptyState);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        tvEmptySub = view.findViewById(R.id.tvEmptySub);
        rv = view.findViewById(R.id.rv);
        fab = view.findViewById(R.id.fabAdd);
        btnBack = view.findViewById(R.id.btnBack);
        ivHeaderAction = view.findViewById(R.id.ivHeaderAction);
        etSearchStockAdjustment = view.findViewById(R.id.etSearchStockAdjustment);
    }

    private void setupSearch() {
        if (etSearchStockAdjustment == null) return;

        etSearchStockAdjustment.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                currentQuery = s == null ? "" : s.toString().trim();
                applyFilter();
            }
        });
    }

    private String buildSearchText(@NonNull StockAdjustment item) {
        StringBuilder sb = new StringBuilder();

        sb.append(String.valueOf(item));

        return sb.toString().toLowerCase();
    }

    private void applyFilter() {
        data.clear();

        if (TextUtils.isEmpty(currentQuery)) {
            data.addAll(allData);
        } else {
            String q = currentQuery.toLowerCase().trim();

            for (StockAdjustment item : allData) {
                String searchable = buildSearchText(item);
                if (searchable.contains(q)) {
                    data.add(item);
                }
            }
        }

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }

        updateEmptyState();
    }
    private void updateEmptyState() {
        boolean empty = data.isEmpty();
        boolean isSearching = !TextUtils.isEmpty(currentQuery);

        if (emptyState != null) {
            emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        }

        if (tvEmpty != null) {
            tvEmpty.setText(isSearching
                    ? getString(R.string.msg_no_matching_stock_adjustments)
                    : getString(R.string.msg_no_stock_adjustments_yet));
        }

        if (tvEmptySub != null) {
            tvEmptySub.setText(isSearching
                    ? getString(R.string.msg_no_matching_stock_adjustments_sub)
                    : getString(R.string.msg_no_stock_adjustments_yet_sub));
        }

        if (rv != null) {
            rv.setVisibility(empty ? View.GONE : View.VISIBLE);
        }
    }

    private void setLocalEmpty() {
        if (emptyState != null) {
            emptyState.setVisibility(View.VISIBLE);
        }
        if (tvEmpty != null) {
            tvEmpty.setText(InventoryOperationCacheRepository.NO_LOCAL_DATA_MESSAGE);
        }
        if (tvEmptySub != null) {
            tvEmptySub.setText("");
        }
        if (rv != null) {
            rv.setVisibility(View.GONE);
        }
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
        if (!NetworkUtils.isNetworkAvailable(requireContext())) {
            toast(InventoryOperationCacheRepository.INTERNET_REQUIRED_MESSAGE);
            return;
        }

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

        cacheRepository.loadStockAdjustmentsRoomFirst(new InventoryOperationCacheRepository.RoomFirstCallback<StockAdjustment>() {
            @Override
            public void onLocal(@NonNull List<StockAdjustment> list) {
                if (!isAdded() || list.isEmpty()) return;

                allData.clear();
                allData.addAll(list);
                applyFilter();
                showListLoading(false);
            }

            @Override
            public void onRemote(@NonNull List<StockAdjustment> list) {
                if (!isAdded()) return;

                isLoadingList = false;
                showListLoading(false);

                allData.clear();
                allData.addAll(list);

                applyFilter();
            }

            @Override
            public void onNoInternet(boolean hasLocalData) {
                if (!isAdded()) return;

                isLoadingList = false;
                showListLoading(false);

                if (!hasLocalData && allData.isEmpty()) {
                    setLocalEmpty();
                    toast(InventoryOperationCacheRepository.NO_LOCAL_DATA_MESSAGE);
                }
            }

            @Override
            public void onError(@NonNull String message, boolean hasLocalData) {
                if (!isAdded()) return;

                isLoadingList = false;
                showListLoading(false);

                if (!hasLocalData && allData.isEmpty()) {
                    setLocalEmpty();
                    showApiError(message.trim().isEmpty() ? "Failed to load stock adjustments" : message);
                }
            }
        });
    }

    private void loadProducts() {
        if (!isAdded()) return;
        if (isLoadingProducts) return;
        if (!NetworkUtils.isNetworkAvailable(requireContext())) {
            productsLoaded = false;
            productsJson = null;
            setFabEnabled(true);
            return;
        }

        isLoadingProducts = true;

        SessionManager sm = new SessionManager(requireContext());
        String url = ApiConfig.url(sm, "api/products/?track_stock=true");

        StringRequest req = new StringRequest(
                Request.Method.GET,
                url,
                response -> {
                    if (!isAdded()) return;

                    isLoadingProducts = false;
                    try {
                        productsJson = extractResultsArray(response);
                    } catch (Exception e) {
                        productsJson = null;
                    }
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
                    showApiError(msg);
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

    private static JSONArray extractResultsArray(String response) throws Exception {
        Object parsed = new JSONTokener(response == null ? "[]" : response).nextValue();

        if (parsed instanceof JSONArray) {
            return (JSONArray) parsed;
        }

        if (parsed instanceof JSONObject) {
            JSONArray results = ((JSONObject) parsed).optJSONArray("results");
            return results != null ? results : new JSONArray();
        }

        return new JSONArray();
    }

    private void showListLoading(boolean loading) {
        if (loading) {
            if (emptyState != null) {
                emptyState.setVisibility(View.GONE);
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
        emptyState = null;
        tvEmpty = null;
        tvEmptySub = null;
        rv = null;
        fab = null;
        btnBack = null;
        ivHeaderAction = null;
        etSearchStockAdjustment = null;
        adapter = null;
        cacheRepository = null;

        isAddDialogShowing = false;
        isLoadingList = false;
        isLoadingProducts = false;

        super.onDestroyView();
    }
}
