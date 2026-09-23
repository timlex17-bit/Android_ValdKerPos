package com.valdker.pos.ui.ownerchat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Satu jawaban asisten, dibaca dari dua bentuk respons yang berbeda.
 *
 * <p>Bentuk baru - {@code POST /api/ai/assistant/chat/} - selalu memuat enam
 * kunci yang sama, sukses maupun gagal: {@code conversation_id},
 * {@code message}, {@code structured_data}, {@code source},
 * {@code generated_at}, {@code error}. Bentuk lama -
 * {@code POST /api/owner/chat/} - memakai {@code reply_text} beserta
 * {@code cards} dan {@code links}.
 *
 * <p>Urutan pembacaan teks utamanya sengaja menaruh {@code message} lebih dulu.
 * Endpoint lama menyalin {@code reply_text} ke {@code message} juga, jadi
 * kedua bentuk tetap terbaca; sebaliknya, kalau {@code reply_text} yang
 * didahulukan, sebuah respons campuran dari endpoint lama akan menutupi
 * jawaban AI - persis kegagalan yang diminta untuk dihindari.
 *
 * <p>Kelas ini sengaja bebas dari kelas Android (tidak memakai
 * {@code TextUtils}) supaya bisa diuji langsung di JVM dengan
 * {@code org.json} yang asli.
 */
public class OwnerChatResponse {

    /** Teks jawaban yang ditampilkan ke pengguna. Bisa kosong. */
    public String replyText = "";

    /**
     * Id percakapan dari server, atau null bila belum/tidak ada.
     *
     * <p>Bilangan bulat, bukan teks: {@code AIChatRequestSerializer}
     * mendeklarasikannya sebagai {@code IntegerField}.
     */
    @Nullable
    public Integer conversationId;

    /**
     * Asal jawaban menurut server: {@code tool}, {@code llm}, {@code fallback},
     * atau {@code error}.
     *
     * <p>{@code "error"} di sini <b>bukan</b> kegagalan permintaan. Kontraknya
     * menyatakan bahwa ketika model mengusulkan argumen yang tidak sah, server
     * tetap menjawab 200 dengan penjelasan berbahasa manusia dan
     * {@code source: "error"} - itu asisten yang gagal membantu, bukan
     * permintaan yang salah. Kegagalan sungguhan terlihat dari status HTTP dan
     * dari {@link #errorCode}.
     */
    public String source = "";

    /** Waktu jawaban dibuat server (ISO 8601), apa adanya. */
    public String generatedAt = "";

    /**
     * Angka-angka pendukung jawaban ketika bentuknya berupa objek JSON, atau
     * null bila tidak ada atau bentuknya bukan objek.
     *
     * <p>Dipertahankan apa adanya (tipe dan perilaku, termasuk untuk bentuk
     * bukan-objek) demi kompatibilitas ke belakang dengan kode dan uji yang
     * sudah memanggil {@code .getInt(...)}/{@code .getString(...)} langsung
     * di atasnya. {@link #structuredDataRaw} adalah sumber yang dipakai
     * {@link StructuredDataMapper} - ia menyimpan nilai apa adanya tanpa
     * peduli tipenya, supaya bentuk objek, larik, maupun bentuk lain semua
     * bisa dipetakan.
     */
    @Nullable
    public JSONObject structuredData;

    /**
     * Nilai {@code structured_data} apa adanya - {@link JSONObject},
     * {@link JSONArray}, {@link JSONObject#NULL}, atau tipe primitif lain -
     * dipetakan oleh {@link StructuredDataMapper#map(Object)} menjadi
     * sesuatu yang bisa ditampilkan. Kontrak sungguhannya
     * ({@code ai_assistant/tools/registry.py}) hanya pernah mengirim objek
     * datar, tidak pernah larik di tingkat atas - bentuk lain ditangani di
     * sini semata sebagai jaga-jaga, bukan karena backend mengirimkannya.
     */
    @Nullable
    public Object structuredDataRaw;

