package com.example.valdker.ui.categories;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import com.example.valdker.BuildConfig;
import com.example.valdker.R;
import com.example.valdker.SessionManager;
import com.example.valdker.base.BaseFragment;
import com.example.valdker.models.Category;
import com.example.valdker.network.ApiClient;
import com.example.valdker.network.ApiConfig;
import com.example.valdker.utils.InsetsHelper;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CategoriesFragment extends BaseFragment {

    private static final String TAG = "CATEGORIES";
    private static final String ENDPOINT_CATEGORIES = "api/categories/";
    private static final Object FETCH_TAG = "CategoriesFetchRequests";
    private static final Object MUTATION_TAG = "CategoriesMutationRequests";
    private static final long FAB_CLICK_DELAY_MS = 700L;

    private static final int MAX_RAW_FILE_BYTES = 10 * 1024 * 1024;
    private static final int MAX_UPLOAD_DIMENSION = 1024;
    private static final int JPEG_QUALITY = 82;
    private static final int PNG_COMPRESSION_QUALITY = 100;
    private static final int WEBP_QUALITY = 82;

    private SessionManager session;

    private RecyclerView rv;
    private ProgressBar progress;
    private TextView tvEmpty;
    private TextView tvTitle;
    private FloatingActionButton fabAdd;
    private ImageView btnBack;
    private ImageView ivHeaderAction;
    private EditText etSearch;

    private final List<Category> items = new ArrayList<>();
    private final List<Category> allItems = new ArrayList<>();
    private CategoryAdapter adapter;

    @Nullable
    private PendingIconState currentFormState;

    private ActivityResultLauncher<String> pickIconLauncher;

    private long lastFabClickTime = 0L;
    private boolean isFormShowing = false;
    private boolean isLoading = false;
    private boolean isDeleteRunning = false;
    private String currentQuery = "";

    public CategoriesFragment() {
        super(R.layout.fragment_manage_categories);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new SessionManager(requireContext());

        pickIconLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    logd("pickIconLauncher callback uri=" + uri);

                    final PendingIconState state = currentFormState;
                    if (uri == null || state == null) {
                        logw("Picker result ignored: uri/state null");
                        return;
                    }

                    final Context appCtx = state.appContext;
                    try {
                        String originalMime = guessMime(appCtx, uri);
                        String safeMime = normalizeAllowedMime(originalMime);

                        if (safeMime == null) {
                            showToast(appCtx, "Use JPG, PNG, or WEBP image only.");
                            clearFormIconState(state, true);
                            return;
                        }

                        byte[] rawBytes = readBytes(appCtx, uri);
                        if (rawBytes == null || rawBytes.length == 0) {
                            showToast(appCtx, "Failed to read selected image.");
                            clearFormIconState(state, true);
                            return;
                        }

                        if (rawBytes.length > MAX_RAW_FILE_BYTES) {
                            showToast(appCtx, "Selected image is too large.");
                            clearFormIconState(state, true);
                            return;
                        }

                        byte[] uploadBytes = resizeAndCompressImage(rawBytes, safeMime, MAX_UPLOAD_DIMENSION);
                        if (uploadBytes == null || uploadBytes.length == 0) {
                            showToast(appCtx, "Selected image is invalid or unsupported.");
                            clearFormIconState(state, true);
                            return;
                        }

                        String fileName = ensureFileExtension(
                                guessFileName(appCtx, uri, "category_icon"),
                                safeMime
                        );

                        state.uri = uri;
                        state.fileName = fileName;
                        state.mime = safeMime;
                        state.bytes = uploadBytes;

                        if (state.preview != null) {
                            state.preview.setVisibility(View.VISIBLE);
                            Glide.with(this).load(uri).into(state.preview);
                        }

                        logd("Icon prepared mime=" + safeMime
                                + ", fileName=" + fileName
                                + ", uploadBytes=" + uploadBytes.length);

                        showToast(appCtx, "Icon selected");

                    } catch (Exception e) {
                        loge("Failed preparing picked icon", e);
                        clearFormIconState(state, true);
                        showToast(appCtx, "Failed to load selected image.");
                    }
                }
        );
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        applyTopInset(view.findViewById(R.id.topBar));

        rv = view.findViewById(R.id.rvList);
        progress = view.findViewById(R.id.progress);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        fabAdd = view.findViewById(R.id.fabAddCategory);
        btnBack = view.findViewById(R.id.btnBack);
        ivHeaderAction = view.findViewById(R.id.ivHeaderAction);
        tvTitle = view.findViewById(R.id.tvTitle);
        etSearch = view.findViewById(R.id.etSearch);

        if (tvTitle != null) {
            tvTitle.setText("Categories");
        }

        InsetsHelper.applyRecyclerBottomInsets(view, rv, TAG);
        applyFabBottomInset(fabAdd, 56);

        if (rv != null) {
            rv.setLayoutManager(new LinearLayoutManager(requireContext()));
            rv.setHasFixedSize(false);
            rv.setClipToPadding(false);
        }

        adapter = new CategoryAdapter(items, new CategoryAdapter.Listener() {
            @Override
            public void onEdit(Category c) {
                openForm(c);
            }

            @Override
            public void onDelete(Category c) {
                confirmDelete(c);
            }
        });

        if (rv != null) rv.setAdapter(adapter);

        if (fabAdd != null) {
            fabAdd.setOnClickListener(v -> openAddCategorySafely());

            fabAdd.post(() -> {
                if (fabAdd == null) return;
                fabAdd.bringToFront();
                fabAdd.setElevation(100f);
                fabAdd.setTranslationZ(100f);
            });
        }

        if (btnBack != null) {
            btnBack.setOnClickListener(v ->
                    requireActivity().getOnBackPressedDispatcher().onBackPressed()
            );
        }

        if (ivHeaderAction != null) {
            ivHeaderAction.setOnClickListener(v -> {
                if (isLoading) return;
                fetch();
            });
        }

        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    currentQuery = s == null ? "" : s.toString().trim();
                    applyFilter();
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });
        }

        fetch();
    }

    private void openAddCategorySafely() {
        if (!isAdded()) return;
        if (isFormShowing) return;
        if (isLoading) return;

        long now = SystemClock.elapsedRealtime();
        if (now - lastFabClickTime < FAB_CLICK_DELAY_MS) {
            return;
        }
        lastFabClickTime = now;

        setFabEnabled(false);
        openForm(null);
    }

    @Override
    public void onStop() {
        super.onStop();
        try {
            Context ctx = getContext();
            if (ctx != null) {
                ApiClient.getInstance(ctx.getApplicationContext()).cancelAll(FETCH_TAG);
                ApiClient.getInstance(ctx.getApplicationContext()).cancelAll(MUTATION_TAG);
            }
        } catch (Exception e) {
            logw("cancel requests failed: " + e.getMessage());
        }
    }

    private void fetch() {
        if (isLoading) return;
        isLoading = true;
        setLoading(true);

        final String token = session != null ? session.getToken() : null;
        if (token == null || token.trim().isEmpty()) {
            isLoading = false;
            setLoading(false);
            setEmpty(true);
            showToast(getContext(), "Token is missing. Please login again.");
            return;
        }

        final Context ctx = getContext();
        if (ctx == null) {
            isLoading = false;
            setLoading(false);
            return;
        }

        final String url = ApiConfig.url(session, ENDPOINT_CATEGORIES);

        JsonArrayRequest req = new JsonArrayRequest(
                Request.Method.GET,
                url,
                null,
                (JSONArray res) -> {
                    isLoading = false;
                    if (!isAdded()) return;

                    allItems.clear();

                    for (int i = 0; i < res.length(); i++) {
                        JSONObject o = res.optJSONObject(i);
                        if (o == null) continue;

                        Category c = new Category();
                        c.id = o.optInt("id", 0);
                        c.name = o.optString("name", "");
                        c.iconUrl = o.optString("icon_url", "");
                        allItems.add(c);
                    }

                    applyFilter();
                    setLoading(false);
                },
                err -> {
                    isLoading = false;
                    if (!isAdded()) return;
                    setLoading(false);
                    setEmpty(items.isEmpty());
                    toastVolleyError("Fetch categories failed", err);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return authHeaders(token);
            }
        };

        req.setTag(FETCH_TAG);
        ApiClient.getInstance(ctx.getApplicationContext()).add(req);
    }

    private void applyFilter() {
        items.clear();

        if (TextUtils.isEmpty(currentQuery)) {
            items.addAll(allItems);
        } else {
            String q = currentQuery.toLowerCase(Locale.US);

            for (Category c : allItems) {
                String name = c.name == null ? "" : c.name.toLowerCase(Locale.US);
                String icon = c.iconUrl == null ? "" : c.iconUrl.toLowerCase(Locale.US);

                if (name.contains(q) || icon.contains(q)) {
                    items.add(c);
                }
            }
        }

        if (adapter != null) adapter.notifyDataSetChanged();
        setEmpty(items.isEmpty());
    }

    private void openForm(@Nullable Category edit) {
        if (!isAdded()) return;
        if (isFormShowing) return;

        isFormShowing = true;
        setFabEnabled(false);

        final Context formContext = requireContext();
        final Context appContext = formContext.getApplicationContext();
        final SessionManager formSession = new SessionManager(appContext);

        final PendingIconState state = new PendingIconState(appContext);
        currentFormState = state;

        View content = LayoutInflater.from(formContext)
                .inflate(R.layout.dialog_category_form, null, false);

        TextInputEditText etName = content.findViewById(R.id.etCategoryName);
        View btnPick = content.findViewById(R.id.btnPickIcon);
        ImageView imgPreview = content.findViewById(R.id.imgIconPreview);
        state.preview = imgPreview;

        if (edit != null) {
            if (etName != null) etName.setText(edit.name);

            if (imgPreview != null) {
                String iconUrl = normalizeIconUrl(edit.iconUrl);
                if (iconUrl != null) {
                    imgPreview.setVisibility(View.VISIBLE);
                    Glide.with(this).load(iconUrl).into(imgPreview);
                } else {
                    Glide.with(this).clear(imgPreview);
                    imgPreview.setImageDrawable(null);
                    imgPreview.setVisibility(View.GONE);
                }
            }
        } else {
            if (imgPreview != null) {
                Glide.with(this).clear(imgPreview);
                imgPreview.setImageDrawable(null);
                imgPreview.setVisibility(View.GONE);
            }
        }

        if (btnPick != null) {
            btnPick.setOnClickListener(v -> {
                if (pickIconLauncher != null) {
                    pickIconLauncher.launch("image/*");
                } else {
                    showToast(formContext, "Image picker not ready.");
                }
            });
        }

        AlertDialog dialog = new AlertDialog.Builder(formContext)
                .setTitle(edit == null ? "Add Category" : "Edit Category")
                .setView(content)
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .setPositiveButton(edit == null ? "Create" : "Save", null)
                .create();

        dialog.setOnDismissListener(d -> {
            isFormShowing = false;

            if (currentFormState == state) {
                currentFormState = null;
            }
            clearFormIconState(state, false);
            setFabEnabled(true);
        });

        dialog.setOnShowListener(d -> {
            View positiveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (positiveBtn == null) return;

            positiveBtn.setOnClickListener(v -> {
                try {
                    final String name = safeText(etName);
                    if (name.isEmpty()) {
                        if (etName != null) {
                            etName.setError("Name is required");
                            etName.requestFocus();
                        }
                        return;
                    }

                    positiveBtn.setEnabled(false);

                    if (edit == null) {
                        createCategory(appContext, formSession, name, dialog, positiveBtn, state);
                    } else {
                        updateCategory(appContext, formSession, edit.id, name, dialog, positiveBtn, state);
                    }

                } catch (Throwable t) {
                    loge("Save dispatch crashed", t);
                    try {
                        positiveBtn.setEnabled(true);
                    } catch (Exception ignored) {
                    }
                    showToast(formContext, "Unexpected error occurred.");
                }
            });
        });

        dialog.show();
    }

    private void createCategory(
            @NonNull Context appCtx,
            @NonNull SessionManager formSession,
            @NonNull String name,
            @NonNull AlertDialog dialog,
            @NonNull View positiveBtn,
            @NonNull PendingIconState state
    ) {
        setLoading(true);

        final String token = formSession.getToken();
        final String url = ApiConfig.url(formSession, ENDPOINT_CATEGORIES);

        VolleyMultipartRequest req = new VolleyMultipartRequest(
                Request.Method.POST,
                url,
                response -> {
                    setLoading(false);
                    dialog.dismiss();
                    showToast(appCtx, "Category created");

                    if (isAdded()) {
                        fetch();
                    }
                },
                error -> {
                    setLoading(false);
                    positiveBtn.setEnabled(true);
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

                if (state.bytes != null && state.bytes.length > 0) {
                    String fileName = state.fileName != null ? state.fileName : "category_icon.jpg";
                    String mime = state.mime != null ? state.mime : "image/jpeg";
                    out.put("icon", new DataPart(fileName, state.bytes, mime));
                }

                return out;
            }

            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return authHeaders(token);
            }
        };

        req.setTag(MUTATION_TAG);
        ApiClient.getInstance(appCtx).add(req);
    }

    private void updateCategory(
            @NonNull Context appCtx,
            @NonNull SessionManager formSession,
            int id,
            @NonNull String name,
            @NonNull AlertDialog dialog,
            @NonNull View positiveBtn,
            @NonNull PendingIconState state
    ) {
        setLoading(true);

        final String token = formSession.getToken();
        final String url = ApiConfig.url(formSession, ENDPOINT_CATEGORIES) + id + "/";

        VolleyMultipartRequest req = new VolleyMultipartRequest(
                Request.Method.PUT,
                url,
                response -> {
                    setLoading(false);
                    dialog.dismiss();
                    showToast(appCtx, "Category updated");

                    if (isAdded()) {
                        fetch();
                    }
                },
                error -> {
                    setLoading(false);
                    positiveBtn.setEnabled(true);
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

                if (state.bytes != null && state.bytes.length > 0) {
                    String fileName = state.fileName != null ? state.fileName : "category_icon.jpg";
                    String mime = state.mime != null ? state.mime : "image/jpeg";
                    out.put("icon", new DataPart(fileName, state.bytes, mime));
                }

                return out;
            }

            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return authHeaders(token);
            }
        };

        req.setTag(MUTATION_TAG);
        ApiClient.getInstance(appCtx).add(req);
    }

    @Nullable
    private String normalizeIconUrl(@Nullable String url) {
        if (url == null) return null;

        String s = url.trim();
        if (s.isEmpty()) return null;
        if ("null".equalsIgnoreCase(s)) return null;
        if ("/null".equalsIgnoreCase(s)) return null;

        return s;
    }

    private void confirmDelete(@NonNull Category c) {
        if (!isAdded()) return;
        if (isDeleteRunning) return;

        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Category")
                .setMessage("Delete \"" + c.name + "\"?")
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .setPositiveButton("Delete", (d, w) -> deleteCategory(c.id))
                .show();
    }

    private void deleteCategory(int id) {
        if (isDeleteRunning) return;
        isDeleteRunning = true;
        setLoading(true);

        final Context ctx = getContext();
        if (ctx == null) {
            isDeleteRunning = false;
            setLoading(false);
            return;
        }

        final String token = session != null ? session.getToken() : null;
        final String url = ApiConfig.url(session, ENDPOINT_CATEGORIES) + id + "/";

        StringRequest req = new StringRequest(
                Request.Method.DELETE,
                url,
                res -> {
                    isDeleteRunning = false;
                    if (!isAdded()) return;
                    setLoading(false);
                    showToast(ctx, "Category deleted");
                    fetch();
                },
                err -> {
                    isDeleteRunning = false;
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

        req.setTag(MUTATION_TAG);
        ApiClient.getInstance(ctx.getApplicationContext()).add(req);
    }

    private void setLoading(boolean loading) {
        if (progress != null) progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        setFabEnabled(!loading);
    }

    private void setFabEnabled(boolean enabled) {
        if (fabAdd == null) return;
        boolean finalEnabled = enabled && !isFormShowing && !isLoading && !isDeleteRunning;
        fabAdd.setEnabled(finalEnabled);
        fabAdd.setAlpha(finalEnabled ? 1f : 0.65f);
    }

    private void setEmpty(boolean empty) {
        if (tvEmpty != null) {
            tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            if (empty) {
                tvEmpty.setText(TextUtils.isEmpty(currentQuery) ? "No data yet." : "No matching categories.");
            }
        }
        if (rv != null) rv.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    @NonNull
    private String safeText(@Nullable TextInputEditText et) {
        if (et == null || et.getText() == null) return "";
        return et.getText().toString().trim();
    }

    private Map<String, String> authHeaders(@Nullable String token) {
        Map<String, String> h = new HashMap<>();
        h.put("Accept", "application/json");
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
                String body = new String(r.data, StandardCharsets.UTF_8);
                msg = prefix + " (" + r.statusCode + ")";
                logw(prefix + " body=" + body);
            } else if (err.getMessage() != null) {
                msg = prefix + ": " + err.getMessage();
            }
        } catch (Exception ignored) {
        }

        logw(msg);
        showToast(getContext(), msg);
    }

    private void clearFormIconState(@NonNull PendingIconState state, boolean clearPreview) {
        state.uri = null;
        state.bytes = null;
        state.fileName = null;
        state.mime = null;

        if (clearPreview && state.preview != null) {
            try {
                Glide.with(this).clear(state.preview);
                state.preview.setImageDrawable(null);
                state.preview.setVisibility(View.GONE);
            } catch (Exception ignored) {
            }
        }
    }

    @Nullable
    private static String normalizeAllowedMime(@Nullable String mime) {
        if (mime == null) return null;

        String m = mime.trim().toLowerCase();
        switch (m) {
            case "image/jpeg":
            case "image/jpg":
                return "image/jpeg";
            case "image/png":
                return "image/png";
            case "image/webp":
                return "image/webp";
            default:
                return null;
        }
    }

    @NonNull
    private static String ensureFileExtension(@NonNull String fileName, @NonNull String mime) {
        String lower = fileName.toLowerCase();

        if ("image/jpeg".equals(mime)) {
            if (!lower.endsWith(".jpg") && !lower.endsWith(".jpeg")) {
                return fileName + ".jpg";
            }
            return fileName;
        }

        if ("image/png".equals(mime)) {
            if (!lower.endsWith(".png")) {
                return fileName + ".png";
            }
            return fileName;
        }

        if ("image/webp".equals(mime)) {
            if (!lower.endsWith(".webp")) {
                return fileName + ".webp";
            }
            return fileName;
        }

        return fileName;
    }

    @Nullable
    private static byte[] resizeAndCompressImage(@NonNull byte[] input, @NonNull String mime, int maxDimension) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(input, 0, input.length, bounds);

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension, maxDimension);

        Bitmap bitmap = BitmapFactory.decodeByteArray(input, 0, input.length, opts);
        if (bitmap == null) {
            return null;
        }

        Bitmap scaled = bitmap;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int maxCurrent = Math.max(width, height);

        if (maxCurrent > maxDimension) {
            float ratio = (float) maxDimension / (float) maxCurrent;
            int newW = Math.max(1, Math.round(width * ratio));
            int newH = Math.max(1, Math.round(height * ratio));
            scaled = Bitmap.createScaledBitmap(bitmap, newW, newH, true);
            if (scaled != bitmap) {
                bitmap.recycle();
            }
        }

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            Bitmap.CompressFormat format;
            int quality;

            switch (mime) {
                case "image/png":
                    format = Bitmap.CompressFormat.PNG;
                    quality = PNG_COMPRESSION_QUALITY;
                    break;
                case "image/webp":
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        format = Bitmap.CompressFormat.WEBP_LOSSY;
                    } else {
                        format = Bitmap.CompressFormat.WEBP;
                    }
                    quality = WEBP_QUALITY;
                    break;
                case "image/jpeg":
                default:
                    format = Bitmap.CompressFormat.JPEG;
                    quality = JPEG_QUALITY;
                    break;
            }

            boolean ok = scaled.compress(format, quality, bos);
            if (!ok) return null;

            return bos.toByteArray();
        } finally {
            if (!scaled.isRecycled()) {
                scaled.recycle();
            }
        }
    }

    private static int calculateInSampleSize(int width, int height, int reqWidth, int reqHeight) {
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return Math.max(1, inSampleSize);
    }

    @Nullable
    private static byte[] readBytes(@NonNull Context ctx, @NonNull Uri uri) {
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            if (in == null) return null;

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);

            return bos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    @NonNull
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
        } catch (Exception ignored) {
        }

        return "image/jpeg";
    }

    @NonNull
    private static String guessFileName(@NonNull Context ctx, @NonNull Uri uri, @NonNull String fallbackBase) {
        Cursor c = null;

        try {
            c = ctx.getContentResolver().query(uri, null, null, null, null);
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String name = c.getString(idx);
                    if (name != null && !name.trim().isEmpty()) {
                        return name;
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }

        return fallbackBase + "_" + System.currentTimeMillis() + ".jpg";
    }

    private void showToast(@Nullable Context ctx, @NonNull String msg) {
        if (ctx == null) return;
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show();
    }

    private static void logd(@NonNull String msg) {
        if (BuildConfig.DEBUG) Log.d(TAG, msg);
    }

    private static void logw(@NonNull String msg) {
        Log.w(TAG, msg);
    }

    private static void loge(@NonNull String msg, @NonNull Throwable t) {
        Log.e(TAG, msg, t);
    }

    private static class PendingIconState {
        final Context appContext;
        @Nullable Uri uri;
        @Nullable byte[] bytes;
        @Nullable String fileName;
        @Nullable String mime;
        @Nullable ImageView preview;

        PendingIconState(@NonNull Context appContext) {
            this.appContext = appContext;
        }
    }

    static class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.VH> {

        interface Listener {
            void onEdit(Category c);
            void onDelete(Category c);
        }

        private final List<Category> data;
        private final Listener listener;

        CategoryAdapter(@NonNull List<Category> data, @NonNull Listener listener) {
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

            if (h.tvTitle != null) h.tvTitle.setText(c.name);

            if (h.img != null) {
                String iconUrl = c.iconUrl != null ? c.iconUrl.trim() : "";

                if (!iconUrl.isEmpty()
                        && !"null".equalsIgnoreCase(iconUrl)
                        && !"/null".equalsIgnoreCase(iconUrl)) {
                    h.img.setVisibility(View.VISIBLE);
                    Glide.with(h.img.getContext())
                            .load(iconUrl)
                            .placeholder(R.drawable.ic_store)
                            .error(R.drawable.ic_store)
                            .into(h.img);
                } else {
                    Glide.with(h.img.getContext()).clear(h.img);
                    h.img.setImageDrawable(null);
                    h.img.setVisibility(View.GONE);
                }
            }

            if (h.btnEdit != null) h.btnEdit.setOnClickListener(v -> listener.onEdit(c));
            if (h.btnDelete != null) h.btnDelete.setOnClickListener(v -> listener.onDelete(c));
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

    public abstract static class VolleyMultipartRequest extends com.android.volley.Request<NetworkResponse> {

        private final Response.Listener<NetworkResponse> listener;
        @Nullable
        private final Response.ErrorListener errorListener;
        private String boundary;

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
                        bos.write(("--" + b + "\r\n").getBytes(StandardCharsets.UTF_8));
                        bos.write(("Content-Disposition: form-data; name=\"" + e.getKey() + "\"\r\n\r\n")
                                .getBytes(StandardCharsets.UTF_8));
                        bos.write((e.getValue() + "\r\n").getBytes(StandardCharsets.UTF_8));
                    }
                }

                Map<String, DataPart> data = getByteData();

                if (data != null) {
                    for (Map.Entry<String, DataPart> e : data.entrySet()) {
                        DataPart p = e.getValue();

                        bos.write(("--" + b + "\r\n").getBytes(StandardCharsets.UTF_8));
                        bos.write(("Content-Disposition: form-data; name=\"" + e.getKey()
                                + "\"; filename=\"" + p.fileName + "\"\r\n")
                                .getBytes(StandardCharsets.UTF_8));
                        bos.write(("Content-Type: " + p.type + "\r\n\r\n")
                                .getBytes(StandardCharsets.UTF_8));
                        bos.write(p.content);
                        bos.write("\r\n".getBytes(StandardCharsets.UTF_8));
                    }
                }

                bos.write(("--" + b + "--\r\n").getBytes(StandardCharsets.UTF_8));
                return bos.toByteArray();

            } catch (Exception ex) {
                loge("Multipart build failed", ex);
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
}