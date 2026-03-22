package com.example.valdker.ui.units;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.example.valdker.R;
import com.example.valdker.SessionManager;
import com.example.valdker.base.BaseFragment;
import com.example.valdker.network.ApiClient;
import com.example.valdker.network.ApiConfig;
import com.example.valdker.utils.InsetsHelper;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UnitsFragment extends BaseFragment {

    private static final String TAG = "UNITS";
    private static final String ENDPOINT_UNITS = "api/units/";
    private static final Object REQ_TAG = "UnitsFragmentRequests";
    private static final long FAB_CLICK_DELAY_MS = 700L;

    private ImageView btnBack;
    private ImageView ivHeaderAction;
    private TextView tvTitle;
    private EditText etSearch;

    private SessionManager session;

    private RecyclerView rv;
    private ProgressBar progress;
    private TextView tvEmpty;
    private FloatingActionButton fabAdd;

    private final List<Unit> items = new ArrayList<>();
    private final List<Unit> allItems = new ArrayList<>();
    private UnitAdapter adapter;

    private long lastFabClickTime = 0L;
    private boolean isFormShowing = false;

    public UnitsFragment() {
        super(R.layout.fragment_units);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new SessionManager(requireContext());
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        applyTopInset(view.findViewById(R.id.topBar));

        rv = view.findViewById(R.id.rvList);
        progress = view.findViewById(R.id.progress);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        fabAdd = view.findViewById(R.id.fabAddUnit);

        btnBack = view.findViewById(R.id.btnBack);
        ivHeaderAction = view.findViewById(R.id.ivHeaderAction);
        tvTitle = view.findViewById(R.id.tvTitle);
        etSearch = view.findViewById(R.id.etSearch);

        if (tvTitle != null) {
            tvTitle.setText("Units");
        }

        if (btnBack != null) {
            btnBack.setOnClickListener(v ->
                    requireActivity().getOnBackPressedDispatcher().onBackPressed()
            );
        }

        if (ivHeaderAction != null) {
            ivHeaderAction.setOnClickListener(v -> fetch());
        }

        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    applyFilter(s == null ? "" : s.toString());
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });
        }

        InsetsHelper.applyRecyclerBottomInsets(view, rv, TAG);

        if (rv != null) {
            rv.setLayoutManager(new LinearLayoutManager(requireContext()));
            rv.setHasFixedSize(true);
        }

        adapter = new UnitAdapter(items, new UnitAdapter.Listener() {
            @Override
            public void onEdit(Unit u) {
                openForm(u);
            }

            @Override
            public void onDelete(Unit u) {
                confirmDelete(u);
            }
        });

        if (rv != null) {
            rv.setAdapter(adapter);
        }

        if (fabAdd != null) {
            fabAdd.setOnClickListener(v -> openAddUnitSafely());
        }

        fetch();
    }

    private void applyFilter(@Nullable String query) {
        String q = query == null ? "" : query.trim().toLowerCase();

        items.clear();

        if (q.isEmpty()) {
            items.addAll(allItems);
        } else {
            for (Unit u : allItems) {
                String name = u.name == null ? "" : u.name.toLowerCase();
                if (name.contains(q)) {
                    items.add(u);
                }
            }
        }

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }

        setEmpty(items.isEmpty());
    }

    private void openAddUnitSafely() {
        if (!isAdded()) return;
        if (isFormShowing) return;

        long now = System.currentTimeMillis();
        if (now - lastFabClickTime < FAB_CLICK_DELAY_MS) {
            return;
        }
        lastFabClickTime = now;

        if (fabAdd != null) {
            fabAdd.setEnabled(false);
            fabAdd.postDelayed(() -> {
                if (fabAdd != null && isAdded() && !isFormShowing) {
                    fabAdd.setEnabled(true);
                }
            }, FAB_CLICK_DELAY_MS);
        }

        openForm(null);
    }

    @Override
    public void onStop() {
        super.onStop();
        Context ctx = getContext();
        if (ctx != null) {
            ApiClient.getInstance(ctx.getApplicationContext()).cancelAll(REQ_TAG);
        }
    }

    private void fetch() {
        if (!isAdded()) return;

        setLoading(true);

        final String token = session != null ? session.getToken() : null;
        if (token == null || token.trim().isEmpty()) {
            setLoading(false);
            setEmpty(true);
            toast("Token is missing. Please login again.");
            return;
        }

        final Context ctx = getContext();
        if (ctx == null) {
            setLoading(false);
            return;
        }

        final String url = ApiConfig.url(session, ENDPOINT_UNITS);

        JsonArrayRequest req = new JsonArrayRequest(
                Request.Method.GET,
                url,
                null,
                (JSONArray res) -> {
                    if (!isAdded()) return;

                    allItems.clear();

                    for (int i = 0; i < res.length(); i++) {
                        JSONObject o = res.optJSONObject(i);
                        if (o == null) continue;

                        Unit u = new Unit();
                        u.id = o.optInt("id", 0);
                        u.name = o.optString("name", "");
                        allItems.add(u);
                    }

                    applyFilter(etSearch != null ? etSearch.getText().toString() : "");

                    setLoading(false);
                    Log.i(TAG, "Fetched units: " + allItems.size());
                },
                err -> {
                    if (!isAdded()) return;
                    setLoading(false);
                    setEmpty(items.isEmpty());
                    toastVolleyError("Fetch units failed", err);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return authHeaders(token);
            }
        };

        req.setTag(REQ_TAG);
        ApiClient.getInstance(ctx.getApplicationContext()).add(req);
    }

    private void openForm(@Nullable Unit edit) {
        if (!isAdded()) return;
        if (isFormShowing) return;

        isFormShowing = true;
        if (fabAdd != null) fabAdd.setEnabled(false);

        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_unit_form, null, false);

        TextInputEditText etName = content.findViewById(R.id.etUnitName);

        if (edit != null && etName != null) {
            etName.setText(edit.name);
        }

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(edit == null ? "Add Unit" : "Edit Unit")
                .setView(content)
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .setPositiveButton(edit == null ? "Create" : "Save", null)
                .create();

        dialog.setOnDismissListener(d -> {
            isFormShowing = false;

            if (fabAdd != null && isAdded()) {
                fabAdd.postDelayed(() -> {
                    if (fabAdd != null && isAdded() && !isFormShowing) {
                        fabAdd.setEnabled(true);
                    }
                }, 180L);
            }
        });

        dialog.setOnShowListener(d -> {
            View positiveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (positiveBtn == null) return;

            positiveBtn.setOnClickListener(v -> {
                String name = safeText(etName);

                if (name.isEmpty()) {
                    if (etName != null) {
                        etName.setError("Name is required");
                        etName.requestFocus();
                    }
                    return;
                }

                positiveBtn.setEnabled(false);

                if (edit == null) {
                    createUnit(name, dialog, positiveBtn);
                } else {
                    updateUnit(edit.id, name, dialog, positiveBtn);
                }
            });
        });

        dialog.show();
    }

    private void createUnit(@NonNull String name, @NonNull AlertDialog dialog, @NonNull View positiveBtn) {
        if (!isAdded()) return;

        setLoading(true);

        final String token = session != null ? session.getToken() : null;
        final String url = ApiConfig.url(session, ENDPOINT_UNITS);

        JSONObject body = new JSONObject();
        try {
            body.put("name", name);
        } catch (Exception ignored) {
        }

        Context ctx = getContext();
        if (ctx == null) {
            setLoading(false);
            positiveBtn.setEnabled(true);
            return;
        }

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.POST,
                url,
                body,
                res -> {
                    if (!isAdded()) return;

                    setLoading(false);
                    dialog.dismiss();
                    toast("Unit created");
                    fetch();
                },
                err -> {
                    if (!isAdded()) return;

                    setLoading(false);
                    positiveBtn.setEnabled(true);
                    toastVolleyError("Create unit failed", err);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return authHeaders(token);
            }
        };

        req.setTag(REQ_TAG);
        ApiClient.getInstance(ctx.getApplicationContext()).add(req);
    }

    private void updateUnit(int id, @NonNull String name, @NonNull AlertDialog dialog, @NonNull View positiveBtn) {
        if (!isAdded()) return;

        setLoading(true);

        final String token = session != null ? session.getToken() : null;
        final String url = ApiConfig.url(session, ENDPOINT_UNITS) + id + "/";

        JSONObject body = new JSONObject();
        try {
            body.put("name", name);
        } catch (Exception ignored) {
        }

        Context ctx = getContext();
        if (ctx == null) {
            setLoading(false);
            positiveBtn.setEnabled(true);
            return;
        }

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.PUT,
                url,
                body,
                res -> {
                    if (!isAdded()) return;

                    setLoading(false);
                    dialog.dismiss();
                    toast("Unit updated");
                    fetch();
                },
                err -> {
                    if (!isAdded()) return;

                    setLoading(false);
                    positiveBtn.setEnabled(true);
                    toastVolleyError("Update unit failed", err);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return authHeaders(token);
            }
        };

        req.setTag(REQ_TAG);
        ApiClient.getInstance(ctx.getApplicationContext()).add(req);
    }

    private void confirmDelete(@NonNull Unit u) {
        if (!isAdded()) return;

        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Unit")
                .setMessage("Delete \"" + u.name + "\" ?")
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .setPositiveButton("Delete", (d, w) -> deleteUnit(u.id))
                .show();
    }

    private void deleteUnit(int id) {
        if (!isAdded()) return;

        setLoading(true);

        final String token = session != null ? session.getToken() : null;
        final String url = ApiConfig.url(session, ENDPOINT_UNITS) + id + "/";

        Context ctx = getContext();
        if (ctx == null) {
            setLoading(false);
            return;
        }

        StringRequest req = new StringRequest(
                Request.Method.DELETE,
                url,
                res -> {
                    if (!isAdded()) return;

                    setLoading(false);
                    toast("Unit deleted");
                    fetch();
                },
                err -> {
                    if (!isAdded()) return;

                    setLoading(false);
                    toastVolleyError("Delete unit failed", err);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return authHeaders(token);
            }
        };

        req.setTag(REQ_TAG);
        ApiClient.getInstance(ctx.getApplicationContext()).add(req);
    }

    private void setLoading(boolean loading) {
        if (progress != null) {
            progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (fabAdd != null) {
            fabAdd.setEnabled(!loading && !isFormShowing);
        }
    }

    private void setEmpty(boolean empty) {
        if (tvEmpty != null) {
            tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        }
        if (rv != null) {
            rv.setVisibility(empty ? View.GONE : View.VISIBLE);
        }
    }

    @NonNull
    private String safeText(@Nullable TextInputEditText et) {
        if (et == null || et.getText() == null) return "";
        return et.getText().toString().trim();
    }

    @NonNull
    private Map<String, String> authHeaders(@Nullable String token) {
        Map<String, String> h = new HashMap<>();
        h.put("Accept", "application/json");
        if (token != null && !token.trim().isEmpty()) {
            h.put("Authorization", "Token " + token.trim());
        }
        return h;
    }

    private void toastVolleyError(@NonNull String prefix, @NonNull com.android.volley.VolleyError err) {
        String msg = prefix;

        try {
            NetworkResponse r = err.networkResponse;
            if (r != null && r.data != null) {
                msg = prefix + " (" + r.statusCode + "): " + new String(r.data, StandardCharsets.UTF_8);
            } else if (err.getMessage() != null) {
                msg = prefix + ": " + err.getMessage();
            }
        } catch (Exception ignored) {
        }

        Log.w(TAG, msg);
        toast(prefix);
    }

    private void toast(@NonNull String msg) {
        if (!isAdded()) return;
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
    }

    static class Unit {
        int id;
        String name;
    }

    static class UnitAdapter extends RecyclerView.Adapter<UnitAdapter.VH> {

        interface Listener {
            void onEdit(Unit u);
            void onDelete(Unit u);
        }

        private final List<Unit> data;
        private final Listener listener;

        UnitAdapter(@NonNull List<Unit> data, @NonNull Listener listener) {
            this.data = data;
            this.listener = listener;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_simple_row, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Unit u = data.get(position);

            if (h.tvTitle != null) h.tvTitle.setText(u.name);
            if (h.img != null) h.img.setVisibility(View.GONE);

            if (h.btnEdit != null) {
                h.btnEdit.setOnClickListener(v -> listener.onEdit(u));
            }
            if (h.btnDelete != null) {
                h.btnDelete.setOnClickListener(v -> listener.onDelete(u));
            }
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final ImageView img;
            final TextView tvTitle;
            final ImageButton btnEdit;
            final ImageButton btnDelete;

            VH(@NonNull View itemView) {
                super(itemView);
                img = itemView.findViewById(R.id.imgIcon);
                tvTitle = itemView.findViewById(R.id.tvTitle);
                btnEdit = itemView.findViewById(R.id.btnEdit);
                btnDelete = itemView.findViewById(R.id.btnDelete);
            }
        }
    }
}