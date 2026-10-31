package com.showcase.desertant.ask

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.temporal.TemporalAdjusters

/**
 * Turns the words people use for "when" ("by Friday", "next Tuesday", "the
 * tenth", "20 November", "अगले मंगलवार") into a date, counted from the day of
 * the talk. Small models get this arithmetic wrong, so Burrow does it itself;
 * anything it can't place stays as the words.
 */
object DueDates {
    private val WEEKDAYS = mapOf(
        "monday" to DayOfWeek.MONDAY, "tuesday" to DayOfWeek.TUESDAY, "wednesday" to DayOfWeek.WEDNESDAY, "thursday" to DayOfWeek.THURSDAY,
        "friday" to DayOfWeek.FRIDAY, "saturday" to DayOfWeek.SATURDAY, "sunday" to DayOfWeek.SUNDAY,
        "mon" to DayOfWeek.MONDAY, "tue" to DayOfWeek.TUESDAY, "wed" to DayOfWeek.WEDNESDAY, "thu" to DayOfWeek.THURSDAY, "fri" to DayOfWeek.FRIDAY,
        "सोमवार" to DayOfWeek.MONDAY, "मंगलवार" to DayOfWeek.TUESDAY, "बुधवार" to DayOfWeek.WEDNESDAY, "गुरुवार" to DayOfWeek.THURSDAY,
        "बृहस्पतिवार" to DayOfWeek.THURSDAY, "शुक्रवार" to DayOfWeek.FRIDAY, "शनिवार" to DayOfWeek.SATURDAY, "रविवार" to DayOfWeek.SUNDAY,
    )

    private val MONTHS = Month.entries.associateBy { it.name.lowercase() } + Month.entries.associateBy { it.name.lowercase().take(3) }

    private val ORDINALS = mapOf(
        "first" to 1, "second" to 2, "third" to 3, "fourth" to 4, "fifth" to 5, "sixth" to 6, "seventh" to 7, "eighth" to 8, "ninth" to 9,
        "tenth" to 10, "eleventh" to 11, "twelfth" to 12, "thirteenth" to 13, "fourteenth" to 14, "fifteenth" to 15, "sixteenth" to 16,
        "seventeenth" to 17, "eighteenth" to 18, "nineteenth" to 19, "twentieth" to 20, "thirtieth" to 30,
    )

    fun resolve(words: String?, talk: LocalDate): LocalDate? {
        val w = words?.lowercase()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        runCatching { LocalDate.parse(w.take(10)) }.getOrNull()?.let { return it.takeIf { d -> !d.isBefore(talk) } }
        val tokens = w.split(Regex("[^\\p{L}\\p{M}\\p{N}]+")).filter { it.isNotEmpty() }
        when {
            "परसों" in tokens || "day after tomorrow" in w -> return talk.plusDays(2)
            "tomorrow" in tokens || "कल" in tokens -> return talk.plusDays(1)
            "today" in tokens || "tonight" in tokens || "आज" in tokens -> return talk
            "end" in tokens && "month" in tokens -> return talk.with(TemporalAdjusters.lastDayOfMonth())
            ("next" in tokens || "अगले" in tokens || "अगला" in tokens) && ("week" in tokens || "हफ्ते" in tokens || "सप्ताह" in tokens) -> return talk.plusWeeks(1)
            "end" in tokens && "week" in tokens -> return talk.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))
        }
        tokens.firstNotNullOfOrNull { WEEKDAYS[it] }?.let { day -> return talk.with(TemporalAdjusters.next(day)) }
        // A day of the month, maybe with a month: "the tenth", "13th", "20 November", "November 20".
        val day = tokens.firstNotNullOfOrNull { t -> ORDINALS[t] ?: t.removeSuffix("st").removeSuffix("nd").removeSuffix("rd").removeSuffix("th").toIntOrNull()?.takeIf { it in 1..31 } }
            ?: tokens.windowed(2).firstNotNullOfOrNull { (a, b) -> if (a == "twenty" || a == "thirty") (if (a == "twenty") 20 else 30) + (ORDINALS[b] ?: 0) else null }
            ?: return null
        val month = tokens.firstNotNullOfOrNull { MONTHS[it] }
        return runCatching {
            if (month != null) {
                val d = LocalDate.of(talk.year, month, day)
                if (d.isBefore(talk)) d.plusYears(1) else d
            } else {
                val d = talk.withDayOfMonth(day)
                if (d.isBefore(talk)) d.plusMonths(1).withDayOfMonth(day) else d
            }
        }.getOrNull()
    }
}
