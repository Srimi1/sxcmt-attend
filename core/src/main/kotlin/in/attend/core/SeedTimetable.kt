package `in`.attend.core

import java.time.DayOfWeek

object SeedTimetable {
    val titles = linkedMapOf(
        "EDP" to "Entrepreneurship Development Programme", "OJT" to "OJT / Internship and Viva",
        "SAPM" to "Security Analysis & Portfolio Management", "ST_MGMT" to "Strategic Management",
        "RETAIL" to "Retail Management", "IFS" to "International Financial System",
        "PLACEMENT" to "Placement Seminar", "ACTIVITY" to "Activity", "FM" to "Foundation & Mentoring",
    )
    private val times = listOf("09:00–09:50", "09:55–10:45", "10:50–11:40", "12:10–13:00", "13:05–13:55", "14:00–14:50")
    private val days = listOf(
        listOf("EDP", "OJT", "SAPM", "ST_MGMT", "RETAIL"),
        listOf("IFS", "", "ST_MGMT", "RETAIL"),
        listOf("SAPM", "PLACEMENT", "EDP"),
        listOf("IFS", "EDP", "RETAIL", "SAPM"),
        listOf("IFS", "SAPM", "IFS", "ST_MGMT", "EDP"),
        listOf("ST_MGMT", "RETAIL", "ACTIVITY", "FM"),
    )
    val sessions: List<WeekSession> = days.flatMapIndexed { d, subjects ->
        subjects.mapIndexedNotNull { p, code ->
            if (code.isBlank()) null else WeekSession("${d + 1}-${p + 1}", DayOfWeek.of(d + 1), p + 1, code, titles.getValue(code), times[p])
        }
    }
}
