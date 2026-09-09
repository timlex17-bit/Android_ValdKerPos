# AUDIT LENGKAP FITUR, UI & MODUL — Android_ValdKerPos (Valora POS)

- Tanggal: 2026-09-10
- Commit: `2aa289b` (branch `master`)
- Sifat: **read-only**. Tidak ada kode yang diubah.
- Tujuan dokumen: menjadi acuan tunggal untuk **menyelaraskan Android dengan backend**.
  Setiap kontrak API di bawah ini diambil langsung dari kode, bukan dari dokumentasi.

**Cara pakai dokumen ini:** bagian 4 (kontrak endpoint) dan bagian 5 (kontrak checkout)
adalah inti untuk tim backend. Bagian 11 adalah **checklist pertanyaan** yang harus
dijawab tim backend sebelum penyelarasan dimulai.

---

## 0. Angka dasar

| Kategori | Jumlah |
|---|---|
| File Java | 281 (61.359 baris) |
| Activity (terdaftar di manifest) | 20 + 1 `Application` |
| Fragment / DialogFragment | 36 |
| RecyclerView/Array adapter | 46 |
| Repository / API class | 29 repository + 10 class `network/` |
| Model (POJO) | 40 |
| Room entity | 33 (31 di DB utama + 2 di DB draft) |
| Room DAO | 25 |
| Layout XML | 103 (`layout/`) + 4 (`layout-land/`) + 9 (`layout-sw600dp/`) |
| Drawable | 105 |
| Endpoint API berbeda | 41 path + 4 sub-aksi |
| Modul di `ModuleRegistry` | 29 kunci |
| Tile di dashboard | 28 |
| Locale | 3 (`values` 935 string, `values-in` 921, `values-b+tet` 921) |

---

## 1. Arsitektur & alur navigasi

```
SplashActivity  (launcher, satu-satunya komponen exported)
   └─> LoginActivity            POST api/auth/login/
         └─> HomeDashboardActivity   ← hub utama
               ├─ Grid 28 tile modul (difilter effective_modules + menu_permissions + role)
               ├─ BottomNav: Home | Reports | AI | Settings
               ├─ Fragment in-place (18 modul)  → openFragmentSafe()
               └─ Activity terpisah (10 modul)  → startActivity()
                     ├─ MainActivity            (POS)
                     ├─ PendingOrdersActivity   (order offline)
                     ├─ BankAccountActivity
                     ├─ PurchaseReturnListActivity
                     └─ 6 × modul workshop
```

**Pola arsitektur:** tanpa DI, tanpa ViewModel, tanpa LiveData/Flow. Semua akses jaringan
lewat static/instance repository yang memanggil Volley langsung dan mengembalikan hasil
via interface callback. State layar disimpan sebagai field di Activity/Fragment.

**POS bercabang berdasarkan `business_type`** (`MainActivity.java:1061-1089`):

| business_type | Fragment POS | Keranjang | Checkout |
|---|---|---|---|
| `workshop` | `workshop/WorkshopPOSFragment.java` (4.035 baris) | internal fragment | `WorkshopPOSFragment.handleCheckoutBank()` |
| `retail` | `ui/retail/RetailPOSFragment.java` (1.512 baris) | internal fragment | `MainActivity.submitRetailOrder()` |
| `restaurant` **dan semua nilai lain** | `ui/ProductsFragment.java` + overlay `ui/CartFragment.java` | `cart/CartManager` (SharedPreferences) | `CartFragment.submitCheckout()` |

> Ini bukan hanya soal tampilan — **tiga jalur ini mengirim payload yang berbeda ke
> endpoint `POST api/orders/` yang sama.** Lihat bagian 5.

---

## 2. Autentikasi & multi-tenant (WAJIB diselaraskan)

### 2.1 Skema autentikasi
Seluruh 45 titik pemanggilan memakai header:
```
Authorization: Token <token>
Accept: application/json
```
Format `Token ` (DRF `TokenAuthentication`), **bukan** `Bearer`. Konsisten di seluruh app.

### 2.2 Scoping toko — ADA TIGA MEKANISME BERBEDA

| Mekanisme | Dipakai oleh | Bukti |
|---|---|---|
| **Implisit dari token** (tidak ada penanda toko dikirim) | ±39 dari 45 pemanggilan: orders, products, customers, expenses, reports, dst. | semua repository di `repositories/` |
| **Query `?shop=<id>`** | HANYA endpoint shift | `repositories/ShiftRepository.java:79-82,94,156,230,310` |
| **Header `X-Shop-Code`** | HANYA 2 tempat: modul workshop generik + owner chat | `network/WorkshopModuleApi.java:96`; `ui/ownerchat/OwnerChatRepository.java:77` |

**Yang lebih rawan:** `MechanicApi` dan `ServicePackageApi` adalah endpoint workshop juga
tetapi **tidak** mengirim `X-Shop-Code` (`network/MechanicApi.java:137-140`,
`network/ServicePackageApi.java:137-140`), sedangkan 4 modul workshop lainnya mengirim.
Jadi bahkan di dalam satu domain (workshop), scoping-nya tidak seragam.

**Pertanyaan ke backend:** mana yang otoritatif? Kalau token sudah mengikat shop,
`?shop=` dan `X-Shop-Code` harus dihapus. Kalau tidak, keduanya harus dipasang di
**semua** endpoint.

### 2.3 Kontrak `POST api/auth/login/`

Request (`LoginActivity.java:215`):
```json
{ "username": "...", "password": "...", "shop_code": "..." }
```

Response yang **dibaca** app (`LoginActivity.java:300-460`):
```json
{
  "token": "...",
  "shop_id": 0,
  "user": {
    "id": 0, "username": "...", "full_name": "...", "role": "cashier",
    "email": "...", "is_active": true, "last_login": "...",
    "shop_id": 0, "shop_code": "...", "shop_name": "...",
    "shop_business_type": "retail", "plan": "basic",
    "is_superuser": false, "is_platform_admin": false,
    "is_shop_owner": false, "is_shop_manager": false, "is_shop_cashier": false,
    "permissions": ["..."],
    "role_id": 0, "role_name": "...",
    "effective_modules": ["pos","orders", "..."]
  },
  "shop": {
    "id": 0, "code": "...", "name": "...", "address": "...",
    "logo": "...", "logo_url": "...",
    "business_type": "retail", "plan": "basic",
    "effective_modules": ["..."],
    "features": { "...": true }
  }
}
```

Aturan fallback yang sudah ada di klien (`LoginActivity.java:341-364`):
`business_type` dicari di `shop` lalu `user.shop_business_type`, default `"retail"`.
`plan` dicari di `shop` lalu `user.plan`, default `"basic"`.
`effective_modules` dicari di `shop` lalu `user`.

### 2.4 Kontrak `features` (objek boolean)

Dipakai di `SessionManager.java:360-408`. Semua punya default di klien:

| Key | Default | Catatan |
|---|---|---|
| `use_grid_pos_layout` | `true` untuk restaurant, `false` lainnya | |
| `show_product_images_in_pos` | `true` untuk restaurant, `false` lainnya | |
| `enable_barcode_scan` | `true` | |
| `enable_dine_in` | `true` untuk restaurant, dipaksa `false` lainnya | |
| `enable_takeaway` | `true` untuk restaurant, dipaksa `false` lainnya | |
| `enable_table_number` | `true` untuk restaurant, dipaksa `false` lainnya | |
| `enable_delivery` | `true` untuk restaurant, dipaksa `false` lainnya | |
| `enable_split_payment` | `false` | **Dibaca (`MainActivity.java:479`, `CartFragment.java:257`) tapi tidak pernah dipakai untuk apa pun.** Split payment tidak ada UI-nya. |

