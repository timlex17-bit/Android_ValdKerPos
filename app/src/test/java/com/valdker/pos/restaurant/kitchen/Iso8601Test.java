package com.valdker.pos.restaurant.kitchen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.TimeZone;

public class Iso8601Test {

    /** 2026-09-12T14:20:34.939Z dalam milidetik epoch. */
    private static final long BASE = 1789222834939L;

    @Test
    public void sixDigitFractionIsTruncatedNotAddedAsMillis() {
        // Inti seluruh kelas ini: 939538 mikrodetik adalah 939 ms, bukan
        // 939.538 ms. Kalau salah, hasilnya meleset lebih dari 15 menit.
        Long parsed = Iso8601.parseToEpochMillis("2026-09-12T14:20:34.939538Z");
        assertEquals(Long.valueOf(BASE), parsed);

        Long threeDigits = Iso8601.parseToEpochMillis("2026-09-12T14:20:34.939Z");
        assertEquals(threeDigits, parsed);
    }

    @Test
    public void offsetFormsAreUnderstood() {
        // Semua menunjuk instan yang sama.
        Long utc = Iso8601.parseToEpochMillis("2026-09-12T14:20:34.939Z");
        Long plus9 = Iso8601.parseToEpochMillis("2026-09-12T23:20:34.939+09:00");
        Long minus5 = Iso8601.parseToEpochMillis("2026-09-12T09:20:34.939-05:00");
        assertEquals(utc, plus9);
        assertEquals(utc, minus5);
    }

    @Test
    public void missingFractionIsFine() {
        assertEquals(Long.valueOf(BASE - 939L),
                Iso8601.parseToEpochMillis("2026-09-12T14:20:34Z"));
    }

    @Test
    public void parsingDoesNotDependOnDeviceTimeZone() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Dili"));
            Long dili = Iso8601.parseToEpochMillis("2026-09-12T14:20:34.939538Z");
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            Long newYork = Iso8601.parseToEpochMillis("2026-09-12T14:20:34.939538Z");
            assertEquals(dili, newYork);
            assertEquals(Long.valueOf(BASE), dili);
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    public void garbageReturnsNullInsteadOfGuessing() {
        assertNull(Iso8601.parseToEpochMillis(null));
        assertNull(Iso8601.parseToEpochMillis(""));
        assertNull(Iso8601.parseToEpochMillis("kemarin"));
        assertNull(Iso8601.parseToEpochMillis("2026-09-12"));
        assertNull(Iso8601.parseToEpochMillis("2026-13-45T99:99:99Z"));
    }

    // ------------------------------------------------------------ durasi

    @Test
    public void shortAgoReadsLikeAKitchenBoard() {
        assertEquals("baru", Iso8601.shortAgo("2026-09-12T14:20:34.939538Z", BASE));
        assertEquals("baru", Iso8601.shortAgo("2026-09-12T14:20:34.939538Z", BASE + 59_000L));
        assertEquals("1m", Iso8601.shortAgo("2026-09-12T14:20:34.939538Z", BASE + 60_000L));
        assertEquals("4m", Iso8601.shortAgo("2026-09-12T14:20:34.939538Z", BASE + 4 * 60_000L));
        assertEquals("59m", Iso8601.shortAgo("2026-09-12T14:20:34.939538Z", BASE + 59 * 60_000L));
        assertEquals("1j", Iso8601.shortAgo("2026-09-12T14:20:34.939538Z", BASE + 60 * 60_000L));
        assertEquals("1j 12m", Iso8601.shortAgo("2026-09-12T14:20:34.939538Z", BASE + 72 * 60_000L));
    }

    @Test
    public void clockSkewNeverShowsNegativeDuration() {
        // Jam tablet dapur di belakang jam server: server mengirim timestamp
        // yang "di masa depan" menurut perangkat.
        assertEquals("baru", Iso8601.shortAgo("2026-09-12T14:20:34.939538Z", BASE - 120_000L));
        assertEquals(0, Iso8601.minutesSince("2026-09-12T14:20:34.939538Z", BASE - 120_000L));
    }

    @Test
    public void unreadableTimestampHasNoDuration() {
        assertNull(Iso8601.shortAgo("entah kapan", BASE));
        assertEquals(-1, Iso8601.minutesSince(null, BASE));
    }

    @Test
    public void minutesSinceCountsWholeMinutes() {
        assertEquals(0, Iso8601.minutesSince("2026-09-12T14:20:34.939538Z", BASE + 59_999L));
        assertEquals(1, Iso8601.minutesSince("2026-09-12T14:20:34.939538Z", BASE + 60_000L));
        assertEquals(12, Iso8601.minutesSince("2026-09-12T14:20:34.939538Z", BASE + 12 * 60_000L));
    }
}
