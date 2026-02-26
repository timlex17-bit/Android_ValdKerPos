package com.example.valdker.ui.units;

import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
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
import com.example.valdker.network.ApiClient;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UnitsFragment extends Fragment {

    private static final String TAG = "UNITS";
    private static final String BASE_URL = "https://valdker.onrender.com/api/units/";
    private static final Object REQ_TAG = "UnitsFragmentRequests";

    private SessionManager session;

    private RecyclerView rv;
    private ProgressBar progress;
    private TextView tvEmpty;
    private MaterialButton btnAdd;

    private final List<Unit> items = new ArrayList<>();
    private UnitAdapter adapter;

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
        rv = view.findViewById(R.id.rvList);
        progress = view.findViewById(R.id.progress);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        btnAdd = view.findViewById(R.id.btnAdd);

        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new UnitAdapter(items, new UnitAdapter.Listener() {
            @Override public void onEdit(Unit u) { openForm(u); }
            @Override public void onDelete(Unit u) { confirmDelete(u); }
        });
        rv.setAdapter(adapter);

        btnAdd.setOnClickListener(v -> openForm(null));

        fetch();
    }

    @Override
    public void onStop() {
        super.onStop();
        // Prevent callbacks after fragment is no longer visible
        ApiClient.getInstance(requireContext()).cancelAll(REQ_TAG);
    }

    private void fetch() {
        setLoading(true);

        final String token = session.getToken();

        JsonArrayRequest req = new JsonArrayRequest(
                Request.Method.GET,
                BASE_URL,
                null,
                (JSONArray res) -> {
                    items.clear();

                    for (int i = 0; i < res.length(); i++) {
                        JSONObject o = res.optJSONObject(i);
                        if (o == null) continue;

                        Unit u = new Unit();
                        u.id = o.optInt("id", 0);
                        u.name = o.optString("name", "");
                        items.add(u);
                    }

                    adapter.notifyDataSetChanged();
                    setLoading(false);
                    setEmpty(items.isEmpty());

                    Log.i(TAG, "Fetched units: " + items.size());
                },
                err -> {
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
        ApiClient.getInstance(requireContext()).add(req);
    }

    private void openForm(@Nullable Unit edit) {
        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_unit_form, null, false);

        TextInputEditText etName = content.findViewById(R.id.etUnitName);
        if (edit != null) etName.setText(edit.name);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(edit == null ? "Add Unit" : "Edit Unit")
                .setView(content)
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .setPositiveButton(edit == null ? "Create" : "Save", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = safeText(etName);
            if (name.isEmpty()) {
                etName.setError("Name is required");
                etName.requestFocus();
                return;
            }

            if (edit == null) createUnit(name, dialog);
            else updateUnit(edit.id, name, dialog);
        }));

        dialog.show();
    }

    private void createUnit(@NonNull String name, @NonNull AlertDialog dialog) {
        setLoading(true);

        final String token = session.getToken();

        JSONObject body = new JSONObject();
        try { body.put("name", name); } catch (Exception ignored) {}

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.POST,
                BASE_URL,
                body,
                res -> {
                    setLoading(false);
                    dialog.dismiss();
                    Toast.makeText(requireContext(), "Unit created", Toast.LENGTH_SHORT).show();
                    fetch();
                },
                err -> {
                    setLoading(false);
                    toastVolleyError("Create unit failed", err);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return authHeaders(token);
            }
        };

        req.setTag(REQ_TAG);
        ApiClient.getInstance(requireContext()).add(req);
    }

    private void updateUnit(int id, @NonNull String name, @NonNull AlertDialog dialog) {
        setLoading(true);

        final String token = session.getToken();
        final String url = BASE_URL + id + "/";

        JSONObject body = new JSONObject();
        try { body.put("name", name); } catch (Exception ignored) {}

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.PUT,
                url,
                body,
                res -> {
                    setLoading(false);
                    dialog.dismiss();
                    Toast.makeText(requireContext(), "Unit updated", Toast.LENGTH_SHORT).show();
                    fetch();
                },
                err -> {
                    setLoading(false);
                    toastVolleyError("Update unit failed", err);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return authHeaders(token);
            }
        };

        req.setTag(REQ_TAG);
        ApiClient.getInstance(requireContext()).add(req);
    }

    private void confirmDelete(@NonNull Unit u) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Unit")
                .setMessage("Delete \"" + u.name + "\" ?")
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .setPositiveButton("Delete", (d, w) -> deleteUnit(u.id))
                .show();
    }

    private void deleteUnit(int id) {
        setLoading(true);

        final String token = session.getToken();
        final String url = BASE_URL + id + "/";

        StringRequest req = new StringRequest(
                Request.Method.DELETE,
                url,
                res -> {
                    setLoading(false);
                    Toast.makeText(requireContext(), "Unit deleted", Toast.LENGTH_SHORT).show();
                    fetch();
                },
                err -> {
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
        ApiClient.getInstance(requireContext()).add(req);
    }

    private void setLoading(boolean loading) {
        if (progress != null) progress.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void setEmpty(boolean empty) {
        if (tvEmpty != null) tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (rv != null) rv.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private String safeText(@Nullable TextInputEditText et) {
        if (et == null || et.getText() == null) return "";
        return et.getText().toString().trim();
    }

    private Map<String, String> authHeaders(@Nullable String token) {
        Map<String, String> h = new HashMap<>();
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
        } catch (Exception ignored) {}

        Log.w(TAG, msg);
        Toast.makeText(requireContext(), prefix, Toast.LENGTH_SHORT).show();
    }

    /* -----------------------------
     * Model + Adapter
     * ----------------------------- */

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

        UnitAdapter(List<Unit> data, Listener listener) {
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

            h.tvTitle.setText(u.name);
            h.img.setVisibility(View.GONE);

            h.btnEdit.setOnClickListener(v -> listener.onEdit(u));
            h.btnDelete.setOnClickListener(v -> listener.onDelete(u));
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
