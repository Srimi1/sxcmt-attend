package `in`.attend.core

import java.time.LocalDate

/** Extracurricular hours the college credits separately from timetabled classes. */
data class ActivityEntry(val id: String, val date: LocalDate, val hours: Int, val label: String) {
    init { require(hours in 1..500 && label.length <= 80) { "Activity hours must be between 1 and 500" } }
}

/**
 * Class attendance plus activity credit.
 *
 * Activity counts as attended lectures only: each hour adds [lecturesPerHour] to the attended side and nothing to
 * the held side. 360 / 600 (60%) plus 30 hours becomes 390 / 600 (65%). Credit beyond the held total cannot raise
 * attendance above 100%; [unusedLectures] reports the excess instead of hiding it.
 */
data class ActivityCredit(val classes: Counts, val hours: Long, val creditedLectures: Long, val withActivity: Counts, val unusedLectures: Long)

object ActivityMath {
    fun totalHours(entries: List<ActivityEntry>): Long = entries.sumOf { it.hours.toLong() }

    fun credit(classes: Counts, hours: Long, lecturesPerHour: Int = 1): ActivityCredit {
        require(hours >= 0 && lecturesPerHour in 1..10) { "Invalid activity credit" }
        val lectures = hours * lecturesPerHour
        val attended = minOf(classes.held, classes.attended + lectures)
        return ActivityCredit(classes, hours, lectures, Counts(attended, classes.held), classes.attended + lectures - attended)
    }

    /** Activity hours that would lift class attendance to the target with no further absences; null when no class is held. */
    fun hoursToTarget(classes: Counts, targetBasisPoints: Int, lecturesPerHour: Int = 1): Long? {
        require(targetBasisPoints in 1..10000 && lecturesPerHour in 1..10) { "Invalid target" }
        if (classes.held == 0L) return null
        val needed = (targetBasisPoints.toLong() * classes.held + 9999) / 10000 - classes.attended
        return if (needed <= 0) 0 else (needed + lecturesPerHour - 1) / lecturesPerHour
    }
}
