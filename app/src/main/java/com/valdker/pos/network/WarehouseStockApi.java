package com.valdker.pos.network;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.valdker.pos.BuildConfig;
import com.valdker.pos.SessionManager;
import com.valdker.pos.models.WarehouseStock;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WarehouseStockApi {

    public interface WarehouseStockListCallback {
        void onSuccess(List<WarehouseStock> stocks);
        void onError(String message);
    }

    public interface WarehouseStockCallback {
        void onSuccess(WarehouseStock stock);
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

    public static void getWarehouseStocks(
            @NonNull Context context,
            @NonNull SessionManager sessionManager,
            @NonNull WarehouseStockListCallback callback
    ) {
        String url = ApiConfig.url(sessionManager, "api/warehouse-stocks/");

        StringRequest request = new StringRequest(
                Request.Method.GET,
                url,
                response -> {
                    try {
                        JSONArray items = extractResultsArray(response);
                        List<WarehouseStock> stocks = new ArrayList<>();

                        for (int i = 0; i < items.length(); i++) {
                            JSONObject obj = items.optJSONObject(i);
                            if (obj != null) {
                                stocks.add(WarehouseStock.fromJson(obj));
                            }
                        }

                        callback.onSuccess(stocks);
                    } catch (Exception e) {
                        callback.onError("Invalid warehouse stock response");
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

    public static void createWarehouseStock(
            @NonNull Context context,
            @NonNull SessionManager sessionManager,
            @NonNull WarehouseStock stock,
            @NonNull WarehouseStockCallback callback
    ) {
        String url = ApiConfig.url(sessionManager, "api/warehouse-stocks/");

        JSONObject body;
        try {
            body = stock.toJson();
        } catch (JSONException e) {
            callback.onError("Invalid warehouse stock data");
            return;
        }

        if (BuildConfig.DEBUG) {
            Log.d("WAREHOUSE_STOCK_POST", "payload=" + body);
        }

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                url,
                body,
                response -> callback.onSuccess(WarehouseStock.fromJson(response)),
                error -> callback.onError(parseVolleyError(error))
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return headers(sessionManager);
            }
        };

        ApiClient.getInstance(context).add(request);
    }

    public static void updateWarehouseStock(
            @NonNull Context context,
            @NonNull SessionManager sessionManager,
            int id,
            @NonNull WarehouseStock stock,
            @NonNull WarehouseStockCallback callback
    ) {
        String url = ApiConfig.url(sessionManager, "api/warehouse-stocks/" + id + "/");

        JSONObject body;
        try {
            body = stock.toJson();
        } catch (JSONException e) {
            callback.onError("Invalid warehouse stock data");
            return;
        }

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.PUT,
                url,
                body,
                response -> callback.onSuccess(WarehouseStock.fromJson(response)),
                error -> callback.onError(parseVolleyError(error))
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return headers(sessionManager);
            }
        };

        ApiClient.getInstance(context).add(request);
    }

    public static void deleteWarehouseStock(
            @NonNull Context context,
            @NonNull SessionManager sessionManager,
            int id,
            @NonNull DeleteCallback callback
    ) {
        String url = ApiConfig.url(sessionManager, "api/warehouse-stocks/" + id + "/");

        StringRequest request = new StringRequest(
                Request.Method.DELETE,
                url,
                response -> callback.onSuccess(),
                error -> {
                    if (error.networkResponse != null && error.networkResponse.statusCode == 204) {
                        callback.onSuccess();
                    } else {
                        callback.onError(parseVolleyError(error));
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

    private static String parseVolleyError(com.android.volley.VolleyError error) {
        if (error == null) return "Unknown error";

        if (error.networkResponse != null) {
            int code = error.networkResponse.statusCode;

            String body = "";
            try {
                body = new String(error.networkResponse.data, "UTF-8");
            } catch (Exception ignored) {
            }

            if (body != null && !body.trim().isEmpty()) {
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
            appendErrorField(builder, obj, "item_type");
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
}
