package com.example.valdker.ui.categories;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.AuthFailureError;
import com.android.volley.Cache;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.StringRequest;
import com.bumptech.glide.Glide;
import com.example.valdker.R;
import com.example.valdker.SessionManager;
import com.example.valdker.models.Category;
import com.example.valdker.network.ApiClient;
import com.example.valdker.offline.repositories.CategoryCacheRepository;
import com.example.valdker.utils.InsetsHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CategoriesFragment extends Fragment {

    private static final String TAG = "CATEGORIES";
    private static final String BASE_URL = "https://valdker.onrender.com/api/categories/";
    private static final Object REQ_TAG = "CategoriesFragmentRequests";

    private SessionManager session;

    private RecyclerView rv;
    private ProgressBar progress;
    private TextView tvEmpty;
    private MaterialButton btnAdd;

    private final List<Category> items = new ArrayList<>();
    private CategoryAdapter adapter;

    private Uri pendingIconUri;

    private final ActivityResultLauncher<String> pickIconLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null && isAdded()) {
                    pendingIconUri = uri;
                    Toast.makeText(requireContext(), "Icon selected", Toast.LENGTH_SHORT).show();
                }
            });

    public CategoriesFragment() {
        super(R.layout.fragment_manage_categories);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new SessionManager(requireContext());
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rv = view.findViewById(R.id.rvList);
        progress = view.findViewById(R.id.progress);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        btnAdd = view.findViewById(R.id.btnAdd);

        if (rv == null) Log.w(TAG, "rvList not found (check fragment_manage_categories.xml / layout-land).");
        if (progress == null) Log.w(TAG, "progress not found.");
        if (tvEmpty == null) Log.w(TAG, "tvEmpty not found.");
        if (btnAdd == null) Log.w(TAG, "btnAdd not found.");

        InsetsHelper.applyRecyclerBottomInsets(view, rv, TAG);

        if (rv != null) rv.setLayoutManager(new LinearLayoutManager(requireContext()));

        adapter = new CategoryAdapter(items, new CategoryAdapter.Listener() {
            @Override public void onEdit(Category c) { openForm(c); }
            @Override public void onDelete(Category c) { confirmDelete(c); }
        });
        if (rv != null) rv.setAdapter(adapter);

        if (btnAdd != null) btnAdd.setOnClickListener(v -> openForm(null));

        // ✅ OFFLINE-FIRST:
        // 1) load dari Room dulu (cepat)
        loadCategoriesFromRoom();

        // 2) lalu fetch online untuk refresh + cache (kalau offline, UI tetap pakai Room)
        fetchOnlineAndCache();
    }

    @Override
    public void onStop() {
        super.onStop();
        try {
            ApiClient.getInstance(requireContext()).cancelAll(REQ_TAG);
        } catch (Exception e) {
            Log.w(TAG, "cancelAll failed: " + e.getMessage());
        }
        try {
            ApiClient.getInstance(requireContext()).cancelAll(CategoryCacheRepository.class.getSimpleName());
        } catch (Exception ignored) {}
    }

    // =========================================================
    // OFFLINE-FIRST helpers
    // =========================================================
    private void loadCategoriesFromRoom() {
        CategoryCacheRepository.loadFromRoom(requireContext(), new CategoryCacheRepository.ListCallback() {
            @Override
            public void onSuccess(@NonNull List<Category> models) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    items.clear();
                    items.addAll(models);
                    if (adapter != null) adapter.notifyDataSetChanged();
                    setEmpty(items.isEmpty());
                });
            }

            @Override
            public void onError(@NonNull String message) {
                Log.w(TAG, "loadFromRoom failed: " + message);
            }
        });
    }

    private void fetchOnlineAndCache() {
        // gunakan fetch() online kamu, tapi kita tambahkan cache ke Room
        fetch();
    }

    // =========================================================
    // ONLINE FETCH (existing) + cache to room
    // =========================================================
    private void fetch() {
        setLoading(true);

        final String token = session != null ? session.getToken() : null;
        if (token == null || token.trim().isEmpty()) {
            setLoading(false);
            setEmpty(true);
            Toast.makeText(requireContext(), "Token kosong. Silakan login ulang.", Toast.LENGTH_SHORT).show();
            return;
        }

        JsonArrayRequest req = new JsonArrayRequest(
                Request.Method.GET,
                BASE_URL,
                null,
                (JSONArray res) -> {
                    if (!isAdded()) return;

                    items.clear();

                    for (int i = 0; i < res.length(); i++) {
                        JSONObject o = res.optJSONObject(i);
                        if (o == null) continue;

                        Category c = new Category();
                        c.id = o.optInt("id", 0);
                        c.name = o.optString("name", "");
                        c.iconUrl = o.optString("icon_url", "");
                        items.add(c);
                    }

                    if (adapter != null) adapter.notifyDataSetChanged();
                    setLoading(false);
                    setEmpty(items.isEmpty());

                    Log.i(TAG, "Fetched categories online: " + items.size());

                    // ✅ cache ke Room (online sukses)
                    CategoryCacheRepository.syncOnlineAndCache(
                            requireContext(),
                            new CategoryCacheRepository.ListCallback() {
                                @Override public void onSuccess(@NonNull List<Category> ignored) {
                                    Log.i(TAG, "Room cache updated from online");
                                }
                                @Override public void onError(@NonNull String message) {
                                    Log.w(TAG, "Room cache update failed: " + message);
                                }
                            }
                    );
                },
                err -> {
                    if (!isAdded()) return;

                    setLoading(false);

                    // ✅ fallback: tetap tampilkan Room yang sudah diload tadi
                    setEmpty(items.isEmpty());
                    toastVolleyError("Fetch categories failed", err);

                    Log.w(TAG, "Online failed, keep Room data (size=" + items.size() + ")");
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

    // =========================================================
    // CRUD tetap sama
    // =========================================================
    private void openForm(@Nullable Category edit) {
        if (!isAdded()) return;

        pendingIconUri = null;

        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_category_form, null, false);

        TextInputEditText etName = content.findViewById(R.id.etCategoryName);
        MaterialButton btnPick = content.findViewById(R.id.btnPickIcon);
        ImageView imgPreview = content.findViewById(R.id.imgIconPreview);

        if (edit != null) {
            etName.setText(edit.name);
            if (edit.iconUrl != null && !edit.iconUrl.trim().isEmpty()) {
                imgPreview.setVisibility(View.VISIBLE);
                Glide.with(this).load(edit.iconUrl.trim()).into(imgPreview);
            } else {
                imgPreview.setVisibility(View.GONE);
            }
        } else {
            imgPreview.setVisibility(View.GONE);
        }

        btnPick.setOnClickListener(v -> pickIconLauncher.launch("image/*"));

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(edit == null ? "Add Category" : "Edit Category")
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

            if (pendingIconUri != null) {
                imgPreview.setImageURI(pendingIconUri);
                imgPreview.setVisibility(View.VISIBLE);
            }

            if (edit == null) {
                createCategory(name, pendingIconUri, dialog);
            } else {
                updateCategory(edit.id, name, pendingIconUri, dialog);
            }
        }));

        dialog.show();
    }

    private void createCategory(@NonNull String name, @Nullable Uri iconUri, @NonNull AlertDialog dialog) {
        setLoading(true);

        final String token = session != null ? session.getToken() : null;

        VolleyMultipartRequest req = new VolleyMultipartRequest(
                Request.Method.POST,
                BASE_URL,
                response -> {
                    if (!isAdded()) return;
                    setLoading(false);
                    dialog.dismiss();
                    Toast.makeText(requireContext(), "Category created", Toast.LENGTH_SHORT).show();
                    fetch(); // refresh + cache
                },
                error -> {
                    if (!isAdded()) return;
                    setLoading(false);
                    toastVolleyError("Create category failed", error);
                }
        ) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> p = new HashMap<>();
                p.put("name", name);
                return p;
            }

            @Override
            protected Map<String, DataPart> getByteData() {
                Map<String, DataPart> out = new HashMap<>();
                if (iconUri != null) {
                    byte[] bytes = readBytes(requireContext(), iconUri);
                    if (bytes != null && bytes.length > 0) {
                        String fileName = guessFileName(requireContext(), iconUri, "category_icon");
                        String mime = guessMime(requireContext(), iconUri);
                        out.put("category_icon", new DataPart(fileName, bytes, mime));
                    }
                }
                return out;
            }

            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return authHeaders(token);
            }
        };

        req.setTag(REQ_TAG);
        ApiClient.getInstance(requireContext()).add(req);
    }

    private void updateCategory(int id, @NonNull String name, @Nullable Uri iconUri, @NonNull AlertDialog dialog) {
        setLoading(true);

        final String token = session != null ? session.getToken() : null;
        final String url = BASE_URL + id + "/";

        VolleyMultipartRequest req = new VolleyMultipartRequest(
                Request.Method.PUT,
                url,
                response -> {
                    if (!isAdded()) return;
                    setLoading(false);
                    dialog.dismiss();
                    Toast.makeText(requireContext(), "Category updated", Toast.LENGTH_SHORT).show();
                    fetch(); // refresh + cache
                },
                error -> {
                    if (!isAdded()) return;
                    setLoading(false);
                    toastVolleyError("Update category failed", error);
                }
        ) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> p = new HashMap<>();
                p.put("name", name);
                return p;
            }

            @Override
            protected Map<String, DataPart> getByteData() {
                Map<String, DataPart> out = new HashMap<>();
                if (iconUri != null) {
                    byte[] bytes = readBytes(requireContext(), iconUri);
                    if (bytes != null && bytes.length > 0) {
                        String fileName = guessFileName(requireContext(), iconUri, "category_icon");
                        String mime = guessMime(requireContext(), iconUri);
                        out.put("category_icon", new DataPart(fileName, bytes, mime));
                    }
                }
                return out;
            }

            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return authHeaders(token);
            }
        };

        req.setTag(REQ_TAG);
        ApiClient.getInstance(requireContext()).add(req);
    }

    private void confirmDelete(@NonNull Category c) {
        if (!isAdded()) return;

        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Category")
                .setMessage("Delete \"" + c.name + "\" ?")
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .setPositiveButton("Delete", (d, w) -> deleteCategory(c.id))
                .show();
    }

    private void deleteCategory(int id) {
        setLoading(true);

        final String token = session != null ? session.getToken() : null;
        final String url = BASE_URL + id + "/";

        StringRequest req = new StringRequest(
                Request.Method.DELETE,
                url,
                res -> {
                    if (!isAdded()) return;
                    setLoading(false);
                    Toast.makeText(requireContext(), "Category deleted", Toast.LENGTH_SHORT).show();
                    fetch(); // refresh + cache
                },
                err -> {
                    if (!isAdded()) return;
                    setLoading(false);
                    toastVolleyError("Delete category failed", err);
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

    private void toastVolleyError(@NonNull String prefix, @NonNull VolleyError err) {
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
        if (isAdded()) Toast.makeText(requireContext(), prefix, Toast.LENGTH_SHORT).show();
    }

    // =========================================================
    // Adapter (tetap)
    // =========================================================
    static class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.VH> {

        interface Listener {
            void onEdit(Category c);
            void onDelete(Category c);
        }

        private final List<Category> data;
        private final Listener listener;

        CategoryAdapter(List<Category> data, Listener listener) {
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
            Category c = data.get(position);

            h.tvTitle.setText(c.name);

            if (c.iconUrl != null && !c.iconUrl.trim().isEmpty()) {
                h.img.setVisibility(View.VISIBLE);
                Glide.with(h.img.getContext()).load(c.iconUrl.trim()).into(h.img);
            } else {
                h.img.setVisibility(View.GONE);
            }

            h.btnEdit.setOnClickListener(v -> listener.onEdit(c));
            h.btnDelete.setOnClickListener(v -> listener.onDelete(c));
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

    // =========================================================
    // Multipart Request (Volley) - tetap sama
    // =========================================================
    public abstract static class VolleyMultipartRequest extends com.android.volley.Request<NetworkResponse> {

        private final Response.Listener<NetworkResponse> listener;
        @Nullable private final Response.ErrorListener errorListener;

        public VolleyMultipartRequest(
                int method,
                @NonNull String url,
                @NonNull Response.Listener<NetworkResponse> listener,
                @Nullable Response.ErrorListener errorListener
        ) {
            super(method, url, errorListener);
            this.listener = listener;
            this.errorListener = errorListener;
        }

        protected abstract Map<String, DataPart> getByteData();

        @Override
        protected Response<NetworkResponse> parseNetworkResponse(NetworkResponse response) {
            Cache.Entry entry = new Cache.Entry();
            entry.data = response.data;
            entry.responseHeaders = response.headers;
            entry.ttl = 0;
            entry.softTtl = 0;
            return Response.success(response, entry);
        }

        @Override
        protected void deliverResponse(NetworkResponse response) {
            listener.onResponse(response);
        }

        @Override
        public void deliverError(VolleyError error) {
            if (errorListener != null) errorListener.onErrorResponse(error);
        }

        @Override
        public String getBodyContentType() {
            return "multipart/form-data; boundary=" + getBoundary();
        }

        @Override
        public byte[] getBody() throws AuthFailureError {
            return buildMultipartBody();
        }

        private String boundary;

        private String getBoundary() {
            if (boundary == null) boundary = "valdker-" + System.currentTimeMillis();
            return boundary;
        }

        private byte[] buildMultipartBody() {
            String b = getBoundary();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();

            try {
                Map<String, String> params = getParams();
                if (params != null) {
                    for (Map.Entry<String, String> e : params.entrySet()) {
                        bos.write(("--" + b + "\r\n").getBytes());
                        bos.write(("Content-Disposition: form-data; name=\"" + e.getKey() + "\"\r\n\r\n").getBytes());
                        bos.write((e.getValue() + "\r\n").getBytes());
                    }
                }

                Map<String, DataPart> data = getByteData();
                if (data != null) {
                    for (Map.Entry<String, DataPart> e : data.entrySet()) {
                        DataPart p = e.getValue();
                        bos.write(("--" + b + "\r\n").getBytes());
                        bos.write(("Content-Disposition: form-data; name=\"" + e.getKey() + "\"; filename=\"" + p.fileName + "\"\r\n").getBytes());
                        bos.write(("Content-Type: " + p.type + "\r\n\r\n").getBytes());
                        bos.write(p.content);
                        bos.write("\r\n".getBytes());
                    }
                }

                bos.write(("--" + b + "--\r\n").getBytes());
                return bos.toByteArray();

            } catch (Exception ex) {
                Log.w(TAG, "Multipart build failed: " + ex.getMessage());
                return new byte[0];
            }
        }

        public static class DataPart {
            public final String fileName;
            public final byte[] content;
            public final String type;

            public DataPart(@NonNull String fileName, @NonNull byte[] content, @NonNull String type) {
                this.fileName = fileName;
                this.content = content;
                this.type = type;
            }
        }
    }

    // =========================================================
    // File helpers (tetap)
    // =========================================================
    private static byte[] readBytes(@NonNull Context ctx, @NonNull Uri uri) {
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            if (in == null) return null;

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);

            return bos.toByteArray();
        } catch (Exception e) {
            Log.w(TAG, "readBytes failed: " + e.getMessage());
            return null;
        }
    }

    private static String guessMime(@NonNull Context ctx, @NonNull Uri uri) {
        try {
            ContentResolver cr = ctx.getContentResolver();
            String mime = cr.getType(uri);
            if (mime != null) return mime;

            String ext = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
            if (ext != null) {
                String out = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
                if (out != null) return out;
            }
        } catch (Exception ignored) {}
        return "image/*";
    }

    private static String guessFileName(@NonNull Context ctx, @NonNull Uri uri, @NonNull String fallbackBase) {
        Cursor c = null;
        try {
            c = ctx.getContentResolver().query(uri, null, null, null, null);
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String name = c.getString(idx);
                    if (name != null && !name.trim().isEmpty()) return name;
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }

        return fallbackBase + "_" + System.currentTimeMillis() + ".png";
    }
}