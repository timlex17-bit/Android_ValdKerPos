package com.example.valdker.ui.stockadjustments;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.valdker.R;
import com.example.valdker.models.StockAdjustment;

import org.json.JSONObject;

public class StockAdjustmentDetailActivity extends AppCompatActivity {

    private static final String EXTRA_JSON = "extra_json";

    public static void open(Context ctx, StockAdjustment it) {
        try {
            JSONObject o = new JSONObject();
            o.put("id", it.id);
            o.put("old_stock", it.old_stock);
            o.put("new_stock", it.new_stock);
            o.put("reason", it.reason);
            o.put("note", it.note);
            o.put("adjusted_at", it.adjusted_at);
            o.put("product", it.product);
            o.put("adjusted_by", it.adjusted_by);

            Intent i = new Intent(ctx, StockAdjustmentDetailActivity.class);
            i.putExtra(EXTRA_JSON, o.toString());
            ctx.startActivity(i);
        } catch (Exception ignored) {}
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stock_adjustment_detail);

        TextView tvTitle = findViewById(R.id.tvTitle);
        TextView tvMeta = findViewById(R.id.tvMeta);
        TextView tvReason = findViewById(R.id.tvReason);
        TextView tvFromTo = findViewById(R.id.tvFromTo);
        TextView tvDiff = findViewById(R.id.tvDiff);
        TextView tvNote = findViewById(R.id.tvNote);

        String json = getIntent().getStringExtra(EXTRA_JSON);
        if (json != null) {
            try {
                JSONObject o = new JSONObject(json);

                int product = o.optInt("product");
                int oldStock = o.optInt("old_stock");
                int newStock = o.optInt("new_stock");
                int diff = newStock - oldStock;

                tvTitle.setText("Stock Adjustment");
                tvMeta.setText("ID: " + o.optInt("id") +
                        " • Product #" + product +
                        " • By #" + o.optInt("adjusted_by") +
                        " • " + o.optString("adjusted_at"));

                tvReason.setText("Reason: " + o.optString("reason", "-"));
                tvFromTo.setText("Stock: " + oldStock + " → " + newStock);
                tvDiff.setText("Diff: " + (diff > 0 ? "+" : "") + diff);

                String note = o.optString("note");
                tvNote.setText(note == null || note.trim().isEmpty() ? "-" : note);

            } catch (Exception e) {
                tvNote.setText("Parse error: " + e.getMessage());
            }
        }
    }
}