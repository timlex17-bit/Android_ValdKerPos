package com.valdker.pos.ui.common;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.toolbox.StringRequest;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.valdker.pos.R;
import com.valdker.pos.SessionManager;
import com.valdker.pos.network.ApiClient;
import com.valdker.pos.network.ApiConfig;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Memilih satu nilai dari daftar, alih-alih mengetiknya.
 *
 * <p>Dipakai di mana pun sebuah kolom sebenarnya hanya menerima sekumpulan
 * nilai tertentu: relasi di formulir bengkel (kendaraan, mekanik, work order)
 * dan penyaring di layar Laporan (produk, kategori, metode pembayaran).
 * Sebelumnya hanya modul bengkel yang punya pemilih seperti ini dan ia
 * terkurung di paketnya sendiri, sehingga layar Laporan tetap meminta
 * pengguna mengetik - termasuk mengetik NOMOR kategori, yang tidak mungkin
 * ditebak siapa pun.
 *
 * <p>Nilainya bertipe String, bukan int, karena tidak semua penyaring memakai
 * id: metode pembayaran disaring dengan kodenya ({@code CASH}, {@code QRIS},
 * dan juga {@code SPLIT}/{@code UNPAID} yang bukan baris tabel mana pun).
 * Pemanggil yang memang butuh id tinggal mengurai kembali.
 */
public final class RecordPickerDialog {

    private static final int TIMEOUT_MS = 20000;

    /** Satu baris yang bisa dipilih: apa yang dibaca, dan apa yang dikirim. */
    public static final class Option {
        @NonNull public final String value;
        @NonNull public final String label;

        public Option(@NonNull String value, @NonNull String label) {
            this.value = value;
            this.label = label;
        }

        /** Nilai kosong berarti "tanpa penyaring" / "semua". */
        public boolean isEmpty() {
            return value.trim().isEmpty();
        }

        public int asId() {
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
    }

    public interface OptionBuilder {
        /** @return null untuk melewati baris ini (data tidak lengkap). */
        @Nullable
        Option build(@NonNull JSONObject item);
    }

    public interface Listener {
        void onPicked(@NonNull Option option);
    }

    private RecordPickerDialog() {
    }

    /** Daftar tetap yang sudah diketahui pemanggil - tanpa permintaan jaringan. */
    public static void fromOptions(@NonNull Context context,
                                   @NonNull String title,
                                   @NonNull List<Option> options,
                                   @NonNull Listener listener) {
        if (options.isEmpty()) {
            showMessage(context, title, context.getString(R.string.picker_empty));
            return;
        }
        showList(context, title, options, listener);
    }

    /**
     * Daftar yang diambil dari sebuah endpoint.
     *
     * @param leadingOption baris yang selalu ditaruh paling atas - biasanya
     *                      "Semua" atau "Kosongkan". Null berarti tanpa baris
     *                      itu. Tanpa opsi ini sebuah penyaring yang terlanjur
     *                      diisi tidak akan pernah bisa dikosongkan lagi.
     */
    public static void fromEndpoint(@NonNull Context context,
                                    @NonNull String title,
                                    @NonNull String endpoint,
                                    @Nullable Option leadingOption,
                                    @NonNull OptionBuilder builder,
                                    @NonNull Listener listener) {

        SessionManager session = new SessionManager(context);
        String url = ApiConfig.url(session, endpoint);

        androidx.appcompat.app.AlertDialog loading = new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(context.getString(R.string.picker_loading))
                .setCancelable(true)
                .show();

        StringRequest req = new StringRequest(
                Request.Method.GET,
                url,
                body -> {
                    loading.dismiss();
                    List<Option> options = new ArrayList<>();

                    try {
                        JSONArray arr = extractArray(body);
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject item = arr.optJSONObject(i);
                            if (item == null) continue;
                            Option option = builder.build(item);
                            if (option == null || option.label.trim().isEmpty()) continue;
                            options.add(option);
                        }
                    } catch (Exception e) {
                        showMessage(context, title, context.getString(R.string.picker_unreadable));
                        return;
                    }

                    if (options.isEmpty()) {
                        // Daftar kosong bukan kegagalan: datanya memang belum
                        // dibuat. Mengatakannya apa adanya lebih berguna
                        // daripada dialog kosong yang bisa dipencet.
                        showMessage(context, title, context.getString(R.string.picker_empty));
                        return;
                    }

                    if (leadingOption != null) {
                        options.add(0, leadingOption);
                    }

                    showList(context, title, options, listener);
                },
                error -> {
                    loading.dismiss();
                    String detail = "";
                    if (error != null && error.networkResponse != null
                            && error.networkResponse.data != null) {
                        try {
                            detail = new String(error.networkResponse.data, StandardCharsets.UTF_8);
                        } catch (Exception ignored) {
                        }
                    }
                    if (detail.length() > 160) detail = detail.substring(0, 160) + "...";
                    showMessage(context, title, context.getString(
                            R.string.picker_failed,
                            detail.isEmpty() ? "network error" : detail));
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> h = new HashMap<>();
                h.put("Accept", "application/json");
                String token = session.getToken();
                if (token != null && !token.trim().isEmpty()) {
                    h.put("Authorization", "Token " + token.trim());
                }
                return h;
            }
        };

        req.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, 1, 1.2f));
        req.setShouldCache(false);
        ApiClient.getInstance(context).add(req);
    }

    private static void showList(@NonNull Context context,
                                 @NonNull String title,
                                 @NonNull List<Option> options,
                                 @NonNull Listener listener) {
        String[] labels = new String[options.size()];
        for (int i = 0; i < options.size(); i++) {
            labels[i] = options.get(i).label;
        }

        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setItems(labels, (d, which) -> listener.onPicked(options.get(which)))
                .setNegativeButton(context.getString(R.string.action_cancel), null)
                .show();
    }

    private static void showMessage(@NonNull Context context,
                                    @NonNull String title,
                                    @NonNull String message) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(context.getString(R.string.action_close), null)
                .show();
    }

    /** Endpoint ini membalas array telanjang, tapi terima juga bentuk paginasi. */
    @NonNull
    private static JSONArray extractArray(@Nullable String body) throws Exception {
        if (body == null || body.trim().isEmpty()) return new JSONArray();
        Object parsed = new JSONTokener(body).nextValue();
        if (parsed instanceof JSONArray) return (JSONArray) parsed;
        if (parsed instanceof JSONObject) {
            JSONArray results = ((JSONObject) parsed).optJSONArray("results");
            if (results != null) return results;
        }
        return new JSONArray();
    }
}
