package `in`.attend.core

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class PortalParserTest : FunSpec({
    test("only exact HTTPS ERP origin is accepted") {
        listOf("http://erp.sxcpatna.edu.in/", "https://erp.sxcpatna.edu.in.evil.test/", "https://evil.test@erp.sxcpatna.edu.in/", "javascript:alert(1)", "file:///data/private", "https://erp.sxcpatna.edu.in:8443/").forEach { PortalPolicy.allowed(it) shouldBe false }
        PortalPolicy.allowed(PortalPolicy.DASHBOARD) shouldBe true
    }
    test("password page requires sign-in and never imports a stale profile") {
        PortalParser.inspect(page().copy(hasPasswordInput = true), StudentProfile("Old", "OLD", "5")) shouldBe PortalResult.LoginRequired
    }
    test("explicit labels and dates import counts without inferring from percentages") {
        val ready = PortalParser.inspect(page()).shouldBeInstanceOf<PortalResult.Ready>()
        ready.reports.single().profile.rollNumber shouldBe "TEST-1"
        ready.reports.single().counts shouldBe Counts(43, 45)
        ready.reports.single().subjects.single().code shouldBe "RETAIL"
    }
    test("missing profile cannot be replaced by a guessed name or roll") {
        PortalParser.inspect(page().copy(fields = emptyMap())).shouldBeInstanceOf<PortalResult.Unsupported>()
    }
    test("missing report date never becomes today's date") {
        PortalParser.inspect(page().copy(text = "Attendance")).shouldBeInstanceOf<PortalResult.Unsupported>()
    }
    test("invalid date and reversed dates fail closed") {
        PortalParser.date("31/02/2026") shouldBe null
        PortalParser.inspect(page().copy(text = "30/09/2026 to 01/09/2026")).shouldBeInstanceOf<PortalResult.Unsupported>()
    }
    test("explicit report ranges accept a dash with or without surrounding spaces") {
        listOf("1.09.2026-30.09.2026", "01/09/2026 - 30/09/2026", "2026-09-01–2026-09-30").forEach {
            PortalParser.inspect(page().copy(text = it)).shouldBeInstanceOf<PortalResult.Ready>()
        }
    }
    test("unknown layout and percentage-only tables cannot silently update attendance") {
        PortalParser.inspect(page().copy(fields = emptyMap(), tables = listOf(PortalTable("", listOf(listOf("Subject", "Percentage"), listOf("Retail", "95.56"))))), StudentProfile("Test", "T", "5")).shouldBeInstanceOf<PortalResult.Unsupported>()
    }
    test("account mismatch prevents pairing one student's profile with another's attendance") {
        PortalParser.inspect(page().copy(fields = mapOf("Roll No" to "NEW")), StudentProfile("Old", "OLD", "5")).shouldBeInstanceOf<PortalResult.IdentityChanged>()
    }
    test("partially labelled semester or name changes cannot borrow the prior profile") {
        PortalParser.inspect(page().copy(fields = mapOf("Semester" to "6")), StudentProfile("Test student", "TEST-1", "5")).shouldBeInstanceOf<PortalResult.IdentityChanged>()
        PortalParser.inspect(page().copy(fields = mapOf("Student Name" to "Different student")), StudentProfile("Test student", "TEST-1", "5")).shouldBeInstanceOf<PortalResult.IdentityChanged>()
    }
    test("changed programme cannot inherit the previous ERP identifier") {
        val previous = StudentProfile("Test student", "TEST-1", "5", "BBA", "ID-A", "2026-27")
        val changed = page().copy(fields = page().fields + ("Programme" to "BCA"))
        PortalParser.inspect(changed, previous).shouldBeInstanceOf<PortalResult.IdentityChanged>()
        val profile = PortalParser.inspect(changed).shouldBeInstanceOf<PortalResult.Ready>().reports.first().profile
        profile.course shouldBe "BCA"
        profile.portalId shouldBe null
        (profile.termKey == previous.termKey) shouldBe false
        PortalParser.inspect(page().copy(fields = mapOf("Programme" to "BCA")), previous).shouldBeInstanceOf<PortalResult.IdentityChanged>()
    }
    test("failed changed-identity report invalidates a visit before the next identity-free page") {
        val visit = PortalVisit()
        visit.inspect(page().copy(tables = emptyList())).shouldBeInstanceOf<PortalResult.Profile>()
        val changed = page().copy(fields = page().fields + ("Semester" to "6"), tables = listOf(PortalTable("Monthly attendance", listOf(listOf("Subject", "Total Classes", "Present"), listOf("Total", "45", "Pending")))))
        visit.inspect(changed).shouldBeInstanceOf<PortalResult.IdentityChanged>()
        visit.profile shouldBe null
        visit.inspect(page().copy(fields = emptyMap())).shouldBeInstanceOf<PortalResult.Unsupported>()
    }
    test("partial programme change and sign-in both clear the fresh-visit identity") {
        val visit = PortalVisit()
        visit.inspect(page().copy(fields = page().fields + ("Programme" to "BBA"), tables = emptyList()))
        visit.inspect(page().copy(fields = mapOf("Programme" to "BCA"))).shouldBeInstanceOf<PortalResult.IdentityChanged>()
        visit.profile shouldBe null
        visit.inspect(page().copy(tables = emptyList()))
        visit.inspect(page().copy(hasPasswordInput = true)) shouldBe PortalResult.LoginRequired
        visit.profile shouldBe null
    }
    test("same-period tables with conflicting totals never silently select the last table") {
        val original = page()
        val conflict = original.tables.first().copy(rows = listOf(original.tables.first().rows.first(), listOf("Retail Management", "45", "12"), listOf("Total", "45", "12")))
        PortalParser.inspect(original.copy(tables = original.tables + conflict)).shouldBeInstanceOf<PortalResult.Unsupported>()
    }
    test("more than one reporting range requires selecting an individual report") {
        PortalParser.inspect(page().copy(text = "01/09/2026 to 30/09/2026\n01/08/2026 to 31/08/2026")).shouldBeInstanceOf<PortalResult.Unsupported>()
    }
    test("explicit cumulative as-of reports have cumulative scope") {
        val p = page().copy(text = "", fields = page().fields + ("As Of" to "30/09/2026"), tables = page().tables.map { it.copy(context = "Cumulative attendance") })
        PortalParser.inspect(p).shouldBeInstanceOf<PortalResult.Ready>().reports.single().kind shouldBe ReportKind.CUMULATIVE
    }
    test("invalid counts and repeated subjects block import") {
        val table = page().tables.first()
        PortalParser.inspect(page().copy(tables = listOf(table.copy(rows = listOf(table.rows[0], listOf("Retail Management", "45", "46")))))).shouldBeInstanceOf<PortalResult.Unsupported>()
        PortalParser.inspect(page().copy(tables = listOf(table.copy(rows = table.rows + listOf(table.rows[1]))))).shouldBeInstanceOf<PortalResult.Unsupported>()
    }
    test("pending rows or totals never turn a partial subject sum into overall attendance") {
        val original = page()
        val bad = original.tables.first().copy(rows = listOf(listOf("Subject", "Total Classes", "Present"), listOf("Retail", "45", "43"), listOf("Strategic Management", "30", "Pending"), listOf("Total", "75", "Pending")))
        PortalParser.inspect(original.copy(tables = listOf(bad))).shouldBeInstanceOf<PortalResult.Unsupported>()
        PortalParser.inspect(original.copy(tables = original.tables.map { it.copy(rows = it.rows.dropLast(1)) })).shouldBeInstanceOf<PortalResult.Unsupported>()
    }
})

private fun page() = PortalPage(
    PortalPolicy.LOGIN, "Attendance", "01/09/2026 to 30/09/2026",
    mapOf("Student Name" to "Test student", "Roll Number" to "TEST-1", "Semester" to "5"),
    listOf(PortalTable("Monthly attendance", listOf(listOf("Subject", "Total Classes", "Present"), listOf("Retail Management", "45", "43"), listOf("Total", "45", "43")))),
)
