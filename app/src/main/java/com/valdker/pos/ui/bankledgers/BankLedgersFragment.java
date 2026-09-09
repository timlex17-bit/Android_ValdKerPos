package com.valdker.pos.ui.bankledgers;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.valdker.pos.utils.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.valdker.pos.R;
import com.valdker.pos.SessionManager;
import com.valdker.pos.base.BaseFragment;
import com.valdker.pos.models.BankLedger;
import com.valdker.pos.network.BankLedgerApi;
import com.valdker.pos.repositories.TransactionHistoryCacheRepository;
import com.valdker.pos.utils.DateRangeFilterHelper;
import com.valdker.pos.utils.NetworkUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BankLedgersFragment extends BaseFragment {

    private RecyclerView rvBankLedgers;
    private ProgressBar progressBar;
    private LinearLayout emptyState;
    private TextView tvEmptyBankLedgers;
    private TextView tvEmptyBankLedgersSub;
    private EditText etSearchBankLedger;
    private ImageView btnBack;
    private ImageView ivRefreshBankLedger;
    private ImageView btnDateRange;
    private SwipeRefreshLayout swipeRefreshLayout;

    private SessionManager sessionManager;
    private TransactionHistoryCacheRepository cacheRepository;
    private BankLedgerAdapter adapter;
    private final List<BankLedger> allLedgers = new ArrayList<>();
    private boolean isLoading = false;
    private DateRangeFilterHelper dateRangeFilter;

    public BankLedgersFragment() {
        super(R.layout.fragment_bank_ledgers);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        applyTopInset(view.findViewById(R.id.topBar));

        sessionManager = new SessionManager(requireContext());
        cacheRepository = new TransactionHistoryCacheRepository(requireContext());

        initViews(view);
        setupRecycler();
        setupListeners();
        loadBankLedgers();
    }

    private void initViews(View view) {
        rvBankLedgers = view.findViewById(R.id.rvBankLedgers);
        progressBar = view.findViewById(R.id.progressBar);
        emptyState = view.findViewById(R.id.emptyState);
        tvEmptyBankLedgers = view.findViewById(R.id.tvEmptyBankLedgers);
        tvEmptyBankLedgersSub = view.findViewById(R.id.tvEmptyBankLedgersSub);
        etSearchBankLedger = view.findViewById(R.id.etSearchBankLedger);
        btnBack = view.findViewById(R.id.btnBack);
        ivRefreshBankLedger = view.findViewById(R.id.ivRefreshBankLedger);
        btnDateRange = view.findViewById(R.id.btnDateRange);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshBankLedgers);
    }

    private void setupRecycler() {
        adapter = new BankLedgerAdapter(requireContext(), this::showLedgerDetail);
        rvBankLedgers.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvBankLedgers.setClipToPadding(false);
        rvBankLedgers.setAdapter(adapter);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed()
        );

        ivRefreshBankLedger.setOnClickListener(v -> loadBankLedgers());
        swipeRefreshLayout.setOnRefreshListener(this::loadBankLedgers);

        etSearchBankLedger.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterLedgers(s != null ? s.toString() : "");
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        dateRangeFilter = new DateRangeFilterHelper(this, btnDateRange, () ->
                filterLedgers(etSearchBankLedger != null && etSearchBankLedger.getText() != null
                        ? etSearchBankLedger.getText().toString()
                        : ""));
    }

    private void loadBankLedgers() {
        if (!isAdded()) return;
        if (isLoading) {
            if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
            return;
        }
        isLoading = true;
        showLoading(true);

        cacheRepository.loadBankLedgersRoomFirst(new TransactionHistoryCacheRepository.RoomFirstCallback<BankLedger>() {
            @Override
            public void onLocal(@NonNull List<BankLedger> ledgers) {
                if (!isAdded() || ledgers.isEmpty()) return;

                allLedgers.clear();
                allLedgers.addAll(ledgers);
                filterLedgers(etSearchBankLedger.getText() != null ? etSearchBankLedger.getText().toString() : "");
                showLoading(false);
            }

            @Override
            public void onRemote(@NonNull List<BankLedger> ledgers) {
                if (!isAdded()) return;

                isLoading = false;
                showLoading(false);
                allLedgers.clear();
                allLedgers.addAll(ledgers);
                filterLedgers(etSearchBankLedger.getText() != null ? etSearchBankLedger.getText().toString() : "");
            }

            @Override
            public void onNoInternet(boolean hasLocalData) {
                if (!isAdded()) return;

                isLoading = false;
                showLoading(false);
                if (!hasLocalData && allLedgers.isEmpty()) {
                    updateLocalEmptyState();
                    Toast.makeText(requireContext(), TransactionHistoryCacheRepository.NO_LOCAL_DATA_MESSAGE, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(int statusCode, @NonNull String message, boolean hasLocalData) {
                if (!isAdded()) return;

                isLoading = false;
                showLoading(false);
                if (!hasLocalData && allLedgers.isEmpty()) {
                    updateLocalEmptyState();
                    showApiError(message);
                }
            }
        });
    }

    private void filterLedgers(String keyword) {
        String query = keyword == null ? "" : keyword.trim().toLowerCase(Locale.US);
        List<BankLedger> filtered = new ArrayList<>();

        for (BankLedger ledger : allLedgers) {
            if (ledger == null) continue;

            boolean matchesQuery = query.isEmpty()
                    || contains(ledger.getBankAccountName(), query)
                    || contains(ledger.getTransactionType(), query)
                    || contains(ledger.getDirection(), query)
                    || contains(ledger.getReferenceOrderInvoice(), query)
                    || contains(ledger.getDescription(), query)
                    || contains(ledger.getAmount(), query);
            boolean matchesDate = dateRangeFilter == null || dateRangeFilter.matches(ledger.getCreatedAt(), ledger.getDisplayDate());

            if (matchesQuery && matchesDate) {
                filtered.add(ledger);
            }
        }

        adapter.setData(filtered);
        updateEmptyState(filtered.isEmpty());
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.US).contains(query);
    }

    private void showLedgerDetail(BankLedger ledger) {
        if (!isAdded()) return;
        if (!NetworkUtils.isNetworkAvailable(requireContext())) {
            showLedgerDetailDialog(ledger);
            return;
        }

        BankLedgerApi.getBankLedgerDetail(requireContext(), sessionManager, ledger.getId(), new BankLedgerApi.BankLedgerDetailCallback() {
            @Override
            public void onSuccess(BankLedger detail) {
                if (!isAdded()) return;
                showLedgerDetailDialog(detail);
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                showLedgerDetailDialog(ledger);
                showApiError(message);
            }
        });
    }

    private void showLedgerDetailDialog(BankLedger ledger) {
        View view = getLayoutInflater().inflate(R.layout.dialog_bank_ledger_detail, null, false);
        TextView tvTitle = view.findViewById(R.id.tvTitle);
        TextView tvDetails = view.findViewById(R.id.tvDetails);

        tvTitle.setText(ledger.getBankAccountName().isEmpty()
                ? getString(R.string.bank_ledger_title)
                : ledger.getBankAccountName());
        tvDetails.setText(buildDetailText(ledger));

        new AlertDialog.Builder(requireContext())
                .setView(view)
                .setPositiveButton(getString(R.string.action_ok), null)
                .show();
    }

    private String buildDetailText(BankLedger ledger) {
        return getString(
                R.string.bank_ledger_detail_format,
                ledger.getId(),
                ledger.getShopName(),
                ledger.getShopCode(),
                ledger.getBankAccount(),
                ledger.getBankAccountName(),
                ledger.getDisplayTransactionType(),
                ledger.getDirection(),
                ledger.getFormattedAmount(),
                ledger.getFormattedBalanceBefore(),
                ledger.getFormattedBalanceAfter(),
                ledger.getReferenceOrder(),
                safeDash(ledger.getReferenceOrderInvoice()),
                ledger.getReferencePayment(),
                safeDash(ledger.getDescription()),
                ledger.getDisplayDate(),
                ledger.getCreatedBy()
        );
    }

    private String safeDash(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value.trim();
    }

    private void showLoading(boolean loading) {
        if (progressBar != null) progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
        if (ivRefreshBankLedger != null) {
            ivRefreshBankLedger.setEnabled(!loading);
            ivRefreshBankLedger.setAlpha(loading ? 0.5f : 1f);
        }
    }

    private void updateEmptyState(boolean empty) {
        if (emptyState != null) {
            emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        }
        if (rvBankLedgers != null) {
            rvBankLedgers.setVisibility(empty ? View.GONE : View.VISIBLE);
        }
    }

    private void updateLocalEmptyState() {
        if (tvEmptyBankLedgers != null) {
            tvEmptyBankLedgers.setText(TransactionHistoryCacheRepository.NO_LOCAL_DATA_MESSAGE);
        }
        if (tvEmptyBankLedgersSub != null) {
            tvEmptyBankLedgersSub.setText("");
        }
        updateEmptyState(true);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        rvBankLedgers = null;
        progressBar = null;
        emptyState = null;
        tvEmptyBankLedgers = null;
        tvEmptyBankLedgersSub = null;
        etSearchBankLedger = null;
        btnBack = null;
        ivRefreshBankLedger = null;
        btnDateRange = null;
        swipeRefreshLayout = null;
        cacheRepository = null;
        dateRangeFilter = null;
    }
}
