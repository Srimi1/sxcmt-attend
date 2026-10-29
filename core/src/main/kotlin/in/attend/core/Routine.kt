package `in`.attend.core

import java.time.Instant
import java.time.LocalDate

/** One published version of a class's shared timetable. Versions are append-only; a revert is a new version. */
data class RoutineVersion(
    val version: Int, val effectiveFrom: LocalDate, val sessions: List<WeekSession>,
    val note: String, val byMe: Boolean, val createdAt: Instant,
)

data class ClassLink(val id: String, val name: String, val joinCode: String, val isOwner: Boolean = false)

object Routines {
    /** The timetable in force on [date]: the newest version already effective, else the earliest one waiting to start. */
    fun active(versions: List<RoutineVersion>, date: LocalDate): RoutineVersion? =
        versions.filter { it.effectiveFrom <= date }.maxByOrNull { it.version }
            ?: versions.minByOrNull { it.effectiveFrom }

    /** A newer version scheduled to start after [date], if any. */
    fun upcoming(versions: List<RoutineVersion>, date: LocalDate): RoutineVersion? {
        val current = active(versions, date) ?: return null
        return versions.filter { it.effectiveFrom > date && it.version > current.version }.minByOrNull { it.effectiveFrom }
    }

    /**
     * A version applied without asking: the first one after joining or creating a class, or one this user published.
     * Classmates' changes wait for approval (see [pending]) so nobody can silently rewrite another student's timetable.
     */
    fun autoApply(versions: List<RoutineVersion>, applied: Int?, date: LocalDate): RoutineVersion? {
        val current = active(versions, date) ?: return null
        return current.takeIf { it.version != applied && (applied == null || it.byMe) }
    }

    /** A classmate's version now in force that this user has neither applied nor dismissed. */
    fun pending(versions: List<RoutineVersion>, applied: Int?, dismissed: Int?, date: LocalDate): RoutineVersion? {
        val current = active(versions, date) ?: return null
        return current.takeIf { applied != null && it.version != applied && !it.byMe && it.version != dismissed }
    }

    /** Server rules mirrored locally so a bad edit fails before it reaches the network. */
    fun validate(sessions: List<WeekSession>): String? = when {
        sessions.isEmpty() -> "Add at least one period"
        sessions.size > 72 -> "At most 72 periods per week"
        sessions.any { it.day == java.time.DayOfWeek.SUNDAY } -> "Sunday periods cannot be shared"
        sessions.any { !Regex("[A-Z0-9_]{1,20}").matches(it.subject) } -> "Subject codes use A–Z, 0–9 and _"
        sessions.any { it.title.isBlank() || it.title.length > 80 || it.time.length > 30 || !Regex("[A-Za-z0-9-]{1,64}").matches(it.id) } -> "A period has an invalid title, time or id"
        sessions.any { s -> (s.title + s.time).any { it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt() } } -> "Remove hidden characters from titles and times"
        sessions.map { it.id }.distinct().size != sessions.size -> "Duplicate period ids"
        sessions.map { it.day to it.period }.distinct().size != sessions.size -> "Two subjects share one period"
        else -> null
    }
}