### 2.5 Kontrak `menu_permissions` dan role

`menu_permissions` adalah objek `{ "<module_key>": true|false|"1" }`
(`SessionManager.java:595-608`). Kalau objek ini **kosong**, klien jatuh ke tabel role
hardcoded (`SessionManager.java:613-690`):

| Role (string) | Modul yang diizinkan klien |
|---|---|
| `owner` / `admin` / `superuser` (atau flag `is_shop_owner`/`is_platform_admin`/`is_superuser`) | semua yang ada di `isSupportedMenuKey()` |
| `manager` | pos, orders, customers, reports, stock_movements, inventory_counts, expenses, suppliers, purchases, products, categories, units, product_returns, stock_adjustments, offline_orders, vehicles, mechanics, work_orders, service_history, service_packages, bookings |
| `cashier` / `kasir` | pos, orders, customers |
| `inventory_staff` | products, categories, units, warehouses, warehouse_stocks, stock_transfers, stock_movements, stock_adjustments, inventory_counts, offline_orders |
| `finance` | reports, expenses, bank_accounts, bank_ledgers, orders |

**Celah yang perlu dikonfirmasi:** daftar `manager` **tidak** memuat `warehouses`,
`warehouse_stocks`, `stock_transfers`, `bank_accounts`, `bank_ledgers`,
`purchase_returns`, maupun `settings`. Jadi shop manager tanpa `menu_permissions` dari
server kehilangan 7 modul.

### 2.6 `effective_modules`

Gerbang final adalah `canAccessModule()` = `isEffectiveModuleAllowed()` **AND**
`canAccessMenu()` (`SessionManager.java:296-300`). Kalau `effective_modules` **kosong**,
klien hanya mengizinkan 4 modul (`SessionManager.java:816-821`):
`dashboard`, `pos`, `orders`, `settings`.

Artinya: **kalau backend tidak mengirim `effective_modules`, aplikasi akan tampak
"kehilangan hampir semua menu"** — ini bukan bug klien, ini fallback yang disengaja.

### 2.7 29 kunci modul (`ModuleRegistry.java`)

```
dashboard, pos, orders, customers, products, categories, units, suppliers,
purchases, expenses, reports, settings, offline_orders, inventory_counts,
stock_adjustments, product_returns, purchase_returns, bank_accounts,
bank_ledgers, warehouses, warehouse_stocks, stock_transfers, stock_movements,
vehicles, mechanics, work_orders, service_history, service_packages, bookings
```
Nilai `effective_modules` dan key `menu_permissions` harus memakai **string persis ini**
(dinormalkan hanya dengan `trim().toLowerCase()`, `SessionManager.java:811-814`).

---

## 3. Inventaris 28 modul

Legenda: **C**reate, **R**ead, **U**pdate, **D**elete. "Cache" = ada Room offline read.

| # | Modul (`module_key`) | Layar | Endpoint | CRUD | Cache | Catatan penting |
|---|---|---|---|---|---|---|
| 1 | `pos` | `MainActivity` + 3 fragment POS | `api/orders/`, `api/products/`, `api/categories/` | C order | ✔ produk/kategori | 3 varian per business_type; payload berbeda (bagian 5) |
| 2 | `orders` | `ui/orders/OrdersFragment` + `OrderDetailRepository` | `GET api/orders/`, `GET api/orders/{id}/` | R | ✔ `cached_orders` | Filter tanggal & pencarian **client-side** (`OrdersFragment.java:298-306`); tidak ada query param dikirim |
| 3 | `customers` | `ui/customers/CustomersFragment` + `CustomerFormDialog` | `api/customers/` (+ `{id}/`) | C R U D | ✔ `customers` | |
| 4 | `products` | `ui/ProductsManageFragment` + `ui/ProductFormDialog` | `api/products/` (+ `{id}/`) | C R U D | ✔ `products` | **multipart** (field `image`); `stock` hanya dikirim saat CREATE, tidak saat UPDATE (`ProductRepository.java:213` vs `:335-354`) |
| 5 | `categories` | `ui/categories/CategoriesFragment` | `api/categories/` (+ `{id}/`) | C R U D | ✔ `categories` | **multipart** (field `icon`) |
| 6 | `units` | `ui/units/UnitsFragment` | `api/units/` (+ `{id}/`) | C R U D | ✔ `units` | payload hanya `{name}` |
| 7 | `suppliers` | `ui/suppliers/SuppliersFragment` | `api/suppliers/` (+ `{id}/`) | C R U D | ✔ `suppliers` | |
| 8 | `purchases` | `ui/purchases/PurchasesFragment` + `PurchaseAddDialog` | `api/purchases/` | C R | ✔ `cached_purchases` | **tidak ada update/delete** |
| 9 | `purchase_returns` | `ui/purchasereturns/PurchaseReturnListActivity` (32 baris) | `api/purchase-returns/` | R saja | ✘ | **BUG: mewarisi guard `!session.isWorkshop()`** → modul ini mati untuk toko retail/restaurant. Lihat bagian 9 |
| 10 | `product_returns` | `ui/productreturns/ProductReturnsFragment`, `ProductReturnAddDialog`, `ProductReturnDetailActivity` | `api/productreturns/` (+ `{id}/`) | C R D | ✔ `cached_product_returns` | field pakai sufiks `_id` (`product_id`, `customer_id`) — beda dari modul lain |
| 11 | `expenses` | `ui/expenses/ExpensesFragment` | `api/expenses/` (+ `{id}/`) | C R U D | ✔ `cached_expenses` | |
| 12 | `bank_accounts` | `ui/BankAccountActivity` | `api/bank-accounts/` (+ `{id}/`) | C R U D | ✔ `bank_accounts` | update pakai **PATCH** |
| 13 | `bank_ledgers` | `ui/bankledgers/BankLedgersFragment` | `bank-ledgers/` (+ `{id}/`) | R | ✔ `cached_bank_ledgers` | **satu-satunya endpoint tanpa prefix `api/` di kode** — `ApiConfig` yang menambahkannya (`ApiConfig.java:45-47`) |
| 14 | `warehouses` | `ui/warehouses/WarehousesFragment` | `api/warehouses/` (+ `{id}/`) | C R U D | ✔ `warehouses` | update pakai **PUT** |
| 15 | `warehouse_stocks` | `ui/warehousestocks/WarehouseStocksFragment` | `api/warehouse-stocks/` (+ `{id}/`) | C R U D | ✔ `warehouse_stocks` | |
| 16 | `stock_transfers` | `ui/stocktransfers/StockTransfersFragment` | `api/stock-transfers/` (+ `{id}/`, `{id}/complete/`, `{id}/cancel/`) | C R U D + 2 aksi | ✔ `cached_stock_transfers` | status: `DRAFT`/`COMPLETED`/`CANCELLED` |
| 17 | `stock_movements` | `ui/stockmovements/StockMovementsFragment` + `StockMovementDetailActivity` | `api/stockmovements/` | R saja | ✔ `cached_stock_movements` | audit log |
| 18 | `stock_adjustments` | `ui/stockadjustments/StockAdjustmentsFragment`, `StockAdjustmentFormDialog`, `StockAdjustmentDetailActivity` | `api/stockadjustments/` (+ `{id}/`) | C R U D | ✔ `cached_stock_adjustments` | update pakai **PATCH** |
| 19 | `inventory_counts` | `ui/inventorycount/InventoryCountsFragment`, `InventoryCountFormDialog`, `InventoryCountDetailActivity` | `api/inventorycounts/` (+ `{id}/`, `{id}/finalize/`) | C R U D + finalize | ✔ `cached_inventory_counts` | status `DRAFT` → `COMPLETED` |
| 20 | `reports` | `ui/reports/ReportsFragment` | 8 endpoint `api/reports/*` | R | ✔ `cached_reports` | tombol Export **disembunyikan & disabled** (`ReportsFragment.java:229-230`) → `api/reports/sales/export/` tidak pernah dipanggil |
| 21 | `settings` | `ui/settings/SettingsFragment` | `api/shop/me/` (PATCH multipart) | R U | ✔ `shop_profile` | juga mengatur base URL, bahasa, dan printer (lokal) |
| 22 | `offline_orders` | `ui/offlineorders/PendingOrdersActivity` | — (Room + retry ke `api/orders/`) | R + retry | Room `pending_orders` | filter: ALL/PENDING/FAILED/NEEDS_REVIEW/SYNCED |
| 23 | `vehicles` | `ui/workshop/VehiclesActivity` → `VehicleListActivity` | `api/workshop/vehicles/` (+ `{id}/`) | C R U D | ✘ | pakai `WorkshopModuleApi` (kirim `X-Shop-Code`) |
| 24 | `mechanics` | `ui/workshop/MechanicsActivity` → `MechanicListActivity` | `api/workshop/mechanics/` (+ `{id}/`) | C R U D | ✘ | pakai `MechanicApi` (**tidak** kirim `X-Shop-Code`) |
| 25 | `work_orders` | `ui/workshop/WorkOrdersActivity` → `WorkOrderListActivity` | `api/workshop/work-orders/` (+ `{id}/`) | C R U D | ✘ | |
| 26 | `service_history` | `ui/workshop/ServiceHistoryActivity` | `api/workshop/service-history/` (+ `{id}/`) | C R U D | ✘ | |
| 27 | `service_packages` | `ui/workshop/ServicePackagesActivity` → `ServicePackageActivity` | `api/workshop/service-packages/` (+ `{id}/`) | C R U D | ✘ | pakai `ServicePackageApi` (**tidak** kirim `X-Shop-Code`) |
| 28 | `bookings` | `ui/workshop/BookingsActivity` → `BookingListActivity` | `api/workshop/bookings/` (+ `{id}/`) | C R U D | ✘ | |

