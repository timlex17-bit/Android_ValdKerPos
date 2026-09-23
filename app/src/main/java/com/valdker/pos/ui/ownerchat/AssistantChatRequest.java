package com.valdker.pos.ui.ownerchat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

/**
 * Bentuk permintaan untuk {@code POST /api/ai/assistant/chat/}.
 *
 * <p>Kontraknya ada di {@code docs/api/AI_ASSISTANT_API.md} pada repo Django
 * dan ditegakkan oleh {@code ai_assistant/serializers.py}:
 *
 * <pre>
 * {"message": "...", "conversation_id": null|int, "language": "en"|"id"}
 * </pre>
 *
 * <p>Tiga hal yang mudah salah dan karena itu dikunci di sini:
 *
 * <ul>
 *   <li>{@code conversation_id} adalah <b>bilangan bulat</b>, bukan teks.
 *       Endpoint lama ({@code /api/owner/chat/}) tidak pernah mengembalikan
 *       field ini sama sekali, jadi klien lama menyimpannya sebagai String -
 *       bentuk yang tidak cocok dengan {@code IntegerField} di server.</li>
 *   <li>Percakapan baru dimulai dengan <b>menghilangkan</b> field itu, bukan
 *       mengirim 0 atau string kosong.</li>
 *   <li>{@code language} hanya menerima {@code "en"} atau {@code "id"}. Nilai
 *       lain - termasuk Tetun, yang dipakai salah satu terjemahan aplikasi -
 *       ditolak server dengan 400 {@code validation_error}, jadi pemetaannya
 *       dilakukan di sini alih-alih meneruskan kode bahasa perangkat apa
 *       adanya.</li>
 * </ul>
 *
 * <p>Tidak ada token, sandi, atau kode toko di dalam badan permintaan:
 * autentikasi memakai header {@code Authorization}, dan toko diselesaikan
 * server dari pengguna yang terautentikasi.
 */
public final class AssistantChatRequest {

    /** Batas panjang pesan yang ditegakkan server (AIChatRequestSerializer). */
    public static final int MAX_MESSAGE_LENGTH = 2000;

    public static final String LANGUAGE_EN = "en";
    public static final String LANGUAGE_ID = "id";

    private AssistantChatRequest() {
    }

    /**
     * @param conversationId id percakapan sebelumnya, atau null untuk memulai
     *                       percakapan baru
     */
    @NonNull
    public static JSONObject body(@NonNull String message,
                                  @Nullable Integer conversationId,
                                  @NonNull String language) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("message", message);
        obj.put("language", language);

        // Dihilangkan, bukan dikirim sebagai null: serializer-nya sudah
        // memberi nilai baku null, dan kunci yang absen adalah bentuk yang
        // paling jelas berarti "mulai percakapan baru".
        if (conversationId != null && conversationId > 0) {
            obj.put("conversation_id", (int) conversationId);
        }

        return obj;
    }

    /**
     * Nilai header Authorization. Token yang tersimpan kadang sudah memuat
     * awalan skema, kadang belum - keduanya harus menghasilkan satu bentuk.
     */
    @NonNull
    public static String authHeader(@NonNull String token) {
        String trimmed = token.trim();
        return trimmed.startsWith("Token ") ? trimmed : ("Token " + trimmed);
    }

    /**
     * Memetakan kode bahasa perangkat ke salah satu bahasa yang diterima
     * server.
     *
     * <p>Bahasa Indonesia punya dua kode yang beredar: {@code "id"} dan
     * {@code "in"} - yang terakhir adalah kode lama yang masih dikembalikan
     * {@link Locale} pada Java. Keduanya dipetakan ke {@code "id"}. Selain itu
     * - termasuk Tetun - jatuh ke {@code "en"}, karena kontraknya menyatakan
     * Tetun belum ada dan nilai tak dikenal ditolak dengan 400.
     */
    @NonNull
    public static String language(@Nullable String languageTag) {
        if (languageTag == null) return LANGUAGE_EN;

        String tag = languageTag.trim().toLowerCase(Locale.US);
        if (tag.isEmpty()) return LANGUAGE_EN;

        int separator = indexOfSeparator(tag);
        if (separator > 0) {
            tag = tag.substring(0, separator);
        }

        if (LANGUAGE_ID.equals(tag) || "in".equals(tag)) {
            return LANGUAGE_ID;
        }
        return LANGUAGE_EN;
    }

    private static int indexOfSeparator(@NonNull String tag) {
        int dash = tag.indexOf('-');
        int underscore = tag.indexOf('_');
        if (dash < 0) return underscore;
        if (underscore < 0) return dash;
        return Math.min(dash, underscore);
    }
}
