package `in`.attend.core

import java.time.LocalDate

enum class AttendanceMark { PRESENT, ABSENT }
data class AttendanceEntry(val date: LocalDate, val sessionId: String, val mark: AttendanceMark, val sessionSnapshot: WeekSession? = null)
data class TrackingSummary(val baseline: Baseline?, val studentCounts: Counts, val unmarked: Int)

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
        return TeachingCalendar.generate(date, date, schedule.filterNot { it.id in savedIds } + saved, rules, termEnd)
    }

    fun summarize(base: Baseline?, entries: List<AttendanceEntry>, schedule: List<WeekSession>,
                  rules: List<CalendarException>, today: LocalDate, started: LocalDate? = null): TrackingSummary {
        val relevant = entries.filter { it.date <= today && (base == null || it.date > base.asOf) }
            .associateBy { it.date to it.sessionId }
        val from = base?.asOf?.plusDays(1) ?: started ?: relevant.keys.minOfOrNull { it.first } ?: today
        fun slot(entry: AttendanceEntry) = entry.sessionSnapshot?.takeIf { it.id == entry.sessionId }
            ?: schedule.firstOrNull { it.id == entry.sessionId }
        val valid = relevant.values.filter { entry ->
            val session = slot(entry)
            session != null && entry.date >= from && entry.date.dayOfWeek == session.day &&
                entry.date.dayOfWeek != java.time.DayOfWeek.SUNDAY && NationalHolidays.name(entry.date) == null &&
                (base?.termEnd == null || entry.date <= base.termEnd) &&
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
            valid.isEmpty() -> base
            else -> Baseline((base?.counts ?: Counts(0, 0)) + added, subjects, today, base?.termEnd)
        }
        val marked = valid.map { it.date to it.sessionId }.toSet()
        // Historical records may predate the forecast's ten-year range limit.
        var unmarked = 0
        var cursor = from
        while (cursor <= today) {
            val end = minOf(today, cursor.plusDays(3660))
            unmarked += TeachingCalendar.generate(cursor, end, schedule, rules, base?.termEnd)
                .count { (it.date to it.session.id) !in marked }
            cursor = end.plusDays(1)
        }
        return TrackingSummary(combined, added, unmarked)
    }
}
