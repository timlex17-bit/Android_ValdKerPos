# AUDIT ANDROID — Valora POS

- Tanggal: 2026-09-10 · Branch `master`
- Kontrak acuan: `MODULE_MATRIX.md` + `GET /api/modules/` (backend, 46 modul)
- Pengujian runtime: backend Django **lokal** di `10.0.2.2:8010`, emulator API 36.
  Produksi (`api.valdker.web.id`) tidak pernah disentuh.

Dokumen ini menggantikan audit sebelumnya. Yang **sudah diperbaiki dan diverifikasi
di perangkat** ditandai ✅; yang hanya temuan ditandai dengan tingkat keparahannya.

---

## Ringkasan

Blocker uang (`double`) sudah ditutup di putaran tersendiri, lima lapis dengan
satu commit per lapis. Yang tersisa sebagai blocker rilis adalah cakupan test
dan crash reporting.

| Prioritas | Status |
|---|---|
| P1.1 uang pakai `double` | ✅ **selesai** — 5 lapis, `BigDecimal` skala 2, migrasi Room 14→15 diuji upgrade di perangkat |
| P1.2 order bisa hilang | ✅ selesai, diverifikasi dengan membunuh proses di tengah request |
| P1.3 idempotency | ✅ terverifikasi sudah benar (dikerjakan sesi sebelumnya) |
| P2.1 modul & plan | ✅ sebagian — kode mati dibuang; ketidakcocokan kontrak dilaporkan |
| P2.2 paritas business type | ❌ temuan saja |
| P2.3 hak akses staff | ⚠️ premis prompt tidak berlaku untuk Android (lihat MAJOR-4) |
| P3.1 signingConfig | ✅ selesai, APK bertanda tangan diverifikasi `apksigner` |
| P3.2 R8 + refleksi | ✅ selesai, harga diverifikasi benar dengan R8 aktif |
| P3.3 lain-lain | ✅ sebagian (versionCode, BASE_URL, lint hijau, i18n menu) |

---

## BLOCKER

| No | Temuan | File:baris | Bukti / dampak |
|---|---|---|---|
| ~~B-1~~ | ~~Aritmetika uang memakai `double`~~ — **SELESAI**, lihat bagian "Migrasi uang" | — | — |
| B-2 | **Nol test.** 2 file test template untuk ~61.000 baris. | `app/src/test/java/com/valdker/pos/ExampleUnitTest.java`, `app/src/androidTest/.../ExampleInstrumentedTest.java` | Ini prasyarat B-1: migrasi uang tanpa test adalah tebak-tebakan. |
| B-3 | **Tidak ada crash reporting.** Nol referensi Crashlytics/Sentry di `app/build.gradle`. | `app/build.gradle` (dependencies) | Setelah rilis, crash di lapangan tidak terlihat. |

### Migrasi uang (`double` → `BigDecimal`) — SELESAI

Dikerjakan sebagai putaran tersendiri dalam lima lapis, satu commit per lapis.

**Representasi: `BigDecimal` skala 2.** Backend menyimpan setiap nilai uang
sebagai `DecimalField(max_digits=12, decimal_places=2)` dan mengirimkannya
sebagai string desimal, jadi `BigDecimal` memetakan satu-lawan-satu dan nilai
menyeberang tanpa langkah konversi. Satuan terkecil (`long` sen) akan menambah
konversi di setiap batas — JSON, Room, tampilan — dan tiap konversi adalah
tempat baru untuk salah.

**Pembulatan: `HALF_EVEN`, bukan `HALF_UP`.** Ini menyimpang dari kebiasaan POS
dan disengaja. Backend membulatkan pajak dengan
`(base * percent / 100).quantize(Decimal("0.01"))` (`pos/serializers.py:2004`),
dan konteks `decimal` bawaan Python adalah `ROUND_HALF_EVEN`. `HALF_UP` akan
berbeda satu sen tepat di batas .005, dan komentar di serializer menyatakan
server akan menjadi otoritatif atas pajak di "Stage B" — selisih itu akan
berubah dari peringatan log menjadi nilai yang ditimpa.

**Catatan arsitektur yang menentukan prioritas:** server **menghitung ulang**
`subtotal` dan `total` dari `items[].price × quantity` lalu menimpa kiriman
klien (`pos/serializers.py:2014-2015`). Jadi yang menentukan angka tersimpan
bukan field `total` yang dikirim, melainkan presisi harga baris.

