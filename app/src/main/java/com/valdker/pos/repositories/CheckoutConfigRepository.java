package com.valdker.pos.repositories;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonArrayRequest;
import com.valdker.pos.SessionManager;
import com.valdker.pos.network.ApiClient;
import com.valdker.pos.network.ApiConfig;
import com.valdker.pos.ui.checkout.BankAccountItem;
import com.valdker.pos.ui.checkout.PaymentMethodItem;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CheckoutConfigRepository {

    public interface PaymentMethodsCallback {
        void onSuccess(@NonNull List<PaymentMethodItem> items);
        void onError(int statusCode, @NonNull String message);
    }

    public interface BankAccountsCallback {
        void onSuccess(@NonNull List<BankAccountItem> items);
        void onError(int statusCode, @NonNull String message);
    }

    private static final int TIMEOUT_MS = 20000;
    private static final int MAX_RETRIES = 1;
    private static final float BACKOFF_MULT = 1.2f;

    private static final String ENDPOINT_PAYMENT_METHODS = "api/payment-methods/";
    private static final String ENDPOINT_BANK_ACCOUNTS = "api/bank-accounts/";

    private final Context appContext;

    public CheckoutConfigRepository(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

    public void fetchPaymentMethods(@Nullable String token, @NonNull PaymentMethodsCallback cb) {
        String url = ApiConfig.url(new SessionManager(appContext), ENDPOINT_PAYMENT_METHODS);

        JsonArrayRequest req = new JsonArrayRequest(
                Request.Method.GET,
                url,
                null,
                res -> {
                    try {
                        List<PaymentMethodItem> out = new ArrayList<>();
                        for (int i = 0; i < res.length(); i++) {
                            JSONObject o = res.optJSONObject(i);
                            if (o == null) continue;

                            PaymentMethodItem item = new PaymentMethodItem();
                            item.id = o.optInt("id", 0);
                            item.name = o.optString("name", "");
                            item.code = o.optString("code", "");
                            item.payment_type = o.optString("payment_type", "");
                            item.requires_bank_account = o.optBoolean("requires_bank_account", false);
                            item.is_active = o.optBoolean("is_active", true);
                            item.note = o.optString("note", "");
                            out.add(item);
                        }
                        cb.onSuccess(out);
                    } catch (Exception e) {
                        cb.onError(0, "Parse error: " + e.getMessage());
                    }
                },
                err -> {
                    int code = err.networkResponse != null ? err.networkResponse.statusCode : -1;
                    cb.onError(code, buildError(err.networkResponse));
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return buildHeaders(token);
            }
        };

        req.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, MAX_RETRIES, BACKOFF_MULT));
        req.setShouldCache(false);
        ApiClient.getInstance(appContext).add(req);
    }

    public void fetchBankAccounts(@Nullable String token, @NonNull BankAccountsCallback cb) {
        String url = ApiConfig.url(new SessionManager(appContext), ENDPOINT_BANK_ACCOUNTS);

        JsonArrayRequest req = new JsonArrayRequest(
                Request.Method.GET,
                url,
                null,
                res -> {
                    try {
                        List<BankAccountItem> out = new ArrayList<>();
                        for (int i = 0; i < res.length(); i++) {
                            JSONObject o = res.optJSONObject(i);
                            if (o == null) continue;

                            BankAccountItem item = new BankAccountItem();
                            item.id = o.optInt("id", 0);
                            item.name = o.optString("name", "");
                            item.bank_name = o.optString("bank_name", "");
                            item.account_number = o.optString("account_number", "");
                            item.account_holder = o.optString("account_holder", "");
                            item.account_type = o.optString("account_type", "");
                            item.current_balance = o.optString("current_balance", "0.00");
                            item.is_active = o.optBoolean("is_active", true);
                            out.add(item);
                        }
                        cb.onSuccess(out);
                    } catch (Exception e) {
                        cb.onError(0, "Parse error: " + e.getMessage());
                    }
                },
                err -> {
                    int code = err.networkResponse != null ? err.networkResponse.statusCode : -1;
                    cb.onError(code, buildError(err.networkResponse));
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return buildHeaders(token);
            }
        };

        req.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, MAX_RETRIES, BACKOFF_MULT));
        req.setShouldCache(false);
        ApiClient.getInstance(appContext).add(req);
    }

    private Map<String, String> buildHeaders(@Nullable String token) {
        Map<String, String> h = new HashMap<>();
        h.put("Accept", "application/json");
        if (token != null && !token.trim().isEmpty()) {
            h.put("Authorization", "Token " + token.trim());
        }
        return h;
    }

    private String buildError(@Nullable com.android.volley.NetworkResponse nr) {
        if (nr == null) return "Network error";
        try {
            if (nr.data != null) {
                String body = new String(nr.data, StandardCharsets.UTF_8).trim();
                if (body.length() > 250) body = body.substring(0, 250) + "...";
                return "HTTP " + nr.statusCode + " - " + body;
            }
        } catch (Exception ignored) {}
        return "HTTP " + nr.statusCode;
    }
}