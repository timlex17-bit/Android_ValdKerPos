package com.example.valdker.ui.inventorycount;

import android.os.Bundle;
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

    // products for spinner
    private JSONArray productsJson = null;

    private static final int TIMEOUT_MS = 20000;
    private static final int MAX_RETRIES = 1;
    private static final float BACKOFF_MULT = 1.2f;

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

        InsetsHelper.applyFabMarginInsets(fab, 18, "InventoryCount");

        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new InventoryCountAdapter(
                data,
                // onClick row -> detail
                item -> InventoryCountDetailActivity.open(requireContext(), item),
                // onEdit -> open dialog edit
                item -> {
                    if (productsJson == null || productsJson.length() == 0) {
                        Toast.makeText(requireContext(), "Loading products...", Toast.LENGTH_SHORT).show();
                        loadProducts(true, item);
                        return;
                    }
                    openEditDialog(item);
                },
                // onDelete -> delete
                item -> deleteItem(item)
        );
        rv.setAdapter(adapter);

        swipe.setOnRefreshListener(this::loadCounts);

        fab.setEnabled(true);
        fab.setAlpha(1f);
        fab.post(() -> {
            fab.bringToFront();
            fab.setElevation(100f);
            fab.setTranslationZ(100f);
        });

        fab.setOnClickListener(vv -> {
            if (!isAdded()) return;

            if (productsJson == null || productsJson.length() == 0) {
                Toast.makeText(requireContext(), "Loading products...", Toast.LENGTH_SHORT).show();
                loadProducts(true, null);
                return;
            }
            openAddDialog();
        });

        loadProducts(false, null);
        loadCounts();
        return v;
    }

    private void openAddDialog() {
        if (!isAdded()) return;

        InventoryCountFormDialog dialog = InventoryCountFormDialog.newAddInstance(
                productsJson != null ? productsJson.toString() : "[]"
        );
        dialog.setOnSavedListener(this::loadCounts);
        dialog.show(getParentFragmentManager(), "add_inventory_count");
    }

    private void openEditDialog(@NonNull InventoryCount item) {
        if (!isAdded()) return;

        InventoryCountFormDialog dialog = InventoryCountFormDialog.newEditInstance(
                productsJson != null ? productsJson.toString() : "[]",
                item
        );
        dialog.setOnSavedListener(this::loadCounts);
        dialog.show(getParentFragmentManager(), "edit_inventory_count");
    }

    private void loadCounts() {
        if (!isAdded()) return;

        tvEmpty.setVisibility(View.GONE);
        if (!swipe.isRefreshing()) progress.setVisibility(View.VISIBLE);

        InventoryCountRepository.fetch(requireContext(), new InventoryCountRepository.Callback() {
            @Override
            public void onSuccess(@NonNull List<InventoryCount> list) {
                if (!isAdded()) return;

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

                progress.setVisibility(View.GONE);
                swipe.setRefreshing(false);

                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                tvEmpty.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
            }
        });
    }

    /**
     * @param openDialogAfterLoad kalau true: setelah products berhasil load, buka dialog add/edit
     * @param editItem optional: kalau tidak null, berarti yang dimau edit
     */
    private void loadProducts(boolean openDialogAfterLoad, @Nullable InventoryCount editItem) {
        if (!isAdded()) return;

        SessionManager sm = new SessionManager(requireContext());
        String url = ApiConfig.url(sm, "api/products/");

        JsonArrayRequest req = new JsonArrayRequest(
                Request.Method.GET,
                url,
                null,
                response -> {
                    if (!isAdded()) return;

                    productsJson = response;

                    if (productsJson == null || productsJson.length() == 0) {
                        Toast.makeText(requireContext(), "Products are empty", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (openDialogAfterLoad) {
                        if (editItem != null) openEditDialog(editItem);
                        else openAddDialog();
                    }
                },
                error -> {
                    if (!isAdded()) return;

                    productsJson = null;

                    String msg = "Failed load products";
                    if (error != null && error.networkResponse != null) {
                        msg += " (" + error.networkResponse.statusCode + ")";
                    }
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
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

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete")
                .setMessage("Delete \"" + item.title + "\"?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> {
                    InventoryCountRepository.delete(requireContext(), item.id, new InventoryCountRepository.DeleteCallback() {
                        @Override
                        public void onSuccess() {
                            if (!isAdded()) return;
                            Toast.makeText(requireContext(), "Deleted", Toast.LENGTH_SHORT).show();
                            loadCounts();
                        }

                        @Override
                        public void onError(@NonNull String message) {
                            if (!isAdded()) return;
                            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .show();
    }
}