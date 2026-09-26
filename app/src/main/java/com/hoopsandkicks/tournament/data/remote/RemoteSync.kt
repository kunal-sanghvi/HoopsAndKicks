/*
 * =====================================================================================================
 *  BUILD NOTE — READ FIRST
 *  This module depends on Firebase (Firestore + Auth) through the `com.google.gms.google-services`
 *  Gradle plugin. The app module WILL FAIL TO BUILD until you add `app/google-services.json`, which only
 *  you can generate from your own (free) Firebase console project. That failure is expected, not a bug.
 *  See README.md, section "Hosting a tournament live (optional)".
 *
 *  At runtime everything in here fails soft: with no network, a misconfigured project or no Firebase
 *  at all, calls return null / do nothing and the app keeps working fully offline.
 * =====================================================================================================
 */
package com.hoopsandkicks.tournament.data.remote

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.hoopsandkicks.tournament.data.Json
import com.hoopsandkicks.tournament.data.MatchEvent
import com.hoopsandkicks.tournament.data.MatchLog
import com.hoopsandkicks.tournament.data.Tournament
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random

/** What a read-only viewer currently sees for a room. */
sealed class JoinState {
    object Connecting : JoinState()
    object NotFound : JoinState()
    data class Failed(val message: String) : JoinState()
    data class Live(val code: String, val tournament: Tournament) : JoinState()
}

/**
 * Firestore room hosting. One device (the host) writes; anyone with the 6-character code can watch.
 *
 * Layout:
 *   rooms/{code}                                   { hostUid, tournamentId, createdAt, updatedAt, tournament: <Json.toJson map, logs stripped> }
 *   rooms/{code}/matches/{matchId}/events/{id}     one document per MatchEvent, document id = event id
 */
class RemoteSync private constructor() {

