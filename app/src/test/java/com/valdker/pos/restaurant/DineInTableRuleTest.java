package com.valdker.pos.restaurant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.valdker.pos.restaurant.DineInTableRule.Decision;

import org.junit.Test;

/**
 * Matriks keputusan meja dine-in.
 *
 * <p>Inti yang dijaga tes ini: kolom nomor meja teks bebas dan grid meja tidak
 * pernah aktif bersamaan. Itu kondisi yang dulu membuat kasir memilih meja A1
 * lalu tetap diwajibkan mengetik nomor meja yang dijamin ditimpa server.
 */
public class DineInTableRuleTest {

    private static final long TABLE_A1 = 1L;

    // ------------------------------------------------ tidak ada item dine-in

    @Test
    public void nonDineInCartNeverAsksAboutTables() {
        // Keranjang bungkus semua: tidak ada pertanyaan meja, apa pun setelannya.
        assertEquals(Decision.NOT_APPLICABLE,
                DineInTableRule.decide(false, false, true, 8, null, true));
        assertEquals(Decision.NOT_APPLICABLE,
                DineInTableRule.decide(false, false, false, 0, null, true));
        // Bahkan kalau sebuah meja sempat terpilih saat bill masih dine-in.
        assertEquals(Decision.NOT_APPLICABLE,
                DineInTableRule.decide(false, false, true, 8, TABLE_A1, true));
    }

    // ------------------------------------------------------ grid bisa dipakai

    @Test
    public void pickerUsableButNothingPickedBlocksCheckout() {
        Decision d = DineInTableRule.decide(true, false, true, 8, null, true);
        assertEquals(Decision.BLOCK_UNTIL_PICKED, d);
        assertTrue(DineInTableRule.blocksCheckout(d));
        assertFalse(DineInTableRule.needsFreeTextField(d));
    }

    @Test
    public void pickedTableSilencesTheFreeTextFieldEvenWhenEnabled() {
        // Justru ini kombinasi yang dulu bermasalah: meja sudah dipilih, tapi
        // enable_table_number menyala sehingga dialog tetap meminta ketikan.
        Decision d = DineInTableRule.decide(true, false, true, 8, TABLE_A1, true);
        assertEquals(Decision.USE_PICKED_TABLE, d);
        assertFalse(DineInTableRule.needsFreeTextField(d));
        assertFalse(DineInTableRule.blocksCheckout(d));
    }

    @Test
    public void pickedTableIsHonouredEvenIfTheCountLookedEmpty() {
        // Jumlah meja gagal dimuat (-1) tapi kasir sudah sempat memilih.
        assertEquals(Decision.USE_PICKED_TABLE,
                DineInTableRule.decide(true, false, true, -1, TABLE_A1, true));
    }

    @Test
    public void zeroAsPickedIdIsNotAPick() {
        assertEquals(Decision.BLOCK_UNTIL_PICKED,
                DineInTableRule.decide(true, false, true, 8, 0L, true));
    }

    // ------------------------------------------------ grid tidak bisa dipakai

    @Test
    public void moduleOnButNoTablesCreatedFallsBackToFreeText() {
        // Lubang yang paling mudah terjadi: modul menyala, tapi toko belum
        // membuat satu meja pun. Menyembunyikan kolom teks di sini akan
        // membuat order dine-in tidak punya tempat mencatat meja sama sekali.
        Decision d = DineInTableRule.decide(true, false, true, 0, null, true);
        assertEquals(Decision.ASK_FREE_TEXT, d);
        assertTrue(DineInTableRule.needsFreeTextField(d));
        assertFalse(DineInTableRule.blocksCheckout(d));
    }

    @Test
    public void unknownTableCountNeverBlocksTheSale() {
        // Permintaan daftar meja belum selesai atau gagal. Menahan checkout
        // berdasarkan tebakan bisa mengunci kasir sepenuhnya.
        Decision d = DineInTableRule.decide(true, false, true, -1, null, true);
        assertEquals(Decision.ASK_FREE_TEXT, d);
        assertFalse(DineInTableRule.blocksCheckout(d));
    }

    @Test
    public void moduleOffUsesFreeTextField() {
        assertEquals(Decision.ASK_FREE_TEXT,
                DineInTableRule.decide(true, false, false, 0, null, true));
    }

