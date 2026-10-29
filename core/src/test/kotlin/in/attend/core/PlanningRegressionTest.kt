package `in`.attend.core

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate

class PlanningRegressionTest : FunSpec({
    test("saving a planned absence changes the current-rate forecast on 23 October") {
        val day = LocalDate.parse("2026-10-23")
        val base = Baseline(Counts(75, 100), emptyList(), day.minusDays(1))
        val rule = CalendarException("leave", day, day, ExceptionKind.ABSENCE, "Away")
        val result = ForecastEngine.forecast(base, day, Scenario.CONTINUE_RATE, SeedTimetable.sessions, listOf(rule))
        result.futureHeld shouldBe 5
        result.futureAttended shouldBe 0.0
    }
})
