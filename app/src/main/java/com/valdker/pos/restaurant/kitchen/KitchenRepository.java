package com.valdker.pos.restaurant.kitchen;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.JsonObjectRequest;
import com.valdker.pos.SessionManager;
import com.valdker.pos.network.ApiClient;
import com.valdker.pos.network.ApiConfig;

import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Endpoint papan dapur.
 *
 * <p>Digerbangi kunci modul {@code "kitchen_display"} yang
 * {@code business_types=[RESTAURANT]}; shop non-restoran mendapat 403. Seperti
 * modul restoran lain, gerbang itu diperiksa pemanggil lewat
 * {@link SessionManager#canAccessModule(String)} supaya layarnya tidak pernah
 * terbuka - 403 hanya jaring pengaman.
 */
public class KitchenRepository {

    /** Kunci modul; sama persis dengan yang dipakai backend. */
    public static final String MODULE_KITCHEN_DISPLAY = "kitchen_display";

    private static final String TAG = "KITCHEN_REPO";
    private static final int TIMEOUT_MS = 15000;
    /**
     * Tanpa retry. Papan ini polling terus; sebuah putaran yang gagal cukup
     * dibiarkan gagal dan diulang oleh putaran berikutnya beberapa detik lagi.
     * Retry di dalam satu putaran hanya menumpuk request saat jaringan buruk.
     */
    private static final int MAX_RETRIES = 0;
    private static final float BACKOFF_MULT = 1f;

    /** Hasil satu putaran polling. */
    public interface BoardCallback {
        /**
         * @param etag nilai ETag yang harus dikirim balik sebagai
         *             {@code If-None-Match} pada putaran berikutnya. Wajib
         *             disimpan dari respons TERAKHIR, bukan dari yang pertama.
         */
        void onBoard(@NonNull KitchenBoard board, @Nullable String etag);

        /**
         * Server menjawab {@code 304 Not Modified}: tidak ada perubahan sejak
         * ETag yang dikirim. Tidak ada body sama sekali - pemanggil
         * mempertahankan papan yang sedang tampil.
         */
        void onNotModified(@Nullable String etag);

        void onError(int statusCode, @NonNull String message);
    }

    public interface StatusCallback {
        /** @param item item hasil perubahan, bentuknya sama seperti entri papan. */
        void onSuccess(@NonNull KitchenBoard.Item item);

        /**
         * @param statusCode 400 berarti transisinya ditolak - biasanya karena
         *                   perangkat lain sudah memindahkan item ini lebih
         *                   dulu. {@code message} sudah berupa teks yang bisa
         *                   ditampilkan.
         */
        void onError(int statusCode, @NonNull String message);
    }

    private final Context appContext;

    public KitchenRepository(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

    // ------------------------------------------------------------- papan

    /**
     * Mengambil papan dapur.
     *
     * @param ifNoneMatch ETag dari respons sebelumnya, atau null pada muat
     *                    pertama. Dikirim apa adanya termasuk prefix
     *                    {@code W/} dan tanda kutipnya.
     */
    public void fetchBoard(@Nullable String token,
                           @Nullable String ifNoneMatch,
                           @NonNull BoardCallback cb) {
        String url = ApiConfig.url(new SessionManager(appContext), "api/restaurant/kitchen/");

        BoardRequest req = new BoardRequest(
                url,
                token,
                ifNoneMatch,
                result -> {
                    if (result.notModified) {
                        Log.d(TAG, "fetchBoard() 304 not modified");
                        cb.onNotModified(result.etag);
                    } else {
                        KitchenBoard board = result.board != null ? result.board : KitchenBoard.EMPTY;
                        Log.d(TAG, "fetchBoard() 200 orders=" + board.orders.size()
                                + " items=" + board.itemCount() + " etag=" + result.etag);
                        cb.onBoard(board, result.etag);
                    }
                },
                err -> fail(err, cb::onError, "fetchBoard"));

        dispatch(req);
    }

    // ------------------------------------------------------------ status

    /**
     * Mengubah status satu item.
     *
     * <p>Server membaca ulang status saat ini di dalam {@code select_for_update()},
     * jadi dua perangkat yang menekan tombol yang sama hampir bersamaan tidak
     * bisa dua-duanya berhasil: yang kedua mendapat 400 karena status sudah
     * berpindah. Klien tidak mencoba mencegah itu - klien menampilkannya.
     */
    public void setItemStatus(@Nullable String token,
                              long itemId,
                              @NonNull String newStatus,
                              @NonNull StatusCallback cb) {
        String url = ApiConfig.url(new SessionManager(appContext),
                "api/restaurant/kitchen/items/" + itemId + "/status/");

        JSONObject body = new JSONObject();
        try {
            body.put("kitchen_status", KitchenStatus.normalize(newStatus));
        } catch (Exception e) {
            cb.onError(0, "Unable to build status body: " + e.getMessage());
            return;
        }

        Log.i(TAG, "setItemStatus() POST -> " + url + " kitchen_status=" + newStatus);

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.POST,
                url,
                body,
                response -> {
                    KitchenBoard.Item item = KitchenBoard.Item.fromJson(response);
                    if (item == null) {
                        cb.onError(0, "Unreadable item in status response");
                        return;
                    }
                    cb.onSuccess(item);
                },
                err -> fail(err, cb::onError, "setItemStatus")
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return buildHeaders(token, null);
            }
        };

        dispatch(req);
    }

    // --------------------------------------------------------- request

    /** Hasil mentah satu GET papan. */
    static final class BoardResult {
        final boolean notModified;
        @Nullable final String etag;
        @Nullable final KitchenBoard board;

        BoardResult(boolean notModified, @Nullable String etag, @Nullable KitchenBoard board) {
            this.notModified = notModified;
            this.etag = etag;
            this.board = board;
        }
    }

    /**
     * Request khusus, bukan {@link com.android.volley.toolbox.StringRequest},
     * karena dua hal yang tidak bisa dilakukan request bawaan:
     *
     * <ol>
     *   <li><b>Membaca header {@code ETag}.</b> Callback sukses Volley hanya
     *       menerima body; header respons hilang sebelum sampai ke sana.</li>
     *   <li><b>Bertahan pada 304.</b> Ini yang paling mudah salah. Karena
     *       cache Volley dimatikan, {@code BasicNetwork} mengembalikan
     *       {@code NetworkResponse} dengan {@code data == null} untuk 304.
     *       {@code StringRequest.parseNetworkResponse()} langsung memanggil
     *       {@code new String(response.data, ...)} pada null itu, melempar
     *       NullPointerException, dan Volley mengirimkannya ke callback GALAT -
     *       jadi "tidak ada perubahan" datang menyamar sebagai kegagalan.
     *       Layar yang mengosongkan papan saat galat akan berkedip kosong
     *       setiap kali dapur sedang tenang. Di sini 304 dikenali eksplisit
     *       dan dikembalikan sebagai sukses tanpa body.</li>
     * </ol>
     */
    private static final class BoardRequest extends Request<BoardResult> {

        @Nullable private final String token;
        @Nullable private final String ifNoneMatch;
        private final Response.Listener<BoardResult> listener;

        BoardRequest(@NonNull String url,
                     @Nullable String token,
                     @Nullable String ifNoneMatch,
                     @NonNull Response.Listener<BoardResult> listener,
                     @NonNull Response.ErrorListener errorListener) {
            super(Method.GET, url, errorListener);
            this.token = token;
            this.ifNoneMatch = ifNoneMatch;
            this.listener = listener;
        }

        @Override
        public Map<String, String> getHeaders() {
            return buildHeaders(token, ifNoneMatch);
        }

        @Override
        protected Response<BoardResult> parseNetworkResponse(NetworkResponse response) {
            String etag = headerValue(response, "ETag");

            // Urutan pemeriksaan penting: 304 diputuskan dari status code dan
            // dari ketiadaan body, bukan hanya dari flag notModified milik
            // Volley - flag itu bergantung pada keberadaan entri cache, yang
            // sengaja tidak kita punya.
            boolean notModified = response.statusCode == 304
                    || response.notModified
                    || response.data == null
                    || response.data.length == 0;

            if (notModified) {
                return Response.success(new BoardResult(true, etag, null), null);
            }

            try {
                String charset = HttpHeaderParser.parseCharset(response.headers, "utf-8");
                String body = new String(response.data, charset);
                JSONObject root = new JSONObject(body);
                return Response.success(
                        new BoardResult(false, etag, KitchenBoard.fromJson(root)),
                        null);
            } catch (UnsupportedEncodingException | org.json.JSONException e) {
                return Response.error(new ParseError(e));
            }
        }

        @Override
        protected void deliverResponse(BoardResult result) {
            listener.onResponse(result);
        }

        @Nullable
        private static String headerValue(@NonNull NetworkResponse response, @NonNull String name) {
            if (response.headers == null) return null;
            for (Map.Entry<String, String> e : response.headers.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) {
                    String v = e.getValue();
                    return v == null || v.trim().isEmpty() ? null : v.trim();
                }
            }
            return null;
        }
    }

    // ---------------------------------------------------------- bantu

    private interface ErrorSink {
        void accept(int statusCode, @NonNull String message);
    }

    private static void fail(@Nullable VolleyError err,
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
        sink.accept(code, readableError(detail));
    }

    /**
     * Galat field DRF berbentuk {@code {field: [messages]}}. Papan dapur dibaca
     * dari jarak beberapa meter; menampilkan JSON mentah di sana tidak ada
     * gunanya, jadi pesan pertama diambil apa adanya.
     */
    @NonNull
    static String readableError(@Nullable String rawBody) {
        String body = rawBody == null ? "" : rawBody.trim();
        if (body.isEmpty()) return "Unknown error";
        if (!body.startsWith("{")) return body;

        try {
            JSONObject o = new JSONObject(body);
            // kitchen_status lebih dulu: itu field yang dipakai endpoint ini.
            String fromField = firstMessage(o, "kitchen_status");
            if (fromField != null) return fromField;
            for (java.util.Iterator<String> it = o.keys(); it.hasNext(); ) {
                String msg = firstMessage(o, it.next());
                if (msg != null) return msg;
            }
        } catch (Exception ignored) {
        }
        return body;
    }

    @Nullable
    private static String firstMessage(@NonNull JSONObject o, @NonNull String key) {
        if (!o.has(key)) return null;
        org.json.JSONArray arr = o.optJSONArray(key);
        if (arr != null && arr.length() > 0) {
            String first = Iso8601.safe(arr.optString(0, ""));
            return first.isEmpty() ? null : first;
        }
        String direct = Iso8601.safe(o.optString(key, ""));
        return direct.isEmpty() ? null : direct;
    }

    private void dispatch(@NonNull Request<?> req) {
        req.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, MAX_RETRIES, BACKOFF_MULT));
        // Cache Volley dimatikan: kesegaran diurus sendiri lewat ETag, dan
        // cache yang aktif akan menjawab dari salinan lama tanpa pernah
        // menyentuh jaringan.
        req.setShouldCache(false);
        ApiClient.getInstance(appContext).add(req);
    }

    @NonNull
    private static Map<String, String> buildHeaders(@Nullable String token,
                                                    @Nullable String ifNoneMatch) {
        Map<String, String> h = new HashMap<>();
        h.put("Accept", "application/json");
        if (token != null && !token.trim().isEmpty()) {
            h.put("Authorization", "Token " + token.trim());
        }
        if (ifNoneMatch != null && !ifNoneMatch.trim().isEmpty()) {
            h.put("If-None-Match", ifNoneMatch.trim());
        }
        return h;
    }

    @NonNull
    static String upper(@Nullable String s) {
        return s == null ? "" : s.trim().toUpperCase(Locale.US);
    }
}
