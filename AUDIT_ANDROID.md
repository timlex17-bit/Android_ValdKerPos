# AUDIT KESIAPAN PRODUKSI — Android_ValdKerPos (Valora POS)

- Tanggal audit: 2026-09-09
- Commit yang diaudit: `2abfce9` (branch `master`)
- Metode: read-only. Semua temuan diverifikasi dengan membaca file + menjalankan
  `./gradlew clean assembleDebug`, `./gradlew assembleRelease`, dan `./gradlew lint`.
- Ukuran nyata kode: **281 file Java**, **61.359 baris** di `app/src/main/java`
  (bukan 255 file / 55.900 baris — angka lama sudah tertinggal).

---

## 1. Ringkasan eksekutif

Aplikasi ini **fungsional dan bisa di-build** (debug & release keduanya sukses), dan
fondasi offline-nya jauh lebih matang dari dugaan awal: Room sudah punya 14 migrasi
eksplisit, dan setiap order membawa `client_order_id` sebagai idempotency key.
Perkiraan kesiapan produksi: **±55%**. Yang menahan rilis bukan fitur, tapi
**rilis engineering** dan **integritas uang**.

Tiga penghalang terbesar:
1. **Tidak bisa dirilis secara teknis** — tidak ada `signingConfig`, output nyata
   adalah `app-release-unsigned.apk`; `minifyEnabled false`; ProGuard kosong;
   `versionCode 1`; BASE_URL produksi dipakai juga di debug.
2. **Uang pakai `double` di seluruh jalur transaksi**, dan tiga jalur checkout
   (retail / workshop / restaurant) punya perilaku serialisasi, diskon, pajak, dan
   guard yang **berbeda-beda** — inkonsistensi ini yang paling berpotensi bikin
   selisih kas.
3. **Nol observability & nol test** — tidak ada crash reporting, hanya 2 file test
   template untuk 61.000 baris kode, dan `Log.d` yang membocorkan payload checkout
   tetap ikut di APK release karena minify mati.

---

## 2. Verifikasi 11 temuan awal

