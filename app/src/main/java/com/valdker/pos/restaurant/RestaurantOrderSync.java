package com.valdker.pos.restaurant;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

/**
 * Menempelkan meja/pelayan ke payload create order, lalu memeriksa apakah
 * server benar-benar menyimpannya.
 *
 * <h3>Kenapa perlu diperiksa</h3>
 * DRF membuang field yang tidak dikenal serializer tanpa bersuara. Kalau
 * backend belum menerima {@code table_id}/{@code waiter_id}, server tetap
 * membalas {@code 201} dan order tetap dibuat - hanya tanpa meja dan pelayan.
 * Ini kegagalan senyap yang bentuknya sama persis dengan yang dulu
 * menghilangkan {@code client_order_id}: semua tampak berhasil sampai
 * seseorang membuka laporan dan menemukan kolomnya kosong. Karena itu
 * {@code 201} saja tidak pernah dianggap bukti; yang dianggap bukti adalah
 * isi respons.
 *
 * <p>Semua pemanggilan di sini harus sudah berada di balik
 * {@code SessionManager.canAccessModule("tables"/"waiters")}.
 */
public final class RestaurantOrderSync {

    /** Tag khusus supaya mudah di-grep di logcat saat memverifikasi. */
    public static final String TAG = "DINEIN_VERIFY";

    public static final String FIELD_TABLE_ID = "table_id";
    public static final String FIELD_WAITER_ID = "waiter_id";

    /** Nama field balasan; read-only di serializer Order. */
    private static final String RESPONSE_TABLE = "table";
    private static final String RESPONSE_WAITER = "waiter";

    private RestaurantOrderSync() {
    }

    /**
     * Hasil pemeriksaan satu order. Sengaja membedakan "tidak dikirim" dari
     * "dikirim tapi hilang" - hanya yang kedua yang merupakan masalah.
     */
    public static final class VerifyResult {
        @Nullable public final Long sentTableId;
        @Nullable public final Long sentWaiterId;
        @Nullable public final Long returnedTableId;
        @Nullable public final Long returnedWaiterId;

        VerifyResult(@Nullable Long sentTableId,
                     @Nullable Long sentWaiterId,
                     @Nullable Long returnedTableId,
                     @Nullable Long returnedWaiterId) {
            this.sentTableId = sentTableId;
            this.sentWaiterId = sentWaiterId;
            this.returnedTableId = returnedTableId;
            this.returnedWaiterId = returnedWaiterId;
        }

        public boolean sentAnything() {
            return sentTableId != null || sentWaiterId != null;
        }

        public boolean tableDropped() {
            return sentTableId != null && !sentTableId.equals(returnedTableId);
        }

        public boolean waiterDropped() {
            return sentWaiterId != null && !sentWaiterId.equals(returnedWaiterId);
        }

        public boolean anythingDropped() {
            return tableDropped() || waiterDropped();
        }
    }

    /**
     * Menaruh {@code table_id}/{@code waiter_id} di payload. Nilai {@code null}
     * tidak ditulis sama sekali, bukan ditulis sebagai JSON null: pada create
     * order, "tidak menyebut field" dan "menyebut field bernilai null" bisa
     * ditangani berbeda oleh serializer, dan yang kita maksud adalah yang
     * pertama.
     */
    public static void attachDineInFields(@NonNull JSONObject payload,
                                          @Nullable Long tableId,
                                          @Nullable Long waiterId) {
        try {
            if (tableId != null && tableId > 0L) payload.put(FIELD_TABLE_ID, (long) tableId);
            if (waiterId != null && waiterId > 0L) payload.put(FIELD_WAITER_ID, (long) waiterId);
        } catch (Exception e) {
            Log.e(TAG, "Unable to attach dine-in fields to order payload", e);
        }
    }

    @NonNull
    public static VerifyResult verify(@Nullable JSONObject sentPayload,
                                      @Nullable JSONObject response) {
        return new VerifyResult(
                readId(sentPayload, FIELD_TABLE_ID),
                readId(sentPayload, FIELD_WAITER_ID),
                readId(response, RESPONSE_TABLE),
                readId(response, RESPONSE_WAITER)
        );
    }

