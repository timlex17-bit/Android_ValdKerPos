package com.valdker.pos.restaurant;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.valdker.pos.SessionManager;
import com.valdker.pos.network.ApiClient;
import com.valdker.pos.network.ApiConfig;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Endpoint dine-in restoran: meja, pelayan, dan laporan pelayan.
 *
 * <p>Semua endpoint di sini dijaga backend oleh kunci modul {@code "tables"} /
 * {@code "waiters"}, yang keduanya {@code business_types=[RESTAURANT]}. Shop
 * retail/workshop mendapat {@code 403} untuk semuanya. Klien tidak mengandalkan
 * 403 itu sebagai gerbang - pemanggil harus sudah memeriksa
 * {@link SessionManager#canAccessModule(String)} lebih dulu supaya UI-nya tidak
 * pernah muncul, bukan muncul lalu gagal.
 */
public class RestaurantRepository {

    public interface TablesCallback {
        void onSuccess(@NonNull List<RestaurantTable> tables);
        void onError(int statusCode, @NonNull String message);
    }

    public interface WaitersCallback {
        void onSuccess(@NonNull List<Waiter> waiters);
        void onError(int statusCode, @NonNull String message);
    }

    public interface AssignCallback {
        void onSuccess(@NonNull JSONObject order);
        void onError(int statusCode, @NonNull String message);
    }

    public interface PerformanceCallback {
        void onSuccess(@NonNull List<WaiterPerformanceRow> rows);
        void onError(int statusCode, @NonNull String message);
    }

    /** Kunci modul; sama persis dengan yang dipakai backend. */
    public static final String MODULE_TABLES = "tables";
    public static final String MODULE_WAITERS = "waiters";

    private static final String TAG = "RESTAURANT_REPO";
    private static final int TIMEOUT_MS = 20000;
    private static final int MAX_RETRIES = 1;
    private static final float BACKOFF_MULT = 1.2f;

    private final Context appContext;

    public RestaurantRepository(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

    // ------------------------------------------------------------- meja

    /**
     * Meja dengan {@code is_active=false} dibuang di sini, bukan di UI, supaya
     * tidak ada layar yang lupa menyaringnya.
     */
    public void fetchTables(@Nullable String token, @NonNull TablesCallback cb) {
        String url = ApiConfig.url(new SessionManager(appContext), "api/tables/");
        Log.i(TAG, "fetchTables() GET -> " + url);

        StringRequest req = new StringRequest(
                Request.Method.GET,
                url,
                body -> {
                    try {
                        JSONArray arr = extractArray(body);
                        List<RestaurantTable> out = new ArrayList<>();
                        for (int i = 0; i < arr.length(); i++) {
                            RestaurantTable t = RestaurantTable.fromJson(arr.optJSONObject(i));
                            if (t != null && t.isActive) out.add(t);
                        }
                        Log.i(TAG, "fetchTables() ok count=" + out.size());
                        cb.onSuccess(out);
                    } catch (Exception e) {
                        cb.onError(0, "Parse error: " + e.getMessage());
                    }
                },
                err -> failFrom(err, cb::onError, "fetchTables")
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return buildHeaders(token);
            }
        };
        dispatch(req);
    }

    // ---------------------------------------------------------- pelayan

    public void fetchWaiters(@Nullable String token, @NonNull WaitersCallback cb) {
        String url = ApiConfig.url(new SessionManager(appContext), "api/restaurant/waiters/");
        Log.i(TAG, "fetchWaiters() GET -> " + url);

        StringRequest req = new StringRequest(
                Request.Method.GET,
                url,
                body -> {
                    try {
                        JSONArray arr = extractArray(body);
                        List<Waiter> out = new ArrayList<>();
                        for (int i = 0; i < arr.length(); i++) {
                            Waiter w = Waiter.fromJson(arr.optJSONObject(i));
                            if (w != null) out.add(w);
                        }
                        Log.i(TAG, "fetchWaiters() ok count=" + out.size());
                        cb.onSuccess(out);
                    } catch (Exception e) {
                        cb.onError(0, "Parse error: " + e.getMessage());
                    }
                },
                err -> failFrom(err, cb::onError, "fetchWaiters")
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return buildHeaders(token);
            }
        };
        dispatch(req);
    }

    /**
     * Satu-satunya jalur tulis untuk pelayan sebuah order.
     *
     * <p>Sengaja BUKAN {@code PATCH /api/orders/{id}/}: endpoint itu rusak untuk
     * request apa pun yang menyertakan {@code items}, dan {@code items} wajib
     * ada di setiap update menurut serializer-nya. Aksi ini juga yang menegakkan
     * aturan "pelayan harus staff aktif di toko yang sama" dan "order lunas
     * tidak boleh ditugaskan ulang".
     *
     * @param waiterId {@code null} untuk mengosongkan pelayan.
     */
    public void assignWaiter(@Nullable String token,
                             long orderId,
                             @Nullable Long waiterId,
                             @NonNull AssignCallback cb) {
        String url = ApiConfig.url(new SessionManager(appContext),
                "api/orders/" + orderId + "/assign-waiter/");

        JSONObject body = new JSONObject();
        try {
            // Field ini wajib ada. Tidak mengirimnya sama sekali -> 400,
            // yang berbeda artinya dari mengirim null (= kosongkan pelayan).
            body.put("waiter_id", waiterId == null ? JSONObject.NULL : waiterId);
        } catch (Exception e) {
            cb.onError(0, "Unable to build assign-waiter body: " + e.getMessage());
            return;
        }

        Log.i(TAG, "assignWaiter() POST -> " + url + " waiter_id=" + waiterId);

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.POST,
                url,
                body,
                res -> {
                    Log.i(TAG, "assignWaiter() ok orderId=" + orderId
                            + " waiter=" + res.opt("waiter"));
                    cb.onSuccess(res);
                },
                err -> failFrom(err, cb::onError, "assignWaiter")
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return buildHeaders(token);
            }
        };
        dispatch(req);
    }

    /**
     * Kembaran {@link #assignWaiter} untuk meja, dengan aturan yang sama:
     * order lunas tidak boleh dipindah meja, dan meja harus milik toko ini.
     * Meja yang sedang terisi TIDAK ditolak - satu meja memang boleh
     * membawa beberapa order terbuka.
     */
    public void assignTable(@Nullable String token,
                            long orderId,
                            @Nullable Long tableId,
                            @NonNull AssignCallback cb) {
        String url = ApiConfig.url(new SessionManager(appContext),
                "api/orders/" + orderId + "/assign-table/");

        JSONObject body = new JSONObject();
        try {
            body.put("table_id", tableId == null ? JSONObject.NULL : tableId);
        } catch (Exception e) {
            cb.onError(0, "Unable to build assign-table body: " + e.getMessage());
            return;
        }

        Log.i(TAG, "assignTable() POST -> " + url + " table_id=" + tableId);

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.POST,
                url,
                body,
                res -> {
                    Log.i(TAG, "assignTable() ok orderId=" + orderId
                            + " table=" + res.opt("table"));
                    cb.onSuccess(res);
                },
                err -> failFrom(err, cb::onError, "assignTable")
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return buildHeaders(token);
            }
        };
        dispatch(req);
    }

    // -------------------------------------------------------- laporan

    /**
     * @param dateFrom {@code YYYY-MM-DD} atau kosong
     * @param dateTo   {@code YYYY-MM-DD} atau kosong
     */
    public void fetchWaiterPerformance(@Nullable String token,
                                       @Nullable String dateFrom,
                                       @Nullable String dateTo,
                                       @NonNull PerformanceCallback cb) {
        StringBuilder path = new StringBuilder("api/reports/waiter-performance/");
        String from = dateFrom == null ? "" : dateFrom.trim();
        String to = dateTo == null ? "" : dateTo.trim();
        if (!from.isEmpty() || !to.isEmpty()) {
            path.append('?');
            if (!from.isEmpty()) path.append("date_from=").append(from);
            if (!from.isEmpty() && !to.isEmpty()) path.append('&');
            if (!to.isEmpty()) path.append("date_to=").append(to);
        }

        String url = ApiConfig.url(new SessionManager(appContext), path.toString());
        Log.i(TAG, "fetchWaiterPerformance() GET -> " + url);

        StringRequest req = new StringRequest(
                Request.Method.GET,
                url,
                body -> {
                    try {
                        Object parsed = new JSONTokener(body).nextValue();
                        JSONArray arr = (parsed instanceof JSONObject)
                                ? ((JSONObject) parsed).optJSONArray("results")
                                : (JSONArray) parsed;
                        List<WaiterPerformanceRow> out = new ArrayList<>();
                        if (arr != null) {
                            for (int i = 0; i < arr.length(); i++) {
                                WaiterPerformanceRow r =
                                        WaiterPerformanceRow.fromJson(arr.optJSONObject(i));
                                // Baris waiter_id=null ikut masuk dengan sengaja.
                                if (r != null) out.add(r);
                            }
                        }
                        Log.i(TAG, "fetchWaiterPerformance() ok rows=" + out.size());
                        cb.onSuccess(out);
                    } catch (Exception e) {
                        cb.onError(0, "Parse error: " + e.getMessage());
                    }
                },
                err -> failFrom(err, cb::onError, "fetchWaiterPerformance")
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return buildHeaders(token);
            }
        };
        dispatch(req);
    }

    // ---------------------------------------------------------- bantu

    private interface ErrorSink {
        void accept(int statusCode, @NonNull String message);
    }

    private static void failFrom(@Nullable com.android.volley.VolleyError err,
                                 @NonNull ErrorSink sink,
                                 @NonNull String what) {
        NetworkResponse nr = err != null ? err.networkResponse : null;
        int code = nr != null ? nr.statusCode : -1;
        String detail = "";
        if (nr != null && nr.data != null) {
            try {
                detail = new String(nr.data, StandardCharsets.UTF_8);
            } catch (Exception ignored) {
            }
        }
        if (detail.isEmpty()) {
            detail = err != null && err.getMessage() != null ? err.getMessage() : "Network error";
        }
        Log.e(TAG, what + " ERROR status_code=" + code + " detail=" + detail);
        sink.accept(code, detail);
    }

    /** Endpoint ini membalas array telanjang, tapi terima juga bentuk paginasi. */
    @NonNull
    private static JSONArray extractArray(@Nullable String body) throws Exception {
        if (body == null || body.trim().isEmpty()) return new JSONArray();
        Object parsed = new JSONTokener(body).nextValue();
        if (parsed instanceof JSONArray) return (JSONArray) parsed;
        if (parsed instanceof JSONObject) {
            JSONArray results = ((JSONObject) parsed).optJSONArray("results");
            if (results != null) return results;
        }
        return new JSONArray();
    }

    private void dispatch(@NonNull Request<?> req) {
        req.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, MAX_RETRIES, BACKOFF_MULT));
        req.setShouldCache(false);
        ApiClient.getInstance(appContext).add(req);
    }

    private static Map<String, String> buildHeaders(@Nullable String token) {
        Map<String, String> h = new HashMap<>();
        h.put("Accept", "application/json");
        if (token != null && !token.trim().isEmpty()) {
            h.put("Authorization", "Token " + token.trim());
        }
        return h;
    }
}