| Lapis | Isi | Commit |
|---|---|---|
| 1 | Kelas `Money` + 23 unit test | `71ae992` |
| 2 | Aritmetika keranjang (`CartManager`, `CartItem`) + 7 test | `3cb2b94` |
| 3 | Dialog checkout dan tiga pembuat payload | `bf57fab` |
| 4 | 9 kolom uang order belum tersinkron → TEXT, migrasi Room 14→15 | `3ef9ed5` |
| 5 | Pemformatan layar dan struk | `90b29d1` |

**Sisa yang belum dimigrasi:** 14 kolom `REAL` di tabel `cached_*`
(`cached_orders` 4, `cached_order_items` 3, `cached_stock_transfer_items` 3,
`cached_payments` 1, `cached_purchase_items` 1, `cached_product_return_items` 1,
`products` 1). Semuanya salinan tampilan yang di-refresh dari server, bukan uang
yang belum tersinkron — karena itu ditinggal di lapis berikutnya, bukan karena
terlewat. Pemformatan di luar uang order juga belum disentuh:
`ui/inventorycount/InventoryCountDetailActivity.java:221`,
`ui/purchases/PurchaseItemDraftAdapter.java:77`,
`ui/reports/ReportResultAdapter.java:153`, `ui/reports/ReportsFragment.java:582`.

---

## MAJOR

| No | Temuan | File:baris | Dampak |
|---|---|---|---|
| M-1 | **Android memaksa shift terbuka untuk SEMUA business type, padahal backend hanya mengakui modul `shifts` untuk RESTAURANT.** | `MainActivity.java:250` (`ensureShiftOpenOrBlock`), dipanggil di `:627`, `:1047`, `:2309`; kontrak: `MODULE_MATRIX.md` baris `shifts` = `business_types=[RESTAURANT]` | Terverifikasi runtime: saya membuka shift untuk shop **retail** (BEMORI) dan **workshop** (VALDKER) — API menerimanya (`POST /api/shifts/open/?shop=1` → 201). Jadi ini bukan blokade API, melainkan **modul yang tidak pernah muncul di dashboard** untuk toko retail/bengkel padahal aplikasi mewajibkannya. Ini persis temuan B2 di `MODULE_MATRIX.md` yang menunggu keputusan bisnis. |
| M-2 | **`effective_modules` kosong → klien memberi akses sendiri ke 4 modul.** | `SessionManager.java:816-821` (`isDefaultOfflineModule`: dashboard, pos, orders, settings) dipakai di `:313` | Kontrak menyatakan `effective_modules` otoritatif. Fallback ini memberi akses yang server tidak pernah berikan. Tidak saya ubah: menghapusnya membuat aplikasi tidak bisa dipakai offline saat cache auth kosong. Perlu keputusan Anda. |
| M-3 | **Daftar 29 kunci modul disalin ke kode Android.** | `ModuleRegistry.java` (29 konstanta); `SessionManager.java` `isSupportedMenuKey()` (29 `case`) | Kabar baiknya: **tidak ada kunci hantu** — seluruh 29 kunci Android valid di backend (saya diff terhadap `pos/module_registry.py`), jadi bencana dashboard Vue tidak terulang di sini. Kabar buruknya, 17 modul backend tidak dikenal Android, dan daftar ini akan basi diam-diam. Android **tidak pernah memanggil `GET /api/modules/`**. |
| M-4 | **Premis "switch akses staff" tidak berlaku di Android.** | tidak ada layar staff; nol pemanggilan `api/staff/` maupun `menu-permissions/` | Android hanya *mengonsumsi* `menu_permissions` dari respons login (`SessionManager.java:595-608`). Tidak ada tombol untuk dimatikan di sini — switch itu ada di dashboard web. Yang relevan untuk Android: kalau server menolak, aplikasi memang menampilkan pesan (mis. `ui/workshop/WorkshopSimpleListActivity.java:120`), bukan gagal diam-diam. |
| M-5 | **Role `manager` kehilangan 7 modul kalau server tidak mengirim `menu_permissions`.** | `SessionManager.java:623-650` | Daftar hardcoded untuk manager tidak memuat `warehouses`, `warehouse_stocks`, `stock_transfers`, `bank_accounts`, `bank_ledgers`, `purchase_returns`, `settings`. |
| M-6 | **Purchase Returns mati untuk toko non-workshop.** | `ui/purchasereturns/PurchaseReturnListActivity.java:8` mewarisi guard `!session.isWorkshop()` di `ui/workshop/WorkshopSimpleListActivity.java:100` | Tile muncul di dashboard retail/restoran lalu langsung tertutup dengan toast "permission denied". Backend memang `implemented=False` (placeholder 501), jadi dampaknya terbatas — tapi UI-nya menipu. |
| M-7 | **Tidak ada backoff maupun sync latar belakang.** | `repositories/OfflineOrderRepository.java:47` (`MAX_SYNC_ATTEMPTS = 5`); dependensi `androidx.work:work-runtime` **nol pemakaian** | Sync hanya jalan saat ada checkout baru atau ditekan manual. Order offline bisa mengendap meski jaringan sudah pulih. |
| M-8 | **`SYNC_RUNNING` statis tanpa timeout.** | `repositories/OfflineOrderRepository.java:48,243-246` | Kalau satu callback Volley tidak pernah kembali, seluruh sinkronisasi mati sampai proses di-restart, tanpa pesan apa pun. |
| M-9 | **Token disimpan di `SharedPreferences` biasa.** | `SessionManager.java:69` (`MODE_PRIVATE`); nol pemakaian `EncryptedSharedPreferences` | Mitigasi yang sudah ada: `allowBackup="false"` dan `usesCleartextTraffic="false"` di manifest. |
| M-10 | **`requireContext()` di blok catch tanpa penjaga `isAdded()`.** | `workshop/WorkshopPOSFragment.java` — catch pada jalur checkout | `IllegalStateException` kalau fragment sudah detach saat exception terjadi. |

