package com.valdker.pos.ui.reports;

import android.app.DatePickerDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedDispatcher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.valdker.pos.R;
import com.valdker.pos.SessionManager;
import com.valdker.pos.base.BaseFragment;
import com.valdker.pos.models.DailyProfitReport;
import com.valdker.pos.models.DailyProfitRow;
import com.valdker.pos.repositories.ReportRepository;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReportsFragment extends BaseFragment {

    private static final String TAG = "REPORTS";
    private static final long CLICK_GUARD_MS = 700L;

    private SessionManager session;
    private ReportRepository repo;

    private EditText etStart;
    private EditText etEnd;

    private MaterialButton btnFetch;
    private Chip btnToday;
    private Chip btn7Days;
    private Chip btnThisMonth;

    private TextView tvSales;
    private TextView tvExpense;
    private TextView tvProfit;
    private ProgressBar progress;

    private LineChart lineChart;
    private BarChart barChart;
    private RecyclerView rvRows;

    private ImageView btnBack;
    private ImageView ivHeaderAction;

    private ProfitRowAdapter adapter;

    private boolean isLoading = false;
    private long lastQuickActionAt = 0L;
    private long lastRefreshClickAt = 0L;

    public ReportsFragment() {
        super(R.layout.fragment_reports);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        applyTopInset(view.findViewById(R.id.topBar));

        session = new SessionManager(requireContext());
        repo = new ReportRepository(requireContext());

        bindViews(view);
        setupHeader();
        setupRecycler();
        setupDatePickers();
        setupQuickRanges();
        setupFetchButton();

        String today = fmtDate(new Date());
        setRange(today, today);

        fetch();
    }

    private void bindViews(@NonNull View view) {
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
        rvRows = view.findViewById(R.id.rvRows);

        btnBack = view.findViewById(R.id.btnBack);
        ivHeaderAction = view.findViewById(R.id.ivHeaderAction);
    }

    private void setupHeader() {
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                if (!isAdded()) return;
                OnBackPressedDispatcher dispatcher = requireActivity().getOnBackPressedDispatcher();
                dispatcher.onBackPressed();
            });
        }

        if (ivHeaderAction != null) {
            ivHeaderAction.setOnClickListener(v -> {
                if (!isAdded()) return;
                if (isRapidRefreshClick()) return;
                fetch();
            });
        }
    }

    private void setupRecycler() {
        if (rvRows == null) return;

        rvRows.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvRows.setHasFixedSize(false);

        adapter = new ProfitRowAdapter();
        rvRows.setAdapter(adapter);
    }

    private void setupDatePickers() {
        if (etStart != null) {
            etStart.setOnClickListener(v -> {
                if (isLoading) return;
                pickDate(etStart);
            });
        }

        if (etEnd != null) {
            etEnd.setOnClickListener(v -> {
                if (isLoading) return;
                pickDate(etEnd);
            });
        }
    }

    private void setupQuickRanges() {
        if (btnToday != null) {
            btnToday.setOnClickListener(v -> {
                if (!canRunQuickAction()) return;

                String t = fmtDate(new Date());
                setRange(t, t);
                fetch();
            });
        }

        if (btn7Days != null) {
            btn7Days.setOnClickListener(v -> {
                if (!canRunQuickAction()) return;

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
                if (!canRunQuickAction()) return;

                Calendar c = Calendar.getInstance();
                String end = fmtDate(c.getTime());
                c.set(Calendar.DAY_OF_MONTH, 1);
                String start = fmtDate(c.getTime());
                setRange(start, end);
                fetch();
            });
        }
    }

    private void setupFetchButton() {
        if (btnFetch != null) {
            btnFetch.setOnClickListener(v -> {
                if (isLoading) return;
                fetch();
            });
        }
    }

    private boolean canRunQuickAction() {
        if (isLoading) return false;

        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastQuickActionAt < CLICK_GUARD_MS) {
            return false;
        }
        lastQuickActionAt = now;
        return true;
    }

    private boolean isRapidRefreshClick() {
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastRefreshClickAt < CLICK_GUARD_MS) {
            return true;
        }
        lastRefreshClickAt = now;
        return false;
    }

    private void fetch() {
        if (!isAdded()) return;
        if (isLoading) return;

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

        isLoading = true;
        showLoading(true);

        Log.i(TAG, "Fetching daily profit: start=" + start + ", end=" + end);

        repo.fetchDailyProfit(token, start, end, new ReportRepository.Callback() {
            @Override
            public void onSuccess(@NonNull DailyProfitReport report) {
                if (!isAdded()) return;

                isLoading = false;
                showLoading(false);

                tvSales.setText("Sales: $" + report.totalSales);
                tvExpense.setText("Expense: $" + report.totalExpense);
                tvProfit.setText("Profit: $" + report.totalProfit);

                if (adapter != null) {
                    adapter.submit(report.rows);
                }

                renderCharts(report);

                Log.i(TAG, "Report OK rows=" + (report.rows != null ? report.rows.size() : 0));
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                if (!isAdded()) return;

                isLoading = false;
                showLoading(false);

                Toast.makeText(
                        requireContext(),
                        "Report failed: " + statusCode,
                        Toast.LENGTH_SHORT
                ).show();

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
            DailyProfitRow row = report.rows.get(i);
            if (row == null) continue;

            salesEntries.add(new Entry(i, (float) row.sales));
            expenseEntries.add(new Entry(i, (float) row.expense));
            profitEntries.add(new BarEntry(i, (float) row.profit));
        }

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

    private void pickDate(@NonNull EditText target) {
        if (!isAdded()) return;

        Calendar cal = Calendar.getInstance();
        DatePickerDialog dialog = new DatePickerDialog(
                requireContext(),
                (dp, year, month, day) -> {
                    String out = String.format(Locale.US, "%04d-%02d-%02d", year, (month + 1), day);
                    target.setText(out);
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
        );
        dialog.show();
    }

    private void setRange(@NonNull String start, @NonNull String end) {
        if (etStart != null) etStart.setText(start);
        if (etEnd != null) etEnd.setText(end);
    }

    @NonNull
    private String fmtDate(@NonNull Date date) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date);
    }

    @NonNull
    private String safeText(@Nullable EditText et) {
        if (et == null || et.getText() == null) return "";
        return et.getText().toString().trim();
    }

    private boolean isValidDate(@Nullable String s) {
        return s != null && s.matches("^\\d{4}-\\d{2}-\\d{2}$");
    }

    private void showLoading(boolean on) {
        if (progress != null) {
            progress.setVisibility(on ? View.VISIBLE : View.GONE);
        }

        if (btnFetch != null) btnFetch.setEnabled(!on);
        if (btnToday != null) btnToday.setEnabled(!on);
        if (btn7Days != null) btn7Days.setEnabled(!on);
        if (btnThisMonth != null) btnThisMonth.setEnabled(!on);

        if (etStart != null) etStart.setEnabled(!on);
        if (etEnd != null) etEnd.setEnabled(!on);

        if (ivHeaderAction != null) ivHeaderAction.setEnabled(!on);
    }

    @Override
    public void onDestroyView() {
        if (rvRows != null) {
            rvRows.setAdapter(null);
        }

        etStart = null;
        etEnd = null;
        btnFetch = null;
        btnToday = null;
        btn7Days = null;
        btnThisMonth = null;
        tvSales = null;
        tvExpense = null;
        tvProfit = null;
        progress = null;
        lineChart = null;
        barChart = null;
        rvRows = null;
        btnBack = null;
        ivHeaderAction = null;
        adapter = null;

        super.onDestroyView();
    }
}