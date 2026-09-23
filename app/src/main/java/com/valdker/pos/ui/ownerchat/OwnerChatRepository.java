package com.valdker.pos.ui.ownerchat;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.valdker.pos.BuildConfig;
import com.valdker.pos.R;
import com.valdker.pos.SessionManager;
import com.valdker.pos.network.ApiConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Jalur jaringan Owner Chat.
 *
 * <p>Dulu menunjuk {@code POST /api/owner/chat/}, sebuah endpoint berbasis
 * aturan yang tidak pernah memanggil model bahasa sama sekali - ia mencocokkan
 * kata kunci lalu merangkai string berformat tetap, sehingga setiap pertanyaan
 * dijawab template. Sekarang menunjuk {@code POST /api/ai/assistant/chat/},
 * yang memilih alat baca-saja lewat LLM dan menjawab dari hasil query
 * sungguhan. Endpoint lama tidak dihapus di server; layar ini saja yang
 * berpindah.
 *
 * <p>Kontrak lengkapnya: {@code docs/api/AI_ASSISTANT_API.md} pada repo Django.
 */
public class OwnerChatRepository {

    public interface ChatCallback {
        void onSuccess(OwnerChatResponse res);

        /**
         * @param kind    jenis kegagalan, supaya pemanggil bisa memulihkan diri
         *                (mis. melupakan percakapan yang sudah tidak ada)
         * @param message teks siap tampil; tidak pernah berisi rincian dari
         *                server atau dari penyedia AI
         */
        void onError(@NonNull AssistantError kind, @NonNull String message);
    }

    private static final String TAG = "OWNER_CHAT";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    /**
     * Endpoint asisten bisnis. Disimpan tanpa garis miring di depan supaya
     * {@code ApiConfig.url()} bisa merapikannya untuk semua bentuk base URL.
     */
    static final String ENDPOINT = "api/ai/assistant/chat/";

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build();
    private final SessionManager session;
    private final Context ctx;

    public OwnerChatRepository(Context ctx, SessionManager session) {
        this.ctx = ctx.getApplicationContext();
        this.session = session;
    }

    public void sendChat(String message, @Nullable Integer conversationId, ChatCallback cb) {
        try {
            String token = session.getToken();
            if (TextUtils.isEmpty(token)) {
                cb.onError(AssistantError.UNAUTHENTICATED,
                        ctx.getString(R.string.owner_chat_session_expired));
                return;
            }

            String shopCode = session.getShopCode();
            if (TextUtils.isEmpty(shopCode)) {
                cb.onError(AssistantError.FORBIDDEN,
                        ctx.getString(R.string.owner_chat_shop_context_missing));
                return;
            }

            JSONObject obj = AssistantChatRequest.body(message, conversationId, currentLanguage());
            RequestBody body = RequestBody.create(obj.toString(), JSON);
            final String url = ApiConfig.url(session, ENDPOINT);

            if (BuildConfig.DEBUG) {
                // Hanya alamat, asalnya, dan bentuk permintaan. Token, kode
                // toko, dan isi pertanyaan pengguna sengaja tidak dicatat.
                Log.i(TAG, "DIAG request url=" + url
                        + " base=" + ApiConfig.describe(session)
                        + " keys=" + keysOf(obj)
                        + " message_len=" + message.length()
                        + " has_conversation_id=" + (conversationId != null));
            }

            Request req = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", AssistantChatRequest.authHeader(token))
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")
                    .addHeader("X-Shop-Code", shopCode)
                    .post(body)
                    .build();

            client.newCall(req).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    // Pesan pengecualiannya tidak pernah ikut ke layar; hanya
                    // jenisnya yang menentukan teks mana yang dipakai.
                    Log.w(TAG, "Assistant chat network failed: " + e.getClass().getSimpleName());
                    if (e instanceof SocketTimeoutException || e instanceof UnknownHostException) {
                        cb.onError(AssistantError.NETWORK,
                                ctx.getString(R.string.owner_chat_network_timeout));
                    } else {
                        cb.onError(AssistantError.NETWORK,
                                ctx.getString(R.string.owner_chat_unable_contact));
                    }
                }

                @Override
                public void onResponse(Call call, Response response) {
                    String raw = "";
                    try {
                        raw = response.body() != null ? response.body().string() : "";
                    } catch (IOException e) {
                        Log.w(TAG, "Assistant chat body unreadable: " + e.getClass().getSimpleName());
                    }

                    OwnerChatResponse parsed = null;
                    try {
                        if (!raw.trim().isEmpty()) {
                            parsed = OwnerChatResponse.fromJson(new JSONObject(raw));
                        }
                    } catch (Exception ignored) {
                        // Badan yang bukan JSON - halaman galat proxy, HTML
                        // 502, badan terpotong. Ditangani di bawah sebagai
                        // UNREADABLE, bukan dilempar ke pemanggil mentah.
                        parsed = null;
                    }

                    if (BuildConfig.DEBUG) {
                        Log.i(TAG, "DIAG response url=" + call.request().url()
                                + " status=" + response.code()
                                + " " + describeSafely(raw, parsed));
                    }

                    if (!response.isSuccessful()) {
                        AssistantError kind = AssistantError.classify(
                                response.code(), parsed != null ? parsed.errorCode : null);
                        cb.onError(kind, messageFor(kind));
                        return;
                    }

                    if (parsed == null) {
                        cb.onError(AssistantError.UNREADABLE,
                                ctx.getString(R.string.owner_chat_response_unreadable));
                        return;
                    }

                    // Sebuah 200 yang tetap membawa amplop galat bukan jawaban.
                    // Tanpa pemeriksaan ini, teks galat server akan tampil
                    // sebagai kalimat asisten yang meyakinkan.
                    if (parsed.hasError()) {
                        AssistantError kind = AssistantError.classify(
                                response.code(), parsed.errorCode);
                        cb.onError(kind, messageFor(kind));
                        return;
                    }

                    if (!parsed.hasReplyText()) {
                        cb.onError(AssistantError.UNREADABLE,
                                ctx.getString(R.string.owner_chat_response_unreadable));
                        return;
                    }

                    cb.onSuccess(parsed);
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Assistant chat request failed: " + e.getClass().getSimpleName());
            cb.onError(AssistantError.UNKNOWN, ctx.getString(R.string.owner_chat_unable_contact));
        }
    }