    /** {@code error.code} bila respons membawa amplop galat. */
    @Nullable
    public String errorCode;

    public List<Card> cards = new ArrayList<>();
    public List<Link> links = new ArrayList<>();

    /**
     * Apakah respons ini membawa amplop galat.
     *
     * <p>Diperiksa terpisah dari status HTTP: sebuah 200 yang membawa
     * {@code error} tidak boleh tampil sebagai jawaban biasa.
     */
    public boolean hasError() {
        return errorCode != null && !errorCode.trim().isEmpty();
    }

    public boolean hasReplyText() {
        return !replyText.trim().isEmpty();
    }

    @NonNull
    public static OwnerChatResponse fromJson(@NonNull JSONObject js) {
        OwnerChatResponse res = new OwnerChatResponse();

        res.replyText = firstNonEmpty(
                optString(js, "message"),
                optString(js, "reply_text"),
                optString(js, "reply"),
                optString(js, "answer")
        );

        res.conversationId = optInteger(js, "conversation_id");
        res.source = optString(js, "source");
        res.generatedAt = optString(js, "generated_at");
        res.structuredData = js.optJSONObject("structured_data");
        res.structuredDataRaw = js.isNull("structured_data") ? null : js.opt("structured_data");

        JSONObject error = js.optJSONObject("error");
        if (error != null) {
            String code = optString(error, "code");
            res.errorCode = code.isEmpty() ? "error" : code;
        } else {
            // Bentuk lama memakai {"ok": false, "error": "<teks>"} - "error"
            // sebagai string, bukan objek. Tetap dikenali sebagai galat.
            String legacy = optString(js, "error");
            boolean flaggedFailure = js.has("ok") && !js.optBoolean("ok", true);
            if (!legacy.isEmpty() || flaggedFailure) {
                res.errorCode = legacy.isEmpty() ? "error" : "error";
            }
        }

        JSONArray cardsArray = js.optJSONArray("cards");
        if (cardsArray != null) {
            for (int i = 0; i < cardsArray.length(); i++) {
                JSONObject c = cardsArray.optJSONObject(i);
                if (c == null) continue;

                Card card = new Card();
                card.label = optString(c, "label");
                card.value = optString(c, "value");
                if (!card.label.isEmpty() && !card.value.isEmpty()) {
                    res.cards.add(card);
                }
            }
        }

        JSONArray linksArray = js.optJSONArray("links");
        if (linksArray != null) {
            for (int i = 0; i < linksArray.length(); i++) {
                JSONObject l = linksArray.optJSONObject(i);
                if (l == null) continue;

                Link link = new Link();
                link.title = optString(l, "title");
                link.url = optString(l, "url");
                if (!link.title.isEmpty() && !link.url.isEmpty()) {
                    res.links.add(link);
                }
            }
        }

        return res;
    }

    /**
     * {@code optString} bawaan mengembalikan teks {@code "null"} untuk sebuah
     * {@code JSONObject.NULL}, yang kemudian lolos setiap pemeriksaan "tidak
     * kosong" dan berakhir tampil di layar.
     */
    @NonNull
    private static String optString(@NonNull JSONObject js, @NonNull String key) {
        if (js.isNull(key)) return "";
        Object value = js.opt(key);
        if (value == null) return "";
        return String.valueOf(value).trim();
    }

    @Nullable
    private static Integer optInteger(@NonNull JSONObject js, @NonNull String key) {
        if (js.isNull(key)) return null;
        Object value = js.opt(key);
        if (value == null) return null;
        if (value instanceof Number) {
            int id = ((Number) value).intValue();
            return id > 0 ? id : null;
        }
        try {
            int id = Integer.parseInt(String.valueOf(value).trim());
            return id > 0 ? id : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @NonNull
    private static String firstNonEmpty(@NonNull String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    public static class Card {
        public String label = "";
        public String value = "";
    }

    public static class Link {
        public String title = "";
        public String url = "";
    }
}