    companion object {
        val instance: RemoteSync by lazy { RemoteSync() }

        /** No 0/O or 1/I so codes can be read aloud and typed without confusion. */
        const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        const val CODE_LENGTH = 6
        private const val ROOMS = "rooms"
        private const val TIMEOUT_MS = 20_000L

        fun generateCode(rnd: Random = Random.Default): String =
            buildString { repeat(CODE_LENGTH) { append(ALPHABET[rnd.nextInt(ALPHABET.length)]) } }

        /** Upper-cases and drops anything outside the code alphabet. */
        fun normalizeCode(raw: String): String = raw.uppercase().filter { it in ALPHABET }.take(CODE_LENGTH)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val authLock = Mutex()

    private fun db(): FirebaseFirestore? = try {
        FirebaseFirestore.getInstance()
    } catch (e: Exception) {
        null // Firebase not initialised (e.g. no google-services config).
    }

    private fun auth(): FirebaseAuth? = try {
        FirebaseAuth.getInstance()
    } catch (e: Exception) {
        null
    }

    /** Anonymous sign-in (reused across launches). Returns the uid, or null on any failure. */
    suspend fun signIn(): String? = authLock.withLock {
        try {
            val a = auth() ?: return@withLock null
            a.currentUser?.uid ?: withTimeoutOrNull(TIMEOUT_MS) { a.signInAnonymously().awaitResult().user?.uid }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    // ---------------------------------------------------------------- host side

    /**
     * Creates a new room for [t] and returns its code, or null if hosting is not possible right now
     * (no Firebase config, offline, rules not set up...). The caller stores the code on the tournament.
     */
    suspend fun hostTournament(t: Tournament): String? {
        return try {
            val db = db() ?: return null
            val uid = signIn() ?: return null
            withTimeoutOrNull(TIMEOUT_MS) {
                var chosen: String? = null
                var attempts = 0
                while (chosen == null && attempts < 8) {
                    attempts += 1
                    val candidate = generateCode()
                    val existing = db.collection(ROOMS).document(candidate).get().awaitResult()
                    if (!existing.exists()) chosen = candidate
                }
                val code = chosen
                if (code == null) {
                    null
                } else {
                    val doc = HashMap<String, Any>()
                    doc["hostUid"] = uid
                    doc["tournamentId"] = t.id
                    doc["createdAt"] = FieldValue.serverTimestamp()
                    doc["updatedAt"] = FieldValue.serverTimestamp()
                    doc["tournament"] = snapshotMap(t.copy(roomCode = code))
                    db.collection(ROOMS).document(code).set(doc).awaitResult()
                    code
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** Best-effort removal of the room document. Viewers then see "room closed". */
    fun stopHosting(code: String) {
        scope.launch {
            try {
                val db = db() ?: return@launch
                if (signIn() == null) return@launch
                db.collection(ROOMS).document(code).delete()
            } catch (e: Exception) {
                // Fail soft.
            }
        }
    }

    /**
     * Writes one event to rooms/{code}/matches/{matchId}/events/{event.id}. The event's own id is the
     * document id, so re-sending is harmless (the rules reject the duplicate write; the data is already there).
     */
    fun pushEvent(code: String, matchId: String, event: MatchEvent) {
        scope.launch {
            try {
                val db = db() ?: return@launch
                if (signIn() == null) return@launch
                db.collection(ROOMS).document(code)
                    .collection("matches").document(matchId)
                    .collection("events").document(event.id)
                    .set(jsonToMap(Json.eventToJson(event)))
            } catch (e: Exception) {
                // Fail soft; Firestore also queues writes while offline.
            }
        }
    }

    /** Overwrites the room's `tournament` field (teams, schedule, results) with the current state. */
    fun pushTournamentSnapshot(code: String, t: Tournament) {
        scope.launch {
            try {
                val db = db() ?: return@launch
                if (signIn() == null) return@launch
                val upd = HashMap<String, Any>()
                upd["tournament"] = snapshotMap(t)
                upd["updatedAt"] = FieldValue.serverTimestamp()
                db.collection(ROOMS).document(code).update(upd)
            } catch (e: Exception) {
                // Fail soft.
            }
        }
    }

    /**
     * Called by the Repository after every local save of a hosted tournament. Pushes every newly
     * appended MatchEvent, and a full snapshot when non-live structure changed (teams, schedule, results).
     */
    fun onTournamentSaved(prev: Tournament?, t: Tournament) {
        val code = t.roomCode ?: return
        val fresh = prev == null || prev.roomCode != code
        if (fresh || structuralKey(prev!!) != structuralKey(t)) pushTournamentSnapshot(code, t)
        for (m in t.matches) {
            val pm = if (fresh) null else prev?.match(m.id)
            if (pm != null && pm.log === m.log) continue
            val known = pm?.log?.mapTo(HashSet()) { it.id } ?: emptySet<String>()
            m.log.forEach { if (it.id !in known) pushEvent(code, m.id, it) }
        }
    }

    /** Changes to anything in here trigger a snapshot push; live clock/score ticks do not. */
    private fun structuralKey(t: Tournament): Int = listOf<Any?>(
        t.name, t.startAt, t.endAt, t.courts, t.matchBufferMin, t.venue, t.status, t.championId, t.format, t.algorithm, t.groups, t.advance,
        t.players, t.teams, t.roomCode, t.sport,
        t.matches.map { listOf(it.id, it.teamAId, it.teamBId, it.status, it.winnerId, it.label, it.isFinal, it.scheduledAt, it.court) }
    ).hashCode()

    /** Tournament as a Firestore map. Match logs are stripped: they live in the events subcollections. */
    private fun snapshotMap(t: Tournament): Map<String, Any?> =
        jsonToMap(Json.toJson(t.copy(matches = t.matches.map { it.copy(log = emptyList()) })))

    // ---------------------------------------------------------------- viewer side

    /**
     * Read-only view of a room: listens to the room document and to every match's events
     * subcollection, and emits the tournament rebuilt from the snapshot plus the event logs.
     */
    fun joinRoom(code: String): Flow<JoinState> = callbackFlow {
        val registrations = HashMap<String, ListenerRegistration>()
        val eventsByMatch = HashMap<String, List<MatchEvent>>()
        var roomReg: ListenerRegistration? = null
        var snapshot: Tournament? = null

        fun emitLive() {
            val base = snapshot ?: return
            val rebuilt = try {
                MatchLog.applyRemote(base, eventsByMatch)
            } catch (e: Exception) {
                base
            }
            trySend(JoinState.Live(code, rebuilt))
        }

        val db = db()
        if (db == null) {
            trySend(JoinState.Failed("Live rooms are not set up in this build."))
        } else {
            trySend(JoinState.Connecting)
            try {
                val roomRef = db.collection(ROOMS).document(code)
                roomReg = roomRef.addSnapshotListener { doc, err ->
                    if (err != null) {
                        trySend(JoinState.Failed(err.message ?: "Could not reach the room."))
                        return@addSnapshotListener
                    }
                    if (doc == null || !doc.exists()) {
                        trySend(JoinState.NotFound)
                        return@addSnapshotListener
                    }
                    val parsed = try {
                        val raw = doc.get("tournament") as? Map<*, *>
                        if (raw == null) null else Json.fromJson(mapToJson(raw))
                    } catch (e: Exception) {
                        null
                    }
                    if (parsed == null) {
                        trySend(JoinState.Failed("This room's data could not be read."))
                        return@addSnapshotListener
                    }
                    snapshot = parsed
                    parsed.matches.forEach { m ->
                        if (!m.bye && !registrations.containsKey(m.id)) {
                            val mid = m.id
                            registrations[mid] = roomRef.collection("matches").document(mid).collection("events")
                                .addSnapshotListener { qs, e2 ->
                                    if (e2 != null || qs == null) return@addSnapshotListener
                                    eventsByMatch[mid] = qs.documents.mapNotNull { d ->
                                        val data = d.data ?: return@mapNotNull null
                                        try {
                                            Json.eventFromJson(mapToJson(data))
                                        } catch (e: Exception) {
                                            null
                                        }
                                    }
                                    emitLive()
                                }
                        }
                    }
                    emitLive()
                }
            } catch (e: Exception) {
                trySend(JoinState.Failed("Could not reach the room."))
            }
        }

        awaitClose {
            try {
                roomReg?.remove()
                registrations.values.forEach { it.remove() }
            } catch (e: Exception) {
                // Ignore.
            }
        }
    }
}

// -------------------------------------------------------------------- helpers

/** Awaits a Play Services Task without the extra kotlinx-coroutines-play-services dependency. */
private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) {
            cont.resume(task.result)
        } else {
            cont.resumeWithException(task.exception ?: RuntimeException("Task failed"))
        }
    }
}

internal fun jsonToMap(o: JSONObject): Map<String, Any?> {
    val out = HashMap<String, Any?>()
    val keys = o.keys()
    while (keys.hasNext()) {
        val k = keys.next()
        out[k] = jsonValue(o.opt(k))
    }
    return out
}

private fun jsonValue(v: Any?): Any? = when (v) {
    null -> null
    JSONObject.NULL -> null
    is JSONObject -> jsonToMap(v)
    is JSONArray -> (0 until v.length()).map { jsonValue(v.opt(it)) }
    else -> v
}

internal fun mapToJson(m: Map<*, *>): JSONObject {
    val o = JSONObject()
    for ((k, v) in m) {
        if (k == null) continue
        o.put(k.toString(), toJsonValue(v))
    }
    return o
}

private fun toJsonValue(v: Any?): Any? = when (v) {
    null -> JSONObject.NULL
    is Map<*, *> -> mapToJson(v)
    is List<*> -> JSONArray().also { a -> v.forEach { a.put(toJsonValue(it)) } }
    is String, is Boolean, is Int, is Long, is Double -> v
    is Number -> v.toLong()
    else -> v.toString() // e.g. Firestore Timestamp; not read back by Json.fromJson
}
