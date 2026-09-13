package com.valdker.pos.ui.kitchen;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.valdker.pos.R;
import com.valdker.pos.SessionManager;
import com.valdker.pos.restaurant.kitchen.KitchenBoard;
import com.valdker.pos.restaurant.kitchen.KitchenRepository;
import com.valdker.pos.restaurant.kitchen.KitchenStatus;
import com.valdker.pos.ui.common.SystemBars;
import com.valdker.pos.utils.Toast;

/**
 * Papan Dapur (Kitchen Display, Modul 3).
 *
 * <p>Layar ini hidup berbeda dari layar lain di aplikasi: ia dibiarkan menyala
 * berjam-jam di dinding dapur, tidak disentuh kecuali untuk menggeser status,
 * dan dibaca dari jarak beberapa meter. Tiga konsekuensi yang terlihat di
 * kode ini:
 *
 * <ul>
 *   <li><b>Polling berhenti di {@code onPause()}.</b> Papan yang tetap polling
 *       di latar belakang membakar baterai dan kuota sepanjang malam untuk
 *       layar yang tidak dilihat siapa pun.</li>
 *   <li><b>{@code FLAG_KEEP_SCREEN_ON}</b> selama layar terlihat - papan yang
 *       mati sendiri setiap dua menit tidak berguna sebagai papan.</li>
 *   <li><b>304 tidak mengosongkan apa pun.</b> Sebagian besar putaran polling
 *       memang tidak membawa perubahan; itu keadaan normal, bukan kegagalan.
 *       Lihat {@code KitchenRepository.BoardRequest} untuk jebakan Volley di
 *       balik ini.</li>
 * </ul>
 */