| # | Klaim | Vonis | Bukti | Dampak produksi |
|---|-------|-------|-------|-----------------|
| 1 | Tidak ada `signingConfig` | **BENAR** | `app/build.gradle:25-36` (blok `buildTypes` tanpa `signingConfig`); output nyata `app/build/outputs/apk/release/app-release-unsigned.apk` | Tidak bisa upload ke Play Store sama sekali. |
| 2 | `minifyEnabled false` + ProGuard 0 rule | **BENAR** | `app/build.gradle:31`; `app/proguard-rules.pro:1-21` (21 baris, semuanya komentar) | APK 8,1 MB tanpa shrink; kode tidak diobfuscate; **semua `Log.d` ikut di release** (478 pemanggilan `Log.*`). |
| 3 | BASE_URL hardcoded sama untuk debug & release | **BENAR** | `app/build.gradle:27` dan `app/build.gradle:34` — dua-duanya `"https://api.valdker.web.id/api/"` | Tidak ada staging. Developer testing menembak database produksi. Catatan: `SessionManager.getBaseUrl()` (`SessionManager.java:785-793`) memungkinkan override runtime, jadi masih ada jalan keluar manual. |
| 4 | `versionCode 1` / `versionName "1.0"` | **BENAR** | `app/build.gradle:15-16` | Upload kedua ke Play akan ditolak; tidak bisa memetakan crash ke versi. |
| 5 | Test praktis tidak ada | **BENAR** | Hanya `app/src/test/java/com/valdker/pos/ExampleUnitTest.java` dan `app/src/androidTest/java/com/valdker/pos/ExampleInstrumentedTest.java` | 61.359 baris tanpa jaring pengaman. Regresi hanya ketahuan di tangan kasir. |
| 6 | Rebrand setengah jalan | **BENAR (angkanya lebih parah)** | `valdker` muncul di **281 dari 281** file Java (semua `package com.valdker.pos`), + 7 file di `res/`. `valora` hanya di 17 file, mis. `local/ValoraLocalDatabase.java:509` (`"valora_local_master.db"`) | Dua nama database hidup berdampingan: `valora_local_master.db` vs `drafts/PosDraftDatabase.java:32` `"valdker_pos_drafts.db"`. Detail lengkap di bagian 6. |
| 7 | `restaurant` tidak punya fragment khusus | **BENAR** | `MainActivity.java:1061-1089` — `isWorkshopBusiness()` → `WorkshopPOSFragment`, `isRetailBusiness()` → `RetailPOSFragment`, selebihnya jatuh ke `ProductsFragment` + overlay `ui/CartFragment` | Restaurant memakai jalur checkout lama yang paling lemah guard-nya (lihat BLOCKER-3, MAJOR-1). |
| 8 | Dua HTTP stack | **BENAR, tapi dampaknya sempit** | Volley: 44 file. OkHttp: **1 file saja** — `ui/ownerchat/OwnerChatRepository.java:36-39` | Semua jalur bisnis (order, stok, laporan) 100% Volley dengan `DefaultRetryPolicy(20000, 1, 1.2f)`. OkHttp hanya untuk fitur Owner Chat (timeout 15/30/15 detik). Jadi bukan "penanganan error tidak seragam di mana-mana", melainkan satu fitur yang menyimpang. Tetap perlu disatukan. |
| 9 | i18n belum tuntas, ~29 string ID hardcoded | **SEBAGIAN — jauh lebih banyak** | Ditemukan **±60 string Bahasa Indonesia hardcoded** di `.java`. Contoh terverifikasi: `workshop/WorkshopPOSFragment.java:1458` "Shift belum dibuka. Silakan open shift terlebih dahulu.", `:3026` "Session belum siap", `:3214` "Order repository belum siap", `:3302` "Gagal simpan transaksi: ", `ui/dashboard/HomeDashboardActivity.java:932` "Anda tidak punya akses ke menu ini.". **Plus** string Inggris hardcoded di `ui/CartFragment.java:512,522,531,549,555,649`. **Plus** lint melaporkan **427 `HardcodedText`** di layout XML. Jumlah string: `values` 935, `values-in` 921, `values-b+tet` 921 (bukan 891 masing-masing) | Lint **gagal (error, bukan warning)** karena 14 string menu baru belum diterjemahkan — lihat BLOCKER-5. |
| 10 | `data_extraction_rules.xml` masih template TODO | **BENAR** | `app/src/main/res/xml/data_extraction_rules.xml:6-19` seluruhnya komentar TODO; `app/src/main/res/xml/backup_rules.xml:8-13` juga | Dampaknya **rendah**, karena `AndroidManifest.xml:35` sudah `android:allowBackup="false"` sehingga backup/transfer memang tidak jalan. Tetap harus dirapikan agar tidak menyesatkan. |
| 11 | Tidak ada referensi plan SaaS sama sekali | **SALAH** | `SessionManager.java:55` `KEY_PLAN`, `:280-294` `getPlan()/isBasic()/isPro()/isEnterprise()`, `:296-321` `canAccessModule()` + `isEffectiveModuleAllowed()`, `:334-346` `getShopFeatures()/getFeatureBoolean()`. Diisi saat login di `LoginActivity.java:341-364`. Dipakai untuk gating di 18 tempat, mis. `ui/BankAccountActivity.java:105`, `ui/offlineorders/PendingOrdersActivity.java:70`, `ui/workshop/WorkshopSimpleListActivity.java:100`, `workshop/WorkshopPOSFragment.java:1179` | Gating **ada dan aktif**, tapi berbasis `effective_modules` dari server, bukan nama plan. `isBasic()/isPro()/isEnterprise()` sendiri **tidak punya satu pun pemanggil** di luar `SessionManager` — jadi tidak ada aturan plan yang di-enforce di sisi klien. Ini kelemahan nyata (lihat MAJOR-7), tapi bukan "tidak ada sama sekali". |

---

## 3. BLOCKER — tidak boleh rilis

