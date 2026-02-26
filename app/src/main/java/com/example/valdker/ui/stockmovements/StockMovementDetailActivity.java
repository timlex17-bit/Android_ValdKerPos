package com.example.valdker.ui.stockmovements;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.valdker.R;
import com.example.valdker.models.StockMovement;

import org.json.JSONObject;

public class StockMovementDetailActivity extends AppCompatActivity {

    private static final String EXTRA_JSON = "extra_json";

    public static void open(Context ctx, StockMovement it) {
        try {
            JSONObject o = new JSONObject();
            o.put("id", it.id);
            o.put("created_at", it.created_at);
            o.put("movement_type", it.movement_type);
            o.put("quantity_delta", it.quantity_delta);
            o.put("before_stock", it.before_stock);
            o.put("after_stock", it.after_stock);
            o.put("note", it.note);
            o.put("ref_model", it.ref_model);
            o.put("ref_id", it.ref_id);
            o.put("product", it.product);
            o.put("product_name", it.product_name);
            o.put("product_code", it.product_code);
            o.put("product_sku", it.product_sku);
            o.put("created_by", it.created_by);

            Intent i = new Intent(ctx, StockMovementDetailActivity.class);
            i.putExtra(EXTRA_JSON, o.toString());
            ctx.startActivity(i);
        } catch (Exception ignored) {}
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stock_movement_detail);

        TextView tvTitle = findViewById(R.id.tvTitle);
        TextView tvMeta = findViewById(R.id.tvMeta);
        TextView tvType = findViewById(R.id.tvType);
        TextView tvQty = findViewById(R.id.tvQty);
        TextView tvStock = findViewById(R.id.tvStock);
        TextView tvRef = findViewById(R.id.tvRef);
        TextView tvNote = findViewById(R.id.tvNote);

        String json = getIntent().getStringExtra(EXTRA_JSON);
        if (json != null) {
            try {
                JSONObject o = new JSONObject(json);

                String name = o.optString("product_name");
                if (name == null || name.trim().isEmpty()) name = "Product #" + o.optInt("product");

                tvTitle.setText(name);
                tvMeta.setText("SKU: " + o.optString("product_sku", "-") +
                        " • Code: " + o.optString("product_code", "-"));

                tvType.setText("Type: " + o.optString("movement_type", "-") +
                        " • " + o.optString("created_at", "-"));

                int delta = o.optInt("quantity_delta");
                tvQty.setText("Quantity Δ: " + (delta > 0 ? "+" : "") + delta);

                tvStock.setText("Stock: " + o.optInt("before_stock") +
                        " → " + o.optInt("after_stock"));

                String refModel = o.optString("ref_model", "");
                int refId = o.optInt("ref_id", 0);
                tvRef.setText("Ref: " + (refModel.isEmpty() || refId == 0 ? "-" : (refModel + " #" + refId)));

                String note = o.optString("note");
                tvNote.setText(note == null || note.trim().isEmpty() ? "-" : note);

            } catch (Exception e) {
                tvNote.setText("Parse error: " + e.getMessage());
            }
        }
    }
}