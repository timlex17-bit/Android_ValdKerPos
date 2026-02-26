package com.example.valdker.repositories;

import android.content.Context;

import androidx.annotation.NonNull;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.example.valdker.models.DailyProfitReport;
import com.example.valdker.models.DailyProfitRow;
import com.example.valdker.network.ApiClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class ReportRepository {

    public interface Callback {
        void onSuccess(@NonNull DailyProfitReport report);
        void onError(int statusCode, @NonNull String message);
    }

    private static final String BASE_URL = "https://valdker.onrender.com/api";
    private final Context context;

    public ReportRepository(@NonNull Context ctx) {
        this.context = ctx.getApplicationContext();
    }

    public void fetchDailyProfit(@NonNull String token, @NonNull String start, @NonNull String end, @NonNull Callback cb) {
        String url = BASE_URL + "/reports/daily-profit/?start=" + start + "&end=" + end;

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                res -> {
                    DailyProfitReport out = new DailyProfitReport();
                    JSONObject range = res.optJSONObject("range");
                    if (range != null) {
                        out.start = range.optString("start", "");
                        out.end = range.optString("end", "");
                    }

                    JSONObject summary = res.optJSONObject("summary");
                    if (summary != null) {
                        out.totalSales = summary.optDouble("sales", 0);
                        out.totalExpense = summary.optDouble("expense", 0);
                        out.totalProfit = summary.optDouble("profit", 0);
                    }

                    JSONArray rows = res.optJSONArray("rows");
                    if (rows != null) {
                        for (int i = 0; i < rows.length(); i++) {
                            JSONObject r = rows.optJSONObject(i);
                            if (r == null) continue;
                            DailyProfitRow row = new DailyProfitRow();
                            row.date = r.optString("date", "");
                            row.sales = r.optDouble("sales", 0);
                            row.expense = r.optDouble("expense", 0);
                            row.profit = r.optDouble("profit", 0);
                            out.rows.add(row);
                        }
                    }

                    cb.onSuccess(out);
                },
                err -> {
                    int status = err.networkResponse != null ? err.networkResponse.statusCode : 0;
                    String msg = err.getMessage();
                    if (err.networkResponse != null && err.networkResponse.data != null) {
                        msg = new String(err.networkResponse.data, StandardCharsets.UTF_8);
                    }
                    cb.onError(status, msg != null ? msg : "Failed to fetch report");
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> h = new HashMap<>();
                h.put("Authorization", "Token " + token);
                return h;
            }
        };

        req.setTag("report_daily_profit");
        ApiClient.getInstance(context).add(req);
    }
}
