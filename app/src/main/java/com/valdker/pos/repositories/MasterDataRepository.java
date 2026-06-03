package com.valdker.pos.repositories;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.valdker.pos.SessionManager;
import com.valdker.pos.local.BankAccountEntity;
import com.valdker.pos.local.CategoryEntity;
import com.valdker.pos.local.CustomerEntity;
import com.valdker.pos.local.PaymentMethodEntity;
import com.valdker.pos.local.ProductEntity;
import com.valdker.pos.local.SupplierEntity;
import com.valdker.pos.local.UnitEntity;
import com.valdker.pos.local.ValoraLocalDatabase;
import com.valdker.pos.local.WarehouseEntity;
import com.valdker.pos.local.WarehouseStockEntity;
import com.valdker.pos.models.Category;
import com.valdker.pos.models.Customer;
import com.valdker.pos.models.Product;
import com.valdker.pos.models.ProductUnit;
import com.valdker.pos.models.Supplier;
import com.valdker.pos.models.UnitLite;
import com.valdker.pos.models.Warehouse;
import com.valdker.pos.models.WarehouseStock;
import com.valdker.pos.network.WarehouseApi;
import com.valdker.pos.network.WarehouseStockApi;
import com.valdker.pos.ui.checkout.BankAccountItem;
import com.valdker.pos.ui.checkout.PaymentMethodItem;
import com.valdker.pos.utils.NetworkUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MasterDataRepository {

    public static final String MESSAGE_NO_INTERNET_SHOWING_LOCAL =
            "No internet connection. Showing local POS data.";
    public static final String MESSAGE_NO_LOCAL_POS_DATA =
            "No local data found. Please connect to internet and sync once.";
    public static final String MESSAGE_NO_LOCAL_CATEGORY_DATA =
            "No local category data found. Please connect to internet and sync once.";

    private static final String TAG = "MASTER_DATA_REPO";

    public interface ProductsCallback {
        void onLocalProducts(@NonNull List<Product> products);
        void onRemoteProducts(@NonNull List<Product> products);
        void onNoInternet(@NonNull List<Product> localProducts);
        void onError(int statusCode, @NonNull String message);
    }

    public interface CategoriesCallback {
        void onLocalCategories(@NonNull List<Category> categories);
        void onRemoteCategories(@NonNull List<Category> categories);
        void onNoInternet(@NonNull List<Category> localCategories);
        void onError(@NonNull String message);
    }

    public interface ProductCallback {
        void onResult(@Nullable Product product);
    }

    public interface CustomersCallback {
        void onLocalCustomers(@NonNull List<Customer> customers);
        void onRemoteCustomers(@NonNull List<Customer> customers);
        void onNoInternet(@NonNull List<Customer> localCustomers);
        void onError(int statusCode, @NonNull String message);
    }

    public interface CheckoutDataCallback {
        void onLocalCheckoutData(@NonNull List<Customer> customers,
                                 @NonNull List<PaymentMethodItem> paymentMethods,
                                 @NonNull List<BankAccountItem> bankAccounts);

        void onRemoteCheckoutData(@NonNull List<Customer> customers,
                                  @NonNull List<PaymentMethodItem> paymentMethods,
                                  @NonNull List<BankAccountItem> bankAccounts);

        void onNoInternet(@NonNull List<Customer> customers,
                          @NonNull List<PaymentMethodItem> paymentMethods,
                          @NonNull List<BankAccountItem> bankAccounts);

        void onError(int statusCode, @NonNull String message);
    }

    public interface PaymentConfigCallback {
        void onLocalPaymentConfig(@NonNull List<PaymentMethodItem> paymentMethods,
                                  @NonNull List<BankAccountItem> bankAccounts);

        void onRemotePaymentConfig(@NonNull List<PaymentMethodItem> paymentMethods,
                                   @NonNull List<BankAccountItem> bankAccounts);

        void onNoInternet(@NonNull List<PaymentMethodItem> paymentMethods,
                          @NonNull List<BankAccountItem> bankAccounts);

        void onError(int statusCode, @NonNull String message);
    }

    private final Context appContext;
    private final SessionManager sessionManager;
    private final ValoraLocalDatabase db;
    private final ProductRepository productRepository;
    private final UnitRepository unitRepository;
    private final CustomerRepository customerRepository;
    private final CheckoutConfigRepository checkoutConfigRepository;
    private final SupplierRepository supplierRepository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public MasterDataRepository(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.sessionManager = new SessionManager(appContext);
        this.db = ValoraLocalDatabase.getInstance(appContext);
        this.productRepository = new ProductRepository(appContext);
        this.unitRepository = new UnitRepository(appContext);
        this.customerRepository = new CustomerRepository(appContext);
        this.checkoutConfigRepository = new CheckoutConfigRepository(appContext);
        this.supplierRepository = new SupplierRepository(appContext);
    }

    public void loadProductsRoomFirst(@Nullable String token,
                                      @Nullable String categoryId,
                                      @NonNull ProductsCallback callback) {
        executor.execute(() -> {
            List<Product> localProducts = productsFromEntities(
                    hasKnownShop()
                            ? db.productDao().getActiveProductsForShop(currentShopId(), currentShopCode(), currentApiBaseUrl(), normalizeCategory(categoryId))
                            : new ArrayList<>()
            );

            mainHandler.post(() -> callback.onLocalProducts(localProducts));

            if (!NetworkUtils.isNetworkAvailable(appContext)) {
                mainHandler.post(() -> callback.onNoInternet(localProducts));
                return;
            }

            if (TextUtils.isEmpty(safe(token))) {
                mainHandler.post(() -> callback.onError(401, "Token missing"));
                return;
            }

            mainHandler.post(() -> refreshProductsFromApi(token, categoryId, callback));
        });
    }

    public void loadCategoriesRoomFirst(@Nullable String token,
                                        @NonNull CategoriesCallback callback) {
        executor.execute(() -> {
            List<Category> localCategories = categoriesFromEntities(
                    hasKnownShop()
                            ? db.categoryDao().getAllForShop(currentShopId(), currentShopCode(), currentApiBaseUrl())
                            : new ArrayList<>()
            );
            Log.i(TAG, "Room category count=" + localCategories.size());
            mainHandler.post(() -> callback.onLocalCategories(localCategories));

            if (!NetworkUtils.isNetworkAvailable(appContext)) {
                mainHandler.post(() -> callback.onNoInternet(localCategories));
                return;
            }

            String cleanToken = safe(token);
            if (cleanToken.isEmpty()) {
                mainHandler.post(() -> callback.onError("Token missing"));
                return;
            }

            mainHandler.post(() -> CategoryRepository.fetchCategoriesOnly(appContext, cleanToken, new CategoryRepository.Callback() {
                @Override
                public void onSuccess(@NonNull List<Category> categories) {
                    Log.i(TAG, "API category count=" + categories.size());
                    executor.execute(() -> {
                        saveCategories(categories);
                        List<Category> fresh = categoriesFromEntities(
                                hasKnownShop()
                                        ? db.categoryDao().getAllForShop(currentShopId(), currentShopCode(), currentApiBaseUrl())
                                        : new ArrayList<>()
                        );
                        Log.i(TAG, "Room category count=" + fresh.size() + " after API sync");
                        mainHandler.post(() -> callback.onRemoteCategories(fresh));
                    });
                }

                @Override
                public void onError(@NonNull String message) {
                    callback.onError(message);
                }
            }));
        });
    }

    public void findProductByBarcodeLocal(@NonNull String barcode,
                                          @NonNull ProductCallback callback) {
        String clean = safe(barcode);
        if (clean.isEmpty()) {
            mainHandler.post(() -> callback.onResult(null));
            return;
        }

        executor.execute(() -> {
            ProductEntity entity = hasKnownShop()
                    ? db.productDao().findByBarcodeOrSkuForShop(currentShopId(), currentShopCode(), currentApiBaseUrl(), clean)
                    : null;
            Product product = entity == null ? null : productFromEntity(entity);
            mainHandler.post(() -> callback.onResult(product));
        });
    }

    public void syncSupportingData(@Nullable String token) {
        String cleanToken = safe(token);
        if (cleanToken.isEmpty() || !NetworkUtils.isNetworkAvailable(appContext)) return;

        syncCategories(cleanToken);
        syncUnits(cleanToken);
        syncWarehouseStocks();
        syncCustomers(cleanToken);
        syncPaymentMethods(cleanToken);
        syncBankAccounts(cleanToken);
        syncWarehouses();
        syncSuppliers(cleanToken);
    }

    public void loadCustomersRoomFirst(@Nullable String token,
                                       @NonNull CustomersCallback callback) {
        executor.execute(() -> {
            List<Customer> localCustomers = customersFromEntities(
                    hasKnownShop()
                            ? db.customerDao().getAllForShop(currentShopId(), currentShopCode(), currentApiBaseUrl())
                            : new ArrayList<>()
            );
            mainHandler.post(() -> callback.onLocalCustomers(localCustomers));

            if (!NetworkUtils.isNetworkAvailable(appContext)) {
                mainHandler.post(() -> callback.onNoInternet(localCustomers));
                return;
            }

            String cleanToken = safe(token);
            if (cleanToken.isEmpty()) {
                mainHandler.post(() -> callback.onError(401, "Token missing"));
                return;
            }

            mainHandler.post(() -> customerRepository.fetchCustomers(cleanToken, new CustomerRepository.ListCallback() {
                @Override
                public void onSuccess(@NonNull List<Customer> customers) {
                    executor.execute(() -> {
                        saveCustomers(customers);
                        List<Customer> fresh = customersFromEntities(
                                hasKnownShop()
                                        ? db.customerDao().getAllForShop(currentShopId(), currentShopCode(), currentApiBaseUrl())
                                        : new ArrayList<>()
                        );
                        mainHandler.post(() -> callback.onRemoteCustomers(fresh));
                    });
                }

                @Override
                public void onError(int statusCode, @NonNull String message) {
                    callback.onError(statusCode, message);
                }
            }));
        });
    }

    public void loadCheckoutDataRoomFirst(@Nullable String token,
                                          @NonNull CheckoutDataCallback callback) {
        executor.execute(() -> {
            List<Customer> localCustomers = customersFromEntities(
                    hasKnownShop()
                            ? db.customerDao().getAllForShop(currentShopId(), currentShopCode(), currentApiBaseUrl())
                            : new ArrayList<>()
            );
            List<PaymentMethodItem> localPaymentMethods = paymentMethodsFromEntities(
                    hasKnownShop()
                            ? db.paymentMethodDao().getActiveForShop(currentShopId(), currentShopCode(), currentApiBaseUrl())
                            : new ArrayList<>()
            );
            List<BankAccountItem> localBankAccounts = bankAccountsFromEntities(
                    hasKnownShop()
                            ? db.bankAccountDao().getActiveForShop(currentShopId(), currentShopCode(), currentApiBaseUrl())
                            : new ArrayList<>()
            );
            mainHandler.post(() -> callback.onLocalCheckoutData(localCustomers, localPaymentMethods, localBankAccounts));

            if (!NetworkUtils.isNetworkAvailable(appContext)) {
                mainHandler.post(() -> callback.onNoInternet(localCustomers, localPaymentMethods, localBankAccounts));
                return;
            }

            String cleanToken = safe(token);
            if (cleanToken.isEmpty()) {
                mainHandler.post(() -> callback.onError(401, "Token missing"));
                return;
            }

            mainHandler.post(() -> refreshCheckoutDataFromApi(cleanToken, callback));
        });
    }

    public void loadPaymentConfigRoomFirst(@Nullable String token,
                                           @NonNull PaymentConfigCallback callback) {
        executor.execute(() -> {
            List<PaymentMethodItem> localPaymentMethods = paymentMethodsFromEntities(
                    hasKnownShop()
                            ? db.paymentMethodDao().getActiveForShop(currentShopId(), currentShopCode(), currentApiBaseUrl())
                            : new ArrayList<>()
            );
            List<BankAccountItem> localBankAccounts = bankAccountsFromEntities(
                    hasKnownShop()
                            ? db.bankAccountDao().getActiveForShop(currentShopId(), currentShopCode(), currentApiBaseUrl())
                            : new ArrayList<>()
            );
            mainHandler.post(() -> callback.onLocalPaymentConfig(localPaymentMethods, localBankAccounts));

            if (!NetworkUtils.isNetworkAvailable(appContext)) {
                mainHandler.post(() -> callback.onNoInternet(localPaymentMethods, localBankAccounts));
                return;
            }

            String cleanToken = safe(token);
            if (cleanToken.isEmpty()) {
                mainHandler.post(() -> callback.onError(401, "Token missing"));
                return;
            }

            mainHandler.post(() -> refreshPaymentConfigFromApi(cleanToken, callback));
        });
    }

    private void refreshProductsFromApi(@Nullable String token,
                                        @Nullable String categoryId,
                                        @NonNull ProductsCallback callback) {
        productRepository.fetchProducts(token, normalizeCategory(categoryId), new ProductRepository.Callback() {
            @Override
            public void onSuccess(@NonNull List<Product> products) {
                executor.execute(() -> {
                    long syncAt = System.currentTimeMillis();
                    List<ProductEntity> entities = new ArrayList<>();
                    for (Product product : products) {
                        ProductEntity entity = productToEntity(product, syncAt);
                        if (entity != null) entities.add(entity);
                    }

                    if (isAllCategory(categoryId) && hasKnownShop()) {
                        db.productDao().replaceAllForShop(currentShopId(), currentShopCode(), currentApiBaseUrl(), entities);
                    } else {
                        db.productDao().upsertAll(entities);
                    }

                    List<Product> freshLocal = productsFromEntities(
                            hasKnownShop()
                                    ? db.productDao().getActiveProductsForShop(currentShopId(), currentShopCode(), currentApiBaseUrl(), normalizeCategory(categoryId))
                                    : new ArrayList<>()
                    );
                    mainHandler.post(() -> callback.onRemoteProducts(freshLocal));
                });

                syncSupportingData(token);
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                callback.onError(statusCode, message);
            }
        });
    }

    private void refreshCheckoutDataFromApi(@NonNull String token,
                                            @NonNull CheckoutDataCallback callback) {
        checkoutConfigRepository.fetchPaymentMethods(token, new CheckoutConfigRepository.PaymentMethodsCallback() {
            @Override
            public void onSuccess(@NonNull List<PaymentMethodItem> paymentMethods) {
                checkoutConfigRepository.fetchBankAccounts(token, new CheckoutConfigRepository.BankAccountsCallback() {
                    @Override
                    public void onSuccess(@NonNull List<BankAccountItem> bankAccounts) {
                        customerRepository.fetchCustomers(token, new CustomerRepository.ListCallback() {
                            @Override
                            public void onSuccess(@NonNull List<Customer> customers) {
                                executor.execute(() -> {
                                    savePaymentMethods(paymentMethods);
                                    saveBankAccounts(bankAccounts);
                                    saveCustomers(customers);
                                    List<Customer> freshCustomers = customersFromEntities(
                                            hasKnownShop()
                                                    ? db.customerDao().getAllForShop(currentShopId(), currentShopCode(), currentApiBaseUrl())
                                                    : new ArrayList<>()
                                    );
                                    List<PaymentMethodItem> freshPaymentMethods = paymentMethodsFromEntities(
                                            hasKnownShop()
                                                    ? db.paymentMethodDao().getActiveForShop(currentShopId(), currentShopCode(), currentApiBaseUrl())
                                                    : new ArrayList<>()
                                    );
                                    List<BankAccountItem> freshBankAccounts = bankAccountsFromEntities(
                                            hasKnownShop()
                                                    ? db.bankAccountDao().getActiveForShop(currentShopId(), currentShopCode(), currentApiBaseUrl())
                                                    : new ArrayList<>()
                                    );
                                    mainHandler.post(() -> callback.onRemoteCheckoutData(
                                            freshCustomers,
                                            freshPaymentMethods,
                                            freshBankAccounts
                                    ));
                                });
                            }

                            @Override
                            public void onError(int statusCode, @NonNull String message) {
                                executor.execute(() -> {
                                    savePaymentMethods(paymentMethods);
                                    saveBankAccounts(bankAccounts);
                                    List<Customer> freshCustomers = customersFromEntities(
                                            hasKnownShop()
                                                    ? db.customerDao().getAllForShop(currentShopId(), currentShopCode(), currentApiBaseUrl())
                                                    : new ArrayList<>()
                                    );
                                    List<PaymentMethodItem> freshPaymentMethods = paymentMethodsFromEntities(
                                            hasKnownShop()
                                                    ? db.paymentMethodDao().getActiveForShop(currentShopId(), currentShopCode(), currentApiBaseUrl())
                                                    : new ArrayList<>()
                                    );
                                    List<BankAccountItem> freshBankAccounts = bankAccountsFromEntities(
                                            hasKnownShop()
                                                    ? db.bankAccountDao().getActiveForShop(currentShopId(), currentShopCode(), currentApiBaseUrl())
                                                    : new ArrayList<>()
                                    );
                                    mainHandler.post(() -> callback.onRemoteCheckoutData(
                                            freshCustomers,
                                            freshPaymentMethods,
                                            freshBankAccounts
                                    ));
                                });
                            }
                        });
                    }

                    @Override
                    public void onError(int statusCode, @NonNull String message) {
                        callback.onError(statusCode, message);
                    }
                });
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                callback.onError(statusCode, message);
            }
        });
    }

    private void refreshPaymentConfigFromApi(@NonNull String token,
                                             @NonNull PaymentConfigCallback callback) {
        checkoutConfigRepository.fetchPaymentMethods(token, new CheckoutConfigRepository.PaymentMethodsCallback() {
            @Override
            public void onSuccess(@NonNull List<PaymentMethodItem> paymentMethods) {
                checkoutConfigRepository.fetchBankAccounts(token, new CheckoutConfigRepository.BankAccountsCallback() {
                    @Override
                    public void onSuccess(@NonNull List<BankAccountItem> bankAccounts) {
                        executor.execute(() -> {
                            savePaymentMethods(paymentMethods);
                            saveBankAccounts(bankAccounts);
                            List<PaymentMethodItem> freshPaymentMethods = paymentMethodsFromEntities(
                                    hasKnownShop()
                                            ? db.paymentMethodDao().getActiveForShop(currentShopId(), currentShopCode(), currentApiBaseUrl())
                                            : new ArrayList<>()
                            );
                            List<BankAccountItem> freshBankAccounts = bankAccountsFromEntities(
                                    hasKnownShop()
                                            ? db.bankAccountDao().getActiveForShop(currentShopId(), currentShopCode(), currentApiBaseUrl())
                                            : new ArrayList<>()
                            );
                            mainHandler.post(() -> callback.onRemotePaymentConfig(freshPaymentMethods, freshBankAccounts));
                        });
                    }

                    @Override
                    public void onError(int statusCode, @NonNull String message) {
                        executor.execute(() -> {
                            savePaymentMethods(paymentMethods);
                            List<PaymentMethodItem> freshPaymentMethods = paymentMethodsFromEntities(
                                    hasKnownShop()
                                            ? db.paymentMethodDao().getActiveForShop(currentShopId(), currentShopCode(), currentApiBaseUrl())
                                            : new ArrayList<>()
                            );
                            List<BankAccountItem> freshBankAccounts = bankAccountsFromEntities(
                                    hasKnownShop()
                                            ? db.bankAccountDao().getActiveForShop(currentShopId(), currentShopCode(), currentApiBaseUrl())
                                            : new ArrayList<>()
                            );
                            mainHandler.post(() -> callback.onRemotePaymentConfig(freshPaymentMethods, freshBankAccounts));
                        });
                    }
                });
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                callback.onError(statusCode, message);
            }
        });
    }

    private void syncCategories(@NonNull String token) {
        CategoryRepository.fetchCategoriesOnly(appContext, token, new CategoryRepository.Callback() {
            @Override
            public void onSuccess(@NonNull List<Category> categories) {
                Log.i(TAG, "API category count=" + categories.size());
                executor.execute(() -> saveCategories(categories));
            }

            @Override
            public void onError(@NonNull String message) {
                Log.w(TAG, "Category sync failed: " + message);
            }
        });
    }

    private void saveCategories(@Nullable List<Category> categories) {
        long syncAt = System.currentTimeMillis();
        List<CategoryEntity> entities = new ArrayList<>();
        if (categories != null) {
            for (Category category : categories) {
                if (category == null || category.id <= 0) continue;
                CategoryEntity entity = new CategoryEntity();
                entity.id = category.id;
                entity.shopId = currentShopId();
                entity.shopCode = currentShopCode();
                entity.apiBaseUrl = currentApiBaseUrl();
                entity.cacheKey = cacheKey(entity.id);
                entity.name = safe(category.name);
                entity.iconUrl = safe(category.iconUrl);
                entity.rawJson = "";
                entity.lastSyncAt = syncAt;
                entities.add(entity);
            }
        }
        if (hasKnownShop()) {
            db.categoryDao().replaceAllForShop(currentShopId(), currentShopCode(), currentApiBaseUrl(), entities);
        }
    }

    private void syncUnits(@NonNull String token) {
        unitRepository.fetchUnits(token, new UnitRepository.Callback() {
            @Override
            public void onSuccess(@NonNull List<UnitLite> list) {
                executor.execute(() -> {
                    long syncAt = System.currentTimeMillis();
                    List<UnitEntity> entities = new ArrayList<>();
                    for (UnitLite unit : list) {
                        if (unit == null || unit.id <= 0) continue;
                        UnitEntity entity = new UnitEntity();
                        entity.id = unit.id;
                        entity.shopId = currentShopId();
                        entity.shopCode = currentShopCode();
                        entity.apiBaseUrl = currentApiBaseUrl();
                        entity.cacheKey = cacheKey(entity.id);
                        entity.name = safe(unit.name);
                        entity.rawJson = "";
                        entity.lastSyncAt = syncAt;
                        entities.add(entity);
                    }
                    if (hasKnownShop()) {
                        db.unitDao().replaceAllForShop(currentShopId(), currentShopCode(), currentApiBaseUrl(), entities);
                    }
                });
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                Log.w(TAG, "Unit sync failed: " + message);
            }
        });
    }

    private void syncWarehouseStocks() {
        WarehouseStockApi.getWarehouseStocks(appContext, sessionManager, new WarehouseStockApi.WarehouseStockListCallback() {
            @Override
            public void onSuccess(List<WarehouseStock> stocks) {
                executor.execute(() -> {
                    long syncAt = System.currentTimeMillis();
                    List<WarehouseStockEntity> entities = new ArrayList<>();
                    if (stocks != null) {
                        for (WarehouseStock stock : stocks) {
                            WarehouseStockEntity entity = warehouseStockToEntity(stock, syncAt);
                            if (entity != null) entities.add(entity);
                        }
                    }
                    if (hasKnownShop()) {
                        db.warehouseStockDao().replaceAllForShop(currentShopId(), currentShopCode(), currentApiBaseUrl(), entities);
                    }
                });
            }

            @Override
            public void onError(String message) {
                Log.w(TAG, "Warehouse stock sync failed: " + message);
            }
        });
    }

    private void syncCustomers(@NonNull String token) {
        customerRepository.fetchCustomers(token, new CustomerRepository.ListCallback() {
            @Override
            public void onSuccess(@NonNull List<Customer> customers) {
                executor.execute(() -> saveCustomers(customers));
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                Log.w(TAG, "Customer sync failed: " + message);
            }
        });
    }

    private void syncPaymentMethods(@NonNull String token) {
        checkoutConfigRepository.fetchPaymentMethods(token, new CheckoutConfigRepository.PaymentMethodsCallback() {
            @Override
            public void onSuccess(@NonNull List<PaymentMethodItem> items) {
                executor.execute(() -> savePaymentMethods(items));
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                Log.w(TAG, "Payment method sync failed: " + message);
            }
        });
    }

    private void syncBankAccounts(@NonNull String token) {
        checkoutConfigRepository.fetchBankAccounts(token, new CheckoutConfigRepository.BankAccountsCallback() {
            @Override
            public void onSuccess(@NonNull List<BankAccountItem> items) {
                executor.execute(() -> saveBankAccounts(items));
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                Log.w(TAG, "Bank account sync failed: " + message);
            }
        });
    }

    private void syncWarehouses() {
        WarehouseApi.getWarehouses(appContext, sessionManager, new WarehouseApi.WarehouseListCallback() {
            @Override
            public void onSuccess(List<Warehouse> warehouses) {
                executor.execute(() -> saveWarehouses(warehouses));
            }

            @Override
            public void onError(String message) {
                Log.w(TAG, "Warehouse sync failed: " + message);
            }
        });
    }

    private void syncSuppliers(@NonNull String token) {
        supplierRepository.fetchSuppliers(token, new SupplierRepository.ListCallback() {
            @Override
            public void onSuccess(@NonNull List<Supplier> list) {
                executor.execute(() -> saveSuppliers(list));
            }

            @Override
            public void onError(int code, @NonNull String message) {
                Log.w(TAG, "Supplier sync failed: " + message);
            }
        });
    }

    private void saveCustomers(@Nullable List<Customer> customers) {
        long syncAt = System.currentTimeMillis();
        List<CustomerEntity> entities = new ArrayList<>();
        if (customers != null) {
            for (Customer customer : customers) {
                if (customer == null || customer.id <= 0) continue;
                CustomerEntity entity = new CustomerEntity();
                entity.id = customer.id;
                entity.shopId = currentShopId();
                entity.shopCode = currentShopCode();
                entity.apiBaseUrl = currentApiBaseUrl();
                entity.cacheKey = cacheKey(entity.id);
                entity.name = safe(customer.name);
                entity.cell = safe(customer.cell);
                entity.email = safe(customer.email);
                entity.address = safe(customer.address);
                entity.points = customer.points;
                entity.rawJson = "";
                entity.lastSyncAt = syncAt;
                entities.add(entity);
            }
        }
        if (hasKnownShop()) {
            db.customerDao().replaceAllForShop(currentShopId(), currentShopCode(), currentApiBaseUrl(), entities);
        }
    }

    private void savePaymentMethods(@Nullable List<PaymentMethodItem> paymentMethods) {
        long syncAt = System.currentTimeMillis();
        List<PaymentMethodEntity> entities = new ArrayList<>();
        if (paymentMethods != null) {
            for (PaymentMethodItem item : paymentMethods) {
                if (item == null || item.id <= 0) continue;
                PaymentMethodEntity entity = new PaymentMethodEntity();
                entity.id = item.id;
                entity.shopId = currentShopId();
                entity.shopCode = currentShopCode();
                entity.apiBaseUrl = currentApiBaseUrl();
                entity.cacheKey = cacheKey(entity.id);
                entity.name = safe(item.name);
                entity.code = safe(item.code);
                entity.paymentType = safe(item.payment_type);
                entity.requiresBankAccount = item.requires_bank_account;
                entity.isActive = item.is_active;
                entity.note = safe(item.note);
                entity.rawJson = "";
                entity.lastSyncAt = syncAt;
                entities.add(entity);
            }
        }
        if (hasKnownShop()) {
            db.paymentMethodDao().replaceAllForShop(currentShopId(), currentShopCode(), currentApiBaseUrl(), entities);
        }
    }

    private void saveBankAccounts(@Nullable List<BankAccountItem> bankAccounts) {
        long syncAt = System.currentTimeMillis();
        List<BankAccountEntity> entities = new ArrayList<>();
        if (bankAccounts != null) {
            for (BankAccountItem item : bankAccounts) {
                if (item == null || item.id <= 0) continue;
                BankAccountEntity entity = new BankAccountEntity();
                entity.id = item.id;
                entity.shopId = currentShopId();
                entity.shopName = sessionManager.getShopName();
                entity.shopCode = currentShopCode();
                entity.apiBaseUrl = currentApiBaseUrl();
                entity.cacheKey = cacheKey(entity.id);
                entity.name = safe(item.name);
                entity.bankName = safe(item.bank_name);
                entity.accountNumber = safe(item.account_number);
                entity.accountHolder = safe(item.account_holder);
                entity.accountType = safe(item.account_type);
                entity.currentBalance = safe(item.current_balance);
                entity.isActive = item.is_active;
                entity.rawJson = "";
                entity.lastSyncAt = syncAt;
                entities.add(entity);
            }
        }
        if (hasKnownShop()) {
            db.bankAccountDao().replaceAllForShop(currentShopId(), currentShopCode(), currentApiBaseUrl(), entities);
        }
    }

    private void saveWarehouses(@Nullable List<Warehouse> warehouses) {
        long syncAt = System.currentTimeMillis();
        List<WarehouseEntity> entities = new ArrayList<>();
        if (warehouses != null) {
            for (Warehouse warehouse : warehouses) {
                if (warehouse == null || warehouse.getId() <= 0) continue;
                WarehouseEntity entity = new WarehouseEntity();
                entity.id = warehouse.getId();
                entity.shopId = currentShopId();
                entity.shopName = safe(warehouse.getShopName()).isEmpty() ? sessionManager.getShopName() : safe(warehouse.getShopName());
                entity.shopCode = currentShopCode();
                entity.apiBaseUrl = currentApiBaseUrl();
                entity.cacheKey = cacheKey(entity.id);
                entity.name = safe(warehouse.getName());
                entity.code = safe(warehouse.getCode());
                entity.location = safe(warehouse.getLocation());
                entity.isActive = warehouse.isActive();
                entity.isDefault = warehouse.isDefault();
                entity.createdAt = safe(warehouse.getCreatedAt());
                entity.updatedAt = safe(warehouse.getUpdatedAt());
                entity.rawJson = "";
                entity.lastSyncAt = syncAt;
                entities.add(entity);
            }
        }
        if (hasKnownShop()) {
            db.warehouseDao().replaceAllForShop(currentShopId(), currentShopCode(), currentApiBaseUrl(), entities);
        }
    }

    private void saveSuppliers(@Nullable List<Supplier> suppliers) {
        long syncAt = System.currentTimeMillis();
        List<SupplierEntity> entities = new ArrayList<>();
        if (suppliers != null) {
            for (Supplier supplier : suppliers) {
                if (supplier == null || supplier.id <= 0) continue;
                SupplierEntity entity = new SupplierEntity();
                entity.id = supplier.id;
                entity.shopId = currentShopId();
                entity.shopCode = currentShopCode();
                entity.apiBaseUrl = currentApiBaseUrl();
                entity.cacheKey = cacheKey(entity.id);
                entity.name = safe(supplier.name);
                entity.contactPerson = safe(supplier.contactPerson);
                entity.cell = safe(supplier.cell);
                entity.email = safe(supplier.email);
                entity.address = safe(supplier.address);
                entity.rawJson = "";
                entity.lastSyncAt = syncAt;
                entities.add(entity);
            }
        }
        if (hasKnownShop()) {
            db.supplierDao().replaceAllForShop(currentShopId(), currentShopCode(), currentApiBaseUrl(), entities);
        }
    }

    @Nullable
    private ProductEntity productToEntity(@Nullable Product product, long syncAt) {
        if (product == null || safe(product.id).isEmpty()) return null;

        ProductEntity entity = new ProductEntity();
        entity.id = safe(product.id);
        entity.name = safe(product.name);
        entity.shopId = currentShopId();
        entity.shopCode = currentShopCode();
        entity.apiBaseUrl = currentApiBaseUrl();
        entity.cacheKey = cacheKey(entity.id);
        entity.sku = safe(product.sku);
        entity.barcode = safe(product.barcode);
        entity.price = product.price;
        entity.stock = product.stock;
        entity.imageUrl = firstNonEmpty(product.imageUrl, product.image_url);
        entity.categoryId = safe(product.categoryId);
        entity.categoryName = safe(product.categoryName);
        entity.description = safe(product.description);
        entity.buyPrice = safe(product.buyPrice);
        entity.sellPrice = safe(product.sellPrice);
        entity.weight = safe(product.weight);
        entity.unitId = safe(product.unitId);
        entity.unitName = safe(product.unitName);
        entity.supplierId = safe(product.supplierId);
        entity.supplierName = safe(product.supplierName);
        entity.itemType = safe(product.itemType);
        entity.isActive = product.isActive;
        entity.trackStock = product.trackStock;
        entity.productUnitsJson = productUnitsToJson(product.product_units);
        entity.rawJson = "";
        entity.lastSyncAt = syncAt;
        return entity;
    }

    @NonNull
    private static Product productFromEntity(@NonNull ProductEntity entity) {
        Product product = new Product();
        product.id = entity.id;
        product.name = entity.name;
        product.shopId = entity.shopId;
        product.shop_id = entity.shopId;
        product.sku = entity.sku;
        product.barcode = entity.barcode;
        product.price = entity.price;
        product.stock = entity.stock;
        product.imageUrl = entity.imageUrl;
        product.image_url = entity.imageUrl;
        product.categoryId = entity.categoryId;
        product.categoryName = entity.categoryName;
        product.description = entity.description;
        product.buyPrice = entity.buyPrice;
        product.sellPrice = entity.sellPrice;
        product.weight = entity.weight;
        product.unitId = entity.unitId;
        product.unitName = entity.unitName;
        product.supplierId = entity.supplierId;
        product.supplierName = entity.supplierName;
        product.itemType = entity.itemType;
        product.isActive = entity.isActive;
        product.trackStock = entity.trackStock;
        product.product_units.clear();
        product.product_units.addAll(productUnitsFromJson(entity.productUnitsJson));
        return product;
    }

    @NonNull
    private static List<Product> productsFromEntities(@Nullable List<ProductEntity> entities) {
        List<Product> products = new ArrayList<>();
        if (entities == null) return products;

        for (ProductEntity entity : entities) {
            if (entity != null) products.add(productFromEntity(entity));
        }
        return products;
    }

    @NonNull
    private static List<Category> categoriesFromEntities(@Nullable List<CategoryEntity> entities) {
        List<Category> categories = new ArrayList<>();
        if (entities == null) return categories;

        for (CategoryEntity entity : entities) {
            if (entity == null || entity.id <= 0) continue;
            categories.add(new Category(entity.id, safe(entity.name), safe(entity.iconUrl)));
        }
        return categories;
    }

    @NonNull
    private static List<Customer> customersFromEntities(@Nullable List<CustomerEntity> entities) {
        List<Customer> customers = new ArrayList<>();
        if (entities == null) return customers;

        for (CustomerEntity entity : entities) {
            if (entity == null || entity.id <= 0) continue;
            customers.add(new Customer(
                    entity.id,
                    safe(entity.name),
                    safe(entity.cell),
                    nullableString(entity.email),
                    nullableString(entity.address),
                    entity.points
            ));
        }
        return customers;
    }

    @NonNull
    private static List<PaymentMethodItem> paymentMethodsFromEntities(@Nullable List<PaymentMethodEntity> entities) {
        List<PaymentMethodItem> items = new ArrayList<>();
        if (entities == null) return items;

        for (PaymentMethodEntity entity : entities) {
            if (entity == null || entity.id <= 0) continue;
            PaymentMethodItem item = new PaymentMethodItem();
            item.id = entity.id;
            item.name = safe(entity.name);
            item.code = safe(entity.code);
            item.payment_type = safe(entity.paymentType);
            item.requires_bank_account = entity.requiresBankAccount;
            item.is_active = entity.isActive;
            item.note = safe(entity.note);
            items.add(item);
        }
        return items;
    }

    @NonNull
    private static List<BankAccountItem> bankAccountsFromEntities(@Nullable List<BankAccountEntity> entities) {
        List<BankAccountItem> items = new ArrayList<>();
        if (entities == null) return items;

        for (BankAccountEntity entity : entities) {
            if (entity == null || entity.id <= 0) continue;
            BankAccountItem item = new BankAccountItem();
            item.id = entity.id;
            item.name = safe(entity.name);
            item.bank_name = safe(entity.bankName);
            item.account_number = safe(entity.accountNumber);
            item.account_holder = safe(entity.accountHolder);
            item.account_type = safe(entity.accountType);
            item.current_balance = safe(entity.currentBalance);
            item.is_active = entity.isActive;
            items.add(item);
        }
        return items;
    }

    @Nullable
    private WarehouseStockEntity warehouseStockToEntity(@Nullable WarehouseStock stock, long syncAt) {
        if (stock == null) return null;

        WarehouseStockEntity entity = new WarehouseStockEntity();
        entity.id = stock.getId();
        entity.shopId = currentShopId();
        entity.shopName = safe(stock.getShopName()).isEmpty() ? sessionManager.getShopName() : safe(stock.getShopName());
        entity.shopCode = currentShopCode();
        entity.apiBaseUrl = currentApiBaseUrl();
        entity.warehouse = stock.getWarehouse();
        entity.warehouseName = safe(stock.getWarehouseName());
        entity.warehouseCode = safe(stock.getWarehouseCode());
        entity.product = stock.getProduct();
        entity.productUnit = stock.getProductUnit();
        entity.productName = safe(stock.getProductName());
        entity.productCode = safe(stock.getProductCode());
        entity.productSku = safe(stock.getProductSku());
        entity.productTrackStock = stock.isProductTrackStock();
        entity.quantity = stock.getQuantity();
        entity.minStock = stock.getMinStock();
        entity.quantityBase = stock.getQuantityBase();
        entity.baseUnitName = safe(stock.getBaseUnitName());
        entity.displayStock = safe(stock.getDisplayStock());
        entity.displayMinimumStock = safe(stock.getDisplayMinimumStock());
        entity.isLowStock = stock.isLowStock();
        entity.createdAt = safe(stock.getCreatedAt());
        entity.updatedAt = safe(stock.getUpdatedAt());
        entity.rawJson = "";
        entity.lastSyncAt = syncAt;
        entity.cacheKey = cacheKey(stock.getId() > 0
                ? String.valueOf(stock.getId())
                : stock.getWarehouse() + ":" + stock.getProduct() + ":" + stock.getProductUnit());
        return entity;
    }

    @NonNull
    private static String productUnitsToJson(@Nullable List<ProductUnit> units) {
        JSONArray array = new JSONArray();
        if (units == null) return array.toString();

        for (ProductUnit unit : units) {
            if (unit == null) continue;
            JSONObject obj = new JSONObject();
            try {
                obj.put("id", unit.getId());
                obj.put("product", unit.getProduct());
                obj.put("unit", unit.getUnitId());
                obj.put("unit_name", unit.getUnitName());
                obj.put("conversion_qty", unit.getConversionQty());
                obj.put("barcode", unit.getBarcode());
                obj.put("is_base_unit", unit.isBaseUnit());
                obj.put("is_default_purchase_unit", unit.isDefaultPurchaseUnit());
                obj.put("is_default_sale_unit", unit.isDefaultSaleUnit());
                array.put(obj);
            } catch (Exception ignored) {
            }
        }
        return array.toString();
    }

    @NonNull
    private static List<ProductUnit> productUnitsFromJson(@Nullable String json) {
        List<ProductUnit> out = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(TextUtils.isEmpty(json) ? "[]" : json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj != null) out.add(ProductUnit.fromJson(obj));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    @NonNull
    private static String normalizeCategory(@Nullable String categoryId) {
        String c = safe(categoryId);
        return c.isEmpty() ? "all" : c;
    }

    private static boolean isAllCategory(@Nullable String categoryId) {
        String c = normalizeCategory(categoryId);
        return "all".equalsIgnoreCase(c) || "-1".equals(c);
    }

    private boolean hasKnownShop() {
        return currentShopId() > 0 && !currentShopCode().isEmpty();
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
    private static String normalizeShopCode(@Nullable String value) {
        return safe(value).toUpperCase(Locale.US);
    }

    @NonNull
    private static String safe(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    @Nullable
    private static String nullableString(@Nullable String value) {
        String clean = safe(value);
        return clean.isEmpty() ? null : clean;
    }

    @NonNull
    private static String firstNonEmpty(@Nullable String first, @Nullable String second) {
        String a = safe(first);
        return a.isEmpty() ? safe(second) : a;
    }
}
