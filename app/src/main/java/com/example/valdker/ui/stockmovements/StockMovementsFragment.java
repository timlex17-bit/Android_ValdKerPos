package com.example.valdker.ui.stockmovements;

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

import com.example.valdker.R;
import com.example.valdker.models.StockMovement;
import com.example.valdker.repositories.StockMovementRepository;
import com.example.valdker.utils.InsetsHelper;

import java.util.ArrayList;
import java.util.List;

public class StockMovementsFragment extends Fragment {

    private SwipeRefreshLayout swipe;
    private ProgressBar progress;
    private TextView tvEmpty;
    private RecyclerView rv;

    private StockMovementsAdapter adapter;
    private final List<StockMovement> data = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_stock_movements, container, false);

        swipe = v.findViewById(R.id.swipe);
        progress = v.findViewById(R.id.progress);
        tvEmpty = v.findViewById(R.id.tvEmpty);
        rv = v.findViewById(R.id.rv);

        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new StockMovementsAdapter(data, item ->
                StockMovementDetailActivity.open(requireContext(), item)
        );
        rv.setAdapter(adapter);

        // ✅ Apply safe bottom inset so list is not covered by navigation bar
        InsetsHelper.applyRecyclerBottomInsets(v, rv);

        swipe.setOnRefreshListener(this::load);

        load();
        return v;
    }

    private void load() {
        tvEmpty.setVisibility(View.GONE);

        if (!swipe.isRefreshing()) {
            progress.setVisibility(View.VISIBLE);
        }

        StockMovementRepository.fetch(requireContext(), new StockMovementRepository.Callback() {
            @Override
            public void onSuccess(List<StockMovement> list) {
                if (!isAdded()) return;

                progress.setVisibility(View.GONE);
                swipe.setRefreshing(false);

                data.clear();
                data.addAll(list);
                adapter.notifyDataSetChanged();

                if (data.isEmpty()) {
                    tvEmpty.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;

                progress.setVisibility(View.GONE);
                swipe.setRefreshing(false);

                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();

                if (data.isEmpty()) {
                    tvEmpty.setVisibility(View.VISIBLE);
                }
            }
        });
    }
}