| No | Temuan | File:baris | Dampak | Estimasi |
|----|--------|-----------|--------|----------|
| B-1 | Tidak ada `signingConfig`. `./gradlew assembleRelease` sukses tapi menghasilkan `app-release-unsigned.apk` (8,1 MB) | `app/build.gradle:25-36`; output `app/build/outputs/apk/release/` | Tidak ada artefak yang bisa diunggah ke Play Store. Blocker mutlak. | 0,5 hari (+ keystore dan penyimpanan rahasianya) |
| B-2 | `minifyEnabled false` dan ProGuard kosong → 478 pemanggilan `Log.*` ikut ke release, termasuk **payload checkout lengkap** dan **response order dari server** | `app/build.gradle:31`; `app/proguard-rules.pro:1-21`; `ui/CartFragment.java:674` (`Log.d(TAG, "Checkout payload = " + payload.toString())`); `repositories/OrderRepository.java:73` (`createOrder SUCCESS response=`); `ui/workshop/WorkshopSimpleListActivity.java:430,459`; `network/StockTransferApi.java:127`; `repositories/PurchaseRepository.java:140` | Data pelanggan, nominal transaksi, dan struktur API terbaca lewat `adb logcat` di perangkat toko mana pun. | 1–2 hari (aktifkan R8 + tulis rule + regresi manual) |
| B-3 | **`double` untuk semua nilai uang** di seluruh jalur transaksi | `cart/CartManager.java:240-244` (`getTotalAmount()` menjumlahkan `price * qty` dalam `double`), `:92` (harga di-extract sebagai `double`), `:483-500`; `ui/checkout/NativeCheckoutDialogFragment.java:100-104,409-431,518-526,666-676`; `ui/CartFragment.java:560-567`; `MainActivity.java:2107-2152`; `local/ValoraLocalDatabase.java:82` (kolom `subtotal/discount/tax/total/paidAmount/changeAmount` disimpan sebagai `REAL`) | Selisih pembulatan pada penjumlahan baris, kembalian, dan biaya kirim. `BigDecimal` hanya dipakai untuk **validasi input**, tidak untuk aritmetika (`MainActivity.java:2705`, `ui/shift/ShiftOpenDialogFragment.java:69`, `ui/workshop/ServicePackageActivity.java:311`). Efek langsung: `NativeCheckoutDialogFragment.java:558` `cashReceived < totalNow` bisa menolak uang pas karena error floating-point. | 5–8 hari (ubah ke integer sen / `BigDecimal` end-to-end, termasuk kolom Room dan serialisasi payload) |
| B-4 | **Tiga jalur checkout menserialisasi uang dengan cara berbeda** | Restaurant: `ui/CartFragment.java:585-588,664` mengirim string `"%.2f"`. Retail: `MainActivity.java:2128,2152` mengirim `double` mentah ke JSON. Workshop: `workshop/WorkshopPOSFragment.java:3440-3441` mengirim `double`. | Server menerima tipe berbeda untuk field yang sama tergantung jenis usaha. Satu perubahan validasi di backend bisa mematikan satu jalur tanpa terdeteksi. | 2–3 hari |
| B-5 | `./gradlew lint` **GAGAL** dengan 14 error `MissingTranslation` — 14 string menu baru (purchase returns + 6 modul workshop) tidak ada di `values-in` dan `values-b+tet` | `app/src/main/res/values/strings.xml:97-110`; log: `Lint found 14 errors, 1489 warnings` | Menu Vehicles / Mechanics / Work Orders / Service History / Service Packages / Bookings / Purchase Returns akan tampil dalam bahasa Inggris di tengah UI Tetum — dan **default bahasa aplikasi adalah `tet`** (`ValdkerApp.java:18`). Lint yang gagal juga memblokir CI. | 0,5 hari |
| B-6 | Tidak ada crash reporting sama sekali (dikonfirmasi: nol referensi Crashlytics / Sentry / Firebase / Bugsnag di seluruh repo) | pencarian di `app/` — nihil | Setelah rilis, crash di lapangan tidak terlihat. Digabung dengan nol test, tidak ada cara mengetahui aplikasi rusak selain laporan lisan kasir. | 1 hari |

---

## 4. MAJOR — bisa merugikan / merusak data, rilis terbatas masih mungkin

