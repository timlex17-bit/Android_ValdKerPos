package com.valdker.pos.ui.inventorycount;

import android.os.Bundle;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedDispatcher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonArrayRequest;
import com.valdker.pos.R;
import com.valdker.pos.SessionManager;
import com.valdker.pos.base.BaseFragment;
import com.valdker.pos.models.InventoryCount;
import com.valdker.pos.network.ApiClient;
import com.valdker.pos.network.ApiConfig;
import com.valdker.pos.repositories.InventoryCountRepository;
import com.valdker.pos.utils.InsetsHelper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class InventoryCountsFragment extends BaseFragment {

    private static final int TIMEOUT_MS = 20000;
    private static final int MAX_RETRIES = 1;
    private static final float BACKOFF_MULT = 1.2f;
    private static final long CLICK_GUARD_MS = 700L;
    private static final String TAG = "INVENTORY_COUNTS";

    private SwipeRefreshLayout swipe;
    private ProgressBar progress;
    private TextView tvEmpty;
    private TextView tvEmptySub;
    private View emptyState;
    private RecyclerView rv;
    private FloatingActionButton fab;
    private EditText etSearch;
    private ImageView btnBack;
    private ImageView ivHeaderAction;

    private InventoryCountAdapter adapter;

    private final List<InventoryCount> data = new ArrayList<>();
    private final List<InventoryCount> allData = new ArrayList<>();

    private JSONArray productsJson = null;
    private String currentQuery = "";

    private boolean isLoadingCounts = false;
    private boolean isLoadingProducts = false;
    private boolean isDeleteRunning = false;
    private boolean isDialogOpening = false;

    private long lastFabClickAt = 0L;
    private long lastRowActionAt = 0L;
    private long lastRefreshClickAt = 0L;

    public InventoryCountsFragment() {
        super(R.layout.fragment_inventory_count_list);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        applyTopInset(view.findViewById(R.id.topBar));

        bindViews(view);
        applyInsets(view);
        setupHeader();
        setupRecycler();
        setupSearch();
        setupSwipe();
        setupFab();

        loadProducts(false, null);
        loadCounts();
    }

    private void bindViews(@NonNull View view) {
        swipe = view.findViewById(R.id.swipe);
        progress = view.findViewById(R.id.progress);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        tvEmptySub = view.findViewById(R.id.tvEmptySub);
        emptyState = view.findViewById(R.id.emptyState);
        rv = view.findViewById(R.id.rv);
        fab = view.findViewById(R.id.fabAdd);
        etSearch = view.findViewById(R.id.etSearch);
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
                if (isLoadingCounts) return;

                if (swipe != null && !swipe.isRefreshing()) {
                    swipe.setRefreshing(true);
                }
                loadCounts();
            });
        }
    }

    private void setupRecycler() {
        if (rv == null) return;

        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setHasFixedSize(false);
        rv.setClipToPadding(false);

        adapter = new InventoryCountAdapter(
                data,
                item -> {
                    if (!canRunRowAction()) return;
                    if (!isAdded()) return;
                    InventoryCountDetailActivity.open(requireContext(), item);
                },
                item -> {
                    if (!canRunRowAction()) return;
                    if (!isAdded()) return;

                    if (productsJson == null || productsJson.length() == 0) {
                        toast("Loading products...");
                        loadProducts(true, item);
                        return;
                    }
                    openEditDialog(item);
                },
                item -> {
                    if (!canRunRowAction()) return;
                    if (!isAdded()) return;
                    deleteItem(item);
                }
        );

        rv.setAdapter(adapter);
    }

    private void setupSearch() {
        if (etSearch == null) return;

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentQuery = s == null ? "" : s.toString().trim();
                applyFilter();
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });
    }

    private void setupSwipe() {
        if (swipe == null) return;

        swipe.setOnRefreshListener(() -> {
            if (isLoadingCounts) {
                if (swipe != null) swipe.setRefreshing(false);
                return;
            }
            loadCounts();
        });
    }

    private void setupFab() {
        if (fab == null) return;

        setFabEnabled(true);

        fab.post(() -> {
            if (fab == null) return;
            fab.bringToFront();
            fab.setElevation(100f);
            fab.setTranslationZ(100f);
        });

        fab.setOnClickListener(v -> {
            if (!isAdded()) return;
            if (isRapidFabClick()) return;
            if (isDialogOpening) return;
            if (isLoadingCounts) return;

            if (productsJson == null || productsJson.length() == 0) {
                toast("Loading products...");
                loadProducts(true, null);
                return;
            }
            openAddDialog();
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
        if (now - lastRowActionAt < CLICK_GUARD_MS) {
            return false;
        }
        lastRowActionAt = now;
        return true;
    }

    private void openAddDialog() {
        if (!isAdded()) return;
        if (isDialogOpening) return;
        if (isStateSaved()) return;

        isDialogOpening = true;
        setFabEnabled(false);

        InventoryCountFormDialog dialog = InventoryCountFormDialog.newAddInstance(
                productsJson != null ? productsJson.toString() : "[]"
        );

        dialog.setOnSavedListener(() -> {
            isDialogOpening = false;
            setFabEnabled(true);
            loadCounts();
        });

        dialog.setOnDismissListener(d -> {
            isDialogOpening = false;
            setFabEnabled(true);
        });

        dialog.show(getParentFragmentManager(), "add_inventory_count");
    }

    private void openEditDialog(@NonNull InventoryCount item) {
        if (!isAdded()) return;
        if (isDialogOpening) return;
        if (isStateSaved()) return;

        isDialogOpening = true;
        setFabEnabled(false);

        InventoryCountFormDialog dialog = InventoryCountFormDialog.newEditInstance(
                productsJson != null ? productsJson.toString() : "[]",
                item
        );

        dialog.setOnSavedListener(() -> {
            isDialogOpening = false;
            setFabEnabled(true);
            loadCounts();
        });

        dialog.setOnDismissListener(d -> {
            isDialogOpening = false;
            setFabEnabled(true);
        });

        dialog.show(getParentFragmentManager(), "edit_inventory_count");
    }

    private void loadCounts() {
        if (!isAdded()) return;
        if (isLoadingCounts) return;

        isLoadingCounts = true;
        showCountsLoading(true);

        InventoryCountRepository.fetch(requireContext(), new InventoryCountRepository.Callback() {
            @Override
            public void onSuccess(@NonNull List<InventoryCount> list) {
                isLoadingCounts = false;
                if (!isAdded()) return;

                showCountsLoading(false);

                allData.clear();
                allData.addAll(list);
                applyFilter();
            }

            @Override
            public void onError(@NonNull String message) {
                isLoadingCounts = false;
                if (!isAdded()) return;

                showCountsLoading(false);
                toast(message);
                updateEmptyState();
            }
        });
    }

    private void loadProducts(boolean openDialogAfterLoad, @Nullable InventoryCount editItem) {
        if (!isAdded()) return;
        if (isLoadingProducts) return;

        isLoadingProducts = true;
        setFabEnabled(false);

        SessionManager sm = new SessionManager(requireContext());
        String url = ApiConfig.url(sm, "api/products/?track_stock=true");

        JsonArrayRequest req = new JsonArrayRequest(
                Request.Method.GET,
                url,
                null,
                response -> {
                    isLoadingProducts = false;
                    if (!isAdded()) return;

                    productsJson = response;
                    setFabEnabled(true);

                    if (productsJson == null || productsJson.length() == 0) {
                        toast("Products are empty");
                        return;
                    }

                    if (openDialogAfterLoad) {
                        if (editItem != null) {
                            openEditDialog(editItem);
                        } else {
                            openAddDialog();
                        }
                    }
                },
                error -> {
                    isLoadingProducts = false;
                    if (!isAdded()) return;

                    productsJson = null;
                    setFabEnabled(true);

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
        req.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, MAX_RETRIES, BACKOFF_MULT));
        ApiClient.getInstance(requireContext()).add(req);
    }

    private void applyFilter() {
        data.clear();

        if (allData.isEmpty()) {
            notifyAdapterChanged();
            updateEmptyState();
            return;
        }

        String query = currentQuery == null ? "" : currentQuery.trim().toLowerCase(Locale.getDefault());

        if (query.isEmpty()) {
            data.addAll(allData);
        } else {
            for (InventoryCount item : allData) {
                if (item == null) continue;

                String title = item.title == null ? "" : item.title.trim().toLowerCase(Locale.getDefault());
                if (title.contains(query)) {
                    data.add(item);
                }
            }
        }

        notifyAdapterChanged();
        updateEmptyState();
    }

    private void notifyAdapterChanged() {
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void updateEmptyState() {
        boolean showEmpty = data.isEmpty();

        if (emptyState != null) {
            emptyState.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
        } else if (tvEmpty != null) {
            tvEmpty.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
        }

        if (tvEmpty != null) {
            tvEmpty.setText(
                    (currentQuery == null || currentQuery.trim().isEmpty())
                            ? "No inventory counts found"
                            : "No matching inventory counts"
            );
        }

        if (tvEmptySub != null) {
            tvEmptySub.setText(
                    (currentQuery == null || currentQuery.trim().isEmpty())
                            ? "Tap + to create a new inventory count."
                            : "Try another keyword."
            );
        }
    }

    private void deleteItem(@NonNull InventoryCount item) {
        if (!isAdded()) return;
        if (isDeleteRunning) return;

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete")
                .setMessage("Delete \"" + safeText(item.title) + "\"?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> {
                    if (!isAdded()) return;
                    if (isDeleteRunning) return;

                    isDeleteRunning = true;
                    setFabEnabled(false);

                    InventoryCountRepository.delete(
                            requireContext(),
                            item.id,
                            new InventoryCountRepository.DeleteCallback() {
                                @Override
                                public void onSuccess() {
                                    isDeleteRunning = false;
                                    if (!isAdded()) return;

                                    toast("Deleted");
                                    setFabEnabled(true);
                                    loadCounts();
                                }

                                @Override
                                public void onError(@NonNull String message) {
                                    isDeleteRunning = false;
                                    if (!isAdded()) return;

                                    toast(message);
                                    setFabEnabled(true);
                                }
                            }
                    );
                })
                .show();
    }

    private void showCountsLoading(boolean loading) {
        if (loading) {
            if (emptyState != null) {
                emptyState.setVisibility(View.GONE);
            } else if (tvEmpty != null) {
                tvEmpty.setVisibility(View.GONE);
            }
            if (swipe != null && !swipe.isRefreshing() && progress != null) {
                progress.setVisibility(View.VISIBLE);
            }
        } else {
            if (progress != null) progress.setVisibility(View.GONE);
            if (swipe != null) swipe.setRefreshing(false);
        }

        if (ivHeaderAction != null) {
            boolean enabled = !loading;
            ivHeaderAction.setEnabled(enabled);
            ivHeaderAction.setAlpha(enabled ? 1f : 0.5f);
        }

        setFabEnabled(true);
    }

    private void setFabEnabled(boolean enabled) {
        if (fab == null) return;
        boolean finalEnabled = enabled && !isDialogOpening && !isLoadingCounts && !isLoadingProducts && !isDeleteRunning;
        fab.setEnabled(finalEnabled);
        fab.setAlpha(finalEnabled ? 1f : 0.65f);
    }

    @NonNull
    private String safeText(@Nullable String value) {
        return value == null || value.trim().isEmpty() ? "-" : value.trim();
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
        tvEmptySub = null;
        emptyState = null;
        rv = null;
        fab = null;
        etSearch = null;
        btnBack = null;
        ivHeaderAction = null;
        adapter = null;

        isLoadingCounts = false;
        isLoadingProducts = false;
        isDeleteRunning = false;
        isDialogOpening = false;

        super.onDestroyView();
    }
}