---

## MINOR

| No | Temuan | File:baris |
|---|---|---|
| N-1 | ~60 string Bahasa Indonesia hardcoded di `.java`, padahal 3 locale lengkap tersedia | mis. `workshop/WorkshopPOSFragment.java:1458`, `:3026`, `:3214`; `ui/dashboard/HomeDashboardActivity.java:932` |
| N-2 | 427 `HardcodedText` di layout XML (laporan lint) | `app/src/main/res/layout/*` |
| N-3 | Judul BottomNav hardcoded, tidak ikut ganti bahasa | `res/menu/bottom_nav_menu.xml` |
| N-4 | `data_extraction_rules.xml` & `backup_rules.xml` masih template TODO | `res/xml/data_extraction_rules.xml:6-19` — dampak rendah karena `allowBackup="false"` |
| N-5 | Dua HTTP stack: Volley (44 file) vs OkHttp (1 file, Owner Chat) | `ui/ownerchat/OwnerChatRepository.java:36-40` |
| N-6 | Izin `READ_MEDIA_VIDEO`/`READ_MEDIA_AUDIO`/`READ_MEDIA_IMAGES` nol referensi di kode | `AndroidManifest.xml:16-18`. `ACCESS_FINE_LOCATION` (`:31`) **wajar** — dibatasi `maxSdkVersion=30`, syarat BT scan |
| N-7 | Dependensi tak terpakai: `gson`, `MPAndroidChart`, `ESCPOS-ThermalPrinter`, `work-runtime` | `app/build.gradle` |
| N-8 | `appcompat` & `material` dideklarasikan dua kali dengan versi berbeda | `app/build.gradle` vs `gradle/libs.versions.toml` |
| N-9 | `exportSchema = false` → 14 migrasi Room tidak bisa diuji otomatis | `local/ValoraLocalDatabase.java:47` |
| N-10 | `PosDraftDatabase` versi 1 tanpa migrasi & tanpa fallback | `drafts/PosDraftDatabase.java:29-34` |
| N-11 | `values-night/` mewarisi tema Light; seluruh folder kode mati karena `MODE_NIGHT_NO` dipaksa | `res/values-night/themes.xml:3`; `ValdkerApp.java:15` |
| N-12 | Tabel Room `cached_roles` tanpa DAO | `local/CachedRoleEntity.java` |
| N-13 | Tombol Export laporan disembunyikan & disabled | `ui/reports/ReportsFragment.java:229-230` |
| N-14 | Pagination tidak diikuti kecuali `api/products/`; filter tanggal & pencarian dilakukan di klien | `ui/orders/OrdersFragment.java:298-306` |

---

## Yang diperbaiki di sesi ini

### ✅ P1.2 — write-ahead (`b888fa8`)

Ketiga alur dulu menulis order ke Room **setelah** request gagal. Sekarang
ditulis `PENDING_SYNC` lebih dulu, request baru dikirim setelah penulisan
sukses, dan sukses menandainya `SYNCED`.

