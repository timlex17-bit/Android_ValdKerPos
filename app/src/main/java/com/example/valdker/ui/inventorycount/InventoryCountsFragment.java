package com.example.valdker.ui.inventorycount;

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

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonArrayRequest;
import com.example.valdker.R;
import com.example.valdker.SessionManager;
import com.example.valdker.models.InventoryCount;
import com.example.valdker.network.ApiClient;
import com.example.valdker.network.ApiConfig;
import com.example.valdker.repositories.InventoryCountRepository;
import com.example.valdker.utils.InsetsHelper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InventoryCountsFragment extends Fragment {

    private SwipeRefreshLayout swipe;
    private ProgressBar progress;
    private TextView tvEmpty;
    private RecyclerView rv;
    private FloatingActionButton fab;

    private InventoryCountAdapter adapter;
    private final List<InventoryCount> data = new ArrayList<>();

    private JSONArray productsJson = null;

    private static final int TIMEOUT_MS = 20000;
    private static final int MAX_RETRIES = 1;
    private static final float BACKOFF_MULT = 1.2f;
    private static final long CLICK_GUARD_MS = 700L;

    private boolean isLoadingCounts = false;
    private boolean isLoadingProducts = false;
    private boolean isDeleteRunning = false;
    private boolean isDialogOpening = false;

    private long lastFabClickAt = 0L;
    private long lastRowActionAt = 0L;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_inventory_count_list, container, false);

        swipe = v.findViewById(R.id.swipe);
        progress = v.findViewById(R.id.progress);
        tvEmpty = v.findViewById(R.id.tvEmpty);
        rv = v.findViewById(R.id.rv);
        fab = v.findViewById(R.id.fabAdd);

        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setHasFixedSize(false);

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

        swipe.setOnRefreshListener(() -> {
            if (isLoadingCounts) {
                swipe.setRefreshing(false);
                return;
            }
            loadCounts();
        });

        fab.setEnabled(true);
        fab.setAlpha(1f);
        fab.post(() -> {
            if (fab == null) return;
            fab.bringToFront();
            fab.setElevation(100f);
            fab.setTranslationZ(100f);
        });

        fab.setOnClickListener(vv -> {
            if (!isAdded()) return;
            if (isRapidFabClick()) return;
            if (isDialogOpening) return;

            if (productsJson == null || productsJson.length() == 0) {
                toast("Loading products...");
                loadProducts(true, null);
                return;
            }
            openAddDialog();
        });

        loadProducts(false, null);
        loadCounts();
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

        InventoryCountFormDialog dialog = InventoryCountFormDialog.newAddInstance(
                productsJson != null ? productsJson.toString() : "[]"
        );

        dialog.setOnSavedListener(() -> {
            isDialogOpening = false;
            loadCounts();
        });

        dialog.setOnDismissListener(d -> isDialogOpening = false);

        dialog.show(getParentFragmentManager(), "add_inventory_count");
    }

    private void openEditDialog(@NonNull InventoryCount item) {
        if (!isAdded()) return;
        if (isDialogOpening) return;
        if (isStateSaved()) return;

        isDialogOpening = true;

        InventoryCountFormDialog dialog = InventoryCountFormDialog.newEditInstance(
                productsJson != null ? productsJson.toString() : "[]",
                item
        );

        dialog.setOnSavedListener(() -> {
            isDialogOpening = false;
            loadCounts();
        });

        dialog.setOnDismissListener(d -> isDialogOpening = false);

        dialog.show(getParentFragmentManager(), "edit_inventory_count");
    }

    private void loadCounts() {
        if (!isAdded()) return;
        if (isLoadingCounts) return;

        isLoadingCounts = true;

        tvEmpty.setVisibility(View.GONE);
        if (!swipe.isRefreshing()) {
            progress.setVisibility(View.VISIBLE);
        }

        InventoryCountRepository.fetch(requireContext(), new InventoryCountRepository.Callback() {
            @Override
            public void onSuccess(@NonNull List<InventoryCount> list) {
                if (!isAdded()) return;

                isLoadingCounts = false;
                progress.setVisibility(View.GONE);
                swipe.setRefreshing(false);

                data.clear();
                data.addAll(list);
                adapter.notifyDataSetChanged();

                tvEmpty.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(@NonNull String message) {
                if (!isAdded()) return;

                isLoadingCounts = false;
                progress.setVisibility(View.GONE);
                swipe.setRefreshing(false);

                toast(message);
                tvEmpty.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
            }
        });
    }

    private void loadProducts(boolean openDialogAfterLoad, @Nullable InventoryCount editItem) {
        if (!isAdded()) return;
        if (isLoadingProducts) return;

        isLoadingProducts = true;
        setFabLoading(true);

        SessionManager sm = new SessionManager(requireContext());
        String url = ApiConfig.url(sm, "api/products/");

        JsonArrayRequest req = new JsonArrayRequest(
                Request.Method.GET,
                url,
                null,
                response -> {
                    if (!isAdded()) return;

                    isLoadingProducts = false;
                    setFabLoading(false);
                    productsJson = response;

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
                    if (!isAdded()) return;

                    isLoadingProducts = false;
                    setFabLoading(false);
                    productsJson = null;

                    String msg = "Failed load products";
                    if (error != null && error.networkResponse != null) {
                        msg += " (" + error.networkResponse.statusCode + ")";
                    }
                    toast(msg);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                SessionManager sm = new SessionManager(requireContext());
                String token = sm.getToken();

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

    private void deleteItem(@NonNull InventoryCount item) {
        if (!isAdded()) return;
        if (isDeleteRunning) return;

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete")
                .setMessage("Delete \"" + item.title + "\"?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> {
                    if (!isAdded()) return;
                    if (isDeleteRunning) return;

                    isDeleteRunning = true;

                    InventoryCountRepository.delete(requireContext(), item.id,
                            new InventoryCountRepository.DeleteCallback() {
                                @Override
                                public void onSuccess() {
                                    if (!isAdded()) return;

                                    isDeleteRunning = false;
                                    toast("Deleted");
                                    loadCounts();
                                }

                                @Override
                                public void onError(@NonNull String message) {
                                    if (!isAdded()) return;

                                    isDeleteRunning = false;
                                    toast(message);
                                }
                            });
                })
                .show();
    }

    private void setFabLoading(boolean loading) {
        if (fab == null) return;
        fab.setEnabled(!loading);
        fab.setAlpha(loading ? 0.65f : 1f);
    }

    private void toast(@NonNull String message) {
        if (!isAdded()) return;
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        swipe = null;
        progress = null;
        tvEmpty = null;
        rv = null;
        fab = null;
    }
}