| No | Temuan | File:baris | Dampak | Estimasi |
|----|--------|-----------|--------|----------|
| M-1 | **Order tidak ditulis ke Room sebelum request jaringan.** Order baru disimpan lokal hanya kalau `createOrder` gagal | `ui/CartFragment.java:686-764` (repo.createOrder dulu, `saveCheckoutOffline` hanya di `onError`); `workshop/WorkshopPOSFragment.java:3246-3307` | **Jawaban pertanyaan "app dimatikan paksa saat offline":** kalau app dibunuh **setelah tap bayar tapi sebelum callback**, order hilang total — tidak ada di server, tidak ada di Room. Jalur retail sedikit lebih baik karena cek jaringan dulu (`MainActivity.java:1650-1653`), tapi tetap tidak write-ahead. | 3 hari (write-ahead: simpan `PENDING_SYNC` dulu, baru kirim) |
| M-2 | **Guard double-submit tidak seragam.** Retail pakai `volatile boolean`; workshop pakai boolean biasa dan hanya menjaga *pembukaan dialog*, bukan callback konfirmasi; restaurant hanya mengandalkan `setEnabled(false)` pada tombol | Retail: `MainActivity.java:150,1622-1625,1644`. Workshop: `WorkshopPOSFragment.java:134,3001-3004` (guard) vs `:3212-3226` (`handleCheckoutBank` **tidak** memeriksa `checkoutSubmitting`). Restaurant: `ui/CartFragment.java:505,513,525,532,550,556,651,678,725,757,761` | Guard restaurant hilang begitu view fragment dibuat ulang (rotasi layar, proses di-restore) karena tombol di-inflate ulang dalam keadaan enabled. Guard workshop bergantung sepenuhnya pada dialog yang dismiss lebih dulu. | 1–2 hari |
| M-3 | **Volley meretry POST create-order secara otomatis** (`MAX_RETRIES = 1`) | `repositories/OrderRepository.java:50-52,100` | Kalau server sudah memproses order lalu koneksi timeout di 20 detik, Volley mengirim ulang. Order ganda **hanya tercegah kalau backend benar-benar men-deduplikasi `client_order_id`**. **TIDAK TERVERIFIKASI** — kode backend tidak ada di repo ini. Ini harus dikonfirmasi ke tim backend sebelum rilis. | 0,5 hari (verifikasi) + 1 hari (matikan retry untuk POST non-idempoten) |
| M-4 | Idempotency key **dibuat ulang setiap kali tombol bayar ditekan** | `ui/CartFragment.java:682`; `MainActivity.java:1645`; `workshop/WorkshopPOSFragment.java:3246` | Jalur retry otomatis aman (id dipertahankan lewat `payloadForOfflineSync`, `OfflineOrderRepository.java:617-636`, dan duplikat lokal ditolak di `:193-205`). Tapi kalau kasir **menekan bayar lagi secara manual** setelah error, `client_order_id` baru dibuat → server melihatnya sebagai order berbeda → **transaksi ganda**. | 1–2 hari (kunci id ke isi keranjang sampai sukses/dibatalkan) |
| M-5 | Tidak ada backoff dan tidak ada sync latar belakang. Retry hanya terjadi kalau ada checkout baru atau kasir menekan tombol manual | Pemicu sync hanya di `MainActivity.java:1791`, `ui/CartFragment.java:690,834`, `workshop/WorkshopPOSFragment.java:3255,3962`, `ui/offlineorders/PendingOrdersActivity.java:363`. Dependensi `androidx.work:work-runtime:2.9.1` (`app/build.gradle:54`) **nol pemakaian** | Order offline bisa mengendap berjam-jam meski jaringan sudah pulih, kalau tidak ada transaksi baru. Batasnya `MAX_SYNC_ATTEMPTS = 5` (`OfflineOrderRepository.java:47,525`) lalu masuk `NEEDS_REVIEW`. | 2 hari (WorkManager + backoff eksponensial) |
| M-6 | `SYNC_RUNNING` adalah `AtomicBoolean` statis yang tidak punya timeout | `repositories/OfflineOrderRepository.java:48,243-246` | Kalau satu callback Volley tidak pernah kembali, flag tetap `true` dan **seluruh sinkronisasi mati sampai proses di-restart** — tanpa pesan apa pun ke kasir. | 1 hari |
| M-7 | **Transaksi bisa dilakukan tanpa shift terbuka** di jalur retail dan restaurant | `MainActivity.java:1621-1653` (`submitRetailOrder` tidak memeriksa `isShiftOpen()`); `ui/CartFragment.java:502-560` (idem). Hanya workshop yang memblokir (`WorkshopPOSFragment.java:1453-1463`) dan scan barcode (`MainActivity.java:2226-2230`) | Gerbang shift (`MainActivity.java:238-258`) memakai `shiftGateAlreadyPassed` sehingga hanya berjalan sekali per sesi Activity; kalau shift ditutup dari menu, jalur checkout tidak memeriksa ulang. Akibatnya transaksi bisa jatuh di luar shift dan rekonsiliasi kas tidak cocok. | 1 hari |
| M-8 | **Tutup shift dan logout tidak memeriksa order offline yang masih pending** | `MainActivity.java:2483-2492,2539-2617` — satu-satunya syarat `canCloseShift` adalah shift terbuka dan `shiftId > 0`; tidak ada pemanggilan `countPendingSyncForShop` (`local/PendingOrderDao.java:59-60`) | Shift bisa ditutup dengan order yang belum sampai ke server → total shift salah. Datanya sendiri **tidak hilang** (Room tidak pernah dibersihkan saat logout, `SessionManager.clear()` hanya menyentuh SharedPreferences), tapi order tersebut menjadi milik shift yang sudah tertutup. | 1 hari |
| M-9 | **Diskon dan pajak dipaku ke nol** di jalur restaurant/retail | `ui/CartFragment.java:586-587` (`"discount": "0.00"`, `"tax": "0.00"`); jalur retail (`MainActivity.java:2073-2157`) tidak mengirim field diskon/pajak sama sekali. Hanya workshop yang mengirim nilai nyata (`WorkshopPOSFragment.java:3440-3441`) | Tidak ada diskon per-item maupun per-order untuk retail dan restaurant. Kalau backend menghitung pajak, klien dan server bisa berbeda total. | 3–5 hari (fitur, bukan sekadar perbaikan) |
| M-10 | **Split payment tidak ada di jalur mana pun** — array `payments` selalu berisi tepat satu objek | `ui/CartFragment.java:655-672`; `MainActivity.java:2133-2154`; `WorkshopPOSFragment.java:3517-3537` | Bayar sebagian tunai + sebagian transfer tidak bisa dilakukan. Struktur payload sudah array, jadi tinggal UI-nya. | 3–4 hari |
| M-11 | Validasi stok offline memakai cache Room yang bisa basi | `ui/retail/RetailPOSFragment.java:1199,1224,1245` ("Stok tidak cukup" dari data lokal) | Saat perangkat offline, stok yang berubah di server tidak terlihat. Order tetap dibuat dan baru ditolak saat sync — setelah struk dicetak dan pelanggan pergi. Tidak ada mekanisme resolusi konflik di `OfflineOrderRepository` (nol referensi stok di file itu). | 3 hari (minimal: tandai order sebagai butuh review + prosedur manual) |
| M-12 | Token autentikasi disimpan di `SharedPreferences` biasa | `SessionManager.java:32,65-70,121,468,477-478` — `getSharedPreferences("valdker_session", MODE_PRIVATE)`; nol pemakaian `EncryptedSharedPreferences` di seluruh repo | Di perangkat yang di-root atau lewat backup ADB, token terbaca polos. Mitigasi yang sudah ada: `allowBackup="false"` (`AndroidManifest.xml:35`) dan `usesCleartextTraffic="false"` (`:36`). | 1 hari |
| M-13 | Crash: `requireContext()` dipanggil di blok catch tanpa penjaga `isAdded()` | `workshop/WorkshopPOSFragment.java:3309-3313` — semua cabang lain di sekitarnya dijaga `isAdded()` (`:3293`, `:3329`, `:3356`), yang ini tidak | `IllegalStateException` kalau fragment sudah detach saat exception terjadi — persis pada saat kegagalan checkout, momen paling buruk. | 0,5 hari |
| M-14 | `CartManager` mengambil harga lewat **refleksi nama field** | `cart/CartManager.java:60-65,75,92-94,478-525` (`getField`/`getDeclaredField` pada `"price"`, `"selling_price"`, `"shopId"`, dst.) | Saat R8 diaktifkan untuk memperbaiki B-2, nama field akan di-obfuscate dan **semua harga menjadi 0.0 tanpa error apa pun** — keranjang senilai nol. Dua blocker ini saling mengunci dan wajib dikerjakan bersamaan. | 2 hari |