**Modul tanpa tile dashboard tapi tetap ada:**
- Shift (`api/shifts/*`) — diakses lewat gerbang otomatis di `MainActivity` dan menu user popup.
- Owner Chat / AI (`api/owner/chat/`) — diakses lewat BottomNav item "AI".
- Printer (lokal, Bluetooth) — diakses dari Settings.

**Ketimpangan cache yang perlu diputuskan:** 6 modul workshop + purchase_returns +
shifts **tidak punya cache Room**, jadi **online-only**. Sementara 21 modul lain
punya pola "Room-first lalu refresh". Untuk bengkel yang koneksinya buruk, ini
berarti seluruh modul intinya tidak bisa dibuka offline.

---

## 4. Kontrak endpoint lengkap

Semua request memakai `Authorization: Token <token>` + `Accept: application/json`.

### 4.1 Auth & shop

| Method | Path | Request | Response dibaca |
|---|---|---|---|
| POST | `api/auth/login/` | `username`, `password`, `shop_code` | lihat 2.3 |
| GET | `api/auth/me/` | — | `user{ id, username, full_name, email, role, role_id, role_name, is_active, last_login, permissions[], shop{} }` (`AuthCacheRepository.java`) |
| GET | `api/shop/` | — | `id`, `name`, `all_category_icon_url` (`CategoryRepository`) |
| GET | `api/shop/me/` | — | `id`, `shop_id`, `name`, `store_name`, `address`, `phone`, `email`, `logo`, `logo_url`, `location`, `version`, `updated_at` |
| PATCH | `api/shop/me/` | **multipart**: `name`, `address`, `phone`, `email`, file `logo` | sama seperti GET |

### 4.2 Shift

| Method | Path | Request | Response dibaca |
|---|---|---|---|
| GET | `api/shifts/current/?shop={id}` | — | `shift{}` / `data{}` / `result{}` / `open` — klien menerima 4 bentuk pembungkus (`ShiftRepository.java`) |
| GET | `api/shifts/?shop={id}` | — | **array atau `{results:[]}`**; `HomeDashboardActivity.java:544` memakai `JsonArrayRequest` sehingga **hanya menerima array polos** |
| POST | `api/shifts/open/?shop={id}` | `opening_cash`, `note`, `shop` | objek shift |
| POST | `api/shifts/close/?shop={id}` | `closing_cash`, `note`, `shop` | objek shift |

Field `Shift` yang dibaca (`models/Shift.java`): `id`, `status`, `opened_at`,
`closed_at`, `opening_cash`, `closing_cash`, `expected_cash`, `cash_difference`,
`total_sales`, `total_expenses`, `total_refunds`, `note`. Status yang dikenali: `OPEN`.

> **Risiko konkret:** kalau backend memaginasi `api/shifts/`, kartu "Opening Cash" di
> dashboard rusak (parse error), sementara `ShiftRepository` tetap jalan. Dua konsumen,
> dua asumsi bentuk respons.

### 4.3 Produk & katalog

| Method | Path | Request | Catatan |
|---|---|---|---|
| GET | `api/products/` | — | `{results:[]}` atau array |
| GET | `api/products/?page_size=1000` | — | dipakai `ProductOptionApi`; **satu-satunya tempat yang mengikuti `next`** (`ProductOptionApi.java:87`) |
| GET | `api/products/?track_stock=true` | — | dipakai form stock adjustment |
| GET | `api/products/scan/?code=<urlencoded>` | — | response: `id`/`product_id`, `name`/`product_name`, `sku`, `code`, `barcode`, `barcode_type`, `item_type`, `matched_unit`, `product_units[]` |
| GET | `api/products/{id}/` | — | `id`, `code`, `name`, `sku` |
| POST | `api/products/` | **multipart**: `name`, `sku`, `code`, `category_id`, `description`, `stock`, `buy_price`, `sell_price`, `weight`, `unit_id`, `supplier_id`, `item_type`, `is_active`, `track_stock`, file `image` | |
| PUT | `api/products/{id}/` | sama **tanpa `stock`** | |
| DELETE | `api/products/{id}/` | — | |
| GET/POST/PUT/DELETE | `api/categories/`, `api/categories/{id}/` | **multipart**: `name`, file `icon` | |
| GET/POST/PUT/DELETE | `api/units/`, `api/units/{id}/` | `name` | |

`product_units[]` yang dibaca (`models/ProductUnit.java`): `id`, `product`/`product_id`,
`unit`/`unit_id`, `unit_name`, `name`, `conversion_qty`/`conversion_factor`,
`barcode`, `is_base`/`is_base_unit`, `base_unit`, `is_default_sale_unit`,
`is_default_purchase_unit`, `product_unit`/`product_unit_id`.

> **Celah:** `product_units` hanya **dibaca**. Tidak ada endpoint untuk membuat atau
> mengubah unit produk dari Android — padahal harga jual per unit dipakai di POS.
> Multi-unit hanya bisa dikelola dari web.

