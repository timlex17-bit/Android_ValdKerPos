package com.valdker.pos.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(
        entities = {
                ProductEntity.class,
                CategoryEntity.class,
                UnitEntity.class,
                WarehouseStockEntity.class,
                CustomerEntity.class,
                PaymentMethodEntity.class,
                BankAccountEntity.class,
                WarehouseEntity.class,
                SupplierEntity.class,
                PendingOrderEntity.class,
                PendingOrderItemEntity.class,
                ShopProfileEntity.class,
                CachedOrderEntity.class,
                CachedOrderItemEntity.class,
                CachedPaymentEntity.class,
                CachedExpenseEntity.class,
                CachedBankLedgerEntity.class,
                CachedStockMovementEntity.class,
                CachedStockAdjustmentEntity.class,
                CachedStockTransferEntity.class,
                CachedStockTransferItemEntity.class,
                CachedInventoryCountEntity.class,
                CachedInventoryCountItemEntity.class,
                CachedPurchaseEntity.class,
                CachedPurchaseItemEntity.class,
                CachedProductReturnEntity.class,
                CachedProductReturnItemEntity.class,
                CachedReportEntity.class,
                CachedUserEntity.class,
                CachedRoleEntity.class,
                CachedMenuPermissionEntity.class
        },
        version = 14,
        exportSchema = false
)
public abstract class ValoraLocalDatabase extends RoomDatabase {

