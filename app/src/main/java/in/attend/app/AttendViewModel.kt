package `in`.attend.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import `in`.attend.app.data.AppData
import `in`.attend.core.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.*

data class AttendUiState(val data: AppData = AppData(), val isDemo: Boolean = false, val loaded: Boolean = false)

class AttendViewModel(application: Application) : AndroidViewModel(application) {
    var openingRefreshHandled = false
    private val repository = (application as AttendApplication).repository
    private val demoData = MutableStateFlow<AppData?>(null)
    val ui = combine(repository.data, demoData) { real, demo -> AttendUiState(demo ?: real, demo != null, true) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AttendUiState())
    val notice = MutableStateFlow<String?>(null)
    fun update(transform: (AppData) -> AppData) {
        val demo = demoData.value
        if (demo != null) { demoData.value = transform(demo); return }
        viewModelScope.launch { runCatching { repository.update(transform) }.onFailure { notice.value = it.message ?: "Could not save changes" } }
    }
    fun showDemo() { demoData.value = sampleData() }
    fun leaveDemo() { demoData.value = null }
    fun accept(reports: List<AttendanceSnapshot>, signature: String, profileUrl: String?, done: () -> Unit, failed: (String) -> Unit = {}) {
        viewModelScope.launch {
            runCatching { repository.importReports(reports, signature, profileUrl) }
                .onSuccess { demoData.value = null; notice.value = "Attendance imported from ERP"; done() }
                .onFailure { val message = it.message ?: "Import failed; previous records kept"; notice.value = message; failed(message) }
        }
    }
    fun observeIdentity(profile: StudentProfile) {
        viewModelScope.launch { repository.forgetIfDifferent(profile) }
    }
    fun logout(done: () -> Unit) {
        viewModelScope.launch { repository.clear(); demoData.value = null; notice.value = "Signed out. Personal records cleared."; done() }
    }
    companion object {
        private fun sampleData(): AppData {
            val p = StudentProfile("Sample student", "DEMO", "5", "BBA IB")
            val subjects = listOf(
                SubjectAttendance("RETAIL", "Retail Management", Counts(30, 36)), SubjectAttendance("IFS", "International Financial System", Counts(29, 33)),
                SubjectAttendance("EDP", "Entrepreneurship Development Programme", Counts(34, 35)), SubjectAttendance("ST_MGMT", "Strategic Management", Counts(26, 31)),
                SubjectAttendance("SAPM", "Security Analysis & Portfolio Management", Counts(29, 32)), SubjectAttendance("OJT", "OJT / Internship and Viva", Counts(7, 11)),
                SubjectAttendance("FM", "Foundation & Mentoring", Counts(6, 7)),
            )
            val r = AttendanceSnapshot(p, LocalDate.parse("2026-07-13"), LocalDate.parse("2026-09-30"), ReportKind.CUMULATIVE, Counts(161, 185), subjects, Instant.parse("2026-10-17T10:00:00Z"), "demo://sample")
            return AppData(reports = listOf(r))
        }
    }
}
