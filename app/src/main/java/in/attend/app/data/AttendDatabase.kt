package `in`.attend.app.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import `in`.attend.core.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.*

@Entity(tableName = "app_state")
data class StoredState(@PrimaryKey val id: Int = 1, val payload: String)
@Dao
interface StateDao {
    @Query("SELECT * FROM app_state WHERE id = 1") fun observe(): Flow<StoredState?>
    @Query("SELECT * FROM app_state WHERE id = 1") suspend fun read(): StoredState?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun write(value: StoredState)
    @Query("DELETE FROM app_state") suspend fun clear()
}
@Database(entities = [StoredState::class], version = 1, exportSchema = true)
abstract class AttendDatabase : RoomDatabase() {
    abstract fun stateDao(): StateDao
    companion object {
        fun open(context: Context) = Room.databaseBuilder(context, AttendDatabase::class.java, "attend.db").build()
    }
}

data class AppData(
    val schedule: List<WeekSession> = SeedTimetable.sessions,
    val rules: List<CalendarException> = CollegeCalendar.confirmedClosures,
    val targetBps: Int = 7500,
    val teachingEnd: LocalDate? = null,
    val entries: List<AttendanceEntry> = emptyList(),
    val manualBaseline: Baseline? = null,
    val trackingStarted: LocalDate? = null,
    val activity: List<ActivityEntry> = emptyList(),
    val lecturesPerHour: Int = 1,
    val classLink: ClassLink? = null,
    val routineVersions: List<RoutineVersion> = emptyList(),
    val appliedRoutineVersion: Int? = null,
    val dismissedRoutineVersion: Int? = null,
    val markingStyle: MarkingStyle = MarkingStyle.MARK_EACH,
    val assumePresentFrom: LocalDate? = null,
    val hiddenSubjects: Set<String> = emptySet(),
    val reminderTime: LocalTime? = null,
    val setupDone: Boolean = false,
) {
    /**
     * Store the latest shared versions and apply the one in force today only when it is the first after joining or one
     * this user published. Classmates' changes wait in [pendingRoutine]. Past marks keep their own session snapshots.
     */
    fun withRoutine(link: ClassLink, versions: List<RoutineVersion>, today: LocalDate = LocalDate.now()): AppData {
        val kept = versions.sortedByDescending { it.version }.take(RoutineClient.HISTORY_LIMIT)
        val apply = Routines.autoApply(kept, appliedRoutineVersion, today)
        return copy(classLink = link, routineVersions = kept, schedule = apply?.sessions ?: schedule, appliedRoutineVersion = apply?.version ?: appliedRoutineVersion)
    }
    fun pendingRoutine(today: LocalDate = LocalDate.now()) = Routines.pending(routineVersions, appliedRoutineVersion, dismissedRoutineVersion, today)
    fun acceptRoutine(version: RoutineVersion) = copy(schedule = version.sessions, appliedRoutineVersion = version.version)
    fun withoutClass() = copy(classLink = null, routineVersions = emptyList(), appliedRoutineVersion = null, dismissedRoutineVersion = null)
    val sheetBaseline: Baseline? get() = manualBaseline?.let { it.copy(termEnd = teachingEnd ?: it.termEnd) }
    /** The weekly timetable minus subjects this student doesn't take. */
    val mySchedule: List<WeekSession> get() = schedule.filter { it.subject !in hiddenSubjects }
    private val assumeFrom: LocalDate? get() = assumePresentFrom.takeIf { markingStyle == MarkingStyle.ASSUME_PRESENT }
    fun tracking(today: LocalDate = LocalDate.now(), now: LocalTime = LocalTime.now()) =
        AttendanceTracker.summarize(sheetBaseline, entries, schedule, rules, today, trackingStarted, assumeFrom, now, hiddenSubjects)

    /** The mark a period shows without a tap (present by default), or null. */
    fun assumedMark(date: LocalDate, session: WeekSession, today: LocalDate = LocalDate.now(), now: LocalTime = LocalTime.now()) =
        AttendanceTracker.assumedMark(date, session, rules, assumeFrom, sheetBaseline?.asOf, today, now)

    private fun isNoClassRule(rule: CalendarException, date: LocalDate, sessionId: String) =
        rule.kind == ExceptionKind.CANCELLATION && rule.sessionId == sessionId && rule.start == date && rule.end == date
    /** Periods to list for a day, including ones marked "No class" so the tap can be undone. */
    fun periodsOn(date: LocalDate): List<DatedSession> {
        val listed = rules.filterNot { it.kind == ExceptionKind.CANCELLATION && it.sessionId != null && it.start == date && it.end == date }
        return AttendanceTracker.sessionsOn(date, entries, mySchedule, listed, teachingEnd ?: manualBaseline?.termEnd)
    }
    fun isNoClass(date: LocalDate, sessionId: String) = rules.any { isNoClassRule(it, date, sessionId) }
    /** "No class": the teacher didn't take it, so it leaves the held count. Any mark on that period is dropped. */
    fun withNoClass(date: LocalDate, session: WeekSession, on: Boolean): AppData = if (!on) copy(rules = rules.filterNot { isNoClassRule(it, date, session.id) })
        else copy(rules = rules.filterNot { isNoClassRule(it, date, session.id) } +
            CalendarException("noclass-$date-${session.id}", date, date, ExceptionKind.CANCELLATION, "No class · period ${session.period}", session.id),
            entries = entries.filterNot { it.date == date && it.sessionId == session.id })

    /** Switching to mark-each keeps what was counted: periods assumed present so far become saved marks. */
    fun withMarkingStyle(style: MarkingStyle, today: LocalDate = LocalDate.now(), now: LocalTime = LocalTime.now()): AppData = when {
        style == markingStyle && (style == MarkingStyle.MARK_EACH || assumePresentFrom != null) -> this
        style == MarkingStyle.ASSUME_PRESENT -> copy(markingStyle = style, assumePresentFrom = today,
            trackingStarted = trackingStarted?.let { minOf(it, today) } ?: today)
        else -> {
            val kept = AttendanceTracker.assumedEntries(sheetBaseline, entries.filter { it.date <= today }, mySchedule, rules, today, assumeFrom, now)
            copy(markingStyle = style, assumePresentFrom = null, entries = entries + kept)
        }
    }
    val baseline: Baseline? get() = tracking().baseline
    fun activityCredit(classes: Counts) = ActivityMath.credit(classes, ActivityMath.totalHours(activity), lecturesPerHour)
    fun withMarks(date: LocalDate, sessionIds: Set<String>, mark: AttendanceMark?): AppData = copy(
        trackingStarted = trackingStarted?.let { minOf(it, date) } ?: date,
        entries = entries.filterNot { it.date == date && it.sessionId in sessionIds } +
            if (mark == null) emptyList() else sessionIds.mapNotNull { id ->
                val snapshot = entries.lastOrNull { it.date == date && it.sessionId == id }?.sessionSnapshot
                    ?: schedule.firstOrNull { it.id == id }
                snapshot?.let { AttendanceEntry(date, id, mark, it) }
            },
    )
}

