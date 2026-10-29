package `in`.attend.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import java.time.LocalDate

class ActivityTest : FunSpec({
    test("activity hours add to attended only: 60 percent plus 30 hours becomes 65 percent") {
        val credit = ActivityMath.credit(Counts(360, 600), 30)
        credit.withActivity shouldBe Counts(390, 600)
        credit.withActivity.percentage!! shouldBe (65.0 plusOrMinus 0.000001)
        credit.unusedLectures shouldBe 0L
    }
    test("credit never lifts attendance above 100 percent and reports the excess") {
        val credit = ActivityMath.credit(Counts(161, 185), 30)
        credit.withActivity shouldBe Counts(185, 185)
        credit.unusedLectures shouldBe 6L
    }
    test("conversion factor scales lectures per hour") {
        ActivityMath.credit(Counts(100, 200), 10, lecturesPerHour = 2).withActivity shouldBe Counts(120, 200)
        shouldThrow<IllegalArgumentException> { ActivityMath.credit(Counts(1, 2), -1) }
    }
    test("hours needed to reach the target use exact thresholds") {
        ActivityMath.hoursToTarget(Counts(360, 600), 7500) shouldBe 90L
        ActivityMath.hoursToTarget(Counts(161, 185), 7500) shouldBe 0L
        ActivityMath.hoursToTarget(Counts(74, 100), 7500) shouldBe 1L
        ActivityMath.hoursToTarget(Counts(0, 0), 7500) shouldBe null
        ActivityMath.hoursToTarget(Counts(70, 100), 7500, lecturesPerHour = 2) shouldBe 3L
        val c = Counts(360, 600)
        val h = ActivityMath.hoursToTarget(c, 7500)!!
        (10000L * ActivityMath.credit(c, h).withActivity.attended >= 7500L * c.held) shouldBe true
        (10000L * ActivityMath.credit(c, h - 1).withActivity.attended < 7500L * c.held) shouldBe true
    }
    test("totals sum entries and invalid entries are rejected") {
        val day = LocalDate.parse("2026-10-10")
        ActivityMath.totalHours(listOf(ActivityEntry("a", day, 20, "NSS"), ActivityEntry("b", day, 10, "Fest"))) shouldBe 30L
        shouldThrow<IllegalArgumentException> { ActivityEntry("c", day, 0, "None") }
    }
})
