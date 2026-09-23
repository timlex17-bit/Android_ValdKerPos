package com.valdker.pos.ui.ownerchat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Menerjemahkan kegagalan {@code POST /api/ai/assistant/chat/} menjadi satu
 * dari sedikit keadaan yang perlu dibedakan pengguna.
 *
 * <p>Kontraknya ({@code docs/api/AI_ASSISTANT_API.md}) memberi tiap galat
 * sebuah {@code error.code} yang stabil dan meminta klien bercabang pada kode
 * itu, bukan pada teks pesannya - teks itu berbahasa Inggris, ditulis untuk
 * pengembang, dan bisa berubah kapan saja. Status HTTP saja juga tidak cukup:
 * 429 punya dua arti yang berbeda ({@code rate_limited} berarti kita terlalu
 * sering memanggil, {@code ai_rate_limited} berarti penyedia AI-nya yang
 * sibuk), jadi kode diperiksa lebih dulu dan status menjadi cadangan.
 *
 * <p>Yang tidak boleh terjadi di sini: meneruskan {@code error.detail} ke
 * layar. Kontraknya sendiri mencatat sebuah insiden ketika teks galat penyedia
 * memantulkan kembali potongan kunci API yang ditolak; karena itu setiap
 * keadaan di bawah dipetakan ke string aplikasi sendiri.
 */
public enum AssistantError {

    /** 401 - token hilang atau tidak berlaku lagi. */
    UNAUTHENTICATED,

    /** 403 - peran tidak diizinkan, modul laporan mati, atau toko bukan miliknya. */
    FORBIDDEN,

    /** 404 - conversation_id tidak dikenal; percakapan harus dimulai ulang. */
    CONVERSATION_GONE,

    /** 400 - permintaan ditolak server (pesan kosong, terlalu panjang, bahasa asing). */
    INVALID_REQUEST,

    /** 429 - baik kuota pemanggil maupun penyedia AI yang sibuk. */
    RATE_LIMITED,

    /** 5xx, termasuk 502/503 ai_provider_unavailable. */
    SERVER,

    /** Badan respons tidak bisa dibaca sebagai JSON, atau tanpa pesan sama sekali. */
    UNREADABLE,

    /** Tidak ada jawaban sama sekali: waktu habis, host tidak terpecahkan. */
    NETWORK,

    /** Status lain yang tidak masuk kategori mana pun. */
    UNKNOWN;

    /**
     * @param statusCode status HTTP respons
     * @param errorCode  isi {@code error.code} bila ada; boleh null
     */
    @NonNull
    public static AssistantError classify(int statusCode, @Nullable String errorCode) {
        String code = errorCode == null ? "" : errorCode.trim();

        // Kode dulu: ia lebih spesifik daripada statusnya, dan pada 429 ia
        // satu-satunya cara membedakan kedua artinya.
        switch (code) {
            case "not_authenticated":
                return UNAUTHENTICATED;
            case "permission_denied":
                return FORBIDDEN;
            case "not_found":
                return CONVERSATION_GONE;
            case "validation_error":
                return INVALID_REQUEST;
            case "rate_limited":
            case "ai_rate_limited":
                return RATE_LIMITED;
            case "ai_provider_unavailable":
            case "internal_error":
                return SERVER;
            default:
                break;
        }

        if (statusCode == 401) return UNAUTHENTICATED;
        if (statusCode == 403) return FORBIDDEN;
        if (statusCode == 404) return CONVERSATION_GONE;
        if (statusCode == 429) return RATE_LIMITED;
        if (statusCode == 400 || statusCode == 422) return INVALID_REQUEST;
        if (statusCode >= 500) return SERVER;
        if (statusCode >= 400) return UNKNOWN;

        return UNKNOWN;
    }

    /**
     * Apakah percakapan yang tersimpan harus dilupakan setelah galat ini.
     *
     * <p>Hanya untuk {@link #CONVERSATION_GONE}: tanpa ini, sebuah id yang
     * sudah tidak ada di server akan ikut terkirim pada setiap pesan
     * berikutnya, dan percakapan itu tidak akan pernah pulih sampai layarnya
     * ditutup.
     */
    public boolean shouldForgetConversation() {
        return this == CONVERSATION_GONE;
    }
}
