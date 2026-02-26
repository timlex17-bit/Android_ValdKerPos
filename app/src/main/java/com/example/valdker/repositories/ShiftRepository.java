package com.example.valdker.repositories;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.example.valdker.SessionManager;
import com.example.valdker.models.Shift;
import com.example.valdker.network.ApiClient;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class ShiftRepository {

    public interface CurrentCallback {
        void onSuccess(boolean open, Shift shift);
        void onError(@NonNull String message);
    }

    public interface OpenCallback {
        void onSuccess(@NonNull Shift shift);
        void onError(int statusCode, @NonNull String message);
    }

    public interface SimpleCallback {
        void onSuccess();
        void onError(@NonNull String message);
    }

    private final Context ctx;
    private final SessionManager session;

    public ShiftRepository(@NonNull Context ctx, @NonNull SessionManager session) {
        this.ctx = ctx.getApplicationContext();
        this.session = session;
    }

    private String baseUrl() {
        return "https://valdker.onrender.com/api/";
    }

    private Map<String, String> authHeaders() {
        Map<String, String> h = new HashMap<>();
        h.put("Accept", "application/json");
        h.put("Content-Type", "application/json");
        String token = session.getToken();
        if (token != null && !token.trim().isEmpty()) {
            h.put("Authorization", "Token " + token.trim());
        }
        return h;
    }

    private static String parseVolleyErrorMessage(com.android.volley.VolleyError err) {
        try {
            if (err.networkResponse != null && err.networkResponse.data != null) {
                String body = new String(err.networkResponse.data, StandardCharsets.UTF_8);
                return body;
            }
        } catch (Exception ignored) {}
        return (err.getMessage() != null) ? err.getMessage() : "unknown";
    }

    private String withShop(String url, @Nullable Integer shopId) {
        if (shopId == null || shopId <= 0) return url;
        if (url.contains("?")) return url + "&shop_id=" + shopId;
        return url + "?shop_id=" + shopId;
    }

    // ============================================================
    // CURRENT
    // ============================================================
    public void getCurrent(@NonNull CurrentCallback cb) {
        // backward compatible
        getCurrent(null, cb);
    }

    public void getCurrent(@Nullable Integer shopId, @NonNull CurrentCallback cb) {
        String url = withShop(baseUrl() + "shifts/current/", shopId);

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                res -> {
                    boolean open = res.optBoolean("open", false);
                    JSONObject shiftObj = res.optJSONObject("shift");
                    Shift shift = (shiftObj != null) ? Shift.fromJson(shiftObj) : null;
                    cb.onSuccess(open, shift);
                },
                err -> cb.onError("Gagal cek shift: " + parseVolleyErrorMessage(err))
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return authHeaders();
            }
        };

        req.setTag("SHIFT");
        ApiClient.getInstance(ctx).add(req);
    }

    // ============================================================
    // OPEN
    // ============================================================
    public void openShift(@NonNull String openingCash, @NonNull String note, @NonNull OpenCallback cb) {
        openShift(null, openingCash, note, cb);
    }

    public void openShift(@Nullable Integer shopId, @NonNull String openingCash, @NonNull String note, @NonNull OpenCallback cb) {
        String url = withShop(baseUrl() + "shifts/open/", shopId);

        JSONObject body = new JSONObject();
        try {
            body.put("opening_cash", openingCash);
            body.put("note", note);
        } catch (Exception ignored) {}

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.POST,
                url,
                body,
                res -> {
                    JSONObject shiftObj = res.optJSONObject("shift");
                    Shift shift = (shiftObj != null) ? Shift.fromJson(shiftObj) : null;
                    if (shift == null || shift.id <= 0) {
                        cb.onError(0, "Open shift gagal: shift invalid.");
                        return;
                    }

                    session.setShiftOpen(true);
                    session.setShiftId(shift.id);
                    session.setOpeningCash(
                            (shift.opening_cash == null || shift.opening_cash.trim().isEmpty())
                                    ? "0.00"
                                    : shift.opening_cash
                    );

                    cb.onSuccess(shift);
                },
                err -> {
                    int code = (err.networkResponse != null) ? err.networkResponse.statusCode : 0;
                    String msg = parseVolleyErrorMessage(err);

                    // ✅ kalau 409 shift sudah OPEN — coba ambil shift dari body
                    if (code == 409 && err.networkResponse != null && err.networkResponse.data != null) {
                        try {
                            String bodyStr = new String(err.networkResponse.data, StandardCharsets.UTF_8);
                            JSONObject obj = new JSONObject(bodyStr);
                            JSONObject shiftObj = obj.optJSONObject("shift");
                            Shift shift = (shiftObj != null) ? Shift.fromJson(shiftObj) : null;
                            if (shift != null && shift.id > 0) {
                                session.setShiftOpen(true);
                                session.setShiftId(shift.id);
                                session.setOpeningCash(
                                        (shift.opening_cash == null || shift.opening_cash.trim().isEmpty())
                                                ? "0.00"
                                                : shift.opening_cash
                                );
                                cb.onSuccess(shift); // treat as success
                                return;
                            }
                        } catch (Exception ignored) {}
                    }

                    cb.onError(code, msg);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return authHeaders();
            }
        };

        req.setTag("SHIFT");
        ApiClient.getInstance(ctx).add(req);
    }

    // ============================================================
    // CLOSE
    // ============================================================
    public void closeShift(@NonNull String closingCash, @NonNull String note, @NonNull SimpleCallback cb) {
        closeShift(null, closingCash, note, cb);
    }

    public void closeShift(@Nullable Integer shopId, @NonNull String closingCash, @NonNull String note, @NonNull SimpleCallback cb) {
        String url = withShop(baseUrl() + "shifts/close/", shopId);

        JSONObject body = new JSONObject();
        try {
            body.put("closing_cash", closingCash);
            body.put("note", note);
        } catch (Exception ignored) {}

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.POST,
                url,
                body,
                res -> cb.onSuccess(),
                err -> cb.onError("Gagal tutup shift: " + parseVolleyErrorMessage(err))
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return authHeaders();
            }
        };

        req.setTag("SHIFT");
        ApiClient.getInstance(ctx).add(req);
    }
}