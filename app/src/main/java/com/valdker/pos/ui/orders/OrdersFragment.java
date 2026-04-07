package com.valdker.pos.ui.orders;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
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

import com.valdker.pos.R;
import com.valdker.pos.SessionManager;
import com.valdker.pos.base.BaseFragment;
import com.valdker.pos.models.Order;
import com.valdker.pos.repositories.OrderRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OrdersFragment extends BaseFragment {

    private static final String TAG = "OrdersFragment";

    private SwipeRefreshLayout swipeOrders;
    private RecyclerView rvOrders;
    private TextView tvEmpty;
    private ProgressBar progress;
    private EditText etSearchOrders;
    private ImageView btnBack;
    private ImageView ivHeaderAction;

    private OrdersAdapter adapter;

    private final List<Order> allOrders = new ArrayList<>();
    private final List<Order> filteredOrders = new ArrayList<>();

    private boolean isLoading = false;
    private String currentQuery = "";

    public OrdersFragment() {
        super(R.layout.fragment_orders);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        applyTopInset(view.findViewById(R.id.topBar));

        bindViews(view);
        setupHeader();
        setupRecycler();
        setupSearch();
        setupSwipe();

        fetch();
    }

    @Override
    public void onResume() {
        super.onResume();
        fetch();
    }

    private void bindViews(@NonNull View view) {
        swipeOrders = view.findViewById(R.id.swipeOrders);
        rvOrders = view.findViewById(R.id.rvOrders);
        tvEmpty = view.findViewById(R.id.tvEmptyOrders);
        progress = view.findViewById(R.id.progressOrders);
        etSearchOrders = view.findViewById(R.id.etSearchOrders);
        btnBack = view.findViewById(R.id.btnBack);
        ivHeaderAction = view.findViewById(R.id.ivHeaderAction);

        if (rvOrders == null) Log.w(TAG, "rvOrders not found.");
        if (tvEmpty == null) Log.w(TAG, "tvEmptyOrders not found.");
        if (progress == null) Log.w(TAG, "progressOrders not found.");
        if (swipeOrders == null) Log.w(TAG, "swipeOrders not found.");
        if (etSearchOrders == null) Log.w(TAG, "etSearchOrders not found.");
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
                if (swipeOrders != null && !swipeOrders.isRefreshing()) {
                    swipeOrders.setRefreshing(true);
                }
                fetch();
            });
        }
    }

    private void setupRecycler() {
        if (rvOrders == null) return;

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

        rvOrders.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvOrders.setAdapter(adapter);
        rvOrders.setHasFixedSize(false);
    }

    private void setupSearch() {
        if (etSearchOrders == null) return;

        etSearchOrders.addTextChangedListener(new TextWatcher() {
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
        if (swipeOrders == null) return;

        swipeOrders.setOnRefreshListener(() -> {
            if (isLoading) {
                swipeOrders.setRefreshing(false);
                return;
            }
            fetch();
        });
    }

    private void fetch() {
        if (!isAdded()) return;
        if (isLoading) return;

        isLoading = true;
        setLoading(true);

        SessionManager session = new SessionManager(requireContext());
        String token = session.getToken();

        Log.d(TAG, "fetch() token=" + mask(token));

        if (token == null || token.trim().isEmpty()) {
            isLoading = false;
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

                isLoading = false;
                setLoading(false);

                allOrders.clear();
                if (orders != null) {
                    allOrders.addAll(orders);
                }

                applyFilter();

                Log.i(TAG, "Loaded orders: " + allOrders.size());
            }

            @Override
            public void onError(int statusCode, String message) {
                if (!isAdded()) return;

                isLoading = false;
                setLoading(false);
                allOrders.clear();
                filteredOrders.clear();

                if (adapter != null) {
                    adapter.setData(filteredOrders);
                }

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

    private void applyFilter() {
        filteredOrders.clear();

        String q = currentQuery == null ? "" : currentQuery.trim().toLowerCase(Locale.getDefault());

        if (q.isEmpty()) {
            filteredOrders.addAll(allOrders);
        } else {
            for (Order order : allOrders) {
                if (order == null) continue;

                String invoice = order.getInvoiceNumber();
                if (invoice == null || invoice.trim().isEmpty()) {
                    invoice = String.valueOf(order.getId());
                }

                String invoiceSafe = invoice.toLowerCase(Locale.getDefault());

                if (invoiceSafe.contains(q)) {
                    filteredOrders.add(order);
                }
            }
        }

        if (adapter != null) {
            adapter.setData(filteredOrders);
        }

        showEmpty(filteredOrders.isEmpty());
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

        if (loading) {
            if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
            if (rvOrders != null) rvOrders.setVisibility(View.GONE);
        } else {
            if (swipeOrders != null) swipeOrders.setRefreshing(false);
        }
    }

    private void showEmpty(boolean empty) {
        if (tvEmpty != null) {
            tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        }

        if (rvOrders != null) {
            rvOrders.setVisibility(empty ? View.GONE : View.VISIBLE);
        }

        if (swipeOrders != null) {
            swipeOrders.setRefreshing(false);
        }
    }

    @Override
    public void onDestroyView() {
        if (swipeOrders != null) {
            swipeOrders.setOnRefreshListener(null);
        }

        if (rvOrders != null) {
            rvOrders.setAdapter(null);
        }

        swipeOrders = null;
        rvOrders = null;
        tvEmpty = null;
        progress = null;
        etSearchOrders = null;
        btnBack = null;
        ivHeaderAction = null;
        adapter = null;

        super.onDestroyView();
    }
}