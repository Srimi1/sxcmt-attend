package `in`.attend.app.data

import android.content.Context
import `in`.attend.app.BuildConfig
import `in`.attend.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.DayOfWeek
import java.time.OffsetDateTime
import java.time.LocalDate

class RoutineException(message: String) : IOException(message)

/**
 * Shared class routine over the InsForge REST API. Only timetables and class membership leave the phone.
 * Sign-in is an emailed one-time code; the rotating refresh token lives in private app storage, excluded from backups.
 */
class RoutineClient(context: Context) {
    private val prefs = context.getSharedPreferences("routine_session", Context.MODE_PRIVATE)
    private val base = BuildConfig.ROUTINE_BASE_URL.trimEnd('/')
    private val lock = Mutex()
    @Volatile private var accessToken: String? = null

    val email: String? get() = prefs.getString("email", null)
    val userId: String? get() = prefs.getString("user", null)
    val signedIn: Boolean get() = prefs.getString("refresh", null) != null

    suspend fun sendCode(email: String) { request("POST", "/api/auth/email/send-otp", JSONObject().put("email", email.trim()), BuildConfig.ROUTINE_ANON_KEY) }

    suspend fun verifyCode(email: String, code: String) {
        val body = JSONObject().put("method", "otp").put("email", email.trim()).put("otp", code.trim())
        val session = request("POST", "/api/auth/sessions?client_type=mobile", body, BuildConfig.ROUTINE_ANON_KEY) as JSONObject
        store(session, email.trim())
    }

    /** Revokes the session on the server when reachable, then forgets it locally either way. */
    suspend fun signOut() {
        val refresh = prefs.getString("refresh", null)
        if (refresh != null) runCatching {
            request("POST", "/api/auth/logout?client_type=mobile", JSONObject().put("refreshToken", refresh), accessToken ?: BuildConfig.ROUTINE_ANON_KEY)
        }
        forget()
    }

    fun forget() { accessToken = null; prefs.edit().clear().apply() }

    suspend fun createClass(name: String, sessions: List<WeekSession>, effectiveFrom: LocalDate): ClassLink =
        link(rpc("create_class", JSONObject().put("p_name", name.trim()).put("p_sessions", encode(sessions)).put("p_effective_from", effectiveFrom.toString())) as JSONObject)

    suspend fun joinClass(code: String): ClassLink {
        val found = rpc("join_class", JSONObject().put("p_code", code.trim().uppercase())) as? JSONObject
        if (found == null || found.isNull("id")) throw RoutineException("No class has that code")
        return link(found)
    }

    /** Creator only: replaces a leaked join code. Members who already joined stay. */
    suspend fun rotateCode(classId: String): ClassLink = link(rpc("rotate_join_code", JSONObject().put("p_class", classId)) as JSONObject)

    suspend fun leaveClass(classId: String) { rpc("leave_class", JSONObject().put("p_class", classId)) }

    suspend fun publish(classId: String, sessions: List<WeekSession>, effectiveFrom: LocalDate, note: String) {
        Routines.validate(sessions)?.let { throw RoutineException(it) }
        rpc("publish_routine", JSONObject().put("p_class", classId).put("p_effective_from", effectiveFrom.toString())
            .put("p_sessions", encode(sessions)).put("p_note", note.take(200)))
    }

    /** Removes this account from every class and detaches its name from published versions, then signs out. */
    suspend fun deleteMyClassData() { rpc("delete_my_class_data", JSONObject()); signOut() }

    /**
     * The newest [HISTORY_LIMIT] versions and whether this user created the class. A version that fails to decode or
     * breaks the timetable rules is skipped, so one bad entry cannot stop the rest of the class from syncing.
     */
    suspend fun history(classId: String): Pair<List<RoutineVersion>, Boolean> {
        val rows = rpc("routine_history", JSONObject().put("p_class", classId).put("p_limit", HISTORY_LIMIT)) as? JSONArray ?: JSONArray()
        val objects = (0 until rows.length()).mapNotNull { rows.optJSONObject(it) }
        val versions = objects.mapNotNull { r ->
            runCatching {
                RoutineVersion(r.getInt("version"), LocalDate.parse(r.getString("effective_from")), decode(r.getJSONArray("sessions")),
                    r.optString("note"), r.optBoolean("by_me"), OffsetDateTime.parse(r.getString("created_at")).toInstant())
            }.getOrNull()?.takeIf { Routines.validate(it.sessions) == null }
        }
        return versions to (objects.firstOrNull()?.optBoolean("is_owner") == true)
    }

