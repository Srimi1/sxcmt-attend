package `in`.attend.app.ui

import androidx.compose.runtime.compositionLocalOf
import java.time.LocalDateTime

/** One lifecycle-aware clock snapshot keeps all time-dependent attendance on screen consistent. */
val LocalAttendanceTime = compositionLocalOf { LocalDateTime.now() }
