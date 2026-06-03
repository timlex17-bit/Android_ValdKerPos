package com.valdker.pos.network;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.valdker.pos.BuildConfig;
import com.valdker.pos.R;
import com.valdker.pos.SessionManager;
import com.valdker.pos.models.StockTransfer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

public class StockTransferApi {

    public interface StockTransferListCallback {
        void onSuccess(List<StockTransfer> transfers);
        void onError(String message);
    }

    public interface StockTransferCallback {
        void onSuccess(StockTransfer transfer);
        void onError(String message);
    }

    public interface DeleteCallback {
        void onSuccess();
        void onError(String message);
    }

    private static Map<String, String> headers(SessionManager sessionManager) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");

        String token = sessionManager.getToken();
        if (token != null && !token.trim().isEmpty()) {
            headers.put("Authorization", "Token " + token);
        }

        return headers;
    }

    public static void getStockTransfers(
            @NonNull Context context,
            @NonNull SessionManager sessionManager,
            @NonNull StockTransferListCallback callback
    ) {
        String url = ApiConfig.url(sessionManager, "api/stock-transfers/");

        StringRequest request = new StringRequest(
                Request.Method.GET,
                url,
                response -> {
                    try {
                        JSONArray items = extractResultsArray(response);
                        List<StockTransfer> transfers = new ArrayList<>();

                        for (int i = 0; i < items.length(); i++) {
                            JSONObject obj = items.optJSONObject(i);
                            if (obj != null) {
                                transfers.add(StockTransfer.fromJson(obj));
                            }
                        }

                        callback.onSuccess(transfers);
                    } catch (Exception e) {
                        callback.onError("Invalid stock transfer response");
                    }
                },
                error -> callback.onError(parseVolleyError(context, error))
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return headers(sessionManager);
            }
        };

        ApiClient.getInstance(context).add(request);
    }

    private static JSONArray extractResultsArray(String response) throws JSONException {
        Object parsed = new JSONTokener(response == null ? "[]" : response).nextValue();

        if (parsed instanceof JSONArray) {
            return (JSONArray) parsed;
        }

        if (parsed instanceof JSONObject) {
            JSONArray results = ((JSONObject) parsed).optJSONArray("results");
            return results != null ? results : new JSONArray();
        }

        return new JSONArray();
    }

    public static void createStockTransfer(
            @NonNull Context context,
            @NonNull SessionManager sessionManager,
            @NonNull StockTransfer transfer,
            @NonNull StockTransferCallback callback
    ) {
        String url = ApiConfig.url(sessionManager, "api/stock-transfers/");

        JSONObject body;
        try {
            body = transfer.toJson();
        } catch (JSONException e) {
            callback.onError("Invalid stock transfer data");
            return;
        }

        if (BuildConfig.DEBUG) {
            Log.d("STOCK_TRANSFER_POST", "payload=" + body);
        }

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                url,
                body,
                response -> callback.onSuccess(StockTransfer.fromJson(response)),
                error -> callback.onError(parseVolleyError(context, error))
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return headers(sessionManager);
            }
        };

        ApiClient.getInstance(context).add(request);
    }

    public static void updateStockTransfer(
            @NonNull Context context,
            @NonNull SessionManager sessionManager,
            int id,
            @NonNull StockTransfer transfer,
            @NonNull StockTransferCallback callback
    ) {
        String url = ApiConfig.url(sessionManager, "api/stock-transfers/" + id + "/");

        JSONObject body;
        try {
            body = transfer.toJson();
        } catch (JSONException e) {
            callback.onError("Invalid stock transfer data");
            return;
        }

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.PUT,
                url,
                body,
                response -> callback.onSuccess(StockTransfer.fromJson(response)),
                error -> callback.onError(parseVolleyError(context, error))
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return headers(sessionManager);
            }
        };

        ApiClient.getInstance(context).add(request);
    }

    public static void completeStockTransfer(
            @NonNull Context context,
            @NonNull SessionManager sessionManager,
            int id,
            @NonNull StockTransferCallback callback
    ) {
        String url = ApiConfig.url(sessionManager, "api/stock-transfers/" + id + "/complete/");

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                url,
                new JSONObject(),
                response -> callback.onSuccess(StockTransfer.fromJson(response)),
                error -> callback.onError(parseVolleyError(context, error))
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return headers(sessionManager);
            }
        };

        ApiClient.getInstance(context).add(request);
    }

    public static void cancelStockTransfer(
            @NonNull Context context,
            @NonNull SessionManager sessionManager,
            int id,
            @NonNull StockTransferCallback callback
    ) {
        String url = ApiConfig.url(sessionManager, "api/stock-transfers/" + id + "/cancel/");

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                url,
                new JSONObject(),
                response -> callback.onSuccess(StockTransfer.fromJson(response)),
                error -> callback.onError(parseVolleyError(context, error))
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return headers(sessionManager);
            }
        };

        ApiClient.getInstance(context).add(request);
    }

    public static void deleteStockTransfer(
            @NonNull Context context,
            @NonNull SessionManager sessionManager,
            int id,
            @NonNull DeleteCallback callback
    ) {
        String url = ApiConfig.url(sessionManager, "api/stock-transfers/" + id + "/");

        StringRequest request = new StringRequest(
                Request.Method.DELETE,
                url,
                response -> callback.onSuccess(),
                error -> {
                    if (error.networkResponse != null && error.networkResponse.statusCode == 204) {
                        callback.onSuccess();
                    } else {
                        callback.onError(parseVolleyError(context, error));
                    }
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return headers(sessionManager);
            }
        };

        ApiClient.getInstance(context).add(request);
    }

    private static String parseVolleyError(Context context, com.android.volley.VolleyError error) {
        if (error == null) return "Unknown error";

        if (error.networkResponse != null) {
            int code = error.networkResponse.statusCode;

            String body = "";
            try {
                body = new String(error.networkResponse.data, "UTF-8");
            } catch (Exception ignored) {
            }

            if (body != null && !body.trim().isEmpty()) {
                if (BuildConfig.DEBUG) {
                    Log.d("STOCK_TRANSFER_ERROR", safeLogDetail(body));
                }
                if (isProductUnitInvalidPk(body)) {
                    return context.getString(R.string.msg_selected_product_unit_invalid_reload);
                }
                String readable = extractReadableBackendError(body);
                return readable == null || readable.trim().isEmpty() ? "HTTP " + code : readable;
            }

            return "HTTP " + code;
        }

        if (error.getMessage() != null) {
            return error.getMessage();
        }

        return "Network error";
    }

    private static boolean isProductUnitInvalidPk(String body) {
        String normalized = body == null ? "" : body.toLowerCase(Locale.US);
        return normalized.contains("product_unit")
                && normalized.contains("invalid pk");
    }

    private static String extractReadableBackendError(String body) {
        try {
            Object parsed = new JSONTokener(body).nextValue();
            if (!(parsed instanceof JSONObject)) {
                return body;
            }

            JSONObject obj = (JSONObject) parsed;
            StringBuilder builder = new StringBuilder();
            appendErrorField(builder, obj, "non_field_errors");
            appendErrorField(builder, obj, "warehouse");
            appendErrorField(builder, obj, "from_warehouse");
            appendErrorField(builder, obj, "to_warehouse");
            appendErrorField(builder, obj, "product");
            appendErrorField(builder, obj, "product_unit");
            appendErrorField(builder, obj, "quantity");
            appendErrorField(builder, obj, "quantity_input");
            appendErrorField(builder, obj, "items");

            return builder.length() > 0 ? builder.toString() : body;
        } catch (Exception e) {
            return body;
        }
    }

    private static void appendErrorField(StringBuilder builder, JSONObject obj, String field) {
        if (!obj.has(field)) return;

        Object value = obj.opt(field);
        String message = errorValueToString(value);
        if (message == null || message.trim().isEmpty()) return;

        if (builder.length() > 0) {
            builder.append("\n");
        }

        if ("non_field_errors".equals(field)) {
            builder.append(message);
        } else {
            builder.append(field.replace("_", " ")).append(": ").append(message);
        }
    }

    private static String errorValueToString(Object value) {
        if (value == null || value == JSONObject.NULL) return "";

        if (value instanceof JSONArray) {
            JSONArray arr = (JSONArray) value;
            List<String> parts = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                String part = errorValueToString(arr.opt(i));
                if (part != null && !part.trim().isEmpty()) {
                    parts.add(part);
                }
            }
            return String.join(" ", parts);
        }

        if (value instanceof JSONObject) {
            JSONObject obj = (JSONObject) value;
            StringBuilder builder = new StringBuilder();
            JSONArray names = obj.names();
            if (names == null) return obj.toString();

            for (int i = 0; i < names.length(); i++) {
                String name = names.optString(i);
                String part = errorValueToString(obj.opt(name));
                if (part == null || part.trim().isEmpty()) continue;
                if (builder.length() > 0) {
                    builder.append(" ");
                }
                builder.append(name.replace("_", " ")).append(": ").append(part);
            }
            return builder.toString();
        }

        return String.valueOf(value);
    }

    @NonNull
    private static String safeLogDetail(@Nullable String value) {
        if (value == null) return "";
        String clean = value.replace('\n', ' ').replace('\r', ' ').trim();
        String lower = clean.toLowerCase(Locale.US);
        if (lower.startsWith("<!doctype") || lower.startsWith("<html") || lower.contains("<body")) {
            return "<html omitted>";
        }
        return clean.length() > 180 ? clean.substring(0, 180).trim() + "..." : clean;
    }
}