    private suspend fun rpc(name: String, body: JSONObject): Any? = authorized("POST", "/api/database/rpc/$name", body)

    private suspend fun authorized(method: String, path: String, body: JSONObject?): Any? {
        val token = accessToken ?: refresh()
        return try { request(method, path, body, token) } catch (e: Unauthorized) {
            try { request(method, path, body, refresh()) } catch (e: Unauthorized) { throw RoutineException("Not allowed. Sign in again or rejoin the class.") }
        }
    }

    private suspend fun refresh(): String = lock.withLock {
        val token = prefs.getString("refresh", null) ?: throw RoutineException("Sign in to use the shared routine")
        val session = try {
            request("POST", "/api/auth/refresh?client_type=mobile", JSONObject().put("refreshToken", token), BuildConfig.ROUTINE_ANON_KEY) as JSONObject
        } catch (e: Unauthorized) { signOut(); throw RoutineException("Your sign-in expired. Sign in again.") }
        store(session, email.orEmpty())
        accessToken!!
    }

    private fun store(session: JSONObject, email: String) {
        accessToken = session.getString("accessToken")
        prefs.edit().putString("refresh", session.getString("refreshToken")).putString("email", email)
            .putString("user", session.getJSONObject("user").getString("id")).apply()
    }

    private class Unauthorized : IOException()

    private suspend fun request(method: String, path: String, body: JSONObject?, bearer: String): Any? = withContext(Dispatchers.IO) {
        val connection = URL(base + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000; connection.readTimeout = 20_000
            connection.setRequestProperty("Authorization", "Bearer $bearer")
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = connection.responseCode
            val text = (if (code < 400) connection.inputStream else connection.errorStream)?.use { input ->
                val bytes = input.readNBytesCompat(MAX_RESPONSE_BYTES + 1)
                if (bytes.size > MAX_RESPONSE_BYTES) throw RoutineException("The server sent too much data")
                String(bytes, Charsets.UTF_8)
            }.orEmpty()
            if (code == 401) throw Unauthorized()
            if (code >= 400) throw RoutineException(message(text) ?: "Request failed ($code)")
            when {
                text.isBlank() -> null
                text.trimStart().startsWith("[") -> JSONArray(text)
                else -> JSONObject(text)
            }
        } catch (e: IOException) {
            if (e is RoutineException || e is Unauthorized) throw e
            throw RoutineException("No connection. Your saved routine still works offline.")
        } finally { connection.disconnect() }
    }

    /** Only messages written for people are shown: our own database checks (P0001) and auth errors. */
    private fun message(text: String): String? = runCatching {
        val j = JSONObject(text)
        val code = j.optString("code")
        if (code.isNotEmpty() && code != "P0001") null else j.optString("message").takeIf { it.isNotBlank() && it.length <= 120 }
    }.getOrNull()

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (out.size() < limit) {
            val n = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }
    private fun link(j: JSONObject) = ClassLink(j.getString("id"), j.getString("name"), j.getString("join_code"))

    companion object {
        const val HISTORY_LIMIT = 10
        private const val MAX_RESPONSE_BYTES = 1_000_000
        fun encode(sessions: List<WeekSession>) = JSONArray().also { a ->
            sessions.forEach { s -> a.put(JSONObject().put("id", s.id).put("day", s.day.value).put("period", s.period).put("subject", s.subject).put("title", s.title).put("time", s.time)) }
        }
        fun decode(a: JSONArray) = (0 until a.length()).map { a.getJSONObject(it) }.map { s ->
            WeekSession(s.getString("id"), DayOfWeek.of(s.getInt("day")), s.getInt("period"), s.getString("subject"), s.getString("title"), s.getString("time"))
        }
    }
}
