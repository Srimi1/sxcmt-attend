package `in`.attend.app

import android.app.Application
import `in`.attend.app.data.AttendDatabase
import `in`.attend.app.data.AttendRepository
import `in`.attend.app.data.RoutineClient

class AttendApplication : Application() {
    val repository by lazy { AttendRepository(AttendDatabase.open(this)) }
    val routine by lazy { RoutineClient(this) }
}
