package com.example.valdker.ui.productreturns;

import android.content.Intent;
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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.valdker.R;
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

public class ProductReturnsFragment extends Fragment {

    private static final String TAG = "ProductReturnsFragment";

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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_product_returns, container, false);

        fabAdd = v.findViewById(R.id.fabAdd);
        fabAdd.setOnClickListener(view -> openAddDialog());

        swipe = v.findViewById(R.id.swipe);
        progress = v.findViewById(R.id.progress);
        tvEmpty = v.findViewById(R.id.tvEmpty);
        rv = v.findViewById(R.id.rv);

        InsetsHelper.applyRecyclerBottomInsets(v, rv, TAG);
        InsetsHelper.applyFabMarginInsets(fabAdd, 16, TAG);

        adapter = new ProductReturnAdapter(data, item -> {
            Intent i = new Intent(requireContext(), ProductReturnDetailActivity.class);
            i.putExtra(ProductReturnDetailActivity.EXTRA_DATA, item);
            startActivity(i);
        });

        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);

        swipe.setOnRefreshListener(this::load);

        load();
        preloadLiteData();

        return v;
    }

    private void openAddDialog() {

        if (ordersLite.isEmpty() || customersLite.isEmpty() || productsLite.isEmpty()) {
            Toast.makeText(requireContext(), "Loading spinner data...", Toast.LENGTH_SHORT).show();
            preloadLiteData();
            return;
        }

        ProductReturnAddDialog dlg = new ProductReturnAddDialog(
                ordersLite,
                this::fetchData
        );

        dlg.show(getChildFragmentManager(), "add_return");
    }

    private void fetchData() {
        load(); // refresh after create
    }

    private void preloadLiteData() {

        LiteRepository.fetchOrdersLite(requireContext(), new LiteRepository.LiteCallback<OrderLite>() {
            @Override
            public void onSuccess(@NonNull List<OrderLite> items) {
                if (!isAdded()) return;
                ordersLite.clear();
                ordersLite.addAll(items);
            }

            @Override
            public void onError(@NonNull String message) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });

        LiteRepository.fetchCustomersLite(requireContext(), new LiteRepository.LiteCallback<CustomerLite>() {
            @Override
            public void onSuccess(@NonNull List<CustomerLite> items) {
                if (!isAdded()) return;
                customersLite.clear();
                customersLite.addAll(items);
            }

            @Override
            public void onError(@NonNull String message) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });

        LiteRepository.fetchProductsLite(requireContext(), new LiteRepository.LiteCallback<ProductLite>() {
            @Override
            public void onSuccess(@NonNull List<ProductLite> items) {
                if (!isAdded()) return;
                productsLite.clear();
                productsLite.addAll(items);
            }

            @Override
            public void onError(@NonNull String message) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void load() {
        showLoading(true);

        ProductReturnRepository.fetchAll(requireContext(), new ProductReturnRepository.ListCallback() {
            @Override
            public void onSuccess(@NonNull List<ProductReturn> items) {
                if (!isAdded()) return;
                data.clear();
                data.addAll(items);
                adapter.notifyDataSetChanged();
                showLoading(false);
                updateEmpty();
            }

            @Override
            public void onError(@NonNull String message) {
                if (!isAdded()) return;
                showLoading(false);
                tvEmpty.setText(message);
                tvEmpty.setVisibility(View.VISIBLE);
            }
        });
    }

    private void showLoading(boolean loading) {
        swipe.setRefreshing(false);
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void updateEmpty() {
        if (data.isEmpty()) {
            tvEmpty.setText("No product returns yet.");
            tvEmpty.setVisibility(View.VISIBLE);
        } else {
            tvEmpty.setVisibility(View.GONE);
        }
    }
}