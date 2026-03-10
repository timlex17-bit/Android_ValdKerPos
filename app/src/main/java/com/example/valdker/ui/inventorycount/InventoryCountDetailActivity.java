package com.example.valdker.ui.inventorycount;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.valdker.R;
import com.example.valdker.models.InventoryCount;
import com.example.valdker.models.InventoryCountItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class InventoryCountDetailActivity extends AppCompatActivity {

    private static final String EXTRA_JSON = "extra_json";

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

        TextView tvTitle = findViewById(R.id.tvTitle);
        TextView tvMeta = findViewById(R.id.tvMeta);
        TextView tvNote = findViewById(R.id.tvNote);
        RecyclerView rv = findViewById(R.id.rvItems);

        TextView tvTotalItems = findViewById(R.id.tvTotalItems);
        TextView tvTotalDiff = findViewById(R.id.tvTotalDiff);
        TextView tvValueImpact = findViewById(R.id.tvValueImpact);

        rv.setLayoutManager(new LinearLayoutManager(this));

        List<InventoryCountItem> items = new ArrayList<>();
        InventoryCountItemAdapter adapter = new InventoryCountItemAdapter(items);
        rv.setAdapter(adapter);

        String json = getIntent().getStringExtra(EXTRA_JSON);
        if (json != null) {
            try {
                JSONObject o = new JSONObject(json);
                tvTitle.setText(o.optString("title"));

                int id = o.optInt("id");
                String status = o.optString("status", "DRAFT");

                String countedAt = formatDate(o.optString("counted_at", "-"));

                String byName = "-";
                JSONObject u = o.optJSONObject("counted_by");
                if (u != null) {
                    String dn = u.optString("display_name", "");
                    String un = u.optString("username", "");
                    if (dn != null && !dn.trim().isEmpty()) byName = dn;
                    else if (un != null && !un.trim().isEmpty()) byName = un;
                    else byName = "#" + u.optInt("id", 0);
                }

                tvMeta.setText(countedAt + " • By: " + byName + " • " + status);

                android.widget.Button btnFinalize = findViewById(R.id.btnFinalize);

                String st = (status == null || status.trim().isEmpty())
                        ? "DRAFT"
                        : status.trim().toUpperCase(java.util.Locale.US);

                boolean canFinalize = !("APPROVED".equals(st) || "COMPLETED".equals(st));

                if (btnFinalize != null) {
                    btnFinalize.setVisibility(canFinalize ? android.view.View.VISIBLE : android.view.View.GONE);
                }

                String note = o.optString("note");
                tvNote.setText(note == null || note.trim().isEmpty() ? "-" : note);

                JSONArray arr = o.optJSONArray("items");
                if (arr != null) {

                    items.clear();

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

                    // 🔹 update recycler
                    adapter.notifyDataSetChanged();

                    // ===============================
                    // COUNT SUMMARY
                    // ===============================

                    int totalItems = items.size();
                    int totalDiff = 0;
                    double valueImpact = 0.0;

                    for (InventoryCountItem row : items) {
                        totalDiff += row.difference;

                        double cost = 0.0;
                        try {
                            cost = Double.parseDouble(row.cost_price);
                        } catch (Exception ignored) {}

                        valueImpact += (row.difference * cost);
                    }

                    if (tvTotalItems != null)
                        tvTotalItems.setText("Total Items: " + totalItems);

                    if (tvTotalDiff != null) {
                        tvTotalDiff.setText("Total Diff: " + totalDiff);
                        applyDiffStyle(tvTotalDiff, totalDiff);
                    }

                    if (tvValueImpact != null) {
                        tvValueImpact.setText(
                                String.format(
                                        java.util.Locale.US,
                                        "Total Value Impact: $%.2f",
                                        valueImpact
                                )
                        );
                    }
                }
            } catch (Exception e) {
                tvNote.setText("Parse error: " + e.getMessage());
            }
        }
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
                    new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.ENGLISH);
            java.text.SimpleDateFormat output =
                    new java.text.SimpleDateFormat("dd MMM yyyy - HH:mm", java.util.Locale.ENGLISH);

            java.util.Date d = input.parse(clean);
            return output.format(d);
        } catch (Exception e) {
            return (iso != null) ? iso : "-";
        }
    }

    private static String displayUser(InventoryCount it) {
        if (it == null || it.counted_by == null) return "-";
        if (it.counted_by.display_name != null && !it.counted_by.display_name.trim().isEmpty())
            return it.counted_by.display_name;
        if (it.counted_by.username != null && !it.counted_by.username.trim().isEmpty())
            return it.counted_by.username;
        return "#" + it.counted_by.id;
    }

    private static void applyDiffStyle(TextView tv, int diff) {
        if (tv == null) return;
        if (diff == 0) tv.setTextColor(0xFF2E7D32);
        else if (diff < 0) tv.setTextColor(0xFFC62828);
        else tv.setTextColor(0xFFEF6C00);
    }
}