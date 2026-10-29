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
    val reports: List<AttendanceSnapshot> = emptyList(),
    val schedule: List<WeekSession> = SeedTimetable.sessions,
    val rules: List<CalendarException> = CollegeCalendar.confirmedClosures,
    val targetBps: Int = 7500,
    val verifiedLayouts: Set<String> = emptySet(),
    val attendanceUrl: String? = null,
    val profileUrl: String? = null,
    val teachingEnd: LocalDate? = null,
    val entries: List<AttendanceEntry> = emptyList(),
    val manualBaseline: Baseline? = null,
    val trackingStarted: LocalDate? = null,
) {
    val profile: StudentProfile? get() = reports.lastOrNull()?.profile
    val reportBaseline: Baseline? get() = (manualBaseline ?: Ledger.baseline(reports))?.let { it.copy(termEnd = teachingEnd ?: it.termEnd) }
    fun tracking(today: LocalDate = LocalDate.now()) = AttendanceTracker.summarize(reportBaseline, entries, schedule, rules, today, trackingStarted)
    val baseline: Baseline? get() = tracking().baseline
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
    suspend fun importReports(reports: List<AttendanceSnapshot>, signature: String, profileUrl: String?) = update { previous ->
        val switched = previous.profile?.let { StudentIdentity.conflicts(it, reports.first().profile) } ?: false
        val clean = if (switched) AppData() else previous
        clean.copy(reports = Ledger.merge(clean.reports, reports), manualBaseline = null, verifiedLayouts = clean.verifiedLayouts + signature,
            attendanceUrl = reports.last().sourceUrl, profileUrl = profileUrl?.takeIf(PortalPolicy::allowed))
    }
    suspend fun forgetIfDifferent(profile: StudentProfile) = update { old ->
        val saved = old.profile
        when {
            saved == null -> old
            StudentIdentity.conflicts(saved, profile) -> AppData()
            else -> old.copy(reports = old.reports.map { it.copy(profile = StudentIdentity.reconcile(saved, profile)) })
        }
    }
    suspend fun clear() = mutex.withLock { database.clearAllTablesAsync() }
    private suspend fun AttendDatabase.clearAllTablesAsync() { withTransaction { stateDao().clear() } }
}

object StateCodec {
    private fun array(values: Iterable<Any>) = JSONArray().also { a -> values.forEach(a::put) }
    private fun profile(p: StudentProfile) = JSONObject().put("name", p.name).put("roll", p.rollNumber).put("semester", p.semester).put("course", p.course).put("portalId", p.portalId).put("academicYear", p.academicYear)
    private fun counts(c: Counts) = JSONObject().put("attended", c.attended).put("held", c.held)
    private fun decodeCounts(j: JSONObject) = Counts(j.getLong("attended"), j.getLong("held"))
    private fun session(s: WeekSession) = JSONObject().put("id", s.id).put("day", s.day.value).put("period", s.period).put("subject", s.subject).put("title", s.title).put("time", s.time)
    private fun decodeSession(s: JSONObject) = WeekSession(s.getString("id"), DayOfWeek.of(s.getInt("day")), s.getInt("period"), s.getString("subject"), s.getString("title"), s.getString("time"))
    fun encode(data: AppData): String = JSONObject().apply {
        put("calendarRevision", 1)
        put("trackingStarted", data.trackingStarted?.toString())
        put("manualBaseline", data.manualBaseline?.let { JSONObject().put("counts", counts(it.counts)).put("asOf", it.asOf.toString()) })
        put("entries", array(data.entries.map { JSONObject().put("date", it.date.toString()).put("session", it.sessionId).put("mark", it.mark.name).put("snapshot", it.sessionSnapshot?.let(::session)) }))
        put("target", data.targetBps); put("attendanceUrl", data.attendanceUrl); put("profileUrl", data.profileUrl); put("teachingEnd", data.teachingEnd?.toString())
        put("verified", array(data.verifiedLayouts))
        put("reports", array(data.reports.map { r -> JSONObject().apply {
            put("profile", profile(r.profile)); put("start", r.start?.toString()); put("end", r.end.toString()); put("kind", r.kind.name)
            put("counts", counts(r.counts)); put("fetched", r.fetchedAt.toString()); put("url", r.sourceUrl); put("termEnd", r.termEnd?.toString())
            put("subjects", array(r.subjects.map { s -> JSONObject().put("code", s.code).put("name", s.name).put("counts", counts(s.counts)) }))
        } }))
        put("schedule", array(data.schedule.map { s -> JSONObject().put("id", s.id).put("day", s.day.value).put("period", s.period).put("subject", s.subject).put("title", s.title).put("time", s.time) }))
        put("rules", array(data.rules.map { r -> JSONObject().put("id", r.id).put("start", r.start.toString()).put("end", r.end.toString()).put("kind", r.kind.name).put("label", r.label).put("session", r.sessionId) }))
    }.toString()

    fun decode(value: String): AppData {
        val j = JSONObject(value)
        return AppData(
            reports = j.getJSONArray("reports").objects().map { r ->
                val p = r.getJSONObject("profile")
                AttendanceSnapshot(StudentProfile(p.getString("name"), p.getString("roll"), p.getString("semester"), p.optString("course"), p.nullable("portalId"), p.nullable("academicYear")), r.nullable("start")?.let(LocalDate::parse), LocalDate.parse(r.getString("end")), ReportKind.valueOf(r.getString("kind")), decodeCounts(r.getJSONObject("counts")),
                    r.getJSONArray("subjects").objects().map { s -> SubjectAttendance(s.getString("code"), s.getString("name"), decodeCounts(s.getJSONObject("counts"))) },
                    Instant.parse(r.getString("fetched")), r.getString("url"), r.nullable("termEnd")?.let(LocalDate::parse))
            },
            schedule = j.getJSONArray("schedule").objects().map { s -> WeekSession(s.getString("id"), DayOfWeek.of(s.getInt("day")), s.getInt("period"), s.getString("subject"), s.getString("title"), s.getString("time")) },
            rules = j.getJSONArray("rules").objects().map { r -> CalendarException(r.getString("id"), LocalDate.parse(r.getString("start")), LocalDate.parse(r.getString("end")), ExceptionKind.valueOf(r.getString("kind")), r.getString("label"), r.nullable("session")) }.let {
                if (j.optInt("calendarRevision", 0) == 0) it + CollegeCalendar.confirmedClosures.filter { seed -> it.none { old -> old.id == seed.id } } else it
            },
            targetBps = j.getInt("target"), verifiedLayouts = j.getJSONArray("verified").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() },
            attendanceUrl = j.nullable("attendanceUrl"), profileUrl = j.nullable("profileUrl"), teachingEnd = j.nullable("teachingEnd")?.let(LocalDate::parse),
            entries = j.optJSONArray("entries")?.objects()?.map { AttendanceEntry(LocalDate.parse(it.getString("date")), it.getString("session"), AttendanceMark.valueOf(it.getString("mark")), it.optJSONObject("snapshot")?.let(::decodeSession)) }.orEmpty(),
            manualBaseline = j.optJSONObject("manualBaseline")?.let { Baseline(decodeCounts(it.getJSONObject("counts")), emptyList(), LocalDate.parse(it.getString("asOf"))) },
            trackingStarted = j.nullable("trackingStarted")?.let(LocalDate::parse),
        )
    }
    private fun JSONObject.nullable(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null
    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
}
