package com.valdker.pos.print;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Perataan kolom struk. Bisa diuji tanpa printer, dan memang harus: sebelum
 * ada kelas ini setiap angka pada struk hanya dipisahkan satu spasi dari
 * labelnya, dan satu-satunya cara mengetahuinya adalah membakar kertas.
 */
public class ReceiptLayoutTest {

    private static final int CHARS_58MM = 32;
    private static final int CHARS_80MM = 48;

    @Test
    public void rightColumnEndsAtPaperEdgeOn58mm() {
        ReceiptLayout.Line line = ReceiptLayout.layout("[L]Subtotal[R]$10.00", CHARS_58MM);

        assertEquals(CHARS_58MM, line.text.length());
        assertTrue(line.text.startsWith("Subtotal"));
        assertTrue(line.text.endsWith("$10.00"));
        // Perataannya dihitung sendiri, jadi printer harus diminta rata kiri.
        assertEquals(ReceiptLayout.ALIGN_LEFT, line.align);
    }

    @Test
    public void rightColumnEndsAtPaperEdgeOn80mm() {
        ReceiptLayout.Line line = ReceiptLayout.layout("[L]Subtotal[R]$10.00", CHARS_80MM);

        assertEquals(CHARS_80MM, line.text.length());
        assertTrue(line.text.endsWith("$10.00"));
    }

    @Test
    public void columnsOfDifferentLabelsLineUpWithEachOther() {
        String subtotal = ReceiptLayout.layout("[L]Subtotal[R]$9.00", CHARS_58MM).text;
        String tax = ReceiptLayout.layout("[L]VAT / Tax[R]$1.00", CHARS_58MM).text;
        String total = ReceiptLayout.layout("[L]<b>Total</b>[R]<b>$10.00</b>", CHARS_58MM).text;

        assertEquals(CHARS_58MM, subtotal.length());
        assertEquals(CHARS_58MM, tax.length());
        assertEquals(CHARS_58MM, total.length());

        // Ini inti masalahnya: ketiga angka harus berhenti di kolom yang sama.
        assertEquals(subtotal.length(), tax.length());
        assertEquals(tax.length(), total.length());
    }

    @Test
    public void boldSurvivesTheSplit() {
        ReceiptLayout.Line line = ReceiptLayout.layout("[L]<b>Total</b>[R]<b>$10.00</b>", CHARS_58MM);

        assertTrue(line.bold);
        assertTrue(line.text.startsWith("Total"));
        assertTrue(line.text.endsWith("$10.00"));
        assertTrue("tag tebal tidak boleh ikut tercetak", !line.text.contains("<b>"));
    }

    @Test
    public void longProductNameIsTruncatedButTheAmountIsNot() {
        String longName = "Nasi Goreng Spesial Pedas Telur Dadar Ekstra";
        ReceiptLayout.Line line =
                ReceiptLayout.layout("[L]<b>" + longName + "</b>[R]<b>$12.50</b>", CHARS_58MM);

        assertEquals(CHARS_58MM, line.text.length());
        assertTrue("angka tidak boleh terpotong", line.text.endsWith("$12.50"));
        assertTrue("nama harus terpotong, bukan angkanya", !line.text.contains(longName));
        assertTrue(line.text.startsWith("Nasi Goreng"));
        // Minimal satu spasi supaya nama dan angka tidak menempel jadi satu kata.
        assertTrue(line.text.charAt(CHARS_58MM - "$12.50".length() - 1) == ' ');
    }

    @Test
    public void amountWiderThanThePaperIsNeverCut() {
        String hugeAmount = "$123456789012345678901234567890123456.00";
        ReceiptLayout.Line line = ReceiptLayout.layout("[L]Total[R]" + hugeAmount, CHARS_58MM);

        assertEquals(hugeAmount, line.text);
    }

    @Test
    public void separatorFillsThePaperWidth() {
        assertEquals(32, ReceiptLayout.layout("[C]" + ReceiptLayout.SEPARATOR, CHARS_58MM).text.length());
        assertEquals(48, ReceiptLayout.layout("[C]" + ReceiptLayout.SEPARATOR, CHARS_80MM).text.length());

        String line58 = ReceiptLayout.layout("[C]" + ReceiptLayout.SEPARATOR, CHARS_58MM).text;
        assertEquals("--------------------------------", line58);
    }

    @Test
    public void separatorMatchesThePrinterProfile() {
        // Lebarnya harus datang dari profil kertas, bukan dari 32 setrip mati.
        int chars58 = PrinterService.profileForMm(58).chars;
        int chars80 = PrinterService.profileForMm(80).chars;

        assertEquals(chars58, ReceiptLayout.separator(chars58).length());
        assertEquals(chars80, ReceiptLayout.separator(chars80).length());
        assertEquals(32, chars58);
        assertEquals(48, chars80);
    }

    @Test
    public void centeredAndPlainLinesKeepTheirAlignment() {
        ReceiptLayout.Line centered = ReceiptLayout.layout("[C]Thank you", CHARS_58MM);
        assertEquals(ReceiptLayout.ALIGN_CENTER, centered.align);
        assertEquals("Thank you", centered.text);

        ReceiptLayout.Line plain = ReceiptLayout.layout("[L]2 x $1.50", CHARS_58MM);
        assertEquals(ReceiptLayout.ALIGN_LEFT, plain.align);
        assertEquals("2 x $1.50", plain.text);
    }

    @Test
    public void accentsAreStrippedBeforeTheColumnIsMeasured() {
        // Panjang setelah normalisasi yang menentukan kolom; kalau diukur
        // sebelum normalisasi, baris ber-aksen akan meleset satu kolom.
        // Huruf beraksennya dibangun dari kode karakter, bukan ditulis
        // langsung: berkas sumber dibaca javac dengan encoding platform.
        String sugar = "Acu" + (char) 0x00e7 + "ar";
        ReceiptLayout.Line line = ReceiptLayout.layout("[L]" + sugar + "[R]$2.00", CHARS_58MM);

        assertEquals(CHARS_58MM, line.text.length());
        assertTrue(line.text.startsWith("Acucar"));
        assertTrue(line.text.endsWith("$2.00"));
    }

    @Test
    public void lineWithoutRightColumnIsLeftUntouched() {
        ReceiptLayout.Line line = ReceiptLayout.layout("[L]Table: 12", CHARS_58MM);
        assertEquals("Table: 12", line.text);
    }
}