    // --------------------------------------------------- toko tanpa pencatatan

    @Test
    public void bothMechanismsOffIsTheShopSettingNotAnError() {
        // Warung yang memang tidak menomori mejanya. Tidak diblokir, tidak
        // dipaksa mengisi apa pun.
        Decision d = DineInTableRule.decide(true, false, false, 0, null, false);
        assertEquals(Decision.NO_TABLE_RECORDED, d);
        assertFalse(DineInTableRule.blocksCheckout(d));
        assertFalse(DineInTableRule.needsFreeTextField(d));
    }

    @Test
    public void tableNumberFieldOffButPickerUsableStillBlocks() {
        // enable_table_number mati bukan berarti meja tidak dicatat - grid
        // meja masih mekanisme yang sah dan lebih baik.
        assertEquals(Decision.BLOCK_UNTIL_PICKED,
                DineInTableRule.decide(true, false, true, 8, null, false));
    }
    // ------------------------------------------------- keranjang campur

    @Test
    public void tableWithNonDineInItemIsRefused() {
        // Satu item bungkus di bon bermeja sudah cukup: server menyimpan order
        // sebagai GENERAL dan mejanya tidak akan pernah tampil terisi.
        Decision d = DineInTableRule.decide(true, true, true, 8, TABLE_A1, true);
        assertEquals(Decision.MIXED_WITH_TABLE, d);
        assertTrue(DineInTableRule.blocksCheckout(d));
        assertFalse(DineInTableRule.needsFreeTextField(d));
    }

    @Test
    public void mixingIsFineAsLongAsNoTableIsHeld() {
        // Mencampur tipe tidak dilarang secara umum - yang dilarang hanya
        // mencampur pada bon yang memegang meja.
        assertEquals(Decision.ASK_FREE_TEXT,
                DineInTableRule.decide(true, true, false, 0, null, true));
        assertEquals(Decision.NO_TABLE_RECORDED,
                DineInTableRule.decide(true, true, false, 0, null, false));
        assertEquals(Decision.NOT_APPLICABLE,
                DineInTableRule.decide(false, true, true, 8, null, true));
    }

    @Test
    public void mixedCartStillBlocksWhenTableNotYetPicked() {
        // Urutan penjagaannya tetap: pilih meja dulu, baru campurnya ketahuan.
        assertEquals(Decision.BLOCK_UNTIL_PICKED,
                DineInTableRule.decide(true, true, true, 8, null, true));
    }

    @Test
    public void pureDineInWithTableIsTheHappyPath() {
        assertEquals(Decision.USE_PICKED_TABLE,
                DineInTableRule.decide(true, false, true, 8, TABLE_A1, true));
    }

    // ------------------------------------------------------------- invarian

    @Test
    public void freeTextAndPickerAreNeverBothActive() {
        // Sapu seluruh matriks: tidak ada satu pun kombinasi yang sekaligus
        // meminta ketikan nomor meja DAN mengandalkan grid meja.
        int[] counts = {-1, 0, 1, 8};
        Long[] picks = {null, 0L, TABLE_A1};
        for (boolean dineIn : new boolean[]{true, false}) {
            for (boolean mixed : new boolean[]{true, false}) {
                for (boolean moduleOn : new boolean[]{true, false}) {
                    for (int count : counts) {
                        for (Long pick : picks) {
                            for (boolean fieldOn : new boolean[]{true, false}) {
                                Decision d = DineInTableRule.decide(
                                        dineIn, mixed, moduleOn, count, pick, fieldOn);
                                String where = "dineIn=" + dineIn + " mixed=" + mixed
                                        + " moduleOn=" + moduleOn + " count=" + count
                                        + " pick=" + pick + " fieldOn=" + fieldOn
                                        + " -> " + d;

                                boolean asksText = DineInTableRule.needsFreeTextField(d);
                                boolean usesPicker = d == Decision.USE_PICKED_TABLE
                                        || d == Decision.BLOCK_UNTIL_PICKED;
                                assertFalse(where, asksText && usesPicker);

                                // Invarian kedua: sebuah meja tidak pernah
                                // diteruskan bersama keranjang campur.
                                assertFalse(where,
                                        d == Decision.USE_PICKED_TABLE && mixed && dineIn);
                            }
                        }
                    }
                }
            }
        }
    }
}