Penanganan penolakan server dibedakan: kegagalan transport → tetap
`PENDING_SYNC`; penolakan HTTP → `FAILED` (masih akan diulang); penolakan
`device_time` → `NEEDS_REVIEW`. Yang terakhir penting — sync otomatis mengirim
ulang dengan `is_offline_sync=true`, dan backend **melewati** pemeriksaan jam
untuk payload offline (`pos/serializers.py:1705`), jadi retry otomatis justru
akan meloloskan order yang baru saja ditolak penjaga jam.

**Bukti runtime.** Server dibuat menahan `POST /api/orders/` 45 detik, lalu
aplikasi dibunuh paksa di tengah request:

```
15:30:53.109 MAIN_NATIVE: Retail order submit client_order_id=android-shop3-…-f94a7c15…
15:30:53.116 OFFLINE_ORDER_REPO: Pending order saved … syncStatus=PENDING_SYNC inserted=true
15:30:53.125 MAIN_NATIVE: Retail order write-ahead saved … inserted=true
>>> am force-stop com.valdker.pos
```

Order tersimpan **7 ms** setelah tap, sebelum request selesai. Server ternyata
tetap membuat order (INV000000000087) setelah klien mati — skenario terburuk.
Setelah restart, sync mengirim ulang kunci yang sama, server memutar ulang order
yang sudah ada, dan barisnya ditutup:

```
15:32:41.966 OFFLINE_ORDER_REPO: Syncing pending order … attempt=1
15:32:42.230 ORDER_REPO: createOrder SUCCESS response={"id":87,"invoice_number":"INV000000000087",…}
15:32:42.233 OFFLINE_ORDER_REPO: Pending order synced …
```

Query database: **1** order untuk kunci itu, **nol** duplikat di seluruh tabel.
Sebelum perbaikan, order itu akan ada di server tanpa jejak lokal sama sekali.

### ✅ P1.3 — idempotency terverifikasi

Ketiganya sudah benar (dikerjakan sesi sebelumnya, saya verifikasi ulang):
kunci dicetak **sekali per checkout** dan **diikat ke draft aktif** lewat peta
`draftId → kunci` (`MainActivity.java:159,163`; `workshop/WorkshopPOSFragment.java:141`),
dan `ensureClientOrderId` benar-benar "ensure" (`OfflineOrderRepository.java:144-145`).

Peta restoran sengaja dipegang `MainActivity`, bukan `CartFragment`, karena
fragment itu dibuat ulang setiap kali overlay keranjang dibuka.

### ✅ P3.1 — signing (`7edaa2a`)

Kredensial dibaca dari `keystore.properties` yang di-gitignore
(`app/build.gradle`, dengan `keystore.properties.example` sebagai contoh). Kalau
file itu tidak ada, build tetap sukses dan kembali menghasilkan APK unsigned —
mesin dev dan CI tanpa rahasia tidak rusak.

Diverifikasi dengan keystore sekali pakai (sudah dihapus):
`app-release.apk`, `apksigner verify` → `Signer #1 certificate DN: CN=Throwaway Verify…`.

Sekalian: `versionCode` 1 → 2, dan `BASE_URL` jadi properti gradle per build
type. Manfaatnya langsung terasa — seluruh pengujian sesi ini memakai
`-PdebugBaseUrl=http://10.0.2.2:8010/api/` **tanpa mengedit kode sama sekali**.

### ✅ P3.2 — refleksi dibuang, R8 menyala (`493c65a`)

Prompt menyebut satu titik refleksi (`CartManager`). Ternyata ada **empat**:
`cart/CartManager.java`, `adapters/ProductAdapter.java`,
`ui/purchases/PurchasesFragment.java`, `ui/productreturns/ProductReturnsFragment.java`.
Menyalakan R8 tanpa membersihkan keempatnya akan membuat harga jadi 0.00.

Penggantinya akses field langsung — dan itu memang setara, karena
`ProductRepository.java:499-505` sudah meresolusi seluruh alias harga dari JSON
ke `Product.price` jauh sebelum kelas-kelas itu melihat objeknya.

Dua daftar tebakan bahkan **tidak punya satu pun field yang cocok**: pencarian
pembelian menebak `invoiceNumber`, `invoice_id`, `status`, `date`, `createdAt` —
tidak ada satu pun di `PurchaseLite`, sehingga selama ini hanya mencari nama
pemasok. Sekarang mencari invoice, pemasok, tanggal, dan total.

