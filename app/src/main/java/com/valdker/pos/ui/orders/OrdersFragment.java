package com.valdker.pos.ui.orders;

import android.app.DatePickerDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.valdker.pos.utils.Toast;

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
import com.valdker.pos.repositories.TransactionHistoryCacheRepository;

import java.util.ArrayList;
import java.util.Calendar;
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
    private ImageView btnDateRange;

    private OrdersAdapter adapter;
    private TransactionHistoryCacheRepository cacheRepository;

    private final List<Order> allOrders = new ArrayList<>();
    private final List<Order> filteredOrders = new ArrayList<>();

    private boolean isLoading = false;
    private String currentQuery = "";
    private String startDateFilter = "";
    private String endDateFilter = "";

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
        setupDateRange();
        setupSwipe();

        cacheRepository = new TransactionHistoryCacheRepository(requireContext());
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
        btnDateRange = view.findViewById(R.id.btnDateRange);

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

    private void setupDateRange() {
        if (btnDateRange == null) return;

        btnDateRange.setOnClickListener(v -> showDateRangePicker());
        btnDateRange.setOnLongClickListener(v -> {
            clearDateRangeFilter();
            return true;
        });
        updateDateRangeIcon();
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

        cacheRepository.loadOrdersRoomFirst(token, new TransactionHistoryCacheRepository.RoomFirstCallback<Order>() {
            @Override
            public void onLocal(@NonNull List<Order> orders) {
                if (!isAdded()) return;

                if (orders.isEmpty()) return;

                allOrders.clear();
                allOrders.addAll(orders);

                applyFilter();
                setLoading(false);
            }

            @Override
            public void onRemote(@NonNull List<Order> orders) {
                if (!isAdded()) return;

                isLoading = false;
                setLoading(false);

                allOrders.clear();
                allOrders.addAll(orders);

                applyFilter();

                Log.i(TAG, "Loaded orders: " + allOrders.size());
            }

            @Override
            public void onNoInternet(boolean hasLocalData) {
                if (!isAdded()) return;

                isLoading = false;
                setLoading(false);

                if (!hasLocalData && allOrders.isEmpty()) {
                    showLocalEmpty();
                    Toast.makeText(requireContext(), TransactionHistoryCacheRepository.NO_LOCAL_DATA_MESSAGE, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(int statusCode, @NonNull String message, boolean hasLocalData) {
                if (!isAdded()) return;

                isLoading = false;
                setLoading(false);

                Log.e(TAG, "Failed (" + statusCode + "): " + message);

                if (!hasLocalData && allOrders.isEmpty()) {
                    showLocalEmpty();
                    showApiError("Failed to load orders (" + statusCode + "): " + message);
                }
            }
        });
    }

    private void applyFilter() {
        filteredOrders.clear();

        String q = currentQuery == null ? "" : currentQuery.trim().toLowerCase(Locale.getDefault());

        for (Order order : allOrders) {
            if (order == null) continue;

            String invoice = order.getInvoiceNumber();
            if (invoice == null || invoice.trim().isEmpty()) {
                invoice = String.valueOf(order.getId());
            }

            String invoiceSafe = invoice.toLowerCase(Locale.getDefault());

            if ((q.isEmpty() || invoiceSafe.contains(q)) && isOrderInDateRange(order)) {
                filteredOrders.add(order);
            }
        }

        if (adapter != null) {
            adapter.setData(filteredOrders);
        }

        showEmpty(filteredOrders.isEmpty());
    }

    private boolean isOrderInDateRange(@NonNull Order order) {
        boolean hasStart = startDateFilter != null && !startDateFilter.isEmpty();
        boolean hasEnd = endDateFilter != null && !endDateFilter.isEmpty();
        if (!hasStart && !hasEnd) return true;

        String orderDate = datePart(order.getCreatedAtIso());
        if (orderDate.isEmpty()) return false;

        if (hasStart && orderDate.compareTo(startDateFilter) < 0) return false;
        if (hasEnd && orderDate.compareTo(endDateFilter) > 0) return false;
        return true;
    }

    @NonNull
    private String datePart(@Nullable String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        if (trimmed.length() < 10) return "";

        String date = trimmed.substring(0, 10);
        return date.matches("^\\d{4}-\\d{2}-\\d{2}$") ? date : "";
    }

    private void showDateRangePicker() {
        if (!isAdded()) return;

        Calendar initialStart = calendarFromDate(startDateFilter);
        DatePickerDialog startDialog = new DatePickerDialog(
                requireContext(),
                (picker, year, month, day) -> {
                    String selectedStart = formatDate(year, month, day);
                    Calendar initialEnd = calendarFromDate(endDateFilter.isEmpty() ? selectedStart : endDateFilter);

                    DatePickerDialog endDialog = new DatePickerDialog(
                            requireContext(),
                            (endPicker, endYear, endMonth, endDay) -> {
                                String selectedEnd = formatDate(endYear, endMonth, endDay);
                                applyDateRange(selectedStart, selectedEnd);
                            },
                            initialEnd.get(Calendar.YEAR),
                            initialEnd.get(Calendar.MONTH),
                            initialEnd.get(Calendar.DAY_OF_MONTH)
                    );
                    endDialog.setTitle("Select end date");
                    endDialog.show();
                },
                initialStart.get(Calendar.YEAR),
                initialStart.get(Calendar.MONTH),
                initialStart.get(Calendar.DAY_OF_MONTH)
        );
        startDialog.setTitle("Select start date");
        startDialog.show();
    }

    private void applyDateRange(@NonNull String start, @NonNull String end) {
        if (end.compareTo(start) < 0) {
            startDateFilter = end;
            endDateFilter = start;
        } else {
            startDateFilter = start;
            endDateFilter = end;
        }

        updateDateRangeIcon();
        applyFilter();

        if (isAdded()) {
            Toast.makeText(
                    requireContext(),
                    "Date range: " + startDateFilter + " - " + endDateFilter,
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void clearDateRangeFilter() {
        boolean hadFilter = (startDateFilter != null && !startDateFilter.isEmpty())
                || (endDateFilter != null && !endDateFilter.isEmpty());
        startDateFilter = "";
        endDateFilter = "";
        updateDateRangeIcon();
        applyFilter();

        if (hadFilter && isAdded()) {
            Toast.makeText(requireContext(), "Date range cleared", Toast.LENGTH_SHORT).show();
        }
    }

    @NonNull
    private Calendar calendarFromDate(@Nullable String date) {
        Calendar calendar = Calendar.getInstance();
        if (date == null || !date.matches("^\\d{4}-\\d{2}-\\d{2}$")) return calendar;

        try {
            calendar.set(Calendar.YEAR, Integer.parseInt(date.substring(0, 4)));
            calendar.set(Calendar.MONTH, Integer.parseInt(date.substring(5, 7)) - 1);
            calendar.set(Calendar.DAY_OF_MONTH, Integer.parseInt(date.substring(8, 10)));
        } catch (Exception ignored) {
        }
        return calendar;
    }

    @NonNull
    private String formatDate(int year, int month, int day) {
        return String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day);
    }

    private void updateDateRangeIcon() {
        if (btnDateRange == null) return;

        boolean active = (startDateFilter != null && !startDateFilter.isEmpty())
                || (endDateFilter != null && !endDateFilter.isEmpty());
        btnDateRange.setColorFilter(Color.parseColor(active ? "#22C55E" : "#6B7280"));
        btnDateRange.setAlpha(active ? 1f : 0.85f);
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

    private void showLocalEmpty() {
        if (tvEmpty != null) {
            tvEmpty.setText(TransactionHistoryCacheRepository.NO_LOCAL_DATA_MESSAGE);
        }
        showEmpty(true);
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
        btnDateRange = null;
        cacheRepository = null;
        adapter = null;

        super.onDestroyView();
    }
}
