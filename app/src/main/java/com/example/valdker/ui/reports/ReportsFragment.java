package com.example.valdker.ui.reports;

import android.app.DatePickerDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.valdker.R;
import com.example.valdker.SessionManager;
import com.example.valdker.models.DailyProfitReport;
import com.example.valdker.models.DailyProfitRow;
import com.example.valdker.repositories.ReportRepository;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReportsFragment extends Fragment {

    private static final String TAG = "REPORTS";

    private SessionManager session;
    private ReportRepository repo;

    private EditText etStart, etEnd;

    private Button btnFetch;
    private Button btnToday, btn7Days, btnThisMonth;

    private TextView tvSales, tvExpense, tvProfit;
    private ProgressBar progress;

    private LineChart lineChart;
    private BarChart barChart;

    private ProfitRowAdapter adapter;

    public ReportsFragment() {
        super(R.layout.fragment_reports);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        session = new SessionManager(requireContext());
        repo = new ReportRepository(requireContext());

        etStart = view.findViewById(R.id.etStart);
        etEnd = view.findViewById(R.id.etEnd);

        btnFetch = view.findViewById(R.id.btnFetch);
        btnToday = view.findViewById(R.id.btnToday);
        btn7Days = view.findViewById(R.id.btn7Days);
        btnThisMonth = view.findViewById(R.id.btnThisMonth);

        tvSales = view.findViewById(R.id.tvSales);
        tvExpense = view.findViewById(R.id.tvExpense);
        tvProfit = view.findViewById(R.id.tvProfit);
        progress = view.findViewById(R.id.progress);

        lineChart = view.findViewById(R.id.lineChart);
        barChart = view.findViewById(R.id.barChart);

        RecyclerView rv = view.findViewById(R.id.rvRows);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ProfitRowAdapter();
        rv.setAdapter(adapter);

        // default today
        String today = fmtDate(new Date());
        setRange(today, today);

        // Date picker
        etStart.setOnClickListener(v -> pickDate(etStart));
        etEnd.setOnClickListener(v -> pickDate(etEnd));

        // Quick ranges
        if (btnToday != null) {
            btnToday.setOnClickListener(v -> {
                String t = fmtDate(new Date());
                setRange(t, t);
                fetch();
            });
        }

        if (btn7Days != null) {
            btn7Days.setOnClickListener(v -> {
                Calendar c = Calendar.getInstance();
                String end = fmtDate(c.getTime());
                c.add(Calendar.DAY_OF_MONTH, -6);
                String start = fmtDate(c.getTime());
                setRange(start, end);
                fetch();
            });
        }

        if (btnThisMonth != null) {
            btnThisMonth.setOnClickListener(v -> {
                Calendar c = Calendar.getInstance();
                String end = fmtDate(c.getTime());
                c.set(Calendar.DAY_OF_MONTH, 1);
                String start = fmtDate(c.getTime());
                setRange(start, end);
                fetch();
            });
        }

        if (btnFetch != null) {
            btnFetch.setOnClickListener(v -> fetch());
        }

        // initial
        fetch();
    }

    private void fetch() {
        String token = session.getToken();
        if (TextUtils.isEmpty(token)) {
            Toast.makeText(requireContext(), "No token. Please login again.", Toast.LENGTH_SHORT).show();
            return;
        }

        String start = safeText(etStart);
        String end = safeText(etEnd);

        if (!isValidDate(start) || !isValidDate(end)) {
            Toast.makeText(requireContext(), "Date must be YYYY-MM-DD", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading(true);

        Log.i(TAG, "Fetching daily profit: start=" + start + ", end=" + end);

        repo.fetchDailyProfit(token, start, end, new ReportRepository.Callback() {
            @Override
            public void onSuccess(@NonNull DailyProfitReport report) {
                showLoading(false);

                tvSales.setText("Sales: $" + report.totalSales);
                tvExpense.setText("Expense: $" + report.totalExpense);
                tvProfit.setText("Profit: $" + report.totalProfit);

                adapter.submit(report.rows);

                renderCharts(report);

                Log.i(TAG, "Report OK rows=" + (report.rows != null ? report.rows.size() : 0));
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                showLoading(false);
                Toast.makeText(requireContext(), "Report failed: " + statusCode, Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Report error " + statusCode + " " + message);
            }
        });
    }

    private void renderCharts(@NonNull DailyProfitReport report) {
        if (lineChart == null || barChart == null) return;

        List<Entry> salesEntries = new ArrayList<>();
        List<Entry> expenseEntries = new ArrayList<>();
        List<BarEntry> profitEntries = new ArrayList<>();

        if (report.rows == null || report.rows.isEmpty()) {
            lineChart.clear();
            barChart.clear();
            lineChart.invalidate();
            barChart.invalidate();
            return;
        }

        for (int i = 0; i < report.rows.size(); i++) {
            DailyProfitRow r = report.rows.get(i);

            float sales = (float) r.sales;
            float expense = (float) r.expense;
            float profit = (float) r.profit;

            salesEntries.add(new Entry(i, sales));
            expenseEntries.add(new Entry(i, expense));
            profitEntries.add(new BarEntry(i, profit));
        }

        // LINE: Sales vs Expense
        LineDataSet salesSet = new LineDataSet(salesEntries, "Sales");
        salesSet.setColor(Color.BLUE);
        salesSet.setLineWidth(2f);
        salesSet.setCircleRadius(3.5f);
        salesSet.setCircleColor(Color.BLUE);
        salesSet.setDrawValues(false);

        LineDataSet expenseSet = new LineDataSet(expenseEntries, "Expense");
        expenseSet.setColor(Color.RED);
        expenseSet.setLineWidth(2f);
        expenseSet.setCircleRadius(3.5f);
        expenseSet.setCircleColor(Color.RED);
        expenseSet.setDrawValues(false);

        LineData lineData = new LineData(salesSet, expenseSet);
        lineChart.setData(lineData);
        lineChart.getDescription().setEnabled(false);
        lineChart.getAxisRight().setEnabled(false);
        lineChart.invalidate();

        // BAR: Profit
        BarDataSet profitSet = new BarDataSet(profitEntries, "Profit");
        profitSet.setColor(Color.GREEN);
        profitSet.setDrawValues(false);

        BarData barData = new BarData(profitSet);
        barData.setBarWidth(0.7f);

        barChart.setData(barData);
        barChart.getDescription().setEnabled(false);
        barChart.getAxisRight().setEnabled(false);
        barChart.invalidate();
    }

    private void pickDate(EditText target) {
        Calendar cal = Calendar.getInstance();
        DatePickerDialog dlg = new DatePickerDialog(
                requireContext(),
                (dp, year, month, day) -> {
                    String out = String.format(Locale.US, "%04d-%02d-%02d", year, (month + 1), day);
                    target.setText(out);
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
        );
        dlg.show();
    }

    private void setRange(@NonNull String start, @NonNull String end) {
        if (etStart != null) etStart.setText(start);
        if (etEnd != null) etEnd.setText(end);
    }

    private String fmtDate(@NonNull Date d) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(d);
    }

    private String safeText(EditText et) {
        if (et == null || et.getText() == null) return "";
        return et.getText().toString().trim();
    }

    private boolean isValidDate(String s) {
        if (s == null) return false;
        return s.matches("^\\d{4}-\\d{2}-\\d{2}$");
    }

    private void showLoading(boolean on) {
        if (progress != null) progress.setVisibility(on ? View.VISIBLE : View.GONE);

        if (btnFetch != null) btnFetch.setEnabled(!on);
        if (btnToday != null) btnToday.setEnabled(!on);
        if (btn7Days != null) btn7Days.setEnabled(!on);
        if (btnThisMonth != null) btnThisMonth.setEnabled(!on);

        if (etStart != null) etStart.setEnabled(!on);
        if (etEnd != null) etEnd.setEnabled(!on);
    }
}
