package com.example.valdker.repositories;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.example.valdker.SessionManager;
import com.example.valdker.models.Shift;
import com.example.valdker.network.ApiClient;
import com.example.valdker.network.ApiConfig;

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
        return ApiConfig.url(session, "api/");
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
        if (err instanceof com.android.volley.TimeoutError) return "Request timeout. Check internet.";
        if (err instanceof com.android.volley.NoConnectionError) return "No internet connection.";
        return (err.getMessage() != null) ? err.getMessage() : "unknown";
    }

    private String withShop(String url, @Nullable Integer shopId) {
        if (shopId == null || shopId <= 0) return url;
        if (url.contains("?")) return url + "&shop=" + shopId;
        return url + "?shop=" + shopId;
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
                    JSONObject shiftObj = extractShiftObject(res);
                    Shift shift = (shiftObj != null) ? Shift.fromJson(shiftObj) : null;
                    cb.onSuccess(open, shift);
                },
                err -> cb.onError("Failed check shift: " + parseVolleyErrorMessage(err))
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return authHeaders();
            }
        };

        req.setTag("SHIFT");

        req.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(
                6000,
                1,
                1.0f
        ));

        ApiClient.getInstance(ctx).add(req);
    }

    // ============================================================
    // OPEN
    // ============================================================
    public void openShift(@NonNull String openingCash, @NonNull String note, @NonNull OpenCallback cb) {
        Integer sid = session != null ? session.getShopId() : 0;
        if (sid != null && sid <= 0) sid = 1;
        openShift(sid, openingCash, note, cb);
    }

    @Nullable
    private JSONObject extractShiftObject(@NonNull JSONObject res) {
        JSONObject shiftObj = res.optJSONObject("shift");
        if (shiftObj != null) return shiftObj;

        if (res.has("id")) return res;

        shiftObj = res.optJSONObject("data");
        if (shiftObj != null) return shiftObj;

        shiftObj = res.optJSONObject("result");
        return shiftObj;
    }

    public void openShift(@Nullable Integer shopId, @NonNull String openingCash, @NonNull String note, @NonNull OpenCallback cb) {
        String url = withShop(baseUrl() + "shifts/open/", shopId);

        JSONObject body = new JSONObject();
        try {
            if (shopId != null && shopId > 0) body.put("shop", shopId);
            body.put("opening_cash", openingCash);
            body.put("note", note);
        } catch (Exception ignored) {
        }

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.POST,
                url,
                body,
                res -> {
                    JSONObject shiftObj = extractShiftObject(res);
                    Shift shift = (shiftObj != null) ? Shift.fromJson(shiftObj) : null;

                    if (shift == null || shift.id <= 0) {
                        Log.w("SHIFT", "Open shift unexpected: " + res);
                        cb.onError(0, "Open shift failed: response doesn't match.");
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

                    if (code == 409 && err.networkResponse != null && err.networkResponse.data != null) {
                        try {
                            String bodyStr = new String(err.networkResponse.data, StandardCharsets.UTF_8);
                            JSONObject obj = new JSONObject(bodyStr);

                            JSONObject shiftObj = extractShiftObject(obj);
                            Shift shift = (shiftObj != null) ? Shift.fromJson(shiftObj) : null;

                            if (shift != null && shift.id > 0) {
                                session.setShiftOpen(true);
                                session.setShiftId(shift.id);
                                session.setOpeningCash(
                                        (shift.opening_cash == null || shift.opening_cash.trim().isEmpty())
                                                ? "0.00"
                                                : shift.opening_cash
                                );
                                cb.onSuccess(shift);
                                return;
                            }

                            Log.w("SHIFT", "409 but shift not parseable: " + obj);

                        } catch (Exception e) {
                            Log.e("SHIFT", "409 parse error: " + e.getMessage(), e);
                        }
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
        req.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(
                8000,
                0,
                1.0f
        ));

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
        } catch (Exception ignored) {
        }

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.POST,
                url,
                body,
                res -> cb.onSuccess(),
                err -> cb.onError("Failed to close shift: " + parseVolleyErrorMessage(err))
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return authHeaders();
            }
        };

        req.setTag("SHIFT");

        req.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(
                8000,
                0,
                1.0f
        ));

        ApiClient.getInstance(ctx).add(req);
    }
}