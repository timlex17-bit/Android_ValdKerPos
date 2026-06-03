package com.valdker.pos.repositories;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.valdker.pos.SessionManager;
import com.valdker.pos.network.ApiClient;
import com.valdker.pos.network.ApiConfig;
import com.valdker.pos.network.ApiService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class ReportRepository {

    public interface Callback {
        void onSuccess(@NonNull ReportResponse response);
        void onError(int statusCode, @NonNull String message);
    }

    public static final String TYPE_DAILY = "daily";
    public static final String TYPE_SALES = "sales";
    public static final String TYPE_ITEMS = "items";
    public static final String TYPE_PAYMENTS = "payments";
    public static final String TYPE_EXPENSES = "expenses";
    public static final String TYPE_STOCK = "stock";
    public static final String TYPE_LOW_STOCK = "low_stock";
    public static final String TYPE_SHIFTS = "shifts";

    private static final String TAG_REPORTS = "REPORT_API";

    private final Context context;
    private final SessionManager session;

    public ReportRepository(@NonNull Context ctx) {
        this.context = ctx.getApplicationContext();
        this.session = new SessionManager(context);
    }

    public void fetchReport(@NonNull String reportType,
                            @NonNull Map<String, String> query,
                            @NonNull Callback cb) {
        String endpoint = ApiService.reportEndpoint(reportType);
        String url = appendQuery(ApiConfig.url(session, endpoint), query);

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                res -> cb.onSuccess(ReportResponse.fromJson(res)),
                err -> {
                    int status = err.networkResponse != null ? err.networkResponse.statusCode : 0;
                    String msg = err.getMessage();
                    if (err.networkResponse != null && err.networkResponse.data != null) {
                        msg = new String(err.networkResponse.data, StandardCharsets.UTF_8);
                    }
                    cb.onError(status, !TextUtils.isEmpty(msg) ? msg : "Failed to fetch report");
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> h = new HashMap<>();
                h.put("Accept", "application/json");
                h.put("Authorization", "Token " + session.getToken());
                return h;
            }
        };

        req.setTag(TAG_REPORTS);
        ApiClient.getInstance(context).add(req);
    }

    public void cancel() {
        ApiClient.getInstance(context).cancelAll(TAG_REPORTS);
    }

    @NonNull
    private static String appendQuery(@NonNull String baseUrl, @NonNull Map<String, String> query) {
        StringBuilder sb = new StringBuilder(baseUrl);
        boolean first = !baseUrl.contains("?");

        for (Map.Entry<String, String> entry : query.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (TextUtils.isEmpty(key) || TextUtils.isEmpty(value)) continue;
            if ("business_type".equals(key)) continue;

            sb.append(first ? '?' : '&');
            first = false;
            sb.append(urlEncode(key)).append('=').append(urlEncode(value));
        }

        return sb.toString();
    }

    @NonNull
    private static String urlEncode(@NonNull String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception ignored) {
            return value;
        }
    }

    public static class ReportResponse {
        @NonNull public final JSONObject raw;
        @NonNull public final JSONObject shop;
        @NonNull public final JSONObject summary;
        @NonNull public final JSONObject breakdown;
        @NonNull public final JSONArray results;
        @NonNull public final JSONObject pagination;
        @NonNull public final JSONObject filters;

        private ReportResponse(@NonNull JSONObject raw,
                               @Nullable JSONObject shop,
                               @Nullable JSONObject summary,
                               @Nullable JSONObject breakdown,
                               @Nullable JSONArray results,
                               @Nullable JSONObject pagination,
                               @Nullable JSONObject filters) {
            this.raw = raw;
            this.shop = shop != null ? shop : new JSONObject();
            this.summary = summary != null ? summary : new JSONObject();
            this.breakdown = breakdown != null ? breakdown : new JSONObject();
            this.results = results != null ? results : new JSONArray();
            this.pagination = pagination != null ? pagination : new JSONObject();
            this.filters = filters != null ? filters : new JSONObject();
        }

        @NonNull
        static ReportResponse fromJson(@NonNull JSONObject res) {
            JSONArray rows = res.optJSONArray("results");
            if (rows == null) rows = res.optJSONArray("rows");
            if (rows == null) rows = res.optJSONArray("data");

            return new ReportResponse(
                    res,
                    res.optJSONObject("shop"),
                    res.optJSONObject("summary"),
                    res.optJSONObject("breakdown"),
                    rows,
                    res.optJSONObject("pagination"),
                    res.optJSONObject("filters")
            );
        }
    }
}
