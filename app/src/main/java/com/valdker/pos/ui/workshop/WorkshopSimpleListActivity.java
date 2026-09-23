package com.valdker.pos.ui.workshop;

import androidx.appcompat.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.valdker.pos.R;
import com.valdker.pos.SessionManager;
import com.valdker.pos.ui.common.RecordPickerDialog;
import com.valdker.pos.ui.common.SystemBars;
import com.valdker.pos.models.Customer;
import com.valdker.pos.network.ApiClient;
import com.valdker.pos.network.ApiConfig;
import com.valdker.pos.utils.Toast;
import com.valdker.pos.workshop.CustomerPickerDialog;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public abstract class WorkshopSimpleListActivity extends AppCompatActivity {
    private static final int TIMEOUT_MS = 20000;
    private static final int MAX_RETRIES = 1;
    private static final float BACKOFF_MULT = 1.2f;

    private SessionManager session;
    private WorkshopRowAdapter adapter;
    private TextView tvTitle;
    private TextView tvSubtitle;
    private TextView tvEmpty;
    private TextView tvError;
    private ProgressBar progressBar;
    private RecyclerView recyclerView;
    private FloatingActionButton fabAdd;
    private boolean loading = false;
    private final Map<String, Integer> selectedRelationIds = new HashMap<>();
    /** Nilai enum yang dipilih, dipetakan dari label yang dibaca pengguna. */
    private final Map<String, String> selectedChoiceValues = new HashMap<>();

    @NonNull
    protected abstract String moduleKey();

    @NonNull
    protected abstract String endpoint();

    @NonNull
    protected abstract String screenTitle();

    @NonNull
    protected abstract String emptyMessage();

    @NonNull
    protected List<FormField> formFields() {
        return Collections.emptyList();
    }

    @NonNull
    protected String logTag() {
        return getClass().getSimpleName();
    }

    @NonNull
    protected String subtitle() {
        return "Workshop module";
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(logTag(), "Opening " + screenTitle());
        session = new SessionManager(this);
        if (!session.isWorkshop() || !session.canAccessModule(moduleKey())) {
            showAccessDeniedAndFinish();
            return;
        }
        setContentView(R.layout.activity_workshop_module_list);
        setupSystemBars();
        bindViews();
        setupViews();
        loadData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (session != null && !loading && adapter != null) {
            loadData();
        }
    }

    protected void showAccessDeniedAndFinish() {
        Log.w(logTag(), "Access denied for module " + moduleKey());
        Toast.makeText(this, getString(R.string.msg_permission_denied), Toast.LENGTH_SHORT).show();
        finish();
    }

    /**
     * Tujuh layar bengkel memakai kerangka ini, jadi bilah sistemnya disiapkan
     * di sini sekali - bukan disalin ke tiap turunan. Bilah atas ungu yang
     * tumbuh sampai ke belakang bilah status, dan daftar dijauhkan dari batang
     * gestur di dasar layar.
     */
    private void setupSystemBars() {
        SystemBars.apply(this);
        SystemBars.padTopBar(findViewById(R.id.topBar));
        SystemBars.padBottom(findViewById(R.id.moduleListContent));
    }

    private void bindViews() {
        ImageButton btnBack = findViewById(R.id.btnBack);
        tvTitle = findViewById(R.id.tvTopBarTitle);
        tvSubtitle = findViewById(R.id.tvTopBarSubtitle);
        // Subjudul bilah atas bawaannya GONE; layar daftar ini selalu punya
        // subjudul, jadi ia dinyalakan di sini.
        if (tvSubtitle != null) tvSubtitle.setVisibility(View.VISIBLE);
        tvEmpty = findViewById(R.id.tvEmpty);
        tvError = findViewById(R.id.tvError);
        progressBar = findViewById(R.id.progressBar);
        recyclerView = findViewById(R.id.rvItems);
        fabAdd = findViewById(R.id.fabAdd);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }
    }

    private void setupViews() {
        if (tvTitle != null) tvTitle.setText(screenTitle());
        if (tvSubtitle != null) tvSubtitle.setText(subtitle());

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        boolean crud = supportsCrud();
        adapter = new WorkshopRowAdapter(crud, new WorkshopRowAdapter.Listener() {
            @Override
            public void onEdit(@NonNull WorkshopRow row) {
                showFormDialog(row);
            }

            @Override
            public void onDelete(@NonNull WorkshopRow row) {
                confirmDelete(row);
            }
        });
        recyclerView.setAdapter(adapter);

        if (fabAdd != null) {
            // Modul tanpa formulir - retur pembelian saat ini - dulu tetap
            // menampilkan tombol tambah dan tombol Edit di tiap baris, yang
            // hanya menghasilkan pesan "Create form is not configured".
            // Kontrol yang bisa ditekan dan tidak melakukan apa pun lebih baik
            // disembunyikan sampai formulirnya benar-benar ada.
            if (crud) {
                fabAdd.setOnClickListener(v -> showFormDialog(null));
            } else {
                fabAdd.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Menaruh data baru tanpa memindahkan daftar.
     *
     * <p>{@code notifyDataSetChanged()} membuang seluruh keadaan daftar,
     * termasuk posisi gulirnya. Dua penjagaan: kalau isinya sama persis -
     * yang menjadi kasus biasa saat kembali dari layar lain - tidak ada
     * pemberitahuan sama sekali; kalau berubah, posisi gulir disimpan dan
     * dipasang kembali setelahnya.
     */
    private void submitKeepingScroll(@NonNull List<WorkshopRow> rows) {
        if (adapter == null) return;
        Parcelable scroll = null;
        if (recyclerView != null && recyclerView.getLayoutManager() != null) {
            scroll = recyclerView.getLayoutManager().onSaveInstanceState();
        }
        if (!adapter.submit(rows)) return;
        if (scroll != null && recyclerView.getLayoutManager() != null) {
            recyclerView.getLayoutManager().onRestoreInstanceState(scroll);
        }
    }

    private boolean supportsCrud() {
        return !formFields().isEmpty();
    }

    protected void loadData() {
        loading = true;
        // Pemintal hanya untuk pemuatan pertama. Setiap kembali dari layar lain
        // memicu onResume() -> loadData(); kalau daftarnya dikosongkan dan
        // disembunyikan lebih dulu, layar berkedip kosong lalu isinya muncul
        // lagi dari baris teratas - itulah "lari ke atas" yang dikeluhkan.
        if (adapter == null || adapter.getItemCount() == 0) {
            showLoading();
        }

        String url = ApiConfig.url(session, endpoint());
        Log.i(logTag(), "GET " + url);
        StringRequest req = new StringRequest(
                Request.Method.GET,
                url,
                response -> {
                    loading = false;
                    try {
                        List<WorkshopRow> rows = parseRows(extractArray(response));
                        Log.i(logTag(), "API success. rows=" + rows.size());
                        submitKeepingScroll(rows);
                        if (rows.isEmpty()) {
                            showEmpty();
                        } else {
                            showList();
                        }
                    } catch (Exception e) {
                        Log.e(logTag(), "API parse failure", e);
                        showError("Parse error: " + e.getMessage());
                    }
                },
                error -> {
                    loading = false;
                    String message = buildErrorMessage(error.networkResponse, error);
                    Log.w(logTag(), "API failure: " + message);
                    showError(message);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return authHeaders();
            }
        };
        enqueue(req);
    }

    private void showFormDialog(@Nullable WorkshopRow row) {
        if (!supportsCrud()) {
            Toast.makeText(this, R.string.msg_create_form_not_configured, Toast.LENGTH_LONG).show();
            return;
        }

        boolean isEdit = row != null;
        selectedRelationIds.clear();
        selectedChoiceValues.clear();
        View view = buildFormView(row);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(view)
                .setPositiveButton(isEdit ? getString(R.string.action_update) : getString(R.string.action_create), null)
                .setNegativeButton(getString(R.string.action_cancel), null)
                .create();

        dialog.setOnShowListener(d -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positive.setOnClickListener(v -> {
                JSONObject payload = buildPayload(view);
                if (payload == null) return;
                positive.setEnabled(false);
                positive.setText(R.string.msg_loading);
                if (isEdit) {
                    update(row.id, payload, dialog);
                } else {
                    create(payload, dialog);
                }
            });
        });

        dialog.show();
    }

    @NonNull
    private View buildFormView(@Nullable WorkshopRow row) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        container.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText(row == null ? "Add " + screenTitle() : "Edit " + screenTitle());
        title.setTextColor(0xFF111827);
        title.setTextSize(20);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        container.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        for (FormField field : formFields()) {
            EditText input = new EditText(this);
            input.setTag(field.key);
            input.setHint(hintFor(field));
            input.setBackgroundResource(R.drawable.bg_input);
            input.setPadding(dp(14), 0, dp(14), 0);
            input.setTextColor(0xFF111827);
            input.setHintTextColor(0xFF94A3B8);

            switch (field.kind) {
                case FormField.KIND_RELATION: {
                    int id = initialRelationId(row, field);
                    if (id > 0) {
                        selectedRelationIds.put(field.key, id);
                        input.setText(initialRelationLabel(row, field, id));
                    }
                    makeTapOnly(input);
                    if (isCustomerRelation(field)) {
                        input.setOnClickListener(v -> showCustomerPicker(field, input));
                    } else {
                        input.setOnClickListener(v -> showRelationPicker(field, input));
                    }
                    break;
                }
                case FormField.KIND_CHOICE: {
                    String value = initialValue(row, field);
                    if (!value.isEmpty()) {
                        selectedChoiceValues.put(field.key, value);
                        input.setText(choiceLabelOf(field, value));
                    }
                    makeTapOnly(input);
                    input.setOnClickListener(v -> showChoicePicker(field, input));
                    break;
                }
                case FormField.KIND_DATE: {
                    input.setText(initialValue(row, field));
                    makeTapOnly(input);
                    input.setOnClickListener(v -> showDatePicker(input));
                    break;
                }
                case FormField.KIND_TIME: {
                    input.setText(trimSeconds(initialValue(row, field)));
                    makeTapOnly(input);
                    input.setOnClickListener(v -> showTimePicker(input));
                    break;
                }
                default: {
                    input.setInputType(field.inputType);
                    input.setSingleLine((field.inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) == 0);
                    input.setText(initialValue(row, field));
                    break;
                }
            }

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    field.multiLine ? dp(96) : dp(52)
            );
            lp.topMargin = dp(12);
            container.addView(input, lp);
        }

        // Formulir work order dan riwayat servis punya tujuh kolom; pada layar
        // 720x1612 kolom terakhir jatuh di bawah tombol dialog dan tidak bisa
        // dijangkau sama sekali. Dialog tidak menggulir sendiri, jadi isinya
        // yang dibungkus.
        android.widget.ScrollView scroller = new android.widget.ScrollView(this);
        scroller.setFillViewport(true);
        scroller.addView(container, new android.widget.FrameLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return scroller;
    }

    /**
     * Kolom yang diisi lewat dialog tidak boleh bisa diketik. Kalau papan
     * ketiknya tetap muncul, pengguna akan mengetik sesuatu yang tidak pernah
     * dibaca - nilai yang dikirim berasal dari pilihan, bukan dari teksnya.
     */
    private static void makeTapOnly(@NonNull EditText input) {
        input.setInputType(InputType.TYPE_NULL);
        input.setFocusable(false);
        input.setFocusableInTouchMode(false);
        input.setCursorVisible(false);
        input.setLongClickable(false);
        input.setSingleLine(true);
        input.setClickable(true);
    }

    @NonNull
    private String hintFor(@NonNull FormField field) {
        String suffix;
        switch (field.kind) {
            case FormField.KIND_RELATION:
            case FormField.KIND_CHOICE:
                suffix = " - " + getString(R.string.workshop_field_tap_to_pick);
                break;
            case FormField.KIND_DATE:
            case FormField.KIND_TIME:
                suffix = " - " + getString(R.string.workshop_field_tap_to_set);
                break;
            default:
                suffix = "";
                break;
        }
        return field.label + (field.required ? " *" : "") + suffix;
    }

    private void showRelationPicker(@NonNull FormField field, @NonNull EditText input) {
        // Relasi opsional mendapat baris "Kosongkan" di paling atas; yang wajib
        // tidak, karena mengosongkannya hanya akan ditolak saat disimpan.
        RecordPickerDialog.Option clearRow = field.required
                ? null
                : new RecordPickerDialog.Option("", getString(R.string.workshop_pick_none));

        RecordPickerDialog.fromEndpoint(
                this,
                pickerTitleFor(field),
                field.relationEndpoint,
                clearRow,
                item -> {
                    int id = item.optInt("id", 0);
                    if (id <= 0) return null;
                    return new RecordPickerDialog.Option(
                            String.valueOf(id), relationOptionLabel(field, item));
                },
                option -> {
                    int id = option.asId();
                    selectedRelationIds.put(field.key, id);
                    input.setText(id > 0 ? option.label : "");
                });
    }

    private void showChoicePicker(@NonNull FormField field, @NonNull EditText input) {
        String current = selectedChoiceValues.get(field.key);
        int checked = -1;
        for (int i = 0; i < field.choiceValues.length; i++) {
            if (field.choiceValues[i].equals(current)) {
                checked = i;
                break;
            }
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(field.label)
                .setSingleChoiceItems(field.choiceLabels, checked, (d, which) -> {
                    selectedChoiceValues.put(field.key, field.choiceValues[which]);
                    input.setText(field.choiceLabels[which]);
                    d.dismiss();
                })
                .setNegativeButton(getString(R.string.action_cancel), null)
                .show();
    }

    private void showDatePicker(@NonNull EditText input) {
        Calendar cal = Calendar.getInstance();
        String current = input.getText() == null ? "" : input.getText().toString().trim();
        String[] parts = current.split("-");
        if (parts.length == 3) {
            try {
                cal.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2]));
            } catch (NumberFormatException ignored) {
                // Teks lama yang tidak terbaca cukup diabaikan; hari ini tetap
                // menjadi titik awal yang masuk akal.
            }
        }
        new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> input.setText(
                        String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth)),
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
        ).show();
    }

    private void showTimePicker(@NonNull EditText input) {
        Calendar cal = Calendar.getInstance();
        String current = input.getText() == null ? "" : input.getText().toString().trim();
        String[] parts = current.split(":");
        if (parts.length >= 2) {
            try {
                cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(parts[0]));
                cal.set(Calendar.MINUTE, Integer.parseInt(parts[1]));
            } catch (NumberFormatException ignored) {
            }
        }
        new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> input.setText(
                        String.format(Locale.US, "%02d:%02d", hourOfDay, minute)),
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                true
        ).show();
    }

    /**
     * Server mengembalikan jam sebagai {@code HH:MM:SS}; yang dipilih pengguna
     * hanya jam dan menit, jadi detiknya dipangkas supaya kolomnya tidak
     * berubah tampilan setiap kali dibuka ulang.
     */
    @NonNull
    private static String trimSeconds(@NonNull String time) {
        String[] parts = time.split(":");
        return parts.length >= 2 ? parts[0] + ":" + parts[1] : time;
    }

    /** Judul dialog pemilih. Dipetakan dari kunci supaya tetap satu bahasa. */
    @NonNull
    protected String pickerTitleFor(@NonNull FormField field) {
        switch (field.key) {
            case "customer":
                return getString(R.string.workshop_pick_customer);
            case "vehicle":
                return getString(R.string.workshop_pick_vehicle);
            case "mechanic":
                return getString(R.string.workshop_pick_mechanic);
            case "work_order":
                return getString(R.string.workshop_pick_work_order);
            default:
                return field.label;
        }
    }

    /**
     * Satu baris di dalam dialog pemilih. Turunan boleh menimpanya, tetapi
     * bentuk baku ini sudah mencakup ketiga relasi yang ada.
     */
    @NonNull
    protected String relationOptionLabel(@NonNull FormField field, @NonNull JSONObject item) {
        int id = item.optInt("id", 0);
        switch (field.key) {
            case "vehicle": {
                String plate = item.optString("plate_number", "").trim();
                String make = firstNonEmpty(
                        (item.optString("brand", "").trim() + " " + item.optString("model", "").trim()).trim(),
                        item.optString("vehicle_type", "").trim());
                String owner = item.optString("customer_name", "").trim();
                String head = plate.isEmpty() ? getString(R.string.workshop_row_unnamed, id) : plate;
                String tail = firstNonEmpty(make, owner);
                return tail.isEmpty() ? head : head + " - " + tail;
            }
            case "mechanic": {
                String name = item.optString("name", "").trim();
                String extra = firstNonEmpty(
                        item.optString("specialization", ""),
                        item.optString("phone", ""));
                String head = name.isEmpty() ? getString(R.string.workshop_row_unnamed, id) : name;
                return extra.isEmpty() ? head : head + " - " + extra;
            }
            case "work_order": {
                String head = getString(R.string.workshop_work_order_short, id);
                String tail = firstNonEmpty(
                        item.optString("vehicle_plate_number", ""),
                        item.optString("customer_name", ""),
                        item.optString("complaint", ""));
                return tail.isEmpty() ? head : head + " - " + tail;
            }
            default: {
                String label = firstNonEmpty(
                        item.optString("name", ""),
                        item.optString("title", ""),
                        item.optString("plate_number", ""));
                return label.isEmpty() ? getString(R.string.workshop_row_unnamed, id) : label;
            }
        }
    }

    /** Label relasi yang sudah tersimpan, dibaca dari baris yang sedang diedit. */
    @NonNull
    private String initialRelationLabel(@Nullable WorkshopRow row, @NonNull FormField field, int id) {
        if (isCustomerRelation(field)) return initialCustomerLabel(row, id);
        if (row != null) {
            Object nested = row.raw.opt(field.key);
            if (nested instanceof JSONObject) {
                String label = relationOptionLabel(field, (JSONObject) nested).trim();
                if (!label.isEmpty()) return label;
            }
            String flat = firstNonEmpty(
                    row.raw.optString(field.key + "_name", ""),
                    row.raw.optString(field.key + "_plate_number", ""),
                    row.raw.optString(field.key + "_number", ""));
            if (!flat.isEmpty()) return flat;
        }
        if ("work_order".equals(field.key)) {
            return getString(R.string.workshop_work_order_short, id);
        }
        return getString(R.string.workshop_row_unnamed, id);
    }

    @NonNull
    private String choiceLabelOf(@NonNull FormField field, @NonNull String value) {
        for (int i = 0; i < field.choiceValues.length; i++) {
            if (field.choiceValues[i].equals(value)) return field.choiceLabels[i];
        }
        // Nilai yang tidak dikenal berasal dari server, bukan dari layar ini -
        // tampilkan apa adanya alih-alih menyembunyikannya.
        return value;
    }

    private void showCustomerPicker(@NonNull FormField field, @NonNull EditText input) {
        CustomerPickerDialog picker = CustomerPickerDialog.newInstance(false, !field.required);
        picker.setListener(customer -> applySelectedCustomer(field, input, customer));
        picker.show(getSupportFragmentManager(), "workshop_customer_picker");
    }

    private void applySelectedCustomer(@NonNull FormField field,
                                       @NonNull EditText input,
                                       @NonNull Customer customer) {
        if (customer.id <= 0) {
            selectedRelationIds.put(field.key, 0);
            input.setText("");
            return;
        }
        selectedRelationIds.put(field.key, customer.id);
        input.setText(customerDisplayLabel(customer));
    }

    @Nullable
    private JSONObject buildPayload(@NonNull View formView) {
        JSONObject payload = new JSONObject();
        for (FormField field : formFields()) {
            EditText input = formView.findViewWithTag(field.key);

            if (field.kind == FormField.KIND_RELATION) {
                Integer relationId = selectedRelationIds.get(field.key);
                if (relationId == null || relationId <= 0) {
                    if (field.required) {
                        Toast.makeText(this,
                                getString(R.string.workshop_pick_required, relationNameOf(field)),
                                Toast.LENGTH_LONG).show();
                        if (input != null) input.performClick();
                        return null;
                    }
                    // Null hanya dikirim kalau pengguna memang menyentuh kolom
                    // ini dan mengosongkannya. Tanpa syarat itu, setiap
                    // penyuntingan akan ikut menghapus relasi yang tidak
                    // ditampilkan di layar.
                    if (relationId != null) {
                        try {
                            payload.put(field.key, JSONObject.NULL);
                        } catch (Exception ex) {
                            return null;
                        }
                    }
                    continue;
                }
                try {
                    payload.put(field.key, relationId);
                } catch (Exception ex) {
                    return null;
                }
                continue;
            }

            if (field.kind == FormField.KIND_CHOICE) {
                String choice = selectedChoiceValues.get(field.key);
                if (choice == null || choice.trim().isEmpty()) {
                    if (field.required) {
                        Toast.makeText(this,
                                getString(R.string.workshop_pick_required, field.label),
                                Toast.LENGTH_LONG).show();
                        if (input != null) input.performClick();
                        return null;
                    }
                    continue;
                }
                try {
                    payload.put(field.key, choice);
                } catch (Exception ex) {
                    return null;
                }
                continue;
            }

            String value = input != null && input.getText() != null ? input.getText().toString().trim() : "";

            if (TextUtils.isEmpty(value)) {
                if (field.required) {
                    if (field.kind == FormField.KIND_DATE || field.kind == FormField.KIND_TIME) {
                        Toast.makeText(this,
                                getString(R.string.workshop_pick_required, field.label),
                                Toast.LENGTH_LONG).show();
                        if (input != null) input.performClick();
                    } else if (!field.relationLabel.isEmpty()) {
                        Toast.makeText(this, getString(R.string.msg_select_required, field.relationLabel),
                    Toast.LENGTH_LONG).show();
                    } else if (input != null) {
                        input.setError(getString(R.string.error_required));
                        input.requestFocus();
                    }
                    return null;
                }
                continue;
            }

            try {
                switch (field.kind) {
                    case FormField.KIND_INT:
                    case FormField.KIND_RELATION:
                        payload.put(field.key, Integer.parseInt(value));
                        break;
                    case FormField.KIND_DECIMAL:
                        BigDecimal decimal = new BigDecimal(value);
                        if (decimal.compareTo(BigDecimal.ZERO) < 0) {
                            if (input != null) input.setError(getString(R.string.msg_quantity_must_be_non_negative));
                            return null;
                        }
                        payload.put(field.key, decimal.toPlainString());
                        break;
                    default:
                        payload.put(field.key, value);
                        break;
                }
            } catch (NumberFormatException ex) {
                if (input != null) {
                    input.setError(getString(R.string.msg_invalid_number_format));
                    input.requestFocus();
                }
                return null;
            } catch (Exception ex) {
                Toast.makeText(this, ex.getMessage() == null ? "Invalid form data" : ex.getMessage(), Toast.LENGTH_LONG).show();
                return null;
            }
        }
        return payload;
    }

    @NonNull
    private String relationNameOf(@NonNull FormField field) {
        switch (field.key) {
            case "customer":
                return getString(R.string.workshop_label_customer);
            case "vehicle":
                return getString(R.string.workshop_label_vehicle);
            case "mechanic":
                return getString(R.string.workshop_label_mechanic);
            case "work_order":
                return getString(R.string.workshop_label_work_order);
            default:
                return field.label;
        }
    }

    private static boolean isCustomerRelation(@NonNull FormField field) {
        return field.kind == FormField.KIND_RELATION && "customer".equals(field.key);
    }

    private static int initialRelationId(@Nullable WorkshopRow row, @NonNull FormField field) {
        if (row == null) return 0;
        Object value = row.raw.opt(field.key);
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof JSONObject) return ((JSONObject) value).optInt("id", 0);
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return 0;
        }
    }

    @NonNull
    private static String initialCustomerLabel(@Nullable WorkshopRow row, int customerId) {
        if (row != null) {
            String name = row.raw.optString("customer_name", "").trim();
            JSONObject customer = row.raw.optJSONObject("customer");
            if (name.isEmpty() && customer != null) {
                name = customer.optString("name", "").trim();
            }
            if (!name.isEmpty()) return name;
        }
        return "Customer #" + customerId;
    }

    @NonNull
    private static String customerDisplayLabel(@NonNull Customer customer) {
        String name = customer.name == null ? "" : customer.name.trim();
        String cell = customer.cell == null ? "" : customer.cell.trim();
        String label = name.isEmpty() ? "Customer #" + customer.id : name;
        return cell.isEmpty() ? label : label + " - " + cell;
    }

    private void create(@NonNull JSONObject payload, @NonNull AlertDialog dialog) {
        String url = ApiConfig.url(session, endpoint());
        Log.i(logTag(), "POST " + url + " payload=" + payload);
        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.POST,
                url,
                payload,
                response -> {
                    Log.i(logTag(), "Create success id=" + response.optInt("id", 0));
                    dialog.dismiss();
                    Toast.makeText(this, screenTitle() + " created", Toast.LENGTH_SHORT).show();
                    loadData();
                },
                error -> {
                    String message = buildErrorMessage(error.networkResponse, error);
                    Log.w(logTag(), "Create failed: " + message);
                    resetDialogButton(dialog, getString(R.string.action_create));
                    showError(message);
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return jsonHeaders();
            }
        };
        enqueue(req);
    }

    private void update(int id, @NonNull JSONObject payload, @NonNull AlertDialog dialog) {
        String url = ApiConfig.url(session, endpoint() + id + "/");
        Log.i(logTag(), "PATCH " + url + " payload=" + payload);
        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.PATCH,
                url,
                payload,
                response -> {
                    Log.i(logTag(), "Update success id=" + response.optInt("id", id));
                    dialog.dismiss();
                    Toast.makeText(this, screenTitle() + " updated", Toast.LENGTH_SHORT).show();
                    loadData();
                },
                error -> {
                    String message = buildErrorMessage(error.networkResponse, error);
                    Log.w(logTag(), "Update failed: " + message);
                    resetDialogButton(dialog, getString(R.string.action_update));
                    showError(message);
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return jsonHeaders();
            }
        };
        enqueue(req);
    }

    private void confirmDelete(@NonNull WorkshopRow row) {
        if (row.id <= 0) {
            Toast.makeText(this, R.string.msg_cannot_delete_without_id, Toast.LENGTH_LONG).show();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.title_delete_named, screenTitle()))
                .setMessage(getString(R.string.msg_delete_named_confirm, row.title))
                .setPositiveButton(getString(R.string.action_delete), (dialog, which) -> delete(row))
                .setNegativeButton(getString(R.string.action_cancel), null)
                .show();
    }

    private void delete(@NonNull WorkshopRow row) {
        String url = ApiConfig.url(session, endpoint() + row.id + "/");
        Log.i(logTag(), "DELETE " + url);
        showLoading();
        StringRequest req = new StringRequest(
                Request.Method.DELETE,
                url,
                response -> {
                    Log.i(logTag(), "Delete success id=" + row.id);
                    Toast.makeText(this, screenTitle() + " deleted", Toast.LENGTH_SHORT).show();
                    loadData();
                },
                error -> {
                    String message = buildErrorMessage(error.networkResponse, error);
                    Log.w(logTag(), "Delete failed: " + message);
                    showError(message);
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return authHeaders();
            }
        };
        enqueue(req);
    }

    private void resetDialogButton(@NonNull AlertDialog dialog, @NonNull String text) {
        Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (button != null) {
            button.setEnabled(true);
            button.setText(text);
        }
    }

    private void showError(@NonNull String message) {
        if (progressBar != null) progressBar.setVisibility(View.GONE);
        if (recyclerView != null) recyclerView.setVisibility(View.GONE);
        if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
        if (tvError != null) {
            tvError.setText(message);
            tvError.setVisibility(View.VISIBLE);
        }
    }

    private void showLoading() {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        if (recyclerView != null) recyclerView.setVisibility(View.GONE);
        if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
        if (tvError != null) tvError.setVisibility(View.GONE);
    }

    private void showList() {
        if (progressBar != null) progressBar.setVisibility(View.GONE);
        if (recyclerView != null) recyclerView.setVisibility(View.VISIBLE);
        if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
        if (tvError != null) tvError.setVisibility(View.GONE);
    }

    private void showEmpty() {
        Log.i(logTag(), "Showing empty state: " + emptyMessage());
        if (progressBar != null) progressBar.setVisibility(View.GONE);
        if (recyclerView != null) recyclerView.setVisibility(View.GONE);
        if (tvError != null) tvError.setVisibility(View.GONE);
        if (tvEmpty != null) {
            tvEmpty.setText(emptyMessage());
            tvEmpty.setVisibility(View.VISIBLE);
        }
    }

    private void enqueue(@NonNull Request<?> request) {
        request.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, MAX_RETRIES, BACKOFF_MULT));
        request.setShouldCache(false);
        request.setTag(screenTitle());
        ApiClient.getInstance(this).add(request);
    }

    @NonNull
    private Map<String, String> jsonHeaders() {
        Map<String, String> h = authHeaders();
        h.put("Content-Type", "application/json");
        return h;
    }

    @NonNull
    private Map<String, String> authHeaders() {
        Map<String, String> h = new HashMap<>();
        h.put("Accept", "application/json");
        h.put("Authorization", "Token " + session.getToken().trim());
        return h;
    }

    @NonNull
    private static JSONArray extractArray(@Nullable String response) throws Exception {
        Object parsed = new JSONTokener(response == null ? "[]" : response).nextValue();
        if (parsed instanceof JSONArray) return (JSONArray) parsed;
        if (parsed instanceof JSONObject) {
            JSONArray results = ((JSONObject) parsed).optJSONArray("results");
            return results != null ? results : new JSONArray();
        }
        return new JSONArray();
    }

    @NonNull
    private List<WorkshopRow> parseRows(@NonNull JSONArray arr) {
        List<WorkshopRow> rows = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject item = arr.optJSONObject(i);
            if (item == null) continue;
            rows.add(new WorkshopRow(item.optInt("id", 0), titleOf(item, i), describeRow(item), item));
        }
        return rows;
    }

    /**
     * Baris kedua pada kartu daftar.
     *
     * <p>Bentuk bakunya menebak dari sekumpulan nama field yang lazim. Tebakan
     * itu meleset untuk kendaraan - serializer kendaraan tidak memuat satu pun
     * di antaranya - dan dulu sisanya jatuh ke {@code item.toString()}, yang
     * menumpahkan seluruh objek JSON Django ke dalam kartu informasi mobil.
     * Karena itu tiap modul menimpa metode ini dengan kalimat yang memang
     * dibaca manusia, dan bentuk bakunya kini mengembalikan kosong alih-alih
     * menampilkan isi mentah.
     */
    @NonNull
    protected String describeRow(@NonNull JSONObject item) {
        return subtitleOf(item);
    }

    @NonNull
    private String initialValue(@Nullable WorkshopRow row, @NonNull FormField field) {
        if (row == null) return field.defaultValue;
        Object value = row.raw.opt(field.key);
        if (value == null || JSONObject.NULL.equals(value)) return "";
        return String.valueOf(value);
    }

    @NonNull
    private static String titleOf(@NonNull JSONObject item, int index) {
        String title = firstNonEmpty(
                item.optString("name", ""),
                item.optString("plate_number", ""),
                item.optString("reference_no", ""),
                item.optString("booking_no", ""),
                item.optString("invoice_number", ""),
                item.optString("customer_name", "")
        );
        if (!title.isEmpty()) return title;
        int id = item.optInt("id", index + 1);
        return "Item #" + id;
    }

    @NonNull
    private static String subtitleOf(@NonNull JSONObject item) {
        String subtitle = firstNonEmpty(
                item.optString("status", ""),
                item.optString("vehicle_plate_number", ""),
                item.optString("mechanic_name", ""),
                item.optString("booking_date", ""),
                item.optString("service_date", ""),
                item.optString("complaint", ""),
                item.optString("notes", ""),
                item.optString("created_at", "")
        );
        // Sengaja kosong ketika tidak ada yang cocok: baris subjudul yang
        // kosong lebih baik daripada satu objek JSON mentah di dalam kartu.
        return subtitle;
    }

    @NonNull
    private static String firstNonEmpty(@NonNull String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty() && !"null".equalsIgnoreCase(value.trim())) {
                return value.trim();
            }
        }
        return "";
    }

    @NonNull
    private static String buildErrorMessage(@Nullable NetworkResponse nr, @NonNull Exception fallbackErr) {
        if (nr == null) {
            String message = fallbackErr.getMessage();
            return message != null && !message.trim().isEmpty() ? message : "Network error";
        }
        try {
            if (nr.data != null) {
                String body = new String(nr.data, StandardCharsets.UTF_8).trim();
                if (body.length() > 240) body = body.substring(0, 240) + "...";
                return "HTTP " + nr.statusCode + " - " + body;
            }
        } catch (Exception ignored) {
        }
        return "HTTP " + nr.statusCode;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    protected static final class FormField {
        static final int KIND_TEXT = 0;
        static final int KIND_INT = 1;
        static final int KIND_DECIMAL = 2;
        static final int KIND_RELATION = 3;
        /** Satu dari sekumpulan nilai tetap (enum server), dipilih bukan diketik. */
        static final int KIND_CHOICE = 4;
        static final int KIND_DATE = 5;
        static final int KIND_TIME = 6;

        final String key;
        final String label;
        final int kind;
        final boolean required;
        final String relationLabel;
        final String defaultValue;
        final int inputType;
        final boolean multiLine;
        /** Endpoint sumber untuk KIND_RELATION; kosong untuk relasi customer. */
        final String relationEndpoint;
        /** Nilai yang dikirim ke server, sejajar dengan choiceLabels. */
        final String[] choiceValues;
        /** Yang dibaca pengguna. */
        final String[] choiceLabels;

        private FormField(@NonNull String key,
                          @NonNull String label,
                          int kind,
                          boolean required,
                          @Nullable String relationLabel,
                          @Nullable String defaultValue,
                          int inputType,
                          boolean multiLine,
                          @Nullable String relationEndpoint,
                          @Nullable String[] choiceValues,
                          @Nullable String[] choiceLabels) {
            this.key = key;
            this.label = label;
            this.kind = kind;
            this.required = required;
            this.relationLabel = relationLabel == null ? "" : relationLabel;
            this.defaultValue = defaultValue == null ? "" : defaultValue;
            this.inputType = inputType;
            this.multiLine = multiLine;
            this.relationEndpoint = relationEndpoint == null ? "" : relationEndpoint;
            this.choiceValues = choiceValues == null ? new String[0] : choiceValues;
            this.choiceLabels = choiceLabels == null ? new String[0] : choiceLabels;
        }

        @NonNull
        protected static FormField text(@NonNull String key, @NonNull String label, boolean required) {
            return new FormField(key, label, KIND_TEXT, required, "", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, false, null, null, null);
        }

        @NonNull
        protected static FormField multiline(@NonNull String key, @NonNull String label, boolean required) {
            return new FormField(key, label, KIND_TEXT, required, "", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, true, null, null, null);
        }

        @NonNull
        protected static FormField integer(@NonNull String key, @NonNull String label, boolean required, @Nullable String defaultValue) {
            return new FormField(key, label, KIND_INT, required, "", defaultValue, InputType.TYPE_CLASS_NUMBER, false, null, null, null);
        }

        @NonNull
        protected static FormField decimal(@NonNull String key, @NonNull String label, boolean required, @Nullable String defaultValue) {
            return new FormField(key, label, KIND_DECIMAL, required, "", defaultValue, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL, false, null, null, null);
        }

        @NonNull
        protected static FormField relation(@NonNull String key, @NonNull String label, boolean required, @NonNull String relationLabel) {
            return new FormField(key, label, KIND_RELATION, required, relationLabel, "", InputType.TYPE_CLASS_NUMBER, false, null, null, null);
        }

        /**
         * Relasi yang dipilih dari daftar catatan endpoint lain.
         *
         * <p>{@code endpoint} kosong berarti relasi pelanggan, yang punya
         * pemilihnya sendiri (CustomerPickerDialog) dengan pencarian.
         */
        @NonNull
        protected static FormField relation(@NonNull String key,
                                            @NonNull String label,
                                            boolean required,
                                            @NonNull String relationLabel,
                                            @NonNull String endpoint) {
            return new FormField(key, label, KIND_RELATION, required, relationLabel, "",
                    InputType.TYPE_NULL, false, endpoint, null, null);
        }

        /** Nilai tetap dari server - dipilih, tidak pernah diketik. */
        @NonNull
        protected static FormField choice(@NonNull String key,
                                          @NonNull String label,
                                          boolean required,
                                          @NonNull String[] values,
                                          @NonNull String[] labels,
                                          @Nullable String defaultValue) {
            return new FormField(key, label, KIND_CHOICE, required, "", defaultValue,
                    InputType.TYPE_NULL, false, null, values, labels);
        }

        @NonNull
        protected static FormField date(@NonNull String key, @NonNull String label, boolean required) {
            return new FormField(key, label, KIND_DATE, required, "", "", InputType.TYPE_NULL, false, null, null, null);
        }

        @NonNull
        protected static FormField time(@NonNull String key, @NonNull String label, boolean required) {
            return new FormField(key, label, KIND_TIME, required, "", "", InputType.TYPE_NULL, false, null, null, null);
        }

        @NonNull
        protected FormField defaultValue(@NonNull String value) {
            return new FormField(key, label, kind, required, relationLabel, value, inputType, multiLine,
                    relationEndpoint, choiceValues, choiceLabels);
        }
    }

    private static final class WorkshopRow {
        final int id;
        final String title;
        final String subtitle;
        final JSONObject raw;

        WorkshopRow(int id, @NonNull String title, @NonNull String subtitle, @NonNull JSONObject raw) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.raw = raw;
        }
    }

    private static final class WorkshopRowAdapter extends RecyclerView.Adapter<WorkshopRowAdapter.VH> {
        interface Listener {
            void onEdit(@NonNull WorkshopRow row);
            void onDelete(@NonNull WorkshopRow row);
        }

        private final List<WorkshopRow> rows = new ArrayList<>();
        private final boolean showActions;
        private final Listener listener;

        WorkshopRowAdapter(boolean showActions, @NonNull Listener listener) {
            this.showActions = showActions;
            this.listener = listener;
        }

        /** @return true kalau isinya benar-benar berubah. */
        boolean submit(@NonNull List<WorkshopRow> next) {
            if (sameContent(next)) return false;
            rows.clear();
            rows.addAll(next);
            notifyDataSetChanged();
            return true;
        }

        private boolean sameContent(@NonNull List<WorkshopRow> next) {
            if (rows.size() != next.size()) return false;
            for (int i = 0; i < rows.size(); i++) {
                WorkshopRow a = rows.get(i);
                WorkshopRow b = next.get(i);
                if (a.id != b.id
                        || !a.title.equals(b.title)
                        || !a.subtitle.equals(b.subtitle)) {
                    return false;
                }
            }
            return true;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_workshop_module_row, parent, false);
            return new VH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            WorkshopRow row = rows.get(position);
            holder.title.setText(row.title);
            holder.subtitle.setText(row.subtitle);
            // Subjudul kosong berarti modulnya memang tidak punya keterangan
            // baris; menyisakan barisnya membuat tinggi kartu tidak rata.
            holder.subtitle.setVisibility(row.subtitle.isEmpty() ? View.GONE : View.VISIBLE);
            if (holder.actions != null) {
                holder.actions.setVisibility(showActions ? View.VISIBLE : View.GONE);
            }
            holder.btnEdit.setOnClickListener(v -> listener.onEdit(row));
            holder.btnDelete.setOnClickListener(v -> listener.onDelete(row));
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        static final class VH extends RecyclerView.ViewHolder {
            final TextView title;
            final TextView subtitle;
            final Button btnEdit;
            final Button btnDelete;
            final View actions;

            VH(@NonNull View itemView) {
                super(itemView);
                this.title = itemView.findViewById(R.id.tvRowTitle);
                this.subtitle = itemView.findViewById(R.id.tvRowSubtitle);
                this.actions = itemView.findViewById(R.id.rowActions);
                this.btnEdit = itemView.findViewById(R.id.btnEdit);
                this.btnDelete = itemView.findViewById(R.id.btnDelete);
            }
        }
    }
}
