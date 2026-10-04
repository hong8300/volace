package com.hong.volace.schedule

import com.hong.volace.data.ScheduleRule
import com.hong.volace.data.ScheduleSkip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ScheduleCalcTest {

    private val zone = ZoneId.of("Asia/Tokyo")

    private fun at(text: String): Long = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    private fun rule(id: Long, time: String, days: Int, profileId: Long = id * 10, enabled: Boolean = true): ScheduleRule {
        val (h, m) = time.split(":").map(String::toInt)
        return ScheduleRule(id = id, minuteOfDay = h * 60 + m, days = days, profileId = profileId, enabled = enabled)
    }

    private fun skip(from: String, to: String = from) =
        ScheduleSkip(fromDay = LocalDate.parse(from).toEpochDay(), toDay = LocalDate.parse(to).toEpochDay())

    // 2026-10-02 is a Friday.
    private val night = rule(1, "22:00", ScheduleCalc.WEEKDAYS)
    private val morning = rule(2, "07:00", ScheduleCalc.WEEKDAYS)

    @Test
    fun next_laterToday() {
        val next = ScheduleCalc.next(listOf(night, morning), emptyList(), at("2026-10-02T12:00"), zone)
        assertEquals(Boundary(1, 10, at("2026-10-02T22:00")), next)
    }

    @Test
    fun next_skipsTheWeekendForWeekdayRules() {
        val next = ScheduleCalc.next(listOf(night, morning), emptyList(), at("2026-10-02T23:00"), zone)
        assertEquals(at("2026-10-05T07:00"), next?.at)
    }

    @Test
    fun next_notAtTheVeryMomentItFired() {
        val next = ScheduleCalc.next(listOf(night), emptyList(), at("2026-10-02T22:00"), zone)
        assertEquals(at("2026-10-05T22:00"), next?.at)
    }

    @Test
    fun next_passesOverDaysOff() {
        val next = ScheduleCalc.next(listOf(night, morning), listOf(skip("2026-10-05", "2026-10-06")), at("2026-10-02T23:00"), zone)
        assertEquals(at("2026-10-07T07:00"), next?.at)
    }

    @Test
    fun next_nullWhenNothingIsOn() {
        assertNull(ScheduleCalc.next(listOf(night.copy(enabled = false)), emptyList(), at("2026-10-02T12:00"), zone))
        assertNull(ScheduleCalc.next(listOf(night.copy(days = 0)), emptyList(), at("2026-10-02T12:00"), zone))
        assertNull(ScheduleCalc.next(emptyList(), emptyList(), at("2026-10-02T12:00"), zone))
    }

    @Test
    fun sameMoment_theNewerRuleWinsInBoth() {
        val older = rule(3, "22:00", ScheduleCalc.ALL_DAYS, profileId = 30)
        val newer = rule(4, "22:00", ScheduleCalc.ALL_DAYS, profileId = 40)
        val rules = listOf(newer, older)
        assertEquals(40L, ScheduleCalc.next(rules, emptyList(), at("2026-10-02T12:00"), zone)?.profileId)
        assertEquals(40L, ScheduleCalc.latestDue(rules, emptyList(), at("2026-10-02T21:00"), at("2026-10-02T22:00:01"), zone)?.profileId)
    }

    @Test
    fun latestDue_theAlarmThatJustWentOff() {
        val due = ScheduleCalc.latestDue(listOf(night, morning), emptyList(), at("2026-10-02T21:00"), at("2026-10-02T22:00:00.050"), zone)
        assertEquals(Boundary(1, 10, at("2026-10-02T22:00")), due)
    }

    @Test
    fun latestDue_nothingTwice() {
        assertNull(
            ScheduleCalc.latestDue(listOf(night, morning), emptyList(), at("2026-10-02T22:00:00.050"), at("2026-10-02T22:30"), zone),
        )
    }

    @Test
    fun latestDue_afterThePhoneWasOffOnlyTheLatestCounts() {
        val every = listOf(rule(5, "22:00", ScheduleCalc.ALL_DAYS, 50), rule(6, "07:00", ScheduleCalc.ALL_DAYS, 60))
        val due = ScheduleCalc.latestDue(every, emptyList(), at("2026-10-02T21:00"), at("2026-10-03T08:00"), zone)
        assertEquals(Boundary(6, 60, at("2026-10-03T07:00")), due)
    }

    @Test
    fun latestDue_notOnADayOff() {
        assertNull(
            ScheduleCalc.latestDue(listOf(night), listOf(skip("2026-10-02")), at("2026-10-02T21:00"), at("2026-10-02T22:01"), zone),
        )
    }

    @Test
    fun latestDue_nullWhenTheClockWentBack() {
        assertNull(ScheduleCalc.latestDue(listOf(night), emptyList(), at("2026-10-02T23:00"), at("2026-10-02T22:30"), zone))
    }

    @Test
    fun daylightSaving_aTimeInTheGapMovesPastIt() {
        // 2026-03-08 02:30 does not exist in New York; it becomes 03:30.
        val ny = ZoneId.of("America/New_York")
        val early = rule(7, "02:30", ScheduleCalc.ALL_DAYS)
        val next = ScheduleCalc.next(listOf(early), emptyList(), LocalDateTime.parse("2026-03-08T00:00").atZone(ny).toInstant().toEpochMilli(), ny)
        assertEquals(LocalDateTime.parse("2026-03-08T03:30").atZone(ny).toInstant().toEpochMilli(), next?.at)
    }
}
