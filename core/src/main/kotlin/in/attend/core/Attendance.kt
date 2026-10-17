package `in`.attend.core

import java.time.*
import java.time.temporal.TemporalAdjusters

data class Counts(val attended: Long, val held: Long) {
    init { require(attended >= 0 && held >= attended && held <= 10_000_000) { "Invalid attended/held counts" } }
    val percentage: Double? get() = if (held == 0L) null else 100.0 * attended / held
    operator fun plus(other: Counts) = Counts(attended + other.attended, held + other.held)
}

data class StudentProfile(val name: String, val rollNumber: String, val semester: String, val course: String = "", val portalId: String? = null, val academicYear: String? = null) {
    init { require(name.isNotBlank() && rollNumber.isNotBlank() && semester.isNotBlank()) { "ERP name, roll number and semester are required" } }
    private fun clean(value: String) = value.trim().uppercase().replace(Regex("\\s+"), " ")
    // Local roll numbers are not college-wide identifiers. Fall back conservatively to all visible discriminators.
    val accountKey: String get() = portalId?.takeIf { it.isNotBlank() }?.let { "ERP:${clean(it)}" }
        ?: "${clean(rollNumber)}|${clean(name)}|${clean(course)}"
    val termKey: String get() = "$accountKey|${clean(course)}|${clean(semester)}|${academicYear?.let(::clean).orEmpty()}"
}

object StudentIdentity {
    private fun same(a: String, b: String) = a.trim().replace(Regex("\\s+"), " ").equals(b.trim().replace(Regex("\\s+"), " "), true)
    fun conflicts(a: StudentProfile, b: StudentProfile): Boolean =
        !same(a.name, b.name) || !same(a.rollNumber, b.rollNumber) || !same(a.semester, b.semester) ||
            (a.course.isNotBlank() && b.course.isNotBlank() && !same(a.course, b.course)) ||
            (!a.portalId.isNullOrBlank() && !b.portalId.isNullOrBlank() && !same(a.portalId, b.portalId)) ||
            (!a.academicYear.isNullOrBlank() && !b.academicYear.isNullOrBlank() && !same(a.academicYear, b.academicYear))

    /** Retain optional metadata only after fresh complete identity agrees with every shared discriminator. */
    fun reconcile(previous: StudentProfile, fresh: StudentProfile): StudentProfile = if (conflicts(previous, fresh)) fresh else fresh.copy(
        course = fresh.course.ifBlank { previous.course }, portalId = fresh.portalId ?: previous.portalId,
        academicYear = fresh.academicYear ?: previous.academicYear,
    )
}

enum class ReportKind { PERIOD, CUMULATIVE }
data class SubjectAttendance(val code: String, val name: String, val counts: Counts)
data class AttendanceSnapshot(
    val profile: StudentProfile,
    val start: LocalDate?,
    val end: LocalDate,
    val kind: ReportKind,
    val counts: Counts,
    val subjects: List<SubjectAttendance>,
    val fetchedAt: Instant,
    val sourceUrl: String,
    val termEnd: LocalDate? = null,
) {
    init {
        require(start == null || !end.isBefore(start)) { "Reporting dates are reversed" }
        require(kind == ReportKind.CUMULATIVE || start != null) { "Period report needs a start date" }
        require(subjects.map { it.code }.distinct().size == subjects.size) { "Duplicate subject totals" }
    }
    val key: String get() = "${profile.termKey}|$kind|$start|$end"
}

data class Baseline(val counts: Counts, val subjects: List<SubjectAttendance>, val asOf: LocalDate, val termEnd: LocalDate? = null)

object Ledger {
    fun merge(existing: List<AttendanceSnapshot>, incoming: List<AttendanceSnapshot>): List<AttendanceSnapshot> {
        require(incoming.isNotEmpty()) { "No attendance records found" }
        val freshProfile = incoming.map { it.profile }.reduce { a, b ->
            require(!StudentIdentity.conflicts(a, b)) { "Import contains more than one student or semester" }
            StudentIdentity.reconcile(a, b)
        }
        val profile = existing.firstOrNull()?.profile?.let { StudentIdentity.reconcile(it, freshProfile) } ?: freshProfile
        val normalizedIncoming = incoming.map { it.copy(profile = profile) }
        val retained = existing.filter { old -> !StudentIdentity.conflicts(old.profile, profile) && normalizedIncoming.none { fresh ->
            fresh.kind == ReportKind.PERIOD && old.kind == fresh.kind && old.start == fresh.start && old.end < fresh.end
        } }.map { it.copy(profile = profile) }
        normalizedIncoming.filter { fresh -> fresh.kind == ReportKind.PERIOD }.forEach { fresh ->
            require(retained.none { old -> old.kind == fresh.kind && old.start == fresh.start && old.end > fresh.end }) { "This report is older than the saved version. Saved attendance was kept." }
        }
        normalizedIncoming.groupBy { it.key }.values.forEach { copies ->
            require(copies.map { it.counts to it.subjects }.distinct().size == 1) { "Conflicting totals for the same reporting period" }
        }
        val merged = (retained + normalizedIncoming).associateBy { it.key }.values.sortedWith(compareBy({ it.end }, { it.kind.name }))
        baseline(merged) // Reject inconsistent ranges before storage is changed.
        return merged
    }

    fun baseline(reports: List<AttendanceSnapshot>): Baseline? {
        if (reports.isEmpty()) return null
        require(reports.map { it.profile.termKey }.distinct().size == 1) { "Mixed student records" }
        val unique = reports.associateBy { it.key }.values
        val periods = unique.filter { it.kind == ReportKind.PERIOD }.sortedBy { it.start }
        periods.zipWithNext().forEach { (a, b) ->
            require(b.start!!.isAfter(a.end)) { "Reporting periods overlap. Import a complete replacement report." }
        }
        val cumulative = unique.filter { it.kind == ReportKind.CUMULATIVE }.maxByOrNull { it.end }
        val selected = if (cumulative == null) periods else {
            periods.forEach {
                require(!(it.start!! <= cumulative.end && it.end > cumulative.end)) { "Period partially overlaps cumulative attendance" }
            }
            listOf(cumulative) + periods.filter { it.start!!.isAfter(cumulative.end) }
        }
        if (selected.isEmpty()) return null
        // A gap cannot be interpreted as zero classes. Do not present a partial sum as semester attendance.
        selected.zipWithNext().forEach { (a, b) ->
            require(b.start == a.end.plusDays(1)) { "Attendance history has a gap; import a cumulative report or missing period." }
        }
        val commonSubjects = selected.map { it.subjects.map { s -> s.code }.toSet() }.reduce { a, b -> a intersect b }
        val subjects = commonSubjects.map { code ->
            val values = selected.map { r -> r.subjects.first { it.code == code } }
            values.first().copy(counts = values.map { it.counts }.reduce(Counts::plus))
        }.sortedBy { it.name }
        return Baseline(
            selected.map { it.counts }.reduce(Counts::plus), subjects, selected.maxOf { it.end },
            selected.mapNotNull { it.termEnd }.maxOrNull(),
        )
    }
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
    CONTINUE_RATE("Continue current rate"), ATTEND_ALL("Attend every session"), PLANNED_ABSENCES("Use planned absences")
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
            Scenario.CONTINUE_RATE -> if (counts.held == 0L) null else items.size * counts.attended.toDouble() / counts.held
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
