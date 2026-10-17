package `in`.attend.core

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.assertions.throwables.shouldThrow
import java.time.*

class AttendanceTest : FunSpec({
    test("sheet totals use counts, not an average of percentages") {
        val reports = listOf(
            report("2026-07-13", "2026-07-31", 42, 54),
            report("2026-08-01", "2026-08-31", 76, 86),
            report("2026-09-01", "2026-09-30", 43, 45),
        )
        Ledger.baseline(reports.take(1))!!.counts shouldBe Counts(42, 54)
        Ledger.baseline(reports.take(2))!!.counts shouldBe Counts(118, 140)
        val total = Ledger.baseline(reports)!!.counts
        total shouldBe Counts(161, 185)
        total.percentage!! shouldBe (87.027027027 plusOrMinus 0.000001)
    }
    test("refresh replaces a reporting period rather than adding attendance twice") {
        val original = report("2026-09-01", "2026-09-30", 42, 45)
        val updated = report("2026-09-01", "2026-09-30", 43, 45)
        val ledger = Ledger.merge(listOf(original), listOf(updated, updated))
        ledger.size shouldBe 1
        Ledger.baseline(ledger)!!.counts shouldBe Counts(43, 45)
    }
    test("cumulative total and months inside it are not added twice") {
        val months = listOf(report("2026-08-01", "2026-08-31", 76, 86), report("2026-09-01", "2026-09-30", 43, 45))
        val cumulative = report("2026-07-13", "2026-08-31", 118, 140, ReportKind.CUMULATIVE)
        Ledger.baseline(months + cumulative)!!.counts shouldBe Counts(161, 185)
    }
    test("partially overlapping periods cannot corrupt totals") {
        shouldThrow<IllegalArgumentException> {
            Ledger.merge(listOf(report("2026-09-01", "2026-09-20", 10, 20)), listOf(report("2026-09-15", "2026-09-30", 12, 15)))
        }
        shouldThrow<IllegalArgumentException> {
            Ledger.baseline(listOf(report("2026-07-13", "2026-09-15", 100, 120, ReportKind.CUMULATIVE), report("2026-09-01", "2026-09-30", 43, 45)))
        }
    }
    test("different student or semester cannot inherit previous attendance") {
        val old = report("2026-09-01", "2026-09-30", 43, 45)
        val other = old.copy(profile = StudentProfile("Other student", "OTHER", "5"), counts = Counts(10, 45))
        Ledger.merge(listOf(old), listOf(other)) shouldBe listOf(other)
        Ledger.merge(listOf(old), listOf(old.copy(profile = old.profile.copy(semester = "6")))).size shouldBe 1
    }
    test("same local roll and semester from another student or programme remain isolated") {
        val old = report("2026-09-01", "2026-09-30", 43, 45)
        val other = old.copy(profile = old.profile.copy(name = "Different student"), counts = Counts(10, 45))
        Ledger.merge(listOf(old), listOf(other)) shouldBe listOf(other)
        (old.profile.copy(course = "BCA").termKey == old.profile.termKey) shouldBe false
        (old.profile.copy(academicYear = "2027-28").termKey == old.profile.copy(academicYear = "2026-27").termKey) shouldBe false
    }
    test("missing optional identity metadata preserves existing history after a fresh matching profile") {
        val old = report("2026-09-01", "2026-09-30", 43, 45).let { it.copy(profile = it.profile.copy(portalId = "ERP-ID", academicYear = "2026-27")) }
        val fresh = old.copy(profile = StudentProfile("Sample student", "DEMO", "5"))
        val merged = Ledger.merge(listOf(old), listOf(fresh))
        merged.single().profile shouldBe old.profile
        merged.single().counts shouldBe old.counts
    }
    test("month-to-date refresh extends the same reporting period without adding its prefix twice") {
        val early = report("2026-09-01", "2026-09-15", 20, 22)
        val full = report("2026-09-01", "2026-09-30", 43, 45)
        Ledger.merge(listOf(early), listOf(full)) shouldBe listOf(full)
        shouldThrow<IllegalArgumentException> { Ledger.merge(listOf(full), listOf(early)) }
    }
    test("conflicting same-period records in one import are rejected") {
        val full = report("2026-09-01", "2026-09-30", 43, 45)
        shouldThrow<IllegalArgumentException> { Ledger.merge(emptyList(), listOf(full, full.copy(counts = Counts(10, 45)))) }
    }
    test("unknown attendance and impossible counts are handled explicitly") {
        Counts(0, 0).percentage shouldBe null
        shouldThrow<IllegalArgumentException> { Counts(5, 4) }
        shouldThrow<IllegalArgumentException> { Counts(-1, 4) }
    }
    test("threshold arithmetic does not round 74.99 percent up to 75") {
        AttendanceMath.recoveryClasses(Counts(3, 4), 7500) shouldBe 0L
        AttendanceMath.missAllowance(Counts(3, 4), 7500) shouldBe 0L
        AttendanceMath.recoveryClasses(Counts(74, 100), 7500) shouldBe 4L
        AttendanceMath.missAllowance(Counts(161, 185), 7500) shouldBe 29L
        AttendanceMath.recoveryClasses(Counts(7499, 10000), 7500) shouldBe 4L
        AttendanceMath.recoveryClasses(Counts(9, 10), 10000) shouldBe null
        AttendanceMath.recoveryClasses(Counts(10, 10), 10000) shouldBe 0L
    }
    test("recovery and absence boundaries satisfy the unrounded inequality") {
        for (held in 1L..60L) for (attended in 0L..held) {
            val c = Counts(attended, held)
            val n = AttendanceMath.recoveryClasses(c, 7500)!!
            (10000L * (attended + n) >= 7500L * (held + n)) shouldBe true
            if (n > 0) (10000L * (attended + n - 1) < 7500L * (held + n - 1)) shouldBe true
            val b = AttendanceMath.missAllowance(c, 7500)
            if (10000L * attended >= 7500L * held) {
                (10000L * attended >= 7500L * (held + b)) shouldBe true
                (10000L * attended < 7500L * (held + b + 1)) shouldBe true
            }
        }
    }
    test("timetable has 24 sessions, including two separate Friday IFS periods") {
        SeedTimetable.sessions.size shouldBe 24
        DayOfWeek.entries.map { d -> SeedTimetable.sessions.count { it.day == d } } shouldBe listOf(5, 3, 3, 4, 5, 4, 0)
        SeedTimetable.sessions.count { it.day == DayOfWeek.FRIDAY && it.subject == "IFS" } shouldBe 2
        SeedTimetable.sessions.count { it.subject in setOf("PLACEMENT", "ACTIVITY") } shouldBe 2
    }
    test("September scheduled total stays separate from 45 officially held") {
        TeachingCalendar.generate(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30"), SeedTimetable.sessions, emptyList()).size shouldBe 102
        Ledger.baseline(listOf(report("2026-09-01", "2026-09-30", 43, 45)))!!.counts.held shouldBe 45L
    }
    test("only national holidays are automatic; festivals remain teaching days") {
        NationalHolidays.name(LocalDate.parse("2026-01-26")) shouldBe "Republic Day"
        NationalHolidays.name(LocalDate.parse("2026-08-15")) shouldBe "Independence Day"
        NationalHolidays.name(LocalDate.parse("2026-10-02")) shouldBe "Gandhi Jayanti"
        NationalHolidays.name(LocalDate.parse("2026-11-08")) shouldBe null
        TeachingCalendar.generate(LocalDate.parse("2026-10-02"), LocalDate.parse("2026-10-02"), SeedTimetable.sessions, emptyList()).size shouldBe 0
        TeachingCalendar.generate(LocalDate.parse("2025-01-26"), LocalDate.parse("2025-01-26"), SeedTimetable.sessions, emptyList()).size shouldBe 0
    }
    test("closures and individual cancellations remove held sessions; absences do not") {
        val day = LocalDate.parse("2026-09-04")
        val friday = SeedTimetable.sessions.filter { it.day == DayOfWeek.FRIDAY }
        val cancel = CalendarException("c", day, day, ExceptionKind.CANCELLATION, "Cancelled", friday.first().id)
        val absence = cancel.copy(id = "a", kind = ExceptionKind.ABSENCE)
        TeachingCalendar.generate(day, day, friday, listOf(cancel)).size shouldBe 4
        TeachingCalendar.generate(day, day, friday, listOf(absence)).size shouldBe 5
        TeachingCalendar.generate(day, day, friday, listOf(cancel.copy(sessionId = null, kind = ExceptionKind.CLOSURE))).size shouldBe 0
    }
    test("forecast honors report date, semester end, and planned absences") {
        val day = LocalDate.parse("2026-09-04")
        val base = Baseline(Counts(3, 4), emptyList(), day.minusDays(1), day)
        val all = ForecastEngine.forecast(base, day.plusMonths(1), Scenario.ATTEND_ALL, SeedTimetable.sessions, emptyList())
        all.futureHeld shouldBe 5
        all.futureAttended shouldBe 5.0
        all.percentage!! shouldBe (100.0 * 8 / 9 plusOrMinus 0.00001)
        val absent = CalendarException("a", day, day, ExceptionKind.ABSENCE, "Leave")
        val planned = ForecastEngine.forecast(base, day, Scenario.PLANNED_ABSENCES, SeedTimetable.sessions, listOf(absent))
        planned.futureHeld shouldBe 5
        planned.futureAttended shouldBe 0.0
        planned.percentage!! shouldBe (100.0 * 3 / 9 plusOrMinus 0.00001)
        val trend = ForecastEngine.forecast(base, day, Scenario.CONTINUE_RATE, SeedTimetable.sessions, emptyList())
        trend.percentage!! shouldBe (75.0 plusOrMinus 0.00001)
    }
    test("missing historical rate never becomes an invented trend") {
        val base = Baseline(Counts(0, 0), emptyList(), LocalDate.parse("2026-09-03"))
        ForecastEngine.forecast(base, LocalDate.parse("2026-09-04"), Scenario.CONTINUE_RATE, SeedTimetable.sessions, emptyList()).percentage shouldBe null
        shouldThrow<IllegalArgumentException> { ForecastEngine.forecast(base, base.asOf.minusDays(1), Scenario.ATTEND_ALL, SeedTimetable.sessions, emptyList()) }
    }
})

internal fun report(start: String, end: String, a: Long, h: Long, kind: ReportKind = ReportKind.PERIOD) = AttendanceSnapshot(
    StudentProfile("Sample student", "DEMO", "5", "BBA IB"), LocalDate.parse(start), LocalDate.parse(end), kind,
    Counts(a, h), emptyList(), Instant.parse("2026-10-17T10:00:00Z"), "https://erp.sxcpatna.edu.in/",
)
