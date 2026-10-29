package `in`.attend.core

import java.time.*
import java.time.temporal.TemporalAdjusters

data class Counts(val attended: Long, val held: Long) {
    init { require(attended >= 0 && held >= attended && held <= 10_000_000) { "Invalid attended/held counts" } }
    val percentage: Double? get() = if (held == 0L) null else 100.0 * attended / held
    operator fun plus(other: Counts) = Counts(attended + other.attended, held + other.held)
}

data class SubjectAttendance(val code: String, val name: String, val counts: Counts)

/** Counts the student copied from the college sheet. They cover every class up to and including [asOf]. [month] is that month's sheet total when known. */
data class Baseline(val counts: Counts, val subjects: List<SubjectAttendance>, val asOf: LocalDate, val termEnd: LocalDate? = null, val month: Counts? = null) {
    init { require(month == null || (month.attended <= counts.attended && month.held <= counts.held)) { "Month totals cannot exceed cumulative totals" } }
}

object AttendanceMath {
    private fun validate(targetBasisPoints: Int) { require(targetBasisPoints in 1..10000) { "Target must be above 0 and at most 100%" } }
    fun recoveryClasses(counts: Counts, targetBasisPoints: Int): Long? {
        validate(targetBasisPoints)
        val deficit = targetBasisPoints.toLong() * counts.held - 10000L * counts.attended
        if (deficit <= 0) return 0
        if (targetBasisPoints == 10000) return null
        val divisor = 10000L - targetBasisPoints
        return (deficit + divisor - 1) / divisor
    }
    fun missAllowance(counts: Counts, targetBasisPoints: Int): Long {
        validate(targetBasisPoints)
        return ((10000L * counts.attended - targetBasisPoints.toLong() * counts.held) / targetBasisPoints).coerceAtLeast(0)
    }
}

data class WeekSession(val id: String, val day: DayOfWeek, val period: Int, val subject: String, val title: String, val time: String) {
    init { require(period in 1..12 && subject.isNotBlank() && title.isNotBlank()) }
}
enum class ExceptionKind { CLOSURE, CANCELLATION, ABSENCE }
data class CalendarException(
    val id: String, val start: LocalDate, val end: LocalDate, val kind: ExceptionKind, val label: String,
    val sessionId: String? = null,
) {
    init { require(!end.isBefore(start)) { "End date precedes start date" } }
    fun applies(date: LocalDate, session: WeekSession) = date >= start && date <= end && (sessionId == null || sessionId == session.id)
}
data class DatedSession(val date: LocalDate, val session: WeekSession)

object NationalHolidays {
    fun name(date: LocalDate): String? = when (MonthDay.from(date)) {
        MonthDay.of(1, 26) -> "Republic Day"
        MonthDay.of(8, 15) -> "Independence Day"
        MonthDay.of(10, 2) -> "Gandhi Jayanti"
        else -> null
    }
}

object TeachingCalendar {
    fun generate(from: LocalDate, to: LocalDate, schedule: List<WeekSession>, exceptions: List<CalendarException>, termEnd: LocalDate? = null): List<DatedSession> {
        if (to < from) return emptyList()
        require(to.toEpochDay() - from.toEpochDay() <= 3660) { "Choose a forecast within ten years" }
        val end = minOf(to, termEnd ?: to)
        val byDay = schedule.groupBy { it.day }
        val result = mutableListOf<DatedSession>()
        var day = from
        while (day <= end) {
            if (day.dayOfWeek != DayOfWeek.SUNDAY && NationalHolidays.name(day) == null) {
                byDay[day.dayOfWeek].orEmpty().sortedBy { it.period }.forEach { session ->
                    if (exceptions.none { it.kind != ExceptionKind.ABSENCE && it.applies(day, session) }) result += DatedSession(day, session)
                }
            }
            day = day.plusDays(1)
        }
        return result
    }
}

enum class Scenario(val title: String) {
    CONTINUE_RATE("Current rate + planned leave"), ATTEND_ALL("Attend every session"), PLANNED_ABSENCES("My plan · includes absences")
}
data class ForecastPoint(val date: LocalDate, val percentage: Double?)
data class SubjectForecast(val code: String, val name: String, val futureHeld: Int, val percentage: Double?)
data class ForecastResult(
    val futureHeld: Int, val futureAttended: Double?, val percentage: Double?,
    val points: List<ForecastPoint>, val subjects: List<SubjectForecast>, val through: LocalDate,
)

object ForecastEngine {
    fun forecast(base: Baseline, target: LocalDate, scenario: Scenario, schedule: List<WeekSession>, rules: List<CalendarException>): ForecastResult {
        require(target >= base.asOf) { "Forecast date must be on or after the reporting date" }
        val sessions = TeachingCalendar.generate(base.asOf.plusDays(1), target, schedule, rules, base.termEnd)
        fun expected(items: List<DatedSession>, counts: Counts): Double? = when (scenario) {
            Scenario.ATTEND_ALL -> items.size.toDouble()
            Scenario.CONTINUE_RATE -> if (counts.held == 0L) null else
                items.count { s -> rules.none { it.kind == ExceptionKind.ABSENCE && it.applies(s.date, s.session) } } * counts.attended.toDouble() / counts.held
            Scenario.PLANNED_ABSENCES -> items.count { s -> rules.none { it.kind == ExceptionKind.ABSENCE && it.applies(s.date, s.session) } }.toDouble()
        }
        fun percent(items: List<DatedSession>, counts: Counts): Double? {
            val a = expected(items, counts) ?: return null
            val held = counts.held + items.size
            return if (held == 0L) null else 100.0 * (counts.attended + a) / held
        }
        val through = minOf(target, base.termEnd ?: target).coerceAtLeast(base.asOf)
        val dates = mutableListOf(base.asOf)
        var monthEnd = base.asOf.with(TemporalAdjusters.lastDayOfMonth())
        if (monthEnd == base.asOf) monthEnd = monthEnd.plusMonths(1).with(TemporalAdjusters.lastDayOfMonth())
        while (monthEnd < through) { dates += monthEnd; monthEnd = monthEnd.plusMonths(1).with(TemporalAdjusters.lastDayOfMonth()) }
        dates += through
        val points = dates.distinct().map { date -> ForecastPoint(date, percent(sessions.filter { it.date <= date }, base.counts)) }
        val mappedSubjects = schedule.map { it.subject }.toSet()
        val subjects = base.subjects.filter { it.code in mappedSubjects }.map { subject ->
            val items = sessions.filter { it.session.subject == subject.code }
            SubjectForecast(subject.code, subject.name, items.size, percent(items, subject.counts))
        }
        return ForecastResult(sessions.size, expected(sessions, base.counts), percent(sessions, base.counts), points, subjects, through)
    }
}
