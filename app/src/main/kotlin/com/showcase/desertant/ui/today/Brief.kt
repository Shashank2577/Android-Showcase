package com.showcase.desertant.ui.today

import com.showcase.desertant.ask.TalkSummaries
import com.showcase.desertant.data.MeetingEntity
import com.showcase.desertant.data.SummaryTodo
import com.showcase.desertant.people.Contacts
import com.showcase.desertant.ui.meetings.dueDate
import java.time.LocalDate
import java.time.ZoneId

/** A to-do from some talk's summary, ready for the brief. */
data class BriefTodo(val talk: MeetingEntity, val index: Int, val todo: SummaryTodo, val due: LocalDate?)

/** What the brief shows; worked out from stored summaries, no model needed. */
data class BriefData(val recent: List<MeetingEntity>, val dueNow: List<BriefTodo>, val thisWeek: List<BriefTodo>) {
    val isEmpty get() = recent.isEmpty() && dueNow.isEmpty() && thisWeek.isEmpty()

    companion object {
        fun build(talks: List<MeetingEntity>, done: Set<String>, me: String?, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): BriefData {
            val since = today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val recent = talks.filter { it.createdAt >= since }.sortedByDescending { it.createdAt }.take(4)
            val myName = me?.let(Contacts::plain)?.substringBefore(' ')
            val open = talks.flatMap { m ->
                TalkSummaries.decode(m.summaryJson)?.todos.orEmpty().mapIndexedNotNull { i, t ->
                    if ("${m.id}/$i" in done) return@mapIndexedNotNull null
                    val mine = myName != null && t.who?.let(Contacts::plain)?.substringBefore(' ') == myName
                    BriefTodo(m, i, if (mine) t.copy(who = "you") else t, dueDate(t))
                }
            }
            val dueNow = open.filter { it.due != null && !it.due.isAfter(today.plusDays(1)) && !it.due.isBefore(today.minusDays(14)) }.sortedBy { it.due }
            val week = open.filter { it.due != null && it.due.isAfter(today.plusDays(1)) && !it.due.isAfter(today.plusDays(7)) }.sortedBy { it.due }
            return BriefData(recent, dueNow, week)
        }
    }
}

/** The first sentence, for a one-line recap (works for "." and "।"). */
fun firstSentence(text: String): String {
    val end = Regex("[.!?।](\\s|$)").find(text)?.range?.first ?: return text
    return text.substring(0, end + 1)
}
