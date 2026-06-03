package com.valdker.pos.repositories;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.valdker.pos.SessionManager;
import com.valdker.pos.local.BankAccountEntity;
import com.valdker.pos.local.CategoryEntity;
import com.valdker.pos.local.CustomerEntity;
import com.valdker.pos.local.ProductEntity;
import com.valdker.pos.local.SupplierEntity;
import com.valdker.pos.local.UnitEntity;
import com.valdker.pos.local.ValoraLocalDatabase;
import com.valdker.pos.local.WarehouseEntity;
import com.valdker.pos.local.WarehouseStockEntity;
import com.valdker.pos.models.BankAccount;
import com.valdker.pos.models.Category;
import com.valdker.pos.models.Customer;
import com.valdker.pos.models.Product;
import com.valdker.pos.models.ProductUnit;
import com.valdker.pos.models.Supplier;
import com.valdker.pos.models.UnitLite;
import com.valdker.pos.models.Warehouse;
import com.valdker.pos.models.WarehouseStock;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AdminMasterCacheRepository {

    public static final String NO_LOCAL_DATA_MESSAGE = "No local data found. Please connect to internet and sync once.";
    public static final String INTERNET_REQUIRED_MESSAGE = "This action requires internet connection.";

    public interface Callback<T> {
        void onResult(@NonNull T data);
    }

    private final ValoraLocalDatabase db;
    private final SessionManager sessionManager;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public AdminMasterCacheRepository(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        db = ValoraLocalDatabase.getInstance(appContext);
        sessionManager = new SessionManager(appContext);
    }

    public boolean hasKnownShop() {
        return currentShopId() > 0 && !currentShopCode().isEmpty();
    }

    public void loadProducts(@NonNull Callback<List<Product>> callback) {
        run(callback, () -> {
            if (!hasKnownShop()) return new ArrayList<>();
            List<Product> out = new ArrayList<>();
            for (ProductEntity entity : db.productDao().getAllForShop(currentShopId(), currentShopCode(), currentApiBaseUrl())) {
                out.add(productFromEntity(entity));
            }
            return out;
        });
    }

    public void saveProducts(@NonNull List<Product> products, @NonNull Callback<List<Product>> callback) {
        run(callback, () -> {
            if (!hasKnownShop()) return new ArrayList<>();
            long syncAt = System.currentTimeMillis();
            List<ProductEntity> entities = new ArrayList<>();
            for (Product product : products) {
                entities.add(productToEntity(product, syncAt));
            }
            db.productDao().replaceAllForShop(currentShopId(), currentShopCode(), currentApiBaseUrl(), entities);
            List<Product> out = new ArrayList<>();
            for (ProductEntity entity : db.productDao().getAllForShop(currentShopId(), currentShopCode(), currentApiBaseUrl())) {
                out.add(productFromEntity(entity));
            }
            return out;
        });
    }

    public void loadCategories(@NonNull Callback<List<Category>> callback) {
        run(callback, () -> {
            if (!hasKnownShop()) return new ArrayList<>();
            List<Category> out = new ArrayList<>();
            for (CategoryEntity entity : db.categoryDao().getAllForShop(currentShopId(), currentShopCode(), currentApiBaseUrl())) {
                out.add(new Category(entity.id, safe(entity.name), safe(entity.iconUrl)));
            }
            return out;
        });
    }

    public void saveCategories(@NonNull List<Category> categories, @NonNull Callback<List<Category>> callback) {
        run(callback, () -> {
            if (!hasKnownShop()) return new ArrayList<>();
            long syncAt = System.currentTimeMillis();
            List<CategoryEntity> entities = new ArrayList<>();
            for (Category category : categories) {
                CategoryEntity entity = new CategoryEntity();
                entity.id = category == null ? 0 : category.id;
                entity.shopId = currentShopId();
                entity.shopCode = currentShopCode();
                entity.apiBaseUrl = currentApiBaseUrl();
                entity.cacheKey = cacheKey(entity.id);
                entity.name = category == null ? "" : safe(category.name);
                entity.iconUrl = category == null ? "" : safe(category.iconUrl);
                entity.rawJson = categoryJson(entity).toString();
                entity.lastSyncAt = syncAt;
                entities.add(entity);
            }
            db.categoryDao().replaceAllForShop(currentShopId(), currentShopCode(), currentApiBaseUrl(), entities);
            List<Category> out = new ArrayList<>();
            for (CategoryEntity entity : db.categoryDao().getAllForShop(currentShopId(), currentShopCode(), currentApiBaseUrl())) {
                out.add(new Category(entity.id, safe(entity.name), safe(entity.iconUrl)));
            }
            return out;
        });
    }

    public void loadUnits(@NonNull Callback<List<UnitLite>> callback) {
        run(callback, () -> {
            if (!hasKnownShop()) return new ArrayList<>();
            List<UnitLite> out = new ArrayList<>();
            for (UnitEntity entity : db.unitDao().getAllForShop(currentShopId(), currentShopCode(), currentApiBaseUrl())) {
                out.add(new UnitLite(entity.id, safe(entity.name)));
            }
            return out;
        });
    }

    public void saveUnits(@NonNull List<UnitLite> units, @NonNull Callback<List<UnitLite>> callback) {
        run(callback, () -> {
            if (!hasKnownShop()) return new ArrayList<>();
            long syncAt = System.currentTimeMillis();
            List<UnitEntity> entities = new ArrayList<>();
            for (UnitLite unit : units) {
                UnitEntity entity = new UnitEntity();
                entity.id = unit == null ? 0 : unit.id;
                entity.shopId = currentShopId();
                entity.shopCode = currentShopCode();
                entity.apiBaseUrl = currentApiBaseUrl();
                entity.cacheKey = cacheKey(entity.id);
                entity.name = unit == null ? "" : safe(unit.name);
                entity.rawJson = simpleJson(entity.id, entity.name).toString();
                entity.lastSyncAt = syncAt;
                entities.add(entity);
            }
            db.unitDao().replaceAllForShop(currentShopId(), currentShopCode(), currentApiBaseUrl(), entities);
            List<UnitLite> out = new ArrayList<>();
            for (UnitEntity entity : db.unitDao().getAllForShop(currentShopId(), currentShopCode(), currentApiBaseUrl())) {
                out.add(new UnitLite(entity.id, safe(entity.name)));
            }
            return out;
        });
    }

    public void loadCustomers(@NonNull Callback<List<Customer>> callback) {
        run(callback, () -> {
            if (!hasKnownShop()) return new ArrayList<>();
            List<Customer> out = new ArrayList<>();
            for (CustomerEntity entity : db.customerDao().getAllForShop(currentShopId(), currentShopCode(), currentApiBaseUrl())) {
                out.add(new Customer(entity.id, safe(entity.name), safe(entity.cell), safe(entity.email), safe(entity.address), entity.points));
            }
            return out;
        });
    }

    public void saveCustomers(@NonNull List<Customer> customers, @NonNull Callback<List<Customer>> callback) {
        run(callback, () -> {
            if (!hasKnownShop()) return new ArrayList<>();
            long syncAt = System.currentTimeMillis();
            List<CustomerEntity> entities = new ArrayList<>();
            for (Customer customer : customers) {
                CustomerEntity entity = new CustomerEntity();
                entity.id = customer == null ? 0 : customer.id;
                entity.shopId = currentShopId();
                entity.shopCode = currentShopCode();
                entity.apiBaseUrl = currentApiBaseUrl();
                entity.cacheKey = cacheKey(entity.id);
                entity.name = customer == null ? "" : safe(customer.name);
                entity.cell = customer == null ? "" : safe(customer.cell);
                entity.email = customer == null ? "" : safe(customer.email);
                entity.address = customer == null ? "" : safe(customer.address);
                entity.points = customer == null ? 0L : customer.points;
                entity.rawJson = customerJson(entity).toString();
                entity.lastSyncAt = syncAt;
                entities.add(entity);
            }
            db.customerDao().replaceAllForShop(currentShopId(), currentShopCode(), currentApiBaseUrl(), entities);
            List<Customer> out = new ArrayList<>();
            for (CustomerEntity entity : db.customerDao().getAllForShop(currentShopId(), currentShopCode(), currentApiBaseUrl())) {
                out.add(new Customer(entity.id, safe(entity.name), safe(entity.cell), safe(entity.email), safe(entity.address), entity.points));
            }
            return out;
        });
    }

    public void loadSuppliers(@NonNull Callback<List<Supplier>> callback) {
        run(callback, () -> {
            if (!hasKnownShop()) return new ArrayList<>();
            List<Supplier> out = new ArrayList<>();
            for (SupplierEntity entity : db.supplierDao().getAllForShop(currentShopId(), currentShopCode(), currentApiBaseUrl())) {
                out.add(new Supplier(entity.id, safe(entity.name), safe(entity.contactPerson), safe(entity.cell), safe(entity.email), safe(entity.address)));
            }
            return out;
        });
    }

    public void saveSuppliers(@NonNull List<Supplier> suppliers, @NonNull Callback<List<Supplier>> callback) {
        run(callback, () -> {
            if (!hasKnownShop()) return new ArrayList<>();
            long syncAt = System.currentTimeMillis();
            List<SupplierEntity> entities = new ArrayList<>();
            for (Supplier supplier : suppliers) {
                SupplierEntity entity = new SupplierEntity();
                entity.id = supplier == null ? 0 : supplier.id;
                entity.shopId = currentShopId();
                entity.shopCode = currentShopCode();
                entity.apiBaseUrl = currentApiBaseUrl();
                entity.cacheKey = cacheKey(entity.id);
                entity.name = supplier == null ? "" : safe(supplier.name);
                entity.contactPerson = supplier == null ? "" : safe(supplier.contactPerson);
                entity.cell = supplier == null ? "" : safe(supplier.cell);
                entity.email = supplier == null ? "" : safe(supplier.email);
                entity.address = supplier == null ? "" : safe(supplier.address);
                entity.rawJson = supplierJson(entity).toString();
                entity.lastSyncAt = syncAt;
                entities.add(entity);
            }
            db.supplierDao().replaceAllForShop(currentShopId(), currentShopCode(), currentApiBaseUrl(), entities);
            List<Supplier> out = new ArrayList<>();
            for (SupplierEntity entity : db.supplierDao().getAllForShop(currentShopId(), currentShopCode(), currentApiBaseUrl())) {
                out.add(new Supplier(entity.id, safe(entity.name), safe(entity.contactPerson), safe(entity.cell), safe(entity.email), safe(entity.address)));
            }
            return out;
        });
    }

    public void loadWarehouses(@NonNull Callback<List<Warehouse>> callback) {
        run(callback, () -> {
            if (!hasKnownShop()) return new ArrayList<>();
            List<Warehouse> out = new ArrayList<>();
            for (WarehouseEntity entity : db.warehouseDao().getAllForShop(currentShopId(), currentShopCode(), currentApiBaseUrl())) {
                out.add(warehouseFromEntity(entity));
            }
            return out;
        });
    }

    public void saveWarehouses(@NonNull List<Warehouse> warehouses, @NonNull Callback<List<Warehouse>> callback) {
        run(callback, () -> {
            if (!hasKnownShop()) return new ArrayList<>();
            long syncAt = System.currentTimeMillis();
            List<WarehouseEntity> entities = new ArrayList<>();
            for (Warehouse warehouse : warehouses) {
                entities.add(warehouseToEntity(warehouse, syncAt));
            }
            db.warehouseDao().replaceAllForShop(currentShopId(), currentShopCode(), currentApiBaseUrl(), entities);
            List<Warehouse> out = new ArrayList<>();
            for (WarehouseEntity entity : db.warehouseDao().getAllForShop(currentShopId(), currentShopCode(), currentApiBaseUrl())) {
                out.add(warehouseFromEntity(entity));
            }
            return out;
        });
    }

    public void loadWarehouseStocks(@NonNull Callback<List<WarehouseStock>> callback) {
        run(callback, () -> {
            if (!hasKnownShop()) return new ArrayList<>();
            List<WarehouseStock> out = new ArrayList<>();
            for (WarehouseStockEntity entity : db.warehouseStockDao().getAllForShop(currentShopId(), currentShopCode(), currentApiBaseUrl())) {
                out.add(warehouseStockFromEntity(entity));
            }
            return out;
        });
    }

    public void saveWarehouseStocks(@NonNull List<WarehouseStock> stocks, @NonNull Callback<List<WarehouseStock>> callback) {
        run(callback, () -> {
            if (!hasKnownShop()) return new ArrayList<>();
            long syncAt = System.currentTimeMillis();
            List<WarehouseStockEntity> entities = new ArrayList<>();
            for (WarehouseStock stock : stocks) {
                entities.add(warehouseStockToEntity(stock, syncAt));
            }
            db.warehouseStockDao().replaceAllForShop(currentShopId(), currentShopCode(), currentApiBaseUrl(), entities);
            List<WarehouseStock> out = new ArrayList<>();
            for (WarehouseStockEntity entity : db.warehouseStockDao().getAllForShop(currentShopId(), currentShopCode(), currentApiBaseUrl())) {
                out.add(warehouseStockFromEntity(entity));
            }
            return out;
        });
    }

    public void loadBankAccounts(@NonNull Callback<List<BankAccount>> callback) {
        run(callback, () -> {
            if (!hasKnownShop()) return new ArrayList<>();
            List<BankAccount> out = new ArrayList<>();
            for (BankAccountEntity entity : db.bankAccountDao().getAllForShop(currentShopId(), currentShopCode(), currentApiBaseUrl())) {
                out.add(bankAccountFromEntity(entity));
            }
            return out;
        });
    }

    public void saveBankAccounts(@NonNull List<BankAccount> bankAccounts, @NonNull Callback<List<BankAccount>> callback) {
        run(callback, () -> {
            if (!hasKnownShop()) return new ArrayList<>();
            long syncAt = System.currentTimeMillis();
            List<BankAccountEntity> entities = new ArrayList<>();
            for (BankAccount account : bankAccounts) {
                entities.add(bankAccountToEntity(account, syncAt));
            }
            db.bankAccountDao().replaceAllForShop(currentShopId(), currentShopCode(), currentApiBaseUrl(), entities);
            List<BankAccount> out = new ArrayList<>();
            for (BankAccountEntity entity : db.bankAccountDao().getAllForShop(currentShopId(), currentShopCode(), currentApiBaseUrl())) {
                out.add(bankAccountFromEntity(entity));
            }
            return out;
        });
    }

    private interface Work<T> {
        T execute();
    }

    private <T> void run(@NonNull Callback<T> callback, @NonNull Work<T> work) {
        executor.execute(() -> {
            T result = work.execute();
            mainHandler.post(() -> callback.onResult(result));
        });
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
    private String currentShopName() {
        return safe(sessionManager.getShopName());
    }

    @NonNull
    private static String safe(String value) {
        return value == null ? "" : value;
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

    private ProductEntity productToEntity(Product product, long syncAt) {
        ProductEntity entity = new ProductEntity();
        entity.id = product == null || product.id == null ? "" : product.id;
        entity.shopId = currentShopId();
        entity.shopCode = currentShopCode();
        entity.apiBaseUrl = currentApiBaseUrl();
        entity.cacheKey = cacheKey(entity.id);
        entity.name = product == null ? "" : safe(product.name);
        entity.sku = product == null ? "" : safe(product.sku);
        entity.barcode = product == null ? "" : safe(product.barcode);
        entity.price = product == null ? 0d : product.price;
        entity.stock = product == null ? 0 : product.stock;
        entity.imageUrl = product == null ? "" : safe(product.imageUrl != null ? product.imageUrl : product.image_url);
        entity.categoryId = product == null ? "" : safe(product.categoryId);
        entity.categoryName = product == null ? "" : safe(product.categoryName);
        entity.description = product == null ? "" : safe(product.description);
        entity.buyPrice = product == null ? "" : safe(product.buyPrice);
        entity.sellPrice = product == null ? "" : safe(product.sellPrice);
        entity.weight = product == null ? "" : safe(product.weight);
        entity.unitId = product == null ? "" : safe(product.unitId);
        entity.unitName = product == null ? "" : safe(product.unitName);
        entity.supplierId = product == null ? "" : safe(product.supplierId);
        entity.supplierName = product == null ? "" : safe(product.supplierName);
        entity.itemType = product == null ? "" : safe(product.itemType);
        entity.isActive = product == null || product.isActive;
        entity.trackStock = product == null || product.trackStock;
        entity.productUnitsJson = productUnitsToJson(product == null ? null : product.product_units);
        entity.rawJson = productJson(entity).toString();
        entity.lastSyncAt = syncAt;
        return entity;
    }

    private static Product productFromEntity(ProductEntity entity) {
        Product product = new Product();
        product.id = safe(entity.id);
        product.name = safe(entity.name);
        product.shopId = entity.shopId;
        product.shop_id = entity.shopId;
        product.sku = safe(entity.sku);
        product.barcode = safe(entity.barcode);
        product.price = entity.price;
        product.stock = entity.stock;
        product.imageUrl = safe(entity.imageUrl);
        product.image_url = safe(entity.imageUrl);
        product.categoryId = safe(entity.categoryId);
        product.categoryName = safe(entity.categoryName);
        product.description = safe(entity.description);
        product.buyPrice = safe(entity.buyPrice);
        product.sellPrice = safe(entity.sellPrice);
        product.weight = safe(entity.weight);
        product.unitId = safe(entity.unitId);
        product.unitName = safe(entity.unitName);
        product.supplierId = safe(entity.supplierId);
        product.supplierName = safe(entity.supplierName);
        product.itemType = safe(entity.itemType);
        product.isActive = entity.isActive;
        product.trackStock = entity.trackStock;
        product.product_units = productUnitsFromJson(entity.productUnitsJson);
        return product;
    }

    private WarehouseEntity warehouseToEntity(Warehouse warehouse, long syncAt) {
        WarehouseEntity entity = new WarehouseEntity();
        entity.id = warehouse == null ? 0 : warehouse.getId();
        entity.shopId = currentShopId();
        entity.shopName = warehouse == null || safe(warehouse.getShopName()).isEmpty() ? currentShopName() : safe(warehouse.getShopName());
        entity.shopCode = currentShopCode();
        entity.apiBaseUrl = currentApiBaseUrl();
        entity.cacheKey = cacheKey(entity.id);
        entity.name = warehouse == null ? "" : safe(warehouse.getName());
        entity.code = warehouse == null ? "" : safe(warehouse.getCode());
        entity.location = warehouse == null ? "" : safe(warehouse.getLocation());
        entity.isActive = warehouse == null || warehouse.isActive();
        entity.isDefault = warehouse != null && warehouse.isDefault();
        entity.createdAt = warehouse == null ? "" : safe(warehouse.getCreatedAt());
        entity.updatedAt = warehouse == null ? "" : safe(warehouse.getUpdatedAt());
        entity.rawJson = warehouseJson(entity).toString();
        entity.lastSyncAt = syncAt;
        return entity;
    }

    private static Warehouse warehouseFromEntity(WarehouseEntity entity) {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(entity.id);
        warehouse.setShopId(entity.shopId);
        warehouse.setShopName(safe(entity.shopName));
        warehouse.setShopCode(safe(entity.shopCode));
        warehouse.setName(safe(entity.name));
        warehouse.setCode(safe(entity.code));
        warehouse.setLocation(safe(entity.location));
        warehouse.setActive(entity.isActive);
        warehouse.setDefault(entity.isDefault);
        warehouse.setCreatedAt(safe(entity.createdAt));
        warehouse.setUpdatedAt(safe(entity.updatedAt));
        return warehouse;
    }

    private WarehouseStockEntity warehouseStockToEntity(WarehouseStock stock, long syncAt) {
        WarehouseStockEntity entity = new WarehouseStockEntity();
        entity.id = stock == null ? 0 : stock.getId();
        entity.shopId = currentShopId();
        entity.shopName = stock == null || safe(stock.getShopName()).isEmpty() ? currentShopName() : safe(stock.getShopName());
        entity.shopCode = currentShopCode();
        entity.apiBaseUrl = currentApiBaseUrl();
        entity.warehouse = stock == null ? 0 : stock.getWarehouse();
        entity.warehouseName = stock == null ? "" : safe(stock.getWarehouseName());
        entity.warehouseCode = stock == null ? "" : safe(stock.getWarehouseCode());
        entity.product = stock == null ? 0 : stock.getProduct();
        entity.productUnit = stock == null ? 0 : stock.getProductUnit();
        entity.productName = stock == null ? "" : safe(stock.getProductName());
        entity.productCode = stock == null ? "" : safe(stock.getProductCode());
        entity.productSku = stock == null ? "" : safe(stock.getProductSku());
        entity.productTrackStock = stock == null || stock.isProductTrackStock();
        entity.quantity = stock == null ? 0d : stock.getQuantity();
        entity.minStock = stock == null ? 0d : stock.getMinStock();
        entity.quantityBase = stock == null ? 0d : stock.getQuantityBase();
        entity.baseUnitName = stock == null ? "" : safe(stock.getBaseUnitName());
        entity.displayStock = stock == null ? "" : safe(stock.getDisplayStock());
        entity.displayMinimumStock = stock == null ? "" : safe(stock.getDisplayMinimumStock());
        entity.isLowStock = stock != null && stock.isLowStock();
        entity.createdAt = stock == null ? "" : safe(stock.getCreatedAt());
        entity.updatedAt = stock == null ? "" : safe(stock.getUpdatedAt());
        entity.cacheKey = cacheKey(entity.id > 0
                ? String.valueOf(entity.id)
                : entity.warehouse + ":" + entity.product + ":" + entity.productUnit);
        entity.rawJson = warehouseStockJson(entity).toString();
        entity.lastSyncAt = syncAt;
        return entity;
    }

    private static WarehouseStock warehouseStockFromEntity(WarehouseStockEntity entity) {
        try {
            return WarehouseStock.fromJson(warehouseStockJson(entity));
        } catch (Exception ignored) {
            return new WarehouseStock();
        }
    }

    private BankAccountEntity bankAccountToEntity(BankAccount account, long syncAt) {
        BankAccountEntity entity = new BankAccountEntity();
        entity.id = account == null ? 0 : account.getId();
        entity.shopId = currentShopId();
        entity.shopName = account == null || safe(account.getShopName()).isEmpty() ? currentShopName() : safe(account.getShopName());
        entity.shopCode = currentShopCode();
        entity.apiBaseUrl = currentApiBaseUrl();
        entity.cacheKey = cacheKey(entity.id);
        entity.name = account == null ? "" : safe(account.getName());
        entity.bankName = account == null ? "" : safe(account.getBankName());
        entity.accountNumber = account == null ? "" : safe(account.getAccountNumber());
        entity.accountHolder = account == null ? "" : safe(account.getAccountHolder());
        entity.accountType = account == null ? "" : safe(account.getAccountType());
        entity.openingBalance = account == null ? "0.00" : safe(account.getOpeningBalance());
        entity.currentBalance = account == null ? "0.00" : safe(account.getCurrentBalance());
        entity.isActive = account == null || account.isActive();
        entity.note = account == null ? "" : safe(account.getNote());
        entity.rawJson = bankAccountJson(entity).toString();
        entity.lastSyncAt = syncAt;
        return entity;
    }

    private static BankAccount bankAccountFromEntity(BankAccountEntity entity) {
        return BankAccount.fromJson(bankAccountJson(entity));
    }

    private static String productUnitsToJson(List<ProductUnit> units) {
        JSONArray array = new JSONArray();
        if (units == null) return array.toString();
        for (ProductUnit unit : units) {
            JSONObject obj = new JSONObject();
            put(obj, "id", unit == null ? 0 : unit.id);
            put(obj, "product", unit == null ? 0 : unit.product);
            put(obj, "unit_name", unit == null ? "" : safe(unit.unit_name));
            put(obj, "conversion_qty", unit == null ? 1.0 : unit.conversion_qty);
            put(obj, "barcode", unit == null ? "" : safe(unit.barcode));
            put(obj, "is_base_unit", unit != null && unit.is_base_unit);
            put(obj, "is_default_purchase_unit", unit != null && unit.is_default_purchase_unit);
            put(obj, "is_default_sale_unit", unit != null && unit.is_default_sale_unit);
            array.put(obj);
        }
        return array.toString();
    }

    private static List<ProductUnit> productUnitsFromJson(String json) {
        List<ProductUnit> units = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(safe(json).isEmpty() ? "[]" : json);
            for (int i = 0; i < array.length(); i++) {
                units.add(ProductUnit.fromJson(array.optJSONObject(i)));
            }
        } catch (JSONException ignored) {
        }
        return units;
    }

    private static JSONObject simpleJson(int id, String name) {
        JSONObject obj = new JSONObject();
        put(obj, "id", id);
        put(obj, "name", safe(name));
        return obj;
    }

    private static JSONObject productJson(ProductEntity entity) {
        JSONObject obj = simpleJson(parseInt(entity.id), entity.name);
        put(obj, "shop_id", entity.shopId);
        put(obj, "shop_code", entity.shopCode);
        put(obj, "sku", entity.sku);
        put(obj, "barcode", entity.barcode);
        put(obj, "price", entity.price);
        put(obj, "stock", entity.stock);
        put(obj, "image_url", entity.imageUrl);
        put(obj, "category", entity.categoryId);
        put(obj, "category_name", entity.categoryName);
        put(obj, "item_type", entity.itemType);
        return obj;
    }

    private static JSONObject categoryJson(CategoryEntity entity) {
        JSONObject obj = simpleJson(entity.id, entity.name);
        put(obj, "icon_url", entity.iconUrl);
        put(obj, "shop_id", entity.shopId);
        put(obj, "shop_code", entity.shopCode);
        return obj;
    }

    private static JSONObject customerJson(CustomerEntity entity) {
        JSONObject obj = simpleJson(entity.id, entity.name);
        put(obj, "cell", entity.cell);
        put(obj, "email", entity.email);
        put(obj, "address", entity.address);
        put(obj, "points", entity.points);
        put(obj, "shop_id", entity.shopId);
        put(obj, "shop_code", entity.shopCode);
        return obj;
    }

    private static JSONObject supplierJson(SupplierEntity entity) {
        JSONObject obj = simpleJson(entity.id, entity.name);
        put(obj, "contact_person", entity.contactPerson);
        put(obj, "cell", entity.cell);
        put(obj, "email", entity.email);
        put(obj, "address", entity.address);
        put(obj, "shop_id", entity.shopId);
        put(obj, "shop_code", entity.shopCode);
        return obj;
    }

    private static JSONObject warehouseJson(WarehouseEntity entity) {
        JSONObject obj = simpleJson(entity.id, entity.name);
        put(obj, "shop_id", entity.shopId);
        put(obj, "shop_name", entity.shopName);
        put(obj, "shop_code", entity.shopCode);
        put(obj, "code", entity.code);
        put(obj, "location", entity.location);
        put(obj, "is_active", entity.isActive);
        put(obj, "is_default", entity.isDefault);
        put(obj, "created_at", entity.createdAt);
        put(obj, "updated_at", entity.updatedAt);
        return obj;
    }

    private static JSONObject warehouseStockJson(WarehouseStockEntity entity) {
        JSONObject obj = new JSONObject();
        put(obj, "id", entity.id);
        put(obj, "shop_id", entity.shopId);
        put(obj, "shop_name", entity.shopName);
        put(obj, "shop_code", entity.shopCode);
        put(obj, "warehouse", entity.warehouse);
        put(obj, "warehouse_name", entity.warehouseName);
        put(obj, "warehouse_code", entity.warehouseCode);
        put(obj, "product", entity.product);
        put(obj, "product_unit", entity.productUnit);
        put(obj, "product_name", entity.productName);
        put(obj, "product_code", entity.productCode);
        put(obj, "product_sku", entity.productSku);
        put(obj, "product_track_stock", entity.productTrackStock);
        put(obj, "quantity", entity.quantity);
        put(obj, "minimum_stock", entity.minStock);
        put(obj, "quantity_base", entity.quantityBase);
        put(obj, "base_unit_name", entity.baseUnitName);
        put(obj, "display_stock", entity.displayStock);
        put(obj, "display_minimum_stock", entity.displayMinimumStock);
        put(obj, "is_low_stock", entity.isLowStock);
        put(obj, "created_at", entity.createdAt);
        put(obj, "updated_at", entity.updatedAt);
        return obj;
    }

    private static JSONObject bankAccountJson(BankAccountEntity entity) {
        JSONObject obj = simpleJson(entity.id, entity.name);
        put(obj, "shop_id", entity.shopId);
        put(obj, "shop_name", entity.shopName);
        put(obj, "shop_code", entity.shopCode);
        put(obj, "bank_name", entity.bankName);
        put(obj, "account_number", entity.accountNumber);
        put(obj, "account_holder", entity.accountHolder);
        put(obj, "account_type", entity.accountType);
        put(obj, "opening_balance", entity.openingBalance);
        put(obj, "current_balance", entity.currentBalance);
        put(obj, "is_active", entity.isActive);
        put(obj, "note", entity.note);
        return obj;
    }

    private static void put(JSONObject obj, String key, Object value) {
        try {
            obj.put(key, value);
        } catch (JSONException ignored) {
        }
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(safe(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