---

## 5. MINOR — kualitas, konsistensi, dependensi

| No | Temuan | File:baris |
|----|--------|-----------|
| N-1 | Lint: **1.489 warning**, didominasi 427 `HardcodedText`, 414 `UnusedResources`, 74 `Autofill`, 51 `Overdraw`, 44 `NotifyDataSetChanged`, 37 `SmallSp` | laporan: `app/build/intermediates/lint_intermediate_text_report/debug/lintReportDebug/lint-results-debug.txt` |
| N-2 | Dependensi **tidak terpakai**: `com.google.code.gson:gson` (0 import), `com.github.PhilJay:MPAndroidChart` (0 import), `com.github.DantSu:ESCPOS-ThermalPrinter-Android` (0 import — pencetakan pakai implementasi sendiri `print/BluetoothPrinterManager.java:311,412` `EscPosTextEncoder`), `androidx.work:work-runtime` (0 import) | `app/build.gradle:54,55,56,57` |
| N-3 | `appcompat` dan `material` **dideklarasikan dua kali** dengan versi berbeda: `1.6.1` vs katalog `1.7.1`, dan `1.12.0` vs katalog `1.13.0` | `app/build.gradle:53,62` vs `:66,67` + `gradle/libs.versions.toml:6,7` |
| N-4 | 12 dependensi tertinggal versi (Room 2.6.1→2.8.4, OkHttp 4.12→5.5, Glide 4.16→5.0.9, Material 1.12→1.14, dll.) | keluaran lint `[GradleDependency]` / `[NewerVersionAvailable]` |
| N-5 | `exportSchema = false` pada kedua database Room → skema tidak diekspor, sehingga 14 migrasi yang sudah ditulis **tidak bisa diuji otomatis** | `local/ValoraLocalDatabase.java:47`; `drafts/PosDraftDatabase.java:16` |
| N-6 | `PosDraftDatabase` versi 1 **tanpa migrasi dan tanpa fallback** | `drafts/PosDraftDatabase.java:29-34` — perubahan skema berikutnya akan melempar `IllegalStateException` saat membuka database |
| N-7 | `values-night/themes.xml` memakai parent **`Theme.Material3.Light`** di tema malam | `app/src/main/res/values-night/themes.xml:3`. Praktisnya tidak terlihat karena `ValdkerApp.java:15` memaksa `MODE_NIGHT_NO` — artinya seluruh `values-night/` adalah kode mati |
| N-8 | Izin `READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO`, `READ_MEDIA_IMAGES` dideklarasikan tapi **nol referensi di kode Java** | `AndroidManifest.xml:16,17,18`. Catatan: `ACCESS_FINE_LOCATION` (`:31`) **wajar dipertahankan** — sudah dibatasi `maxSdkVersion="30"` dan memang syarat Bluetooth scan di Android ≤11 |
| N-9 | `resetSyncingToPending()` mereset order **semua toko**, bukan hanya toko aktif | `local/PendingOrderDao.java:74-75`, dipanggil di `repositories/OfflineOrderRepository.java:250,302` |
| N-10 | `parseMoney()` membuang koma sebagai pemisah ribuan | `ui/checkout/NativeCheckoutDialogFragment.java:666-676`. Benar untuk USD (mata uang yang dipakai, `:335` `Locale.US`), tapi akan salah baca kalau nanti ada locale yang memakai koma sebagai desimal — "10,50" menjadi 1050 |
| N-11 | Pola subclass kosong: 6 activity hanya membungkus parent-nya tanpa menambah apa pun | `ui/workshop/BookingsActivity.java`, `MechanicsActivity.java`, `ServicePackagesActivity.java`, `VehiclesActivity.java`, `WorkOrdersActivity.java` (masing-masing 4 baris) |
| N-12 | `MainActivity.java` **2.775 baris**, `WorkshopPOSFragment.java` 4.035 baris | God-object; setiap perubahan berisiko regresi lintas fitur |
| N-13 | Peringatan Gradle: build memakai fitur yang **tidak kompatibel dengan Gradle 9.0** | keluaran `./gradlew assembleDebug` |
| N-14 | Repo mencatat 0 `TODO`/`FIXME` — bukan berarti bersih, artinya utang teknis tidak terdokumentasi di kode | pencarian di `app/src/main/java` |

