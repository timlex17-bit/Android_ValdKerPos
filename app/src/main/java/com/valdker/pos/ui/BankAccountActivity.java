package com.valdker.pos.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import com.valdker.pos.utils.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.valdker.pos.R;
import com.valdker.pos.SessionManager;
import com.valdker.pos.adapters.BankAccountAdapter;
import com.valdker.pos.models.BankAccount;
import com.valdker.pos.network.ApiClient;
import com.valdker.pos.network.ApiConfig;
import com.valdker.pos.repositories.AdminMasterCacheRepository;
import com.valdker.pos.utils.ErrorHandler;
import com.valdker.pos.utils.NetworkUtils;

import org.json.JSONException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class BankAccountActivity extends AppCompatActivity implements BankAccountAdapter.OnBankActionListener {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private View emptyState;
    private TextView tvEmpty;
    private EditText etSearchBank;
    private ImageView btnBack;
    private ImageView ivHeaderAction;
    private FloatingActionButton fabAddBank;
    private SwipeRefreshLayout swipeRefreshLayout;

    private SessionManager sessionManager;
    private AdminMasterCacheRepository cacheRepo;
    private boolean isLoading = false;

    private final ArrayList<BankAccount> bankList = new ArrayList<>();
    private final ArrayList<BankAccount> filteredBankList = new ArrayList<>();
    private BankAccountAdapter adapter;

    private static final String ENDPOINT_BANK_ACCOUNTS = "api/bank-accounts/";
    private static final int TIMEOUT_MS = 20000;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bank_account);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.parseColor("#22C55E"));
        }

        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) {
            controller.setAppearanceLightStatusBars(false);
        }

        sessionManager = new SessionManager(this);
        cacheRepo = new AdminMasterCacheRepository(this);

        if (!sessionManager.canAccessMenu("bank_accounts")) {
            Toast.makeText(this, getString(R.string.error_no_access_bank), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        initViews();
        applyTopInset(findViewById(R.id.topBar));
        setupRecycler();
        setupActions();
        loadBankAccounts();
    }

    private void applyTopInset(View target) {
        if (target == null) return;

        final int initialLeft = target.getPaddingLeft();
        final int initialTop = target.getPaddingTop();
        final int initialRight = target.getPaddingRight();
        final int initialBottom = target.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(target, (v, insets) -> {
            int topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            v.setPadding(
                    initialLeft,
                    initialTop + topInset,
                    initialRight,
                    initialBottom
            );
            return insets;
        });

        ViewCompat.requestApplyInsets(target);
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recyclerBankAccounts);
        progressBar = findViewById(R.id.progressBar);
        emptyState = findViewById(R.id.emptyState);
        tvEmpty = findViewById(R.id.tvEmpty);
        etSearchBank = findViewById(R.id.etSearchBank);
        btnBack = findViewById(R.id.btnBack);
        ivHeaderAction = findViewById(R.id.ivHeaderAction);
        fabAddBank = findViewById(R.id.fabAddBank);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshBankAccounts);
    }

    private void setupRecycler() {
        adapter = new BankAccountAdapter(this, filteredBankList, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void setupActions() {
        btnBack.setOnClickListener(v -> finish());

        ivHeaderAction.setOnClickListener(v -> loadBankAccounts());

        fabAddBank.setOnClickListener(v -> showBankDialog(null));

        swipeRefreshLayout.setOnRefreshListener(this::loadBankAccounts);

        etSearchBank.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterBankAccounts(s != null ? s.toString() : "");
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void loadBankAccounts() {
        if (isLoading) {
            swipeRefreshLayout.setRefreshing(false);
            return;
        }
        isLoading = true;
        if (!swipeRefreshLayout.isRefreshing()) {
            progressBar.setVisibility(View.VISIBLE);
        }
        if (emptyState != null) emptyState.setVisibility(View.GONE);

        cacheRepo.loadBankAccounts(localAccounts -> {
            boolean hasLocal = !localAccounts.isEmpty();
            if (hasLocal) {
                bankList.clear();
                bankList.addAll(localAccounts);
                filterBankAccounts(etSearchBank.getText() != null ? etSearchBank.getText().toString() : "");
            }

            if (!NetworkUtils.isNetworkAvailable(this)) {
                isLoading = false;
                progressBar.setVisibility(View.GONE);
                swipeRefreshLayout.setRefreshing(false);
                if (!hasLocal) {
                    showLocalEmpty();
                }
                return;
            }

            fetchBankAccountsFromApi(hasLocal);
        });
    }

    private void fetchBankAccountsFromApi(boolean hadLocalData) {
        String url = ApiConfig.url(sessionManager, ENDPOINT_BANK_ACCOUNTS);

        StringRequest request = new StringRequest(
                Request.Method.GET,
                url,
                response -> {
                    isLoading = false;
                    progressBar.setVisibility(View.GONE);
                    swipeRefreshLayout.setRefreshing(false);

                    try {
                        JSONArray items = extractResultsArray(response);
                        ArrayList<BankAccount> remoteAccounts = new ArrayList<>();

                        for (int i = 0; i < items.length(); i++) {
                            JSONObject obj = items.optJSONObject(i);
                            if (obj != null) {
                                remoteAccounts.add(BankAccount.fromJson(obj));
                            }
                        }
                        cacheRepo.saveBankAccounts(remoteAccounts, cachedAccounts -> {
                            bankList.clear();
                            bankList.addAll(cachedAccounts);
                            filterBankAccounts(etSearchBank.getText() != null ? etSearchBank.getText().toString() : "");
                        });
                    } catch (Exception e) {
                        Toast.makeText(this, "Parse error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                },
                error -> {
                    isLoading = false;
                    progressBar.setVisibility(View.GONE);
                    swipeRefreshLayout.setRefreshing(false);
                    if (!hadLocalData && bankList.isEmpty()) {
                        showLocalEmpty();
                    } else if (emptyState != null) {
                        emptyState.setVisibility(bankList.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                    ErrorHandler.handleApiError(this, error);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return buildHeaders();
            }
        };

        request.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, 1, 1.0f));
        ApiClient.getInstance(this).add(request);
    }

    private static JSONArray extractResultsArray(String response) throws Exception {
        Object parsed = new JSONTokener(response == null ? "[]" : response).nextValue();

        if (parsed instanceof JSONArray) {
            return (JSONArray) parsed;
        }

        if (parsed instanceof JSONObject) {
            JSONArray results = ((JSONObject) parsed).optJSONArray("results");
            return results != null ? results : new JSONArray();
        }

        return new JSONArray();
    }

    private void filterBankAccounts(String keyword) {
        filteredBankList.clear();

        String query = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);

        if (query.isEmpty()) {
            filteredBankList.addAll(bankList);
        } else {
            for (BankAccount item : bankList) {
                String name = safe(item.getName());
                String bankName = safe(item.getBankName());
                String accountNumber = safe(item.getAccountNumber());
                String accountHolder = safe(item.getAccountHolder());
                String accountType = safe(item.getAccountType());

                if (name.contains(query)
                        || bankName.contains(query)
                        || accountNumber.contains(query)
                        || accountHolder.contains(query)
                        || accountType.contains(query)) {
                    filteredBankList.add(item);
                }
            }
        }

        adapter.notifyDataSetChanged();
        if (recyclerView != null) {
            recyclerView.setVisibility(filteredBankList.isEmpty() ? View.GONE : View.VISIBLE);
        }
        if (emptyState != null) {
            emptyState.setVisibility(filteredBankList.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private void createBankAccount(JSONObject body, Dialog dialog) {
        if (!NetworkUtils.isNetworkAvailable(this)) {
            Toast.makeText(this, AdminMasterCacheRepository.INTERNET_REQUIRED_MESSAGE, Toast.LENGTH_SHORT).show();
            return;
        }
        String url = ApiConfig.url(sessionManager, ENDPOINT_BANK_ACCOUNTS);

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                url,
                body,
                response -> {
                    Toast.makeText(this, getString(R.string.bank_create_success), Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    loadBankAccounts();
                },
                error -> ErrorHandler.handleApiError(this, error)
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return buildHeaders();
            }
        };

        request.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, 1, 1.0f));
        ApiClient.getInstance(this).add(request);
    }

    private void updateBankAccount(int bankId, JSONObject body, Dialog dialog) {
        if (!NetworkUtils.isNetworkAvailable(this)) {
            Toast.makeText(this, AdminMasterCacheRepository.INTERNET_REQUIRED_MESSAGE, Toast.LENGTH_SHORT).show();
            return;
        }
        String url = ApiConfig.url(sessionManager, ENDPOINT_BANK_ACCOUNTS + bankId + "/");

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.PATCH,
                url,
                body,
                response -> {
                    Toast.makeText(this, getString(R.string.bank_update_success), Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    loadBankAccounts();
                },
                error -> ErrorHandler.handleApiError(this, error)
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return buildHeaders();
            }
        };

        request.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, 1, 1.0f));
        ApiClient.getInstance(this).add(request);
    }

    private void deleteBankAccount(BankAccount item) {
        if (!NetworkUtils.isNetworkAvailable(this)) {
            Toast.makeText(this, AdminMasterCacheRepository.INTERNET_REQUIRED_MESSAGE, Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.bank_delete_title))
                .setMessage(getString(
                        R.string.bank_delete_message,
                        item.getBankName() != null ? item.getBankName() : "-",
                        item.getName() != null ? item.getName() : "-"
                ))
                .setPositiveButton(getString(R.string.dialog_delete), (dialog, which) -> {
                    String url = ApiConfig.url(sessionManager, ENDPOINT_BANK_ACCOUNTS + item.getId() + "/");

                    JsonObjectRequest request = new JsonObjectRequest(
                            Request.Method.DELETE,
                            url,
                            null,
                            response -> {
                                Toast.makeText(this, getString(R.string.bank_delete_success), Toast.LENGTH_SHORT).show();
                                loadBankAccounts();
                            },
                            error -> ErrorHandler.handleApiError(this, error)
                    ) {
                        @Override
                        public Map<String, String> getHeaders() {
                            return buildHeaders();
                        }
                    };

                    request.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, 1, 1.0f));
                    ApiClient.getInstance(this).add(request);
                })
                .setNegativeButton(getString(R.string.dialog_cancel), null)
                .show();
    }

    private void showBankDialog(@Nullable BankAccount item) {
        if (!NetworkUtils.isNetworkAvailable(this)) {
            Toast.makeText(this, AdminMasterCacheRepository.INTERNET_REQUIRED_MESSAGE, Toast.LENGTH_SHORT).show();
            return;
        }
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_bank_account, null, false);
        dialog.setContentView(view);

        TextView tvDialogTitle = view.findViewById(R.id.tvDialogTitle);
        EditText etName = view.findViewById(R.id.etName);
        EditText etBankName = view.findViewById(R.id.etBankName);
        EditText etAccountNumber = view.findViewById(R.id.etAccountNumber);
        EditText etAccountHolder = view.findViewById(R.id.etAccountHolder);
        Spinner spinnerAccountType = view.findViewById(R.id.spinnerAccountType);
        EditText etOpeningBalance = view.findViewById(R.id.etOpeningBalance);
        Switch switchActive = view.findViewById(R.id.switchActive);
        EditText etNote = view.findViewById(R.id.etNote);
        Button btnCancel = view.findViewById(R.id.btnCancel);
        Button btnSave = view.findViewById(R.id.btnSave);

        String[] accountTypes = {
                getString(R.string.bank_account_type_bank),
                getString(R.string.bank_account_type_ewallet),
                getString(R.string.bank_account_type_qris)
        };

        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                accountTypes
        );
        spinnerAccountType.setAdapter(typeAdapter);

        boolean isEdit = item != null;
        tvDialogTitle.setText(isEdit
                ? getString(R.string.title_edit_bank_account)
                : getString(R.string.title_add_bank_account));

        if (isEdit) {
            etName.setText(item.getName());
            etBankName.setText(item.getBankName());
            etAccountNumber.setText(item.getAccountNumber());
            etAccountHolder.setText(item.getAccountHolder());
            etOpeningBalance.setText(item.getOpeningBalance());
            switchActive.setChecked(item.isActive());
            etNote.setText(item.getNote());

            for (int i = 0; i < accountTypes.length; i++) {
                if (accountTypes[i].equalsIgnoreCase(item.getAccountType())) {
                    spinnerAccountType.setSelection(i);
                    break;
                }
            }

            etOpeningBalance.setEnabled(false);
        } else {
            switchActive.setChecked(true);
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String bankName = etBankName.getText().toString().trim();
            String accountNumber = etAccountNumber.getText().toString().trim();
            String accountHolder = etAccountHolder.getText().toString().trim();
            String accountType = spinnerAccountType.getSelectedItem().toString();
            String openingBalance = etOpeningBalance.getText().toString().trim();
            boolean isActive = switchActive.isChecked();
            String note = etNote.getText().toString().trim();

            if (TextUtils.isEmpty(name)) {
                etName.setError(getString(R.string.error_name_required));
                etName.requestFocus();
                return;
            }

            if (TextUtils.isEmpty(bankName)) {
                etBankName.setError(getString(R.string.error_bank_name_required));
                etBankName.requestFocus();
                return;
            }

            if (!isEdit && TextUtils.isEmpty(openingBalance)) {
                openingBalance = "0.00";
            }

            try {
                JSONObject body = new JSONObject();
                body.put("name", name);
                body.put("bank_name", bankName);
                body.put("account_number", accountNumber);
                body.put("account_holder", accountHolder);
                body.put("account_type", accountType);
                body.put("is_active", isActive);
                body.put("note", note);

                if (!isEdit) {
                    body.put("opening_balance", openingBalance);
                    createBankAccount(body, dialog);
                } else {
                    updateBankAccount(item.getId(), body, dialog);
                }

            } catch (JSONException e) {
                Toast.makeText(this, getString(R.string.error_create_request), Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.95),
                    RecyclerView.LayoutParams.WRAP_CONTENT
            );
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
    }

    private Map<String, String> buildHeaders() {
        Map<String, String> headers = new HashMap<>();
        String token = sessionManager.getToken();
        headers.put("Authorization", "Token " + token);
        headers.put("Content-Type", "application/json");
        return headers;
    }

    private String parseVolleyError(com.android.volley.VolleyError error) {
        try {
            NetworkResponse response = error.networkResponse;
            if (response != null && response.data != null) {
                String body = new String(response.data, StandardCharsets.UTF_8);
                return body;
            }
        } catch (Exception ignored) {
        }

        if (error.getMessage() != null) {
            return error.getMessage();
        }

        return getString(R.string.error_network);
    }

    private void showLocalEmpty() {
        if (emptyState != null) {
            emptyState.setVisibility(View.VISIBLE);
        }
        if (tvEmpty != null) {
            tvEmpty.setText(AdminMasterCacheRepository.NO_LOCAL_DATA_MESSAGE);
        }
        if (recyclerView != null) {
            recyclerView.setVisibility(View.GONE);
        }
    }

    @Override
    public void onEdit(BankAccount item) {
        showBankDialog(item);
    }

    @Override
    public void onDelete(BankAccount item) {
        deleteBankAccount(item);
    }

    @Override
    public void onViewLedger(BankAccount item) {
        Toast.makeText(
                this,
                getString(R.string.bank_ledger_coming_soon, item.getBankName()),
                Toast.LENGTH_SHORT
        ).show();
    }
}