### 4.4 Checkout config

| Method | Path | Response dibaca |
|---|---|---|
| GET | `api/payment-methods/` | `id`, `name`, `code`, `payment_type`, `requires_bank_account`, `is_active`, `note` |
| GET | `api/bank-accounts/` | `id`, `name`, `bank_name`, `account_number`, `account_holder`, `account_type`, `current_balance`, `is_active`, `note` |

`code` yang diperlakukan khusus oleh klien: **`"CASH"`** (memicu field cash received /
kembalian, `NativeCheckoutDialogFragment.java:419,524,549`). Nilai `code` lain
diperlakukan generik.

### 4.5 Order

| Method | Path | Catatan |
|---|---|---|
| POST | `api/orders/` | **3 bentuk payload berbeda** — bagian 5 |
| GET | `api/orders/` | list; response dibaca: `id`, `invoice_number`, `customer`, `created_at`, `subtotal`, `discount`, `tax`, `total`, `is_paid`, `payment_method`, `notes`, `items[{product, quantity, price, weight_unit}]` |
| GET | `api/orders/{id}/` | detail: `id`, `invoice_number`, `customer`, `items[{name, ...}]` |

Response `POST api/orders/` yang dibaca klien: `invoice_number` (fallback
`invoice_id`, lalu `"INV-" + timestamp` lokal — `CartFragment.java:698-701`),
`id`, `client_order_id`, `device_time`.

### 4.6 Inventory & gudang

| Method | Path | Request |
|---|---|---|
| GET/POST | `api/inventorycounts/` | `title`, `note`, `items[{product, counted_stock}]` |
| PUT/DELETE | `api/inventorycounts/{id}/` | sama |
| POST | `api/inventorycounts/{id}/finalize/` | body kosong → status jadi `COMPLETED` |
| GET/POST | `api/stockadjustments/` | `product`, `old_stock`, `new_stock`, `reason`, `note` |
| PATCH/DELETE | `api/stockadjustments/{id}/` | sama |
| GET | `api/stockmovements/` | read-only; dibaca: `movement_type`, `quantity_delta`, `before_stock`, `after_stock`, `ref_model`, `ref_id`, `product_*`, `created_by`, `created_at`, `note` |
| GET/POST | `api/warehouses/` | `name`, `code`, `location`, `is_active`, `is_default` |
| PUT/DELETE | `api/warehouses/{id}/` | sama |
| GET/POST | `api/warehouse-stocks/` | `warehouse`, `product`, `product_unit`, `quantity`, `minimum_stock` |
| PUT/DELETE | `api/warehouse-stocks/{id}/` | sama |
| GET/POST | `api/stock-transfers/` | `from_warehouse`, `to_warehouse`, `note`, `items[{product, product_unit, quantity_input, note}]` |
| PUT/DELETE | `api/stock-transfers/{id}/` | sama |
| POST | `api/stock-transfers/{id}/complete/` | body kosong |
| POST | `api/stock-transfers/{id}/cancel/` | body kosong |

### 4.7 Pembelian & pengembalian

| Method | Path | Request |
|---|---|---|
| GET/POST | `api/purchases/` | `supplier` (atau `null`), `invoice_id`, `purchase_date`, `note`, `items[{product, quantity, cost_price, expired_date\|null}]` |
| GET/POST | `api/productreturns/` | `order`, `customer_id`, `note`, `returned_at`, `items[{product_id, quantity, unit_price}]` |
| DELETE | `api/productreturns/{id}/` | — |
| GET | `api/purchase-returns/` | read-only, tanpa form (`formFields()` kosong) |

> **Inkonsistensi penamaan:** purchases memakai `product`/`supplier`, product returns
> memakai `product_id`/`customer_id`, orders memakai `product`. Perlu satu konvensi.

### 4.8 Master lain

| Method | Path | Request |
|---|---|---|
| GET/POST/PUT/DELETE | `api/customers/` (+ `{id}/`) | `name`, `cell`, `email`, `address`; response juga membaca `points` |
| GET/POST/PUT/DELETE | `api/suppliers/` (+ `{id}/`) | `name`, `contact_person`, `cell`, `email`, `address` |
| GET/POST/PUT/DELETE | `api/expenses/` (+ `{id}/`) | `name`, `amount`, `date`, `time`, `note` |
| GET/POST/PATCH/DELETE | `api/bank-accounts/` (+ `{id}/`) | `name`, `bank_name`, `account_number`, `account_holder`, `account_type`, `opening_balance`, `is_active`, `note` |
| GET | `bank-ledgers/` (+ `{id}/`) | dibaca: `transaction_type`, `direction`, `amount`, `balance_before`, `balance_after`, `bank_account`, `bank_account_name`, `reference_order`, `reference_order_invoice`, `reference_payment`, `description`, `created_by`, `created_at` |

### 4.9 Laporan

Semua `GET`, semua menerima query `start_date`, `end_date`, `page_size=50`
(`ReportsFragment.java:299-319`), plus:
- `payment_method` (opsional, semua tipe usaha)
- `search` dan `category_id` (**hanya** kalau `business_type == "retail"`)
- `item_type` = `MENU|SERVICE|SPAREPART` (**hanya** workshop)
- `order_type` = `DINE_IN|TAKEAWAY|DELIVERY` (**hanya** restaurant)

| reportType | Path |
|---|---|
| `daily` | `api/reports/dashboard-summary/` |
| `sales` | `api/reports/sales/` |
| `items` | `api/reports/sales-items/` |
| `payments` | `api/reports/payments/` |
| `expenses` | `api/reports/expenses/` |
| `stock` | `api/reports/stock/` |
| `low_stock` | `api/reports/low-stock/` |
| `shifts` | `api/reports/shifts/` |
| (tidak dipakai) | `api/reports/sales/export/` — tombol Export disabled |

Bentuk respons yang diharapkan (`ReportRepository.ReportResponse`):
```json
{ "summary": {...}, "breakdown": {...}, "results": [...],
  "rows": [...], "data": [...], "filters": {...},
  "pagination": {...}, "shop": {...} }
```

**Key `summary` yang dibaca** (klien mencoba beberapa alias, ambil yang pertama ada):

| Kartu | Alias yang dicoba |
|---|---|
| Total Revenue | `total_revenue`, `revenue`, `total_sales` |
| Net Sales | `net_sales`, `net`, `net_revenue` |
| Gross Profit (retail) | `gross_profit`, `profit` |
| Margin % (retail) | `margin`, `margin_percent` |
| Product Sold (retail) | `product_sold`, `products_sold`, `qty`, `quantity` |
| Service/Sparepart/Menu Revenue (workshop) | `service_revenue`\|`service`, `sparepart_revenue`\|`sparepart`, `menu_revenue`\|`menu` (dicari di `summary` lalu `breakdown`) |
| Dine In/Takeaway/Delivery Revenue (restaurant) | `dine_in_revenue`\|`dine_in`, `takeaway_revenue`\|`takeaway`, `delivery_revenue`\|`delivery` |