public class KitchenDisplayActivity extends AppCompatActivity
        implements KitchenOrderAdapter.Listener {

    private static final String TAG = "KITCHEN_DISPLAY";

    /**
     * Jeda antar putaran polling.
     *
     * <p>Lima detik: cukup cepat supaya kasir yang baru mengirim order tidak
     * menunggu lama, dan cukup lambat supaya putaran yang tidak berubah -
     * yang dijawab 304 tanpa body - nyaris tidak berbiaya.
     */
    private static final long POLL_INTERVAL_MS = 5_000L;

    /** Menggambar ulang durasi "sejak" meski papan tidak berubah. */
    private static final long TICK_INTERVAL_MS = 30_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private KitchenRepository repository;
    private SessionManager session;
    private KitchenOrderAdapter adapter;

    private SwipeRefreshLayout swipeRefresh;
    private View emptyState;
    private TextView tvSubtitle;
    private StatBox statPending;
    private StatBox statPreparing;
    private StatBox statReady;

    /** Papan yang sedang tampil. Tidak pernah dikosongkan oleh 304 atau galat. */
    @NonNull
    private KitchenBoard board = KitchenBoard.EMPTY;

    /**
     * ETag dari respons TERAKHIR, bukan dari muat pertama.
     *
     * <p>Kontraknya menyebut jebakan ini secara khusus: mengirim ulang ETag
     * yang sudah basi membuat server menjawab 200 dengan body penuh, dan klien
     * yang mengira 304 berarti "tidak ada perubahan" sambil tidak pernah
     * memperbarui nilai ini akan terus meminta ulang seluruh papan.
     */
    @Nullable
    private String lastEtag;

    private boolean polling = false;
    private boolean requestInFlight = false;
    private long pendingItemId = -1L;

    private final Runnable pollTask = new Runnable() {
        @Override
        public void run() {
            if (!polling) return;
            fetchBoard(false);
            handler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    private final Runnable tickTask = new Runnable() {
        @Override
        public void run() {
            if (!polling) return;
            // Hanya menggambar ulang: "4m" harus menjadi "5m" walaupun server
            // tidak mengirim apa pun yang baru.
            adapter.submit(board, System.currentTimeMillis());
            handler.postDelayed(this, TICK_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        session = new SessionManager(this);
        if (!session.canAccessModule(KitchenRepository.MODULE_KITCHEN_DISPLAY)) {
            Toast.makeText(this, getString(R.string.kitchen_no_permission), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        repository = new KitchenRepository(this);
        setContentView(R.layout.activity_kitchen_display);

        bindViews();
        setupSystemBars();
        setupList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        startPolling();
    }

    @Override
    protected void onPause() {
        super.onPause();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        stopPolling();
    }

    // ------------------------------------------------------------ penyiapan

    private void bindViews() {
        View topBar = findViewById(R.id.topBar);
        TextView tvTitle = topBar.findViewById(R.id.tvTopBarTitle);
        tvSubtitle = topBar.findViewById(R.id.tvTopBarSubtitle);
        ImageButton btnBack = topBar.findViewById(R.id.btnBack);
        ImageButton btnRefresh = topBar.findViewById(R.id.btnTopAction1);

        tvTitle.setText(R.string.kitchen_title);
        tvSubtitle.setVisibility(View.VISIBLE);
        btnBack.setOnClickListener(v -> finish());

        btnRefresh.setVisibility(View.VISIBLE);
        btnRefresh.setImageResource(R.drawable.ic_refresh);
        btnRefresh.setContentDescription(getString(R.string.kitchen_refresh));
        btnRefresh.setOnClickListener(v -> fetchBoard(true));

        statPending = new StatBox(findViewById(R.id.statPending),
                R.string.kitchen_stat_pending, R.color.state_warning);
        statPreparing = new StatBox(findViewById(R.id.statPreparing),
                R.string.kitchen_stat_preparing, R.color.state_info);
        statReady = new StatBox(findViewById(R.id.statReady),
                R.string.kitchen_stat_ready, R.color.state_success);

        swipeRefresh = findViewById(R.id.swipeKitchen);
        emptyState = findViewById(R.id.emptyState);
    }

    private void setupSystemBars() {
        SystemBars.apply(this);
        SystemBars.padTopBar(findViewById(R.id.topBar));
        SystemBars.padBottom(findViewById(R.id.swipeKitchen));
    }

    private void setupList() {
        RecyclerView rv = findViewById(R.id.rvKitchen);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new KitchenOrderAdapter(this);
        rv.setAdapter(adapter);

        swipeRefresh.setColorSchemeColors(ContextCompat.getColor(this, R.color.brand_primary));
        swipeRefresh.setOnRefreshListener(() -> fetchBoard(true));
    }

    // ------------------------------------------------------------- polling

    private void startPolling() {
        if (polling) return;
        polling = true;
        handler.post(pollTask);
        handler.postDelayed(tickTask, TICK_INTERVAL_MS);
        Log.i(TAG, "polling started interval=" + POLL_INTERVAL_MS + "ms");
    }

    private void stopPolling() {
        if (!polling) return;
        polling = false;
        handler.removeCallbacks(pollTask);
        handler.removeCallbacks(tickTask);
        Log.i(TAG, "polling stopped");
    }

    /**
     * @param manual true kalau dipicu tarik-untuk-muat-ulang atau tombol
     *               segarkan. Putaran manual sengaja mengabaikan ETag supaya
     *               orang yang menekan "segarkan" benar-benar mendapat papan
     *               penuh, bukan 304 yang tidak mengubah apa pun di layar.
     */
    private void fetchBoard(boolean manual) {
        if (requestInFlight) {
            if (manual) swipeRefresh.setRefreshing(false);
            return;
        }
        requestInFlight = true;

        String etag = manual ? null : lastEtag;

        repository.fetchBoard(session.getToken(), etag, new KitchenRepository.BoardCallback() {
            @Override
            public void onBoard(@NonNull KitchenBoard newBoard, @Nullable String newEtag) {
                requestInFlight = false;
                swipeRefresh.setRefreshing(false);
                lastEtag = newEtag;
                board = newBoard;
                render();
            }

            @Override
            public void onNotModified(@Nullable String newEtag) {
                requestInFlight = false;
                swipeRefresh.setRefreshing(false);
                // Papan TIDAK disentuh. Yang diperbarui hanya ETag - server
                // mengirimkannya lagi pada 304, dan menyimpan yang terbaru
                // adalah yang membuat putaran berikutnya tetap murah.
                if (newEtag != null) lastEtag = newEtag;
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                requestInFlight = false;
                swipeRefresh.setRefreshing(false);

                // Galat juga tidak mengosongkan papan: kru dapur lebih baik
                // melihat daftar yang berumur beberapa detik daripada layar
                // kosong saat wifi dapur berkedip.
                Log.w(TAG, "fetchBoard failed status=" + statusCode + " msg=" + message);
                if (manual) {
                    Toast.makeText(KitchenDisplayActivity.this,
                            getString(R.string.kitchen_load_failed, message),
                            Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    // -------------------------------------------------------------- aksi

    @Override
    public void onStatusAction(@NonNull KitchenBoard.Item item, @NonNull String newStatus) {
        if (pendingItemId >= 0) return;

        if (KitchenStatus.CANCELLED.equals(KitchenStatus.normalize(newStatus))) {
            // Pembatalan bersifat terminal dan tidak bisa dibalik lewat API
            // mana pun, jadi ia satu-satunya aksi yang meminta konfirmasi.
            new AlertDialog.Builder(this)
                    .setTitle(R.string.kitchen_cancel_confirm_title)
                    .setMessage(getString(R.string.kitchen_cancel_confirm_message, item.productName))
                    .setNegativeButton(R.string.action_cancel, null)
                    .setPositiveButton(R.string.kitchen_action_cancelled,
                            (d, w) -> sendStatus(item, KitchenStatus.CANCELLED))
                    .show();
            return;
        }

        sendStatus(item, newStatus);
    }

    private void sendStatus(@NonNull KitchenBoard.Item item, @NonNull String newStatus) {
        pendingItemId = item.id;
        adapter.setPendingItem(item.id);

        repository.setItemStatus(session.getToken(), item.id, newStatus,
                new KitchenRepository.StatusCallback() {
                    @Override
                    public void onSuccess(@NonNull KitchenBoard.Item updated) {
                        pendingItemId = -1L;
                        adapter.setPendingItem(-1L);

                        // Perubahan diterapkan ke papan lokal supaya terlihat
                        // seketika. ETag lama sengaja dibuang: papan di server
                        // sudah berubah, jadi menyimpannya hanya membuat
                        // putaran berikutnya meminta sesuatu yang basi.
                        board = board.withItemStatus(
                                updated.id, updated.kitchenStatus, updated.kitchenStatusUpdatedAt);
                        lastEtag = null;
                        render();
                    }

                    @Override
                    public void onError(int statusCode, @NonNull String message) {
                        pendingItemId = -1L;
                        adapter.setPendingItem(-1L);

                        // 400 di sini hampir selalu berarti hal yang sama:
                        // perangkat lain memindahkan item ini lebih dulu.
                        // Server membaca ulang status di dalam
                        // select_for_update(), jadi dua penekanan bersamaan
                        // tidak bisa dua-duanya berhasil. Yang kalah tidak
                        // diberi tahu "coba lagi" - papannya disegarkan supaya
                        // ia melihat keadaan yang sebenarnya.
                        String text = statusCode == 400
                                ? getString(R.string.kitchen_conflict)
                                : getString(R.string.kitchen_change_failed, message);
                        Toast.makeText(KitchenDisplayActivity.this, text, Toast.LENGTH_LONG).show();

                        Log.w(TAG, "setItemStatus rejected status=" + statusCode
                                + " item=" + item.id + " to=" + newStatus + " msg=" + message);

                        lastEtag = null;
                        fetchBoard(true);
                    }
                });
    }

    // ---------------------------------------------------------- tampilan

    private void render() {
        long now = System.currentTimeMillis();
        adapter.submit(board, now);

        boolean empty = board.orders.isEmpty();
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);

        tvSubtitle.setText(empty
                ? getString(R.string.kitchen_subtitle_idle)
                : getString(R.string.kitchen_subtitle, board.itemCount(), board.orders.size()));

        statPending.setValue(board.countWithStatus(KitchenStatus.PENDING));
        statPreparing.setValue(board.countWithStatus(KitchenStatus.PREPARING));
        statReady.setValue(board.countWithStatus(KitchenStatus.READY));
    }

    /** Satu kotak angka ringkasan; lihat {@code view_kitchen_stat.xml}. */
    private static final class StatBox {
        private final TextView value;

        StatBox(@NonNull View root, @StringRes int labelRes, @ColorRes int accentRes) {
            this.value = root.findViewById(R.id.tvKitchenStatValue);
            TextView label = root.findViewById(R.id.tvKitchenStatLabel);
            View bar = root.findViewById(R.id.kitchenStatBar);

            label.setText(labelRes);
            bar.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(root.getContext(), accentRes)));
        }

        void setValue(int count) {
            value.setText(String.valueOf(count));
        }
    }
}
