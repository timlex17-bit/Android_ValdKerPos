package com.valdker.pos.ui.offlineorders;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.valdker.pos.SessionManager;
import com.valdker.pos.local.PendingOrderEntity;
import com.valdker.pos.local.PendingOrderItemEntity;
import com.valdker.pos.repositories.OfflineOrderRepository;
import com.valdker.pos.utils.Toast;

import java.util.List;

public class PendingOrdersActivity extends AppCompatActivity {

    private static final String FILTER_ALL = "ALL";
    private static final String FILTER_PENDING = "PENDING";
    private static final String FILTER_FAILED = "FAILED";
    private static final String FILTER_NEEDS_REVIEW = "NEEDS_REVIEW";
    private static final String FILTER_SYNCED = "SYNCED";
    private static final String TAG = "PENDING_ORDERS";

    private OfflineOrderRepository repository;
    private SessionManager sessionManager;
    private PendingOrdersAdapter adapter;

    private TextView tvSummary;
    private TextView tvEmpty;
    private TextView tvPendingCount;
    private TextView tvFailedCount;
    private TextView tvNeedsReviewCount;
    private TextView tvSyncedCount;
    private MaterialButton btnRetryAll;
    private Chip chipAll;
    private Chip chipPending;
    private Chip chipFailed;
    private Chip chipNeedsReview;
    private Chip chipSynced;

    @NonNull
    private String selectedFilter = FILTER_ALL;
    private boolean loading = false;
    private boolean reprintRunning = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        repository = new OfflineOrderRepository(this);
        sessionManager = new SessionManager(this);

