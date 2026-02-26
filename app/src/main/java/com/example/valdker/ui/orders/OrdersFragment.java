package com.example.valdker.ui.orders;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.valdker.R;
import com.example.valdker.SessionManager;
import com.example.valdker.models.Order;
import com.example.valdker.repositories.OrderRepository;
import com.example.valdker.utils.InsetsHelper;

import java.util.ArrayList;
import java.util.List;

public class OrdersFragment extends Fragment {

    private static final String TAG = "OrdersFragment";

    private RecyclerView rvOrders;
    private TextView tvEmpty;
    private ProgressBar progress;

    private OrdersAdapter adapter;

    public OrdersFragment() {
        super(R.layout.fragment_orders);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvOrders = view.findViewById(R.id.rvOrders);
        tvEmpty = view.findViewById(R.id.tvEmptyOrders);
        progress = view.findViewById(R.id.progressOrders);

        // ✅ Null-safe logs (helps if layout-land missing ids)
        if (rvOrders == null) Log.w(TAG, "rvOrders not found (check fragment_orders.xml / layout-land).");
        if (tvEmpty == null) Log.w(TAG, "tvEmptyOrders not found.");
        if (progress == null) Log.w(TAG, "progressOrders not found.");

        // ✅ Use centralized InsetsHelper (production-safe)
        InsetsHelper.applySystemBarsPadding(view, rvOrders, TAG);

        adapter = new OrdersAdapter(new ArrayList<>(), order -> {
            String inv = order.getInvoiceNumber();
            if (inv == null || inv.trim().isEmpty()) {
                inv = String.valueOf(order.getId());
            }

            if (isAdded()) {
                Toast.makeText(
                        requireContext(),
                        "Invoice: " + inv + " (" + order.getItemsCount() + " items)",
                        Toast.LENGTH_SHORT
                ).show();
            }
        });

        if (rvOrders != null) {
            rvOrders.setLayoutManager(new LinearLayoutManager(requireContext()));
            rvOrders.setAdapter(adapter);
        }

        fetch();
    }

    @Override
    public void onResume() {
        super.onResume();
        fetch();
    }

    private void fetch() {
        if (!isAdded()) return;

        setLoading(true);

        SessionManager session = new SessionManager(requireContext());
        String token = session.getToken();

        Log.d(TAG, "fetch() token=" + mask(token));

        if (token == null || token.trim().isEmpty()) {
            setLoading(false);
            showEmpty(true);

            Toast.makeText(
                    requireContext(),
                    "Token la existe. Favor Login fali.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        new OrderRepository(requireContext()).fetchOrders(token, new OrderRepository.Callback() {

            @Override
            public void onSuccess(List<Order> orders) {
                if (!isAdded()) return;

                setLoading(false);

                if (adapter != null) {
                    adapter.setData(orders);
                }

                boolean empty = (orders == null || orders.isEmpty());
                showEmpty(empty);

                Log.i(TAG, "Loaded orders: " + (orders != null ? orders.size() : 0));
            }

            @Override
            public void onError(int statusCode, String message) {
                if (!isAdded()) return;

                setLoading(false);
                showEmpty(true);

                Log.e(TAG, "Failed (" + statusCode + "): " + message);

                Toast.makeText(
                        requireContext(),
                        "Failed to load orders (" + statusCode + "): " + message,
                        Toast.LENGTH_LONG
                ).show();
            }
        });
    }

    private String mask(String token) {
        if (token == null) return "null";
        String t = token.trim();
        if (t.length() <= 8) return t;
        return t.substring(0, 4) + "..." + t.substring(t.length() - 4);
    }

    private void setLoading(boolean loading) {
        if (progress != null) {
            progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (rvOrders != null) {
            rvOrders.setVisibility(loading ? View.GONE : View.VISIBLE);
        }
        if (tvEmpty != null) {
            tvEmpty.setVisibility(View.GONE);
        }
    }

    private void showEmpty(boolean empty) {
        if (tvEmpty != null) {
            tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        }
        if (rvOrders != null) {
            rvOrders.setVisibility(empty ? View.GONE : View.VISIBLE);
        }
    }
}