    /**
     * Memeriksa respons create order dan, kalau meja atau pelayan hilang,
     * memasangnya lewat endpoint assign yang berdiri sendiri.
     *
     * <p>Penambalan ini bukan pengganti pemeriksaannya. Ia ada supaya sebuah
     * order tidak kehilangan mejanya hanya karena create belum menerima
     * field-nya; yang menjadi bukti tetap isi respons, dan setiap penambalan
     * tercatat di log sebagai peringatan, bukan lewat begitu saja.
     *
     * <p>Kegagalan menambal TIDAK boleh menggagalkan checkout: ordernya sudah
     * dibuat dan sudah dibayar. Yang hilang hanya atributnya, dan membuang
     * order karena itu jauh lebih merugikan daripada meja yang kosong.
     */
    public static void reconcileAfterCreate(@NonNull Context context,
                                            @Nullable String token,
                                            @Nullable JSONObject sentPayload,
                                            @Nullable JSONObject response,
                                            @NonNull String source) {
        VerifyResult r = verify(sentPayload, response);
        if (!r.sentAnything()) return;

        long orderId = response != null ? response.optLong("id", 0L) : 0L;

        Log.i(TAG, "source=" + source
                + " orderId=" + orderId
                + " sent[table=" + r.sentTableId + " waiter=" + r.sentWaiterId + "]"
                + " returned[table=" + r.returnedTableId + " waiter=" + r.returnedWaiterId + "]");

        if (!r.anythingDropped()) {
            Log.i(TAG, "OK source=" + source + " orderId=" + orderId
                    + " - server menyimpan meja dan pelayan sesuai yang dikirim");
            return;
        }

        if (r.tableDropped()) {
            Log.w(TAG, "TABLE DROPPED source=" + source + " orderId=" + orderId
                    + " sent=" + r.sentTableId + " returned=" + r.returnedTableId
                    + " - mencoba menambal lewat POST /api/orders/{id}/assign-table/");
            repairTable(context, token, orderId, r.sentTableId, source);
        }

        if (!r.waiterDropped()) return;

        Log.w(TAG, "WAITER DROPPED source=" + source + " orderId=" + orderId
                + " sent=" + r.sentWaiterId + " returned=" + r.returnedWaiterId
                + " - mencoba menambal lewat POST /api/orders/{id}/assign-waiter/");

        if (orderId <= 0L) {
            Log.e(TAG, "WAITER UNRECOVERABLE source=" + source
                    + " - respons tidak memuat id order, assign-waiter tidak bisa dipanggil");
            return;
        }

        final Long wanted = r.sentWaiterId;
        new RestaurantRepository(context).assignWaiter(token, orderId, wanted,
                new RestaurantRepository.AssignCallback() {
                    @Override
                    public void onSuccess(@NonNull JSONObject order) {
                        Long now = readId(order, RESPONSE_WAITER);
                        if (wanted.equals(now)) {
                            Log.i(TAG, "WAITER REPAIRED orderId=" + order.optLong("id", 0L)
                                    + " waiter=" + now);
                        } else {
                            Log.e(TAG, "WAITER STILL MISSING after assign-waiter orderId="
                                    + order.optLong("id", 0L) + " waiter=" + now);
                        }
                    }

                    @Override
                    public void onError(int statusCode, @NonNull String message) {
                        // Order sudah terbuat; ini tidak membatalkan apa pun.
                        Log.e(TAG, "WAITER REPAIR FAILED status=" + statusCode
                                + " detail=" + message);
                    }
                });
    }

    private static void repairTable(@NonNull Context context,
                                    @Nullable String token,
                                    long orderId,
                                    @NonNull Long wanted,
                                    @NonNull String source) {
        if (orderId <= 0L) {
            Log.e(TAG, "TABLE UNRECOVERABLE source=" + source
                    + " - respons tidak memuat id order, assign-table tidak bisa dipanggil");
            return;
        }
        new RestaurantRepository(context).assignTable(token, orderId, wanted,
                new RestaurantRepository.AssignCallback() {
                    @Override
                    public void onSuccess(@NonNull JSONObject order) {
                        Long now = readId(order, RESPONSE_TABLE);
                        if (wanted.equals(now)) {
                            Log.i(TAG, "TABLE REPAIRED orderId=" + order.optLong("id", 0L)
                                    + " table=" + now);
                        } else {
                            Log.e(TAG, "TABLE STILL MISSING after assign-table orderId="
                                    + order.optLong("id", 0L) + " table=" + now);
                        }
                    }

                    @Override
                    public void onError(int statusCode, @NonNull String message) {
                        // Order sudah terbuat dan dibayar; ini tidak membatalkannya.
                        Log.e(TAG, "TABLE REPAIR FAILED status=" + statusCode
                                + " detail=" + message);
                    }
                });
    }

    @Nullable
    private static Long readId(@Nullable JSONObject o, @NonNull String key) {
        if (o == null || !o.has(key) || o.isNull(key)) return null;
        long v = o.optLong(key, 0L);
        return v > 0L ? v : null;
    }
}
