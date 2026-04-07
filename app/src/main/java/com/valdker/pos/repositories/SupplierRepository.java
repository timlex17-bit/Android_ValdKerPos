package com.valdker.pos.repositories;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.valdker.pos.models.Supplier;
import com.valdker.pos.models.SupplierLite;
import com.valdker.pos.network.ApiClient;
import com.valdker.pos.SessionManager;
import com.valdker.pos.network.ApiConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SupplierRepository {

    private static final String ENDPOINT_SUPPLIERS = "api/suppliers/";
    private final Context appContext;
    private final SessionManager session;

    public SupplierRepository(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.session = new SessionManager(appContext);
    }

    // ------------------- CALLBACKS -------------------
    public interface ListCallback {
        void onSuccess(@NonNull List<Supplier> list);
        void onError(int code, @NonNull String message);
    }

    public interface ItemCallback {
        void onSuccess(@NonNull Supplier s);
        void onError(int code, @NonNull String message);
    }

    public interface DeleteCallback {
        void onSuccess();
        void onError(int code, @NonNull String message);
    }

    // ✅ NEW: LiteCallback untuk Spinner
    public interface LiteCallback {
        void onSuccess(@NonNull List<SupplierLite> list);
        void onError(int code, @NonNull String message);
    }

    // ------------------- FETCH -------------------
    public void fetchSuppliers(@NonNull String token, @NonNull ListCallback cb) {
        String url = ApiConfig.url(session, ENDPOINT_SUPPLIERS);
        JsonArrayRequest req = new JsonArrayRequest(
                Request.Method.GET,
                url,
                null,
                (JSONArray res) -> {
                    try {
                        cb.onSuccess(parseList(res));
                    } catch (Exception e) {
                        cb.onError(0, "Parse error");
                    }
                },
                (err) -> {
                    int code = (err.networkResponse != null) ? err.networkResponse.statusCode : -1;
                    cb.onError(code, "Network error");
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return auth(token);
            }
        };

        ApiClient.getInstance(appContext).add(req);
    }

    // ✅ NEW: Fetch suppliers lite (id + name) for Spinner
    public void fetchSupplierLite(@NonNull String token, @NonNull LiteCallback cb) {
        fetchSuppliers(token, new ListCallback() {
            @Override
            public void onSuccess(@NonNull List<Supplier> list) {
                List<SupplierLite> out = new ArrayList<>();
                for (Supplier s : list) {
                    out.add(new SupplierLite(s.id, s.name));
                }
                cb.onSuccess(out);
            }

            @Override
            public void onError(int code, @NonNull String message) {
                cb.onError(code, message);
            }
        });
    }

    // ------------------- CREATE -------------------
    public void createSupplier(@NonNull String token,
                               @NonNull String name,
                               @NonNull String contactPerson,
                               @NonNull String cell,
                               @Nullable String email,
                               @Nullable String address,
                               @NonNull ItemCallback cb) {

        JSONObject body = new JSONObject();
        try {
            body.put("name", name);
            body.put("contact_person", contactPerson);
            body.put("cell", cell);
            if (email != null) body.put("email", email);
            if (address != null) body.put("address", address);
        } catch (Exception e) {
            cb.onError(0, "JSON error");
            return;
        }

        String url = ApiConfig.url(session, ENDPOINT_SUPPLIERS);
        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.POST,
                url,
                body,
                res -> cb.onSuccess(parseOne(res)),
                err -> {
                    int code = (err.networkResponse != null) ? err.networkResponse.statusCode : -1;
                    cb.onError(code, "Create failed");
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> h = auth(token);
                h.put("Content-Type", "application/json");
                return h;
            }
        };

        ApiClient.getInstance(appContext).add(req);
    }

    // ------------------- UPDATE -------------------
    public void updateSupplier(@NonNull String token,
                               int id,
                               @NonNull String name,
                               @NonNull String contactPerson,
                               @NonNull String cell,
                               @Nullable String email,
                               @Nullable String address,
                               @NonNull ItemCallback cb) {

        String url = ApiConfig.url(session, ENDPOINT_SUPPLIERS + id + "/");

        JSONObject body = new JSONObject();
        try {
            body.put("name", name);
            body.put("contact_person", contactPerson);
            body.put("cell", cell);
            body.put("email", email != null ? email : JSONObject.NULL);
            body.put("address", address != null ? address : JSONObject.NULL);
        } catch (Exception e) {
            cb.onError(0, "JSON error");
            return;
        }

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.PUT,
                url,
                body,
                res -> cb.onSuccess(parseOne(res)),
                err -> {
                    int code = (err.networkResponse != null) ? err.networkResponse.statusCode : -1;
                    cb.onError(code, "Update failed");
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> h = auth(token);
                h.put("Content-Type", "application/json");
                return h;
            }
        };

        ApiClient.getInstance(appContext).add(req);
    }

    // ------------------- DELETE -------------------
    public void deleteSupplier(@NonNull String token, int id, @NonNull DeleteCallback cb) {

        String url = ApiConfig.url(session, ENDPOINT_SUPPLIERS + id + "/");

        StringRequest req = new StringRequest(
                Request.Method.DELETE,
                url,
                response -> cb.onSuccess(),
                error -> {
                    int code = (error.networkResponse != null)
                            ? error.networkResponse.statusCode
                            : -1;

                    if (code == 404) {
                        cb.onSuccess();
                        return;
                    }

                    cb.onError(code, "Delete failed");
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return auth(token);
            }
        };

        req.setRetryPolicy(new DefaultRetryPolicy(20000, 0, 1.2f));
        req.setShouldCache(false);

        ApiClient.getInstance(appContext).add(req);
    }

    // ------------------- PARSER -------------------
    private List<Supplier> parseList(JSONArray arr) {
        List<Supplier> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null) out.add(parseOne(o));
        }
        return out;
    }

    private Supplier parseOne(JSONObject o) {
        return new Supplier(
                o.optInt("id"),
                o.optString("name"),
                o.optString("contact_person"),
                o.optString("cell"),
                o.isNull("email") ? null : o.optString("email"),
                o.isNull("address") ? null : o.optString("address")
        );
    }

    private Map<String, String> auth(String token) {
        Map<String, String> h = new HashMap<>();
        h.put("Accept", "application/json");
        h.put("Authorization", "Token " + token);
        return h;
    }
}
