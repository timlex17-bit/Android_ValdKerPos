package com.valdker.pos.network;

import android.content.Context;

import androidx.annotation.NonNull;

import com.android.volley.Request;
import com.android.volley.toolbox.StringRequest;
import com.valdker.pos.SessionManager;
import com.valdker.pos.models.BankLedger;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BankLedgerApi {

    public interface BankLedgerListCallback {
        void onSuccess(List<BankLedger> ledgers);
        void onError(String message);
    }

    public interface BankLedgerDetailCallback {
        void onSuccess(BankLedger ledger);
        void onError(String message);
    }

    public static void getBankLedgers(
            @NonNull Context context,
            @NonNull SessionManager sessionManager,
            @NonNull BankLedgerListCallback callback
    ) {
        String url = ApiConfig.url(sessionManager, "bank-ledgers/");

        StringRequest request = new StringRequest(
                Request.Method.GET,
                url,
                response -> {
                    try {
                        callback.onSuccess(parseLedgerList(response));
                    } catch (Exception e) {
                        callback.onError("Invalid bank ledger response");
                    }
                },
                error -> callback.onError(parseVolleyError(error))
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return headers(sessionManager);
            }
        };

        ApiClient.getInstance(context).add(request);
    }

    public static void getBankLedgerDetail(
            @NonNull Context context,
            @NonNull SessionManager sessionManager,
            int id,
            @NonNull BankLedgerDetailCallback callback
    ) {
        String url = ApiConfig.url(sessionManager, "bank-ledgers/" + id + "/");

        StringRequest request = new StringRequest(
                Request.Method.GET,
                url,
                response -> {
                    try {
                        Object parsed = new JSONTokener(response == null ? "{}" : response).nextValue();
                        if (parsed instanceof JSONObject) {
                            callback.onSuccess(BankLedger.fromJson((JSONObject) parsed));
                        } else {
                            callback.onError("Invalid bank ledger detail response");
                        }
                    } catch (Exception e) {
                        callback.onError("Invalid bank ledger detail response");
                    }
                },
                error -> callback.onError(parseVolleyError(error))
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return headers(sessionManager);
            }
        };

        ApiClient.getInstance(context).add(request);
    }

    private static List<BankLedger> parseLedgerList(String response) throws Exception {
        List<BankLedger> ledgers = new ArrayList<>();
        Object parsed = new JSONTokener(response == null ? "[]" : response).nextValue();

        if (parsed instanceof JSONArray) {
            appendLedgers((JSONArray) parsed, ledgers);
            return ledgers;
        }

        if (parsed instanceof JSONObject) {
            JSONObject obj = (JSONObject) parsed;
            JSONArray results = obj.optJSONArray("results");
            if (results == null) {
                results = obj.optJSONArray("data");
            }
            appendLedgers(results, ledgers);
        }

        return ledgers;
    }

    private static void appendLedgers(JSONArray arr, List<BankLedger> ledgers) {
        if (arr == null) return;

        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.optJSONObject(i);
            if (obj != null) {
                ledgers.add(BankLedger.fromJson(obj));
            }
        }
    }

    private static Map<String, String> headers(SessionManager sessionManager) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");

        String token = sessionManager.getToken();
        if (token != null && !token.trim().isEmpty()) {
            headers.put("Authorization", "Token " + token.trim());
        }

        return headers;
    }

    private static String parseVolleyError(com.android.volley.VolleyError error) {
        if (error == null) return "Network error";

        if (error.networkResponse != null) {
            String body = "";
            try {
                body = new String(error.networkResponse.data, StandardCharsets.UTF_8).trim();
            } catch (Exception ignored) {
            }

            if (looksLikeHtml(body)) {
                return "Server error. Please check backend log.";
            }

            if (!body.isEmpty()) {
                return "HTTP " + error.networkResponse.statusCode + ": " + body;
            }

            return "HTTP " + error.networkResponse.statusCode;
        }

        if (error.getMessage() != null && looksLikeHtml(error.getMessage())) {
            return "Server error. Please check backend log.";
        }

        return error.getMessage() != null ? error.getMessage() : "Network error";
    }

    private static boolean looksLikeHtml(String value) {
        if (value == null) return false;
        String lower = value.trim().toLowerCase();
        return lower.startsWith("<!doctype html")
                || lower.startsWith("<html")
                || lower.contains("<body")
                || lower.contains("</html>");
    }
}
