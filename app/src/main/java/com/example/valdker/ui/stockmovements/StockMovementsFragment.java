package com.example.valdker.ui.stockmovements;

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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.valdker.R;
import com.example.valdker.base.BaseFragment;
import com.example.valdker.models.StockMovement;
import com.example.valdker.repositories.StockMovementRepository;

import java.util.ArrayList;
import java.util.List;

public class StockMovementsFragment extends BaseFragment {

    private static final long CLICK_GUARD_MS = 700L;

    private SwipeRefreshLayout swipe;
    private ProgressBar progress;
    private TextView tvEmpty;
    private RecyclerView rv;
    private ImageView btnBack;
    private ImageView ivHeaderAction;

    private StockMovementsAdapter adapter;
    private final List<StockMovement> data = new ArrayList<>();

    private boolean isLoading = false;
    private long lastRowClickAt = 0L;
    private long lastRefreshClickAt = 0L;

    public StockMovementsFragment() {
        super(R.layout.fragment_stock_movements);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        applyTopInset(view.findViewById(R.id.topBar));

        bindViews(view);
        setupHeader();
        setupRecycler();
        setupSwipe();

        load();
    }

    private void bindViews(@NonNull View view) {
        swipe = view.findViewById(R.id.swipe);
        progress = view.findViewById(R.id.progress);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        rv = view.findViewById(R.id.rv);
        btnBack = view.findViewById(R.id.btnBack);
        ivHeaderAction = view.findViewById(R.id.ivHeaderAction);
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

        adapter = new StockMovementsAdapter(data, item -> {
            if (!canRunRowClick()) return;
            if (!isAdded()) return;
            StockMovementDetailActivity.open(requireContext(), item);
        });

        rv.setAdapter(adapter);
    }

    private void setupSwipe() {
        if (swipe == null) return;

        swipe.setOnRefreshListener(() -> {
            if (isLoading) {
                swipe.setRefreshing(false);
                return;
            }
            load();
        });
    }

    private boolean canRunRowClick() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastRowClickAt < CLICK_GUARD_MS) {
            return false;
        }
        lastRowClickAt = now;
        return true;
    }

    private boolean isRapidRefreshClick() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastRefreshClickAt < CLICK_GUARD_MS) {
            return true;
        }
        lastRefreshClickAt = now;
        return false;
    }

    private void load() {
        if (!isAdded()) return;
        if (isLoading) return;

        isLoading = true;

        if (tvEmpty != null) {
            tvEmpty.setVisibility(View.GONE);
        }

        if (swipe != null && !swipe.isRefreshing() && progress != null) {
            progress.setVisibility(View.VISIBLE);
        }

        StockMovementRepository.fetch(requireContext(), new StockMovementRepository.Callback() {
            @Override
            public void onSuccess(List<StockMovement> list) {
                if (!isAdded()) return;

                isLoading = false;

                if (progress != null) progress.setVisibility(View.GONE);
                if (swipe != null) swipe.setRefreshing(false);

                data.clear();
                if (list != null) {
                    data.addAll(list);
                }

                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }

                if (tvEmpty != null) {
                    tvEmpty.setText(getString(R.string.msg_no_stock_movements));
                    tvEmpty.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
                }
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;

                isLoading = false;

                if (progress != null) progress.setVisibility(View.GONE);
                if (swipe != null) swipe.setRefreshing(false);

                Toast.makeText(
                        requireContext(),
                        message == null || message.trim().isEmpty()
                                ? getString(R.string.msg_failed_load_stock_movements)
                                : message,
                        Toast.LENGTH_LONG
                ).show();

                if (tvEmpty != null) {
                    tvEmpty.setText(getString(R.string.msg_no_stock_movements));
                    tvEmpty.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
                }
            }
        });
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
        btnBack = null;
        ivHeaderAction = null;
        adapter = null;

        super.onDestroyView();
    }
}