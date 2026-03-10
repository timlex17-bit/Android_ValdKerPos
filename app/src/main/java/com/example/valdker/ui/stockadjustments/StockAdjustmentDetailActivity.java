package com.example.valdker.ui.stockadjustments;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.valdker.R;
import com.example.valdker.models.StockAdjustment;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

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
            o.put("product_name", it.product_name);
            o.put("adjusted_by", it.adjusted_by);
            o.put("adjusted_by_name", it.adjusted_by_name);

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
        TextView tvDate = findViewById(R.id.tvDate);
        TextView tvMeta = findViewById(R.id.tvMeta);
        TextView tvReason = findViewById(R.id.tvReason);
        TextView tvFromTo = findViewById(R.id.tvFromTo);
        TextView tvDiff = findViewById(R.id.tvDiff);
        TextView tvBy = findViewById(R.id.tvBy);
        TextView tvNote = findViewById(R.id.tvNote);

        String json = getIntent().getStringExtra(EXTRA_JSON);
        if (json != null) {
            try {
                JSONObject o = new JSONObject(json);

                int id = o.optInt("id");
                int product = o.optInt("product");
                int adjustedBy = o.optInt("adjusted_by");
                int oldStock = o.optInt("old_stock");
                int newStock = o.optInt("new_stock");
                int diff = newStock - oldStock;

                String productName = o.optString("product_name", "").trim();
                String adjustedByName = o.optString("adjusted_by_name", "").trim();

                if (!productName.isEmpty()) {
                    tvTitle.setText(productName);
                } else {
                    tvTitle.setText("Product #" + product);
                }

                tvDate.setText(formatIso(o.optString("adjusted_at", "")));

                tvReason.setText(safe(o.optString("reason", "-")));
                tvFromTo.setText(oldStock + " → " + newStock);

                String sign = diff > 0 ? "+" : "";
                tvDiff.setText(sign + diff);

                if (diff > 0) {
                    tvDiff.setTextColor(Color.parseColor("#16A34A"));
                } else if (diff < 0) {
                    tvDiff.setTextColor(Color.parseColor("#DC2626"));
                } else {
                    tvDiff.setTextColor(Color.parseColor("#374151"));
                }

                if (!adjustedByName.isEmpty()) {
                    tvBy.setText(adjustedByName);
                } else {
                    tvBy.setText("User #" + adjustedBy);
                }

                String note = o.optString("note");
                tvNote.setText(note == null || note.trim().isEmpty() ? "-" : note.trim());

                tvMeta.setVisibility(View.GONE);

            } catch (Exception e) {
                tvNote.setText("Parse error: " + e.getMessage());
            }
        }
    }

    private String safe(String s) {
        return (s == null || s.trim().isEmpty()) ? "-" : s.trim();
    }

    private String formatIso(String iso) {
        if (iso == null || iso.trim().isEmpty()) return "-";

        try {
            String clean = iso.trim();
            clean = clean.replaceFirst("\\.\\d+(?=[+-]\\d{2}:\\d{2}$)", "");

            SimpleDateFormat in;
            if (clean.endsWith("Z")) {
                in = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            } else {
                in = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);
            }

            in.setLenient(false);
            Date d = in.parse(clean);

            SimpleDateFormat out = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
            return d != null ? out.format(d) : iso;

        } catch (Exception e) {
            return iso;
        }
    }
}