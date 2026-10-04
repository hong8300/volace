package com.hong.volace.schedule

import com.hong.volace.data.ScheduleRule
import com.hong.volace.data.ScheduleSkip
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** One switch the schedule makes: [ruleId] puts [profileId] in effect at [at] (wall-clock ms). */
data class Boundary(val ruleId: Long, val profileId: Long, val at: Long)

/** When the rules switch, worked out from the rules and the days off alone (no Android). */
object ScheduleCalc {

    const val ALL_DAYS = 0b111_1111
    const val WEEKDAYS = 0b001_1111
    const val WEEKEND = 0b110_0000

    /** How far [next] looks ahead: a long stretch of days off can hide every boundary for months. */
    private const val LOOKAHEAD_DAYS = 400L

    /**
     * How far [latestDue] looks back when catching up (the phone was off at the time). A week
     * covers every rule; anything older has been overtaken by a later boundary anyway.
     */
    private const val CATCH_UP_DAYS = 7L

    /** Two rules at the same moment: the newer one (higher id) wins, in [next] and [latestDue] alike. */
    private val ORDER = compareBy<Boundary>({ it.at }, { it.ruleId })

    fun dayBit(day: DayOfWeek): Int = 1 shl (day.value - 1)

    fun isSkipped(date: LocalDate, skips: List<ScheduleSkip>): Boolean =
        skips.any { date.toEpochDay() in it.fromDay..it.toDay }

    private fun boundariesOn(date: LocalDate, rules: List<ScheduleRule>, skips: List<ScheduleSkip>, zone: ZoneId): List<Boundary> {
        if (isSkipped(date, skips)) return emptyList()
        val bit = dayBit(date.dayOfWeek)
        return rules.filter { it.enabled && it.days and bit != 0 }.map { rule ->
            val time = LocalTime.of(rule.minuteOfDay / 60, rule.minuteOfDay % 60)
            // A time that does not exist that day (a daylight-saving jump) moves past the gap.
            Boundary(rule.id, rule.profileId, date.atTime(time).atZone(zone).toInstant().toEpochMilli())
        }
    }

    private fun dateOf(at: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()

    /** The first boundary after [now], or null when no rule will ever fire. */
    fun next(rules: List<ScheduleRule>, skips: List<ScheduleSkip>, now: Long, zone: ZoneId = ZoneId.systemDefault()): Boundary? {
        if (rules.none { it.enabled && it.days and ALL_DAYS != 0 }) return null
        val today = dateOf(now, zone)
        for (offset in 0..LOOKAHEAD_DAYS) {
            val ahead = boundariesOn(today.plusDays(offset), rules, skips, zone).filter { it.at > now }
            // Ties at the same moment resolve the way latestDue applies them.
            val first = ahead.minOfOrNull { it.at } ?: continue
            return ahead.filter { it.at == first }.maxWith(ORDER)
        }
        return null
    }

    /**
     * The latest boundary after [after] and up to [now]: the one to apply when the alarm goes off,
     * or after the phone was off through one. Earlier ones in the same span are overtaken by it.
     */
    fun latestDue(
        rules: List<ScheduleRule>,
        skips: List<ScheduleSkip>,
        after: Long,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Boundary? {
        if (now <= after) return null
        val today = dateOf(now, zone)
        for (offset in 0..CATCH_UP_DAYS) {
            val due = boundariesOn(today.minusDays(offset), rules, skips, zone).filter { it.at > after && it.at <= now }
            if (due.isNotEmpty()) return due.maxWith(ORDER)
        }
        return null
    }
}