**Bukti runtime dengan R8 aktif:** harga di grid produk `$4.00 / $10.00 / $1.50 /
$1.00` dan di keranjang `$4.00` (bukan `$0.00`), checkout tetap `201`. Di dex
release, keempat string log sensitif (`Checkout payload = `,
`createOrder SUCCESS response=`, `Retail order submit client_order_id=`,
`FINAL itemsArr = `) sudah **terbuang**. APK 8.130.349 → **3.517.762** byte.

Catatan: log hanya terbuang di build **release**. Build debug bersifat
`debuggable`, sehingga R8 hanya melakukan shrink tanpa optimisasi dan
`-assumenosideeffects` tidak berlaku di sana.

### ✅ i18n menu (`3f5dc1e`) dan kode mati (`164d6cd`)

14 string menu diterjemahkan ke `id` dan `tet`, sehingga `./gradlew lint`
**lulus** (sebelumnya gagal dengan 14 error `MissingTranslation`).
`isBasic()/isPro()/isEnterprise()` dihapus — nol pemanggil, padahal bentuknya
seperti kontrol akses.

---

## Fitur yang belum selesai

1. **POS restoran tanpa fragment sendiri** — jatuh ke `ui/ProductsFragment` +
   overlay `ui/CartFragment` (`MainActivity.java:1073-1101`).
2. **Paritas business type belum setara** (P2.2):

   | Kemampuan | Retail | Bengkel | Restoran |
   |---|---|---|---|
   | Fragment POS sendiri | ✅ | ✅ | ❌ jalur lama |
   | Gerbang shift | dipaksa | dipaksa | dipaksa |
   | Write-ahead offline | ✅ | ✅ | ✅ |
   | Cetak struk | ✅ | ✅ | ✅ |
   | Diskon | ❌ dipaku 0 | ❌ dipaku 0 | ❌ dipaku 0 |
   | Pajak | tidak dikirim | ❌ dipaku 0 | ❌ dipaku 0 |
   | Split payment | ❌ | ❌ | ❌ |
   | Retur | lewat modul terpisah | lewat modul terpisah | backend melarang untuk RESTAURANT |

3. **Split payment** — `enable_split_payment` dibaca (`MainActivity.java:479`)
   lalu tidak dipakai untuk apa pun. Array `payments` selalu satu elemen.
4. **Diskon & pajak** — belum ada UI di jalur mana pun.
5. **`TAKE_OUT` vs `TAKEAWAY`** — POS mengirim `TAKE_OUT` (`cart/CartManager.java:33`),
   filter laporan mengirim `TAKEAWAY` (`ui/reports/ReportsFragment.java:194`).
6. **Sinkronisasi latar belakang** — WorkManager jadi dependensi tapi nol pemakaian.
7. **Konflik stok saat offline lama** — tidak ada penanganan sama sekali.
8. **`GET /api/modules/` belum dipakai** — Android masih bergantung pada
   `effective_modules` di respons login saja.

---

## Urutan pengerjaan yang saya rekomendasikan

**1. Crash reporting (B-3) — 1 hari, kerjakan lebih dulu.**
Bukan karena paling penting, tapi karena semua langkah berikutnya menyentuh
jalur uang. Tanpa ini, regresi di lapangan tidak akan terlihat sampai ada kasir
yang mengeluh.

**2. Test untuk kalkulasi keranjang (B-2) — 3–4 hari.**
Titik termurah untuk memulai: logika murni, tanpa framework Android. Ini
jaring pengaman untuk langkah 3, bukan pekerjaan terpisah.

**3. Migrasi uang ke `BigDecimal`/integer sen (B-1) — 5–8 hari, sendirian.**
Bertahap: `CartManager` → dialog checkout → payload → kolom Room (butuh migrasi
Room baru). Jangan digabung dengan apa pun. Setiap langkah dijalankan di
perangkat dan dicocokkan totalnya dengan server.

**4. Keputusan kontrak `shifts` (M-1) — perlu jawaban Anda, bukan kode.**
Apakah bengkel dan retail memang wajib shift? Kalau ya, `business_types` modul
`shifts` di backend harus diperluas. Kalau tidak, gerbang di Android harus
dibatasi ke restoran. Sekarang keduanya bertentangan.

**5. Konsumsi `GET /api/modules/` (M-3) — 2 hari.**
Hapus `ModuleRegistry` dan `isSupportedMenuKey` sebagai sumber kebenaran;
pakai daftar dari server, cache ke Room untuk offline. Sekaligus menutup M-5.

**6. Keandalan offline (M-7, M-8) — 2 hari.**
WorkManager + backoff, dan timeout untuk `SYNC_RUNNING`.

