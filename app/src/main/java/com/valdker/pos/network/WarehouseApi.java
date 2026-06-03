package com.valdker.pos.network;

import android.content.Context;

import androidx.annotation.NonNull;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.valdker.pos.SessionManager;
import com.valdker.pos.models.Warehouse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WarehouseApi {

    public interface WarehouseListCallback {
        void onSuccess(List<Warehouse> warehouses);
        void onError(String message);
    }

    public interface WarehouseCallback {
        void onSuccess(Warehouse warehouse);
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

    public static void getWarehouses(
            @NonNull Context context,
            @NonNull SessionManager sessionManager,
            @NonNull WarehouseListCallback callback
    ) {
        String url = ApiConfig.url(sessionManager, "api/warehouses/");

        StringRequest request = new StringRequest(
                Request.Method.GET,
                url,
                response -> {
                    try {
                        JSONArray items = extractResultsArray(response);
                        List<Warehouse> warehouses = new ArrayList<>();

                        for (int i = 0; i < items.length(); i++) {
                            JSONObject obj = items.optJSONObject(i);
                            if (obj != null) {
                                warehouses.add(Warehouse.fromJson(obj));
                            }
                        }

                        callback.onSuccess(warehouses);
                    } catch (Exception e) {
                        callback.onError("Invalid warehouse response");
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

    public static void createWarehouse(
            @NonNull Context context,
            @NonNull SessionManager sessionManager,
            @NonNull Warehouse warehouse,
            @NonNull WarehouseCallback callback
    ) {
        String url = ApiConfig.url(sessionManager, "api/warehouses/");

        JSONObject body;
        try {
            body = warehouse.toJson();
        } catch (JSONException e) {
            callback.onError("Invalid warehouse data");
            return;
        }

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                url,
                body,
                response -> callback.onSuccess(Warehouse.fromJson(response)),
                error -> callback.onError(parseVolleyError(error))
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return headers(sessionManager);
            }
        };

        ApiClient.getInstance(context).add(request);
    }

    public static void updateWarehouse(
            @NonNull Context context,
            @NonNull SessionManager sessionManager,
            int id,
            @NonNull Warehouse warehouse,
            @NonNull WarehouseCallback callback
    ) {
        String url = ApiConfig.url(sessionManager, "api/warehouses/" + id + "/");

        JSONObject body;
        try {
            body = warehouse.toJson();
        } catch (JSONException e) {
            callback.onError("Invalid warehouse data");
            return;
        }

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.PUT,
                url,
                body,
                response -> callback.onSuccess(Warehouse.fromJson(response)),
                error -> callback.onError(parseVolleyError(error))
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return headers(sessionManager);
            }
        };

        ApiClient.getInstance(context).add(request);
    }

    public static void deleteWarehouse(
            @NonNull Context context,
            @NonNull SessionManager sessionManager,
            int id,
            @NonNull DeleteCallback callback
    ) {
        String url = ApiConfig.url(sessionManager, "api/warehouses/" + id + "/");

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.DELETE,
                url,
                null,
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
                return "HTTP " + code + ": " + body;
            }

            return "HTTP " + code;
        }

        if (error.getMessage() != null) {
            return error.getMessage();
        }

        return "Network error";
    }
}
