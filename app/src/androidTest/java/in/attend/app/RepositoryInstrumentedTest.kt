package `in`.attend.app

import android.webkit.CookieManager
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import `in`.attend.app.data.*
import `in`.attend.app.portal.PortalSession
import `in`.attend.core.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
        val r = report()
        val rule = CalendarException("test", LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-06"), ExceptionKind.ABSENCE, "Planned leave", "1-1")
        repo.importReports(listOf(r), "verified-schema", PortalPolicy.DASHBOARD)
        repo.update { it.copy(rules = listOf(rule), targetBps = 8050, teachingEnd = LocalDate.parse("2026-12-10")) }
        val saved = repo.data.first()
        assertEquals(r, saved.reports.single())
        assertEquals(listOf(rule), saved.rules)
        assertEquals(8050, saved.targetBps)
        assertEquals(LocalDate.parse("2026-12-10"), saved.baseline!!.termEnd)
        assertEquals(saved, StateCodec.decode(StateCodec.encode(saved)))
    }
    @Test fun refreshIsIdempotentAndFailurePreservesSavedRecord() = runBlocking {
        val r = report()
        repo.importReports(listOf(r), "schema", PortalPolicy.DASHBOARD)
        repo.importReports(listOf(r), "schema", PortalPolicy.DASHBOARD)
        assertEquals(1, repo.data.first().reports.size)
        val bad = r.copy(start = LocalDate.parse("2026-09-15"), end = LocalDate.parse("2026-10-05"))
        assertTrue(runCatching { repo.importReports(listOf(bad), "schema", null) }.isFailure)
        assertEquals(listOf(r), repo.data.first().reports)
    }
    @Test fun repeatedRollNumbersAndNewTermsClearPriorPersonalState() = runBlocking {
        repo.importReports(listOf(report()), "old-layout", PortalPolicy.DASHBOARD)
        repo.update { it.copy(rules = listOf(CalendarException("old", LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-01"), ExceptionKind.ABSENCE, "Private plan"))) }
        val replacement = report().copy(profile = StudentProfile("Another student", "1", "5", "BCA"), counts = Counts(10, 45))
        repo.importReports(listOf(replacement), "new-layout", PortalPolicy.DASHBOARD)
        val saved = repo.data.first()
        assertEquals(listOf(replacement), saved.reports)
        assertTrue(saved.rules.isEmpty())
        assertEquals(setOf("new-layout"), saved.verifiedLayouts)
        repo.forgetIfDifferent(replacement.profile.copy(semester = "6"))
        assertNull(repo.data.first().profile)
    }
    @Test fun offlineReopenReadsSameRoomRecordsWithoutNetwork() = runBlocking {
        val file = "attendance-offline-test.db"
        context.deleteDatabase(file)
        var persistent = Room.databaseBuilder(context, AttendDatabase::class.java, file).build()
        AttendRepository(persistent).importReports(listOf(report()), "schema", PortalPolicy.DASHBOARD)
        persistent.close()
        persistent = Room.databaseBuilder(context, AttendDatabase::class.java, file).build()
        try { assertEquals(Counts(43, 45), AttendRepository(persistent).data.first().baseline!!.counts) }
        finally { persistent.close(); context.deleteDatabase(file) }
    }
    @Test fun logoutClearsRoomPersonalState() = runBlocking {
        repo.importReports(listOf(report()), "schema", PortalPolicy.DASHBOARD)
        repo.clear()
        assertEquals(AppData(), repo.data.first())
    }
    @Test fun missingOptionalIdentityMetadataCannotDeleteOfflineAttendance() = runBlocking {
        val saved = report()
        repo.importReports(listOf(saved), "schema", PortalPolicy.DASHBOARD)
        repo.forgetIfDifferent(StudentProfile("Test student", "1", "5"))
        assertEquals(saved, repo.data.first().reports.single())
        repo.importReports(listOf(saved.copy(profile = StudentProfile("Test student", "1", "5"))), "schema", PortalPolicy.DASHBOARD)
        assertEquals(saved, repo.data.first().reports.single())
    }
    @Test fun disconnectClearsSessionEvenBeforeFirstImport() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val set = CountDownLatch(1)
        instrumentation.runOnMainSync {
            CookieManager.getInstance().setCookie(PortalPolicy.LOGIN, "ATTEND_TEST_SESSION=synthetic; Secure; Path=/") { set.countDown() }
        }
        assertTrue(set.await(10, TimeUnit.SECONDS))
        val clear = CountDownLatch(1)
        instrumentation.runOnMainSync { PortalSession.clear(context) { clear.countDown() } }
        assertTrue(clear.await(10, TimeUnit.SECONDS))
        instrumentation.runOnMainSync { assertFalse(CookieManager.getInstance().hasCookies()) }
    }
    private fun report() = AttendanceSnapshot(
        StudentProfile("Test student", "1", "5", "BBA IB", "TEST-ID", "2026-27"),
        LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30"), ReportKind.PERIOD, Counts(43, 45),
        listOf(SubjectAttendance("RETAIL", "Retail Management", Counts(8, 9))),
        Instant.parse("2026-10-17T10:00:00Z"), PortalPolicy.LOGIN,
    )
}
