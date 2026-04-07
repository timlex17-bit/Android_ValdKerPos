package com.valdker.pos.ui.stockmovements;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.valdker.pos.R;
import com.valdker.pos.models.StockMovement;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

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
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stock_movement_detail);

        TextView tvTitle = findViewById(R.id.tvTitle);
        TextView tvMeta = findViewById(R.id.tvMeta);
        TextView tvType = findViewById(R.id.tvType);
        TextView tvDate = findViewById(R.id.tvDate);
        TextView tvQty = findViewById(R.id.tvQty);
        TextView tvStock = findViewById(R.id.tvStock);
        TextView tvRef = findViewById(R.id.tvRef);
        TextView tvNote = findViewById(R.id.tvNote);

        String json = getIntent().getStringExtra(EXTRA_JSON);
        if (json != null) {
            try {
                JSONObject o = new JSONObject(json);

                String name = o.optString("product_name");
                if (name == null || name.trim().isEmpty()) {
                    name = getString(R.string.label_product_with_id, o.optInt("product"));
                }

                tvTitle.setText(name);

                String sku = safeValue(o.optString("product_sku", ""));
                String code = safeValue(o.optString("product_code", ""));
                tvMeta.setText(getString(R.string.label_sku_and_code, sku, code));

                String movementType = mapMovementType(o.optString("movement_type", ""));
                tvType.setText(getString(R.string.label_type_value, movementType));

                tvDate.setText(formatDateTime(o.optString("created_at", "")));

                int delta = o.optInt("quantity_delta");
                tvQty.setText(getString(
                        R.string.label_quantity_delta_value,
                        (delta > 0 ? "+" : "") + delta
                ));

                tvStock.setText(getString(
                        R.string.label_stock_range,
                        o.optInt("before_stock"),
                        o.optInt("after_stock")
                ));

                String refModel = o.optString("ref_model", "");
                int refId = o.optInt("ref_id", 0);
                String refValue = (refModel == null || refModel.trim().isEmpty() || refId == 0)
                        ? getString(R.string.label_default_dash)
                        : refModel + " #" + refId;
                tvRef.setText(getString(R.string.label_ref_value, refValue));

                String note = o.optString("note");
                tvNote.setText(note == null || note.trim().isEmpty()
                        ? getString(R.string.label_default_dash)
                        : note);

            } catch (Exception e) {
                tvNote.setText(getString(R.string.msg_parse_error, e.getMessage()));
            }
        }
    }

    private String formatDateTime(String isoDate) {
        if (isoDate == null || isoDate.trim().isEmpty()) {
            return getString(R.string.label_default_dash);
        }

        String[] inputPatterns = new String[]{
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"
        };

        for (String pattern : inputPatterns) {
            try {
                SimpleDateFormat inputFormat = new SimpleDateFormat(pattern, Locale.getDefault());
                inputFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

                Date date = inputFormat.parse(isoDate);
                if (date != null) {
                    SimpleDateFormat outputFormat =
                            new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
                    return outputFormat.format(date);
                }
            } catch (Exception ignored) {
            }
        }

        return isoDate;
    }

    private String safeValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return getString(R.string.label_default_dash);
        }
        return value.trim();
    }

    private String mapMovementType(String rawType) {
        if (rawType == null || rawType.trim().isEmpty()) {
            return getString(R.string.label_default_dash);
        }

        String normalized = rawType.trim().toUpperCase(Locale.US);

        switch (normalized) {
            case "SALE":
                return getString(R.string.movement_type_sale);

            case "PURCHASE":
                return getString(R.string.movement_type_purchase);

            case "ADJUSTMENT":
                return getString(R.string.movement_type_adjustment);

            case "RETURN":
                return getString(R.string.movement_type_return);

            case "OPENING":
                return getString(R.string.movement_type_opening);

            case "STOCK_IN":
                return getString(R.string.movement_type_stock_in);

            case "STOCK_OUT":
                return getString(R.string.movement_type_stock_out);

            case "COUNT":
                return getString(R.string.movement_type_count);

            case "TRANSFER":
                return getString(R.string.movement_type_transfer);

            default:
                return rawType.trim();
        }
    }
}