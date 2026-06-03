package com.valdker.pos.ui.reports;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.valdker.pos.R;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ReportResultAdapter extends RecyclerView.Adapter<ReportResultAdapter.VH> {

    private final List<JSONObject> items = new ArrayList<>();
    private String businessType = "retail";
    private String reportType = "sales";

    public void submit(@NonNull List<JSONObject> rows,
                       @NonNull String businessType,
                       @NonNull String reportType) {
        this.items.clear();
        this.items.addAll(rows);
        this.businessType = businessType;
        this.reportType = reportType;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_report_result, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        JSONObject row = items.get(position);
        h.primary.setText(primary(row));
        h.secondary.setText(secondary(row));
        h.meta.setText(meta(row));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    private String primary(@NonNull JSONObject row) {
        String invoice = first(row, "invoice", "invoice_number", "order_number", "number", "shift_number");
        String name = first(row, "item_name", "product_name", "name", "product", "cashier_name", "payment_method");
        if (TextUtils.isEmpty(invoice)) invoice = first(row, "date", "created_at", "opened_at");
        if (TextUtils.isEmpty(name)) name = labelForReport();
        return !TextUtils.isEmpty(invoice) ? invoice + " - " + name : name;
    }

    @NonNull
    private String secondary(@NonNull JSONObject row) {
        if ("shifts".equals(reportType)) {
            return join(
                    "Cashier: " + first(row, "cashier", "cashier_name", "username"),
                    "Open: " + money(first(row, "opening_cash", "open_cash")),
                    "Close: " + money(first(row, "closing_cash", "close_cash"))
            );
        }

        if ("payments".equals(reportType)) {
            return join(
                    "Method: " + first(row, "payment_method", "method", "method_code"),
                    "Amount: " + money(first(row, "amount", "total", "paid_amount")),
                    "Count: " + first(row, "count", "orders_count", "transaction_count")
            );
        }

        if ("daily".equals(reportType)) {
            return join(
                    "Revenue: " + money(first(row, "total_revenue", "revenue", "sales")),
                    "Net: " + money(first(row, "net_sales", "net", "profit")),
                    "Orders: " + first(row, "orders", "order_count", "transactions")
            );
        }

        if ("restaurant".equals(businessType)) {
            return join(
                    "Order: " + first(row, "order_type", "type"),
                    "Qty: " + first(row, "qty", "quantity"),
                    "Subtotal: " + money(first(row, "subtotal", "line_total", "total"))
            );
        }

        if ("workshop".equals(businessType)) {
            return join(
                    "Type: " + first(row, "item_type", "type"),
                    "Qty: " + first(row, "qty", "quantity"),
                    "Subtotal: " + money(first(row, "subtotal", "line_total", "total"))
            );
        }

        return join(
                "SKU: " + first(row, "sku", "barcode", "product_barcode"),
                "Qty: " + first(row, "qty", "quantity"),
                "Subtotal: " + money(first(row, "subtotal", "line_total", "total"))
        );
    }

    @NonNull
    private String meta(@NonNull JSONObject row) {
        if ("retail".equals(businessType)) {
            return join(
                    "Payment: " + first(row, "payment_method", "method", "method_code"),
                    "Profit: " + money(first(row, "profit", "gross_profit"))
            );
        }

        return "Payment: " + first(row, "payment_method", "method", "method_code");
    }

    @NonNull
    private String labelForReport() {
        switch (reportType) {
            case "daily": return "Daily Summary";
            case "payments": return "Payment Report";
            case "shifts": return "Shift Report";
            case "items": return "Product / Item Report";
            default: return "Sales Report";
        }
    }

    @NonNull
    private static String first(@NonNull JSONObject obj, @NonNull String... keys) {
        for (String key : keys) {
            Object value = obj.opt(key);
            if (value == null || JSONObject.NULL.equals(value)) continue;
            String text = String.valueOf(value).trim();
            if (!text.isEmpty()) return text;
        }
        return "";
    }

    @NonNull
    private static String money(@Nullable String raw) {
        if (TextUtils.isEmpty(raw) || "-".equals(raw)) return "$0.00";
        try {
            return String.format(Locale.US, "$%.2f", Double.parseDouble(raw));
        } catch (Exception ignored) {
            return raw;
        }
    }

    @NonNull
    private static String join(@NonNull String... parts) {
        List<String> clean = new ArrayList<>();
        for (String part : parts) {
            if (!TextUtils.isEmpty(part) && !part.endsWith(": -") && !part.endsWith(": ")) {
                clean.add(part);
            }
        }
        return TextUtils.join(" | ", clean);
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView primary;
        final TextView secondary;
        final TextView meta;

        VH(@NonNull View itemView) {
            super(itemView);
            primary = itemView.findViewById(R.id.tvPrimary);
            secondary = itemView.findViewById(R.id.tvSecondary);
            meta = itemView.findViewById(R.id.tvMeta);
        }
    }
}
