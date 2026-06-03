package com.valdker.pos.ui.reports;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedDispatcher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.valdker.pos.R;
import com.valdker.pos.SessionManager;
import com.valdker.pos.base.BaseFragment;
import com.valdker.pos.repositories.ReportCacheRepository;
import com.valdker.pos.repositories.ReportRepository;
import com.valdker.pos.utils.NetworkUtils;
import com.valdker.pos.utils.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ReportsFragment extends BaseFragment {

    private SessionManager session;
    private ReportCacheRepository reportCacheRepository;

    private EditText etStart;
    private EditText etEnd;
    private EditText etSearch;
    private EditText etCategory;
    private EditText etPaymentMethod;
    private MaterialButton btnFetch;
    private MaterialButton btnExport;
    private ChipGroup chipGroupReportType;
    private ChipGroup chipGroupBusinessFilter;
    private LinearLayout layoutSummary;
    private TextView tvTitle;
    private TextView tvSubtitle;
    private TextView tvState;
    private TextView tvListTitle;
    private ProgressBar progress;
    private RecyclerView rvRows;
    private ImageView btnBack;
    private ImageView ivHeaderAction;

    private ReportResultAdapter adapter;
    private String businessType = "retail";
    private String reportType = ReportRepository.TYPE_SALES;
    private String businessFilterKey = "";
    private String businessFilterValue = "";
    private boolean isLoading = false;

    public ReportsFragment() {
        super(R.layout.fragment_reports);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        applyTopInset(view.findViewById(R.id.topBar));

        session = new SessionManager(requireContext());
        reportCacheRepository = new ReportCacheRepository(requireContext());
        businessType = normalizeBusinessType(session.getShopBusinessType());

        bindViews(view);
        setupHeader();
        setupRecycler();
        setupDates();
        setupReportTypeChips();
        configureBusinessFilters();
        setupActions();

        setDefaultMonthRange();
        fetch();
    }

    private void bindViews(@NonNull View view) {
        etStart = view.findViewById(R.id.etStart);
        etEnd = view.findViewById(R.id.etEnd);
        etSearch = view.findViewById(R.id.etSearch);
        etCategory = view.findViewById(R.id.etCategory);
        etPaymentMethod = view.findViewById(R.id.etPaymentMethod);
        btnFetch = view.findViewById(R.id.btnFetch);
        btnExport = view.findViewById(R.id.btnExport);
        chipGroupReportType = view.findViewById(R.id.chipGroupReportType);
        chipGroupBusinessFilter = view.findViewById(R.id.chipGroupBusinessFilter);
        layoutSummary = view.findViewById(R.id.layoutSummary);
        tvTitle = view.findViewById(R.id.tvTitle);
        tvSubtitle = view.findViewById(R.id.tvSubtitle);
        tvState = view.findViewById(R.id.tvState);
        tvListTitle = view.findViewById(R.id.tvListTitle);
        progress = view.findViewById(R.id.progress);
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
                if (!isLoading) fetch();
            });
        }
    }

    private void setupRecycler() {
        if (rvRows == null) return;
        rvRows.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvRows.setNestedScrollingEnabled(false);
        adapter = new ReportResultAdapter();
        rvRows.setAdapter(adapter);
    }

    private void setupDates() {
        if (etStart != null) etStart.setOnClickListener(v -> {
            if (!isLoading) pickDate(etStart);
        });
        if (etEnd != null) etEnd.setOnClickListener(v -> {
            if (!isLoading) pickDate(etEnd);
        });
    }

    private void setupReportTypeChips() {
        setReportChip(R.id.chipDaily, ReportRepository.TYPE_DAILY);
        setReportChip(R.id.chipSales, ReportRepository.TYPE_SALES);
        setReportChip(R.id.chipPayments, ReportRepository.TYPE_PAYMENTS);
        setReportChip(R.id.chipShifts, ReportRepository.TYPE_SHIFTS);
        setReportChip(R.id.chipItems, ReportRepository.TYPE_ITEMS);
        updateTitles();
    }

    private void setReportChip(int id, @NonNull String type) {
        if (chipGroupReportType == null) return;
        Chip chip = chipGroupReportType.findViewById(id);
        if (chip == null) return;
        chip.setOnClickListener(v -> {
            reportType = type;
            updateTitles();
            fetch();
        });
    }

    private void configureBusinessFilters() {
        if (chipGroupBusinessFilter == null) return;

        chipGroupBusinessFilter.removeAllViews();
        businessFilterKey = "";
        businessFilterValue = "";

        if ("workshop".equals(businessType)) {
            businessFilterKey = "item_type";
            addBusinessFilter("Semua", "");
            addBusinessFilter("Menu", "MENU");
            addBusinessFilter("Service", "SERVICE");
            addBusinessFilter("Sparepart", "SPAREPART");
            setRetailFields(false);
            return;
        }

        if ("restaurant".equals(businessType)) {
            businessFilterKey = "order_type";
            addBusinessFilter("Semua", "");
            addBusinessFilter("Dine In", "DINE_IN");
            addBusinessFilter("Takeaway", "TAKEAWAY");
            addBusinessFilter("Delivery", "DELIVERY");
            setRetailFields(false);
            return;
        }

        setRetailFields(true);
    }

    private void addBusinessFilter(@NonNull String label, @NonNull String value) {
        if (!isAdded() || chipGroupBusinessFilter == null) return;
        Chip chip = new Chip(requireContext());
        chip.setText(label);
        chip.setCheckable(true);
        chip.setChecked(chipGroupBusinessFilter.getChildCount() == 0);
        chip.setOnClickListener(v -> {
            businessFilterValue = value;
            fetch();
        });
        chipGroupBusinessFilter.addView(chip);
    }

    private void setRetailFields(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        if (etSearch != null) etSearch.setVisibility(visibility);
        if (etCategory != null) etCategory.setVisibility(visibility);
    }

    private void setupActions() {
        if (btnFetch != null) {
            btnFetch.setOnClickListener(v -> {
                if (!isLoading) fetch();
            });
        }
        if (btnExport != null) {
            btnExport.setVisibility(View.GONE);
            btnExport.setEnabled(false);
            btnExport.setOnClickListener(v -> {
                if (!isAdded()) return;
                if (!NetworkUtils.isNetworkAvailable(requireContext())) {
                    Toast.makeText(requireContext(), ReportCacheRepository.EXPORT_REQUIRES_INTERNET_MESSAGE, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void fetch() {
        if (!isAdded() || isLoading) return;

        if (TextUtils.isEmpty(session.getToken())) {
            showState("No token. Please login again.");
            return;
        }

        String start = text(etStart);
        String end = text(etEnd);
        if (!isValidDate(start) || !isValidDate(end)) {
            showState("Date must be YYYY-MM-DD.");
            return;
        }

        setLoading(true);
        hideState();

        reportCacheRepository.loadReportRoomFirst(reportType, buildQuery(start, end), new ReportCacheRepository.RoomFirstCallback() {
            @Override
            public void onLocal(@NonNull ReportRepository.ReportResponse response,
                                @NonNull ReportCacheRepository.CacheInfo cacheInfo) {
                if (!isAdded()) return;
                applyResponseBusinessType(response.shop);
                render(response);
                showCacheLabel(cacheInfo);
            }

            @Override
            public void onRemote(@NonNull ReportRepository.ReportResponse response,
                                 @NonNull ReportCacheRepository.CacheInfo cacheInfo) {
                if (!isAdded()) return;
                setLoading(false);
                applyResponseBusinessType(response.shop);
                render(response);
                showCacheLabel(cacheInfo);
            }

            @Override
            public void onNoInternet(boolean hasLocalData) {
                if (!isAdded()) return;
                setLoading(false);
                if (!hasLocalData) {
                    showState(ReportCacheRepository.NO_LOCAL_DATA_MESSAGE);
                }
            }

            @Override
            public void onError(int statusCode, @NonNull String message, boolean hasLocalData) {
                if (!isAdded()) return;
                setLoading(false);
                if (!hasLocalData) {
                    showState("Report failed: " + statusCode + " " + message);
                }
            }
        });
    }

    @NonNull
    private Map<String, String> buildQuery(@NonNull String start, @NonNull String end) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("start_date", start);
        query.put("end_date", end);
        query.put("page_size", "50");

        if (!TextUtils.isEmpty(businessFilterKey) && !TextUtils.isEmpty(businessFilterValue)) {
            query.put(businessFilterKey, businessFilterValue);
        }

        String payment = text(etPaymentMethod);
        if (!TextUtils.isEmpty(payment)) query.put("payment_method", payment);

        if ("retail".equals(businessType)) {
            String search = text(etSearch);
            String category = text(etCategory);
            if (!TextUtils.isEmpty(search)) query.put("search", search);
            if (!TextUtils.isEmpty(category)) query.put("category_id", category);
        }

        return query;
    }

    private void render(@NonNull ReportRepository.ReportResponse response) {
        updateTitles();
        renderSummary(response.summary, response.breakdown);

        List<JSONObject> rows = toRows(response.results);
        if (adapter != null) adapter.submit(rows, businessType, reportType);

        if (rows.isEmpty()) {
            showState("No report data for this filter.");
        } else {
            hideState();
        }
    }

    private void renderSummary(@NonNull JSONObject summary, @NonNull JSONObject breakdown) {
        if (layoutSummary == null) return;
        layoutSummary.removeAllViews();

        LinkedHashMap<String, String> cards = new LinkedHashMap<>();

        if (ReportRepository.TYPE_SALES.equals(reportType) || ReportRepository.TYPE_ITEMS.equals(reportType)) {
            if ("workshop".equals(businessType)) {
                cards.put("Total Revenue", money(summary, "total_revenue", "revenue", "total_sales"));
                cards.put("Service Revenue", moneyAny(summary, breakdown, "service_revenue", "service"));
                cards.put("Sparepart Revenue", moneyAny(summary, breakdown, "sparepart_revenue", "sparepart"));
                cards.put("Menu Revenue", moneyAny(summary, breakdown, "menu_revenue", "menu"));
                cards.put("Net Sales", money(summary, "net_sales", "net", "net_revenue"));
            } else if ("restaurant".equals(businessType)) {
                cards.put("Total Revenue", money(summary, "total_revenue", "revenue", "total_sales"));
                cards.put("Dine In Revenue", moneyAny(summary, breakdown, "dine_in_revenue", "dine_in"));
                cards.put("Takeaway Revenue", moneyAny(summary, breakdown, "takeaway_revenue", "takeaway"));
                cards.put("Delivery Revenue", moneyAny(summary, breakdown, "delivery_revenue", "delivery"));
                cards.put("Net Sales", money(summary, "net_sales", "net", "net_revenue"));
            } else {
                cards.put("Total Revenue", money(summary, "total_revenue", "revenue", "total_sales"));
                cards.put("Product Sold", plain(summary, "product_sold", "products_sold", "qty", "quantity"));
                cards.put("Net Sales", money(summary, "net_sales", "net", "net_revenue"));
                cards.put("Gross Profit", money(summary, "gross_profit", "profit"));
                cards.put("Margin %", percent(summary, "margin", "margin_percent"));
            }
        } else {
            addGenericSummary(cards, summary);
        }

        if (cards.isEmpty()) {
            cards.put("Total Revenue", "$0.00");
            cards.put("Net Sales", "$0.00");
        }

        for (Map.Entry<String, String> entry : cards.entrySet()) {
            layoutSummary.addView(createSummaryCard(entry.getKey(), entry.getValue()));
        }
    }

    private void addGenericSummary(@NonNull LinkedHashMap<String, String> out, @NonNull JSONObject summary) {
        JSONArray names = summary.names();
        if (names == null) return;
        for (int i = 0; i < names.length() && out.size() < 6; i++) {
            String key = names.optString(i, "");
            if (TextUtils.isEmpty(key)) continue;
            Object value = summary.opt(key);
            out.put(titleize(key), value != null ? String.valueOf(value) : "-");
        }
    }

    @NonNull
    private View createSummaryCard(@NonNull String label, @NonNull String value) {
        MaterialCardView card = new MaterialCardView(requireContext());
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardLp.setMargins(0, 0, 0, reportDp(10));
        card.setLayoutParams(cardLp);
        card.setCardBackgroundColor(android.graphics.Color.WHITE);
        card.setRadius(reportDp(14));
        card.setCardElevation(0f);
        card.setStrokeColor(android.graphics.Color.parseColor("#E5E7EB"));
        card.setStrokeWidth(reportDp(1));

        LinearLayout body = new LinearLayout(requireContext());
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(reportDp(14), reportDp(12), reportDp(14), reportDp(12));

        TextView tvLabel = new TextView(requireContext());
        tvLabel.setText(label);
        tvLabel.setTextColor(android.graphics.Color.parseColor("#64748B"));
        tvLabel.setTextSize(12f);

        TextView tvValue = new TextView(requireContext());
        tvValue.setText(value);
        tvValue.setTextColor(android.graphics.Color.parseColor("#111827"));
        tvValue.setTextSize(18f);
        tvValue.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);

        body.addView(tvLabel);
        body.addView(tvValue);
        card.addView(body);
        return card;
    }

    private void applyResponseBusinessType(@NonNull JSONObject shop) {
        String responseType = shop.optString("business_type", "");
        if (TextUtils.isEmpty(responseType)) responseType = shop.optString("shop_business_type", "");
        if (TextUtils.isEmpty(responseType)) return;

        String normalized = normalizeBusinessType(responseType);
        if (!normalized.equals(businessType)) {
            businessType = normalized;
            session.setShopBusinessType(normalized);
            configureBusinessFilters();
        }
    }

    private void updateTitles() {
        if (tvTitle != null) tvTitle.setText(titleForReport());
        if (tvListTitle != null) tvListTitle.setText(titleForReport());
        if (tvSubtitle != null) {
            tvSubtitle.setText("Shop type: " + businessType.toUpperCase(Locale.US));
        }
    }

    private void showCacheLabel(@NonNull ReportCacheRepository.CacheInfo cacheInfo) {
        if (tvSubtitle == null) return;
        tvSubtitle.setText("Shop type: "
                + businessType.toUpperCase(Locale.US)
                + "\n"
                + cacheInfo.label());
    }

    @NonNull
    private String titleForReport() {
        switch (reportType) {
            case ReportRepository.TYPE_DAILY: return "Daily Summary";
            case ReportRepository.TYPE_PAYMENTS: return "Payment Report";
            case ReportRepository.TYPE_SHIFTS: return "Shift Report";
            case ReportRepository.TYPE_ITEMS: return "Product / Item Report";
            default: return "Sales Report";
        }
    }

    private void setLoading(boolean loading) {
        isLoading = loading;
        if (progress != null) progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (btnFetch != null) btnFetch.setEnabled(!loading);
        if (ivHeaderAction != null) ivHeaderAction.setEnabled(!loading);
        if (etStart != null) etStart.setEnabled(!loading);
        if (etEnd != null) etEnd.setEnabled(!loading);
        if (etSearch != null) etSearch.setEnabled(!loading);
        if (etCategory != null) etCategory.setEnabled(!loading);
        if (etPaymentMethod != null) etPaymentMethod.setEnabled(!loading);
    }

    private void showState(@NonNull String message) {
        if (tvState != null) {
            tvState.setText(message);
            tvState.setVisibility(View.VISIBLE);
        }
    }

    private void hideState() {
        if (tvState != null) tvState.setVisibility(View.GONE);
    }

    private void pickDate(@NonNull EditText target) {
        Calendar cal = Calendar.getInstance();
        DatePickerDialog dialog = new DatePickerDialog(
                requireContext(),
                (dp, year, month, day) -> target.setText(String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)),
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
        );
        dialog.show();
    }

    private void setDefaultMonthRange() {
        Calendar c = Calendar.getInstance();
        String end = fmtDate(c.getTime());
        c.set(Calendar.DAY_OF_MONTH, 1);
        String start = fmtDate(c.getTime());
        if (etStart != null) etStart.setText(start);
        if (etEnd != null) etEnd.setText(end);
    }

    @NonNull
    private static List<JSONObject> toRows(@NonNull JSONArray arr) {
        List<JSONObject> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject row = arr.optJSONObject(i);
            if (row != null) out.add(row);
        }
        return out;
    }

    @NonNull
    private static String fmtDate(@NonNull Date date) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date);
    }

    private static boolean isValidDate(@Nullable String s) {
        return s != null && s.matches("^\\d{4}-\\d{2}-\\d{2}$");
    }

    @NonNull
    private static String text(@Nullable EditText et) {
        if (et == null || et.getText() == null) return "";
        return et.getText().toString().trim();
    }

    @NonNull
    private static String normalizeBusinessType(@Nullable String value) {
        if (value == null) return "retail";
        String clean = value.trim().toLowerCase(Locale.US);
        return clean.isEmpty() ? "retail" : clean;
    }

    @NonNull
    private static String money(@NonNull JSONObject obj, @NonNull String... keys) {
        return formatMoney(firstValue(obj, keys));
    }

    @NonNull
    private static String moneyAny(@NonNull JSONObject first,
                                   @NonNull JSONObject second,
                                   @NonNull String... keys) {
        String value = firstValue(first, keys);
        if (TextUtils.isEmpty(value)) value = firstValue(second, keys);
        return formatMoney(value);
    }

    @NonNull
    private static String plain(@NonNull JSONObject obj, @NonNull String... keys) {
        String value = firstValue(obj, keys);
        return TextUtils.isEmpty(value) ? "0" : value;
    }

    @NonNull
    private static String percent(@NonNull JSONObject obj, @NonNull String... keys) {
        String value = firstValue(obj, keys);
        if (TextUtils.isEmpty(value)) return "0%";
        if (value.endsWith("%")) return value;
        return value + "%";
    }

    @NonNull
    private static String firstValue(@NonNull JSONObject obj, @NonNull String... keys) {
        for (String key : keys) {
            Object value = obj.opt(key);
            if (value == null || JSONObject.NULL.equals(value)) continue;
            String text = String.valueOf(value).trim();
            if (!text.isEmpty()) return text;
        }
        return "";
    }

    @NonNull
    private static String formatMoney(@Nullable String raw) {
        if (TextUtils.isEmpty(raw)) return "$0.00";
        try {
            return String.format(Locale.US, "$%.2f", Double.parseDouble(raw));
        } catch (Exception ignored) {
            return raw;
        }
    }

    @NonNull
    private static String titleize(@NonNull String key) {
        String[] parts = key.replace('_', ' ').split(" ");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(part.substring(0, 1).toUpperCase(Locale.US));
            if (part.length() > 1) out.append(part.substring(1));
        }
        return out.toString();
    }

    private int reportDp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroyView() {
        if (reportCacheRepository != null) reportCacheRepository.cancel();
        if (rvRows != null) rvRows.setAdapter(null);
        adapter = null;
        reportCacheRepository = null;
        super.onDestroyView();
    }
}
