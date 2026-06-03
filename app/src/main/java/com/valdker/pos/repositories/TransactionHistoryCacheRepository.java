package com.valdker.pos.repositories;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.valdker.pos.SessionManager;
import com.valdker.pos.local.CachedBankLedgerEntity;
import com.valdker.pos.local.CachedExpenseEntity;
import com.valdker.pos.local.CachedOrderEntity;
import com.valdker.pos.local.CachedOrderItemEntity;
import com.valdker.pos.local.CachedPaymentEntity;
import com.valdker.pos.local.ValoraLocalDatabase;
import com.valdker.pos.models.BankLedger;
import com.valdker.pos.models.Expense;
import com.valdker.pos.models.Order;
import com.valdker.pos.models.OrderItem;
import com.valdker.pos.network.BankLedgerApi;
import com.valdker.pos.utils.NetworkUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TransactionHistoryCacheRepository {

    public static final String NO_LOCAL_DATA_MESSAGE = "No local data found. Please connect to internet and sync once.";
    public static final String INTERNET_REQUIRED_MESSAGE = "This action requires internet connection.";

    public interface RoomFirstCallback<T> {
        void onLocal(@NonNull List<T> list);
        void onRemote(@NonNull List<T> list);
        void onNoInternet(boolean hasLocalData);
        void onError(int statusCode, @NonNull String message, boolean hasLocalData);
    }

    private final Context appContext;
    private final SessionManager sessionManager;
    private final ValoraLocalDatabase db;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public TransactionHistoryCacheRepository(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.sessionManager = new SessionManager(appContext);
        this.db = ValoraLocalDatabase.getInstance(appContext);
    }

    public boolean hasKnownShop() {
        return currentShopId() > 0 && !currentShopCode().isEmpty();
    }

    public void loadOrdersRoomFirst(@NonNull String token, @NonNull RoomFirstCallback<Order> callback) {
        executor.execute(() -> {
            List<Order> local = loadCachedOrders();
            postLocal(callback, local);

            if (!NetworkUtils.isNetworkAvailable(appContext)) {
                postNoInternet(callback, !local.isEmpty());
                return;
            }

            new OrderRepository(appContext).fetchOrders(token, new OrderRepository.Callback() {
                @Override
                public void onSuccess(@NonNull List<Order> orders) {
                    saveOrdersAndReload(orders, callback);
                }

                @Override
                public void onError(int statusCode, @NonNull String message) {
                    postError(callback, statusCode, message, !local.isEmpty());
                }
            });
        });
    }

    public void loadExpensesRoomFirst(@NonNull String token, @NonNull RoomFirstCallback<Expense> callback) {
        executor.execute(() -> {
            List<Expense> local = loadCachedExpenses();
            postLocal(callback, local);

            if (!NetworkUtils.isNetworkAvailable(appContext)) {
                postNoInternet(callback, !local.isEmpty());
                return;
            }

            new ExpenseRepository(appContext).fetchExpenses(token, new ExpenseRepository.ListCallback() {
                @Override
                public void onSuccess(@NonNull List<Expense> list) {
                    saveExpensesAndReload(list, callback);
                }

                @Override
                public void onError(int statusCode, @NonNull String message) {
                    postError(callback, statusCode, message, !local.isEmpty());
                }
            });
        });
    }

    public void loadBankLedgersRoomFirst(@NonNull RoomFirstCallback<BankLedger> callback) {
        executor.execute(() -> {
            List<BankLedger> local = loadCachedBankLedgers();
            postLocal(callback, local);

            if (!NetworkUtils.isNetworkAvailable(appContext)) {
                postNoInternet(callback, !local.isEmpty());
                return;
            }

            BankLedgerApi.getBankLedgers(appContext, sessionManager, new BankLedgerApi.BankLedgerListCallback() {
                @Override
                public void onSuccess(List<BankLedger> ledgers) {
                    saveBankLedgersAndReload(ledgers == null ? new ArrayList<>() : ledgers, callback);
                }

                @Override
                public void onError(String message) {
                    postError(callback, -1, message == null ? "Network error" : message, !local.isEmpty());
                }
            });
        });
    }

    public void loadPaymentsRoomFirst(@NonNull RoomFirstCallback<CachedPaymentEntity> callback) {
        executor.execute(() -> {
            List<CachedPaymentEntity> local = hasKnownShop()
                    ? db.cachedPaymentDao().getPaymentsForShop(currentShopId(), currentShopCode(), currentApiBaseUrl())
                    : new ArrayList<>();
            postLocal(callback, local);
            if (!NetworkUtils.isNetworkAvailable(appContext)) {
                postNoInternet(callback, !local.isEmpty());
            } else {
                postRemote(callback, local);
            }
        });
    }

    private List<Order> loadCachedOrders() {
        List<Order> out = new ArrayList<>();
        if (!hasKnownShop()) return out;

        List<CachedOrderEntity> orders = db.cachedOrderDao().getOrdersForShop(currentShopId(), currentShopCode(), currentApiBaseUrl());
        for (CachedOrderEntity entity : orders) {
            List<OrderItem> items = new ArrayList<>();
            for (CachedOrderItemEntity item : db.cachedOrderDao().getItemsForOrder(currentShopId(), currentShopCode(), currentApiBaseUrl(), entity.backendId)) {
                items.add(new OrderItem(item.productId, item.quantity, item.price, null));
            }
            out.add(new Order(
                    entity.backendId,
                    entity.customerId,
                    safe(entity.invoiceNumber),
                    safe(entity.createdAt),
                    safe(entity.paymentMethod),
                    entity.subtotal,
                    entity.discount,
                    entity.tax,
                    entity.total,
                    safe(entity.notes),
                    entity.isPaid,
                    items
            ));
        }
        return out;
    }

    private void saveOrdersAndReload(@NonNull List<Order> orders, @NonNull RoomFirstCallback<Order> callback) {
        executor.execute(() -> {
            if (!hasKnownShop()) {
                postRemote(callback, new ArrayList<>());
                return;
            }

            long syncAt = System.currentTimeMillis();
            List<CachedOrderEntity> orderEntities = new ArrayList<>();
            List<CachedOrderItemEntity> itemEntities = new ArrayList<>();

            for (Order order : orders) {
                if (order == null) continue;
                CachedOrderEntity entity = new CachedOrderEntity();
                entity.backendId = order.getId();
                entity.shopId = currentShopId();
                entity.shopCode = currentShopCode();
                entity.apiBaseUrl = currentApiBaseUrl();
                entity.cacheKey = cacheKey(entity.backendId);
                entity.invoiceNumber = safe(order.getInvoiceNumber());
                entity.customerId = order.getCustomerId();
                entity.subtotal = order.getSubtotal();
                entity.discount = order.getDiscount();
                entity.tax = order.getTax();
                entity.total = order.getTotal();
                entity.paymentMethod = safe(order.getPaymentMethod());
                entity.paymentStatus = order.isPaid() ? "PAID" : "UNPAID";
                entity.orderStatus = "";
                entity.orderType = "";
                entity.businessType = sessionManager.getBusinessType();
                entity.notes = safe(order.getNotes());
                entity.isPaid = order.isPaid();
                entity.itemsCount = order.getItemsCount();
                entity.cashierName = sessionManager.getUsername();
                entity.createdAt = safe(order.getCreatedAtIso());
                entity.rawJson = orderJson(entity).toString();
                entity.lastSyncAt = syncAt;
                orderEntities.add(entity);

                int index = 0;
                for (OrderItem item : order.getItems()) {
                    if (item == null) continue;
                    CachedOrderItemEntity itemEntity = new CachedOrderItemEntity();
                    itemEntity.backendId = index + 1;
                    itemEntity.orderBackendId = order.getId();
                    itemEntity.shopId = currentShopId();
                    itemEntity.shopCode = currentShopCode();
                    itemEntity.apiBaseUrl = currentApiBaseUrl();
                    itemEntity.productId = item.getProductId();
                    itemEntity.itemType = "";
                    itemEntity.quantity = item.getQuantity();
                    itemEntity.price = item.getPrice();
                    itemEntity.total = item.getLineTotal();
                    itemEntity.cacheKey = cacheKey(order.getId() + ":item:" + index + ":" + item.getProductId());
                    itemEntity.rawJson = orderItemJson(itemEntity).toString();
                    itemEntity.lastSyncAt = syncAt;
                    itemEntities.add(itemEntity);
                    index++;
                }
            }

            db.cachedOrderDao().replaceForShop(currentShopId(), currentShopCode(), currentApiBaseUrl(), orderEntities, itemEntities);
            postRemote(callback, loadCachedOrders());
        });
    }

    private List<Expense> loadCachedExpenses() {
        List<Expense> out = new ArrayList<>();
        if (!hasKnownShop()) return out;

        for (CachedExpenseEntity entity : db.cachedExpenseDao().getExpensesForShop(currentShopId(), currentShopCode(), currentApiBaseUrl())) {
            Expense expense = new Expense();
            expense.id = entity.backendId;
            expense.name = safe(entity.name);
            expense.note = safe(entity.note);
            expense.amount = safe(entity.amount);
            expense.date = safe(entity.date);
            expense.time = safe(entity.time);
            out.add(expense);
        }
        return out;
    }

    private void saveExpensesAndReload(@NonNull List<Expense> expenses, @NonNull RoomFirstCallback<Expense> callback) {
        executor.execute(() -> {
            if (!hasKnownShop()) {
                postRemote(callback, new ArrayList<>());
                return;
            }

            long syncAt = System.currentTimeMillis();
            List<CachedExpenseEntity> entities = new ArrayList<>();
            for (Expense expense : expenses) {
                if (expense == null) continue;
                CachedExpenseEntity entity = new CachedExpenseEntity();
                entity.backendId = expense.id;
                entity.shopId = currentShopId();
                entity.shopCode = currentShopCode();
                entity.apiBaseUrl = currentApiBaseUrl();
                entity.cacheKey = cacheKey(entity.backendId);
                entity.name = safe(expense.name);
                entity.title = safe(expense.name);
                entity.note = safe(expense.note);
                entity.amount = safe(expense.amount);
                entity.date = safe(expense.date);
                entity.time = safe(expense.time);
                entity.createdAt = safe(expense.date) + (safe(expense.time).isEmpty() ? "" : "T" + safe(expense.time));
                entity.rawJson = expenseJson(entity).toString();
                entity.lastSyncAt = syncAt;
                entities.add(entity);
            }
            db.cachedExpenseDao().replaceAllForShop(currentShopId(), currentShopCode(), currentApiBaseUrl(), entities);
            postRemote(callback, loadCachedExpenses());
        });
    }

    private List<BankLedger> loadCachedBankLedgers() {
        List<BankLedger> out = new ArrayList<>();
        if (!hasKnownShop()) return out;

        for (CachedBankLedgerEntity entity : db.cachedBankLedgerDao().getLedgersForShop(currentShopId(), currentShopCode(), currentApiBaseUrl())) {
            out.add(bankLedgerFromEntity(entity));
        }
        return out;
    }

    private void saveBankLedgersAndReload(@NonNull List<BankLedger> ledgers, @NonNull RoomFirstCallback<BankLedger> callback) {
        executor.execute(() -> {
            if (!hasKnownShop()) {
                postRemote(callback, new ArrayList<>());
                return;
            }

            long syncAt = System.currentTimeMillis();
            List<CachedBankLedgerEntity> entities = new ArrayList<>();
            for (BankLedger ledger : ledgers) {
                if (ledger == null) continue;
                CachedBankLedgerEntity entity = new CachedBankLedgerEntity();
                entity.backendId = ledger.getId();
                entity.shopId = currentShopId();
                entity.shopCode = currentShopCode();
                entity.apiBaseUrl = currentApiBaseUrl();
                entity.cacheKey = cacheKey(entity.backendId);
                entity.shopName = safe(ledger.getShopName()).isEmpty() ? sessionManager.getShopName() : safe(ledger.getShopName());
                entity.bankAccount = ledger.getBankAccount();
                entity.bankAccountName = safe(ledger.getBankAccountName());
                entity.transactionType = safe(ledger.getTransactionType());
                entity.direction = safe(ledger.getDirection());
                entity.amount = safe(ledger.getAmount());
                entity.balanceBefore = safe(ledger.getBalanceBefore());
                entity.balanceAfter = safe(ledger.getBalanceAfter());
                entity.referenceOrder = ledger.getReferenceOrder();
                entity.referenceOrderInvoice = safe(ledger.getReferenceOrderInvoice());
                entity.referencePayment = ledger.getReferencePayment();
                entity.description = safe(ledger.getDescription());
                entity.createdAt = safe(ledger.getCreatedAt());
                entity.createdBy = ledger.getCreatedBy();
                entity.rawJson = bankLedgerJson(entity).toString();
                entity.lastSyncAt = syncAt;
                entities.add(entity);
            }
            db.cachedBankLedgerDao().replaceAllForShop(currentShopId(), currentShopCode(), currentApiBaseUrl(), entities);
            postRemote(callback, loadCachedBankLedgers());
        });
    }

    private BankLedger bankLedgerFromEntity(@NonNull CachedBankLedgerEntity entity) {
        return BankLedger.fromJson(bankLedgerJson(entity));
    }

    private void postLocal(@NonNull RoomFirstCallback callback, @NonNull List list) {
        mainHandler.post(() -> callback.onLocal(list));
    }

    private void postRemote(@NonNull RoomFirstCallback callback, @NonNull List list) {
        mainHandler.post(() -> callback.onRemote(list));
    }

    private void postNoInternet(@NonNull RoomFirstCallback callback, boolean hasLocalData) {
        mainHandler.post(() -> callback.onNoInternet(hasLocalData));
    }

    private void postError(@NonNull RoomFirstCallback callback, int statusCode, @NonNull String message, boolean hasLocalData) {
        mainHandler.post(() -> callback.onError(statusCode, message, hasLocalData));
    }

    private int currentShopId() {
        return sessionManager.getShopId();
    }

    @NonNull
    private String currentShopCode() {
        return normalizeShopCode(sessionManager.getShopCode());
    }

    @NonNull
    private String currentApiBaseUrl() {
        return safe(sessionManager.getBaseUrl());
    }

    @NonNull
    private String cacheKey(int backendId) {
        return cacheKey(String.valueOf(backendId));
    }

    @NonNull
    private String cacheKey(@NonNull String backendId) {
        return currentApiBaseUrl() + "|" + currentShopId() + "|" + currentShopCode() + "|" + safe(backendId);
    }

    @NonNull
    private static String normalizeShopCode(String value) {
        return safe(value).trim().toUpperCase(Locale.US);
    }

    @NonNull
    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static JSONObject orderJson(@NonNull CachedOrderEntity entity) {
        JSONObject obj = new JSONObject();
        put(obj, "id", entity.backendId);
        put(obj, "invoice_number", entity.invoiceNumber);
        put(obj, "customer", entity.customerId);
        put(obj, "created_at", entity.createdAt);
        put(obj, "payment_method", entity.paymentMethod);
        put(obj, "subtotal", entity.subtotal);
        put(obj, "discount", entity.discount);
        put(obj, "tax", entity.tax);
        put(obj, "total", entity.total);
        put(obj, "notes", entity.notes);
        put(obj, "is_paid", entity.isPaid);
        return obj;
    }

    private static JSONObject orderItemJson(@NonNull CachedOrderItemEntity entity) {
        JSONObject obj = new JSONObject();
        put(obj, "product", entity.productId);
        put(obj, "item_type", entity.itemType);
        put(obj, "quantity", entity.quantity);
        put(obj, "price", entity.price);
        put(obj, "total", entity.total);
        return obj;
    }

    private static JSONObject expenseJson(@NonNull CachedExpenseEntity entity) {
        JSONObject obj = new JSONObject();
        put(obj, "id", entity.backendId);
        put(obj, "name", entity.name);
        put(obj, "note", entity.note);
        put(obj, "amount", entity.amount);
        put(obj, "date", entity.date);
        put(obj, "time", entity.time);
        return obj;
    }

    private static JSONObject bankLedgerJson(@NonNull CachedBankLedgerEntity entity) {
        JSONObject obj = new JSONObject();
        put(obj, "id", entity.backendId);
        put(obj, "shop_id", entity.shopId);
        put(obj, "shop_name", entity.shopName);
        put(obj, "shop_code", entity.shopCode);
        put(obj, "bank_account", entity.bankAccount);
        put(obj, "bank_account_name", entity.bankAccountName);
        put(obj, "transaction_type", entity.transactionType);
        put(obj, "direction", entity.direction);
        put(obj, "amount", entity.amount);
        put(obj, "balance_before", entity.balanceBefore);
        put(obj, "balance_after", entity.balanceAfter);
        put(obj, "reference_order", entity.referenceOrder);
        put(obj, "reference_order_invoice", entity.referenceOrderInvoice);
        put(obj, "reference_payment", entity.referencePayment);
        put(obj, "description", entity.description);
        put(obj, "created_at", entity.createdAt);
        put(obj, "created_by", entity.createdBy);
        return obj;
    }

    private static void put(@NonNull JSONObject obj, @NonNull String key, Object value) {
        try {
            obj.put(key, value);
        } catch (JSONException ignored) {
        }
    }
}
