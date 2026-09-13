package com.valdker.pos.ui.offlineorders;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.valdker.pos.ModuleRegistry;
import com.valdker.pos.R;
import com.valdker.pos.SessionManager;
import com.valdker.pos.local.PendingOrderEntity;
import com.valdker.pos.local.PendingOrderItemEntity;
import com.valdker.pos.repositories.OfflineOrderRepository;
import com.valdker.pos.ui.common.SystemBars;
import com.valdker.pos.utils.Toast;

import java.util.List;

/**
 * Pesanan Offline.
 *
 * <p>Layar ini dulu dibangun sepenuhnya dari kode: sekitar 120 baris
 * {@code new LinearLayout()} / {@code new TextView()} di {@code buildContent()},
 * lalu RecyclerView-nya dicari lagi dengan menelusuri seluruh pohon view dan
 * mencocokkan tag string. Konsekuensinya nyata - tidak ada bilah atas maupun
 * tombol kembali, judulnya tertindih bilah status di Android 15+, dan seluruh
 * teksnya terkunci dalam bahasa Inggris.
 *
 * <p>Sekarang tata letaknya ada di {@code activity_pending_orders.xml},
 * bilah atasnya sama dengan layar lain, dan seluruh teks berasal dari
 * {@code strings.xml}.
 */
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

    private TextView tvSubtitle;
    private View emptyState;
    private TextView tvEmptyTitle;
    private TextView tvEmptyMessage;
    private SwipeRefreshLayout swipeRefresh;
    private MaterialButton btnRetryAll;

    private Chip chipAll;
    private Chip chipPending;
    private Chip chipFailed;
    private Chip chipNeedsReview;
    private Chip chipSynced;

    private StatBox statPending;
    private StatBox statFailed;
    private StatBox statReview;
    private StatBox statSynced;

    @NonNull
    private String selectedFilter = FILTER_ALL;
    private boolean loading = false;
    private boolean reprintRunning = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        repository = new OfflineOrderRepository(this);
        sessionManager = new SessionManager(this);
        if (!sessionManager.canAccessModule(ModuleRegistry.OFFLINE_ORDERS)) {
            Toast.makeText(this, getString(R.string.msg_permission_denied), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setContentView(R.layout.activity_pending_orders);

        bindViews();
        setupSystemBars();
        setupRecycler();
        setupFilters();
        setupActions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    // ------------------------------------------------------------ penyiapan

    private void bindViews() {
        View topBar = findViewById(R.id.topBar);
        TextView tvTitle = topBar.findViewById(R.id.tvTopBarTitle);
        tvSubtitle = topBar.findViewById(R.id.tvTopBarSubtitle);
        ImageButton btnBack = topBar.findViewById(R.id.btnBack);

        tvTitle.setText(R.string.offline_orders_title);
        tvSubtitle.setText(R.string.offline_orders_subtitle);
        tvSubtitle.setVisibility(View.VISIBLE);
        btnBack.setOnClickListener(v -> finish());

        statPending = new StatBox(findViewById(R.id.statPending),
                R.string.offline_orders_stat_pending, R.color.state_warning);
        statFailed = new StatBox(findViewById(R.id.statFailed),
                R.string.offline_orders_stat_failed, R.color.state_danger_strong);
        statReview = new StatBox(findViewById(R.id.statReview),
                R.string.offline_orders_stat_review, R.color.state_review);
        statSynced = new StatBox(findViewById(R.id.statSynced),
                R.string.offline_orders_stat_synced, R.color.state_success);

        chipAll = findViewById(R.id.chipAll);
        chipPending = findViewById(R.id.chipPending);
        chipFailed = findViewById(R.id.chipFailed);
        chipNeedsReview = findViewById(R.id.chipNeedsReview);
        chipSynced = findViewById(R.id.chipSynced);

        swipeRefresh = findViewById(R.id.swipeOrders);
        emptyState = findViewById(R.id.emptyState);
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle);
        tvEmptyMessage = findViewById(R.id.tvEmptyMessage);
        btnRetryAll = findViewById(R.id.btnRetryAll);
    }

    private void setupSystemBars() {
        SystemBars.setup(this, findViewById(R.id.topBar), findViewById(R.id.bottomBar));
    }

    private void setupRecycler() {
        RecyclerView recyclerView = findViewById(R.id.rvPendingOrders);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setHasFixedSize(false);

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
                        toast(getString(R.string.offline_orders_items_failed, message));
                    }
                });
            }

            @Override
            public void onReprint(@NonNull PendingOrderEntity order) {
                reprintReceipt(order);
            }

            @Override
            public boolean isShopMismatch(@NonNull PendingOrderEntity order) {
                return repository.hasShopContextMismatch(order);
            }
        });
        recyclerView.setAdapter(adapter);

        swipeRefresh.setColorSchemeColors(ContextCompat.getColor(this, R.color.brand_primary));
        swipeRefresh.setOnRefreshListener(this::loadData);
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

    // ---------------------------------------------------------------- data

    private void loadData() {
        repository.getPendingOrderSummary(new OfflineOrderRepository.SummaryCallback() {
            @Override
            public void onSuccess(@NonNull OfflineOrderRepository.OfflineOrderSummary summary) {
                tvSubtitle.setText(R.string.offline_orders_subtitle);
                statPending.setValue(summary.pendingSyncCount);
                statFailed.setValue(summary.failedCount);
                statReview.setValue(summary.needsReviewCount);
                statSynced.setValue(summary.syncedCount);

                int total = summary.pendingSyncCount
                        + summary.failedCount
                        + summary.needsReviewCount
                        + summary.syncedCount;
                setChipLabel(chipAll, R.string.offline_orders_filter_all, total);
                setChipLabel(chipPending, R.string.offline_orders_filter_pending,
                        summary.pendingSyncCount);
                setChipLabel(chipFailed, R.string.offline_orders_filter_failed,
                        summary.failedCount);
                setChipLabel(chipNeedsReview, R.string.offline_orders_filter_review,
                        summary.needsReviewCount);
                setChipLabel(chipSynced, R.string.offline_orders_filter_synced,
                        summary.syncedCount);

                // Tidak ada apa pun untuk disinkron: aksi utama tidak perlu aktif.
                btnRetryAll.setEnabled(!loading
                        && (summary.pendingSyncCount + summary.failedCount) > 0);
            }

            @Override
            public void onError(@NonNull String message) {
                tvSubtitle.setText(R.string.offline_orders_subtitle_error);
            }
        });

        OfflineOrderRepository.OrderListCallback callback =
                new OfflineOrderRepository.OrderListCallback() {
                    @Override
                    public void onSuccess(@NonNull List<PendingOrderEntity> orders) {
                        swipeRefresh.setRefreshing(false);
                        adapter.submitOrders(orders);
                        showEmptyState(orders.isEmpty());
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        swipeRefresh.setRefreshing(false);
                        toast(getString(R.string.offline_orders_load_failed, message));
                    }
                };

        repository.getCurrentShopOfflineOrders(statusForFilter(), callback);
    }

    @Nullable
    private String statusForFilter() {
        if (FILTER_PENDING.equals(selectedFilter)) return OfflineOrderRepository.STATUS_PENDING_SYNC;
        if (FILTER_FAILED.equals(selectedFilter)) return OfflineOrderRepository.STATUS_FAILED;
        if (FILTER_NEEDS_REVIEW.equals(selectedFilter)) return OfflineOrderRepository.STATUS_NEEDS_REVIEW;
        if (FILTER_SYNCED.equals(selectedFilter)) return OfflineOrderRepository.STATUS_SYNCED;
        return null;
    }

    /**
     * Daftar kosong karena memang belum ada pesanan offline dan daftar kosong
     * karena filter yang dipilih tidak cocok adalah dua keadaan berbeda, dan
     * saran yang tepat untuk keduanya juga berbeda.
     */
    private void showEmptyState(boolean empty) {
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (!empty) return;

        boolean filtered = !FILTER_ALL.equals(selectedFilter);
        tvEmptyTitle.setText(filtered
                ? R.string.offline_orders_empty_filtered_title
                : R.string.offline_orders_empty_title);
        tvEmptyMessage.setText(filtered
                ? R.string.offline_orders_empty_filtered_message
                : R.string.offline_orders_empty_message);
    }

    // --------------------------------------------------------------- aksi

    private void retryOne(@NonNull PendingOrderEntity order) {
        if (loading) return;
        if (repository.hasShopContextMismatch(order)) {
            Log.w(TAG, "Retry blocked by shop guard"
                    + " pending.shopId=" + order.shopId
                    + " pending.shopCode=" + order.shopCode
                    + " current.shopId=" + sessionManager.getShopId()
                    + " current.shopCode=" + sessionManager.getShopCode());
            boolean unknownShop = order.shopId <= 0
                    || order.shopCode == null
                    || order.shopCode.trim().isEmpty();
            toast(getString(unknownShop
                    ? R.string.offline_orders_shop_unknown
                    : R.string.offline_orders_shop_mismatch));
            return;
        }

        setLoading(true);
        toast(getString(R.string.offline_orders_sync_started));
        repository.retryOrder(order.localOrderId, sessionManager.getToken(),
                new OfflineOrderRepository.SyncCallback() {
                    @Override
                    public void onComplete(@NonNull String message) {
                        setLoading(false);
                        toast(message.isEmpty()
                                ? getString(R.string.offline_orders_sync_ok)
                                : message);
                        loadData();
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        setLoading(false);
                        toast(message.isEmpty()
                                ? getString(R.string.offline_orders_sync_failed)
                                : message);
                        loadData();
                    }
                });
    }

    private void retryAll() {
        if (loading) return;

        setLoading(true);
        toast(getString(R.string.offline_orders_sync_started));
        repository.retryAllPendingAndFailed(sessionManager.getToken(),
                new OfflineOrderRepository.SyncCallback() {
                    @Override
                    public void onComplete(@NonNull String message) {
                        setLoading(false);
                        toast(message.isEmpty()
                                ? getString(R.string.offline_orders_sync_finished)
                                : message);
                        loadData();
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        setLoading(false);
                        toast(getString(R.string.offline_orders_sync_failed));
                        loadData();
                    }
                });
    }

    private void reprintReceipt(@NonNull PendingOrderEntity order) {
        if (reprintRunning) {
            toast(getString(R.string.offline_orders_reprint_running));
            return;
        }

        reprintRunning = true;
        toast(getString(R.string.offline_orders_reprint_started));
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
                                finishReprint(R.string.offline_orders_reprint_ok);
                            }

                            @Override
                            public void onError(@NonNull String message) {
                                finishReprint(R.string.offline_orders_reprint_failed);
                            }

                            @Override
                            public void onSkipped(@NonNull String message) {
                                finishReprint(R.string.offline_orders_reprint_skipped);
                            }

                            @Override
                            public void onTimeout(@NonNull String message) {
                                finishReprint(R.string.offline_orders_reprint_timeout);
                            }
                        }
                );
            }

            @Override
            public void onError(@NonNull String message) {
                finishReprint(R.string.offline_orders_reprint_failed);
            }
        });
    }

    private void finishReprint(@StringRes int messageRes) {
        runOnUiThread(() -> {
            reprintRunning = false;
            toast(getString(messageRes));
        });
    }

    private void setLoading(boolean loading) {
        this.loading = loading;
        btnRetryAll.setEnabled(!loading);
        btnRetryAll.setText(loading
                ? R.string.offline_orders_retry_all_running
                : R.string.offline_orders_retry_all);
    }

    private void setChipLabel(@NonNull Chip chip, @StringRes int labelRes, int count) {
        chip.setText(getString(R.string.offline_orders_filter_count, getString(labelRes), count));
    }

    private void toast(@NonNull String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    /**
     * Pembungkus untuk satu kotak angka ringkasan. Keempat kotak memakai
     * layout yang sama lewat {@code <include>}, jadi pencarian view-nya
     * dibatasi pada akar tiap include - kalau tidak, findViewById tingkat
     * Activity akan selalu mengembalikan kotak pertama.
     */
    private static final class StatBox {
        private final TextView value;

        StatBox(@NonNull View root, @StringRes int labelRes, @ColorRes int accentRes) {
            this.value = root.findViewById(R.id.tvStatValue);
            TextView label = root.findViewById(R.id.tvStatLabel);
            View dot = root.findViewById(R.id.statDot);

            label.setText(labelRes);
            dot.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(root.getContext(), accentRes)));
        }

        void setValue(int count) {
            value.setText(String.valueOf(count));
        }
    }
}
