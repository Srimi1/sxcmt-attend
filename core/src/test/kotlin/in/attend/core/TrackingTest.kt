package `in`.attend.core

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

class TrackingTest : FunSpec({
    val friday = LocalDate.parse("2026-10-23")
    val slots = SeedTimetable.sessions.filter { it.day == DayOfWeek.FRIDAY }
    fun summarize(entries: List<AttendanceEntry>, base: Baseline? = null, rules: List<CalendarException> = emptyList()) =
        AttendanceTracker.summarize(base, entries, SeedTimetable.sessions, rules, friday, friday)

    test("present and absent periods change student counts without marking unknown periods present") {
        val summary = summarize(listOf(AttendanceEntry(friday, slots[0].id, AttendanceMark.PRESENT), AttendanceEntry(friday, slots[1].id, AttendanceMark.ABSENT)))
        summary.baseline!!.counts shouldBe Counts(1, 2)
        summary.unmarked shouldBe 3
    }
    test("editing one period replaces it and repeated marks cannot double count") {
        val entry = AttendanceEntry(friday, slots[0].id, AttendanceMark.PRESENT)
        summarize(listOf(entry, entry.copy(mark = AttendanceMark.ABSENT))).baseline!!.counts shouldBe Counts(0, 1)
        summarize(emptyList()).baseline shouldBe null
    }
    test("newer sheet totals cover earlier student marks without duplication") {
        val entry = AttendanceEntry(friday, slots[0].id, AttendanceMark.ABSENT)
        val base = Baseline(Counts(80, 100), emptyList(), friday)
        summarize(listOf(entry), base).baseline shouldBe base
    }
    test("student periods after the sheet date add to baseline exactly once") {
        val base = Baseline(Counts(80, 100), emptyList(), friday.minusDays(1))
        val summary = summarize(listOf(AttendanceEntry(friday, slots[0].id, AttendanceMark.PRESENT)), base)
        summary.baseline!!.counts shouldBe Counts(81, 101)
        summary.baseline!!.asOf shouldBe friday
    }
    test("future marks and nonexistent timetable periods never become recorded attendance") {
        summarize(listOf(AttendanceEntry(friday.plusDays(7), slots[0].id, AttendanceMark.PRESENT), AttendanceEntry(friday, "unknown", AttendanceMark.PRESENT))).baseline shouldBe null
    }
    test("closures cancellations and term end exclude marks from held counts") {
        val entries = slots.map { AttendanceEntry(friday, it.id, AttendanceMark.PRESENT) }
        val close = CalendarException("closed", friday, friday, ExceptionKind.CLOSURE, "Closed")
        summarize(entries, rules = listOf(close)).baseline shouldBe null
        summarize(entries, rules = listOf(close.copy(kind = ExceptionKind.CANCELLATION, sessionId = slots[0].id))).baseline!!.counts shouldBe Counts(4, 4)
        summarize(entries, Baseline(Counts(1, 2), emptyList(), friday.minusDays(1), friday.minusDays(1))).baseline!!.counts shouldBe Counts(1, 2)
    }
    test("Navratri closure ends on 21 October and teaching resumes on 22") {
        TeachingCalendar.generate(LocalDate.parse("2026-10-17"), LocalDate.parse("2026-10-21"), SeedTimetable.sessions, CollegeCalendar.confirmedClosures).size shouldBe 0
        TeachingCalendar.generate(LocalDate.parse("2026-10-22"), LocalDate.parse("2026-10-22"), SeedTimetable.sessions, CollegeCalendar.confirmedClosures).size shouldBe 4
    }
    test("timetable edits cannot erase a recorded period or its subject count") {
        val entry = AttendanceEntry(friday, slots[0].id, AttendanceMark.PRESENT, slots[0])
        val summary = AttendanceTracker.summarize(null, listOf(entry), emptyList(), emptyList(), friday)
        summary.baseline!!.counts shouldBe Counts(1, 1)
        summary.baseline!!.subjects.single().counts shouldBe Counts(1, 1)
        summary.unmarked shouldBe 0
    }
    test("starting counts and marks older than ten years remain readable") {
        val oldDate = LocalDate.parse("2010-01-08")
        val base = Baseline(Counts(75, 100), emptyList(), oldDate)
        AttendanceTracker.summarize(base, emptyList(), SeedTimetable.sessions, emptyList(), friday).baseline shouldBe base
        val entry = AttendanceEntry(oldDate, slots[0].id, AttendanceMark.PRESENT, slots[0])
        AttendanceTracker.summarize(null, listOf(entry), SeedTimetable.sessions, emptyList(), friday).baseline!!.counts shouldBe Counts(1, 1)
    }
    test("historical calendar rows retain deleted or changed timetable sessions") {
        val original = slots[0]
        val entry = AttendanceEntry(friday, original.id, AttendanceMark.PRESENT, original)
        val changed = original.copy(subject = "NEW", title = "Changed subject")
        val edited = SeedTimetable.sessions.map { if (it.id == original.id) changed else it }
        AttendanceTracker.sessionsOn(friday, listOf(entry), edited, emptyList())
            .first { it.session.id == original.id }.session shouldBe original
        val deleted = edited.filterNot { it.id == original.id }
        AttendanceTracker.sessionsOn(friday, listOf(entry), deleted, emptyList())
            .first { it.session.id == original.id }.session shouldBe original
        AttendanceTracker.sessionsOn(friday, emptyList(), deleted, emptyList()).size shouldBe slots.size - 1
    }
    test("monthly attendance uses the sheet month plus later marks; cumulative keeps running") {
        val base = Baseline(Counts(161, 185), emptyList(), LocalDate.parse("2026-09-30"), month = Counts(43, 45))
        val entries = listOf(AttendanceEntry(friday, slots[0].id, AttendanceMark.PRESENT), AttendanceEntry(friday, slots[1].id, AttendanceMark.ABSENT))
        val summary = summarize(entries, base)
        summary.month(YearMonth.of(2026, 9)) shouldBe Counts(43, 45)
        summary.month(YearMonth.of(2026, 10)) shouldBe Counts(1, 2)
        summary.baseline!!.counts shouldBe Counts(162, 187)
    }
    test("marks in the sheet month after its date extend that month") {
        val base = Baseline(Counts(150, 170), emptyList(), friday.minusDays(1), month = Counts(30, 33))
        summarize(listOf(AttendanceEntry(friday, slots[0].id, AttendanceMark.PRESENT)), base).month(YearMonth.from(friday)) shouldBe Counts(31, 34)
    }
    // Present by default. Friday slots end 09:50, 10:45, 11:40, 13:00, 13:55.
    fun assume(entries: List<AttendanceEntry> = emptyList(), base: Baseline? = null, rules: List<CalendarException> = emptyList(),
               now: LocalTime = LocalTime.MAX, hidden: Set<String> = emptySet(), started: LocalDate? = friday) =
        AttendanceTracker.summarize(base, entries, SeedTimetable.sessions, rules, friday, started, assumeFrom = friday, now = now, hiddenSubjects = hidden)

    test("finished periods count as present without a tap; later periods today wait") {
        val midMorning = assume(now = LocalTime.of(11, 0))
        midMorning.baseline!!.counts shouldBe Counts(2, 2)
        midMorning.assumed shouldBe 2
        midMorning.unmarked shouldBe 0
        assume(now = LocalTime.of(10, 45)).baseline!!.counts shouldBe Counts(2, 2)
        assume().baseline!!.counts shouldBe Counts(5, 5)
        assume(now = LocalTime.of(8, 0)).baseline shouldBe null
    }
    test("assumed periods add to the sheet totals; the sheet date still supersedes") {
        val sheet = Baseline(Counts(161, 185), emptyList(), friday.minusDays(1), month = null)
        assume(base = sheet).baseline!!.counts shouldBe Counts(166, 190)
        assume(base = sheet.copy(asOf = friday)).baseline!!.counts shouldBe Counts(161, 185)
    }
    test("an explicit A overrides the assumed present and is never counted twice") {
        val summary = assume(listOf(AttendanceEntry(friday, slots[0].id, AttendanceMark.ABSENT, slots[0])))
        summary.baseline!!.counts shouldBe Counts(4, 5)
        summary.assumed shouldBe 4
    }
    test("No class removes the period from held; a past planned absence counts absent") {
        val noClass = CalendarException("nc", friday, friday, ExceptionKind.CANCELLATION, "No class", slots[1].id)
        assume(rules = listOf(noClass)).baseline!!.counts shouldBe Counts(4, 4)
        val leave = CalendarException("leave", friday, friday, ExceptionKind.ABSENCE, "Leave", slots[2].id)
        assume(rules = listOf(leave)).baseline!!.counts shouldBe Counts(4, 5)
    }
    test("hidden subjects are never assumed or counted unmarked, but saved marks for them still count") {
        assume(hidden = setOf("IFS")).baseline!!.counts shouldBe Counts(3, 3)
        val marked = assume(listOf(AttendanceEntry(friday, slots[0].id, AttendanceMark.ABSENT, slots[0])), hidden = setOf("IFS"))
        marked.baseline!!.counts shouldBe Counts(3, 4)
        AttendanceTracker.summarize(null, emptyList(), SeedTimetable.sessions, emptyList(), friday, friday, hiddenSubjects = setOf("IFS")).unmarked shouldBe 3
    }
    test("days before present-by-default started stay unmarked, never assumed") {
        val summary = assume(started = friday.minusDays(1))
        summary.baseline!!.counts shouldBe Counts(5, 5)
        summary.unmarked shouldBe 4 // Thursday's periods
    }
    test("month totals include assumed periods") {
        assume(now = LocalTime.of(11, 0)).month(YearMonth.from(friday)) shouldBe Counts(2, 2)
    }
    test("end times are read from the period label; unreadable labels wait until the next day") {
        AttendanceTracker.endTime(slots[0]) shouldBe LocalTime.of(9, 50)
        AttendanceTracker.endTime(slots[0].copy(time = "1:05 - 1:55")) shouldBe LocalTime.of(1, 55)
        val vague = slots[0].copy(time = "Morning")
        AttendanceTracker.assumedMark(friday, vague, emptyList(), friday, null, friday, LocalTime.MAX) shouldBe null
        AttendanceTracker.assumedMark(friday, vague, emptyList(), friday, null, friday.plusDays(1), LocalTime.MIN) shouldBe AttendanceMark.PRESENT
        AttendanceTracker.assumedMark(friday, slots[0], emptyList(), null, null, friday, LocalTime.MAX) shouldBe null
        AttendanceTracker.assumedEntries(null, emptyList(), SeedTimetable.sessions, emptyList(), friday, friday.plusDays(1), LocalTime.MAX) shouldBe emptyList()
    }
})
