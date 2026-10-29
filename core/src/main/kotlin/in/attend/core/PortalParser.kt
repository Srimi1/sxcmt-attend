package `in`.attend.core

import java.net.URI
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

object PortalPolicy {
    const val ORIGIN = "https://erp.sxcpatna.edu.in"
    const val LOGIN = "$ORIGIN/"
    const val DASHBOARD = "$ORIGIN/DashBoard/DashBoard"
    fun allowed(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host.equals("erp.sxcpatna.edu.in", true) &&
            uri.userInfo == null && uri.port in listOf(-1, 443)
    }.getOrDefault(false)
}

data class PortalTable(val context: String, val rows: List<List<String>>)
data class PortalLink(val label: String, val url: String)
data class PortalPage(
    val url: String, val title: String, val text: String, val fields: Map<String, String>,
    val tables: List<PortalTable>, val links: List<PortalLink> = emptyList(), val hasPasswordInput: Boolean = false,
)
sealed interface PortalResult {
    data object LoginRequired : PortalResult
    data class Profile(val profile: StudentProfile) : PortalResult
    data class Ready(val reports: List<AttendanceSnapshot>) : PortalResult
    data class IdentityChanged(val reason: String) : PortalResult
    data class Unsupported(val reason: String) : PortalResult
}

/** Conservative label-based prototype. Live ERP support requires student verification of an import. */
object PortalParser {
    private fun norm(s: String) = s.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
    private val heldLabels = setOf("totalclasses", "totalclassesheld", "classesheld", "held", "conducted", "totallectures", "totalperiods", "conductedclasses", "classesconducted", "totalclassesconducted")
    private val attendedLabels = setOf("attended", "present", "classesattended", "totalpresent", "attendedclasses", "lecturesattended", "presentclasses", "classespresent", "totalclassesattended")
    private val subjectLabels = setOf("subject", "subjectname", "course", "coursename", "paper", "papername")
    private val dateToken = "(?:\\d{4}-\\d{2}-\\d{2}|\\d{1,2}[./-]\\d{1,2}[./-]\\d{4})"
    private val rangeRegex = Regex("($dateToken)\\s*(?:to|–|—|-)\\s*($dateToken)", RegexOption.IGNORE_CASE)
    private val dateFormats = listOf("uuuu-MM-dd", "d/M/uuuu", "d-M-uuuu", "d.M.uuuu", "d MMM uuuu", "d-MMM-uuuu", "d MMMM uuuu").map {
        DateTimeFormatter.ofPattern(it, Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT)
    }
    fun date(text: String): LocalDate? = dateFormats.firstNotNullOfOrNull { f -> runCatching { LocalDate.parse(text.trim(), f) }.getOrNull() }

