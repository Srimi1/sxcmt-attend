package `in`.attend.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import `in`.attend.app.data.AppData
import `in`.attend.app.reminder.Reminders
import `in`.attend.core.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.*

data class AttendUiState(val data: AppData = AppData(), val isDemo: Boolean = false, val loaded: Boolean = false)

class AttendViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as AttendApplication).repository
    private val routine = (application as AttendApplication).routine
    val account = MutableStateFlow(routine.email.takeIf { routine.signedIn })
    val busy = MutableStateFlow(false)
    private val demoData = MutableStateFlow<AppData?>(null)
    val ui = combine(repository.data, demoData) { real, demo -> AttendUiState(demo ?: real, demo != null, true) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AttendUiState())
    val notice = MutableStateFlow<String?>(null)
    fun update(transform: (AppData) -> AppData) {
        val demo = demoData.value
        if (demo != null) {
            runCatching { transform(demo).also { it.baseline } }
                .onSuccess { demoData.value = it }
                .onFailure { notice.value = it.message ?: "Could not save changes" }
            return
        }
        viewModelScope.launch { runCatching { repository.update(transform) }.onFailure { notice.value = it.message ?: "Could not save changes" } }
    }
    fun showDemo() { demoData.value = sampleData() }
    fun leaveDemo() { demoData.value = null }
    fun mark(date: LocalDate, sessionIds: Set<String>, mark: AttendanceMark?) {
        if (date > LocalDate.now()) return
        update { old -> old.withMarks(date, sessionIds, mark) }
    }
    fun noClass(date: LocalDate, session: WeekSession, on: Boolean) {
        if (date > LocalDate.now()) return
        update { it.withNoClass(date, session, on) }
    }
    fun setMarkingStyle(style: MarkingStyle) = update { it.withMarkingStyle(style) }
    fun setHiddenSubjects(subjects: Set<String>) = update { it.copy(hiddenSubjects = subjects) }
    fun setReminder(time: LocalTime?) {
        update { it.copy(reminderTime = time) }
        if (demoData.value == null) Reminders.schedule(getApplication(), time)
    }
    /** First-run choices; each student's setup stays on their own phone. */
    fun finishSetup(targetBps: Int, style: MarkingStyle, hidden: Set<String>, reminder: LocalTime?) {
        update { it.copy(targetBps = targetBps, hiddenSubjects = hidden, reminderTime = reminder, setupDone = true).withMarkingStyle(style) }
        Reminders.schedule(getApplication(), reminder)
    }

    fun clearData(done: () -> Unit) {
        viewModelScope.launch {
            repository.clear(); routine.forget(); Reminders.schedule(getApplication(), null); account.value = null; demoData.value = null
            notice.value = "Your attendance data was cleared from this device."; done()
        }
    }

    init {
        // Refresh the shared routine on app open; offline keeps the saved copy.
        viewModelScope.launch {
            val first = ui.first { it.loaded }
            // Force-stopping the app clears its alarms; opening it re-arms the reminder.
            Reminders.schedule(getApplication(), first.data.reminderTime)
            syncRoutine(quiet = true)
        }
    }

    private fun online(success: String? = null, block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            runCatching { block() }.onSuccess { success?.let { notice.value = it } }
                .onFailure { notice.value = it.message ?: "Something went wrong" }
            account.value = routine.email.takeIf { routine.signedIn }
            busy.value = false
        }
    }
    private suspend fun adopt(link: ClassLink) {
        val (versions, owner) = routine.history(link.id)
        repository.update { it.withRoutine(link.copy(isOwner = owner), versions) }
    }

    fun sendCode(email: String, sent: () -> Unit) = online("Code sent to $email. It expires in 5 minutes.") { routine.sendCode(email); sent() }
    fun verifyCode(email: String, code: String, done: () -> Unit) = online("Signed in") { routine.verifyCode(email, code); done() }
    fun createClass(name: String) = online("Class created. Share the join code with classmates.") {
        val data = ui.value.data
        Routines.validate(data.schedule)?.let { throw IllegalArgumentException(it) }
        adopt(routine.createClass(name, data.schedule, LocalDate.now()))
    }
    fun joinClass(code: String) = online("Joined. Your timetable now follows the class routine.") { adopt(routine.joinClass(code)) }
    fun syncRoutine(quiet: Boolean = false) {
        val link = ui.value.data.classLink ?: return
        if (!routine.signedIn || demoData.value != null) return
        if (quiet) viewModelScope.launch { runCatching { adopt(link) } } else online("Routine is up to date") { adopt(link) }
    }
    fun publishRoutine(sessions: List<WeekSession>, from: LocalDate, note: String) {
        val link = ui.value.data.classLink ?: return
        online("New routine version shared with your class") { routine.publish(link.id, sessions, from, note); adopt(link) }
    }
    fun leaveClass() {
        val link = ui.value.data.classLink ?: return
        online("You left ${link.name}. Your timetable stays on this phone.") {
            routine.leaveClass(link.id); repository.update { it.withoutClass() }
        }
    }
    fun signOut() = online("Signed out") { routine.signOut(); repository.update { it.withoutClass() } }
    fun deleteClassAccountData() = online("Removed from all classes and your account detached from routine history.") {
        routine.deleteMyClassData(); repository.update { it.withoutClass() }
    }
    fun acceptRoutine(version: RoutineVersion) { update { it.acceptRoutine(version) }; notice.value = "Timetable updated to version ${version.version}" }
    fun dismissRoutine(version: RoutineVersion) { update { it.copy(dismissedRoutineVersion = version.version) }; notice.value = "Kept your timetable. You can apply it later from Version history." }
    fun rotateCode() {
        val link = ui.value.data.classLink ?: return
        online("New join code made. The old code no longer works.") { val fresh = routine.rotateCode(link.id); repository.update { it.copy(classLink = fresh.copy(isOwner = true)) } }
    }
    companion object {
        private fun sampleData(): AppData {
            val subjects = listOf(
                SubjectAttendance("RETAIL", "Retail Management", Counts(30, 36)), SubjectAttendance("IFS", "International Financial System", Counts(29, 33)),
                SubjectAttendance("EDP", "Entrepreneurship Development Programme", Counts(34, 35)), SubjectAttendance("ST_MGMT", "Strategic Management", Counts(26, 31)),
                SubjectAttendance("SAPM", "Security Analysis & Portfolio Management", Counts(29, 32)),
            )
            val sheet = Baseline(Counts(161, 185), subjects, LocalDate.parse("2026-09-30"), month = Counts(43, 45))
            return AppData(setupDone = true, markingStyle = MarkingStyle.ASSUME_PRESENT, assumePresentFrom = LocalDate.now().minusDays(7),
                manualBaseline = sheet, activity = listOf(ActivityEntry("demo", LocalDate.parse("2026-10-10"), 6, "College fest volunteering")))
        }
    }
}
