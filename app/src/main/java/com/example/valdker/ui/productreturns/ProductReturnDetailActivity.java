package com.example.valdker.ui.productreturns;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.valdker.R;
import com.example.valdker.models.ProductLite;
import com.example.valdker.models.ProductReturn;
import com.example.valdker.models.ProductReturnItem;
import com.google.android.material.button.MaterialButton;

import java.text.NumberFormat;
import java.util.Locale;

import com.dantsu.escposprinter.EscPosPrinter;
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection;
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections;

public class ProductReturnDetailActivity extends AppCompatActivity {

    public static final String EXTRA_DATA = "data";

    private TextView tvTitle, tvInvoice, tvCustomer, tvReturnedBy, tvReturnedAt, tvNote, tvSummary;
    private RecyclerView rvItems;
    private MaterialButton btnPrint;

    private ProductReturn data;
    private ProductReturnItemAdapter adapter;

    private final NumberFormat usd = NumberFormat.getCurrencyInstance(Locale.US);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_return_detail);

        tvTitle = findViewById(R.id.tvTitle);
        tvInvoice = findViewById(R.id.tvInvoice);
        tvCustomer = findViewById(R.id.tvCustomer);
        tvReturnedBy = findViewById(R.id.tvReturnedBy);
        tvReturnedAt = findViewById(R.id.tvReturnedAt);
        tvNote = findViewById(R.id.tvNote);
        tvSummary = findViewById(R.id.tvSummary);

        rvItems = findViewById(R.id.rvItems);
        btnPrint = findViewById(R.id.btnPrint);

        data = getIntent().getParcelableExtra(EXTRA_DATA);
        if (data == null) {
            Toast.makeText(this, "No return data.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        adapter = new ProductReturnItemAdapter();
        rvItems.setLayoutManager(new LinearLayoutManager(this));
        rvItems.setAdapter(adapter);

        bind();

        btnPrint.setOnClickListener(v -> doPrint());
    }

    private void bind() {

        String inv = data.invoiceNumber != null ? data.invoiceNumber.trim() : "";
        if (!inv.isEmpty()) {
            tvTitle.setText(inv);
        } else if (data.order != null) {
            tvTitle.setText("Order #" + data.order);
        } else {
            tvTitle.setText("Return #" + data.id);
        }

        if (inv.isEmpty()) {
            tvInvoice.setText("Order: " + (data.order != null ? data.order : "-"));
        } else {
            tvInvoice.setText("Invoice: " + inv);
        }

        String customerName = (data.customer != null && data.customer.name != null && !data.customer.name.trim().isEmpty())
                ? data.customer.name : "-";
        tvCustomer.setText("Customer: " + customerName);

        String returnedByName = (data.returnedBy != null) ? data.returnedBy.bestName() : "-";
        tvReturnedBy.setText("Returned by: " + returnedByName);

        tvReturnedAt.setText("Returned: " + formatIso(data.returnedAt));

        String note = data.note != null ? data.note.trim() : "";
        tvNote.setText("Note: " + (note.isEmpty() ? "-" : note));

        adapter.setData(data.items);

        tvSummary.setText("Qty: " + trimZero(data.totalQty()) + " • Total: " + usd.format(data.totalAmount()));
    }

    private String formatIso(String iso) {
        if (iso == null || iso.trim().isEmpty()) return "-";

        try {
            java.text.SimpleDateFormat input =
                    new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX", java.util.Locale.US);
            java.text.SimpleDateFormat output =
                    new java.text.SimpleDateFormat("dd MMM yyyy • HH:mm", java.util.Locale.US);

            java.util.Date date = input.parse(iso);
            return date != null ? output.format(date) : iso;

        } catch (Exception e1) {
            try {
                java.text.SimpleDateFormat input2 =
                        new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US);
                java.text.SimpleDateFormat output2 =
                        new java.text.SimpleDateFormat("dd MMM yyyy • HH:mm", java.util.Locale.US);

                java.util.Date date2 = input2.parse(iso);
                return date2 != null ? output2.format(date2) : iso;

            } catch (Exception e2) {
                return iso;
            }
        }
    }

    private String trimZero(double v) {
        if (Math.abs(v - Math.round(v)) < 0.000001)
            return String.valueOf((long) Math.round(v));
        return String.valueOf(v);
    }

    private void doPrint() {
        // Android 12+ bluetooth permission check
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 101);
                Toast.makeText(this, "Grant Bluetooth permission then try again.", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        try {
            BluetoothConnection connection = BluetoothPrintersConnections.selectFirstPaired();
            if (connection == null) {
                Toast.makeText(this, "No paired Bluetooth printer found.", Toast.LENGTH_LONG).show();
                return;
            }

            EscPosPrinter printer = new EscPosPrinter(connection, 203, 48f, 32);

            String receipt = buildReturnReceiptText();
            printer.printFormattedText(receipt);

            Toast.makeText(this, "Printed.", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Toast.makeText(this, "Print failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String buildReturnReceiptText() {
        String inv = (data.invoiceNumber != null && !data.invoiceNumber.trim().isEmpty())
                ? data.invoiceNumber.trim()
                : (data.order != null ? String.valueOf(data.order) : "-");

        String customerName = (data.customer != null && data.customer.name != null && !data.customer.name.trim().isEmpty())
                ? data.customer.name : "-";

        String returnedByName = (data.returnedBy != null) ? data.returnedBy.bestName() : "-";
        String note = (data.note != null && !data.note.trim().isEmpty()) ? data.note.trim() : "-";

        StringBuilder sb = new StringBuilder();

        sb.append("[C]<b>VALDKER POS</b>\n");
        sb.append("[C]RETURN RECEIPT\n");
        sb.append("[C]-------------------------------\n");

        sb.append("[L]Return #: ").append(data.id).append("\n");
        sb.append("[L]Invoice: ").append(inv).append("\n");
        sb.append("[L]Customer: ").append(customerName).append("\n");
        sb.append("[L]Returned by: ").append(returnedByName).append("\n");
        sb.append("[L]Returned at: ").append(data.returnedAt != null ? data.returnedAt : "-").append("\n");
        sb.append("[L]Note: ").append(note).append("\n");
        sb.append("[C]-------------------------------\n");

        sb.append("[L]<b>ITEMS</b>\n");

        if (data.items != null) {
            for (ProductReturnItem it : data.items) {
                ProductLite p = it.product;
                String name = (p != null && p.name != null && !p.name.trim().isEmpty()) ? p.name : "-";

                String qty = trimZero(it.quantity);
                String unit = usd.format(it.unitPriceAsDouble());
                String line = usd.format(it.lineTotal());

                // 2-line format for readability
                sb.append("[L]").append(name).append("\n");
                sb.append("[L]  ").append(qty).append(" x ").append(unit).append("    [R]").append(line).append("\n");
            }
        }

        sb.append("[C]-------------------------------\n");
        sb.append("[L]Qty: ").append(trimZero(data.totalQty())).append("\n");
        sb.append("[L]<b>Total:</b> [R]<b>").append(usd.format(data.totalAmount())).append("</b>\n");
        sb.append("[C]-------------------------------\n");
        sb.append("[C]Thank you\n\n");

        return sb.toString();
    }
}