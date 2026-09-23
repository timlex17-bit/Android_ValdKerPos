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
import com.valdker.pos.money.Money;
import com.valdker.pos.SessionManager;
import com.valdker.pos.base.BaseFragment;
import com.valdker.pos.repositories.ReportCacheRepository;
import com.valdker.pos.reports.ReportFilterRules;
import com.valdker.pos.reports.ReportSummaryRules;
import com.valdker.pos.repositories.ReportRepository;
import com.valdker.pos.ui.common.RecordPickerDialog;
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

    /**
     * Penyaring mana yang sah untuk laporan ini. Sebelum respons pertama tiba
     * dipakai tebakan berdasarkan jenis usaha; setelah itu jawaban server
     * ({@code filters.accepted}) yang menentukan.
     */
    private ReportFilterRules filterRules = ReportFilterRules.fallbackFor("retail", ReportRepository.TYPE_SALES);

    // Nilai yang dipilih, dipisah dari labelnya: yang satu dikirim ke server,
    // yang lain ditampilkan. Dulu keduanya adalah teks yang sama yang diketik
    // pengguna - itulah kenapa kategori harus diketik sebagai NOMOR.
    private String productFilterValue = "";
    private String categoryFilterValue = "";
    private String paymentFilterValue = "";

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
        setupFilterPickers();
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
        // Chip "Barang" mengikuti bahasa jenis usahanya, sama seperti judul
        // laporannya: restoran menjual menu, bengkel menjual jasa dan suku
        // cadang, dan "Products / Items" tidak berarti apa-apa bagi keduanya.
        if (chipGroupReportType != null) {
            Chip itemsChip = chipGroupReportType.findViewById(R.id.chipItems);
            if (itemsChip != null) {
                itemsChip.setText(ReportSummaryRules.itemsChipResFor(businessType));
            }
        }
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
            // Penyaring per-item hanya berlaku di laporan Items, jadi daftar
            // kolom yang tampil ikut berubah begitu jenis laporannya berganti.
            filterRules = ReportFilterRules.fallbackFor(businessType, reportType);
            applyFilterVisibility();
            updateTitles();
            fetch();
        });
    }

    private void configureBusinessFilters() {
        if (chipGroupBusinessFilter == null) return;

        chipGroupBusinessFilter.removeAllViews();
        businessFilterKey = "";
        businessFilterValue = "";

        filterRules = ReportFilterRules.fallbackFor(businessType, reportType);

        if ("workshop".equals(businessType)) {
            businessFilterKey = "item_type";
            addBusinessFilter("Semua", "");
            addBusinessFilter("Menu", "MENU");
            addBusinessFilter("Service", "SERVICE");
            addBusinessFilter("Sparepart", "SPAREPART");
            applyFilterVisibility();
            return;
        }

        if ("restaurant".equals(businessType)) {
            businessFilterKey = "order_type";
            addBusinessFilter("Semua", "");
            addBusinessFilter("Dine In", "DINE_IN");
            addBusinessFilter("Takeaway", "TAKEAWAY");
            addBusinessFilter("Delivery", "DELIVERY");
            applyFilterVisibility();
            return;
        }

        applyFilterVisibility();
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

    /**
     * Menampilkan hanya penyaring yang benar-benar dibaca server untuk toko
     * dan laporan ini.
     *
     * <p>Sebelumnya kolom Category selalu ada untuk ritel dan selalu hilang
     * untuk yang lain, tanpa memandang laporan yang dibuka. Restoran kehilangan
     * penyaring kategori menu yang sebenarnya didukung server
     * ({@code menu_category}), dan laporan Shift tetap menampilkan kolom
     * produk yang tidak pernah berpengaruh.
     */
    private void applyFilterVisibility() {
        setFilterVisible(etSearch, filterRules.showsProductFilter());
        setFilterVisible(etCategory, filterRules.showsCategoryFilter());
        setFilterVisible(etPaymentMethod, filterRules.showsPaymentFilter());
    }

    private void setFilterVisible(@Nullable EditText field, boolean visible) {
        if (field == null) return;
        field.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) {
            // Penyaring yang disembunyikan tidak boleh diam-diam tetap ikut
            // terkirim; kalau tidak, pengguna melihat hasil tersaring tanpa
            // ada satu pun kolom di layar yang menjelaskan kenapa.
            if (field == etSearch) {
                productFilterValue = "";
            } else if (field == etCategory) {
                categoryFilterValue = "";
            } else if (field == etPaymentMethod) {
                paymentFilterValue = "";
            }
            field.setText("");
        }
    }

    private void setupFilterPickers() {
        bindPicker(etSearch, R.string.report_filter_product, this::openProductPicker);
        bindPicker(etCategory, R.string.report_filter_category, this::openCategoryPicker);
        bindPicker(etPaymentMethod, R.string.report_filter_payment, this::openPaymentPicker);
    }

    /** Kolom penyaring diisi lewat dialog, jadi papan ketik tidak boleh muncul. */
    private void bindPicker(@Nullable EditText field, int labelRes, @NonNull Runnable onTap) {
        if (field == null) return;
        field.setHint(getString(labelRes) + " - " + getString(R.string.report_filter_tap_to_pick));
        field.setInputType(android.text.InputType.TYPE_NULL);
        field.setFocusable(false);
        field.setFocusableInTouchMode(false);
        field.setCursorVisible(false);
        field.setLongClickable(false);
        field.setSingleLine(true);
        field.setClickable(true);
        field.setOnClickListener(v -> {
            if (!isLoading) onTap.run();
        });
    }

    @NonNull
    private RecordPickerDialog.Option allOption() {
        return new RecordPickerDialog.Option("", getString(R.string.report_filter_all));
    }

    private void openProductPicker() {
        if (!isAdded()) return;
        boolean byId = filterRules.productFilterUsesId();
        RecordPickerDialog.fromEndpoint(
                requireContext(),
                getString(R.string.report_pick_product),
                "api/products/?page_size=1000",
                allOption(),
                item -> {
                    String name = item.optString("name", "").trim();
                    if (name.isEmpty()) return null;
                    // Nilai yang dikirim mengikuti penyaring yang didukung:
                    // product_id mencocokkan satu baris, search mencocokkan
                    // nama/kode/SKU.
                    String value = byId ? String.valueOf(item.optInt("id", 0)) : name;
                    if (byId && item.optInt("id", 0) <= 0) return null;
                    String code = item.optString("code", "").trim();
                    return new RecordPickerDialog.Option(
                            value, code.isEmpty() ? name : name + " - " + code);
                },
                option -> {
                    productFilterValue = option.value;
                    if (etSearch != null) etSearch.setText(option.isEmpty() ? "" : option.label);
                    fetch();
                });
    }

    private void openCategoryPicker() {
        if (!isAdded()) return;
        RecordPickerDialog.fromEndpoint(
                requireContext(),
                getString(R.string.report_pick_category),
                "api/categories/",
                allOption(),
                item -> {
                    int id = item.optInt("id", 0);
                    String name = item.optString("name", "").trim();
                    if (id <= 0 || name.isEmpty()) return null;
                    return new RecordPickerDialog.Option(String.valueOf(id), name);
                },
                option -> {
                    categoryFilterValue = option.value;
                    if (etCategory != null) etCategory.setText(option.isEmpty() ? "" : option.label);
                    fetch();
                });
    }

    private void openPaymentPicker() {
        if (!isAdded()) return;
        RecordPickerDialog.fromEndpoint(
                requireContext(),
                getString(R.string.report_pick_payment),
                "api/payment-methods/",
                allOption(),
                item -> {
                    // Server menyimpan KODE metode di Order.payment_method
                    // (pos/services/payment_service.py), bukan namanya dan
                    // bukan id-nya.
                    String code = item.optString("code", "").trim();
                    String name = item.optString("name", "").trim();
                    if (code.isEmpty()) return null;
                    return new RecordPickerDialog.Option(code, name.isEmpty() ? code : name);
                },
                option -> {
                    paymentFilterValue = option.value;
                    if (etPaymentMethod != null) {
                        etPaymentMethod.setText(option.isEmpty() ? "" : option.label);
                    }
                    fetch();
                });
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

        // Nama penyaingnya ditentukan ReportFilterRules, bukan dipaku di sini:
        // ritel memakai category_id, restoran memakai menu_category, dan
        // mengirim nama yang salah berarti server mengabaikannya diam-diam.
        if (!TextUtils.isEmpty(paymentFilterValue) && filterRules.showsPaymentFilter()) {
            query.put(ReportFilterRules.KEY_PAYMENT_METHOD, paymentFilterValue);
        }
        if (!TextUtils.isEmpty(productFilterValue) && filterRules.showsProductFilter()) {
            query.put(filterRules.productFilterKey(), productFilterValue);
        }
        if (!TextUtils.isEmpty(categoryFilterValue) && filterRules.showsCategoryFilter()) {
            query.put(filterRules.categoryFilterKey(), categoryFilterValue);
        }

        return query;
    }

    private void render(@NonNull ReportRepository.ReportResponse response) {
        applyAcceptedFilters(response.filters);
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

        // Kartu mana yang muncul ditentukan ReportSummaryRules - satu tempat
        // yang tahu nama field server untuk tiap laporan dan tiap jenis usaha.
        // Sebelumnya nama itu ditulis langsung di sini dan sebagian SALAH:
        // bengkel mencari "service_revenue" sementara server mengirim
        // "total_service_revenue", jadi kartunya selalu $0.00.
        for (ReportSummaryRules.Card card : ReportSummaryRules.cardsFor(reportType, businessType)) {
            String value = firstValue(summary, card.keys);
            if (TextUtils.isEmpty(value)) value = firstValue(breakdown, card.keys);
            if (TextUtils.isEmpty(value)) continue;

            cards.put(getString(card.labelRes), formatByRule(value, card.format));
        }

        addPaymentBreakdown(cards, breakdown);

        if (cards.isEmpty()) {
            // Dulu di sini dipasang "Total Revenue $0.00" dan "Net Sales
            // $0.00". Angka yang tidak pernah dikirim server tidak boleh
            // ditampilkan sebagai nol - pemilik toko membacanya sebagai
            // "tidak ada penjualan", padahal artinya "tidak ada jawaban".
            showState(getString(R.string.report_no_summary));
            return;
        }

        for (Map.Entry<String, String> entry : cards.entrySet()) {
            layoutSummary.addView(createSummaryCard(entry.getKey(), entry.getValue()));
        }
    }

    /**
     * Rincian per metode pembayaran.
     *
     * <p>Server sudah mengirimnya sebagai {@code breakdown.by_method} pada
     * laporan pembayaran, dan layar ini tidak pernah menampilkannya - padahal
     * "berapa yang masuk lewat tunai dibanding transfer" justru pertanyaan
     * utama pemilik toko pada laporan itu.
     */
    private void addPaymentBreakdown(@NonNull LinkedHashMap<String, String> cards,
                                     @NonNull JSONObject breakdown) {
        JSONArray byMethod = breakdown.optJSONArray("by_method");
        if (byMethod == null || byMethod.length() == 0) return;

        for (int i = 0; i < byMethod.length(); i++) {
            JSONObject row = byMethod.optJSONObject(i);
            if (row == null) continue;

            String name = firstValue(row, "payment_method__name", "payment_method", "name");
            if (TextUtils.isEmpty(name) || "null".equalsIgnoreCase(name)) {
                name = getString(R.string.report_payment_unpaid);
            }
            String total = firstValue(row, "total", "amount");
            if (TextUtils.isEmpty(total)) continue;

            String count = firstValue(row, "count");
            String label = getString(R.string.report_breakdown_by_method) + " - " + name;
            String value = formatMoney(total);
            if (!TextUtils.isEmpty(count)) value = value + "  (" + count + ")";

            cards.put(label, value);
        }
    }

    @NonNull
    private String formatByRule(@NonNull String raw, @NonNull ReportSummaryRules.Format format) {
        switch (format) {
            case COUNT:
                return raw;
            case PERCENT:
                return raw.endsWith("%") ? raw : raw + "%";
            default:
                return formatMoney(raw);
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

    /**
     * Mengambil daftar penyaring yang sah dari respons.
     *
     * <p>Server mengirimkannya di {@code filters.accepted} dan aplikasi ini
     * belum pernah membacanya, sehingga layar menebak sendiri - dan tebakannya
     * meleset untuk restoran, yang sebenarnya mendukung {@code menu_category}.
     */
    private void applyAcceptedFilters(@NonNull JSONObject filters) {
        JSONArray accepted = filters.optJSONArray("accepted");
        if (accepted == null || accepted.length() == 0) return;

        List<String> keys = new ArrayList<>();
        for (int i = 0; i < accepted.length(); i++) {
            String key = accepted.optString(i, "");
            if (!TextUtils.isEmpty(key)) keys.add(key);
        }
        if (keys.isEmpty()) return;

        filterRules = ReportFilterRules.of(keys, reportType);
        applyFilterVisibility();
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
        String title = titleForReport();
        if (tvTitle != null) tvTitle.setText(title);
        if (tvListTitle != null) tvListTitle.setText(title);
        if (tvSubtitle != null) tvSubtitle.setText(subtitleText());
    }

    /**
     * Baris di bawah judul: jenis usaha dalam kata yang dibaca manusia, lalu
     * rentang tanggal yang sedang ditampilkan. Sebelumnya isinya
     * "Shop type: WORKSHOP" - enum server apa adanya, tanpa menyebut periode
     * yang sedang dilihat sama sekali.
     */
    @NonNull
    private String subtitleText() {
        String business = getString(ReportSummaryRules.businessLabelRes(businessType));
        String start = text(etStart);
        String end = text(etEnd);
        String range = TextUtils.isEmpty(start) || TextUtils.isEmpty(end)
                ? ""
                : (start.equals(end) ? start : start + " - " + end);
        return TextUtils.isEmpty(range)
                ? business
                : getString(R.string.report_subtitle, business, range);
    }

    /**
     * Subjudul dengan keterangan kesegaran data ditempel di bawahnya. Memakai
     * {@link #subtitleText()} yang sama, supaya jenis usaha dan periode tidak
     * pernah tampil dalam dua bentuk berbeda di layar yang sama.
     */
    private void showCacheLabel(@NonNull ReportCacheRepository.CacheInfo cacheInfo) {
        if (tvSubtitle == null) return;
        tvSubtitle.setText(subtitleText() + "\n" + cacheInfo.label());
    }

    /**
     * Judul laporan dalam bahasa jenis usahanya. "Product / Item Report" tidak
     * berarti apa-apa bagi pemilik restoran yang menjual menu, atau bengkel
     * yang menjual jasa dan suku cadang.
     */
    @NonNull
    private String titleForReport() {
        return getString(ReportSummaryRules.titleResFor(reportType, businessType));
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
    private static String firstValue(@NonNull JSONObject obj, @NonNull String... keys) {
        for (String key : keys) {
            Object value = obj.opt(key);
            if (value == null || JSONObject.NULL.equals(value)) continue;
            String text = String.valueOf(value).trim();
            if (!text.isEmpty()) return text;
        }
        return "";
    }

    /**
     * Nominal dari server sudah berupa desimal eksak; diformat lewat Money
     * (BigDecimal), bukan Double.parseDouble lalu "%.2f". Jalur double
     * membulatkan HALF_UP dan berbeda dari server pada batas .005, sehingga
     * angka di layar bisa satu sen berbeda dari angka di database.
     */
    @NonNull
    private static String formatMoney(@Nullable String raw) {
        if (TextUtils.isEmpty(raw)) return Money.zero().format();
        Money parsed = Money.of(raw);
        if (parsed.isZero() && !isZeroText(raw)) return raw;
        return "$" + parsed.toPlainString();
    }

    /** Membedakan "nol sungguhan" dari teks yang gagal diurai. */
    private static boolean isZeroText(@Nullable String raw) {
        if (raw == null) return false;
        String clean = raw.trim();
        if (clean.isEmpty()) return false;
        try {
            return new java.math.BigDecimal(clean).signum() == 0;
        } catch (Exception ignored) {
            return false;
        }
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
