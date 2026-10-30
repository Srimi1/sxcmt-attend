package `in`.attend.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

enum class AttendanceMark { PRESENT, ABSENT }

/** ASSUME_PRESENT: finished periods count as present unless the student taps A. MARK_EACH: only tapped periods count. */
enum class MarkingStyle { ASSUME_PRESENT, MARK_EACH }
data class AttendanceEntry(val date: LocalDate, val sessionId: String, val mark: AttendanceMark, val sessionSnapshot: WeekSession? = null)
data class TrackingSummary(val baseline: Baseline?, val studentCounts: Counts, val unmarked: Int, val monthly: Map<YearMonth, Counts> = emptyMap(), val assumed: Int = 0) {
    /** Calendar-month attendance: the sheet's month total for the baseline month plus marks after it. */
    fun month(month: YearMonth): Counts? = monthly[month]
}

object CollegeCalendar {
    // College dates confirmed by the student. This is not an inferred festival calendar.
    val confirmedClosures = listOf(CalendarException("college-navratri-2026", LocalDate.parse("2026-10-17"),
        LocalDate.parse("2026-10-21"), ExceptionKind.CLOSURE, "Navratri break · college confirmed"))
}

object AttendanceTracker {
    fun sessionsOn(date: LocalDate, entries: List<AttendanceEntry>, schedule: List<WeekSession>,
                   rules: List<CalendarException>, termEnd: LocalDate? = null): List<DatedSession> {
        val saved = entries.filter { it.date == date }.associateBy { it.sessionId }.values
            .mapNotNull { entry -> entry.sessionSnapshot?.takeIf { it.id == entry.sessionId } }
        val savedIds = saved.map { it.id }.toSet()
        val savedSlots = saved.map { it.day to it.period }.toSet()
        return TeachingCalendar.generate(date, date,
            schedule.filterNot { it.id in savedIds || (it.day to it.period) in savedSlots } + saved, rules, termEnd)
    }

    private fun sessionFor(entry: AttendanceEntry, schedule: List<WeekSession>): WeekSession? =
        entry.sessionSnapshot?.takeIf { it.id == entry.sessionId } ?: schedule.firstOrNull { it.id == entry.sessionId }

    // A new id for an already-recorded period must not create a second class on the same date.
    private fun occupiedSlots(entries: Collection<AttendanceEntry>, schedule: List<WeekSession>) = entries.mapNotNull { entry ->
        sessionFor(entry, schedule)?.let { Triple(entry.date, it.day, it.period) }
    }.toSet()

    private val clock = Regex("""(?<![0-9])(\d{1,2}):(\d{2})(?![0-9])(?:\s*([ap])\.?m\.?)?""", RegexOption.IGNORE_CASE)

    /** Read the last time in the label, supporting both 24-hour times and explicit AM/PM. */
    fun endTime(session: WeekSession): LocalTime? = clock.findAll(session.time).lastOrNull()
        ?.takeIf { session.time.substring(it.range.last + 1).isBlank() }?.let {
        runCatching {
            val hour = it.groupValues[1].toInt()
            val suffix = it.groupValues[3].lowercase()
            val resolvedHour = if (suffix.isEmpty()) hour else {
                require(hour in 1..12)
                hour % 12 + if (suffix == "p") 12 else 0
            }
            LocalTime.of(resolvedHour, it.groupValues[2].toInt())
        }.getOrNull()
    }

    /**
     * The mark a period gets without a tap in present-by-default mode, or null when it isn't assumed:
     * before [assumeFrom], on or before the sheet date, or later today and not yet over (unreadable times wait a day).
     * A planned absence makes a finished period absent. Callers handle explicit marks, which always win.
     */
    fun assumedMark(date: LocalDate, session: WeekSession, rules: List<CalendarException>, assumeFrom: LocalDate?,
                    sheetDate: LocalDate?, today: LocalDate, now: LocalTime, assumeAfter: LocalDateTime? = null): AttendanceMark? {
        if (assumeFrom == null || date < assumeFrom || date > today || (sheetDate != null && date <= sheetDate)) return null
        // After a timetable change, new slots must not be backfilled into elapsed history.
        // Unknown end times on the change day cannot safely be classified as new periods.
        if (assumeAfter != null) {
            if (date < assumeAfter.toLocalDate()) return null
            if (date == assumeAfter.toLocalDate()) {
                val end = endTime(session) ?: return null
                if (end <= assumeAfter.toLocalTime()) return null
            }
        }
        if (date == today) { val end = endTime(session) ?: return null; if (now < end) return null }
        return if (rules.any { it.kind == ExceptionKind.ABSENCE && it.applies(date, session) }) AttendanceMark.ABSENT else AttendanceMark.PRESENT
    }