---

## 6. Fitur yang belum selesai

1. **Alur `restaurant` tidak punya POS fragment sendiri.** `MainActivity.java:1061-1089` menjatuhkannya ke `ui/ProductsFragment` + overlay `ui/CartFragment`. Konsekuensi nyata, bukan kosmetik: jalur inilah yang tidak punya guard shift (M-7), guard double-submit-nya paling lemah (M-2), diskon/pajaknya dipaku nol (M-9), dan penyimpanan offline-nya tidak write-ahead (M-1). Fitur restoran yang ada (dine-in / take-out / delivery, nomor meja, alamat antar) tersebar di `ui/CartFragment.java:246-301,593-632,838` dan `ui/CartAdapter.java:128-227`.

2. **Enforcement plan SaaS tidak ada di klien.** `SessionManager.isBasic()/isPro()/isEnterprise()` (`SessionManager.java:284-294`) tidak punya satu pun pemanggil. Gating yang benar-benar berjalan memakai `effective_modules` yang dikirim server (`SessionManager.java:296-321`), dan kalau server mengirim daftar kosong, kode jatuh ke `isDefaultOfflineModule()` (`:313`) — perilaku fallback ini perlu ditinjau apakah aman.

3. **Split payment** — struktur payload sudah array (`payments`) di ketiga jalur, tetapi UI hanya bisa mengisi satu metode. Lihat M-10.

4. **Diskon per-item dan per-order** — hanya workshop yang mengirim `discount` nyata (`WorkshopPOSFragment.java:3440`). Retail dan restaurant belum. Lihat M-9.

5. **Sinkronisasi latar belakang** — `androidx.work:work-runtime` sudah menjadi dependensi tapi belum dipakai sama sekali. Lihat M-5.

