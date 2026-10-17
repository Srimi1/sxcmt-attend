package `in`.attend.app

import android.app.Application
import `in`.attend.app.data.AttendDatabase
import `in`.attend.app.data.AttendRepository

class AttendApplication : Application() {
    val repository by lazy { AttendRepository(AttendDatabase.open(this)) }
}