    private static volatile ValoraLocalDatabase instance;

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `customers` (`id` INTEGER NOT NULL, `name` TEXT, `cell` TEXT, `email` TEXT, `address` TEXT, `points` INTEGER NOT NULL, `lastSyncAt` INTEGER NOT NULL, PRIMARY KEY(`id`))");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_customers_name` ON `customers` (`name`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_customers_cell` ON `customers` (`cell`)");

            db.execSQL("CREATE TABLE IF NOT EXISTS `payment_methods` (`id` INTEGER NOT NULL, `name` TEXT, `code` TEXT, `paymentType` TEXT, `requiresBankAccount` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, `note` TEXT, `lastSyncAt` INTEGER NOT NULL, PRIMARY KEY(`id`))");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_payment_methods_code` ON `payment_methods` (`code`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_payment_methods_isActive` ON `payment_methods` (`isActive`)");

            db.execSQL("CREATE TABLE IF NOT EXISTS `bank_accounts` (`id` INTEGER NOT NULL, `name` TEXT, `bankName` TEXT, `accountNumber` TEXT, `accountHolder` TEXT, `accountType` TEXT, `currentBalance` TEXT, `isActive` INTEGER NOT NULL, `lastSyncAt` INTEGER NOT NULL, PRIMARY KEY(`id`))");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_bank_accounts_bankName` ON `bank_accounts` (`bankName`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_bank_accounts_isActive` ON `bank_accounts` (`isActive`)");

            db.execSQL("CREATE TABLE IF NOT EXISTS `warehouses` (`id` INTEGER NOT NULL, `shopId` INTEGER NOT NULL, `shopName` TEXT, `shopCode` TEXT, `name` TEXT, `code` TEXT, `location` TEXT, `isActive` INTEGER NOT NULL, `isDefault` INTEGER NOT NULL, `createdAt` TEXT, `updatedAt` TEXT, `lastSyncAt` INTEGER NOT NULL, PRIMARY KEY(`id`))");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_warehouses_shopId` ON `warehouses` (`shopId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_warehouses_code` ON `warehouses` (`code`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_warehouses_isActive` ON `warehouses` (`isActive`)");

            db.execSQL("CREATE TABLE IF NOT EXISTS `suppliers` (`id` INTEGER NOT NULL, `name` TEXT, `contactPerson` TEXT, `cell` TEXT, `email` TEXT, `address` TEXT, `lastSyncAt` INTEGER NOT NULL, PRIMARY KEY(`id`))");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_suppliers_name` ON `suppliers` (`name`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_suppliers_cell` ON `suppliers` (`cell`)");
        }
    };

    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `pending_orders` (`localOrderId` TEXT NOT NULL, `rawPayloadJson` TEXT NOT NULL, `businessType` TEXT NOT NULL, `customerId` INTEGER, `paymentMethodId` INTEGER, `bankAccountId` INTEGER, `subtotal` REAL NOT NULL, `discount` REAL NOT NULL, `tax` REAL NOT NULL, `total` REAL NOT NULL, `paidAmount` REAL NOT NULL, `changeAmount` REAL NOT NULL, `orderType` TEXT NOT NULL, `note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `syncStatus` TEXT NOT NULL, `syncAttemptCount` INTEGER NOT NULL, `lastSyncError` TEXT NOT NULL, PRIMARY KEY(`localOrderId`))");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_orders_syncStatus` ON `pending_orders` (`syncStatus`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_orders_businessType` ON `pending_orders` (`businessType`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_orders_createdAt` ON `pending_orders` (`createdAt`)");

            db.execSQL("CREATE TABLE IF NOT EXISTS `pending_order_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `localOrderId` TEXT NOT NULL, `productId` INTEGER NOT NULL, `itemType` TEXT NOT NULL, `name` TEXT NOT NULL, `sku` TEXT NOT NULL, `barcode` TEXT NOT NULL, `quantity` INTEGER NOT NULL, `price` REAL NOT NULL, `discount` REAL NOT NULL, `total` REAL NOT NULL, `note` TEXT NOT NULL, FOREIGN KEY(`localOrderId`) REFERENCES `pending_orders`(`localOrderId`) ON UPDATE NO ACTION ON DELETE CASCADE)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_order_items_localOrderId` ON `pending_order_items` (`localOrderId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_order_items_productId` ON `pending_order_items` (`productId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_order_items_itemType` ON `pending_order_items` (`itemType`)");
        }
    };

    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `pending_orders` ADD COLUMN `clientOrderId` TEXT NOT NULL DEFAULT ''");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_orders_clientOrderId` ON `pending_orders` (`clientOrderId`)");
        }
    };

    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `shop_profile` (`localKey` INTEGER NOT NULL, `id` INTEGER NOT NULL, `shopId` TEXT NOT NULL, `name` TEXT NOT NULL, `storeName` TEXT NOT NULL, `address` TEXT NOT NULL, `phone` TEXT NOT NULL, `email` TEXT NOT NULL, `logoUrl` TEXT NOT NULL, `location` TEXT NOT NULL, `version` TEXT NOT NULL, `updatedAt` TEXT NOT NULL, `lastSyncAt` INTEGER NOT NULL, PRIMARY KEY(`localKey`))");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_shop_profile_id` ON `shop_profile` (`id`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_shop_profile_shopId` ON `shop_profile` (`shopId`)");
        }
    };

    static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `pending_orders` ADD COLUMN `shopId` INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE `pending_orders` ADD COLUMN `shopCode` TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE `pending_orders` ADD COLUMN `shopName` TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE `pending_orders` ADD COLUMN `apiBaseUrl` TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE `pending_orders` ADD COLUMN `createdByUserId` TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE `pending_orders` ADD COLUMN `createdByUsername` TEXT NOT NULL DEFAULT ''");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_orders_shopId` ON `pending_orders` (`shopId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_orders_shopCode` ON `pending_orders` (`shopCode`)");
        }
    };

    static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `products` ADD COLUMN `shopCode` TEXT");
            db.execSQL("ALTER TABLE `products` ADD COLUMN `apiBaseUrl` TEXT");
            db.execSQL("ALTER TABLE `products` ADD COLUMN `rawJson` TEXT");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_shopId` ON `products` (`shopId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_shopCode` ON `products` (`shopCode`)");

            db.execSQL("ALTER TABLE `categories` ADD COLUMN `shopId` INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE `categories` ADD COLUMN `shopCode` TEXT");
            db.execSQL("ALTER TABLE `categories` ADD COLUMN `apiBaseUrl` TEXT");
            db.execSQL("ALTER TABLE `categories` ADD COLUMN `rawJson` TEXT");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_categories_shopId` ON `categories` (`shopId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_categories_shopCode` ON `categories` (`shopCode`)");

            db.execSQL("ALTER TABLE `units` ADD COLUMN `shopId` INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE `units` ADD COLUMN `shopCode` TEXT");
            db.execSQL("ALTER TABLE `units` ADD COLUMN `apiBaseUrl` TEXT");
            db.execSQL("ALTER TABLE `units` ADD COLUMN `rawJson` TEXT");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_units_shopId` ON `units` (`shopId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_units_shopCode` ON `units` (`shopCode`)");

            db.execSQL("ALTER TABLE `customers` ADD COLUMN `shopId` INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE `customers` ADD COLUMN `shopCode` TEXT");
            db.execSQL("ALTER TABLE `customers` ADD COLUMN `apiBaseUrl` TEXT");
            db.execSQL("ALTER TABLE `customers` ADD COLUMN `rawJson` TEXT");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_customers_shopId` ON `customers` (`shopId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_customers_shopCode` ON `customers` (`shopCode`)");

            db.execSQL("ALTER TABLE `suppliers` ADD COLUMN `shopId` INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE `suppliers` ADD COLUMN `shopCode` TEXT");
            db.execSQL("ALTER TABLE `suppliers` ADD COLUMN `apiBaseUrl` TEXT");
            db.execSQL("ALTER TABLE `suppliers` ADD COLUMN `rawJson` TEXT");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_suppliers_shopId` ON `suppliers` (`shopId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_suppliers_shopCode` ON `suppliers` (`shopCode`)");

            db.execSQL("ALTER TABLE `warehouses` ADD COLUMN `apiBaseUrl` TEXT");
            db.execSQL("ALTER TABLE `warehouses` ADD COLUMN `rawJson` TEXT");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_warehouses_shopCode` ON `warehouses` (`shopCode`)");

            db.execSQL("ALTER TABLE `warehouse_stocks` ADD COLUMN `apiBaseUrl` TEXT");
            db.execSQL("ALTER TABLE `warehouse_stocks` ADD COLUMN `rawJson` TEXT");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_warehouse_stocks_shopId` ON `warehouse_stocks` (`shopId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_warehouse_stocks_shopCode` ON `warehouse_stocks` (`shopCode`)");

            db.execSQL("ALTER TABLE `bank_accounts` ADD COLUMN `shopId` INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE `bank_accounts` ADD COLUMN `shopName` TEXT");
            db.execSQL("ALTER TABLE `bank_accounts` ADD COLUMN `shopCode` TEXT");
            db.execSQL("ALTER TABLE `bank_accounts` ADD COLUMN `apiBaseUrl` TEXT");
            db.execSQL("ALTER TABLE `bank_accounts` ADD COLUMN `openingBalance` TEXT");
            db.execSQL("ALTER TABLE `bank_accounts` ADD COLUMN `note` TEXT");
            db.execSQL("ALTER TABLE `bank_accounts` ADD COLUMN `rawJson` TEXT");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_bank_accounts_shopId` ON `bank_accounts` (`shopId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_bank_accounts_shopCode` ON `bank_accounts` (`shopCode`)");
        }
    };

    static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            rebuildProducts(db);
            rebuildCategories(db);
            rebuildUnits(db);
            rebuildCustomers(db);
            rebuildSuppliers(db);
            rebuildWarehouses(db);
            rebuildBankAccounts(db);
            rebuildPaymentMethods(db);
            rebuildShopProfile(db);

            db.execSQL("UPDATE `warehouse_stocks` SET `cacheKey` = COALESCE(`apiBaseUrl`, '') || '|' || `shopId` || '|' || UPPER(COALESCE(`shopCode`, '')) || '|' || CASE WHEN `id` > 0 THEN CAST(`id` AS TEXT) ELSE CAST(`warehouse` AS TEXT) || ':' || CAST(`product` AS TEXT) || ':' || CAST(`productUnit` AS TEXT) END");
        }

        private void rebuildProducts(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `products_new` (`cacheKey` TEXT NOT NULL, `id` TEXT NOT NULL, `name` TEXT, `shopId` INTEGER NOT NULL, `shopCode` TEXT, `apiBaseUrl` TEXT, `sku` TEXT, `barcode` TEXT, `price` REAL NOT NULL, `stock` INTEGER NOT NULL, `imageUrl` TEXT, `categoryId` TEXT, `categoryName` TEXT, `description` TEXT, `buyPrice` TEXT, `sellPrice` TEXT, `weight` TEXT, `unitId` TEXT, `unitName` TEXT, `supplierId` TEXT, `supplierName` TEXT, `itemType` TEXT, `isActive` INTEGER NOT NULL, `trackStock` INTEGER NOT NULL, `productUnitsJson` TEXT, `rawJson` TEXT, `lastSyncAt` INTEGER NOT NULL, PRIMARY KEY(`cacheKey`))");
            db.execSQL("INSERT OR REPLACE INTO `products_new` (`cacheKey`, `id`, `name`, `shopId`, `shopCode`, `apiBaseUrl`, `sku`, `barcode`, `price`, `stock`, `imageUrl`, `categoryId`, `categoryName`, `description`, `buyPrice`, `sellPrice`, `weight`, `unitId`, `unitName`, `supplierId`, `supplierName`, `itemType`, `isActive`, `trackStock`, `productUnitsJson`, `rawJson`, `lastSyncAt`) SELECT COALESCE(`apiBaseUrl`, '') || '|' || `shopId` || '|' || UPPER(COALESCE(`shopCode`, '')) || '|' || `id`, `id`, `name`, `shopId`, `shopCode`, `apiBaseUrl`, `sku`, `barcode`, `price`, `stock`, `imageUrl`, `categoryId`, `categoryName`, `description`, `buyPrice`, `sellPrice`, `weight`, `unitId`, `unitName`, `supplierId`, `supplierName`, `itemType`, `isActive`, `trackStock`, `productUnitsJson`, `rawJson`, `lastSyncAt` FROM `products`");
            db.execSQL("DROP TABLE `products`");
            db.execSQL("ALTER TABLE `products_new` RENAME TO `products`");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_barcode` ON `products` (`barcode`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_sku` ON `products` (`sku`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_categoryId` ON `products` (`categoryId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_itemType` ON `products` (`itemType`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_shopId` ON `products` (`shopId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_shopCode` ON `products` (`shopCode`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_isActive` ON `products` (`isActive`)");
        }

        private void rebuildCategories(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `categories_new` (`cacheKey` TEXT NOT NULL, `id` INTEGER NOT NULL, `shopId` INTEGER NOT NULL DEFAULT 0, `shopCode` TEXT, `apiBaseUrl` TEXT, `name` TEXT, `iconUrl` TEXT, `rawJson` TEXT, `lastSyncAt` INTEGER NOT NULL, PRIMARY KEY(`cacheKey`))");
            db.execSQL("INSERT OR REPLACE INTO `categories_new` (`cacheKey`, `id`, `shopId`, `shopCode`, `apiBaseUrl`, `name`, `iconUrl`, `rawJson`, `lastSyncAt`) SELECT COALESCE(`apiBaseUrl`, '') || '|' || `shopId` || '|' || UPPER(COALESCE(`shopCode`, '')) || '|' || CAST(`id` AS TEXT), `id`, `shopId`, `shopCode`, `apiBaseUrl`, `name`, `iconUrl`, `rawJson`, `lastSyncAt` FROM `categories`");
            db.execSQL("DROP TABLE `categories`");
            db.execSQL("ALTER TABLE `categories_new` RENAME TO `categories`");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_categories_name` ON `categories` (`name`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_categories_shopId` ON `categories` (`shopId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_categories_shopCode` ON `categories` (`shopCode`)");
        }

        private void rebuildUnits(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `units_new` (`cacheKey` TEXT NOT NULL, `id` INTEGER NOT NULL, `shopId` INTEGER NOT NULL DEFAULT 0, `shopCode` TEXT, `apiBaseUrl` TEXT, `name` TEXT, `rawJson` TEXT, `lastSyncAt` INTEGER NOT NULL, PRIMARY KEY(`cacheKey`))");
            db.execSQL("INSERT OR REPLACE INTO `units_new` (`cacheKey`, `id`, `shopId`, `shopCode`, `apiBaseUrl`, `name`, `rawJson`, `lastSyncAt`) SELECT COALESCE(`apiBaseUrl`, '') || '|' || `shopId` || '|' || UPPER(COALESCE(`shopCode`, '')) || '|' || CAST(`id` AS TEXT), `id`, `shopId`, `shopCode`, `apiBaseUrl`, `name`, `rawJson`, `lastSyncAt` FROM `units`");
            db.execSQL("DROP TABLE `units`");
            db.execSQL("ALTER TABLE `units_new` RENAME TO `units`");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_units_name` ON `units` (`name`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_units_shopId` ON `units` (`shopId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_units_shopCode` ON `units` (`shopCode`)");
        }

        private void rebuildCustomers(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `customers_new` (`cacheKey` TEXT NOT NULL, `id` INTEGER NOT NULL, `shopId` INTEGER NOT NULL DEFAULT 0, `shopCode` TEXT, `apiBaseUrl` TEXT, `name` TEXT, `cell` TEXT, `email` TEXT, `address` TEXT, `points` INTEGER NOT NULL, `rawJson` TEXT, `lastSyncAt` INTEGER NOT NULL, PRIMARY KEY(`cacheKey`))");
            db.execSQL("INSERT OR REPLACE INTO `customers_new` (`cacheKey`, `id`, `shopId`, `shopCode`, `apiBaseUrl`, `name`, `cell`, `email`, `address`, `points`, `rawJson`, `lastSyncAt`) SELECT COALESCE(`apiBaseUrl`, '') || '|' || `shopId` || '|' || UPPER(COALESCE(`shopCode`, '')) || '|' || CAST(`id` AS TEXT), `id`, `shopId`, `shopCode`, `apiBaseUrl`, `name`, `cell`, `email`, `address`, `points`, `rawJson`, `lastSyncAt` FROM `customers`");
            db.execSQL("DROP TABLE `customers`");
            db.execSQL("ALTER TABLE `customers_new` RENAME TO `customers`");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_customers_name` ON `customers` (`name`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_customers_cell` ON `customers` (`cell`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_customers_shopId` ON `customers` (`shopId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_customers_shopCode` ON `customers` (`shopCode`)");
        }

        private void rebuildSuppliers(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `suppliers_new` (`cacheKey` TEXT NOT NULL, `id` INTEGER NOT NULL, `shopId` INTEGER NOT NULL DEFAULT 0, `shopCode` TEXT, `apiBaseUrl` TEXT, `name` TEXT, `contactPerson` TEXT, `cell` TEXT, `email` TEXT, `address` TEXT, `rawJson` TEXT, `lastSyncAt` INTEGER NOT NULL, PRIMARY KEY(`cacheKey`))");
            db.execSQL("INSERT OR REPLACE INTO `suppliers_new` (`cacheKey`, `id`, `shopId`, `shopCode`, `apiBaseUrl`, `name`, `contactPerson`, `cell`, `email`, `address`, `rawJson`, `lastSyncAt`) SELECT COALESCE(`apiBaseUrl`, '') || '|' || `shopId` || '|' || UPPER(COALESCE(`shopCode`, '')) || '|' || CAST(`id` AS TEXT), `id`, `shopId`, `shopCode`, `apiBaseUrl`, `name`, `contactPerson`, `cell`, `email`, `address`, `rawJson`, `lastSyncAt` FROM `suppliers`");
            db.execSQL("DROP TABLE `suppliers`");
            db.execSQL("ALTER TABLE `suppliers_new` RENAME TO `suppliers`");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_suppliers_name` ON `suppliers` (`name`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_suppliers_cell` ON `suppliers` (`cell`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_suppliers_shopId` ON `suppliers` (`shopId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_suppliers_shopCode` ON `suppliers` (`shopCode`)");
        }

        private void rebuildWarehouses(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `warehouses_new` (`cacheKey` TEXT NOT NULL, `id` INTEGER NOT NULL, `shopId` INTEGER NOT NULL, `shopName` TEXT, `shopCode` TEXT, `apiBaseUrl` TEXT, `name` TEXT, `code` TEXT, `location` TEXT, `isActive` INTEGER NOT NULL, `isDefault` INTEGER NOT NULL, `createdAt` TEXT, `updatedAt` TEXT, `rawJson` TEXT, `lastSyncAt` INTEGER NOT NULL, PRIMARY KEY(`cacheKey`))");
            db.execSQL("INSERT OR REPLACE INTO `warehouses_new` (`cacheKey`, `id`, `shopId`, `shopName`, `shopCode`, `apiBaseUrl`, `name`, `code`, `location`, `isActive`, `isDefault`, `createdAt`, `updatedAt`, `rawJson`, `lastSyncAt`) SELECT COALESCE(`apiBaseUrl`, '') || '|' || `shopId` || '|' || UPPER(COALESCE(`shopCode`, '')) || '|' || CAST(`id` AS TEXT), `id`, `shopId`, `shopName`, `shopCode`, `apiBaseUrl`, `name`, `code`, `location`, `isActive`, `isDefault`, `createdAt`, `updatedAt`, `rawJson`, `lastSyncAt` FROM `warehouses`");
            db.execSQL("DROP TABLE `warehouses`");
            db.execSQL("ALTER TABLE `warehouses_new` RENAME TO `warehouses`");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_warehouses_shopId` ON `warehouses` (`shopId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_warehouses_shopCode` ON `warehouses` (`shopCode`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_warehouses_code` ON `warehouses` (`code`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_warehouses_isActive` ON `warehouses` (`isActive`)");
        }

        private void rebuildBankAccounts(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `bank_accounts_new` (`cacheKey` TEXT NOT NULL, `id` INTEGER NOT NULL, `shopId` INTEGER NOT NULL DEFAULT 0, `shopName` TEXT, `shopCode` TEXT, `apiBaseUrl` TEXT, `name` TEXT, `bankName` TEXT, `accountNumber` TEXT, `accountHolder` TEXT, `accountType` TEXT, `openingBalance` TEXT, `currentBalance` TEXT, `note` TEXT, `isActive` INTEGER NOT NULL, `rawJson` TEXT, `lastSyncAt` INTEGER NOT NULL, PRIMARY KEY(`cacheKey`))");
            db.execSQL("INSERT OR REPLACE INTO `bank_accounts_new` (`cacheKey`, `id`, `shopId`, `shopName`, `shopCode`, `apiBaseUrl`, `name`, `bankName`, `accountNumber`, `accountHolder`, `accountType`, `openingBalance`, `currentBalance`, `note`, `isActive`, `rawJson`, `lastSyncAt`) SELECT COALESCE(`apiBaseUrl`, '') || '|' || `shopId` || '|' || UPPER(COALESCE(`shopCode`, '')) || '|' || CAST(`id` AS TEXT), `id`, `shopId`, `shopName`, `shopCode`, `apiBaseUrl`, `name`, `bankName`, `accountNumber`, `accountHolder`, `accountType`, `openingBalance`, `currentBalance`, `note`, `isActive`, `rawJson`, `lastSyncAt` FROM `bank_accounts`");
            db.execSQL("DROP TABLE `bank_accounts`");
            db.execSQL("ALTER TABLE `bank_accounts_new` RENAME TO `bank_accounts`");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_bank_accounts_bankName` ON `bank_accounts` (`bankName`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_bank_accounts_shopId` ON `bank_accounts` (`shopId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_bank_accounts_shopCode` ON `bank_accounts` (`shopCode`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_bank_accounts_isActive` ON `bank_accounts` (`isActive`)");
        }

        private void rebuildPaymentMethods(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `payment_methods_new` (`cacheKey` TEXT NOT NULL, `id` INTEGER NOT NULL, `shopId` INTEGER NOT NULL DEFAULT 0, `shopCode` TEXT, `apiBaseUrl` TEXT, `name` TEXT, `code` TEXT, `paymentType` TEXT, `requiresBankAccount` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, `note` TEXT, `rawJson` TEXT, `lastSyncAt` INTEGER NOT NULL, PRIMARY KEY(`cacheKey`))");
            db.execSQL("INSERT OR REPLACE INTO `payment_methods_new` (`cacheKey`, `id`, `shopId`, `shopCode`, `apiBaseUrl`, `name`, `code`, `paymentType`, `requiresBankAccount`, `isActive`, `note`, `rawJson`, `lastSyncAt`) SELECT '' || '|' || 0 || '|' || '' || '|' || CAST(`id` AS TEXT), `id`, 0, '', '', `name`, `code`, `paymentType`, `requiresBankAccount`, `isActive`, `note`, '', `lastSyncAt` FROM `payment_methods`");
            db.execSQL("DROP TABLE `payment_methods`");
            db.execSQL("ALTER TABLE `payment_methods_new` RENAME TO `payment_methods`");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_payment_methods_code` ON `payment_methods` (`code`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_payment_methods_shopId` ON `payment_methods` (`shopId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_payment_methods_shopCode` ON `payment_methods` (`shopCode`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_payment_methods_isActive` ON `payment_methods` (`isActive`)");
        }

        private void rebuildShopProfile(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `shop_profile_new` (`cacheKey` TEXT NOT NULL, `localKey` INTEGER NOT NULL, `id` INTEGER NOT NULL, `shopId` TEXT NOT NULL, `shopCode` TEXT NOT NULL, `apiBaseUrl` TEXT NOT NULL, `name` TEXT NOT NULL, `storeName` TEXT NOT NULL, `address` TEXT NOT NULL, `phone` TEXT NOT NULL, `email` TEXT NOT NULL, `logoUrl` TEXT NOT NULL, `location` TEXT NOT NULL, `version` TEXT NOT NULL, `updatedAt` TEXT NOT NULL, `lastSyncAt` INTEGER NOT NULL, PRIMARY KEY(`cacheKey`))");
            db.execSQL("INSERT OR REPLACE INTO `shop_profile_new` (`cacheKey`, `localKey`, `id`, `shopId`, `shopCode`, `apiBaseUrl`, `name`, `storeName`, `address`, `phone`, `email`, `logoUrl`, `location`, `version`, `updatedAt`, `lastSyncAt`) SELECT '' || '|' || COALESCE(`shopId`, '') || '|' || '' || '|' || CAST(`id` AS TEXT), `localKey`, `id`, COALESCE(`shopId`, ''), '', '', COALESCE(`name`, ''), COALESCE(`storeName`, ''), COALESCE(`address`, ''), COALESCE(`phone`, ''), COALESCE(`email`, ''), COALESCE(`logoUrl`, ''), COALESCE(`location`, ''), COALESCE(`version`, ''), COALESCE(`updatedAt`, ''), `lastSyncAt` FROM `shop_profile`");
            db.execSQL("DROP TABLE `shop_profile`");
            db.execSQL("ALTER TABLE `shop_profile_new` RENAME TO `shop_profile`");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_shop_profile_id` ON `shop_profile` (`id`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_shop_profile_shopId` ON `shop_profile` (`shopId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_shop_profile_shopCode` ON `shop_profile` (`shopCode`)");
        }
    };

    static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `cached_orders` (`cacheKey` TEXT NOT NULL, `backendId` INTEGER NOT NULL, `shopId` INTEGER NOT NULL DEFAULT 0, `shopCode` TEXT, `apiBaseUrl` TEXT, `invoiceNumber` TEXT, `customerId` INTEGER, `customerName` TEXT, `subtotal` REAL NOT NULL, `discount` REAL NOT NULL, `tax` REAL NOT NULL, `total` REAL NOT NULL, `paymentMethod` TEXT, `paymentStatus` TEXT, `orderStatus` TEXT, `orderType` TEXT, `businessType` TEXT, `notes` TEXT, `isPaid` INTEGER NOT NULL, `itemsCount` INTEGER NOT NULL, `cashierName` TEXT, `createdAt` TEXT, `rawJson` TEXT, `lastSyncAt` INTEGER NOT NULL, PRIMARY KEY(`cacheKey`))");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_orders_backendId` ON `cached_orders` (`backendId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_orders_shopId` ON `cached_orders` (`shopId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_orders_shopCode` ON `cached_orders` (`shopCode`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_orders_invoiceNumber` ON `cached_orders` (`invoiceNumber`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_orders_createdAt` ON `cached_orders` (`createdAt`)");

            db.execSQL("CREATE TABLE IF NOT EXISTS `cached_order_items` (`cacheKey` TEXT NOT NULL, `backendId` INTEGER NOT NULL, `orderBackendId` INTEGER NOT NULL, `shopId` INTEGER NOT NULL DEFAULT 0, `shopCode` TEXT, `apiBaseUrl` TEXT, `productId` INTEGER NOT NULL, `itemType` TEXT, `name` TEXT, `sku` TEXT, `barcode` TEXT, `quantity` INTEGER NOT NULL, `price` REAL NOT NULL, `discount` REAL NOT NULL, `total` REAL NOT NULL, `note` TEXT, `rawJson` TEXT, `lastSyncAt` INTEGER NOT NULL, PRIMARY KEY(`cacheKey`))");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_order_items_orderBackendId` ON `cached_order_items` (`orderBackendId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_order_items_shopId` ON `cached_order_items` (`shopId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_order_items_shopCode` ON `cached_order_items` (`shopCode`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_order_items_productId` ON `cached_order_items` (`productId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_order_items_itemType` ON `cached_order_items` (`itemType`)");

            db.execSQL("CREATE TABLE IF NOT EXISTS `cached_payments` (`cacheKey` TEXT NOT NULL, `backendId` INTEGER NOT NULL, `shopId` INTEGER NOT NULL DEFAULT 0, `shopCode` TEXT, `apiBaseUrl` TEXT, `orderBackendId` INTEGER NOT NULL, `invoiceNumber` TEXT, `paymentMethodId` INTEGER NOT NULL, `paymentMethod` TEXT, `bankAccountId` INTEGER NOT NULL, `bankAccountName` TEXT, `amount` REAL NOT NULL, `createdAt` TEXT, `rawJson` TEXT, `lastSyncAt` INTEGER NOT NULL, PRIMARY KEY(`cacheKey`))");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_payments_backendId` ON `cached_payments` (`backendId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_payments_shopId` ON `cached_payments` (`shopId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_payments_shopCode` ON `cached_payments` (`shopCode`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_payments_orderBackendId` ON `cached_payments` (`orderBackendId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_payments_createdAt` ON `cached_payments` (`createdAt`)");

            db.execSQL("CREATE TABLE IF NOT EXISTS `cached_expenses` (`cacheKey` TEXT NOT NULL, `backendId` INTEGER NOT NULL, `shopId` INTEGER NOT NULL DEFAULT 0, `shopCode` TEXT, `apiBaseUrl` TEXT, `name` TEXT, `title` TEXT, `note` TEXT, `amount` TEXT, `category` TEXT, `date` TEXT, `time` TEXT, `createdAt` TEXT, `rawJson` TEXT, `lastSyncAt` INTEGER NOT NULL, PRIMARY KEY(`cacheKey`))");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_expenses_backendId` ON `cached_expenses` (`backendId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_expenses_shopId` ON `cached_expenses` (`shopId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_expenses_shopCode` ON `cached_expenses` (`shopCode`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_expenses_date` ON `cached_expenses` (`date`)");

            db.execSQL("CREATE TABLE IF NOT EXISTS `cached_bank_ledgers` (`cacheKey` TEXT NOT NULL, `backendId` INTEGER NOT NULL, `shopId` INTEGER NOT NULL DEFAULT 0, `shopCode` TEXT, `apiBaseUrl` TEXT, `shopName` TEXT, `bankAccount` INTEGER NOT NULL, `bankAccountName` TEXT, `transactionType` TEXT, `direction` TEXT, `amount` TEXT, `balanceBefore` TEXT, `balanceAfter` TEXT, `referenceOrder` INTEGER NOT NULL, `referenceOrderInvoice` TEXT, `referencePayment` INTEGER NOT NULL, `description` TEXT, `createdAt` TEXT, `createdBy` INTEGER NOT NULL, `rawJson` TEXT, `lastSyncAt` INTEGER NOT NULL, PRIMARY KEY(`cacheKey`))");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_bank_ledgers_backendId` ON `cached_bank_ledgers` (`backendId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_bank_ledgers_shopId` ON `cached_bank_ledgers` (`shopId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_bank_ledgers_shopCode` ON `cached_bank_ledgers` (`shopCode`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_bank_ledgers_bankAccount` ON `cached_bank_ledgers` (`bankAccount`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_bank_ledgers_createdAt` ON `cached_bank_ledgers` (`createdAt`)");
        }
    };

    static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `cached_stock_movements` (`cacheKey` TEXT NOT NULL, `backendId` INTEGER NOT NULL, `shopId` INTEGER NOT NULL DEFAULT 0, `shopCode` TEXT, `apiBaseUrl` TEXT, `productId` INTEGER NOT NULL, `productName` TEXT, `productCode` TEXT, `productSku` TEXT, `warehouseId` INTEGER NOT NULL, `warehouseName` TEXT, `movementType` TEXT, `quantity` INTEGER NOT NULL, `beforeStock` INTEGER NOT NULL, `afterStock` INTEGER NOT NULL, `referenceType` TEXT, `referenceId` INTEGER NOT NULL, `note` TEXT, `createdAt` TEXT, `createdBy` INTEGER NOT NULL, `rawJson` TEXT, `lastSyncAt` INTEGER NOT NULL, PRIMARY KEY(`cacheKey`))");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_stock_movements_backendId` ON `cached_stock_movements` (`backendId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_stock_movements_shopId` ON `cached_stock_movements` (`shopId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_stock_movements_shopCode` ON `cached_stock_movements` (`shopCode`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_stock_movements_productId` ON `cached_stock_movements` (`productId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_stock_movements_movementType` ON `cached_stock_movements` (`movementType`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_stock_movements_createdAt` ON `cached_stock_movements` (`createdAt`)");

            db.execSQL("CREATE TABLE IF NOT EXISTS `cached_stock_adjustments` (`cacheKey` TEXT NOT NULL, `backendId` INTEGER NOT NULL, `shopId` INTEGER NOT NULL DEFAULT 0, `shopCode` TEXT, `apiBaseUrl` TEXT, `productId` INTEGER NOT NULL, `productName` TEXT, `oldStock` INTEGER NOT NULL, `newStock` INTEGER NOT NULL, `reason` TEXT, `note` TEXT, `adjustedBy` INTEGER NOT NULL, `adjustedByName` TEXT, `adjustedAt` TEXT, `rawJson` TEXT, `lastSyncAt` INTEGER NOT NULL, PRIMARY KEY(`cacheKey`))");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_stock_adjustments_backendId` ON `cached_stock_adjustments` (`backendId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_stock_adjustments_shopId` ON `cached_stock_adjustments` (`shopId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_stock_adjustments_shopCode` ON `cached_stock_adjustments` (`shopCode`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_stock_adjustments_productId` ON `cached_stock_adjustments` (`productId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_stock_adjustments_adjustedAt` ON `cached_stock_adjustments` (`adjustedAt`)");

            db.execSQL("CREATE TABLE IF NOT EXISTS `cached_stock_transfers` (`cacheKey` TEXT NOT NULL, `backendId` INTEGER NOT NULL, `shopId` INTEGER NOT NULL DEFAULT 0, `shopCode` TEXT, `apiBaseUrl` TEXT, `shopName` TEXT, `referenceNo` TEXT, `sourceWarehouseId` INTEGER NOT NULL, `sourceWarehouseName` TEXT, `sourceWarehouseCode` TEXT, `destinationWarehouseId` INTEGER NOT NULL, `destinationWarehouseName` TEXT, `destinationWarehouseCode` TEXT, `status` TEXT, `note` TEXT, `createdBy` INTEGER NOT NULL, `createdByName` TEXT, `completedByName` TEXT, `cancelledByName` TEXT, `createdAt` TEXT, `updatedAt` TEXT, `completedAt` TEXT, `cancelledAt` TEXT, `rawJson` TEXT, `lastSyncAt` INTEGER NOT NULL, PRIMARY KEY(`cacheKey`))");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_stock_transfers_backendId` ON `cached_stock_transfers` (`backendId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_stock_transfers_shopId` ON `cached_stock_transfers` (`shopId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_stock_transfers_shopCode` ON `cached_stock_transfers` (`shopCode`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_stock_transfers_referenceNo` ON `cached_stock_transfers` (`referenceNo`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_stock_transfers_status` ON `cached_stock_transfers` (`status`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_stock_transfers_createdAt` ON `cached_stock_transfers` (`createdAt`)");

            db.execSQL("CREATE TABLE IF NOT EXISTS `cached_stock_transfer_items` (`cacheKey` TEXT NOT NULL, `backendId` INTEGER NOT NULL, `transferBackendId` INTEGER NOT NULL, `shopId` INTEGER NOT NULL DEFAULT 0, `shopCode` TEXT, `apiBaseUrl` TEXT, `productId` INTEGER NOT NULL, `productUnitId` INTEGER NOT NULL, `productName` TEXT, `productCode` TEXT, `productSku` TEXT, `quantity` REAL NOT NULL, `quantityInput` REAL NOT NULL, `quantityBase` REAL NOT NULL, `unitName` TEXT, `note` TEXT, `rawJson` TEXT, `lastSyncAt` INTEGER NOT NULL, PRIMARY KEY(`cacheKey`))");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_stock_transfer_items_backendId` ON `cached_stock_transfer_items` (`backendId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_stock_transfer_items_transferBackendId` ON `cached_stock_transfer_items` (`transferBackendId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_stock_transfer_items_shopId` ON `cached_stock_transfer_items` (`shopId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_stock_transfer_items_shopCode` ON `cached_stock_transfer_items` (`shopCode`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_stock_transfer_items_productId` ON `cached_stock_transfer_items` (`productId`)");

            db.execSQL("CREATE TABLE IF NOT EXISTS `cached_inventory_counts` (`cacheKey` TEXT NOT NULL, `backendId` INTEGER NOT NULL, `shopId` INTEGER NOT NULL DEFAULT 0, `shopCode` TEXT, `apiBaseUrl` TEXT, `warehouseId` INTEGER NOT NULL, `warehouseName` TEXT, `title` TEXT, `status` TEXT, `note` TEXT, `countedBy` INTEGER NOT NULL, `countedByUsername` TEXT, `countedByName` TEXT, `countedAt` TEXT, `createdAt` TEXT, `rawJson` TEXT, `lastSyncAt` INTEGER NOT NULL, PRIMARY KEY(`cacheKey`))");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_inventory_counts_backendId` ON `cached_inventory_counts` (`backendId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_inventory_counts_shopId` ON `cached_inventory_counts` (`shopId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_inventory_counts_shopCode` ON `cached_inventory_counts` (`shopCode`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_inventory_counts_warehouseId` ON `cached_inventory_counts` (`warehouseId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_inventory_counts_status` ON `cached_inventory_counts` (`status`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_inventory_counts_countedAt` ON `cached_inventory_counts` (`countedAt`)");

            db.execSQL("CREATE TABLE IF NOT EXISTS `cached_inventory_count_items` (`cacheKey` TEXT NOT NULL, `backendId` INTEGER NOT NULL, `inventoryCountBackendId` INTEGER NOT NULL, `shopId` INTEGER NOT NULL DEFAULT 0, `shopCode` TEXT, `apiBaseUrl` TEXT, `productId` INTEGER NOT NULL, `productName` TEXT, `systemStock` INTEGER NOT NULL, `countedStock` INTEGER NOT NULL, `difference` INTEGER NOT NULL, `costPrice` TEXT, `rawJson` TEXT, `lastSyncAt` INTEGER NOT NULL, PRIMARY KEY(`cacheKey`))");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_inventory_count_items_backendId` ON `cached_inventory_count_items` (`backendId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_inventory_count_items_inventoryCountBackendId` ON `cached_inventory_count_items` (`inventoryCountBackendId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_inventory_count_items_shopId` ON `cached_inventory_count_items` (`shopId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_inventory_count_items_shopCode` ON `cached_inventory_count_items` (`shopCode`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_inventory_count_items_productId` ON `cached_inventory_count_items` (`productId`)");
        }
    };

    static final Migration MIGRATION_10_11 = new Migration(10, 11) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `cached_purchases` (`cacheKey` TEXT NOT NULL, `backendId` INTEGER NOT NULL, `shopId` INTEGER NOT NULL DEFAULT 0, `shopCode` TEXT, `apiBaseUrl` TEXT, `invoiceNumber` TEXT, `supplierId` INTEGER NOT NULL, `supplierName` TEXT, `totalItems` INTEGER NOT NULL, `totalAmount` TEXT, `paidAmount` TEXT, `status` TEXT, `purchaseDate` TEXT, `createdAt` TEXT, `note` TEXT, `createdBy` INTEGER NOT NULL, `createdByName` TEXT, `rawJson` TEXT, `lastSyncAt` INTEGER NOT NULL, PRIMARY KEY(`cacheKey`))");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_purchases_backendId` ON `cached_purchases` (`backendId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_purchases_shopId` ON `cached_purchases` (`shopId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_purchases_shopCode` ON `cached_purchases` (`shopCode`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_purchases_invoiceNumber` ON `cached_purchases` (`invoiceNumber`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_purchases_supplierId` ON `cached_purchases` (`supplierId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_purchases_purchaseDate` ON `cached_purchases` (`purchaseDate`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_purchases_createdAt` ON `cached_purchases` (`createdAt`)");

            db.execSQL("CREATE TABLE IF NOT EXISTS `cached_purchase_items` (`cacheKey` TEXT NOT NULL, `backendId` INTEGER NOT NULL, `purchaseBackendId` INTEGER NOT NULL, `shopId` INTEGER NOT NULL DEFAULT 0, `shopCode` TEXT, `apiBaseUrl` TEXT, `productId` INTEGER NOT NULL, `productName` TEXT, `unitName` TEXT, `quantity` REAL NOT NULL, `price` TEXT, `cost` TEXT, `total` TEXT, `rawJson` TEXT, `lastSyncAt` INTEGER NOT NULL, PRIMARY KEY(`cacheKey`))");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_purchase_items_backendId` ON `cached_purchase_items` (`backendId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_purchase_items_purchaseBackendId` ON `cached_purchase_items` (`purchaseBackendId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_purchase_items_shopId` ON `cached_purchase_items` (`shopId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_purchase_items_shopCode` ON `cached_purchase_items` (`shopCode`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_purchase_items_productId` ON `cached_purchase_items` (`productId`)");

            db.execSQL("CREATE TABLE IF NOT EXISTS `cached_product_returns` (`cacheKey` TEXT NOT NULL, `backendId` INTEGER NOT NULL, `shopId` INTEGER NOT NULL DEFAULT 0, `shopCode` TEXT, `apiBaseUrl` TEXT, `returnNumber` TEXT, `orderId` INTEGER, `orderInvoice` TEXT, `customerId` INTEGER NOT NULL, `customerName` TEXT, `totalAmount` TEXT, `reason` TEXT, `note` TEXT, `status` TEXT, `returnedAt` TEXT, `createdAt` TEXT, `createdBy` INTEGER NOT NULL, `createdByName` TEXT, `rawJson` TEXT, `lastSyncAt` INTEGER NOT NULL, PRIMARY KEY(`cacheKey`))");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_product_returns_backendId` ON `cached_product_returns` (`backendId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_product_returns_shopId` ON `cached_product_returns` (`shopId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_product_returns_shopCode` ON `cached_product_returns` (`shopCode`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_product_returns_returnNumber` ON `cached_product_returns` (`returnNumber`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_product_returns_orderId` ON `cached_product_returns` (`orderId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_product_returns_customerId` ON `cached_product_returns` (`customerId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_product_returns_returnedAt` ON `cached_product_returns` (`returnedAt`)");

            db.execSQL("CREATE TABLE IF NOT EXISTS `cached_product_return_items` (`cacheKey` TEXT NOT NULL, `backendId` INTEGER NOT NULL, `returnBackendId` INTEGER NOT NULL, `shopId` INTEGER NOT NULL DEFAULT 0, `shopCode` TEXT, `apiBaseUrl` TEXT, `productId` INTEGER NOT NULL, `productName` TEXT, `productCode` TEXT, `productSku` TEXT, `itemType` TEXT, `quantity` REAL NOT NULL, `price` TEXT, `total` TEXT, `reason` TEXT, `rawJson` TEXT, `lastSyncAt` INTEGER NOT NULL, PRIMARY KEY(`cacheKey`))");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_product_return_items_backendId` ON `cached_product_return_items` (`backendId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_product_return_items_returnBackendId` ON `cached_product_return_items` (`returnBackendId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_product_return_items_shopId` ON `cached_product_return_items` (`shopId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_product_return_items_shopCode` ON `cached_product_return_items` (`shopCode`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_product_return_items_productId` ON `cached_product_return_items` (`productId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_product_return_items_itemType` ON `cached_product_return_items` (`itemType`)");
        }
    };

    static final Migration MIGRATION_11_12 = new Migration(11, 12) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `cached_reports` (`cacheKey` TEXT NOT NULL, `shopId` INTEGER NOT NULL DEFAULT 0, `shopCode` TEXT, `apiBaseUrl` TEXT, `reportType` TEXT, `queryHash` TEXT, `filterJson` TEXT, `responseJson` TEXT, `title` TEXT, `dateFrom` TEXT, `dateTo` TEXT, `lastSyncAt` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`cacheKey`))");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_reports_shopId` ON `cached_reports` (`shopId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_reports_shopCode` ON `cached_reports` (`shopCode`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_reports_reportType` ON `cached_reports` (`reportType`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_reports_queryHash` ON `cached_reports` (`queryHash`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_reports_dateFrom` ON `cached_reports` (`dateFrom`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_reports_dateTo` ON `cached_reports` (`dateTo`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_reports_lastSyncAt` ON `cached_reports` (`lastSyncAt`)");
        }
    };

    static final Migration MIGRATION_12_13 = new Migration(12, 13) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `cached_users` (`cacheKey` TEXT NOT NULL, `backendId` INTEGER NOT NULL, `shopId` INTEGER NOT NULL DEFAULT 0, `shopCode` TEXT, `apiBaseUrl` TEXT, `username` TEXT, `fullName` TEXT, `email` TEXT, `role` TEXT, `shopName` TEXT, `isActive` INTEGER NOT NULL, `lastLogin` TEXT, `syncedAt` TEXT, `permissionsJson` TEXT, `menuPermissionsJson` TEXT, `featuresJson` TEXT, `isSuperuser` INTEGER NOT NULL, `isPlatformAdmin` INTEGER NOT NULL, `isShopOwner` INTEGER NOT NULL, `isShopManager` INTEGER NOT NULL, `isShopCashier` INTEGER NOT NULL, `rawJson` TEXT, `lastSyncAt` INTEGER NOT NULL, PRIMARY KEY(`cacheKey`))");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_users_backendId` ON `cached_users` (`backendId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_users_shopId` ON `cached_users` (`shopId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_users_shopCode` ON `cached_users` (`shopCode`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_users_username` ON `cached_users` (`username`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_users_role` ON `cached_users` (`role`)");

            db.execSQL("CREATE TABLE IF NOT EXISTS `cached_roles` (`cacheKey` TEXT NOT NULL, `backendId` INTEGER NOT NULL, `shopId` INTEGER NOT NULL DEFAULT 0, `shopCode` TEXT, `apiBaseUrl` TEXT, `role` TEXT, `name` TEXT, `description` TEXT, `rawJson` TEXT, `lastSyncAt` INTEGER NOT NULL, PRIMARY KEY(`cacheKey`))");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_roles_backendId` ON `cached_roles` (`backendId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_roles_shopId` ON `cached_roles` (`shopId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_roles_shopCode` ON `cached_roles` (`shopCode`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_roles_role` ON `cached_roles` (`role`)");

            db.execSQL("CREATE TABLE IF NOT EXISTS `cached_menu_permissions` (`cacheKey` TEXT NOT NULL, `shopId` INTEGER NOT NULL DEFAULT 0, `shopCode` TEXT, `apiBaseUrl` TEXT, `userId` INTEGER NOT NULL, `username` TEXT, `role` TEXT, `menuKey` TEXT, `canView` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, `rawJson` TEXT, `lastSyncAt` INTEGER NOT NULL, PRIMARY KEY(`cacheKey`))");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_menu_permissions_shopId` ON `cached_menu_permissions` (`shopId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_menu_permissions_shopCode` ON `cached_menu_permissions` (`shopCode`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_menu_permissions_userId` ON `cached_menu_permissions` (`userId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_menu_permissions_username` ON `cached_menu_permissions` (`username`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_menu_permissions_role` ON `cached_menu_permissions` (`role`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_menu_permissions_menuKey` ON `cached_menu_permissions` (`menuKey`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_menu_permissions_canView` ON `cached_menu_permissions` (`canView`)");
        }
    };

    static final Migration MIGRATION_13_14 = new Migration(13, 14) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `cached_users` ADD COLUMN `businessType` TEXT DEFAULT 'retail'");
            db.execSQL("ALTER TABLE `cached_users` ADD COLUMN `plan` TEXT DEFAULT 'basic'");
            db.execSQL("ALTER TABLE `cached_users` ADD COLUMN `effectiveModulesJson` TEXT DEFAULT '[]'");
        }
    };

    public abstract ProductDao productDao();
    public abstract CategoryDao categoryDao();
    public abstract UnitDao unitDao();
    public abstract WarehouseStockDao warehouseStockDao();
    public abstract CustomerDao customerDao();
    public abstract PaymentMethodDao paymentMethodDao();
    public abstract BankAccountDao bankAccountDao();
    public abstract WarehouseDao warehouseDao();
    public abstract SupplierDao supplierDao();
    public abstract PendingOrderDao pendingOrderDao();
    public abstract ShopProfileDao shopProfileDao();
    public abstract CachedOrderDao cachedOrderDao();
    public abstract CachedPaymentDao cachedPaymentDao();
    public abstract CachedExpenseDao cachedExpenseDao();
    public abstract CachedBankLedgerDao cachedBankLedgerDao();
    public abstract CachedStockMovementDao cachedStockMovementDao();
    public abstract CachedStockAdjustmentDao cachedStockAdjustmentDao();
    public abstract CachedStockTransferDao cachedStockTransferDao();
    public abstract CachedInventoryCountDao cachedInventoryCountDao();
    public abstract CachedPurchaseDao cachedPurchaseDao();
    public abstract CachedProductReturnDao cachedProductReturnDao();
    public abstract CachedReportDao cachedReportDao();
    public abstract CachedUserDao cachedUserDao();
    public abstract CachedMenuPermissionDao cachedMenuPermissionDao();

    @NonNull
    public static ValoraLocalDatabase getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (ValoraLocalDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    ValoraLocalDatabase.class,
                                    "valora_local_master.db"
                            )
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
                            .build();
                }
            }
        }
        return instance;
    }
}