6. **Modul workshop baru (Vehicles, Mechanics, Work Orders, Service History, Service Packages, Bookings)** sudah lengkap secara fungsi (CRUD lewat `ui/workshop/WorkshopSimpleListActivity.java`, 826 baris, dan sudah terhubung dari `ui/dashboard/HomeDashboardActivity.java:905-925`), tetapi **string menunya belum diterjemahkan** — inilah yang membuat lint gagal (B-5).

7. **Resolusi konflik stok saat sync** — belum ada sama sekali. Lihat M-11.

8. **`data_extraction_rules.xml` / `backup_rules.xml`** masih template. Lihat temuan awal #10.

---

## 7. Peta rebrand: Valdker → Valora

### (a) Aman diubah

| Target | Lokasi | Catatan |
|--------|--------|---------|
| Label aplikasi & teks UI | `app/src/main/res/values/strings.xml` dan dua locale lain | Murni tampilan |
| Nama kelas `ValdkerApp` | `app/src/main/java/com/valdker/pos/ValdkerApp.java:9`; referensi di `AndroidManifest.xml:34` | Refactor rename biasa; hanya dirujuk di manifest |
| Nama style `Theme.ValdKer` | `res/values/themes.xml:4`, `res/values-night/themes.xml:3`, `AndroidManifest.xml:43` | Nama resource internal |
| Nama direktori project & judul di Android Studio | root repo | Tidak ada efek runtime |
| Konstanta `TAG` untuk logging, komentar, nama file dokumentasi | tersebar | Tidak ada efek runtime |

### (b) Berisiko — jangan diubah tanpa rencana migrasi

| Target | Lokasi | Kenapa berisiko |
|--------|--------|-----------------|
| `applicationId "com.valdker.pos"` | `app/build.gradle:12` | **Risiko tertinggi.** Play Store memperlakukan `applicationId` baru sebagai **aplikasi yang sama sekali berbeda**. Pengguna lama tidak akan menerima update; harus instal ulang; **seluruh data lokal termasuk order offline yang belum tersinkron akan hilang**. Ini keputusan bisnis, bukan keputusan teknis. |
| `namespace 'com.valdker.pos'` + package 281 file Java | `app/build.gradle:6` + seluruh `app/src/main/java/com/valdker/pos/` | Bisa diubah tanpa mengubah `applicationId` (AGP mengizinkan keduanya berbeda). Tapi diff-nya menyentuh 281 file — harus dilakukan sebagai commit mekanis tersendiri, tanpa perubahan logika, agar review-nya masuk akal. |
| Nama database Room `"valdker_pos_drafts.db"` | `drafts/PosDraftDatabase.java:32` | Mengganti nama = database baru dan kosong. **Draft POS yang belum di-checkout akan hilang.** Kalau tetap diubah, butuh langkah migrasi yang menyalin file lama. |
| Nama database Room `"valora_local_master.db"` | `local/ValoraLocalDatabase.java:509` | **Sudah bernama Valora — jangan disentuh.** Di sinilah `pending_orders` disimpan. Mengganti namanya menghapus order offline pelanggan. |
| Key SharedPreferences `"valdker_session"` | `SessionManager.java:32`; juga di-hardcode ulang di `ui/inventorycount/InventoryCountDetailActivity.java:346` | Mengganti key = semua pengguna ter-logout paksa saat update, dan **state shift terbuka hilang** (`shift_open`, `shift_id`, `opening_cash`) → shift menggantung di server tanpa pasangan di klien. Perlu kode migrasi satu kali. Perhatikan juga duplikasi hardcode di `InventoryCountDetailActivity` yang mudah terlewat. |
| Key SharedPreferences `"valdker_cart"` | `cart/CartManager.java:29` | Mengganti key = keranjang yang sedang berjalan hilang saat update. Dampak lebih ringan dari session, tapi tetap kehilangan data. |
| Domain `api.valdker.web.id` | `app/build.gradle:27,34` | Terikat DNS dan sertifikat TLS. Pindah domain harus dikoordinasikan dengan backend; sediakan periode di mana kedua domain hidup. |
| Deep link | — | **Tidak ada risiko: aplikasi ini tidak punya deep link.** Satu-satunya komponen exported adalah `SplashActivity` dengan intent-filter `MAIN`/`LAUNCHER` (`AndroidManifest.xml:47-54`); tidak ada `<data>`, `scheme`, atau host di seluruh manifest. |

**Rekomendasi:** kerjakan rebrand **hanya di lapisan (a)** untuk sekarang. Tunda seluruh lapisan (b) sampai versioning, signing, dan sinkronisasi offline sudah stabil — dan tangani `applicationId` sebagai keputusan produk tersendiri.

