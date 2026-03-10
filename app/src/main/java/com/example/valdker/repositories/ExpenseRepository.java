package com.example.valdker.repositories;

import android.content.Context;

import androidx.annotation.NonNull;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.example.valdker.SessionManager;
import com.example.valdker.models.Expense;
import com.example.valdker.network.ApiClient;
import com.example.valdker.network.ApiConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExpenseRepository {

    public interface ListCallback {
        void onSuccess(@NonNull List<Expense> list);
        void onError(int statusCode, @NonNull String message);
    }

    public interface ItemCallback {
        void onSuccess(@NonNull Expense expense);
        void onError(int statusCode, @NonNull String message);
    }

    public interface SimpleCallback {
        void onSuccess();
        void onError(int statusCode, @NonNull String message);
    }

    private static final String ENDPOINT_EXPENSES = "api/expenses/";

    // Network tuning (aligned with other repositories)
    private static final int TIMEOUT_MS = 20000;
    private static final int MAX_RETRIES = 1;
    private static final float BACKOFF_MULT = 1.2f;

    private final Context context;
    private final SessionManager session;

    public ExpenseRepository(@NonNull Context ctx) {
        this.context = ctx.getApplicationContext();
        this.session = new SessionManager(this.context);
    }

    public void fetchExpenses(@NonNull String token, @NonNull ListCallback cb) {
        final String expensesUrl = ApiConfig.url(session, ENDPOINT_EXPENSES);

        JsonArrayRequest req = new JsonArrayRequest(
                Request.Method.GET,
                expensesUrl,
                null,
                (JSONArray res) -> {
                    try {
                        List<Expense> out = new ArrayList<>();
                        for (int i = 0; i < res.length(); i++) {
                            JSONObject o = res.optJSONObject(i);
                            if (o == null) continue;
                            Expense e = parseExpense(o);
                            if (e != null) out.add(e);
                        }
                        cb.onSuccess(out);
                    } catch (Exception e) {
                        cb.onError(0, "Parse error: " + e.getMessage());
                    }
                },
                err -> {
                    int status = (err.networkResponse != null) ? err.networkResponse.statusCode : -1;
                    String msg = buildVolleyErrorMessage(err.networkResponse, err);
                    cb.onError(status, msg);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return authHeaders(token, false);
            }
        };

        req.setTag("expenses_list");
        req.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, MAX_RETRIES, BACKOFF_MULT));
        req.setShouldCache(false);

        ApiClient.getInstance(context).add(req);
    }

    public void createExpense(@NonNull String token, @NonNull Expense payload, @NonNull ItemCallback cb) {
        final String expensesUrl = ApiConfig.url(session, ENDPOINT_EXPENSES);

        JSONObject body = new JSONObject();
        try {
            body.put("name", payload.name);
            body.put("note", payload.note != null ? payload.note : "");
            body.put("amount", payload.amount); // "20.00"
            body.put("date", payload.date);     // "YYYY-MM-DD"
            body.put("time", payload.time);     // "HH:mm:ss"
        } catch (Exception e) {
            cb.onError(0, "Build body error: " + e.getMessage());
            return;
        }

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.POST,
                expensesUrl,
                body,
                res -> {
                    Expense e = parseExpense(res);
                    if (e == null) {
                        cb.onError(0, "Invalid response");
                        return;
                    }
                    cb.onSuccess(e);
                },
                err -> {
                    int status = (err.networkResponse != null) ? err.networkResponse.statusCode : -1;
                    String msg = buildVolleyErrorMessage(err.networkResponse, err);
                    cb.onError(status, msg);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return authHeaders(token, true);
            }
        };

        req.setTag("expense_create");
        req.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, MAX_RETRIES, BACKOFF_MULT));
        req.setShouldCache(false);

        ApiClient.getInstance(context).add(req);
    }

    public void updateExpense(@NonNull String token, int id, @NonNull Expense payload, @NonNull ItemCallback cb) {
        final String url = ApiConfig.url(session, ENDPOINT_EXPENSES + id + "/");

        JSONObject body = new JSONObject();
        try {
            body.put("name", payload.name);
            body.put("note", payload.note != null ? payload.note : "");
            body.put("amount", payload.amount);
            body.put("date", payload.date);
            body.put("time", payload.time);
        } catch (Exception e) {
            cb.onError(0, "Build body error: " + e.getMessage());
            return;
        }

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.PUT,
                url,
                body,
                res -> {
                    Expense e = parseExpense(res);
                    if (e == null) {
                        cb.onError(0, "Invalid response");
                        return;
                    }
                    cb.onSuccess(e);
                },
                err -> {
                    int status = (err.networkResponse != null) ? err.networkResponse.statusCode : -1;
                    String msg = buildVolleyErrorMessage(err.networkResponse, err);
                    cb.onError(status, msg);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return authHeaders(token, true);
            }
        };

        req.setTag("expense_update");
        req.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, MAX_RETRIES, BACKOFF_MULT));
        req.setShouldCache(false);

        ApiClient.getInstance(context).add(req);
    }

    public void deleteExpense(@NonNull String token, int id, @NonNull SimpleCallback cb) {
        final String url = ApiConfig.url(session, ENDPOINT_EXPENSES + id + "/");

        StringRequest req = new StringRequest(
                Request.Method.DELETE,
                url,
                response -> cb.onSuccess(),
                err -> {
                    if (err != null && err.networkResponse != null && err.networkResponse.statusCode == 204) {
                        cb.onSuccess();
                        return;
                    }

                    int status = (err != null && err.networkResponse != null) ? err.networkResponse.statusCode : -1;

                    String msg = buildVolleyErrorMessage((err != null) ? err.networkResponse : null, err);
                    cb.onError(status, msg);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return authHeaders(token, false);
            }

            @Override
            protected Response<String> parseNetworkResponse(NetworkResponse response) {
                if (response != null && (response.statusCode == 200 || response.statusCode == 202 || response.statusCode == 204)) {
                    return Response.success("", HttpHeaderParser.parseCacheHeaders(response));
                }
                return super.parseNetworkResponse(response);
            }
        };

        req.setTag("expense_delete");
        req.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, MAX_RETRIES, BACKOFF_MULT));
        req.setShouldCache(false);

        ApiClient.getInstance(context).add(req);
    }

    private Map<String, String> authHeaders(@NonNull String token, boolean json) {
        Map<String, String> h = new HashMap<>();
        h.put("Accept", "application/json");
        h.put("Authorization", "Token " + token.trim());
        if (json) h.put("Content-Type", "application/json");
        return h;
    }

    private Expense parseExpense(JSONObject o) {
        if (o == null) return null;

        Expense e = new Expense();
        e.id = o.optInt("id", 0);
        e.name = o.optString("name", "");
        e.note = o.optString("note", "");
        e.amount = o.optString("amount", "0.00");
        e.date = o.optString("date", "");
        e.time = o.optString("time", "");
        return e;
    }

    private static String buildVolleyErrorMessage(NetworkResponse nr, Throwable err) {
        if (nr == null) {
            String m = (err != null) ? err.getMessage() : null;
            return (m != null && !m.trim().isEmpty()) ? m : "Network error";
        }
        try {
            if (nr.data != null) {
                String body = new String(nr.data, StandardCharsets.UTF_8).trim();
                if (body.length() > 250) body = body.substring(0, 250) + "...";
                return "HTTP " + nr.statusCode + " - " + body;
            }
        } catch (Exception ignored) {
        }
        return "HTTP " + nr.statusCode;
    }
}