**Key baris (`results[]`) yang dibaca** (`ReportResultAdapter.java:57-125`):
`invoice`\|`invoice_number`\|`order_number`\|`number`\|`shift_number`;
`item_name`\|`product_name`\|`name`\|`product`\|`cashier_name`\|`payment_method`;
`date`\|`created_at`\|`opened_at`; `qty`\|`quantity`;
`subtotal`\|`line_total`\|`total`; `sku`\|`barcode`\|`product_barcode`;
`payment_method`\|`method`\|`method_code`; `profit`\|`gross_profit`;
`count`\|`orders_count`\|`transaction_count`; `opening_cash`\|`open_cash`;
`closing_cash`\|`close_cash`; `cashier`\|`cashier_name`\|`username`;
`order_type`\|`type`; `item_type`\|`type`.

Dashboard (`api/reports/dashboard-summary/?start_date=today&end_date=today`) hanya
membaca 3 angka dari `summary` (`HomeDashboardActivity.java:657-671`):
`total_revenue|revenue|total_sales|sales`, `total_expense|expense|expenses`,
`net_income|net_sales|net|profit`.

> **Terlalu banyak alias.** Daftar alias di atas adalah gejala bahwa klien tidak yakin
> nama field backend. Ini yang paling layak dibekukan jadi satu kontrak tetap.

### 4.10 Modul workshop

Semua lewat `WorkshopSimpleListActivity` (GET/POST/PATCH/DELETE) kecuali mechanics &
service packages yang punya API sendiri.

| Endpoint | Field form (= request body) |
|---|---|
| `api/workshop/vehicles/` | `customer` (opsional), `plate_number`*, `vehicle_type`* (`CAR`\|`MOTORCYCLE`, default `CAR`), `brand`, `model`, `year`, `color` |
| `api/workshop/mechanics/` | `name`*, `phone`, `specialty`, `is_active` |
| `api/workshop/work-orders/` | `customer`*, `vehicle`*, `mechanic`, `complaint`*, `diagnosis`, `status` (default `OPEN`), `total_amount` |
| `api/workshop/service-history/` | `work_order`*, `vehicle`*, `customer`*, `notes`*, `service_date`*, `total_amount` |
| `api/workshop/service-packages/` | `name`*, `description`, `price`, `duration_minutes`, `is_active` |
| `api/workshop/bookings/` | `customer`*, `vehicle`, `booking_date`*, `booking_time`*, `status` (default `PENDING`), `notes` |

Field respons yang dibaca untuk baris daftar (`WorkshopSimpleListActivity.java`):
`id`, `name`, `customer`, `customer_name`, `plate_number`, `vehicle_plate_number`,
`mechanic_name`, `complaint`, `notes`, `status`, `booking_date`, `booking_no`,
`service_date`, `invoice_number`, `reference_no`, `created_at`.

`ServicePackageResponse` juga menerima alias: `is_active`\|`active`,
`duration_minutes`\|`estimated_minutes`.

### 4.11 Owner Chat (AI)

| Method | Path | Header tambahan | Request | Response dibaca |
|---|---|---|---|---|
| POST | `api/owner/chat/` | `X-Shop-Code: <shop_code>` | `message`, `conversation_id` (opsional) | `reply`\|`answer`\|`reply_text`\|`message`, `conversation_id`, `links[{title, url}]` |

Satu-satunya endpoint yang **tidak** memakai Volley — memakai OkHttp dengan timeout
15s connect / 30s read / 15s write (`OwnerChatRepository.java:36-40`).

---

## 5. Kontrak checkout — PERBEDAAN 3 JALUR (prioritas tertinggi)

Ketiganya `POST api/orders/`. Tabel berikut membandingkan payload apa adanya.

### 5.1 Field tingkat atas

| Field | Retail (`MainActivity.java:2073-2157`) | Restaurant (`CartFragment.java:573-672`) | Workshop (`WorkshopPOSFragment.java:3393-3540`) |
|---|---|---|---|
| `device_time` | ISO-8601 | ISO-8601 | ISO-8601 |
| `customer` | id, **hanya jika > 0** (field dihilangkan kalau walk-in) | id **atau `null`** (selalu ada) | id **atau `null`** |
| `customer_id` | — | — | ✔ duplikat dari `customer` |
| **`payment_method`** | **integer `payment_method_id`** ⚠️ | **string code** (`"CASH"`) ⚠️ | **string code** ⚠️ |
| `bank_account` | id, hanya jika > 0 | — | — |
| `reference_number` | ✔ (tingkat atas) | — (hanya di `payments[]`) | — (hanya di `payments[]`) |
| `note` | ✔ (tingkat atas) | ✔ | — |
| `notes` | — | `"Checkout from Android"` (konstan) | string gabungan panjang (lihat 5.4) |
| `subtotal` | **tidak dikirim** ⚠️ | string `"%.2f"` | **number** |
| `discount` | **tidak dikirim** | string `"0.00"` (dipaku) | **number** `0.0` (dipaku) |
| `tax` | **tidak dikirim** | string `"0.00"` (dipaku) | **number** `0.0` (dipaku) |
| `total` | **tidak dikirim** ⚠️ | string `"%.2f"` | **number** |
| `is_paid` | — | `true` | — |
| `default_order_type` | — | `DINE_IN`\|`TAKE_OUT`\|`DELIVERY`\|`GENERAL` | — |
| `order_type` | — | (per item) | — |
| `table_number` | hanya jika tidak kosong | selalu (bisa `""`) | — (masuk `notes`) |
| `delivery_address` | hanya jika tidak kosong | selalu (bisa `""`) | — (masuk `notes`) |
| `delivery_fee` | number, hanya jika > 0 | string `"%.2f"` | — (masuk `notes`) |
| `cash_received` | number, hanya jika > 0 | — (hanya cetak struk) | — (masuk `notes`) |
| `change_amount` | number, hanya jika > 0 | — | — (masuk `notes`) |
| `vehicle_type` | — | — | `CAR`\|`MOTORCYCLE` |
| `vehicle` + `vehicle_id` | — | — | ✔ **dua-duanya** |
| `mechanic` + `mechanic_id` | — | — | ✔ **dua-duanya** |
| `booking` + `booking_id` | — | — | ✔ **dua-duanya** |
| `work_order` + `work_order_id` | — | — | ✔ **dua-duanya** |
| `client_order_id` | ✔ | ✔ | ✔ |

### 5.2 Field per item (`items[]`)

| Field | Retail | Restaurant | Workshop |
|---|---|---|---|
| `product` | int | int | int, atau `null` untuk paket servis |
| `service_package_id` | — | — | ✔ bila item adalah paket servis |
| `quantity` | int | int (min 1) | int |
| `price` | **number** | **string `"%.2f"`** | **number** |
| `order_type` | — | ✔ per item | — |
| `item_type` | — | — | `product`\|`menu`\|`service`\|`sparepart` |
| `name` | — | — | ✔ |
| `subtotal` | — | — | ✔ number |

### 5.3 Field pembayaran (`payments[]`) — selalu **tepat satu** elemen

| Field | Retail | Restaurant | Workshop |
|---|---|---|---|
| `payment_method_id` | int | int | int atau `null` |
| `method_code` | — | — | ✔ string code |
| `bank_account_id` | int, hanya jika > 0 | int **atau `null`** (selalu ada) | int, hanya jika > 0 |
| `amount` | **number** | **string `"%.2f"`** | **number** |
| `reference_number` | ✔ hanya jika tidak kosong | ✔ selalu (bisa `""`) | ✔ selalu |
| `note` | ✔ hanya jika tidak kosong | ✔ selalu | ✔ selalu |

### 5.4 Masalah yang harus diputuskan bersama backend

