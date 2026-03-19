package com.example.valdker.ui.stockadjustments;

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
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonArrayRequest;
import com.example.valdker.R;
import com.example.valdker.SessionManager;
import com.example.valdker.models.StockAdjustment;
import com.example.valdker.network.ApiClient;
import com.example.valdker.network.ApiConfig;
import com.example.valdker.repositories.StockAdjustmentRepository;
import com.example.valdker.utils.InsetsHelper;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StockAdjustmentsFragment extends Fragment {

    private static final String TAG = "StockAdjustmentsFragment";
    private static final String TAG_ADD_DIALOG = "add_stock_adjustment";
    private static final long FAB_CLICK_DELAY_MS = 700L;

    private SwipeRefreshLayout swipe;
    private ProgressBar progress;
    private TextView tvEmpty;
    private RecyclerView rv;
    private FloatingActionButton fab;

    private StockAdjustmentsAdapter adapter;
    private final List<StockAdjustment> data = new ArrayList<>();

    private JSONArray productsJson = null;
    private boolean productsLoaded = false;

    private long lastFabClickTime = 0L;
    private boolean isAddDialogShowing = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_stock_adjustments, container, false);

        swipe = v.findViewById(R.id.swipe);
        progress = v.findViewById(R.id.progress);
        tvEmpty = v.findViewById(R.id.tvEmpty);
        rv = v.findViewById(R.id.rv);
        fab = v.findViewById(R.id.fabAdd);

        InsetsHelper.applyRecyclerBottomInsets(v, rv, TAG);
        // Jangan pakai ini supaya posisi FAB konsisten seperti fragment lain
        // InsetsHelper.applyFabMarginInsets(fab, 16, TAG);

        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new StockAdjustmentsAdapter(data, item ->
                StockAdjustmentDetailActivity.open(requireContext(), item)
        );
        rv.setAdapter(adapter);

        swipe.setOnRefreshListener(() -> {
            load();
            // loadProducts();
        });

        fab.setEnabled(false);
        fab.setAlpha(0.4f);
        fab.setOnClickListener(view -> openAddDialogSafely());

        loadProducts();
        load();

        return v;
    }

    private void openAddDialogSafely() {
        if (!isAdded()) return;
        if (!productsLoaded || productsJson == null || productsJson.length() == 0) {
            Toast.makeText(requireContext(), "Product list not loaded yet", Toast.LENGTH_SHORT).show();
            return;
        }
        if (isAddDialogShowing) return;

        long now = System.currentTimeMillis();
        if (now - lastFabClickTime < FAB_CLICK_DELAY_MS) {
            return;
        }
        lastFabClickTime = now;

        FragmentManager fm = getChildFragmentManager();
        if (fm.findFragmentByTag(TAG_ADD_DIALOG) != null) {
            return;
        }

        isAddDialogShowing = true;

        if (fab != null) {
            fab.setEnabled(false);
            fab.setAlpha(0.4f);
            fab.postDelayed(() -> {
                if (fab != null && isAdded() && !isAddDialogShowing) {
                    fab.setEnabled(productsLoaded);
                    fab.setAlpha(productsLoaded ? 1f : 0.4f);
                }
            }, FAB_CLICK_DELAY_MS);
        }

        StockAdjustmentFormDialog dialog =
                StockAdjustmentFormDialog.create(productsJson, this::load);

        dialog.show(fm, TAG_ADD_DIALOG);

        fm.executePendingTransactions();
        Fragment fragment = fm.findFragmentByTag(TAG_ADD_DIALOG);
        if (fragment instanceof StockAdjustmentFormDialog) {
            ((StockAdjustmentFormDialog) fragment).getDialog().setOnDismissListener(d -> {
                isAddDialogShowing = false;
                if (fab != null && isAdded()) {
                    fab.postDelayed(() -> {
                        if (fab != null && isAdded()) {
                            fab.setEnabled(productsLoaded);
                            fab.setAlpha(productsLoaded ? 1f : 0.4f);
                        }
                    }, 180L);
                }
            });
        } else {
            isAddDialogShowing = false;
            if (fab != null && isAdded()) {
                fab.setEnabled(productsLoaded);
                fab.setAlpha(productsLoaded ? 1f : 0.4f);
            }
        }
    }

    private void load() {
        if (!isAdded()) return;

        tvEmpty.setVisibility(View.GONE);
        if (!swipe.isRefreshing()) progress.setVisibility(View.VISIBLE);

        StockAdjustmentRepository.fetch(requireContext(), new StockAdjustmentRepository.ListCallback() {
            @Override
            public void onSuccess(List<StockAdjustment> list) {
                if (!isAdded()) return;

                progress.setVisibility(View.GONE);
                swipe.setRefreshing(false);

                data.clear();
                data.addAll(list);
                adapter.notifyDataSetChanged();

                tvEmpty.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;

                progress.setVisibility(View.GONE);
                swipe.setRefreshing(false);

                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                tvEmpty.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
            }
        });
    }

    private void loadProducts() {
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
                    productsLoaded = (productsJson != null && productsJson.length() > 0);

                    if (fab != null && !isAddDialogShowing) {
                        fab.setEnabled(productsLoaded);
                        fab.setAlpha(productsLoaded ? 1f : 0.4f);
                    }

                    if (!productsLoaded) {
                        Toast.makeText(requireContext(), "Products are empty", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    if (!isAdded()) return;

                    productsLoaded = false;
                    productsJson = null;

                    if (fab != null) {
                        fab.setEnabled(false);
                        fab.setAlpha(0.4f);
                    }

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
                    headers.put("Authorization", "Token " + token);
                }
                return headers;
            }
        };

        req.setShouldCache(false);
        ApiClient.getInstance(requireContext()).add(req);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        swipe = null;
        progress = null;
        tvEmpty = null;
        rv = null;
        fab = null;
        isAddDialogShowing = false;
    }
}