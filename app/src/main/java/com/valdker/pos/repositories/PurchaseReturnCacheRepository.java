package com.valdker.pos.repositories;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.toolbox.StringRequest;
import com.valdker.pos.SessionManager;
import com.valdker.pos.local.CachedProductReturnEntity;
import com.valdker.pos.local.CachedProductReturnItemEntity;
import com.valdker.pos.local.CachedPurchaseEntity;
import com.valdker.pos.local.CachedPurchaseItemEntity;
import com.valdker.pos.local.ValoraLocalDatabase;
import com.valdker.pos.models.ProductReturn;
import com.valdker.pos.models.ProductReturnItem;
import com.valdker.pos.network.ApiClient;
import com.valdker.pos.network.ApiConfig;
import com.valdker.pos.ui.purchases.PurchaseLite;
import com.valdker.pos.utils.NetworkUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PurchaseReturnCacheRepository {

    private static final String TAG = "PURCHASE_RETURN_CACHE";
    private static final String PURCHASES_ENDPOINT = "api/purchases/";
    private static final String PRODUCT_RETURNS_ENDPOINT = "api/productreturns/";

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

    public PurchaseReturnCacheRepository(@NonNull Context context) {
        appContext = context.getApplicationContext();
        sessionManager = new SessionManager(appContext);
        db = ValoraLocalDatabase.getInstance(appContext);
    }

    public boolean hasKnownShop() {
        return currentShopId() > 0 && !currentShopCode().isEmpty();
    }

    public void loadPurchasesRoomFirst(@NonNull RoomFirstCallback<PurchaseLite> callback) {
        executor.execute(() -> {
            List<PurchaseLite> local = loadCachedPurchases();
            postLocal(callback, local);

            if (!NetworkUtils.isNetworkAvailable(appContext)) {
                postNoInternet(callback, !local.isEmpty());
                return;
            }

            fetchRawList(PURCHASES_ENDPOINT, PurchaseRepository.TAG_LIST, new RawListCallback() {
                @Override
                public void onSuccess(@NonNull JSONArray rows) {
                    savePurchasesAndReload(rows, callback);
                }

                @Override
                public void onError(int statusCode, @NonNull String message) {
                    postError(callback, statusCode, message, !local.isEmpty());
                }
            });
        });
    }

    public void loadProductReturnsRoomFirst(@NonNull RoomFirstCallback<ProductReturn> callback) {
        executor.execute(() -> {
            List<ProductReturn> local = loadCachedProductReturns();
            postLocal(callback, local);

            if (!NetworkUtils.isNetworkAvailable(appContext)) {
                postNoInternet(callback, !local.isEmpty());
                return;
            }

            fetchRawList(PRODUCT_RETURNS_ENDPOINT, "PRODUCT_RETURNS", new RawListCallback() {
                @Override
                public void onSuccess(@NonNull JSONArray rows) {
                    saveProductReturnsAndReload(rows, callback);
                }

                @Override
                public void onError(int statusCode, @NonNull String message) {
                    postError(callback, statusCode, message, !local.isEmpty());
                }
            });
        });
    }

    @NonNull
    private List<PurchaseLite> loadCachedPurchases() {
        List<PurchaseLite> out = new ArrayList<>();
        if (!hasKnownShop()) return out;

        for (CachedPurchaseEntity entity : db.cachedPurchaseDao().getPurchasesForShop(currentShopId(), currentShopCode(), currentApiBaseUrl())) {
            out.add(purchaseFromEntity(entity));
        }
        return out;
    }

    private void savePurchasesAndReload(@NonNull JSONArray rows, @NonNull RoomFirstCallback<PurchaseLite> callback) {
        executor.execute(() -> {
            if (!hasKnownShop()) {
                postRemote(callback, new ArrayList<>());
                return;
            }

            long syncAt = System.currentTimeMillis();
            List<CachedPurchaseEntity> purchases = new ArrayList<>();
            List<CachedPurchaseItemEntity> items = new ArrayList<>();

            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row == null) continue;

                CachedPurchaseEntity entity = purchaseEntityFromJson(row, syncAt);
                purchases.add(entity);

                JSONArray rowItems = row.optJSONArray("items");
                if (rowItems == null) {
                    rowItems = row.optJSONArray("purchase_items");
                }
                if (rowItems == null) continue;

                for (int itemIndex = 0; itemIndex < rowItems.length(); itemIndex++) {
                    JSONObject itemObj = rowItems.optJSONObject(itemIndex);
                    if (itemObj == null) continue;
                    items.add(purchaseItemEntityFromJson(entity.backendId, itemObj, itemIndex, syncAt));
                }
            }

            db.cachedPurchaseDao().replaceForShop(currentShopId(), currentShopCode(), currentApiBaseUrl(), purchases, items);
            postRemote(callback, loadCachedPurchases());
        });
    }

    @NonNull
    private CachedPurchaseEntity purchaseEntityFromJson(@NonNull JSONObject row, long syncAt) {
        CachedPurchaseEntity entity = new CachedPurchaseEntity();
        entity.backendId = row.optInt("id", 0);
        entity.shopId = currentShopId();
        entity.shopCode = currentShopCode();
        entity.apiBaseUrl = currentApiBaseUrl();
        entity.cacheKey = cacheKey(entity.backendId);
        entity.invoiceNumber = firstNonEmpty(row, "invoice_id", "invoice_number", "reference_no", "reference");
        entity.supplierId = intOrNestedId(row, "supplier_id", "supplier");
        entity.supplierName = firstNonEmpty(row, "supplier_name");
        JSONObject supplier = row.optJSONObject("supplier");
        if (entity.supplierName.isEmpty() && supplier != null) {
            entity.supplierName = firstNonEmpty(supplier, "name", "supplier_name");
        }
        entity.totalItems = row.optInt("items_count", row.optInt("total_items", 0));
        JSONArray rowItems = row.optJSONArray("items");
        if (entity.totalItems <= 0 && rowItems != null) {
            entity.totalItems = rowItems.length();
        }
        entity.totalAmount = firstNonEmpty(row, "total_cost", "total", "total_amount", "grand_total");
        entity.paidAmount = firstNonEmpty(row, "paid_amount", "paid");
        entity.status = firstNonEmpty(row, "status");
        entity.purchaseDate = firstNonEmpty(row, "purchase_date", "date");
        entity.createdAt = firstNonEmpty(row, "created_at", "created");
        entity.note = firstNonEmpty(row, "note", "notes");
        entity.createdBy = intOrNestedId(row, "created_by", "user");
        JSONObject user = row.optJSONObject("created_by");
        entity.createdByName = firstNonEmpty(row, "created_by_name", "created_by_username");
        if (entity.createdByName.isEmpty() && user != null) {
            entity.createdByName = firstNonEmpty(user, "display_name", "username", "name");
        }
        entity.rawJson = row.toString();
        entity.lastSyncAt = syncAt;
        return entity;
    }

    @NonNull
    private CachedPurchaseItemEntity purchaseItemEntityFromJson(int purchaseBackendId, @NonNull JSONObject row, int index, long syncAt) {
        CachedPurchaseItemEntity entity = new CachedPurchaseItemEntity();
        entity.backendId = row.optInt("id", index + 1);
        entity.purchaseBackendId = purchaseBackendId;
        entity.shopId = currentShopId();
        entity.shopCode = currentShopCode();
        entity.apiBaseUrl = currentApiBaseUrl();
        entity.cacheKey = cacheKey(purchaseBackendId + ":item:" + index + ":" + entity.backendId);
        entity.productId = intOrNestedId(row, "product_id", "product");
        entity.productName = firstNonEmpty(row, "product_name", "name");
        JSONObject product = row.optJSONObject("product");
        if (entity.productName.isEmpty() && product != null) {
            entity.productName = firstNonEmpty(product, "name", "product_name");
        }
        entity.unitName = firstNonEmpty(row, "unit_name", "product_unit_name");
        JSONObject unit = row.optJSONObject("unit");
        if (entity.unitName.isEmpty() && unit != null) {
            entity.unitName = firstNonEmpty(unit, "name", "unit_name");
        }
        entity.quantity = optDouble(row, "quantity", "qty");
        entity.price = firstNonEmpty(row, "price", "unit_price", "cost", "unit_cost");
        entity.cost = firstNonEmpty(row, "cost", "unit_cost", "buy_price", "price");
        entity.total = firstNonEmpty(row, "total", "line_total", "subtotal");
        if (entity.total.isEmpty()) {
            entity.total = String.valueOf(entity.quantity * parseDouble(entity.price));
        }
        entity.rawJson = row.toString();
        entity.lastSyncAt = syncAt;
        return entity;
    }

    @NonNull
    private PurchaseLite purchaseFromEntity(@NonNull CachedPurchaseEntity entity) {
        JSONObject obj = new JSONObject();
        put(obj, "id", entity.backendId);
        put(obj, "purchase_date", safe(entity.purchaseDate).isEmpty() ? entity.createdAt : entity.purchaseDate);
        put(obj, "invoice_id", entity.invoiceNumber);
        put(obj, "supplier_id", entity.supplierId);
        put(obj, "supplier_name", entity.supplierName);
        put(obj, "items_count", entity.totalItems);
        put(obj, "total_cost", entity.totalAmount);
        return PurchaseLite.fromJson(obj);
    }

    @NonNull
    private List<ProductReturn> loadCachedProductReturns() {
        List<ProductReturn> out = new ArrayList<>();
        if (!hasKnownShop()) return out;

        for (CachedProductReturnEntity entity : db.cachedProductReturnDao().getReturnsForShop(currentShopId(), currentShopCode(), currentApiBaseUrl())) {
            out.add(productReturnFromEntity(entity));
        }
        return out;
    }

    private void saveProductReturnsAndReload(@NonNull JSONArray rows, @NonNull RoomFirstCallback<ProductReturn> callback) {
        executor.execute(() -> {
            if (!hasKnownShop()) {
                postRemote(callback, new ArrayList<>());
                return;
            }

            long syncAt = System.currentTimeMillis();
            List<CachedProductReturnEntity> returns = new ArrayList<>();
            List<CachedProductReturnItemEntity> items = new ArrayList<>();

            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row == null) continue;

                CachedProductReturnEntity entity = productReturnEntityFromJson(row, syncAt);
                returns.add(entity);

                JSONArray itemRows = row.optJSONArray("items");
                if (itemRows == null) continue;
                for (int itemIndex = 0; itemIndex < itemRows.length(); itemIndex++) {
                    JSONObject itemRow = itemRows.optJSONObject(itemIndex);
                    if (itemRow == null) continue;
                    items.add(productReturnItemEntityFromJson(entity.backendId, itemRow, itemIndex, syncAt));
                }
            }

            db.cachedProductReturnDao().replaceForShop(currentShopId(), currentShopCode(), currentApiBaseUrl(), returns, items);
            postRemote(callback, loadCachedProductReturns());
        });
    }

    @NonNull
    private CachedProductReturnEntity productReturnEntityFromJson(@NonNull JSONObject row, long syncAt) {
        CachedProductReturnEntity entity = new CachedProductReturnEntity();
        entity.backendId = row.optInt("id", 0);
        entity.shopId = currentShopId();
        entity.shopCode = currentShopCode();
        entity.apiBaseUrl = currentApiBaseUrl();
        entity.cacheKey = cacheKey(entity.backendId);
        entity.returnNumber = firstNonEmpty(row, "return_number", "reference", "invoice_number");
        if (entity.returnNumber.isEmpty()) {
            entity.returnNumber = String.valueOf(entity.backendId);
        }
        entity.orderId = row.has("order") && !row.isNull("order") ? row.optInt("order") : null;
        entity.orderInvoice = firstNonEmpty(row, "invoice_number", "order_invoice", "order_reference");
        JSONObject customer = row.optJSONObject("customer");
        if (customer != null) {
            entity.customerId = customer.optInt("id", 0);
            entity.customerName = firstNonEmpty(customer, "name", "full_name", "customer_name");
        } else {
            entity.customerId = intOrNestedId(row, "customer_id", "customer");
            entity.customerName = firstNonEmpty(row, "customer_name");
        }
        entity.totalAmount = firstNonEmpty(row, "total_amount", "total", "grand_total");
        if (entity.totalAmount.isEmpty()) {
            entity.totalAmount = String.valueOf(ProductReturn.fromJson(row).totalAmount());
        }
        entity.reason = firstNonEmpty(row, "reason", "note");
        entity.note = firstNonEmpty(row, "note", "notes");
        entity.status = firstNonEmpty(row, "status");
        entity.returnedAt = firstNonEmpty(row, "returned_at", "return_date", "created_at");
        entity.createdAt = firstNonEmpty(row, "created_at", "returned_at");
        JSONObject user = row.optJSONObject("returned_by");
        if (user == null) {
            user = row.optJSONObject("created_by");
        }
        entity.createdBy = user != null ? user.optInt("id", 0) : intOrNestedId(row, "created_by", "returned_by");
        entity.createdByName = firstNonEmpty(row, "returned_by_name", "created_by_name");
        if (entity.createdByName.isEmpty() && user != null) {
            entity.createdByName = firstNonEmpty(user, "display_name", "username", "name");
        }
        entity.rawJson = row.toString();
        entity.lastSyncAt = syncAt;
        return entity;
    }

    @NonNull
    private CachedProductReturnItemEntity productReturnItemEntityFromJson(int returnBackendId, @NonNull JSONObject row, int index, long syncAt) {
        CachedProductReturnItemEntity entity = new CachedProductReturnItemEntity();
        entity.backendId = row.optInt("id", index + 1);
        entity.returnBackendId = returnBackendId;
        entity.shopId = currentShopId();
        entity.shopCode = currentShopCode();
        entity.apiBaseUrl = currentApiBaseUrl();
        entity.cacheKey = cacheKey(returnBackendId + ":item:" + index + ":" + entity.backendId);
        JSONObject product = row.optJSONObject("product");
        if (product != null) {
            entity.productId = product.optInt("id", 0);
            entity.productName = firstNonEmpty(product, "name", "product_name");
            entity.productCode = firstNonEmpty(product, "code");
            entity.productSku = firstNonEmpty(product, "sku");
        } else {
            entity.productId = intOrNestedId(row, "product_id", "product");
            entity.productName = firstNonEmpty(row, "product_name", "name");
        }
        entity.itemType = firstNonEmpty(row, "item_type", "itemType", "type");
        entity.quantity = optDouble(row, "quantity", "qty");
        entity.price = firstNonEmpty(row, "unit_price", "price");
        entity.total = firstNonEmpty(row, "total", "line_total", "subtotal");
        if (entity.total.isEmpty()) {
            entity.total = String.valueOf(entity.quantity * parseDouble(entity.price));
        }
        entity.reason = firstNonEmpty(row, "reason", "note");
        entity.rawJson = row.toString();
        entity.lastSyncAt = syncAt;
        return entity;
    }

    @NonNull
    private ProductReturn productReturnFromEntity(@NonNull CachedProductReturnEntity entity) {
        JSONArray itemArr = new JSONArray();
        for (CachedProductReturnItemEntity item : db.cachedProductReturnDao().getItemsForReturn(currentShopId(), currentShopCode(), currentApiBaseUrl(), entity.backendId)) {
            itemArr.put(productReturnItemJson(item));
        }
        JSONObject obj = productReturnJson(entity, null);
        put(obj, "items", itemArr);
        return ProductReturn.fromJson(obj);
    }

    private void fetchRawList(@NonNull String endpoint, @NonNull Object tag, @NonNull RawListCallback callback) {
        final String token = sessionManager.getToken();
        String url = ApiConfig.url(sessionManager, endpoint);
        StringRequest req = new StringRequest(
                Request.Method.GET,
                url,
                response -> {
                    try {
                        callback.onSuccess(extractResultsArray(response));
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to parse list: " + endpoint, e);
                        callback.onError(0, "Failed to parse response.");
                    }
                },
                error -> {
                    NetworkResponse nr = error.networkResponse;
                    int code = nr == null ? -1 : nr.statusCode;
                    String msg = buildVolleyErrorMessage(nr, "Failed to load data.");
                    Log.e(TAG, "fetch list failed: " + endpoint + " " + code + " " + msg, error);
                    callback.onError(code, msg);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Accept", "application/json");
                if (token != null && !token.trim().isEmpty()) {
                    headers.put("Authorization", "Token " + token.trim());
                }
                return headers;
            }
        };
        req.setTag(tag);
        req.setRetryPolicy(new DefaultRetryPolicy(20000, 1, 1.2f));
        req.setShouldCache(false);
        ApiClient.getInstance(appContext).add(req);
    }

    @NonNull
    private static JSONArray extractResultsArray(String response) throws Exception {
        Object parsed = new JSONTokener(response == null ? "[]" : response).nextValue();
        if (parsed instanceof JSONArray) {
            return (JSONArray) parsed;
        }
        if (parsed instanceof JSONObject) {
            JSONArray results = ((JSONObject) parsed).optJSONArray("results");
            return results != null ? results : new JSONArray();
        }
        return new JSONArray();
    }

    private interface RawListCallback {
        void onSuccess(@NonNull JSONArray rows);
        void onError(int statusCode, @NonNull String message);
    }

    private <T> void postLocal(@NonNull RoomFirstCallback<T> callback, @NonNull List<T> list) {
        mainHandler.post(() -> callback.onLocal(list));
    }

    private <T> void postRemote(@NonNull RoomFirstCallback<T> callback, @NonNull List<T> list) {
        mainHandler.post(() -> callback.onRemote(list));
    }

    private <T> void postNoInternet(@NonNull RoomFirstCallback<T> callback, boolean hasLocalData) {
        mainHandler.post(() -> callback.onNoInternet(hasLocalData));
    }

    private <T> void postError(@NonNull RoomFirstCallback<T> callback, int statusCode, @NonNull String message, boolean hasLocalData) {
        mainHandler.post(() -> callback.onError(statusCode, message, hasLocalData));
    }

    private int currentShopId() {
        return sessionManager.getShopId();
    }

    @NonNull
    private String currentShopCode() {
        return safe(sessionManager.getShopCode()).trim().toUpperCase(Locale.US);
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
    private static JSONObject productReturnJson(@NonNull CachedProductReturnEntity entity, List<ProductReturnItem> modelItems) {
        JSONObject obj = new JSONObject();
        put(obj, "id", entity.backendId);
        if (entity.orderId != null) {
            put(obj, "order", entity.orderId);
        }
        put(obj, "invoice_number", entity.orderInvoice);
        JSONObject customer = new JSONObject();
        put(customer, "id", entity.customerId);
        put(customer, "name", entity.customerName);
        put(obj, "customer", customer);
        put(obj, "note", entity.note);
        put(obj, "returned_at", entity.returnedAt);
        JSONObject user = new JSONObject();
        put(user, "id", entity.createdBy);
        put(user, "username", entity.createdByName);
        put(user, "display_name", entity.createdByName);
        put(obj, "returned_by", user);
        if (modelItems != null) {
            JSONArray arr = new JSONArray();
            for (ProductReturnItem item : modelItems) {
                if (item == null) continue;
                JSONObject itemObj = new JSONObject();
                put(itemObj, "id", item.id);
                put(itemObj, "quantity", item.quantity);
                put(itemObj, "unit_price", item.unitPrice);
                if (item.product != null) {
                    JSONObject product = new JSONObject();
                    put(product, "id", item.product.id);
                    put(product, "name", item.product.name);
                    put(product, "code", item.product.code);
                    put(product, "sku", item.product.sku);
                    put(product, "sell_price", item.product.sellPrice);
                    put(itemObj, "product", product);
                }
                arr.put(itemObj);
            }
            put(obj, "items", arr);
        }
        return obj;
    }

    @NonNull
    private static JSONObject productReturnItemJson(@NonNull CachedProductReturnItemEntity entity) {
        JSONObject obj = new JSONObject();
        put(obj, "id", entity.backendId);
        put(obj, "quantity", entity.quantity);
        put(obj, "unit_price", entity.price);
        put(obj, "item_type", entity.itemType);
        JSONObject product = new JSONObject();
        put(product, "id", entity.productId);
        put(product, "name", entity.productName);
        put(product, "code", entity.productCode);
        put(product, "sku", entity.productSku);
        put(product, "sell_price", entity.price);
        put(obj, "product", product);
        return obj;
    }

    @NonNull
    private static String firstNonEmpty(@NonNull JSONObject obj, @NonNull String... keys) {
        for (String key : keys) {
            String value = obj.optString(key, "");
            if (value != null && !value.trim().isEmpty() && !"null".equalsIgnoreCase(value.trim())) {
                return value.trim();
            }
        }
        return "";
    }

    private static int intOrNestedId(@NonNull JSONObject obj, @NonNull String flatKey, @NonNull String nestedKey) {
        if (obj.has(flatKey) && !obj.isNull(flatKey)) {
            return obj.optInt(flatKey, 0);
        }
        if (obj.has(nestedKey) && !obj.isNull(nestedKey)) {
            Object value = obj.opt(nestedKey);
            if (value instanceof JSONObject) {
                return ((JSONObject) value).optInt("id", 0);
            }
            return obj.optInt(nestedKey, 0);
        }
        return 0;
    }

    private static double optDouble(@NonNull JSONObject obj, @NonNull String... keys) {
        for (String key : keys) {
            if (obj.has(key) && !obj.isNull(key)) {
                return obj.optDouble(key, 0);
            }
        }
        return 0;
    }

    private static double parseDouble(String value) {
        try {
            return Double.parseDouble(safe(value));
        } catch (Exception ignored) {
            return 0;
        }
    }

    @NonNull
    private static String buildVolleyErrorMessage(NetworkResponse nr, @NonNull String fallback) {
        if (nr == null) return fallback;
        try {
            if (nr.data != null && nr.data.length > 0) {
                String body = new String(nr.data, StandardCharsets.UTF_8).trim();
                if (body.length() > 300) body = body.substring(0, 300) + "...";
                return fallback + " (HTTP " + nr.statusCode + ") " + body;
            }
        } catch (Exception ignored) {
        }
        return fallback + " (HTTP " + nr.statusCode + ")";
    }

    @NonNull
    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static void put(@NonNull JSONObject obj, @NonNull String key, Object value) {
        try {
            obj.put(key, value);
        } catch (JSONException ignored) {
        }
    }
}