1. **`payment_method` bermakna tiga hal berbeda.** Retail mengirim **id integer**,
   restaurant dan workshop mengirim **kode string**. Kalau backend hanya menerima satu,
   dua dari tiga jalur ini sedang bekerja karena kebetulan (atau memang salah satunya
   sudah rusak dan belum ketahuan).
2. **Retail tidak mengirim `subtotal`/`total`/`discount`/`tax` sama sekali** — backend
   harus menghitungnya sendiri. Restaurant dan workshop mengirim, jadi ada dua sumber
   kebenaran total nilai transaksi.
3. **Tipe data uang berbeda**: restaurant mengirim **string** `"12.50"`, retail dan
   workshop mengirim **number** `12.5`. Serializer backend harus menerima keduanya, atau
   satu jalur harus diseragamkan.
4. **Workshop mengirim alias ganda** (`vehicle`+`vehicle_id`, `mechanic`+`mechanic_id`,
   `booking`+`booking_id`, `work_order`+`work_order_id`, `customer`+`customer_id`) karena
   klien tidak tahu mana yang diterima backend. Perlu dipilih satu; sisanya dihapus.
5. **Workshop menaruh data terstruktur di `notes`** sebagai teks bebas: plat nomor,
   mekanik, biaya kirim, uang tunai, kembalian, nomor referensi, semuanya digabung dengan
   pemisah `" | "` (`WorkshopPOSFragment.java:3444-3487`). Data ini tidak bisa dilaporkan
   atau difilter oleh backend. Kalau laporan bengkel butuh mekanik atau plat, field-nya
   harus dinaikkan menjadi field JSON sendiri.
6. **`discount` dan `tax` dipaku nol** di ketiga jalur (`0.00` string / `0.0` number).
   Tidak ada UI diskon maupun pajak di Android sama sekali.
7. **Split payment tidak ada.** Array `payments` sudah ada tapi selalu satu elemen.
   Flag `enable_split_payment` dibaca lalu diabaikan.
8. **`order_type` vs `default_order_type`.** Restaurant mengirim `default_order_type` di
   tingkat atas **dan** `order_type` per item. Nilai `TAKE_OUT` dipakai di payload
   (`CartManager.java:33`) sementara filter laporan mengirim `TAKEAWAY`
   (`ReportsFragment.java:194`) — **dua ejaan berbeda untuk konsep yang sama.**

### 5.5 Tambahan khusus sinkronisasi offline

Saat mengirim ulang order yang tersimpan offline, klien menambahkan
(`OfflineOrderRepository.java:617-636`):
```json
{ "client_order_id": "android-shop<ID>-<epochMillis>-<uuid>",
  "is_offline_sync": true,
  "offline_created_at": "<ISO-8601 waktu perangkat saat transaksi>" }
```
Backend **harus** memperlakukan `client_order_id` sebagai kunci idempotensi. Ini belum
terverifikasi dari sisi Android — lihat bagian 11.

---

## 6. Kontrak offline & sinkronisasi

### 6.1 Tabel Room (`valora_local_master.db`, versi 14, 31 entity)

**Master (bisa dibaca offline):** `products`, `categories`, `units`, `customers`,
`suppliers`, `warehouses`, `warehouse_stocks`, `payment_methods`, `bank_accounts`,
`shop_profile`

**Cache riwayat:** `cached_orders`, `cached_order_items`, `cached_payments`,
`cached_expenses`, `cached_bank_ledgers`, `cached_stock_movements`,
`cached_stock_adjustments`, `cached_stock_transfers`, `cached_stock_transfer_items`,
`cached_inventory_counts`, `cached_inventory_count_items`, `cached_purchases`,
`cached_purchase_items`, `cached_product_returns`, `cached_product_return_items`,
`cached_reports`

**Auth:** `cached_users`, `cached_menu_permissions`, `cached_roles`
(`cached_roles` **tidak punya DAO** → tabel mati)

**Antrian transaksi:** `pending_orders`, `pending_order_items`

**Database kedua** `valdker_pos_drafts.db` (versi 1, tanpa migrasi): `pos_drafts`,
`pos_draft_items`

### 6.2 Kolom `pending_orders` (yang perlu backend tahu)

`localOrderId` (PK), `rawPayloadJson`, **`clientOrderId`** (indexed),
`businessType`, `shopId`, `shopCode`, `shopName`, `apiBaseUrl`,
`createdByUserId`, `createdByUsername`, `customerId`, `paymentMethodId`,
`bankAccountId`, `subtotal`, `discount`, `tax`, `total`, `paidAmount`,
`changeAmount`, `orderType`, `note`, `createdAt`, `updatedAt`,
`syncStatus`, `syncAttemptCount`, `lastSyncError`

`syncStatus`: `PENDING_SYNC` → `SYNCING` → `SYNCED` | `FAILED` | `NEEDS_REVIEW`
Batas percobaan: 5 (`OfflineOrderRepository.java:47`), lalu `NEEDS_REVIEW`.

### 6.3 Aturan sinkronisasi yang harus diketahui backend

1. Order offline **hanya** disinkronkan ketika `shopId` **dan** `shopCode` device saat
   ini cocok dengan yang tersimpan di order (`OfflineOrderRepository.java:516-520`).
   Kalau kasir login ke toko lain, order dikunci sebagai `NEEDS_REVIEW`.
2. Sinkronisasi **berurutan satu per satu**, bukan batch. Tidak ada endpoint bulk.
3. `shouldSaveOffline()` menganggap order layak disimpan offline **hanya** jika
   `statusCode == 0` (tidak ada respons HTTP sama sekali)
   (`OfflineOrderRepository.java:152-158`). Artinya error HTTP 5xx **tidak** masuk antrian
   offline — langsung `FAILED`.
4. Klien mengirim `device_time` di setiap order dan backend tampaknya memvalidasinya —
   ada penanganan khusus `ErrorHandler.isDeviceTimeValidationError()`
   (`CartFragment.java:732,755`). **Format dan toleransi validasi ini perlu
   didokumentasikan** karena kalau salah, seluruh transaksi ditolak dan tidak
   disimpan offline.

---

## 7. Inventaris UI

### 7.1 Distribusi 103 layout

| Kelompok | Jumlah | Contoh |
|---|---|---|
| `activity_*` | 15 | `activity_home_dashboard`, `activity_main`, `activity_workshop_module_list` |
| `fragment_*` | 25 | `fragment_retail_pos`, `fragment_workshop_pos`, `fragment_cart` |
| `dialog_*` | 24 | `dialog_native_checkout`, `dialog_shift_open`, `dialog_product_form` |
| `item_*` | 36 | `item_dashboard_tile`, `item_workshop_cart_line` |
| `row_*`, `view_*`, `popup_*` | 3 | `row_inventory_count_item_draft`, `view_app_popup_toast`, `popup_user_menu` |

### 7.2 Dukungan layar besar & landscape — sangat tidak merata

Hanya **4** layout punya varian `layout-land/` dan **9** punya `layout-sw600dp/`.

| Layar | phone | land | sw600dp |
|---|---|---|---|
| `activity_main` (POS) | ✔ | ✔ | ✔ |
| `fragment_retail_pos` | ✔ | ✔ | ✔ |
| `fragment_workshop_pos` | ✔ | ✔ | ✔ |
| `item_product` | ✔ | ✔ | ✔ | (**layout ini yatim, tidak dipakai**) |
| `activity_home_dashboard` | ✔ | — | ✔ |
| `activity_login` | ✔ | — | ✔ |
| `item_category_chip`, `item_order`, `item_product_manage` | ✔ | — | ✔ |
| **25 fragment modul lainnya** | ✔ | — | — |

