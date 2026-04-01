package com.example.valdker.ui.inventorycount;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.valdker.BuildConfig;
import com.example.valdker.R;
import com.example.valdker.models.InventoryCount;
import com.example.valdker.models.InventoryCountItem;
import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class InventoryCountDetailActivity extends AppCompatActivity {

    private static final String EXTRA_JSON = "extra_json";

    private TextView tvTitle;
    private TextView tvMeta;
    private TextView tvNote;
    private TextView tvTotalItems;
    private TextView tvTotalDiff;
    private TextView tvValueImpact;
    private RecyclerView rvItems;
    private Button btnFinalize;

    private final List<InventoryCountItem> items = new ArrayList<>();
    private InventoryCountItemAdapter adapter;

    private int inventoryId = 0;
    private String currentStatus = "DRAFT";
    private String currentCountedAt = "-";
    private String currentByName = "-";
    private boolean isFinalizing = false;

    public static void open(Context ctx, InventoryCount item) {
        try {
            JSONObject o = new JSONObject();
            o.put("id", item.id);
            o.put("title", item.title);
            o.put("note", item.note);
            o.put("counted_at", item.counted_at);
            o.put("status", item.status != null ? item.status : "DRAFT");

            JSONObject u = new JSONObject();
            if (item.counted_by != null) {
                u.put("id", item.counted_by.id);
                u.put("username", item.counted_by.username);
                u.put("display_name", item.counted_by.display_name);
            }
            o.put("counted_by", u);

            JSONArray arr = new JSONArray();
            if (item.items != null) {
                for (InventoryCountItem it : item.items) {
                    JSONObject x = new JSONObject();
                    x.put("id", it.id);
                    x.put("product", it.product);
                    x.put("system_stock", it.system_stock);
                    x.put("counted_stock", it.counted_stock);
                    x.put("difference", it.difference);
                    x.put("cost_price", it.cost_price != null ? it.cost_price : "0");
                    arr.put(x);
                }
            }
            o.put("items", arr);

            Intent i = new Intent(ctx, InventoryCountDetailActivity.class);
            i.putExtra(EXTRA_JSON, o.toString());
            ctx.startActivity(i);
        } catch (Exception e) {
            // fallback do nothing
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory_count_detail);

        bindViews();
        setupRecycler();
        parseIntentAndRender();
    }

    private void bindViews() {
        tvTitle = findViewById(R.id.tvTitle);
        tvMeta = findViewById(R.id.tvMeta);
        tvNote = findViewById(R.id.tvNote);
        tvTotalItems = findViewById(R.id.tvTotalItems);
        tvTotalDiff = findViewById(R.id.tvTotalDiff);
        tvValueImpact = findViewById(R.id.tvValueImpact);
        rvItems = findViewById(R.id.rvItems);
        btnFinalize = findViewById(R.id.btnFinalize);
    }

    private void setupRecycler() {
        rvItems.setLayoutManager(new LinearLayoutManager(this));
        adapter = new InventoryCountItemAdapter(items);
        rvItems.setAdapter(adapter);
    }

    private void parseIntentAndRender() {
        String json = getIntent().getStringExtra(EXTRA_JSON);
        if (json == null || json.trim().isEmpty()) {
            tvNote.setText("No detail data");
            if (btnFinalize != null) btnFinalize.setVisibility(View.GONE);
            return;
        }

        try {
            JSONObject o = new JSONObject(json);

            inventoryId = o.optInt("id", 0);
            currentStatus = normalizeStatus(o.optString("status", "DRAFT"));
            currentCountedAt = formatDate(o.optString("counted_at", "-"));
            currentByName = extractByName(o.optJSONObject("counted_by"));

            tvTitle.setText(safeText(o.optString("title"), "Stock Count"));
            tvNote.setText(safeText(o.optString("note"), "-"));
            updateMetaText();

            JSONArray arr = o.optJSONArray("items");
            items.clear();

            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject x = arr.getJSONObject(i);

                    InventoryCountItem itemObj = new InventoryCountItem();
                    itemObj.id = x.optInt("id");
                    itemObj.product = x.optInt("product");
                    itemObj.system_stock = x.optInt("system_stock");
                    itemObj.counted_stock = x.optInt("counted_stock");
                    itemObj.cost_price = x.optString("cost_price", "0");
                    itemObj.difference = x.optInt("difference");

                    items.add(itemObj);
                }
            }

            adapter.notifyDataSetChanged();
            updateSummary();
            updateFinalizeButton();

        } catch (Exception e) {
            tvNote.setText("Parse error: " + e.getMessage());
            if (btnFinalize != null) btnFinalize.setVisibility(View.GONE);
        }
    }

    private void updateMetaText() {
        if (tvMeta == null) return;
        tvMeta.setText(currentCountedAt + " • By: " + currentByName + " • " + currentStatus);
    }

    private void updateSummary() {
        int totalItems = items.size();
        int totalDiff = 0;
        double valueImpact = 0.0;

        for (InventoryCountItem row : items) {
            totalDiff += row.difference;

            double cost = 0.0;
            try {
                cost = Double.parseDouble(row.cost_price == null ? "0" : row.cost_price);
            } catch (Exception ignored) {
            }

            valueImpact += (row.difference * cost);
        }

        if (tvTotalItems != null) {
            tvTotalItems.setText("Total Items: " + totalItems);
        }

        if (tvTotalDiff != null) {
            tvTotalDiff.setText("Total Diff: " + totalDiff);
            applyDiffStyle(tvTotalDiff, totalDiff);
        }

        if (tvValueImpact != null) {
            tvValueImpact.setText(String.format(Locale.US, "Total Value Impact: $%.2f", valueImpact));
        }
    }

    private void updateFinalizeButton() {
        if (btnFinalize == null) return;

        boolean canFinalize = !"COMPLETED".equals(normalizeStatus(currentStatus));

        if (!canFinalize) {
            btnFinalize.setVisibility(View.GONE);
            return;
        }

        btnFinalize.setVisibility(View.VISIBLE);
        btnFinalize.setEnabled(!isFinalizing);
        btnFinalize.setText(isFinalizing ? "Finalizing..." : "Finalize");

        btnFinalize.setOnClickListener(v -> {
            if (isFinalizing) return;
            finalizeInventoryCount();
        });
    }

    private void finalizeInventoryCount() {
        if (inventoryId <= 0) {
            Toast.makeText(this, "Invalid inventory count ID", Toast.LENGTH_SHORT).show();
            return;
        }

        isFinalizing = true;
        updateFinalizeButton();

        String baseUrl = BuildConfig.BASE_URL;
        if (baseUrl == null) baseUrl = "";
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        final String url = baseUrl + "/api/inventorycounts/" + inventoryId + "/finalize/";

        StringRequest request = new StringRequest(
                Request.Method.POST,
                url,
                response -> {
                    isFinalizing = false;
                    currentStatus = "COMPLETED";
                    updateMetaText();
                    updateFinalizeButton();

                    Toast.makeText(
                            InventoryCountDetailActivity.this,
                            "Finalized successfully",
                            Toast.LENGTH_SHORT
                    ).show();

                    setResult(RESULT_OK);
                },
                error -> {
                    isFinalizing = false;
                    updateFinalizeButton();

                    String message = "Failed to finalize";
                    try {
                        if (error != null && error.networkResponse != null) {
                            int code = error.networkResponse.statusCode;
                            String body = error.networkResponse.data != null
                                    ? new String(error.networkResponse.data)
                                    : "";

                            if (body != null && !body.trim().isEmpty()) {
                                message = "Failed to finalize (" + code + ")";
                            } else {
                                message = "Failed to finalize (" + code + ")";
                            }
                        } else if (error != null && error.getMessage() != null && !error.getMessage().trim().isEmpty()) {
                            message = error.getMessage();
                        }
                    } catch (Exception ignored) {
                    }

                    Toast.makeText(
                            InventoryCountDetailActivity.this,
                            message,
                            Toast.LENGTH_LONG
                    ).show();
                }
        ) {
            @Override
            public byte[] getBody() {
                return null; // backend finalize Anda tidak membaca request body
            }

            @Override
            public String getBodyContentType() {
                return "application/json; charset=utf-8";
            }

            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Accept", "application/json");

                String token = getSavedToken();
                if (token != null && !token.trim().isEmpty()) {
                    headers.put("Authorization", "Token " + token.trim());
                }

                return headers;
            }
        };

        request.setRetryPolicy(new DefaultRetryPolicy(
                20000,
                0,
                1f
        ));

        RequestQueue queue = Volley.newRequestQueue(this);
        queue.add(request);
    }

    private String getSavedToken() {
        try {
            // coba beberapa kemungkinan nama prefs/key yang umum dipakai
            String[] prefNames = new String[]{
                    "MyAppPrefs",
                    "SessionManager",
                    "valdker_session",
                    getPackageName() + "_preferences"
            };

            String[] tokenKeys = new String[]{
                    "token",
                    "auth_token",
                    "user_token",
                    "KEY_TOKEN"
            };

            for (String prefName : prefNames) {
                android.content.SharedPreferences sp =
                        getSharedPreferences(prefName, MODE_PRIVATE);

                for (String key : tokenKeys) {
                    String value = sp.getString(key, null);
                    if (value != null && !value.trim().isEmpty()) {
                        return value.trim();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static String extractByName(JSONObject u) {
        if (u == null) return "-";

        String dn = u.optString("display_name", "");
        String un = u.optString("username", "");

        if (dn != null && !dn.trim().isEmpty()) return dn.trim();
        if (un != null && !un.trim().isEmpty()) return un.trim();

        int id = u.optInt("id", 0);
        return id > 0 ? "#" + id : "-";
    }

    private static String normalizeStatus(String status) {
        if (status == null || status.trim().isEmpty()) return "DRAFT";
        return status.trim().toUpperCase(Locale.US);
    }

    private static String safeText(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        return value;
    }

    private static String formatDate(String iso) {
        try {
            String clean = iso;
            if (clean != null && clean.contains(".")) {
                int dot = clean.indexOf(".");
                int plus = clean.indexOf("+", dot);
                if (plus > 0) clean = clean.substring(0, dot) + clean.substring(plus);
            }

            java.text.SimpleDateFormat input =
                    new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ENGLISH);
            java.text.SimpleDateFormat output =
                    new java.text.SimpleDateFormat("dd MMM yyyy - HH:mm", Locale.ENGLISH);

            java.util.Date d = input.parse(clean);
            return output.format(d);
        } catch (Exception e) {
            return (iso != null && !iso.trim().isEmpty()) ? iso : "-";
        }
    }

    private static void applyDiffStyle(TextView tv, int diff) {
        if (tv == null) return;

        if (diff == 0) {
            tv.setTextColor(0xFF2E7D32);
        } else if (diff < 0) {
            tv.setTextColor(0xFFC62828);
        } else {
            tv.setTextColor(0xFFEF6C00);
        }
    }
}