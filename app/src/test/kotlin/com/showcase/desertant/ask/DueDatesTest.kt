package com.showcase.desertant.ask

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DueDatesTest {
    private val friday = LocalDate.of(2026, 10, 30)
    private fun d(s: String) = DueDates.resolve(s, friday)?.toString()

    @Test fun weekdaysCountForward() {
        assertEquals("2026-11-02", d("send it Monday"))
        assertEquals("2026-11-03", d("next Tuesday"))
        assertEquals("2026-11-06", d("by Friday"))
        assertEquals("2026-11-03", d("अगले मंगलवार"))
        assertEquals("2026-11-06", d("शुक्रवार तक"))
    }

    @Test fun relativeDays() {
        assertEquals("2026-10-30", d("today"))
        assertEquals("2026-10-31", d("tomorrow"))
        assertEquals("2026-11-01", d("परसों"))
        assertEquals("2026-11-06", d("next week"))
        assertEquals("2026-10-31", d("end of the month"))
    }

    @Test fun daysOfTheMonth() {
        assertEquals("2026-11-10", d("by the tenth"))
        assertEquals("2026-11-13", d("on the 13th"))
        assertEquals("2026-11-20", d("20 November"))
        assertEquals("2026-11-18", d("by the eighteenth"))
        assertEquals("2027-01-05", d("January 5"))
        assertEquals("2026-10-31", d("31st"))
    }

    @Test fun unplaceable() {
        assertNull(d("soon"))
        assertNull(d(""))
        assertNull("a date already past isn't a due date", d("2026-10-01"))
    }
}