class AttendRepository(private val database: AttendDatabase) {
    private val mutex = Mutex()
    val data: Flow<AppData> = database.stateDao().observe().map { it?.let { value -> StateCodec.decode(value.payload) } ?: AppData() }
    suspend fun update(transform: (AppData) -> AppData) = mutex.withLock {
        database.withTransaction {
            val previous = database.stateDao().read()?.let { StateCodec.decode(it.payload) } ?: AppData()
            val updated = transform(previous)
            updated.baseline // Validate combined counts before persisting a change.
            database.stateDao().write(StoredState(payload = StateCodec.encode(updated)))
        }
    }
    suspend fun clear() = mutex.withLock { database.clearAllTablesAsync() }
    private suspend fun AttendDatabase.clearAllTablesAsync() { withTransaction { stateDao().clear() } }
}

object StateCodec {
    private fun array(values: Iterable<Any>) = JSONArray().also { a -> values.forEach(a::put) }
    private fun counts(c: Counts) = JSONObject().put("attended", c.attended).put("held", c.held)
    private fun decodeCounts(j: JSONObject) = Counts(j.getLong("attended"), j.getLong("held"))
    private fun session(s: WeekSession) = JSONObject().put("id", s.id).put("day", s.day.value).put("period", s.period).put("subject", s.subject).put("title", s.title).put("time", s.time)
    private fun decodeSession(s: JSONObject) = WeekSession(s.getString("id"), DayOfWeek.of(s.getInt("day")), s.getInt("period"), s.getString("subject"), s.getString("title"), s.getString("time"))
    private fun baseline(b: Baseline) = JSONObject().put("counts", counts(b.counts)).put("asOf", b.asOf.toString()).put("month", b.month?.let(::counts))
        .put("subjects", array(b.subjects.map { s -> JSONObject().put("code", s.code).put("name", s.name).put("counts", counts(s.counts)) }))
    private fun decodeBaseline(j: JSONObject) = Baseline(decodeCounts(j.getJSONObject("counts")),
        j.optJSONArray("subjects")?.objects()?.map { s -> SubjectAttendance(s.getString("code"), s.getString("name"), decodeCounts(s.getJSONObject("counts"))) }.orEmpty(),
        LocalDate.parse(j.getString("asOf")), month = j.optJSONObject("month")?.let(::decodeCounts))
    fun encode(data: AppData): String = JSONObject().apply {
        put("calendarRevision", 1)
        put("trackingStarted", data.trackingStarted?.toString())
        put("manualBaseline", data.manualBaseline?.let(::baseline))
        put("entries", array(data.entries.map { JSONObject().put("date", it.date.toString()).put("session", it.sessionId).put("mark", it.mark.name).put("snapshot", it.sessionSnapshot?.let(::session)) }))
        put("target", data.targetBps); put("teachingEnd", data.teachingEnd?.toString())
        put("activity", array(data.activity.map { JSONObject().put("id", it.id).put("date", it.date.toString()).put("hours", it.hours).put("label", it.label) }))
        put("lecturesPerHour", data.lecturesPerHour)
        put("markingStyle", data.markingStyle.name); put("assumeFrom", data.assumePresentFrom?.toString())
        put("hiddenSubjects", array(data.hiddenSubjects)); put("reminder", data.reminderTime?.toString()); put("setupDone", data.setupDone)
        put("classLink", data.classLink?.let { JSONObject().put("id", it.id).put("name", it.name).put("code", it.joinCode).put("owner", it.isOwner) })
        put("appliedRoutine", data.appliedRoutineVersion); put("dismissedRoutine", data.dismissedRoutineVersion)
        put("routineVersions", array(data.routineVersions.map { v -> JSONObject().put("version", v.version).put("from", v.effectiveFrom.toString())
            .put("sessions", array(v.sessions.map(::session))).put("note", v.note).put("mine", v.byMe).put("at", v.createdAt.toString()) }))
        put("schedule", array(data.schedule.map(::session)))
        put("rules", array(data.rules.map { r -> JSONObject().put("id", r.id).put("start", r.start.toString()).put("end", r.end.toString()).put("kind", r.kind.name).put("label", r.label).put("session", r.sessionId) }))
    }.toString()

