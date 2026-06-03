package com.valdker.pos.repositories;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.valdker.pos.SessionManager;
import com.valdker.pos.local.CachedInventoryCountEntity;
import com.valdker.pos.local.CachedInventoryCountItemEntity;
import com.valdker.pos.local.CachedStockAdjustmentEntity;
import com.valdker.pos.local.CachedStockMovementEntity;
import com.valdker.pos.local.CachedStockTransferEntity;
import com.valdker.pos.local.CachedStockTransferItemEntity;
import com.valdker.pos.local.ValoraLocalDatabase;
import com.valdker.pos.models.InventoryCount;
import com.valdker.pos.models.InventoryCountItem;
import com.valdker.pos.models.StockAdjustment;
import com.valdker.pos.models.StockMovement;
import com.valdker.pos.models.StockTransfer;
import com.valdker.pos.models.StockTransferItem;
import com.valdker.pos.network.StockTransferApi;
import com.valdker.pos.utils.NetworkUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class InventoryOperationCacheRepository {

    public static final String NO_LOCAL_DATA_MESSAGE = "No local data found. Please connect to internet and sync once.";
    public static final String INTERNET_REQUIRED_MESSAGE = "This action requires internet connection.";

    public interface RoomFirstCallback<T> {
        void onLocal(@NonNull List<T> list);
        void onRemote(@NonNull List<T> list);
        void onNoInternet(boolean hasLocalData);
        void onError(@NonNull String message, boolean hasLocalData);
    }

    private final Context appContext;
    private final SessionManager sessionManager;
    private final ValoraLocalDatabase db;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public InventoryOperationCacheRepository(@NonNull Context context) {
        appContext = context.getApplicationContext();
        sessionManager = new SessionManager(appContext);
        db = ValoraLocalDatabase.getInstance(appContext);
    }

    public void loadStockMovementsRoomFirst(@NonNull RoomFirstCallback<StockMovement> callback) {
        executor.execute(() -> {
            List<StockMovement> local = loadCachedStockMovements();
            postLocal(callback, local);

            if (!NetworkUtils.isNetworkAvailable(appContext)) {
                postNoInternet(callback, !local.isEmpty());
                return;
            }

            StockMovementRepository.fetch(appContext, new StockMovementRepository.Callback() {
                @Override
                public void onSuccess(List<StockMovement> list) {
                    saveStockMovementsAndReload(list == null ? new ArrayList<>() : list, callback);
                }

                @Override
                public void onError(String message) {
                    postError(callback, safe(message), !local.isEmpty());
                }
            });
        });
    }

    public void loadStockAdjustmentsRoomFirst(@NonNull RoomFirstCallback<StockAdjustment> callback) {
        executor.execute(() -> {
            List<StockAdjustment> local = loadCachedStockAdjustments();
            postLocal(callback, local);

            if (!NetworkUtils.isNetworkAvailable(appContext)) {
                postNoInternet(callback, !local.isEmpty());
                return;
            }

            StockAdjustmentRepository.fetch(appContext, new StockAdjustmentRepository.ListCallback() {
                @Override
                public void onSuccess(List<StockAdjustment> list) {
                    saveStockAdjustmentsAndReload(list == null ? new ArrayList<>() : list, callback);
                }

                @Override
                public void onError(String message) {
                    postError(callback, safe(message), !local.isEmpty());
                }
            });
        });
    }

    public void loadStockTransfersRoomFirst(@NonNull RoomFirstCallback<StockTransfer> callback) {
        executor.execute(() -> {
            List<StockTransfer> local = loadCachedStockTransfers();
            postLocal(callback, local);

            if (!NetworkUtils.isNetworkAvailable(appContext)) {
                postNoInternet(callback, !local.isEmpty());
                return;
            }

            StockTransferApi.getStockTransfers(appContext, sessionManager, new StockTransferApi.StockTransferListCallback() {
                @Override
                public void onSuccess(List<StockTransfer> transfers) {
                    saveStockTransfersAndReload(transfers == null ? new ArrayList<>() : transfers, callback);
                }

                @Override
                public void onError(String message) {
                    postError(callback, safe(message), !local.isEmpty());
                }
            });
        });
    }

    public void loadInventoryCountsRoomFirst(@NonNull RoomFirstCallback<InventoryCount> callback) {
        executor.execute(() -> {
            List<InventoryCount> local = loadCachedInventoryCounts();
            postLocal(callback, local);

            if (!NetworkUtils.isNetworkAvailable(appContext)) {
                postNoInternet(callback, !local.isEmpty());
                return;
            }

            InventoryCountRepository.fetch(appContext, new InventoryCountRepository.Callback() {
                @Override
                public void onSuccess(@NonNull List<InventoryCount> list) {
                    saveInventoryCountsAndReload(list, callback);
                }

                @Override
                public void onError(@NonNull String message) {
                    postError(callback, message, !local.isEmpty());
                }
            });
        });
    }

    private List<StockMovement> loadCachedStockMovements() {
        List<StockMovement> out = new ArrayList<>();
        if (!hasKnownShop()) return out;
        for (CachedStockMovementEntity entity : db.cachedStockMovementDao().getForShop(currentShopId(), currentShopCode(), currentApiBaseUrl())) {
            StockMovement item = new StockMovement();
            item.id = entity.backendId;
            item.created_at = safe(entity.createdAt);
            item.movement_type = safe(entity.movementType);
            item.quantity_delta = entity.quantity;
            item.before_stock = entity.beforeStock;
            item.after_stock = entity.afterStock;
            item.note = safe(entity.note);
            item.ref_model = safe(entity.referenceType);
            item.ref_id = entity.referenceId;
            item.product = entity.productId;
            item.product_name = safe(entity.productName);
            item.product_code = safe(entity.productCode);
            item.product_sku = safe(entity.productSku);
            item.created_by = entity.createdBy;
            out.add(item);
        }
        return out;
    }

    private void saveStockMovementsAndReload(@NonNull List<StockMovement> list, @NonNull RoomFirstCallback<StockMovement> callback) {
        executor.execute(() -> {
            if (!hasKnownShop()) {
                postRemote(callback, new ArrayList<>());
                return;
            }
            long syncAt = System.currentTimeMillis();
            List<CachedStockMovementEntity> entities = new ArrayList<>();
            for (StockMovement item : list) {
                if (item == null) continue;
                CachedStockMovementEntity entity = new CachedStockMovementEntity();
                entity.backendId = item.id;
                entity.shopId = currentShopId();
                entity.shopCode = currentShopCode();
                entity.apiBaseUrl = currentApiBaseUrl();
                entity.cacheKey = cacheKey(entity.backendId);
                entity.productId = item.product;
                entity.productName = safe(item.product_name);
                entity.productCode = safe(item.product_code);
                entity.productSku = safe(item.product_sku);
                entity.movementType = safe(item.movement_type);
                entity.quantity = item.quantity_delta;
                entity.beforeStock = item.before_stock;
                entity.afterStock = item.after_stock;
                entity.referenceType = safe(item.ref_model);
                entity.referenceId = item.ref_id;
                entity.note = safe(item.note);
                entity.createdAt = safe(item.created_at);
                entity.createdBy = item.created_by;
                entity.rawJson = stockMovementJson(entity).toString();
                entity.lastSyncAt = syncAt;
                entities.add(entity);
            }
            db.cachedStockMovementDao().replaceAllForShop(currentShopId(), currentShopCode(), currentApiBaseUrl(), entities);
            postRemote(callback, loadCachedStockMovements());
        });
    }

    private List<StockAdjustment> loadCachedStockAdjustments() {
        List<StockAdjustment> out = new ArrayList<>();
        if (!hasKnownShop()) return out;
        for (CachedStockAdjustmentEntity entity : db.cachedStockAdjustmentDao().getForShop(currentShopId(), currentShopCode(), currentApiBaseUrl())) {
            StockAdjustment item = new StockAdjustment();
            item.id = entity.backendId;
            item.product = entity.productId;
            item.product_name = safe(entity.productName);
            item.old_stock = entity.oldStock;
            item.new_stock = entity.newStock;
            item.reason = safe(entity.reason);
            item.note = safe(entity.note);
            item.adjusted_by = entity.adjustedBy;
            item.adjusted_by_name = safe(entity.adjustedByName);
            item.adjusted_at = safe(entity.adjustedAt);
            out.add(item);
        }
        return out;
    }

    private void saveStockAdjustmentsAndReload(@NonNull List<StockAdjustment> list, @NonNull RoomFirstCallback<StockAdjustment> callback) {
        executor.execute(() -> {
            if (!hasKnownShop()) {
                postRemote(callback, new ArrayList<>());
                return;
            }
            long syncAt = System.currentTimeMillis();
            List<CachedStockAdjustmentEntity> entities = new ArrayList<>();
            for (StockAdjustment item : list) {
                if (item == null) continue;
                CachedStockAdjustmentEntity entity = new CachedStockAdjustmentEntity();
                entity.backendId = item.id;
                entity.shopId = currentShopId();
                entity.shopCode = currentShopCode();
                entity.apiBaseUrl = currentApiBaseUrl();
                entity.cacheKey = cacheKey(entity.backendId);
                entity.productId = item.product;
                entity.productName = safe(item.product_name);
                entity.oldStock = item.old_stock;
                entity.newStock = item.new_stock;
                entity.reason = safe(item.reason);
                entity.note = safe(item.note);
                entity.adjustedBy = item.adjusted_by;
                entity.adjustedByName = safe(item.adjusted_by_name);
                entity.adjustedAt = safe(item.adjusted_at);
                entity.rawJson = stockAdjustmentJson(entity).toString();
                entity.lastSyncAt = syncAt;
                entities.add(entity);
            }
            db.cachedStockAdjustmentDao().replaceAllForShop(currentShopId(), currentShopCode(), currentApiBaseUrl(), entities);
            postRemote(callback, loadCachedStockAdjustments());
        });
    }

    private List<StockTransfer> loadCachedStockTransfers() {
        List<StockTransfer> out = new ArrayList<>();
        if (!hasKnownShop()) return out;
        for (CachedStockTransferEntity entity : db.cachedStockTransferDao().getTransfersForShop(currentShopId(), currentShopCode(), currentApiBaseUrl())) {
            out.add(stockTransferFromEntity(entity));
        }
        return out;
    }

    private void saveStockTransfersAndReload(@NonNull List<StockTransfer> list, @NonNull RoomFirstCallback<StockTransfer> callback) {
        executor.execute(() -> {
            if (!hasKnownShop()) {
                postRemote(callback, new ArrayList<>());
                return;
            }
            long syncAt = System.currentTimeMillis();
            List<CachedStockTransferEntity> transferEntities = new ArrayList<>();
            List<CachedStockTransferItemEntity> itemEntities = new ArrayList<>();
            for (StockTransfer transfer : list) {
                if (transfer == null) continue;
                CachedStockTransferEntity entity = new CachedStockTransferEntity();
                entity.backendId = transfer.getId();
                entity.shopId = currentShopId();
                entity.shopCode = currentShopCode();
                entity.apiBaseUrl = currentApiBaseUrl();
                entity.cacheKey = cacheKey(entity.backendId);
                entity.shopName = safe(transfer.getShopName()).isEmpty() ? sessionManager.getShopName() : safe(transfer.getShopName());
                entity.referenceNo = safe(transfer.getReferenceNo());
                entity.sourceWarehouseId = transfer.getFromWarehouse();
                entity.sourceWarehouseName = safe(transfer.getFromWarehouseName());
                entity.sourceWarehouseCode = safe(transfer.getFromWarehouseCode());
                entity.destinationWarehouseId = transfer.getToWarehouse();
                entity.destinationWarehouseName = safe(transfer.getToWarehouseName());
                entity.destinationWarehouseCode = safe(transfer.getToWarehouseCode());
                entity.status = safe(transfer.getStatus());
                entity.note = safe(transfer.getNote());
                entity.createdBy = transfer.getCreatedBy();
                entity.createdByName = safe(transfer.getCreatedByName());
                entity.completedByName = safe(transfer.getCompletedByName());
                entity.cancelledByName = safe(transfer.getCancelledByName());
                entity.createdAt = safe(transfer.getCreatedAt());
                entity.updatedAt = safe(transfer.getUpdatedAt());
                entity.completedAt = safe(transfer.getCompletedAt());
                entity.cancelledAt = safe(transfer.getCancelledAt());
                entity.rawJson = stockTransferJson(entity, transfer.getItems()).toString();
                entity.lastSyncAt = syncAt;
                transferEntities.add(entity);

                int index = 0;
                if (transfer.getItems() != null) {
                    for (StockTransferItem item : transfer.getItems()) {
                        if (item == null) continue;
                        CachedStockTransferItemEntity itemEntity = new CachedStockTransferItemEntity();
                        itemEntity.backendId = item.getId() > 0 ? item.getId() : index + 1;
                        itemEntity.transferBackendId = transfer.getId();
                        itemEntity.shopId = currentShopId();
                        itemEntity.shopCode = currentShopCode();
                        itemEntity.apiBaseUrl = currentApiBaseUrl();
                        itemEntity.cacheKey = cacheKey(transfer.getId() + ":item:" + index + ":" + itemEntity.backendId);
                        itemEntity.productId = item.getProduct();
                        itemEntity.productUnitId = item.getProductUnit();
                        itemEntity.productName = safe(item.getProductName());
                        itemEntity.productCode = safe(item.getProductCode());
                        itemEntity.productSku = safe(item.getProductSku());
                        itemEntity.quantity = item.getQuantity();
                        itemEntity.quantityInput = item.getQuantityInput();
                        itemEntity.quantityBase = item.getQuantityBase();
                        itemEntity.note = safe(item.getNote());
                        itemEntity.rawJson = stockTransferItemJson(itemEntity).toString();
                        itemEntity.lastSyncAt = syncAt;
                        itemEntities.add(itemEntity);
                        index++;
                    }
                }
            }
            db.cachedStockTransferDao().replaceForShop(currentShopId(), currentShopCode(), currentApiBaseUrl(), transferEntities, itemEntities);
            postRemote(callback, loadCachedStockTransfers());
        });
    }

    private StockTransfer stockTransferFromEntity(@NonNull CachedStockTransferEntity entity) {
        List<CachedStockTransferItemEntity> items = db.cachedStockTransferDao().getItemsForTransfer(currentShopId(), currentShopCode(), currentApiBaseUrl(), entity.backendId);
        JSONArray arr = new JSONArray();
        for (CachedStockTransferItemEntity item : items) {
            arr.put(stockTransferItemJson(item));
        }
        JSONObject obj = stockTransferJson(entity, null);
        put(obj, "items", arr);
        return StockTransfer.fromJson(obj);
    }

    private List<InventoryCount> loadCachedInventoryCounts() {
        List<InventoryCount> out = new ArrayList<>();
        if (!hasKnownShop()) return out;
        for (CachedInventoryCountEntity entity : db.cachedInventoryCountDao().getCountsForShop(currentShopId(), currentShopCode(), currentApiBaseUrl())) {
            out.add(inventoryCountFromEntity(entity));
        }
        return out;
    }

    private void saveInventoryCountsAndReload(@NonNull List<InventoryCount> list, @NonNull RoomFirstCallback<InventoryCount> callback) {
        executor.execute(() -> {
            if (!hasKnownShop()) {
                postRemote(callback, new ArrayList<>());
                return;
            }
            long syncAt = System.currentTimeMillis();
            List<CachedInventoryCountEntity> countEntities = new ArrayList<>();
            List<CachedInventoryCountItemEntity> itemEntities = new ArrayList<>();
            for (InventoryCount count : list) {
                if (count == null) continue;
                CachedInventoryCountEntity entity = new CachedInventoryCountEntity();
                entity.backendId = count.id;
                entity.shopId = currentShopId();
                entity.shopCode = currentShopCode();
                entity.apiBaseUrl = currentApiBaseUrl();
                entity.cacheKey = cacheKey(entity.backendId);
                entity.title = safe(count.title);
                entity.status = safe(count.status);
                entity.note = safe(count.note);
                entity.countedAt = safe(count.counted_at);
                if (count.counted_by != null) {
                    entity.countedBy = count.counted_by.id;
                    entity.countedByUsername = safe(count.counted_by.username);
                    entity.countedByName = safe(count.counted_by.display_name);
                }
                entity.rawJson = inventoryCountJson(entity, count.items).toString();
                entity.lastSyncAt = syncAt;
                countEntities.add(entity);

                int index = 0;
                if (count.items != null) {
                    for (InventoryCountItem item : count.items) {
                        if (item == null) continue;
                        CachedInventoryCountItemEntity itemEntity = new CachedInventoryCountItemEntity();
                        itemEntity.backendId = item.id > 0 ? item.id : index + 1;
                        itemEntity.inventoryCountBackendId = count.id;
                        itemEntity.shopId = currentShopId();
                        itemEntity.shopCode = currentShopCode();
                        itemEntity.apiBaseUrl = currentApiBaseUrl();
                        itemEntity.cacheKey = cacheKey(count.id + ":item:" + index + ":" + itemEntity.backendId);
                        itemEntity.productId = item.product;
                        itemEntity.systemStock = item.system_stock;
                        itemEntity.countedStock = item.counted_stock;
                        itemEntity.difference = item.difference;
                        itemEntity.costPrice = safe(item.cost_price);
                        itemEntity.rawJson = inventoryCountItemJson(itemEntity).toString();
                        itemEntity.lastSyncAt = syncAt;
                        itemEntities.add(itemEntity);
                        index++;
                    }
                }
            }
            db.cachedInventoryCountDao().replaceForShop(currentShopId(), currentShopCode(), currentApiBaseUrl(), countEntities, itemEntities);
            postRemote(callback, loadCachedInventoryCounts());
        });
    }

    private InventoryCount inventoryCountFromEntity(@NonNull CachedInventoryCountEntity entity) {
        InventoryCount count = new InventoryCount();
        count.id = entity.backendId;
        count.title = safe(entity.title);
        count.note = safe(entity.note);
        count.status = safe(entity.status);
        count.counted_at = safe(entity.countedAt);
        InventoryCount.UserLite user = new InventoryCount.UserLite();
        user.id = entity.countedBy;
        user.username = safe(entity.countedByUsername);
        user.display_name = safe(entity.countedByName);
        count.counted_by = user;
        for (CachedInventoryCountItemEntity itemEntity : db.cachedInventoryCountDao().getItemsForCount(currentShopId(), currentShopCode(), currentApiBaseUrl(), entity.backendId)) {
            InventoryCountItem item = new InventoryCountItem();
            item.id = itemEntity.backendId;
            item.product = itemEntity.productId;
            item.system_stock = itemEntity.systemStock;
            item.counted_stock = itemEntity.countedStock;
            item.difference = itemEntity.difference;
            item.cost_price = safe(itemEntity.costPrice);
            count.items.add(item);
        }
        return count;
    }

    private boolean hasKnownShop() {
        return currentShopId() > 0 && !currentShopCode().isEmpty();
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

    private <T> void postLocal(@NonNull RoomFirstCallback<T> callback, @NonNull List<T> list) {
        mainHandler.post(() -> callback.onLocal(list));
    }

    private <T> void postRemote(@NonNull RoomFirstCallback<T> callback, @NonNull List<T> list) {
        mainHandler.post(() -> callback.onRemote(list));
    }

    private <T> void postNoInternet(@NonNull RoomFirstCallback<T> callback, boolean hasLocalData) {
        mainHandler.post(() -> callback.onNoInternet(hasLocalData));
    }

    private <T> void postError(@NonNull RoomFirstCallback<T> callback, @NonNull String message, boolean hasLocalData) {
        mainHandler.post(() -> callback.onError(message, hasLocalData));
    }

    @NonNull
    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static JSONObject stockMovementJson(@NonNull CachedStockMovementEntity entity) {
        JSONObject obj = new JSONObject();
        put(obj, "id", entity.backendId);
        put(obj, "created_at", entity.createdAt);
        put(obj, "movement_type", entity.movementType);
        put(obj, "quantity_delta", entity.quantity);
        put(obj, "before_stock", entity.beforeStock);
        put(obj, "after_stock", entity.afterStock);
        put(obj, "note", entity.note);
        put(obj, "ref_model", entity.referenceType);
        put(obj, "ref_id", entity.referenceId);
        put(obj, "product", entity.productId);
        put(obj, "product_name", entity.productName);
        put(obj, "product_code", entity.productCode);
        put(obj, "product_sku", entity.productSku);
        put(obj, "created_by", entity.createdBy);
        return obj;
    }

    private static JSONObject stockAdjustmentJson(@NonNull CachedStockAdjustmentEntity entity) {
        JSONObject obj = new JSONObject();
        put(obj, "id", entity.backendId);
        put(obj, "old_stock", entity.oldStock);
        put(obj, "new_stock", entity.newStock);
        put(obj, "reason", entity.reason);
        put(obj, "note", entity.note);
        put(obj, "adjusted_at", entity.adjustedAt);
        put(obj, "product", entity.productId);
        put(obj, "product_name", entity.productName);
        put(obj, "adjusted_by", entity.adjustedBy);
        put(obj, "adjusted_by_name", entity.adjustedByName);
        return obj;
    }

    private static JSONObject stockTransferJson(@NonNull CachedStockTransferEntity entity, List<StockTransferItem> modelItems) {
        JSONObject obj = new JSONObject();
        put(obj, "id", entity.backendId);
        put(obj, "shop_id", entity.shopId);
        put(obj, "shop_name", entity.shopName);
        put(obj, "shop_code", entity.shopCode);
        put(obj, "reference_no", entity.referenceNo);
        put(obj, "from_warehouse", entity.sourceWarehouseId);
        put(obj, "from_warehouse_name", entity.sourceWarehouseName);
        put(obj, "from_warehouse_code", entity.sourceWarehouseCode);
        put(obj, "to_warehouse", entity.destinationWarehouseId);
        put(obj, "to_warehouse_name", entity.destinationWarehouseName);
        put(obj, "to_warehouse_code", entity.destinationWarehouseCode);
        put(obj, "note", entity.note);
        put(obj, "status", entity.status);
        put(obj, "created_by", entity.createdBy);
        put(obj, "created_by_name", entity.createdByName);
        put(obj, "completed_by_name", entity.completedByName);
        put(obj, "cancelled_by_name", entity.cancelledByName);
        put(obj, "completed_at", entity.completedAt);
        put(obj, "cancelled_at", entity.cancelledAt);
        put(obj, "created_at", entity.createdAt);
        put(obj, "updated_at", entity.updatedAt);
        if (modelItems != null) {
            JSONArray arr = new JSONArray();
            for (StockTransferItem item : modelItems) {
                if (item == null) continue;
                JSONObject itemObj = new JSONObject();
                put(itemObj, "id", item.getId());
                put(itemObj, "product", item.getProduct());
                put(itemObj, "product_unit", item.getProductUnit());
                put(itemObj, "product_name", item.getProductName());
                put(itemObj, "product_code", item.getProductCode());
                put(itemObj, "product_sku", item.getProductSku());
                put(itemObj, "quantity", item.getQuantity());
                put(itemObj, "quantity_input", item.getQuantityInput());
                put(itemObj, "quantity_base", item.getQuantityBase());
                put(itemObj, "note", item.getNote());
                arr.put(itemObj);
            }
            put(obj, "items", arr);
        }
        return obj;
    }

    private static JSONObject stockTransferItemJson(@NonNull CachedStockTransferItemEntity entity) {
        JSONObject obj = new JSONObject();
        put(obj, "id", entity.backendId);
        put(obj, "product", entity.productId);
        put(obj, "product_unit", entity.productUnitId);
        put(obj, "product_name", entity.productName);
        put(obj, "product_code", entity.productCode);
        put(obj, "product_sku", entity.productSku);
        put(obj, "quantity", entity.quantity);
        put(obj, "quantity_input", entity.quantityInput);
        put(obj, "quantity_base", entity.quantityBase);
        put(obj, "note", entity.note);
        return obj;
    }

    private static JSONObject inventoryCountJson(@NonNull CachedInventoryCountEntity entity, List<InventoryCountItem> modelItems) {
        JSONObject obj = new JSONObject();
        put(obj, "id", entity.backendId);
        put(obj, "title", entity.title);
        put(obj, "note", entity.note);
        put(obj, "status", entity.status);
        put(obj, "counted_at", entity.countedAt);
        JSONObject user = new JSONObject();
        put(user, "id", entity.countedBy);
        put(user, "username", entity.countedByUsername);
        put(user, "display_name", entity.countedByName);
        put(obj, "counted_by", user);
        if (modelItems != null) {
            JSONArray arr = new JSONArray();
            for (InventoryCountItem item : modelItems) {
                if (item == null) continue;
                JSONObject itemObj = new JSONObject();
                put(itemObj, "id", item.id);
                put(itemObj, "product", item.product);
                put(itemObj, "system_stock", item.system_stock);
                put(itemObj, "counted_stock", item.counted_stock);
                put(itemObj, "difference", item.difference);
                put(itemObj, "cost_price", safe(item.cost_price));
                arr.put(itemObj);
            }
            put(obj, "items", arr);
        }
        return obj;
    }

    private static JSONObject inventoryCountItemJson(@NonNull CachedInventoryCountItemEntity entity) {
        JSONObject obj = new JSONObject();
        put(obj, "id", entity.backendId);
        put(obj, "product", entity.productId);
        put(obj, "system_stock", entity.systemStock);
        put(obj, "counted_stock", entity.countedStock);
        put(obj, "difference", entity.difference);
        put(obj, "cost_price", entity.costPrice);
        return obj;
    }

    private static void put(@NonNull JSONObject obj, @NonNull String key, Object value) {
        try {
            obj.put(key, value);
        } catch (JSONException ignored) {
        }
    }
}