    /** Teks siap tampil untuk tiap jenis kegagalan. Semuanya milik aplikasi. */
    @NonNull
    private String messageFor(@NonNull AssistantError kind) {
        switch (kind) {
            case UNAUTHENTICATED:
                return ctx.getString(R.string.owner_chat_session_expired);
            case FORBIDDEN:
                return ctx.getString(R.string.owner_chat_no_permission);
            case CONVERSATION_GONE:
                return ctx.getString(R.string.owner_chat_conversation_expired);
            case INVALID_REQUEST:
                return ctx.getString(R.string.owner_chat_question_not_processed);
            case RATE_LIMITED:
                return ctx.getString(R.string.owner_chat_rate_limited);
            case SERVER:
                return ctx.getString(R.string.owner_chat_server_error);
            case UNREADABLE:
                return ctx.getString(R.string.owner_chat_response_unreadable);
            case NETWORK:
                return ctx.getString(R.string.owner_chat_network_timeout);
            default:
                return ctx.getString(R.string.owner_chat_unable_contact);
        }
    }

    @NonNull
    private String currentLanguage() {
        try {
            return AssistantChatRequest.language(
                    ctx.getResources().getConfiguration().getLocales().get(0).getLanguage());
        } catch (Exception ignored) {
            return AssistantChatRequest.LANGUAGE_EN;
        }
    }

    /**
     * Ringkasan respons yang aman dicatat: kunci apa yang ada, asal jawaban,
     * kode galat, dan panjang teks - bukan isi jawabannya, dan bukan satu pun
     * angka di dalam {@code structured_data}. Angka-angka itu adalah omzet dan
     * stok toko; ia boleh tampil di layar pemiliknya, tidak di logcat yang
     * bisa dibaca aplikasi lain pada perangkat yang di-root.
     */
    @NonNull
    private static String describeSafely(@NonNull String raw, @Nullable OwnerChatResponse parsed) {
        if (parsed == null) {
            return "body=<unparseable> bytes=" + raw.length();
        }
        return "keys=[conversation_id,message,structured_data,source,generated_at,error]"
                + " source=" + parsed.source
                + " conversation_id=" + parsed.conversationId
                + " error_code=" + parsed.errorCode
                + " message_len=" + parsed.replyText.length()
                + " has_structured_data=" + (parsed.structuredData != null)
                + " cards=" + parsed.cards.size()
                + " links=" + parsed.links.size()
                + " " + describeStructuredDataSafely(parsed.structuredDataRaw);
    }

    /**
     * Bentuk {@code structured_data} - tipe, nama kunci tingkat atas, dan
     * panjang larik saja - tidak pernah satu pun nilainya (angka penjualan,
     * nama produk, dsb.). {@code category} dihitung lewat
     * {@link StructuredDataMapper} yang sama yang dipakai layar untuk
     * menampilkannya, supaya baris log ini selalu cocok dengan apa yang
     * sungguh dirender, bukan salinan logika yang bisa menyimpang.
     */
    @NonNull
    private static String describeStructuredDataSafely(@Nullable Object raw) {
        StructuredDataView mapped = StructuredDataMapper.map(raw);
        StringBuilder desc = new StringBuilder("structured_data_category=").append(mapped.category);

        if (raw instanceof JSONObject) {
            JSONObject obj = (JSONObject) raw;
            StringBuilder keys = new StringBuilder();
            Iterator<String> it = obj.keys();
            while (it.hasNext()) {
                if (keys.length() > 0) keys.append(",");
                keys.append(it.next());
            }
            desc.append(" structured_data_type=object")
                    .append(" structured_data_top_level_keys=[").append(keys).append("]");
        } else if (raw instanceof JSONArray) {
            desc.append(" structured_data_type=array")
                    .append(" structured_data_array_length=").append(((JSONArray) raw).length());
        } else if (raw == null || raw == JSONObject.NULL) {
            desc.append(" structured_data_type=none");
        } else {
            desc.append(" structured_data_type=unsupported");
        }

        return desc.toString();
    }

    @NonNull
    private static String keysOf(@NonNull JSONObject obj) {
        StringBuilder sb = new StringBuilder("[");
        for (Iterator<String> it = obj.keys(); it.hasNext(); ) {
            sb.append(it.next());
            if (it.hasNext()) sb.append(',');
        }
        return sb.append(']').toString();
    }
}
