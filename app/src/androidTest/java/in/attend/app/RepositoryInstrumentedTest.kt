package `in`.attend.app

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import `in`.attend.app.data.*
import `in`.attend.core.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.*

@RunWith(AndroidJUnit4::class)
class RepositoryInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AttendDatabase
    private lateinit var repo: AttendRepository
    @Before fun create() {
        db = Room.inMemoryDatabaseBuilder(context, AttendDatabase::class.java).build()
        repo = AttendRepository(db)
    }
    @After fun close() { db.close() }

    @Test fun roomPersistsFullAttendanceAndCalendarState() = runBlocking {
        val rule = CalendarException("test", LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-06"), ExceptionKind.ABSENCE, "Planned leave", "1-1")
        repo.update { it.copy(manualBaseline = sheet(), rules = listOf(rule), targetBps = 8050, teachingEnd = LocalDate.parse("2026-12-10"),
            activity = listOf(ActivityEntry("a", LocalDate.parse("2026-10-10"), 30, "NSS")), lecturesPerHour = 2) }
        val saved = repo.data.first()
        assertEquals(sheet(), saved.manualBaseline)
        assertEquals(listOf(rule), saved.rules)
        assertEquals(8050, saved.targetBps)
        assertEquals(2, saved.lecturesPerHour)
        assertEquals(LocalDate.parse("2026-12-10"), saved.baseline!!.termEnd)
        assertEquals(saved, StateCodec.decode(StateCodec.encode(saved)))
    }
    @Test fun sheetSubjectsAndMonthTotalsSurviveSaving() = runBlocking {
        repo.update { it.copy(manualBaseline = sheet()) }
        val saved = repo.data.first().manualBaseline!!
        assertEquals(Counts(43, 45), saved.month)
        assertEquals(listOf(SubjectAttendance("RETAIL", "Retail Management", Counts(8, 9))), saved.subjects)
    }
    @Test fun offlineReopenReadsSameRoomRecords() = runBlocking {
        val file = "attendance-offline-test.db"
        context.deleteDatabase(file)
        var persistent = Room.databaseBuilder(context, AttendDatabase::class.java, file).build()
        AttendRepository(persistent).update { it.copy(manualBaseline = sheet(), rules = emptyList()) }
        persistent.close()
        persistent = Room.databaseBuilder(context, AttendDatabase::class.java, file).build()
        try { assertEquals(Counts(161, 185), AttendRepository(persistent).data.first().sheetBaseline!!.counts) }
        finally { persistent.close(); context.deleteDatabase(file) }
    }
    @Test fun clearingRemovesAllPersonalState() = runBlocking {
        repo.update { it.copy(manualBaseline = sheet(), activity = listOf(ActivityEntry("a", LocalDate.parse("2026-10-10"), 5, "Fest"))) }
        repo.clear()
        assertEquals(AppData(), repo.data.first())
    }
    @Test fun v021StateWithErpReportsStillOpens() {
        val old = org.json.JSONObject(StateCodec.encode(AppData(manualBaseline = Baseline(Counts(80, 100), emptyList(), LocalDate.parse("2026-09-30")))))
        old.put("reports", org.json.JSONArray().put(org.json.JSONObject().put("kind", "PERIOD")))
            .put("verified", org.json.JSONArray().put("sig")).put("attendanceUrl", "https://example.invalid/").put("profileUrl", JSONObject.NULL)
        listOf("activity", "lecturesPerHour").forEach(old::remove)
        val migrated = StateCodec.decode(old.toString())
        assertEquals(Counts(80, 100), migrated.manualBaseline!!.counts)
        assertTrue(migrated.activity.isEmpty())
        assertEquals(1, migrated.lecturesPerHour)
        assertFalse(StateCodec.encode(migrated).contains("reports"))
    }
    @Test fun studentMarksPersistAndCorrectAnEarlierMarkWithoutCountingTwice() = runBlocking {
        val day = LocalDate.parse("2026-10-16")
        val slots = SeedTimetable.sessions.filter { it.day == DayOfWeek.FRIDAY }
        repo.update { it.copy(manualBaseline = Baseline(Counts(75, 100), emptyList(), day.minusDays(1)),
            entries = listOf(AttendanceEntry(day, slots[0].id, AttendanceMark.PRESENT, slots[0]), AttendanceEntry(day, slots[1].id, AttendanceMark.ABSENT, slots[1]))) }
        val saved = repo.data.first()
        assertEquals(Counts(76, 102), saved.tracking(day).baseline!!.counts)
        assertEquals(saved, StateCodec.decode(StateCodec.encode(saved)))
        repo.update { it.copy(entries = it.entries.filterNot { e -> e.sessionId == slots[1].id } + AttendanceEntry(day, slots[1].id, AttendanceMark.PRESENT)) }
        assertEquals(Counts(77, 102), repo.data.first().tracking(day).baseline!!.counts)
        repo.clear()
        assertTrue(repo.data.first().entries.isEmpty())
    }
    @Test fun oldStateAddsConfirmedClosureOnceAndHonorsLaterRemoval() {
        val old = org.json.JSONObject(StateCodec.encode(AppData(rules = emptyList())))
        listOf("calendarRevision", "entries", "manualBaseline", "trackingStarted").forEach(old::remove)
        val migrated = StateCodec.decode(old.toString())
        assertEquals(CollegeCalendar.confirmedClosures, migrated.rules)
        assertEquals(migrated, StateCodec.decode(StateCodec.encode(migrated)))
        assertTrue(StateCodec.decode(StateCodec.encode(migrated.copy(rules = emptyList()))).rules.isEmpty())
    }
    @Test fun newSheetSupersedesCoveredMarksWithoutDoubleCounting() = runBlocking {
        val day = LocalDate.parse("2026-10-16")
        val slot = SeedTimetable.sessions.first { it.day == day.dayOfWeek }
        repo.update { it.copy(manualBaseline = Baseline(Counts(1, 2), emptyList(), day.minusDays(1)), rules = emptyList()).withMarks(day, setOf(slot.id), AttendanceMark.PRESENT) }
        assertEquals(Counts(2, 3), repo.data.first().tracking(day).baseline!!.counts)
        repo.update { it.copy(manualBaseline = Baseline(Counts(40, 50), emptyList(), day)) }
        assertEquals(Counts(40, 50), repo.data.first().tracking(day).baseline!!.counts)
    }
    @Test fun correctingAndClearingHistoricalMarksPreservesOriginalSubjectAfterTimetableChanges() = runBlocking {
        val day = LocalDate.parse("2026-10-16")
        val slot = SeedTimetable.sessions.first { it.day == day.dayOfWeek }
        repo.update { it.withMarks(day, setOf(slot.id), AttendanceMark.PRESENT) }
        repo.update { data -> data.copy(schedule = data.schedule.map { if (it.id == slot.id) it.copy(subject = "NEW", title = "Changed") else it }) }
        repo.update { it.withMarks(day, setOf(slot.id), AttendanceMark.ABSENT) }
        var saved = repo.data.first()
        assertEquals(slot, saved.entries.single().sessionSnapshot)
        assertEquals(slot.subject, saved.tracking(day).baseline!!.subjects.single().code)
        repo.update { data -> data.copy(schedule = data.schedule.filterNot { it.id == slot.id }) }
        repo.update { it.withMarks(day, setOf(slot.id), AttendanceMark.PRESENT) }
        saved = repo.data.first()
        assertEquals(slot, saved.entries.single().sessionSnapshot)
        assertEquals(Counts(1, 1), saved.tracking(day).baseline!!.counts)
        assertEquals(saved, StateCodec.decode(StateCodec.encode(saved)))
        repo.update { it.withMarks(day, setOf(slot.id), null) }
        assertTrue(repo.data.first().entries.isEmpty())
        assertNull(repo.data.first().tracking(day).baseline)
    }
    @Test fun invalidCombinedCountsCannotPersistAnUnreadableState() = runBlocking {
        val day = LocalDate.parse("2026-10-16")
        val slot = SeedTimetable.sessions.first { it.day == day.dayOfWeek }
        val maxCounts = Counts(10_000_000, 10_000_000)
        repo.update { it.copy(manualBaseline = Baseline(maxCounts, emptyList(), day.minusDays(1)), rules = emptyList()) }
        assertTrue(runCatching { repo.update { it.withMarks(day, setOf(slot.id), AttendanceMark.PRESENT) } }.isFailure)
        assertEquals(maxCounts, repo.data.first().baseline!!.counts)
        assertTrue(repo.data.first().entries.isEmpty())
    }
    private fun sheet() = Baseline(Counts(161, 185), listOf(SubjectAttendance("RETAIL", "Retail Management", Counts(8, 9))),
        LocalDate.parse("2026-09-30"), month = Counts(43, 45))
}