**7. Perbaikan kecil bernilai tinggi — 1 hari.**
M-6 (purchase returns), poin 5 `TAKE_OUT`/`TAKEAWAY`, M-10 (`requireContext()`),
M-9 (`EncryptedSharedPreferences`).

**8. Kelengkapan fitur — 2–3 minggu.**
POS restoran, diskon, split payment, konflik stok.

Rebrand Valora tetap ditunda sesuai instruksi: `applicationId` tidak disentuh.

---

## Bukti uji uang — layar vs server

Backend lokal, emulator API 36. "Layar" = angka yang dibaca kasir; "Server" =
baris yang tersimpan setelah checkout.

| Kasus | Layar | Server | Hasil |
|---|---|---|---|
| 3 item @ 0.10 | total `$0.30`, uang pas `0.30` diterima, kembalian `$0.00` | `subtotal=0.30 total=0.30`, item `price=0.10 qty=3` | cocok |
| 0.10 + 0.20 (dua baris) | total `$0.30`, uang pas `0.30` diterima | `subtotal=0.30 total=0.30` | cocok |
| Kembalian 100.00 untuk 87.65 | `Troka: $12.35` | `subtotal=87.65 total=87.65`, payment `87.65` | cocok |
| Pajak 11% dari 0.30 | Android mengirim `tax=0.00` | server: `expected_tax=0.03`, tersimpan `0.00` + peringatan mismatch | **tidak cocok — Android tidak punya pajak** |
| Diskon 10% dari 99.99 | tidak bisa diuji lewat UI | — | hanya unit test: `10.00` |
| Split 33.33+33.33+33.34 | tidak bisa diuji lewat UI | — | hanya unit test: `100.00`, sisa `0.00` |

Tiga kasus terakhir **tidak bisa dijalankan end-to-end** karena Android tidak
punya UI diskon, pajak, maupun split payment — ketiganya dipaku nol / satu
pembayaran di semua jalur. Aritmetikanya diuji di `MoneyTest`.

Kasus pajak diverifikasi runtime dan hasilnya konkret: dengan
`POSSettings.tax_percent = 11` pada basis 0.30, server mencatat
`Order tax mismatch: client_tax=0.00 expected_tax=0.03` lalu menyimpan `0.00`.
Begitu backend masuk "Stage B" dan menjadi otoritatif atas pajak, **total setiap
order Android akan berubah**. (Nilai `tax_percent` toko uji sudah saya kembalikan
ke 0.)

### Uji migrasi Room 14 → 15

Bukan dibaca dari kode, tapi dengan upgrade di perangkat:

1. Build v14 dipasang, jaringan dimatikan, dibuat order offline 3 × 0.10.
2. Isi tabel v14: `pending_order_items.total = 0.30000000000000004` — bug ini
   memang tersimpan di penjualan yang belum tersinkron.
3. Build v15 dipasang **di atasnya** (tanpa uninstall).
4. Hasil: `user_version` 14 → 15, kolom REAL → TEXT, order **masih ada** dengan
   `localOrderId` dan status `PENDING_SYNC` yang sama, dan `total` menjadi
   `'0.30'`.
5. Jaringan dinyalakan: order hasil migrasi tersinkron ke server sebagai
   `subtotal=0.30 total=0.30`, item `price=0.10 qty=3`.

Tidak ada order yang hilang.

---

## Yang tidak bisa saya verifikasi

- **Perilaku di perangkat fisik** — seluruh pengujian di emulator API 36.
  Pencetakan struk Bluetooth **TIDAK TERVERIFIKASI**: emulator tidak punya
  printer termal. Yang perlu diperiksa di perangkat asli setelah migrasi uang:
  struk harus mencetak `0.30` bukan `0.3` atau `0.30000000000000004`, kolom
  Subtotal/Discount/Tax/Total/Paid/Change harus sejajar, dan struk cetak ulang
  dari layar Pesanan Offline harus menampilkan angka yang sama dengan struk
  aslinya.
- **Build release terhadap backend lokal** — release memblokir HTTP polos
  (benar), jadi verifikasi R8 dilakukan lewat build debug yang di-minify plus
  pemeriksaan statis pada dex release. Perilaku release penuh terhadap backend
  HTTPS **TIDAK TERVERIFIKASI**.
- **Konflik stok saat offline berkepanjangan** — butuh skenario multi-perangkat.
- **Nilai `granted`/`valid_for_shop` dari `GET /api/modules/`** — Android belum
  memanggilnya, jadi tidak ada yang bisa dibandingkan di sisi klien.
