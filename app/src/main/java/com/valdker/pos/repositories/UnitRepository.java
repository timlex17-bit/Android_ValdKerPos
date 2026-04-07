package com.valdker.pos.repositories;

import android.content.Context;

import androidx.annotation.NonNull;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonArrayRequest;
import com.valdker.pos.SessionManager;
import com.valdker.pos.models.UnitLite;
import com.valdker.pos.network.ApiClient;
import com.valdker.pos.network.ApiConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UnitRepository {

    private static final String ENDPOINT_UNITS = "api/units/";
    private final Context appContext;
    private final SessionManager session;

    public interface Callback {
        void onSuccess(@NonNull List<UnitLite> list);
        void onError(int statusCode, @NonNull String message);
    }

    public UnitRepository(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.session = new SessionManager(appContext);
    }

    public void fetchUnits(@NonNull String token, @NonNull Callback cb) {

        String url = ApiConfig.url(session, ENDPOINT_UNITS);

        JsonArrayRequest req = new JsonArrayRequest(
                Request.Method.GET,
                url,
                null,
                (JSONArray res) -> {
                    try {
                        List<UnitLite> out = new ArrayList<>();
                        if (res != null) {
                            for (int i = 0; i < res.length(); i++) {
                                JSONObject o = res.optJSONObject(i);
                                if (o == null) continue;

                                int id = o.optInt("id", 0);
                                String name = o.optString("name", "");

                                if (id != 0 && !name.trim().isEmpty()) {
                                    out.add(new UnitLite(id, name.trim()));
                                }
                            }
                        }
                        cb.onSuccess(out);
                    } catch (Exception e) {
                        cb.onError(0, "Parse error: " + e.getMessage());
                    }
                },
                (err) -> {
                    int code = (err.networkResponse != null) ? err.networkResponse.statusCode : -1;
                    String msg = (err.getMessage() != null) ? err.getMessage() : "Network error";
                    cb.onError(code, msg);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> h = new HashMap<>();
                h.put("Accept", "application/json");
                h.put("Authorization", "Token " + token.trim());
                return h;
            }
        };

        ApiClient.getInstance(appContext).add(req);
    }
}