    /** Periods counted without a tap, as entries carrying their session snapshot. Closures, cancellations and hidden subjects never appear. */
    fun assumedEntries(base: Baseline?, entries: List<AttendanceEntry>, schedule: List<WeekSession>, rules: List<CalendarException>,
                       today: LocalDate, assumeFrom: LocalDate?, now: LocalTime, termEnd: LocalDate? = base?.termEnd,
                       assumeAfter: LocalDateTime? = null): List<AttendanceEntry> {
        if (assumeFrom == null) return emptyList()
        val start = maxOf(assumeFrom, base?.asOf?.plusDays(1) ?: assumeFrom, assumeAfter?.toLocalDate() ?: assumeFrom)
        if (start > today) return emptyList()
        val explicit = entries.map { it.date to it.sessionId }.toSet()
        val occupied = occupiedSlots(entries, schedule)
        val result = mutableListOf<AttendanceEntry>()
        var cursor = start
        while (cursor <= today) {
            val end = minOf(today, cursor.plusDays(3660))
            TeachingCalendar.generate(cursor, end, schedule, rules, termEnd).forEach { s ->
                if ((s.date to s.session.id) !in explicit && Triple(s.date, s.session.day, s.session.period) !in occupied)
                    assumedMark(s.date, s.session, rules, assumeFrom, base?.asOf, today, now, assumeAfter)?.let { result += AttendanceEntry(s.date, s.session.id, it, s.session) }
            }
            cursor = end.plusDays(1)
        }
        return result
    }

    /**
     * Combined attendance: sheet totals, then explicit marks after the sheet date, then (in present-by-default mode)
     * finished periods from [assumeFrom] that have no mark. Periods of [hiddenSubjects] are never generated.
     */
    fun summarize(base: Baseline?, entries: List<AttendanceEntry>, schedule: List<WeekSession>,
                  rules: List<CalendarException>, today: LocalDate, started: LocalDate? = null,
                  assumeFrom: LocalDate? = null, now: LocalTime = LocalTime.MAX, hiddenSubjects: Set<String> = emptySet(),
                  termEnd: LocalDate? = base?.termEnd, assumeAfter: LocalDateTime? = null): TrackingSummary {
        val mine = schedule.filter { it.subject !in hiddenSubjects }
        val assumed = assumedEntries(base, entries.filter { it.date <= today }, mine, rules, today, assumeFrom, now, termEnd, assumeAfter)
        val summary = count(base, entries + assumed, schedule, mine, rules, today, started, unmarkedUntil = assumeFrom?.minusDays(1) ?: today, termEnd = termEnd)
        return summary.copy(assumed = assumed.count { it.mark == AttendanceMark.PRESENT })
    }

    // [schedule] resolves saved marks; [generated] (the student's own subjects) decides what counts as unmarked.
    private fun count(base: Baseline?, entries: List<AttendanceEntry>, schedule: List<WeekSession>, generated: List<WeekSession>,
                      rules: List<CalendarException>, today: LocalDate, started: LocalDate?, unmarkedUntil: LocalDate, termEnd: LocalDate?): TrackingSummary {
        val relevant = entries.filter { it.date <= today && (base == null || it.date > base.asOf) }
            .associateBy { it.date to it.sessionId }
        val from = base?.asOf?.plusDays(1) ?: started ?: relevant.keys.minOfOrNull { it.first } ?: today
        fun slot(entry: AttendanceEntry) = sessionFor(entry, schedule)
        val valid = relevant.values.filter { entry ->
            val session = slot(entry)
            session != null && entry.date >= from && entry.date.dayOfWeek == session.day &&
                entry.date.dayOfWeek != java.time.DayOfWeek.SUNDAY && NationalHolidays.name(entry.date) == null &&
                (termEnd == null || entry.date <= termEnd) &&
                rules.none { it.kind != ExceptionKind.ABSENCE && it.applies(entry.date, session) }
        }
        val added = Counts(valid.count { it.mark == AttendanceMark.PRESENT }.toLong(), valid.size.toLong())
        fun subjectCounts(code: String): Counts {
            val items = valid.filter { slot(it)?.subject == code }
            return Counts(items.count { it.mark == AttendanceMark.PRESENT }.toLong(), items.size.toLong())
        }
        val subjects = if (base != null) base.subjects.map { it.copy(counts = it.counts + subjectCounts(it.code)) }
            else valid.mapNotNull { slot(it) }.distinctBy { it.subject }.map { SubjectAttendance(it.subject, it.title, subjectCounts(it.subject)) }
        val combined = when {
            valid.isEmpty() -> base?.copy(termEnd = termEnd)
            else -> Baseline((base?.counts ?: Counts(0, 0)) + added, subjects, today, termEnd)
        }
        val marked = valid.map { it.date to it.sessionId }.toSet()
        val occupied = occupiedSlots(relevant.values, schedule)
        // Historical records may predate the forecast's ten-year range limit.
        var unmarked = 0
        var cursor = from
        val unmarkedEnd = minOf(today, unmarkedUntil)
        while (cursor <= unmarkedEnd) {
            val end = minOf(unmarkedEnd, cursor.plusDays(3660))
            unmarked += TeachingCalendar.generate(cursor, end, generated, rules, termEnd)
                .count { (it.date to it.session.id) !in marked && Triple(it.date, it.session.day, it.session.period) !in occupied }
            cursor = end.plusDays(1)
        }
        val monthly = valid.groupBy { YearMonth.from(it.date) }.mapValues { (_, items) ->
            Counts(items.count { it.mark == AttendanceMark.PRESENT }.toLong(), items.size.toLong())
        }.toMutableMap()
        base?.month?.let { sheet -> val m = YearMonth.from(base.asOf); monthly[m] = sheet + (monthly[m] ?: Counts(0, 0)) }
        return TrackingSummary(combined, added, unmarked, monthly)
    }
}