---

## 8. Urutan pengerjaan yang direkomendasikan

**Fase 0 — bikin rilis mungkin dilakukan (1 minggu)**
1. `signingConfig` + keystore + `keystore.properties` di luar repo (B-1). *Tanpa ini tidak ada yang bisa diuji di perangkat nyata dalam bentuk release.*
2. Strategi `versionCode`/`versionName` (temuan awal #4). *Prasyarat agar crash bisa dipetakan ke versi.*
3. Terjemahkan 14 string yang hilang supaya lint hijau (B-5), lalu jadikan lint bagian dari CI. *Sekarang lint memblokir build; jadikan ia teman, bukan penghalang.*
4. Pisahkan BASE_URL debug/staging/release (temuan awal #3). *Semua pengujian berikutnya tidak boleh menyentuh data produksi.*

**Fase 1 — pasang mata sebelum menyentuh uang (3–4 hari)**
5. Crashlytics atau Sentry (B-6). *Harus terpasang **sebelum** refactor uang, supaya regresi terlihat.*
6. Aktifkan R8 **bersamaan dengan** mengganti refleksi di `CartManager` (B-2 + M-14). *Wajib satu paket: mengaktifkan R8 sendirian membuat semua harga menjadi 0.0.*

**Fase 2 — integritas uang (2 minggu)**
7. Migrasi ke integer sen atau `BigDecimal` end-to-end, termasuk kolom Room dan serialisasi (B-3).
8. Satukan serialisasi payload ketiga jalur checkout (B-4).
9. Tulis unit test untuk kalkulasi keranjang, kembalian, dan biaya kirim. *Ini titik paling murah untuk memulai testing: logika murni, tanpa Android framework.*

**Fase 3 — keandalan offline (1,5 minggu)**
10. Write-ahead order ke Room sebelum request jaringan (M-1).
11. Kunci `client_order_id` per keranjang, dan konfirmasi ke tim backend bahwa dedup `client_order_id` benar-benar ada (M-3, M-4). *Ini pertanyaan paling penting di seluruh audit dan hanya backend yang bisa menjawabnya.*
12. WorkManager + backoff + timeout untuk `SYNC_RUNNING` (M-5, M-6).

**Fase 4 — disiplin kasir (1 minggu)**
13. Cek shift di semua jalur checkout (M-7).
14. Blokir tutup shift / logout selama masih ada order pending (M-8).
15. Seragamkan guard double-submit (M-2) dan perbaiki `requireContext()` yang tidak terjaga (M-13).

**Fase 5 — kelengkapan fitur (2–3 minggu)**
16. POS fragment khusus restaurant (bagian 6 #1) — mengerjakan ini **setelah** Fase 2–4 berarti jalur baru langsung lahir dengan guard dan aritmetika yang benar, bukan mewarisi utang jalur lama.
17. Diskon dan split payment (M-9, M-10).
18. Penanganan konflik stok (M-11).

**Fase 6 — kebersihan (1 minggu)**
19. `EncryptedSharedPreferences` untuk token (M-12).
20. Buang dependensi tak terpakai, rapikan deklarasi ganda, naikkan versi (N-2, N-3, N-4).
21. Rebrand lapisan (a) saja.
22. Isi `data_extraction_rules.xml`, bersihkan izin yang tidak dipakai, hapus `values-night/` yang mati (temuan awal #10, N-7, N-8).

---

## 9. Hasil build (apa adanya)

| Perintah | Hasil |
|----------|-------|
| `./gradlew clean assembleDebug` | **BUILD SUCCESSFUL in 2m 1s** — 36 task dieksekusi. Peringatan: deprecated API, unchecked/unsafe operations, dan fitur Gradle yang tidak kompatibel dengan Gradle 9.0 |
| `./gradlew assembleRelease` | **BUILD SUCCESSFUL in 16s** — menghasilkan `app/build/outputs/apk/release/app-release-unsigned.apk` (8.130.349 byte). **Unsigned** — bukti langsung B-1 |
| `./gradlew lint` | **BUILD FAILED** — `Lint found 14 errors, 1489 warnings`. Semua 14 error adalah `MissingTranslation` di `values/strings.xml:97-110` |

Catatan lingkungan: JDK sistem 17.0.14, tetapi `gradle/gradle-daemon-jvm.properties` meminta toolchain JetBrains JDK 21 dan Gradle mengunduhnya via foojay. Build tetap berhasil.
