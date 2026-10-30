package `in`.attend.app.ui

import `in`.attend.core.Counts
import `in`.attend.core.SubjectAttendance

/** Collapsing the subject editor must not discard its values; only completely blank rows are omitted. */
internal fun parseSheetSubjects(
    subjects: List<Pair<String, String>>,
    inputs: Map<String, Pair<String, String>>,
): Result<List<SubjectAttendance>> = runCatching {
    subjects.mapNotNull { (code, title) ->
        val (attended, held) = inputs[code] ?: return@mapNotNull null
        if (attended.isBlank() && held.isBlank()) null
        else SubjectAttendance(code, title, Counts(attended.toLong(), held.toLong()))
    }
}