    /** Reads current and v0.2 state. Retired ERP keys (reports, verified, attendanceUrl, profileUrl) are ignored and dropped on the next save. */
    fun decode(value: String): AppData {
        val j = JSONObject(value)
        return AppData(
            schedule = j.getJSONArray("schedule").objects().map(::decodeSession),
            rules = j.getJSONArray("rules").objects().map { r -> CalendarException(r.getString("id"), LocalDate.parse(r.getString("start")), LocalDate.parse(r.getString("end")), ExceptionKind.valueOf(r.getString("kind")), r.getString("label"), r.nullable("session")) }.let {
                if (j.optInt("calendarRevision", 0) == 0) it + CollegeCalendar.confirmedClosures.filter { seed -> it.none { old -> old.id == seed.id } } else it
            },
            targetBps = j.getInt("target"), teachingEnd = j.nullable("teachingEnd")?.let(LocalDate::parse),
            entries = j.optJSONArray("entries")?.objects()?.map { AttendanceEntry(LocalDate.parse(it.getString("date")), it.getString("session"), AttendanceMark.valueOf(it.getString("mark")), it.optJSONObject("snapshot")?.let(::decodeSession)) }.orEmpty(),
            manualBaseline = j.optJSONObject("manualBaseline")?.let(::decodeBaseline),
            trackingStarted = j.nullable("trackingStarted")?.let(LocalDate::parse),
            activity = j.optJSONArray("activity")?.objects()?.map { ActivityEntry(it.getString("id"), LocalDate.parse(it.getString("date")), it.getInt("hours"), it.getString("label")) }.orEmpty(),
            lecturesPerHour = j.optInt("lecturesPerHour", 1).coerceIn(1, 10),
            markingStyle = runCatching { MarkingStyle.valueOf(j.getString("markingStyle")) }.getOrDefault(MarkingStyle.MARK_EACH),
            assumePresentFrom = j.nullable("assumeFrom")?.let(LocalDate::parse),
            hiddenSubjects = j.optJSONArray("hiddenSubjects")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }.orEmpty(),
            reminderTime = j.nullable("reminder")?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
            setupDone = j.optBoolean("setupDone", false),
            // Shared-routine data comes from classmates; if it cannot be read, drop it rather than lose attendance.
            classLink = runCatching { j.optJSONObject("classLink")?.let { ClassLink(it.getString("id"), it.getString("name"), it.getString("code"), it.optBoolean("owner")) } }.getOrNull(),
            routineVersions = j.optJSONArray("routineVersions")?.objects()?.mapNotNull { v -> runCatching { RoutineVersion(v.getInt("version"), LocalDate.parse(v.getString("from")),
                v.getJSONArray("sessions").objects().map(::decodeSession), v.optString("note"), v.optBoolean("mine"), Instant.parse(v.getString("at"))) }.getOrNull() }.orEmpty(),
            appliedRoutineVersion = j.optInt("appliedRoutine", -1).takeIf { it > 0 },
            dismissedRoutineVersion = j.optInt("dismissedRoutine", -1).takeIf { it > 0 },
        )
    }
    private fun JSONObject.nullable(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null
    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
}
