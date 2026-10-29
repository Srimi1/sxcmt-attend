package `in`.attend.core

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

class RoutineTest : FunSpec({
    val day = LocalDate.parse("2026-11-02")
    fun v(n: Int, from: LocalDate) = RoutineVersion(n, from, SeedTimetable.sessions, "", false, Instant.EPOCH)

    test("the newest version already in force wins") {
        val versions = listOf(v(1, day.minusDays(30)), v(2, day.minusDays(3)), v(3, day.plusDays(5)))
        Routines.active(versions, day)!!.version shouldBe 2
        Routines.upcoming(versions, day)!!.version shouldBe 3
        Routines.active(versions, day.plusDays(5))!!.version shouldBe 3
        Routines.upcoming(versions, day.plusDays(5)) shouldBe null
    }
    test("a revert published later wins even with an earlier effective date") {
        val versions = listOf(v(1, day.minusDays(30)), v(2, day.minusDays(3)), v(3, day.minusDays(30)))
        Routines.active(versions, day)!!.version shouldBe 3
    }
    test("before any version starts, the earliest one is used; no versions means none") {
        Routines.active(listOf(v(1, day.plusDays(9)), v(2, day.plusDays(2))), day)!!.version shouldBe 2
        Routines.active(emptyList(), day) shouldBe null
        Routines.upcoming(emptyList(), day) shouldBe null
    }
    test("validation mirrors server rules") {
        Routines.validate(SeedTimetable.sessions) shouldBe null
        Routines.validate(emptyList()) shouldBe "Add at least one period"
        val s = SeedTimetable.sessions.first()
        Routines.validate(listOf(s, s.copy(id = "other"))) shouldBe "Two subjects share one period"
        Routines.validate(listOf(s, s.copy(period = 9))) shouldBe "Duplicate period ids"
        Routines.validate(listOf(s.copy(day = DayOfWeek.SUNDAY))) shouldBe "Sunday periods cannot be shared"
        Routines.validate(listOf(s.copy(subject = "edp"))) shouldBe "Subject codes use A–Z, 0–9 and _"
        Routines.validate(listOf(s.copy(time = "x".repeat(31)))) shouldBe "A period has an invalid title, time or id"
        Routines.validate(List(73) { s.copy(id = "$it", period = it % 12 + 1) }) shouldBe "At most 72 periods per week"
    }
    test("first version and own versions apply automatically; classmates' changes wait for approval") {
        val mine = v(2, day.minusDays(1)).copy(byMe = true)
        val theirs = v(3, day.minusDays(1))
        Routines.autoApply(listOf(v(1, day.minusDays(9))), null, day)!!.version shouldBe 1
        Routines.autoApply(listOf(v(1, day.minusDays(9)), mine), 1, day)!!.version shouldBe 2
        Routines.autoApply(listOf(v(1, day.minusDays(9)), theirs), 1, day) shouldBe null
        Routines.pending(listOf(v(1, day.minusDays(9)), theirs), 1, null, day)!!.version shouldBe 3
        Routines.pending(listOf(v(1, day.minusDays(9)), theirs), 1, 3, day) shouldBe null
        Routines.pending(listOf(v(1, day.minusDays(9)), theirs), 3, null, day) shouldBe null
        Routines.pending(listOf(v(1, day.minusDays(9))), null, null, day) shouldBe null
        Routines.autoApply(listOf(v(1, day.minusDays(9))), 1, day) shouldBe null
    }
})
