package com.valdker.pos.repositories;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.volley.VolleyError;
import com.valdker.pos.SessionManager;
import com.valdker.pos.local.PendingOrderEntity;
import com.valdker.pos.local.PendingOrderItemEntity;
import com.valdker.pos.local.ValoraLocalDatabase;
import com.valdker.pos.money.Money;
import com.valdker.pos.utils.ErrorHandler;
import com.valdker.pos.utils.NetworkUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class OfflineOrderRepository {

    public static final String STATUS_PENDING_SYNC = "PENDING_SYNC";
    public static final String STATUS_SYNCING = "SYNCING";
    public static final String STATUS_SYNCED = "SYNCED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_NEEDS_REVIEW = "NEEDS_REVIEW";
    public static final String MESSAGE_ORDER_SAVED_LOCALLY =
            "No internet connection. Order saved locally and will sync later.";
    public static final String MESSAGE_SHOP_MISMATCH =
            "Shop mismatch. Login to the original shop to sync this order.";
    public static final String MESSAGE_UNKNOWN_SHOP_CONTEXT =
            "Unknown shop context. This order requires manual review.";

    private static final String TAG = "OFFLINE_ORDER_REPO";
    private static final int MAX_SYNC_ATTEMPTS = 5;
    private static final AtomicBoolean SYNC_RUNNING = new AtomicBoolean(false);

    public interface SaveCallback {
        void onSuccess(@NonNull String localOrderId, boolean inserted);
        void onError(@NonNull String message);
    }

    public interface SummaryCallback {
        void onSuccess(@NonNull OfflineOrderSummary summary);
        void onError(@NonNull String message);
    }

    public interface OrderListCallback {
        void onSuccess(@NonNull List<PendingOrderEntity> orders);
        void onError(@NonNull String message);
    }

    public interface OrderItemsCallback {
        void onSuccess(@NonNull List<PendingOrderItemEntity> items);
        void onError(@NonNull String message);
    }

    public interface SyncCallback {
        void onComplete(@NonNull String message);
        void onError(@NonNull String message);
    }

    public static class OfflineOrderSummary {
        public final int pendingSyncCount;
        public final int failedCount;
        public final int needsReviewCount;
        public final int syncedCount;
        public final int otherShopCount;

        public OfflineOrderSummary(int pendingSyncCount, int failedCount, int needsReviewCount) {
            this(pendingSyncCount, failedCount, needsReviewCount, 0, 0);
        }

        public OfflineOrderSummary(int pendingSyncCount,
                                   int failedCount,
                                   int needsReviewCount,
                                   int syncedCount,
                                   int otherShopCount) {
            this.pendingSyncCount = pendingSyncCount;
            this.failedCount = failedCount;
            this.needsReviewCount = needsReviewCount;
            this.syncedCount = syncedCount;
            this.otherShopCount = otherShopCount;
        }

        public int attentionCount() {
            return pendingSyncCount + failedCount + needsReviewCount;
        }
    }

    private final Context appContext;
    private final ValoraLocalDatabase db;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public OfflineOrderRepository(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.db = ValoraLocalDatabase.getInstance(appContext);
    }

    @NonNull
    public static String newLocalOrderId(@Nullable String businessType) {
        String prefix = safe(businessType).toLowerCase(Locale.US);
        if (prefix.isEmpty()) prefix = "pos";
        return prefix + "-" + System.currentTimeMillis() + "-" + UUID.randomUUID();
    }

    @NonNull
    public static String newClientOrderId() {
        return "android-" + System.currentTimeMillis() + "-" + UUID.randomUUID();
    }

    @NonNull
    public static String newClientOrderId(@Nullable Context context) {
        int shopId = 0;
        try {
            if (context != null) {
                shopId = new SessionManager(context.getApplicationContext()).getShopId();
            }
        } catch (Exception ignored) {
        }
        if (shopId > 0) {
            return "android-shop" + shopId + "-" + System.currentTimeMillis() + "-" + UUID.randomUUID();
        }
        return "android-shopunknown-" + System.currentTimeMillis() + "-" + UUID.randomUUID();
    }

    public static void ensureClientOrderId(@NonNull JSONObject payload,
                                           @NonNull String clientOrderId) {
        // Jangan timpa kunci yang sudah ada. Payload yang sedang dikirim ulang
        // harus membawa kunci yang SAMA, kalau tidak dedup di server tidak kena.
        String existing = safe(payload.optString("client_order_id", ""));
        if (!existing.isEmpty()) return;

        String clean = safe(clientOrderId);
        if (clean.isEmpty()) clean = newClientOrderId();

        try {
            payload.put("client_order_id", clean);
        } catch (Exception e) {
            Log.w(TAG, "Unable to attach client_order_id", e);
        }
    }

    public static boolean shouldSaveOffline(@Nullable Context context,
                                            int statusCode,
                                            @Nullable String message) {
        if (statusCode > 0) return false;
        return !NetworkUtils.isNetworkAvailable(context)
                || ErrorHandler.isNetworkError(message);
    }

    public static boolean shouldSaveOffline(@Nullable Context context,
                                            @Nullable Throwable throwable) {
        if (throwable instanceof VolleyError
                && ((VolleyError) throwable).networkResponse != null) {
            return false;
        }
        return !NetworkUtils.isNetworkAvailable(context)
                || ErrorHandler.isNetworkError(throwable);
    }

    public boolean isCurrentShopContextMatch(@Nullable PendingOrderEntity order) {
        return order != null && shopContextMatches(order, currentShopContext());
    }

    public boolean hasShopContextMismatch(@Nullable PendingOrderEntity order) {
        return order == null || !isCurrentShopContextMatch(order);
    }

    public void savePendingOrder(@NonNull String localOrderId,
                                 @NonNull JSONObject payload,
                                 @Nullable String businessType,
                                 @NonNull SaveCallback callback) {
        executor.execute(() -> {
            try {
                String cleanLocalId = safe(localOrderId);
                if (cleanLocalId.isEmpty()) {
                    cleanLocalId = newLocalOrderId(businessType);
                }

                long now = System.currentTimeMillis();
                PendingOrderEntity order = buildOrderEntity(cleanLocalId, payload, businessType, now);
                List<PendingOrderItemEntity> items = buildItemEntities(cleanLocalId, payload);

                PendingOrderEntity existingByClientOrderId = null;
                if (!safe(order.clientOrderId).isEmpty()) {
                    existingByClientOrderId = db.pendingOrderDao().getByClientOrderId(order.clientOrderId);
                }
                if (existingByClientOrderId != null
                        && !cleanLocalId.equals(existingByClientOrderId.localOrderId)) {
                    Log.w(TAG, "Skip duplicate pending order client_order_id=" + order.clientOrderId
                            + " existingLocalOrderId=" + existingByClientOrderId.localOrderId
                            + " newLocalOrderId=" + cleanLocalId);
                    String finalLocalId = existingByClientOrderId.localOrderId;
                    mainHandler.post(() -> callback.onSuccess(finalLocalId, false));
                    return;
                }

                boolean inserted = db.pendingOrderDao().insertOrderWithItems(order, items);
                Log.i(TAG, "Pending order saved localOrderId=" + cleanLocalId
                        + " client_order_id=" + order.clientOrderId
                        + " currentSession.shopId=" + order.shopId
                        + " currentSession.shopCode=" + order.shopCode
                        + " currentSession.shopName=" + order.shopName
                        + " pendingOrder.shopId=" + order.shopId
                        + " pendingOrder.shopCode=" + order.shopCode
                        + " pendingOrder.shopName=" + order.shopName
                        + " syncStatus=" + order.syncStatus
                        + " inserted=" + inserted);
                String finalLocalId = cleanLocalId;
                mainHandler.post(() -> callback.onSuccess(finalLocalId, inserted));
            } catch (Exception e) {
                Log.e(TAG, "Failed to save pending order", e);
                mainHandler.post(() -> callback.onError(
                        e.getMessage() != null ? e.getMessage() : "Failed to save local order"
                ));
            }
        });
    }

    /**
     * Tandai order write-ahead sebagai sudah diterima server.
     *
     * Dipakai setelah POST sukses pada pola write-ahead: barisnya sudah ditulis
     * ke Room sebelum request dikirim, jadi yang tersisa hanya menutupnya.
     * Kalau langkah ini gagal, order paling banter tersinkron ulang dan dedup
     * `client_order_id` di server menahannya - jauh lebih aman daripada order
     * yang tidak pernah tercatat sama sekali.
     */
    public void markWriteAheadSynced(@NonNull String localOrderId) {
        String clean = safe(localOrderId);
        if (clean.isEmpty()) return;
        executor.execute(() -> {
            try {
                db.pendingOrderDao().markSynced(clean, System.currentTimeMillis());
                Log.i(TAG, "Write-ahead order marked synced localOrderId=" + clean);
            } catch (Exception e) {
                Log.e(TAG, "Unable to mark write-ahead order synced localOrderId=" + clean, e);
            }
        });
    }

    /**
     * Server menjawab dengan status HTTP tapi menolak order.
     * FAILED masih ikut terjaring sync berikutnya (sampai MAX_SYNC_ATTEMPTS),
     * jadi kegagalan sementara seperti 5xx tetap punya kesempatan.
     */
    public void markWriteAheadFailed(@NonNull String localOrderId, @Nullable String error) {
        String clean = safe(localOrderId);
        if (clean.isEmpty()) return;
        executor.execute(() -> {
            try {
                db.pendingOrderDao().markFailed(clean, System.currentTimeMillis(), safe(error));
                Log.w(TAG, "Write-ahead order marked failed localOrderId=" + clean + " error=" + safe(error));
            } catch (Exception e) {
                Log.e(TAG, "Unable to mark write-ahead order failed localOrderId=" + clean, e);
            }
        });
    }

    /**
     * Order ditolak karena sesuatu yang tidak boleh diulang otomatis - jam
     * perangkat melenceng, misalnya. Sync otomatis mengirim ulang dengan
     * `is_offline_sync=true`, dan backend melewati pemeriksaan jam untuk
     * payload offline, jadi retry otomatis justru akan meloloskan order yang
     * baru saja ditolak. NEEDS_REVIEW menahannya sampai ada orang yang menilai.
     */
    public void markWriteAheadNeedsReview(@NonNull String localOrderId, @Nullable String error) {
        String clean = safe(localOrderId);
        if (clean.isEmpty()) return;
        executor.execute(() -> {
            try {
                db.pendingOrderDao().markNeedsReviewNoAttemptIncrement(
                        clean, System.currentTimeMillis(), safe(error));
                Log.w(TAG, "Write-ahead order needs review localOrderId=" + clean + " error=" + safe(error));
            } catch (Exception e) {
                Log.e(TAG, "Unable to mark write-ahead order for review localOrderId=" + clean, e);
            }
        });
    }

    public void syncPendingOrders(@Nullable String token) {
        syncPendingOrders(token, null);
    }

    public void syncPendingOrders(@Nullable String token, @Nullable SyncCallback callback) {
        String cleanToken = safe(token);
        if (cleanToken.isEmpty()) {
            postSyncError(callback, "Token is missing. Please login again.");
            return;
        }
        if (!NetworkUtils.isNetworkAvailable(appContext)) {
            postSyncError(callback, "No internet connection.");
            return;
        }
        if (!SYNC_RUNNING.compareAndSet(false, true)) {
            postSyncError(callback, "Sync already running");
            return;
        }

        executor.execute(() -> {
            try {
                db.pendingOrderDao().resetSyncingToPending(System.currentTimeMillis());
                ShopContext currentContext = currentShopContext();
                if (!hasUsableShopContext(currentContext)) {
                    SYNC_RUNNING.set(false);
                    postSyncError(callback, MESSAGE_UNKNOWN_SHOP_CONTEXT);
                    return;
                }
                List<PendingOrderEntity> orders = db.pendingOrderDao()
                        .getSyncableOrdersForShop(currentContext.shopId, currentContext.shopCode);
                List<PendingOrderEntity> safeOrders = orders != null ? orders : new ArrayList<>();
                if (safeOrders.isEmpty()) {
                    SYNC_RUNNING.set(false);
                    postSyncComplete(callback, "No pending or failed orders.");
                    return;
                }
                syncNext(cleanToken, safeOrders, 0, false, callback);
            } catch (Exception e) {
                Log.e(TAG, "Unable to load pending orders for sync", e);
                SYNC_RUNNING.set(false);
                postSyncError(callback, "Unable to load pending orders: " + safe(e.getMessage()));
            }
        });
    }

    public void retryAllPendingAndFailed(@Nullable String token, @Nullable SyncCallback callback) {
        syncPendingOrders(token, callback);
    }

    public void retryOrder(@NonNull String localOrderId,
                           @Nullable String token,
                           @NonNull SyncCallback callback) {
        String cleanLocalOrderId = safe(localOrderId);
        String cleanToken = safe(token);
        if (cleanLocalOrderId.isEmpty()) {
            postSyncError(callback, "Order id is missing.");
            return;
        }
        if (cleanToken.isEmpty()) {
            postSyncError(callback, "Token is missing. Please login again.");
            return;
        }
        if (!NetworkUtils.isNetworkAvailable(appContext)) {
            postSyncError(callback, "No internet connection.");
            return;
        }
        if (!SYNC_RUNNING.compareAndSet(false, true)) {
            postSyncError(callback, "Sync already running");
            return;
        }

        executor.execute(() -> {
            try {
                db.pendingOrderDao().resetSyncingToPending(System.currentTimeMillis());
                PendingOrderEntity order = db.pendingOrderDao().getByLocalOrderId(cleanLocalOrderId);
                if (order == null) {
                    SYNC_RUNNING.set(false);
                    postSyncError(callback, "Order not found.");
                    return;
                }
                ShopContext currentContext = currentShopContext();
                if (!shopContextMatches(order, currentContext)) {
                    logShopGuard("Retry one blocked", order, currentContext, false);
                    db.pendingOrderDao().markShopMismatch(
                            order.localOrderId,
                            System.currentTimeMillis(),
                            shopGuardMessage(order)
                    );
                    SYNC_RUNNING.set(false);
                    postSyncError(callback, shopGuardMessage(order));
                    return;
                }
                if (STATUS_SYNCED.equals(order.syncStatus)) {
                    SYNC_RUNNING.set(false);
                    postSyncComplete(callback, "Order already synced.");
                    return;
                }

                syncNext(cleanToken,
                        Collections.singletonList(order),
                        0,
                        STATUS_NEEDS_REVIEW.equals(order.syncStatus),
                        new SyncCallback() {
                            @Override
                            public void onComplete(@NonNull String message) {
                                executor.execute(() -> {
                                    PendingOrderEntity current = db.pendingOrderDao()
                                            .getByLocalOrderId(cleanLocalOrderId);
                                    if (current != null && STATUS_SYNCED.equals(current.syncStatus)) {
                                        postSyncComplete(callback, "Order synced successfully");
                                    } else {
                                        postSyncError(callback, "Sync failed. Please check error details.");
                                    }
                                });
                            }

                            @Override
                            public void onError(@NonNull String message) {
                                postSyncError(callback, message);
                            }
                        });
            } catch (Exception e) {
                SYNC_RUNNING.set(false);
                Log.e(TAG, "Unable to retry pending order localOrderId=" + cleanLocalOrderId, e);
                postSyncError(callback, "Unable to retry order: " + safe(e.getMessage()));
            }
        });
    }

    public void getPendingOrderSummary(@NonNull SummaryCallback callback) {
        executor.execute(() -> {
            try {
                ShopContext current = currentShopContext();
                int pendingCount = 0;
                int failedCount = 0;
                int needsReviewCount = 0;
                int syncedCount = 0;
                int otherShopCount = db.pendingOrderDao()
                        .countOrdersNotForShop(current.shopId, current.shopCode);
                if (hasUsableShopContext(current)) {
                    pendingCount = db.pendingOrderDao()
                            .countPendingSyncForShop(current.shopId, current.shopCode);
                    failedCount = db.pendingOrderDao()
                            .countFailedForShop(current.shopId, current.shopCode);
                    needsReviewCount = db.pendingOrderDao()
                            .countNeedsReviewForShop(current.shopId, current.shopCode);
                    syncedCount = db.pendingOrderDao()
                            .countSyncedForShop(current.shopId, current.shopCode);
                }
                OfflineOrderSummary summary = new OfflineOrderSummary(
                        pendingCount,
                        failedCount,
                        needsReviewCount,
                        syncedCount,
                        otherShopCount
                );
                mainHandler.post(() -> callback.onSuccess(summary));
            } catch (Exception e) {
                Log.e(TAG, "Unable to load pending order summary", e);
                mainHandler.post(() -> callback.onError(safe(e.getMessage())));
            }
        });
    }

    public void getOfflineOrders(@NonNull OrderListCallback callback) {
        executor.execute(() -> {
            try {
                List<PendingOrderEntity> orders = db.pendingOrderDao().getAllOfflineOrders();
                mainHandler.post(() -> callback.onSuccess(orders != null ? orders : new ArrayList<>()));
            } catch (Exception e) {
                Log.e(TAG, "Unable to load offline orders", e);
                mainHandler.post(() -> callback.onError(safe(e.getMessage())));
            }
        });
    }

    public void getOfflineOrdersByStatus(@NonNull String status,
                                         @NonNull OrderListCallback callback) {
        executor.execute(() -> {
            try {
                List<PendingOrderEntity> orders = db.pendingOrderDao()
                        .getOfflineOrdersByStatus(safe(status));
                mainHandler.post(() -> callback.onSuccess(orders != null ? orders : new ArrayList<>()));
            } catch (Exception e) {
                Log.e(TAG, "Unable to load offline orders by status", e);
                mainHandler.post(() -> callback.onError(safe(e.getMessage())));
            }
        });
    }

    public void getCurrentShopOfflineOrders(@Nullable String status,
                                            @NonNull OrderListCallback callback) {
        executor.execute(() -> {
            try {
                ShopContext current = currentShopContext();
                if (!hasUsableShopContext(current)) {
                    mainHandler.post(() -> callback.onSuccess(new ArrayList<>()));
                    return;
                }
                List<PendingOrderEntity> orders;
                if (safe(status).isEmpty()) {
                    orders = db.pendingOrderDao()
                            .getOrdersForShop(current.shopId, current.shopCode);
                } else {
                    orders = db.pendingOrderDao()
                            .getOrdersForShopByStatus(current.shopId, current.shopCode, safe(status));
                }
                mainHandler.post(() -> callback.onSuccess(orders != null ? orders : new ArrayList<>()));
            } catch (Exception e) {
                Log.e(TAG, "Unable to load current-shop offline orders", e);
                mainHandler.post(() -> callback.onError(safe(e.getMessage())));
            }
        });
    }

    public void getOtherShopOfflineOrders(@NonNull OrderListCallback callback) {
        executor.execute(() -> {
            try {
                ShopContext current = currentShopContext();
                List<PendingOrderEntity> orders = db.pendingOrderDao()
                        .getOrdersNotForShop(current.shopId, current.shopCode);
                mainHandler.post(() -> callback.onSuccess(orders != null ? orders : new ArrayList<>()));
            } catch (Exception e) {
                Log.e(TAG, "Unable to load other-shop offline orders", e);
                mainHandler.post(() -> callback.onError(safe(e.getMessage())));
            }
        });
    }

    public void getNeedsReviewOfflineOrdersForUi(@NonNull OrderListCallback callback) {
        executor.execute(() -> {
            try {
                ShopContext current = currentShopContext();
                List<PendingOrderEntity> orders;
                if (hasUsableShopContext(current)) {
                    orders = db.pendingOrderDao()
                            .getNeedsReviewOrdersForUi(current.shopId, current.shopCode);
                } else {
                    orders = db.pendingOrderDao()
                            .getOfflineOrdersByStatus(STATUS_NEEDS_REVIEW);
                }
                mainHandler.post(() -> callback.onSuccess(orders != null ? orders : new ArrayList<>()));
            } catch (Exception e) {
                Log.e(TAG, "Unable to load needs-review offline orders", e);
                mainHandler.post(() -> callback.onError(safe(e.getMessage())));
            }
        });
    }

    public void getOrderItems(@NonNull String localOrderId,
                              @NonNull OrderItemsCallback callback) {
        executor.execute(() -> {
            try {
                List<PendingOrderItemEntity> items = db.pendingOrderDao()
                        .getItemsByLocalOrderId(safe(localOrderId));
                mainHandler.post(() -> callback.onSuccess(items != null ? items : new ArrayList<>()));
            } catch (Exception e) {
                Log.e(TAG, "Unable to load pending order items", e);
                mainHandler.post(() -> callback.onError(safe(e.getMessage())));
            }
        });
    }

    private void syncNext(@NonNull String token,
                          @NonNull List<PendingOrderEntity> orders,
                          int index) {
        syncNext(token, orders, index, false, null);
    }

    private void syncNext(@NonNull String token,
                          @NonNull List<PendingOrderEntity> orders,
                          int index,
                          boolean allowNeedsReviewRetry,
                          @Nullable SyncCallback callback) {
        if (index >= orders.size()) {
            SYNC_RUNNING.set(false);
            postSyncComplete(callback, "Sync finished");
            return;
        }

        PendingOrderEntity order = orders.get(index);
        if (order == null || safe(order.localOrderId).isEmpty()) {
            syncNext(token, orders, index + 1, allowNeedsReviewRetry, callback);
            return;
        }

        ShopContext currentContext = currentShopContext();
        if (!shopContextMatches(order, currentContext)) {
            logShopGuard("Auto sync blocked", order, currentContext, false);
            markShopMismatchAndContinue(order, token, orders, index, allowNeedsReviewRetry, callback);
            return;
        }
        logShopGuard("Auto sync allowed", order, currentContext, true);

        boolean explicitNeedsReviewRetry = allowNeedsReviewRetry
                && STATUS_NEEDS_REVIEW.equals(order.syncStatus);
        if (!explicitNeedsReviewRetry && order.syncAttemptCount >= MAX_SYNC_ATTEMPTS) {
            markNeedsReviewWithoutAttemptAndContinue(order.localOrderId,
                    "Max sync attempts reached",
                    token,
                    orders,
                    index,
                    allowNeedsReviewRetry,
                    callback);
            return;
        }

        executor.execute(() -> {
            try {
                db.pendingOrderDao().updateStatus(order.localOrderId, STATUS_SYNCING, System.currentTimeMillis());
            } catch (Exception e) {
                Log.e(TAG, "Failed to mark order syncing localOrderId=" + order.localOrderId, e);
                syncNext(token, orders, index + 1, allowNeedsReviewRetry, callback);
                return;
            }

            JSONObject payload;
            try {
                payload = payloadForOfflineSync(order);
                String backfilledClientOrderId = payload.optString("client_order_id", "");
                if (!safe(backfilledClientOrderId).isEmpty()
                        && !safe(backfilledClientOrderId).equals(safe(order.clientOrderId))) {
                    db.pendingOrderDao().updateClientOrderId(
                            order.localOrderId,
                            backfilledClientOrderId,
                            System.currentTimeMillis()
                    );
                    order.clientOrderId = backfilledClientOrderId;
                    Log.i(TAG, "Backfilled pending order client_order_id localOrderId="
                            + order.localOrderId
                            + " client_order_id=" + backfilledClientOrderId);
                }
            } catch (Exception e) {
                markFailedAndContinue(order.localOrderId,
                        "Invalid local payload: " + e.getMessage(),
                        token,
                        orders,
                        index,
                        allowNeedsReviewRetry,
                        callback);
                return;
            }

            Log.i(TAG, "Syncing pending order localOrderId=" + order.localOrderId
                    + " client_order_id=" + payload.optString("client_order_id", "")
                    + " attempt=" + (order.syncAttemptCount + 1));

            new OrderRepository(appContext).createOrder(token, payload, new OrderRepository.CreateCallback() {
                @Override
                public void onSuccess(@NonNull JSONObject response) {
                    executor.execute(() -> {
                        db.pendingOrderDao().markSynced(order.localOrderId, System.currentTimeMillis());
                        Log.i(TAG, "Pending order synced localOrderId=" + order.localOrderId
                                + " client_order_id=" + payload.optString("client_order_id", "")
                                + " response=" + response.toString());
                        syncNext(token, orders, index + 1, allowNeedsReviewRetry, callback);
                    });
                }

                @Override
                public void onError(int statusCode, @NonNull String message) {
                    if (shouldSaveOffline(appContext, statusCode, message)) {
                        executor.execute(() -> {
                            // A no-response/network drop is not counted as a backend retry failure.
                            // Keep it pending and stop the batch so later orders are not marked failed while offline.
                            db.pendingOrderDao().updateStatus(order.localOrderId,
                                    STATUS_PENDING_SYNC,
                                    System.currentTimeMillis());
                            Log.w(TAG, "Network dropped during sync. Stop processing pending orders. localOrderId="
                                    + order.localOrderId);
                            SYNC_RUNNING.set(false);
                            postSyncError(callback, "Sync failed. Please check error details.");
                        });
                        return;
                    }
                    markFailedAndContinue(order.localOrderId,
                            message,
                            token,
                            orders,
                            index,
                            allowNeedsReviewRetry,
                            callback);
                }
            });
        });
    }

    @NonNull
    private JSONObject payloadForOfflineSync(@NonNull PendingOrderEntity order) throws Exception {
        JSONObject payload = new JSONObject(order.rawPayloadJson);

        String clientOrderId = firstNonEmpty(
                payload.optString("client_order_id", ""),
                firstNonEmpty(order.clientOrderId, order.localOrderId)
        );
        payload.put("client_order_id", clientOrderId);
        payload.put("is_offline_sync", true);

        if (safe(payload.optString("offline_created_at", "")).isEmpty()) {
            String offlineCreatedAt = firstNonEmpty(
                    payload.optString("device_time", ""),
                    currentDeviceTimeIso(order.createdAt)
            );
            payload.put("offline_created_at", offlineCreatedAt);
        }

        return payload;
    }

    private void markFailedAndContinue(@NonNull String localOrderId,
                                       @NonNull String message,
                                       @NonNull String token,
                                       @NonNull List<PendingOrderEntity> orders,
                                       int index,
                                       boolean allowNeedsReviewRetry,
                                       @Nullable SyncCallback callback) {
        executor.execute(() -> {
            PendingOrderEntity current = db.pendingOrderDao().getByLocalOrderId(localOrderId);
            int nextAttempt = current == null ? 1 : current.syncAttemptCount + 1;
            if (nextAttempt >= MAX_SYNC_ATTEMPTS) {
                db.pendingOrderDao().markNeedsReview(localOrderId, System.currentTimeMillis(), safe(message));
                Log.w(TAG, "Pending order needs review localOrderId=" + localOrderId
                        + " attempts=" + nextAttempt
                        + " error=" + safe(message));
            } else {
                db.pendingOrderDao().markFailed(localOrderId, System.currentTimeMillis(), safe(message));
                Log.w(TAG, "Pending order sync failed localOrderId=" + localOrderId
                        + " attempts=" + nextAttempt
                        + " error=" + safe(message));
            }
            syncNext(token, orders, index + 1, allowNeedsReviewRetry, callback);
        });
    }

    private void markNeedsReviewAndContinue(@NonNull String localOrderId,
                                            @NonNull String message,
                                            @NonNull String token,
                                            @NonNull List<PendingOrderEntity> orders,
                                            int index,
                                            boolean allowNeedsReviewRetry,
                                            @Nullable SyncCallback callback) {
        executor.execute(() -> {
            db.pendingOrderDao().markNeedsReview(localOrderId, System.currentTimeMillis(), safe(message));
            Log.w(TAG, "Pending order moved to NEEDS_REVIEW localOrderId=" + localOrderId);
            syncNext(token, orders, index + 1, allowNeedsReviewRetry, callback);
        });
    }

    private void markNeedsReviewWithoutAttemptAndContinue(@NonNull String localOrderId,
                                                          @NonNull String message,
                                                          @NonNull String token,
                                                          @NonNull List<PendingOrderEntity> orders,
                                                          int index,
                                                          boolean allowNeedsReviewRetry,
                                                          @Nullable SyncCallback callback) {
        executor.execute(() -> {
            db.pendingOrderDao().markNeedsReviewNoAttemptIncrement(
                    localOrderId,
                    System.currentTimeMillis(),
                    safe(message)
            );
            Log.w(TAG, "Pending order moved to NEEDS_REVIEW without increment localOrderId="
                    + localOrderId);
            syncNext(token, orders, index + 1, allowNeedsReviewRetry, callback);
        });
    }

    private void markShopMismatchAndContinue(@NonNull PendingOrderEntity order,
                                             @NonNull String token,
                                             @NonNull List<PendingOrderEntity> orders,
                                             int index,
                                             boolean allowNeedsReviewRetry,
                                             @Nullable SyncCallback callback) {
        executor.execute(() -> {
            db.pendingOrderDao().markShopMismatch(
                    order.localOrderId,
                    System.currentTimeMillis(),
                    shopGuardMessage(order)
            );
            Log.w(TAG, "Pending order shop guard blocked localOrderId=" + order.localOrderId
                    + " reason=" + shopGuardMessage(order));
            syncNext(token, orders, index + 1, allowNeedsReviewRetry, callback);
        });
    }

    private void postSyncComplete(@Nullable SyncCallback callback, @NonNull String message) {
        if (callback != null) {
            mainHandler.post(() -> callback.onComplete(message));
        }
    }

    private void postSyncError(@Nullable SyncCallback callback, @NonNull String message) {
        if (callback != null) {
            mainHandler.post(() -> callback.onError(message));
        }
    }

    @NonNull
    private PendingOrderEntity buildOrderEntity(@NonNull String localOrderId,
                                                @NonNull JSONObject payload,
                                                @Nullable String businessType,
                                                long now) {
        PendingOrderEntity order = new PendingOrderEntity();
        order.localOrderId = localOrderId;
        ensureClientOrderId(payload, firstNonEmpty(payload.optString("client_order_id", ""), localOrderId));
        order.rawPayloadJson = payload.toString();
        order.clientOrderId = payload.optString("client_order_id", localOrderId);
        order.businessType = safe(businessType);
        ShopContext shopContext = currentShopContext();
        order.shopId = shopContext.shopId;
        order.shopCode = shopContext.shopCode;
        order.shopName = shopContext.shopName;
        order.apiBaseUrl = shopContext.apiBaseUrl;
        order.createdByUserId = shopContext.createdByUserId;
        order.createdByUsername = shopContext.createdByUsername;
        if (!hasUsableShopContext(order)) {
            order.syncStatus = STATUS_NEEDS_REVIEW;
            order.lastSyncError = MESSAGE_UNKNOWN_SHOP_CONTEXT;
        }
        order.customerId = firstNullableInt(payload, "customer", "customer_id");
        order.paymentMethodId = firstNullableInt(
                firstPayment(payload),
                "payment_method_id",
                "payment_method"
        );
        if (order.paymentMethodId == null) {
            order.paymentMethodId = nullableInt(payload, "payment_method");
        }
        order.bankAccountId = firstNullableInt(firstPayment(payload), "bank_account_id", "bank_account");
        if (order.bankAccountId == null) {
            order.bankAccountId = firstNullableInt(payload, "bank_account_id", "bank_account");
        }
        // Payload sudah membawa teks desimal setelah migrasi uang; dibaca apa
        // adanya supaya tidak bolak-balik lewat double.
        order.subtotal = Money.of(payload.optString("subtotal", "")).toPlainString();
        order.discount = Money.of(payload.optString("discount", "")).toPlainString();
        order.tax = Money.of(payload.optString("tax", "")).toPlainString();
        order.total = Money.of(payload.optString("total", "")).toPlainString();
        Money paid = firstPositiveMoney(
                Money.of(firstPayment(payload).optString("amount", "")),
                Money.of(payload.optString("cash_received", "")),
                order.totalMoney()
        );
        order.paidAmount = paid.toPlainString();
        order.changeAmount = Money.of(payload.optString("change_amount", "")).toPlainString();
        order.orderType = firstNonEmpty(
                payload.optString("default_order_type", ""),
                firstItem(payload).optString("order_type", "")
        );
        order.note = firstNonEmpty(payload.optString("notes", ""), payload.optString("note", ""));
        order.createdAt = now;
        order.updatedAt = now;
        if (!STATUS_NEEDS_REVIEW.equals(order.syncStatus)) {
            order.syncStatus = STATUS_PENDING_SYNC;
        }
        order.syncAttemptCount = 0;
        if (!STATUS_NEEDS_REVIEW.equals(order.syncStatus)) {
            order.lastSyncError = "";
        }
        return order;
    }

    @NonNull
    private List<PendingOrderItemEntity> buildItemEntities(@NonNull String localOrderId,
                                                           @NonNull JSONObject payload) {
        List<PendingOrderItemEntity> out = new ArrayList<>();
        JSONArray items = payload.optJSONArray("items");
        if (items == null) return out;

        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;

            PendingOrderItemEntity entity = new PendingOrderItemEntity();
            entity.localOrderId = localOrderId;
            entity.productId = firstInt(item, "product", "product_id", "service_package_id", "item_id");
            entity.itemType = toBackendItemType(item.optString("item_type", ""));
            entity.name = firstNonEmpty(item.optString("name", ""), item.optString("product_name", ""));
            entity.sku = item.optString("sku", "");
            entity.barcode = firstNonEmpty(item.optString("barcode", ""), item.optString("code", ""));
            entity.quantity = Math.max(0, firstInt(item, "quantity", "qty"));
            Money unitPrice = Money.of(item.optString("price", ""));
            entity.price = unitPrice.toPlainString();
            entity.discount = Money.of(item.optString("discount", "")).toPlainString();
            entity.total = firstPositiveMoney(
                    Money.of(item.optString("total", "")),
                    Money.of(item.optString("subtotal", "")),
                    Money.of(item.optString("line_total", "")),
                    unitPrice.times(entity.quantity)
            ).toPlainString();
            entity.note = item.optString("note", "");
            out.add(entity);
        }
        return out;
    }

    @NonNull
    private static Money firstPositiveMoney(@NonNull Money... candidates) {
        for (Money m : candidates) {
            if (m != null && m.isPositive()) return m;
        }
        return Money.zero();
    }

    @NonNull
    private static JSONObject firstPayment(@NonNull JSONObject payload) {
        JSONArray payments = payload.optJSONArray("payments");
        if (payments == null || payments.length() == 0) return new JSONObject();
        JSONObject payment = payments.optJSONObject(0);
        return payment != null ? payment : new JSONObject();
    }

    @NonNull
    private static JSONObject firstItem(@NonNull JSONObject payload) {
        JSONArray items = payload.optJSONArray("items");
        if (items == null || items.length() == 0) return new JSONObject();
        JSONObject item = items.optJSONObject(0);
        return item != null ? item : new JSONObject();
    }

    @Nullable
    private static Integer firstNullableInt(@NonNull JSONObject object, @NonNull String... keys) {
        for (String key : keys) {
            Integer value = nullableInt(object, key);
            if (value != null) return value;
        }
        return null;
    }

    private static int firstInt(@NonNull JSONObject object, @NonNull String... keys) {
        Integer value = firstNullableInt(object, keys);
        return value != null ? value : 0;
    }

    @Nullable
    private static Integer nullableInt(@NonNull JSONObject object, @NonNull String key) {
        if (!object.has(key) || object.isNull(key)) return null;
        Object value = object.opt(key);
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            String s = String.valueOf(value).trim();
            if (s.isEmpty()) return null;
            return Integer.parseInt(s);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static double optDouble(@NonNull JSONObject object, @NonNull String key) {
        if (!object.has(key) || object.isNull(key)) return 0d;
        Object value = object.opt(key);
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            String s = String.valueOf(value).replace("$", "").replace(",", "").trim();
            return TextUtils.isEmpty(s) ? 0d : Double.parseDouble(s);
        } catch (Exception ignored) {
            return 0d;
        }
    }

    private static double firstPositive(double... values) {
        for (double value : values) {
            if (value > 0d) return value;
        }
        return 0d;
    }

    @NonNull
    private static String currentDeviceTimeIso(long timestampMillis) {
        long safeTimestamp = timestampMillis > 0L ? timestampMillis : System.currentTimeMillis();
        return new java.text.SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                Locale.US
        ).format(new java.util.Date(safeTimestamp));
    }

    @NonNull
    private static String toBackendItemType(@Nullable String itemType) {
        String value = safe(itemType).toUpperCase(Locale.US);
        switch (value) {
            case "MENU":
                return "menu";
            case "SERVICE":
            case "SERVICE_PACKAGE":
            case "SERVICEPACKAGE":
            case "PACKAGE":
                return "service";
            case "SPAREPART":
            case "PART":
                return "sparepart";
            case "PRODUCT":
            default:
                return "product";
        }
    }

    @NonNull
    private ShopContext currentShopContext() {
        SessionManager session = new SessionManager(appContext);
        ShopContext context = new ShopContext();
        boolean loggedIn = session.isLoggedIn();
        context.shopId = loggedIn ? session.getShopId() : 0;
        context.shopCode = loggedIn ? safe(session.getShopCode()) : "";
        context.shopName = loggedIn ? safe(session.getShopName()) : "";
        context.apiBaseUrl = normalizeBaseUrl(session.getBaseUrl());
        context.createdByUserId = "";
        context.createdByUsername = loggedIn ? safe(session.getUsername()) : "";
        return context;
    }

    private static boolean shopContextMatches(@NonNull PendingOrderEntity order,
                                              @NonNull ShopContext current) {
        if (!hasUsableShopContext(order) || !hasUsableShopContext(current)) {
            return false;
        }

        if (order.shopId != current.shopId) {
            return false;
        }

        String originalCode = safe(order.shopCode);
        if (!originalCode.equalsIgnoreCase(safe(current.shopCode))) {
            return false;
        }

        String originalBase = normalizeBaseUrl(order.apiBaseUrl);
        if (!originalBase.isEmpty()
                && !originalBase.equals(normalizeBaseUrl(current.apiBaseUrl))) {
            return false;
        }

        return true;
    }

    private static boolean hasUsableShopContext(@NonNull PendingOrderEntity order) {
        return order.shopId > 0 && !safe(order.shopCode).isEmpty();
    }

    private static boolean hasUsableShopContext(@NonNull ShopContext context) {
        return context.shopId > 0 && !safe(context.shopCode).isEmpty();
    }

    @NonNull
    private static String shopGuardMessage(@NonNull PendingOrderEntity order) {
        return hasUsableShopContext(order) ? MESSAGE_SHOP_MISMATCH : MESSAGE_UNKNOWN_SHOP_CONTEXT;
    }

    private static void logShopGuard(@NonNull String prefix,
                                     @NonNull PendingOrderEntity order,
                                     @NonNull ShopContext current,
                                     boolean match) {
        Log.w(TAG, prefix
                + " localOrderId=" + safe(order.localOrderId)
                + " pending.shopId=" + order.shopId
                + " pending.shopCode=" + safe(order.shopCode)
                + " pending.shopName=" + safe(order.shopName)
                + " current.shopId=" + current.shopId
                + " current.shopCode=" + safe(current.shopCode)
                + " current.shopName=" + safe(current.shopName)
                + " match=" + match);
    }

    @NonNull
    private static String normalizeBaseUrl(@Nullable String value) {
        String clean = safe(value).trim().toLowerCase(Locale.US);
        while (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        return clean;
    }

    @NonNull
    private static String firstNonEmpty(@Nullable String first, @Nullable String second) {
        String a = safe(first);
        return !a.isEmpty() ? a : safe(second);
    }

    @NonNull
    private static String safe(@Nullable String value) {
        if (value == null) return "";
        String clean = value.trim();
        return "null".equalsIgnoreCase(clean) ? "" : clean;
    }

    private static final class ShopContext {
        int shopId = 0;
        @NonNull String shopCode = "";
        @NonNull String shopName = "";
        @NonNull String apiBaseUrl = "";
        @NonNull String createdByUserId = "";
        @NonNull String createdByUsername = "";
    }
}
