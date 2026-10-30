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
    @Test fun baselineTeachingEndSurvivesSavingWithoutAnOverride() = runBlocking {
        val end = LocalDate.parse("2026-10-15")
        val baseline = sheet().copy(termEnd = end)
        repo.update { it.copy(manualBaseline = baseline, rules = emptyList()) }
        val saved = repo.data.first()
        assertEquals(baseline, saved.manualBaseline)
        assertEquals(end, saved.sheetBaseline!!.termEnd)
        assertTrue(saved.periodsOn(end.plusDays(1)).isEmpty())
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
    @Test fun personalSetupRoundTripsAndOlderStateNeedsSetup() = runBlocking {
        repo.update { it.copy(hiddenSubjects = setOf("FM"), reminderTime = LocalTime.of(17, 30), setupDone = true).withMarkingStyle(MarkingStyle.ASSUME_PRESENT, LocalDate.parse("2026-10-20")) }
        val saved = repo.data.first()
        assertEquals(MarkingStyle.ASSUME_PRESENT, saved.markingStyle)
        assertEquals(LocalDate.parse("2026-10-20"), saved.assumePresentFrom)
        assertEquals(setOf("FM"), saved.hiddenSubjects)
        assertEquals(LocalTime.of(17, 30), saved.reminderTime)
        assertTrue(saved.setupDone)
        assertEquals(saved, StateCodec.decode(StateCodec.encode(saved)))
        val v030 = JSONObject(StateCodec.encode(AppData()))
        listOf("markingStyle", "assumeFrom", "hiddenSubjects", "reminder", "setupDone").forEach(v030::remove)
        val old = StateCodec.decode(v030.toString())
        assertEquals(MarkingStyle.MARK_EACH, old.markingStyle)
        assertFalse(old.setupDone)
    }
    @Test fun leavingPresentByDefaultKeepsWhatWasCounted() {
        val friday = LocalDate.parse("2026-10-16")
        val data = AppData(rules = emptyList(), manualBaseline = Baseline(Counts(10, 10), emptyList(), friday.minusDays(1)))
            .withMarkingStyle(MarkingStyle.ASSUME_PRESENT, friday)
        val counted = data.tracking(friday, LocalTime.MAX).baseline!!.counts
        assertEquals(Counts(15, 15), counted)
        val switched = data.withMarkingStyle(MarkingStyle.MARK_EACH, friday, LocalTime.MAX)
        assertEquals(counted, switched.tracking(friday, LocalTime.MAX).baseline!!.counts)
        assertEquals(5, switched.entries.size)
        assertNull(switched.assumePresentFrom)
    }
    @Test fun noClassDropsTheMarkAndTheHeldCountAndCanBeUndone() {
        val friday = LocalDate.parse("2026-10-16")
        val slot = SeedTimetable.sessions.first { it.day == friday.dayOfWeek }
        val data = AppData(rules = emptyList(), manualBaseline = Baseline(Counts(10, 10), emptyList(), friday.minusDays(1)))
            .withMarkingStyle(MarkingStyle.ASSUME_PRESENT, friday).withMarks(friday, setOf(slot.id), AttendanceMark.ABSENT)
        val cancelled = data.withNoClass(friday, slot, true)
        assertTrue(cancelled.isNoClass(friday, slot.id))
        assertTrue(cancelled.entries.isEmpty())
        assertEquals(Counts(14, 14), cancelled.tracking(friday, LocalTime.MAX).baseline!!.counts)
        assertTrue(cancelled.periodsOn(friday).any { it.session.id == slot.id })
        assertEquals(Counts(15, 15), cancelled.withNoClass(friday, slot, false).tracking(friday, LocalTime.MAX).baseline!!.counts)
    }
    @Test fun hiddenSubjectsDoNotReappearInForecastButSavedMarksRemain() {
        val friday = LocalDate.parse("2026-10-16")
        val slot = SeedTimetable.sessions.first { it.day == friday.dayOfWeek }
        val data = AppData(rules = emptyList()).withMarks(friday, setOf(slot.id), AttendanceMark.PRESENT)
            .copy(hiddenSubjects = setOf(slot.subject))
        val base = data.tracking(friday).baseline!!
        assertEquals(Counts(1, 1), base.counts)
        val forecast = data.forecast(base, friday.plusDays(7), Scenario.ATTEND_ALL)
        assertTrue(forecast.subjects.none { it.code == slot.subject })
        assertEquals(TeachingCalendar.generate(friday.plusDays(1), friday.plusDays(7), data.mySchedule, data.rules).size, forecast.futureHeld)
        assertTrue(forecast.futureHeld < ForecastEngine.forecast(base, friday.plusDays(7), Scenario.ATTEND_ALL, data.schedule, data.rules).futureHeld)
    }
    @Test fun subjectInputParsingKeepsValuesAndRejectsPartialOrInvalidRows() {
        val subjects = listOf("A" to "Subject A", "B" to "Subject B")
        assertEquals(listOf(SubjectAttendance("A", "Subject A", Counts(3, 4))),
            `in`.attend.app.ui.parseSheetSubjects(subjects, mapOf("A" to ("3" to "4"), "B" to ("" to ""))).getOrThrow())
        assertTrue(`in`.attend.app.ui.parseSheetSubjects(subjects, mapOf("A" to ("3" to ""))).isFailure)
        assertTrue(`in`.attend.app.ui.parseSheetSubjects(subjects, mapOf("A" to ("5" to "4"))).isFailure)
    }
    @Test fun malformedCachedRoutineEntriesDoNotHideAttendance() {
        val version = RoutineVersion(1, LocalDate.parse("2026-10-01"), SeedTimetable.sessions, "", false, Instant.EPOCH)
        val data = AppData(manualBaseline = sheet(), routineVersions = listOf(version))
        val payload = JSONObject(StateCodec.encode(data))
        payload.getJSONArray("routineVersions").put(JSONObject.NULL).put("invalid").put(JSONObject().put("version", 2))
        val decoded = StateCodec.decode(payload.toString())
        assertEquals(sheet(), decoded.manualBaseline)
        assertEquals(listOf(version), decoded.routineVersions)
    }
    @Test fun timetableEditsFreezePastAssumptionsWithoutBackfillingNewPeriods() {
        val friday = LocalDate.parse("2026-10-16")
        val morning = WeekSession("old-morning", DayOfWeek.FRIDAY, 1, "OLD", "Old subject", "09:00–10:00")
        val afternoon = WeekSession("old-afternoon", DayOfWeek.FRIDAY, 2, "OLD", "Old subject", "14:00–15:00")
        val before = AppData(schedule = listOf(morning, afternoon), rules = emptyList())
            .withMarkingStyle(MarkingStyle.ASSUME_PRESENT, friday.minusDays(7))
        assertEquals(Counts(3, 3), before.tracking(friday, LocalTime.NOON).baseline!!.counts)
        val after = before.copy(schedule = listOf(morning.copy(id = "new-morning", subject = "NEW", title = "New subject"),
            afternoon.copy(id = "new-afternoon", subject = "NEW", title = "New subject")))
            .preserveCountedHistory(before, friday, LocalTime.NOON)
        assertEquals(Counts(3, 3), after.tracking(friday, LocalTime.NOON).baseline!!.counts)
        assertEquals(Counts(4, 4), after.tracking(friday, LocalTime.MAX).baseline!!.counts)
        assertEquals(3, after.entries.size)
        assertTrue(after.entries.all { it.sessionSnapshot?.subject == "OLD" })
        assertEquals(after, StateCodec.decode(StateCodec.encode(after)))
        val deleted = after.copy(schedule = emptyList()).preserveCountedHistory(after, friday, LocalTime.MAX)
        assertEquals(Counts(4, 4), deleted.tracking(friday, LocalTime.MAX).baseline!!.counts)
        assertEquals(Counts(4, 4), deleted.withMarkingStyle(MarkingStyle.MARK_EACH, friday, LocalTime.MAX)
            .tracking(friday, LocalTime.MAX).baseline!!.counts)
    }
    @Test fun switchingClassesDoesNotReuseAnotherClassesVersionCounters() {
        val today = LocalDate.parse("2026-10-16")
        val a = ClassLink("a", "Class A", "AAAAAAAAAA")
        val b = ClassLink("b", "Class B", "BBBBBBBBBB")
        val old = AppData(classLink = a, appliedRoutineVersion = 1, dismissedRoutineVersion = 2)
        val newSchedule = listOf(WeekSession("new", DayOfWeek.FRIDAY, 1, "NEW", "New", "09:00–10:00"))
        val version = RoutineVersion(1, today, newSchedule, "", false, Instant.EPOCH)
        val joined = old.withRoutine(b, listOf(version), today)
        assertEquals(newSchedule, joined.schedule)
        assertEquals(1, joined.appliedRoutineVersion)
        assertNull(joined.dismissedRoutineVersion)
    }
    @Test fun editingTimetableSnapshotsLegacyMarksBeforeReplacingTheirSubjects() {
        val friday = LocalDate.parse("2026-10-16")
        val slot = SeedTimetable.sessions.first { it.day == friday.dayOfWeek }
        val before = AppData(rules = emptyList(), entries = listOf(AttendanceEntry(friday, slot.id, AttendanceMark.PRESENT)))
        val after = before.copy(schedule = before.schedule.map { if (it.id == slot.id) it.copy(subject = "NEW", title = "New") else it })
            .preserveCountedHistory(before, friday, LocalTime.MAX)
        assertEquals(slot, after.entries.single().sessionSnapshot)
        assertEquals(slot.subject, after.tracking(friday).baseline!!.subjects.single().code)
    }
    @Test fun invalidatedRoutineResultCannotRestoreClearedClassState() = runBlocking {
        val revisions = RoutineRevision()
        val pending = revisions.current()
        val link = ClassLink("a", "Class A", "AAAAAAAAAA")
        repo.update { it.copy(classLink = link) }
        revisions.invalidate()
        repo.clear()
        // The revision check is inside the serialized update, not before its suspension point.
        repo.update { if (revisions.isCurrent(pending)) it.copy(classLink = link) else it }
        assertNull(repo.data.first().classLink)
        val current = revisions.current()
        assertTrue(revisions.isCurrent(current))
        revisions.invalidate()
        assertFalse(revisions.isCurrent(current))
    }
    @Test fun markingStyleRoundTripDoesNotForgetTimetableAssumptionCutoff() {
        val friday = LocalDate.parse("2026-10-16")
        val slot = WeekSession("old", DayOfWeek.FRIDAY, 1, "OLD", "Old", "09:00–10:00")
        val before = AppData(schedule = listOf(slot), rules = emptyList())
            .withMarkingStyle(MarkingStyle.ASSUME_PRESENT, friday)
        val edited = before.copy(schedule = listOf(slot.copy(id = "new", subject = "NEW", title = "New")))
            .preserveCountedHistory(before, friday, LocalTime.NOON)
        val roundTrip = edited.withMarkingStyle(MarkingStyle.MARK_EACH, friday, LocalTime.NOON)
            .withMarkingStyle(MarkingStyle.ASSUME_PRESENT, friday, LocalTime.NOON)
        assertEquals(edited.assumeAfter, roundTrip.assumeAfter)
        assertEquals(Counts(1, 1), roundTrip.tracking(friday, LocalTime.MAX).baseline!!.counts)
    }
    @Test fun timetableEditsInMarkEachAlsoPreventReenabledAssumptionsDuplicatingSavedSlots() {
        val friday = LocalDate.parse("2026-10-16")
        val morning = WeekSession("old", DayOfWeek.FRIDAY, 1, "OLD", "Old", "09:00–10:00")
        val before = AppData(schedule = listOf(morning), rules = emptyList())
            .withMarks(friday, setOf(morning.id), AttendanceMark.PRESENT)
        val edited = before.copy(schedule = listOf(morning.copy(id = "new"),
            morning.copy(id = "afternoon", period = 2, time = "14:00–15:00")))
            .preserveCountedHistory(before, friday, LocalTime.NOON)
        val enabled = edited.withMarkingStyle(MarkingStyle.ASSUME_PRESENT, friday, LocalTime.NOON)
        assertEquals(Counts(1, 1), enabled.tracking(friday, LocalTime.NOON).baseline!!.counts)
        assertEquals(Counts(2, 2), enabled.tracking(friday, LocalTime.MAX).baseline!!.counts)
        assertEquals(enabled, StateCodec.decode(StateCodec.encode(enabled)))
    }
    private fun sheet() = Baseline(Counts(161, 185), listOf(SubjectAttendance("RETAIL", "Retail Management", Counts(8, 9))),
        LocalDate.parse("2026-09-30"), month = Counts(43, 45))
}