Artinya: di tablet, POS dan dashboard sudah dioptimalkan, tetapi 25 layar modul
(produk, pembelian, laporan, gudang, dll.) memakai layout ponsel yang direntang.

### 7.3 Tema & mode gelap

- `values/themes.xml:4` — `Theme.ValdKer` parent `Theme.Material3.Light.NoActionBar`
- `values-night/themes.xml:3` — parent-nya juga **`...Light...`** (salah)
- `ValdkerApp.java:15` — `AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_NO)`

**Konsekuensi:** mode gelap dimatikan secara global, sehingga seluruh folder
`values-night/` adalah kode mati dan bug parent tema itu tidak terlihat. Kalau nanti mode
gelap diaktifkan, tema malam akan tampil terang.

### 7.4 Bahasa

- Default aplikasi: **`tet` (Tetum)** (`ValdkerApp.java:18`), disimpan di
  SharedPreferences `app_settings` / key `app_language`.
- 3 locale: `values` (935 string), `values-in` (921), `values-b+tet` (921).
- **14 string belum diterjemahkan** → `./gradlew lint` **GAGAL** (error, bukan warning).
  Semua string menu baru: `menu_purchase_returns`, `menu_vehicles`, `menu_mechanics`,
  `menu_work_orders`, `menu_service_history`, `menu_service_packages`, `menu_bookings`
  beserta `*_desc`-nya (`values/strings.xml:97-110`).
- **427 `HardcodedText`** di layout XML (laporan lint).
- **±60 string Bahasa Indonesia hardcoded di `.java`**, contoh:
  `WorkshopPOSFragment.java:1458` "Shift belum dibuka...", `:3026` "Session belum siap",
  `:3214` "Order repository belum siap", `HomeDashboardActivity.java:932`
  "Anda tidak punya akses ke menu ini."
- **String Inggris hardcoded** di `CartFragment.java:512,522,531,549,555,649`.
- **`menu/bottom_nav_menu.xml`** memakai judul hardcoded: "Home", "Reports", "AI",
  "Settings" — tidak ikut berganti bahasa.

### 7.5 Aset yatim (tidak dipakai sama sekali)

| File | Keterangan |
|---|---|
| `layout/fragment_car_wash_pos.xml` | Jejak business type **car wash** yang direncanakan tapi tidak pernah dibuat. Isinya sebagian besar sudah dikomentari. |
| `layout/fragment_categories.xml` | `CategoriesFragment` sebenarnya memakai `fragment_manage_categories` |
| `layout/dialog_inventory_count.xml` | yang dipakai `dialog_inventory_count_form` |
| `layout/item_product.xml` (+ varian land & sw600dp) | adapter memakai `item_product_grid` / `item_product_list` |
| `layout/item_category.xml` | adapter memakai `item_category_chip` |
| `layout/fragment_placeholder.xml` | sisa scaffolding |
| tabel Room `cached_roles` | tidak punya DAO |
| `api/reports/sales/export/` | tombol Export disabled |
| 414 `UnusedResources` (lint) | drawable/string tidak terpakai |

---

## 8. Modul yang bergantung pada perilaku backend tertentu

| Yang diasumsikan klien | Bukti | Kalau backend berbeda |
|---|---|---|
| List boleh berupa **array polos ATAU `{results:[]}`** | 32 pemakaian `optJSONArray("results")` + fallback array di 96 tempat | Aman — klien fleksibel |
| **`api/shifts/` harus array polos** untuk dashboard | `HomeDashboardActivity.java:544-559` `JsonArrayRequest` | Kartu Opening Cash rusak kalau dipaginasi |
| **Pagination tidak diikuti** kecuali `api/products/` | hanya `ProductOptionApi.java:87` | Data terpotong diam-diam di semua daftar lain. `api/products/` di tempat lain memakai `?page_size=1000` sebagai jalan pintas |
| Filter tanggal & pencarian dilakukan **di klien** | `OrdersFragment.java:298-306` | Boros bandwidth; toko dengan ribuan order akan lambat |
| `payment_methods[].code == "CASH"` menandai metode tunai | `NativeCheckoutDialogFragment.java:419,524,549` | Kalau kode tunai bukan tepat `"CASH"`, field uang diterima & kembalian tidak muncul |
| `vehicle_type` bernilai `CAR`\|`MOTORCYCLE` | `VehicleListActivity.java:41`; `WorkshopPOSFragment.java:3296-3299` menangkap error backend `vehicle_type` | |
| Status yang dikenali klien | `OPEN` (shift, work order), `DRAFT`/`COMPLETED` (inventory count, stock transfer), `CANCELLED` (stock transfer), `PENDING` (booking) | Nilai lain ditampilkan mentah |
| `device_time` divalidasi backend | `ErrorHandler.isDeviceTimeValidationError()` | Perlu dokumentasi format & toleransi |

---

## 9. Bug & masalah fungsional yang ditemukan di audit ini

| No | Temuan | File:baris | Dampak |
|---|---|---|---|
| F-1 | **Purchase Returns tidak bisa dibuka oleh toko non-workshop.** `PurchaseReturnListActivity` mewarisi `WorkshopSimpleListActivity` yang memblokir `!session.isWorkshop()` | `ui/purchasereturns/PurchaseReturnListActivity.java:8`; guard di `ui/workshop/WorkshopSimpleListActivity.java:100` | Tile muncul di dashboard retail/restaurant, tapi begitu ditekan langsung tertutup dengan toast "permission denied". Modul efektif mati untuk mayoritas pelanggan |
| F-2 | Purchase Returns juga menampilkan subtitle **"Workshop module"** dan tidak punya form (read-only) | subtitle default `WorkshopSimpleListActivity.java:90-93`; `formFields()` tidak dioverride | Salah label + tidak bisa membuat retur pembelian dari Android |
| F-3 | **`TAKE_OUT` vs `TAKEAWAY`** — payload order memakai `TAKE_OUT`, filter laporan memakai `TAKEAWAY` | `cart/CartManager.java:33` vs `ui/reports/ReportsFragment.java:194` | Filter laporan "Takeaway" tidak akan cocok dengan data yang dikirim POS |
| F-4 | `enable_split_payment` dibaca lalu tidak dipakai | `MainActivity.java:479,490`; `ui/CartFragment.java:257,263` | Mengaktifkan flag di backend tidak menghasilkan apa pun |
| F-5 | Tombol Export laporan disembunyikan dan disabled | `ui/reports/ReportsFragment.java:229-230` | Endpoint `api/reports/sales/export/` tidak terpakai |
| F-6 | `product_units` read-only — tidak ada endpoint tulis | `repositories/MasterDataRepository.java:1020-1028` (hanya serialisasi ke cache) | Multi-unit produk tidak bisa dikelola dari Android |
| F-7 | Tabel Room `cached_roles` tanpa DAO | `local/CachedRoleEntity.java`; `local/ValoraLocalDatabase.java:43` | Tabel mati, ikut di setiap migrasi |
| F-8 | 6 activity hanya subclass kosong | `ui/workshop/{Bookings,Mechanics,ServicePackages,Vehicles,WorkOrders}Activity.java` (4 baris masing-masing) | Duplikasi tanpa manfaat; manifest merujuk nama jamak, implementasi ada di nama tunggal |
| F-9 | `X-Shop-Code` hanya dikirim 2 dari 45 pemanggilan, dan tidak konsisten bahkan di dalam domain workshop | `network/WorkshopModuleApi.java:96` vs `network/MechanicApi.java:137-140` | Ambigu bagi backend multi-tenant |
| F-10 | Modul workshop & purchase returns **tanpa cache Room** | tidak ada DAO terkait | Bengkel tidak bisa membuka modul intinya saat offline, padahal modul retail bisa |
| F-11 | `values-night/themes.xml` mewarisi tema **Light** | `app/src/main/res/values-night/themes.xml:3` | Tersembunyi karena `MODE_NIGHT_NO` dipaksa; akan muncul kalau dark mode diaktifkan |
| F-12 | Judul BottomNav hardcoded | `res/menu/bottom_nav_menu.xml` | 4 label tidak ikut ganti bahasa |

