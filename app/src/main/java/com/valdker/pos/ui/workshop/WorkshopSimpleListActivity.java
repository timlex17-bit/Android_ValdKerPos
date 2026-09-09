package com.valdker.pos.ui.workshop;

import android.app.AlertDialog;
import android.os.Bundle;
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

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.valdker.pos.R;
import com.valdker.pos.SessionManager;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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

    private void bindViews() {
        ImageButton btnBack = findViewById(R.id.btnBack);
        tvTitle = findViewById(R.id.tvTitle);
        tvSubtitle = findViewById(R.id.tvSubtitle);
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
        adapter = new WorkshopRowAdapter(new WorkshopRowAdapter.Listener() {
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
            fabAdd.setOnClickListener(v -> showFormDialog(null));
        }
    }

    private boolean supportsCrud() {
        return !formFields().isEmpty();
    }

    protected void loadData() {
        loading = true;
        showLoading();
        if (adapter != null) adapter.submit(new ArrayList<>());

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
                        if (adapter != null) adapter.submit(rows);
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
            Toast.makeText(this, "Create form is not configured for this module.", Toast.LENGTH_LONG).show();
            return;
        }

        boolean isEdit = row != null;
        selectedRelationIds.clear();
        View view = buildFormView(row);
        AlertDialog dialog = new AlertDialog.Builder(this)
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
                positive.setText("Saving...");
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
            input.setHint(field.label + (field.required ? " *" : ""));
            input.setBackgroundResource(R.drawable.bg_input);
            input.setPadding(dp(14), 0, dp(14), 0);
            input.setTextColor(0xFF111827);
            input.setHintTextColor(0xFF94A3B8);

            if (isCustomerRelation(field)) {
                int customerId = initialRelationId(row, field);
                if (customerId > 0) {
                    selectedRelationIds.put(field.key, customerId);
                    input.setText(initialCustomerLabel(row, customerId));
                }
                input.setInputType(InputType.TYPE_NULL);
                input.setFocusable(false);
                input.setClickable(true);
                input.setSingleLine(true);
                input.setOnClickListener(v -> showCustomerPicker(field, input));
            } else {
                input.setInputType(field.inputType);
                input.setSingleLine((field.inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) == 0);
                input.setText(initialValue(row, field));
            }

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    field.multiLine ? dp(96) : dp(52)
            );
            lp.topMargin = dp(12);
            container.addView(input, lp);
        }
        return container;
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

            if (isCustomerRelation(field)) {
                Integer customerId = selectedRelationIds.get(field.key);
                if (customerId == null || customerId <= 0) {
                    if (field.required) {
                        Toast.makeText(this, "Please select customer first.", Toast.LENGTH_LONG).show();
                        if (input != null) input.performClick();
                        return null;
                    }
                    if (customerId != null) {
                        try {
                            payload.put(field.key, JSONObject.NULL);
                        } catch (Exception ex) {
                            Toast.makeText(this, "Invalid customer", Toast.LENGTH_LONG).show();
                            return null;
                        }
                    }
                    continue;
                }
                try {
                    payload.put(field.key, customerId);
                } catch (Exception ex) {
                    Toast.makeText(this, "Invalid customer", Toast.LENGTH_LONG).show();
                    return null;
                }
                continue;
            }

            String value = input != null && input.getText() != null ? input.getText().toString().trim() : "";

            if (TextUtils.isEmpty(value)) {
                if (field.required) {
                    if (!field.relationLabel.isEmpty()) {
                        Toast.makeText(this, "Please select " + field.relationLabel + " first.", Toast.LENGTH_LONG).show();
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
                            if (input != null) input.setError("Value cannot be negative");
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
                    input.setError("Invalid number");
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
            Toast.makeText(this, "Cannot delete item without a valid id.", Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Delete " + screenTitle())
                .setMessage("Delete \"" + row.title + "\"?")
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
    private static List<WorkshopRow> parseRows(@NonNull JSONArray arr) {
        List<WorkshopRow> rows = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject item = arr.optJSONObject(i);
            if (item == null) continue;
            rows.add(new WorkshopRow(item.optInt("id", 0), titleOf(item, i), subtitleOf(item), item));
        }
        return rows;
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
        return subtitle.isEmpty() ? item.toString() : subtitle;
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

        final String key;
        final String label;
        final int kind;
        final boolean required;
        final String relationLabel;
        final String defaultValue;
        final int inputType;
        final boolean multiLine;

        private FormField(@NonNull String key,
                          @NonNull String label,
                          int kind,
                          boolean required,
                          @Nullable String relationLabel,
                          @Nullable String defaultValue,
                          int inputType,
                          boolean multiLine) {
            this.key = key;
            this.label = label;
            this.kind = kind;
            this.required = required;
            this.relationLabel = relationLabel == null ? "" : relationLabel;
            this.defaultValue = defaultValue == null ? "" : defaultValue;
            this.inputType = inputType;
            this.multiLine = multiLine;
        }

        @NonNull
        protected static FormField text(@NonNull String key, @NonNull String label, boolean required) {
            return new FormField(key, label, KIND_TEXT, required, "", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, false);
        }

        @NonNull
        protected static FormField multiline(@NonNull String key, @NonNull String label, boolean required) {
            return new FormField(key, label, KIND_TEXT, required, "", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, true);
        }

        @NonNull
        protected static FormField integer(@NonNull String key, @NonNull String label, boolean required, @Nullable String defaultValue) {
            return new FormField(key, label, KIND_INT, required, "", defaultValue, InputType.TYPE_CLASS_NUMBER, false);
        }

        @NonNull
        protected static FormField decimal(@NonNull String key, @NonNull String label, boolean required, @Nullable String defaultValue) {
            return new FormField(key, label, KIND_DECIMAL, required, "", defaultValue, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL, false);
        }

        @NonNull
        protected static FormField relation(@NonNull String key, @NonNull String label, boolean required, @NonNull String relationLabel) {
            return new FormField(key, label, KIND_RELATION, required, relationLabel, "", InputType.TYPE_CLASS_NUMBER, false);
        }

        @NonNull
        protected static FormField date(@NonNull String key, @NonNull String label, boolean required) {
            return new FormField(key, label, KIND_TEXT, required, "", "", InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_DATE, false);
        }

        @NonNull
        protected static FormField time(@NonNull String key, @NonNull String label, boolean required) {
            return new FormField(key, label, KIND_TEXT, required, "", "", InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_TIME, false);
        }

        @NonNull
        protected FormField defaultValue(@NonNull String value) {
            return new FormField(key, label, kind, required, relationLabel, value, inputType, multiLine);
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
        private final Listener listener;

        WorkshopRowAdapter(@NonNull Listener listener) {
            this.listener = listener;
        }

        void submit(@NonNull List<WorkshopRow> next) {
            rows.clear();
            rows.addAll(next);
            notifyDataSetChanged();
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

            VH(@NonNull View itemView) {
                super(itemView);
                this.title = itemView.findViewById(R.id.tvRowTitle);
                this.subtitle = itemView.findViewById(R.id.tvRowSubtitle);
                this.btnEdit = itemView.findViewById(R.id.btnEdit);
                this.btnDelete = itemView.findViewById(R.id.btnDelete);
            }
        }
    }
}