    fun inspect(page: PortalPage, observedProfile: StudentProfile? = null, fetchedAt: Instant = Instant.now()): PortalResult {
        if (!PortalPolicy.allowed(page.url)) return PortalResult.Unsupported("Only the official HTTPS ERP portal can be imported.")
        if (page.hasPasswordInput) return PortalResult.LoginRequired
        val fields = linkedMapOf<String, String>()
        page.fields.forEach { (k, v) -> fields[norm(k)] = v.trim() }
        page.tables.flatMap { it.rows }.filter { it.size == 2 }.forEach { row -> fields.putIfAbsent(norm(row[0]), row[1].trim()) }
        page.text.lines().map { it.trim() }.filter { it.contains(':') }.forEach { line ->
            val pair = line.split(':', limit = 2)
            fields.putIfAbsent(norm(pair[0]), pair[1].trim())
        }
        fun field(vararg labels: String): String? = labels.firstNotNullOfOrNull { fields[norm(it)]?.takeIf { v -> v.isNotBlank() } }
        val name = field("Student Name", "Name of Student", "Name")
        val roll = field("Roll Number", "Roll No", "Roll", "Class Roll No")
        val semester = field("Semester", "Current Semester", "Sem")
        val ownProfile = if (name != null && roll != null && semester != null) {
            StudentProfile(name.take(200), roll.take(100), semester.take(100), field("Programme", "Program", "Course")?.take(200).orEmpty(),
                field("Student ID", "Admission Number", "Registration Number"), field("Academic Year", "Session"))
        } else null
        if (ownProfile != null && observedProfile != null && StudentIdentity.conflicts(observedProfile, ownProfile)) {
            return PortalResult.IdentityChanged("A different student, programme or semester is visible. Read the complete profile again before importing attendance.")
        }
        // Never borrow cached identity; caller may only supply a profile seen during this fresh portal visit.
        fun same(a: String, b: String) = a.trim().replace(Regex("\\s+"), " ").equals(b.trim().replace(Regex("\\s+"), " "), true)
        if (observedProfile != null && ownProfile == null &&
            ((roll != null && !same(roll, observedProfile.rollNumber)) ||
             (name != null && !same(name, observedProfile.name)) ||
             (semester != null && !same(semester, observedProfile.semester)) ||
             (field("Programme", "Program", "Course")?.let { observedProfile.course.isNotBlank() && !same(it, observedProfile.course) } == true) ||
             (field("Academic Year", "Session")?.let { !same(it, observedProfile.academicYear.orEmpty()) } == true) ||
             (field("Student ID", "Admission Number", "Registration Number")?.let { !same(it, observedProfile.portalId.orEmpty()) } == true))) {
            return PortalResult.IdentityChanged("Student, programme or semester changed. Open your complete ERP profile before importing attendance.")
        }
        val profile = if (ownProfile != null && observedProfile != null) {
            StudentIdentity.reconcile(observedProfile, ownProfile)
        } else ownProfile ?: observedProfile ?: return PortalResult.Unsupported("Open your ERP profile first. Name, roll number and semester must be visible as labelled fields.")
        val periodText = field("Reporting Period", "Date Range", "Period", "Attendance Period") ?: page.text
        val ranges = rangeRegex.findAll(periodText).mapNotNull { match ->
            val start = date(match.groupValues[1]); val end = date(match.groupValues[2])
            if (start != null && end != null) start to end else null
        }.distinct().toList()
        if (ranges.size > 1) return PortalResult.Unsupported("More than one reporting range is visible. Open one attendance report at a time.")
        val range = ranges.singleOrNull()
        val explicitAsOf = field("As Of", "As On", "Attendance Up To", "Attendance Upto", "Report Date")?.let(::date)
        val end = range?.second ?: explicitAsOf
        val reports = mutableListOf<AttendanceSnapshot>()
        try {
            for (table in page.tables) {
                val headerIndex = table.rows.indexOfFirst { row ->
                    row.any { norm(it) in heldLabels } && row.any { norm(it) in attendedLabels }
                }
                if (headerIndex < 0) continue
                val header = table.rows[headerIndex].map(::norm)
                val heldIndex = header.indexOfFirst { it in heldLabels }
                val attendedIndex = header.indexOfFirst { it in attendedLabels }
                val subjectIndex = header.indexOfFirst { it in subjectLabels }
                val cumulative = Regex("\\bcumulative\\b|\\bsemester attendance\\b", RegexOption.IGNORE_CASE).containsMatchIn(table.context)
                val reportEnd = end ?: return PortalResult.Unsupported("Reporting date is missing. Refresh time cannot substitute for the attendance date.")
                if (range == null && !cumulative) return PortalResult.Unsupported("Report needs an explicit date range or an explicitly cumulative total with an as-of date.")
                val rows = table.rows.drop(headerIndex + 1).filter { row -> row.any { it.isNotBlank() } && row.map(::norm) != header }
                val subjects = mutableListOf<SubjectAttendance>()
                var total: Counts? = null
                for (row in rows) {
                    require(row.size > maxOf(heldIndex, attendedIndex)) { "An attendance row is incomplete. Saved attendance was kept." }
                    val h = requireNotNull(wholeCount(row[heldIndex])) { "A held-class count is missing or pending. Wait for the complete report." }
                    val a = requireNotNull(wholeCount(row[attendedIndex])) { "An attended-class count is missing or pending. Wait for the complete report." }
                    val counts = Counts(a, h)
                    val label = row.getOrNull(subjectIndex).orEmpty().trim()
                    if (norm(label) in setOf("total", "overall", "grandtotal", "cumulativetotal") || subjectIndex < 0) {
                        require(total == null) { "Multiple overall totals are visible" }
                        total = counts
                    } else if (label.isNotBlank()) {
                        subjects += SubjectAttendance(SeedTimetable.canonicalSubject(label), label, counts)
                    }
                }
                if (total == null) return PortalResult.Unsupported("An explicit overall attended/held total is needed. Subject rows alone may be incomplete.")
                if (subjects.isNotEmpty()) {
                    require(subjects.sumOf { it.counts.held } <= total.held && subjects.sumOf { it.counts.attended } <= total.attended) { "Subject counts exceed the reported total" }
                }
                reports += AttendanceSnapshot(
                    profile, range?.first, reportEnd, if (cumulative) ReportKind.CUMULATIVE else ReportKind.PERIOD,
                    total, subjects, fetchedAt, page.url,
                    field("Semester End Date", "Teaching End Date")?.let(::date),
                )
            }
            if (reports.isEmpty()) {
                val a = field("Classes Attended", "Total Present")?.let(::wholeCount)
                val h = field("Total Classes Held", "Classes Held")?.let(::wholeCount)
                if (a != null && h != null && end != null) {
                    val cumulative = page.title.contains("cumulative", true) || field("Report Type")?.equals("cumulative", true) == true
                    if (range == null && !cumulative) return PortalResult.Unsupported("Summary does not identify a reporting range or cumulative scope.")
                    reports += AttendanceSnapshot(profile, range?.first, end, if (cumulative) ReportKind.CUMULATIVE else ReportKind.PERIOD, Counts(a, h), emptyList(), fetchedAt, page.url)
                }
            }
            if (reports.isNotEmpty()) { return PortalResult.Ready(Ledger.merge(emptyList(), reports)) }
        } catch (e: IllegalArgumentException) {
            return PortalResult.Unsupported(e.message ?: "Attendance values failed validation.")
        }
        return if (ownProfile != null) PortalResult.Profile(ownProfile) else PortalResult.Unsupported("This report layout is not supported yet. No attendance was changed. Open a table showing attended and held class counts.")
    }

    private fun wholeCount(value: String): Long? = value.trim().takeIf { it.matches(Regex("\\d{1,8}")) }?.toLongOrNull()
}

/** Identity belongs to this fresh portal visit, never to the offline attendance cache. */
class PortalVisit {
    var profile: StudentProfile? = null
        private set

    fun reset() { profile = null }

    fun inspect(page: PortalPage, fetchedAt: Instant = Instant.now()): PortalResult {
        val result = PortalParser.inspect(page, profile, fetchedAt)
        when (result) {
            PortalResult.LoginRequired, is PortalResult.IdentityChanged -> profile = null
            is PortalResult.Profile -> profile = result.profile
            is PortalResult.Ready -> profile = result.reports.first().profile
            is PortalResult.Unsupported -> Unit
        }
        return result
    }
}