---

## 10. Fitur yang belum selesai

1. **POS restaurant tanpa fragment sendiri** — jatuh ke `ProductsFragment` + overlay
   `CartFragment` (`MainActivity.java:1081-1088`). Ini jalur dengan guard paling lemah,
   diskon/pajak dipaku nol, dan penyimpanan offline paling rapuh.
2. **Business type "car wash"** — hanya menyisakan `layout/fragment_car_wash_pos.xml`
   yang yatim dan sebagian besar sudah dikomentari. `SessionManager.java:838` hanya
   menerima `workshop`, `restaurant`, `retail`.
3. **Enforcement plan SaaS** — `isBasic()`/`isPro()`/`isEnterprise()`
   (`SessionManager.java:284-294`) **tidak punya satu pun pemanggil**. Gating nyata
   memakai `effective_modules` dari server.
4. **Split payment** — struktur array sudah siap, UI belum ada.
5. **Diskon & pajak** — belum ada UI di jalur mana pun.
6. **Export laporan** — tombol ada, dimatikan.
7. **Pengelolaan unit produk (multi-unit)** — read-only.
8. **Retur pembelian** — read-only dan salah gerbang akses (F-1).
9. **Sinkronisasi latar belakang** — `androidx.work:work-runtime` sudah menjadi
   dependensi tapi **nol pemakaian**; sync hanya jalan saat ada checkout baru atau
   ditekan manual.
10. **Server-side filtering & pagination** — belum dipakai kecuali laporan dan
    `api/products/`.

---

## 11. Checklist untuk tim backend

Urut dari yang paling menghambat.

### Harus dijawab sebelum penyelarasan
1. **`POST api/orders/`: `payment_method` menerima apa — id integer, kode string, atau
   keduanya?** Saat ini retail mengirim id, restaurant & workshop mengirim kode.
2. **Apakah `client_order_id` benar-benar didedup di server?** Volley meretry POST
   otomatis sekali (`OrderRepository.java:50-52`), jadi tanpa dedup ada risiko order
   ganda. Ini pertanyaan paling penting di seluruh audit.
3. **Format dan toleransi validasi `device_time`.** Kalau ditolak, order gagal dan
   **tidak** masuk antrian offline.
4. **Nilai `subtotal`/`total` mana yang otoritatif?** Retail tidak mengirimnya sama
   sekali; restaurant mengirim string; workshop mengirim number.
5. **Scoping toko: token, `?shop=`, atau `X-Shop-Code`?** Pilih satu untuk semua endpoint.
6. **Apakah `TAKE_OUT` atau `TAKEAWAY`** yang benar untuk `order_type`? (F-3)

### Perlu dibekukan jadi kontrak tetap
7. **Nama field ringkasan laporan.** Hilangkan kebutuhan alias di bagian 4.9 — pilih satu
   nama per angka, untuk semua 8 tipe laporan dan `dashboard-summary`.
8. **Nama field relasi.** Satu konvensi: `product` atau `product_id`, `customer` atau
   `customer_id`. Sekarang bercampur antar modul (bagian 4.7).
9. **Alias ganda workshop.** Pilih `vehicle` atau `vehicle_id` (idem mechanic, booking,
   work_order) lalu klien membuang sisanya.
10. **Bentuk respons list.** Kalau memaginasi, kirim `{count, next, previous, results}`
    secara **konsisten** — dan beri tahu, karena klien belum mengikuti `next` di 40 dari
    41 endpoint. Khususnya `api/shifts/` yang di dashboard **harus** array polos hari ini.
11. **Enum status lengkap** untuk shift, inventory count, stock transfer, work order,
    booking. Klien saat ini hanya mengenali `OPEN`, `DRAFT`, `COMPLETED`, `CANCELLED`,
    `PENDING`.
12. **Nilai `payment_methods[].code`** untuk metode tunai harus tepat `"CASH"`, atau
    klien perlu diubah.

### Perlu ditambahkan / dipastikan di backend
13. **`effective_modules` wajib dikirim.** Kalau kosong, Android hanya menampilkan 4
    modul (dashboard, pos, orders, settings) — bukan bug, tapi fallback.
14. **`menu_permissions` untuk role `manager`.** Kalau tidak dikirim, manager kehilangan
    7 modul karena tabel role hardcoded di klien (bagian 2.5).
15. **Endpoint tulis untuk `product_units`** kalau multi-unit harus bisa dikelola dari
    Android (F-6).
16. **Endpoint CRUD retur pembelian** kalau modul ini memang dimaksudkan interaktif (F-2).
17. **Field terstruktur untuk data workshop** yang sekarang dijejalkan ke `notes` sebagai
    teks: mekanik, plat nomor, biaya kirim, uang tunai, kembalian (bagian 5.4 #5).
18. **Dukungan bulk sync** kalau antrian offline bisa panjang — sekarang dikirim satu per
    satu secara berurutan.

---

## 12. Ringkasan prioritas penyelarasan

| Prioritas | Pekerjaan | Alasan |
|---|---|---|
| **P0** | Seragamkan payload `POST api/orders/` (satu bentuk untuk tiga jalur) | Uang. Tiga kontrak berbeda untuk satu endpoint adalah sumber selisih dan bug senyap |
| **P0** | Konfirmasi dedup `client_order_id` di server | Risiko transaksi ganda nyata karena retry otomatis Volley |
| **P0** | Dokumentasikan validasi `device_time` | Kegagalan di sini menghilangkan order tanpa antrian offline |
| **P1** | Pilih satu mekanisme scoping toko | Keamanan multi-tenant |
| **P1** | Bekukan nama field laporan (buang semua alias) | Menghilangkan sumber "angka nol tanpa error" |
| **P1** | Perbaiki F-1 (Purchase Returns terkunci ke workshop) dan F-3 (`TAKE_OUT`/`TAKEAWAY`) | Dua bug fungsional yang jelas dan murah diperbaiki |
| **P2** | Konsistenkan penamaan relasi & bentuk respons list; ikuti pagination | Kebenaran data pada volume besar |
| **P2** | Pindahkan filter tanggal/pencarian ke server | Performa di toko dengan banyak transaksi |
| **P3** | Cache Room untuk modul workshop | Kesetaraan pengalaman offline antar tipe usaha |
| **P3** | Selesaikan diskon, pajak, split payment, export laporan, multi-unit | Fitur, bukan perbaikan |

---

*Dokumen ini dihasilkan dari pembacaan kode pada commit `2aa289b`. Tidak ada kode yang
diubah. Untuk temuan kesiapan produksi (signing, ProGuard, `double` untuk uang, test,
crash reporting, peta rebrand), lihat `AUDIT_ANDROID.md`.*