        setContentView(buildContent());
        setupRecycler();
        setupFilters();
        setupActions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    @NonNull
    private View buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#F4F7FB"));
        root.setPadding(dp(14), dp(16), dp(14), 0);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(12));
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        MaterialButton btnBack = new MaterialButton(this);
        btnBack.setText("Back");
        btnBack.setAllCaps(false);
        btnBack.setCornerRadius(dp(10));
        btnBack.setMinHeight(dp(42));
        btnBack.setMinWidth(dp(72));
        btnBack.setOnClickListener(v -> finish());
        header.addView(btnBack, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setPadding(dp(12), 0, 0, 0);
        header.addView(titleBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = text(24, "#101827", true);
        title.setText("Offline Orders");
        titleBox.addView(title);

        tvSummary = text(13, "#6B7280", false);
        tvSummary.setText("Orders saved on this device and waiting for sync.");
        titleBox.addView(tvSummary);

        btnRetryAll = new MaterialButton(this);
        btnRetryAll.setText("Retry All");
        btnRetryAll.setAllCaps(false);
        btnRetryAll.setCornerRadius(dp(10));
        btnRetryAll.setMinHeight(dp(42));
        header.addView(btnRetryAll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout summaryRow = new LinearLayout(this);
        summaryRow.setOrientation(LinearLayout.HORIZONTAL);
        summaryRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams summaryLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        summaryLp.bottomMargin = dp(10);
        root.addView(summaryRow, summaryLp);

        tvPendingCount = addSummaryCard(summaryRow, "Pending", "#92400E", "#FEF3C7");
        tvFailedCount = addSummaryCard(summaryRow, "Failed", "#B91C1C", "#FEE2E2");
        tvNeedsReviewCount = addSummaryCard(summaryRow, "Review", "#7C2D12", "#FFEDD5");
        tvSyncedCount = addSummaryCard(summaryRow, "Synced", "#047857", "#D1FAE5");

        HorizontalScrollView filterScroll = new HorizontalScrollView(this);
        filterScroll.setHorizontalScrollBarEnabled(false);
        filterScroll.setFillViewport(false);
        root.addView(filterScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        ChipGroup filters = new ChipGroup(this);
        filters.setSingleSelection(true);
        filters.setSingleLine(true);
        filters.setChipSpacingHorizontal(dp(8));
        filters.setPadding(0, 0, 0, dp(10));
        filterScroll.addView(filters, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        chipAll = filterChip("All");
        chipPending = filterChip("Pending");
        chipFailed = filterChip("Failed");
        chipNeedsReview = filterChip("Needs Review");
        chipSynced = filterChip("Synced");
        filters.addView(chipAll);
        filters.addView(chipPending);
        filters.addView(chipFailed);
        filters.addView(chipNeedsReview);
        filters.addView(chipSynced);
        chipAll.setChecked(true);

        tvEmpty = text(15, "#6B7280", false);
        tvEmpty.setText("No offline orders\nOrders saved while offline will appear here.");
        tvEmpty.setGravity(Gravity.CENTER);
        tvEmpty.setPadding(dp(16), dp(42), dp(16), dp(42));
        tvEmpty.setVisibility(View.GONE);
        root.addView(tvEmpty, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        RecyclerView recyclerView = new RecyclerView(this);
        recyclerView.setId(View.generateViewId());
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        root.addView(recyclerView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));
        recyclerView.setTag("pending_orders_recycler");

        return root;
    }

    private void setupRecycler() {
        RecyclerView recyclerView = findRecyclerView((ViewGroup) findViewById(android.R.id.content));
        adapter = new PendingOrdersAdapter(new PendingOrdersAdapter.Listener() {
            @Override
            public void onRetry(@NonNull PendingOrderEntity order) {
                retryOne(order);
            }

            @Override
            public void onLoadItems(@NonNull String localOrderId) {
                repository.getOrderItems(localOrderId, new OfflineOrderRepository.OrderItemsCallback() {
                    @Override
                    public void onSuccess(@NonNull List<PendingOrderItemEntity> items) {
                        adapter.setItems(localOrderId, items);
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        Toast.makeText(PendingOrdersActivity.this,
                                "Failed to load order items: " + message,
                                Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onReprint(@NonNull PendingOrderEntity order) {
                reprintReceipt(order);
            }

            @Override
            public boolean isShopMismatch(@NonNull PendingOrderEntity order) {
                return false;
            }
        });
        recyclerView.setAdapter(adapter);
    }

    private void setupFilters() {
        chipAll.setOnClickListener(v -> selectFilter(FILTER_ALL));
        chipPending.setOnClickListener(v -> selectFilter(FILTER_PENDING));
        chipFailed.setOnClickListener(v -> selectFilter(FILTER_FAILED));
        chipNeedsReview.setOnClickListener(v -> selectFilter(FILTER_NEEDS_REVIEW));
        chipSynced.setOnClickListener(v -> selectFilter(FILTER_SYNCED));
    }

    private void setupActions() {
        btnRetryAll.setOnClickListener(v -> retryAll());
    }

    private void selectFilter(@NonNull String filter) {
        selectedFilter = filter;
        loadData();
    }

    private void loadData() {
        setLoading(loading);
        repository.getPendingOrderSummary(new OfflineOrderRepository.SummaryCallback() {
            @Override
            public void onSuccess(@NonNull OfflineOrderRepository.OfflineOrderSummary summary) {
                tvSummary.setText("Orders saved on this device and waiting for sync.");
                tvPendingCount.setText(String.valueOf(summary.pendingSyncCount));
                tvFailedCount.setText(String.valueOf(summary.failedCount));
                tvNeedsReviewCount.setText(String.valueOf(summary.needsReviewCount));
                tvSyncedCount.setText(String.valueOf(summary.syncedCount));
                chipAll.setText("All ("
                        + (summary.pendingSyncCount
                        + summary.failedCount
                        + summary.needsReviewCount
                        + summary.syncedCount)
                        + ")");
                chipPending.setText("Pending (" + summary.pendingSyncCount + ")");
                chipFailed.setText("Failed (" + summary.failedCount + ")");
                chipNeedsReview.setText("Needs Review (" + summary.needsReviewCount + ")");
                chipSynced.setText("Synced (" + summary.syncedCount + ")");
            }

            @Override
            public void onError(@NonNull String message) {
                tvSummary.setText("Unable to load sync counts");
            }
        });

        OfflineOrderRepository.OrderListCallback callback = new OfflineOrderRepository.OrderListCallback() {
            @Override
            public void onSuccess(@NonNull List<PendingOrderEntity> orders) {
                adapter.submitOrders(orders);
                tvEmpty.setVisibility(orders.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(@NonNull String message) {
                Toast.makeText(PendingOrdersActivity.this,
                        "Failed to load offline orders: " + message,
                        Toast.LENGTH_LONG).show();
            }
        };

        if (FILTER_PENDING.equals(selectedFilter)) {
            repository.getCurrentShopOfflineOrders(OfflineOrderRepository.STATUS_PENDING_SYNC, callback);
        } else if (FILTER_FAILED.equals(selectedFilter)) {
            repository.getCurrentShopOfflineOrders(OfflineOrderRepository.STATUS_FAILED, callback);
        } else if (FILTER_NEEDS_REVIEW.equals(selectedFilter)) {
            repository.getCurrentShopOfflineOrders(OfflineOrderRepository.STATUS_NEEDS_REVIEW, callback);
        } else if (FILTER_SYNCED.equals(selectedFilter)) {
            repository.getCurrentShopOfflineOrders(OfflineOrderRepository.STATUS_SYNCED, callback);
        } else {
            repository.getCurrentShopOfflineOrders(null, callback);
        }
    }

    private void retryOne(@NonNull PendingOrderEntity order) {
        if (loading) return;
        if (repository.hasShopContextMismatch(order)) {
            Log.w(TAG, "Retry blocked by shop guard"
                    + " pending.shopId=" + order.shopId
                    + " pending.shopCode=" + order.shopCode
                    + " current.shopId=" + sessionManager.getShopId()
                    + " current.shopCode=" + sessionManager.getShopCode());
            String message = order.shopId <= 0 || order.shopCode == null || order.shopCode.trim().isEmpty()
                    ? "Unknown shop context. This order requires manual review."
                    : "This offline order belongs to another shop. Login to the original shop to sync.";
            Toast.makeText(this,
                    message,
                    Toast.LENGTH_LONG).show();
            return;
        }

        setLoading(true);
        Toast.makeText(this, "Sync started", Toast.LENGTH_SHORT).show();
        repository.retryOrder(order.localOrderId, sessionManager.getToken(), new OfflineOrderRepository.SyncCallback() {
            @Override
            public void onComplete(@NonNull String message) {
                setLoading(false);
                Toast.makeText(PendingOrdersActivity.this,
                        message.isEmpty() ? "Order synced successfully" : message,
                        Toast.LENGTH_LONG).show();
                loadData();
            }

            @Override
            public void onError(@NonNull String message) {
                setLoading(false);
                Toast.makeText(PendingOrdersActivity.this,
                        message.isEmpty() ? "Sync failed. Please check error details." : message,
                        Toast.LENGTH_LONG).show();
                loadData();
            }
        });
    }

    private void retryAll() {
        if (loading) return;

        setLoading(true);
        Toast.makeText(this, "Sync started", Toast.LENGTH_SHORT).show();
        repository.retryAllPendingAndFailed(sessionManager.getToken(), new OfflineOrderRepository.SyncCallback() {
            @Override
            public void onComplete(@NonNull String message) {
                setLoading(false);
                Toast.makeText(PendingOrdersActivity.this,
                        message.isEmpty() ? "Sync finished" : message,
                        Toast.LENGTH_LONG).show();
                loadData();
            }

            @Override
            public void onError(@NonNull String message) {
                setLoading(false);
                Toast.makeText(PendingOrdersActivity.this,
                        "Sync failed. Please check error details.",
                        Toast.LENGTH_LONG).show();
                loadData();
            }
        });
    }

    private void reprintReceipt(@NonNull PendingOrderEntity order) {
        if (reprintRunning) {
            Toast.makeText(this, "Receipt print already running.", Toast.LENGTH_SHORT).show();
            return;
        }

        reprintRunning = true;
        Toast.makeText(this, "Reprint started", Toast.LENGTH_SHORT).show();
        repository.getOrderItems(order.localOrderId, new OfflineOrderRepository.OrderItemsCallback() {
            @Override
            public void onSuccess(@NonNull List<PendingOrderItemEntity> items) {
                PendingOrderReceiptPrinter.reprint(
                        PendingOrdersActivity.this,
                        order,
                        items,
                        new PendingOrderReceiptPrinter.Callback() {
                            @Override
                            public void onSuccess() {
                                runOnUiThread(() -> {
                                    reprintRunning = false;
                                    Toast.makeText(PendingOrdersActivity.this,
                                            "Receipt reprinted successfully.",
                                            Toast.LENGTH_LONG).show();
                                });
                            }

                            @Override
                            public void onError(@NonNull String message) {
                                runOnUiThread(() -> {
                                    reprintRunning = false;
                                    Toast.makeText(PendingOrdersActivity.this,
                                            "Receipt reprint failed. Check printer connection.",
                                            Toast.LENGTH_LONG).show();
                                });
                            }

                            @Override
                            public void onSkipped(@NonNull String message) {
                                runOnUiThread(() -> {
                                    reprintRunning = false;
                                    Toast.makeText(PendingOrdersActivity.this,
                                            message.isEmpty() ? "Receipt reprint skipped." : message,
                                            Toast.LENGTH_LONG).show();
                                });
                            }

                            @Override
                            public void onTimeout(@NonNull String message) {
                                runOnUiThread(() -> {
                                    reprintRunning = false;
                                    Toast.makeText(PendingOrdersActivity.this,
                                            "Printer connection timed out. Please check printer and try reprint.",
                                            Toast.LENGTH_LONG).show();
                                });
                            }
                        }
                );
            }

            @Override
            public void onError(@NonNull String message) {
                reprintRunning = false;
                Toast.makeText(PendingOrdersActivity.this,
                        "Receipt reprint failed. Check printer connection.",
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setLoading(boolean loading) {
        this.loading = loading;
        btnRetryAll.setEnabled(!loading);
        btnRetryAll.setText(loading ? "Syncing..." : "Retry All");
    }

    @NonNull
    private Chip filterChip(@NonNull String label) {
        Chip chip = new Chip(this);
        chip.setText(label);
        chip.setCheckable(true);
        chip.setTextSize(13);
        chip.setMinHeight(dp(40));
        chip.setChipCornerRadius(dp(20));
        return chip;
    }

    private TextView addSummaryCard(@NonNull LinearLayout parent,
                                    @NonNull String label,
                                    @NonNull String textColor,
                                    @NonNull String bgColor) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(8));
        card.setCardElevation(0);
        card.setStrokeWidth(1);
        card.setStrokeColor(Color.parseColor("#E5E7EB"));
        card.setCardBackgroundColor(Color.parseColor(bgColor));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10), dp(10), dp(10), dp(10));
        card.addView(box, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView count = text(20, textColor, true);
        count.setText("0");
        box.addView(count);

        TextView title = text(11, "#64748B", false);
        title.setText(label);
        box.addView(title);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(3), 0, dp(3), 0);
        parent.addView(card, lp);
        return count;
    }

    @NonNull
    private TextView text(int sp, @NonNull String color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setTextSize(sp);
        tv.setTextColor(Color.parseColor(color));
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    @NonNull
    private RecyclerView findRecyclerView(@NonNull ViewGroup group) {
        RecyclerView recyclerView = findRecyclerViewOrNull(group);
        if (recyclerView == null) {
            throw new IllegalStateException("Pending orders RecyclerView not found");
        }
        return recyclerView;
    }

    @Nullable
    private RecyclerView findRecyclerViewOrNull(@NonNull ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof RecyclerView && "pending_orders_recycler".equals(child.getTag())) {
                return (RecyclerView) child;
            }
            if (child instanceof ViewGroup) {
                RecyclerView found = findRecyclerViewOrNull((ViewGroup) child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
