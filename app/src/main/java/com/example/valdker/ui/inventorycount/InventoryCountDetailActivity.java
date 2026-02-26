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
            // serialize minimal JSON (biar gampang)
            JSONObject o = new JSONObject();
            o.put("id", item.id);
            o.put("title", item.title);
            o.put("note", item.note);
            o.put("counted_at", item.counted_at);
            o.put("counted_by", item.counted_by);

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

        rv.setLayoutManager(new LinearLayoutManager(this));

        List<InventoryCountItem> items = new ArrayList<>();
        InventoryCountItemAdapter adapter = new InventoryCountItemAdapter(items);
        rv.setAdapter(adapter);

        String json = getIntent().getStringExtra(EXTRA_JSON);
        if (json != null) {
            try {
                JSONObject o = new JSONObject(json);
                tvTitle.setText(o.optString("title"));
                tvMeta.setText("ID: " + o.optInt("id") +
                        " • By: #" + o.optInt("counted_by") +
                        " • " + o.optString("counted_at"));

                String note = o.optString("note");
                tvNote.setText(note == null || note.trim().isEmpty() ? "-" : note);

                JSONArray arr = o.optJSONArray("items");
                if (arr != null) {
                    items.clear();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject x = arr.getJSONObject(i);
                        InventoryCountItem it = new InventoryCountItem();
                        it.id = x.optInt("id");
                        it.product = x.optInt("product");
                        it.system_stock = x.optInt("system_stock");
                        it.counted_stock = x.optInt("counted_stock");
                        it.difference = x.optInt("difference");
                        items.add(it);
                    }
                    adapter.notifyDataSetChanged();
                }
            } catch (Exception e) {
                tvNote.setText("Parse error: " + e.getMessage());
            }
        }
    }
}