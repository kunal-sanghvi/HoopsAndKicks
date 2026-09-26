package com.hoopsandkicks.tournament.data

import android.content.Context
import com.hoopsandkicks.tournament.data.remote.RemoteSync
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Local-only storage: one JSON file per tournament in the app's private files dir.
 * State is held in memory (StateFlow) and written to disk on a single background thread.
 */
class Repository(context: Context) {
    private val dir = File(context.filesDir, "tournaments").apply { mkdirs() }
    private val io = Executors.newSingleThreadExecutor()
    private val state = MutableStateFlow(load())

    val tournaments: StateFlow<List<Tournament>> get() = state

    private fun load(): List<Tournament> {
        val files = dir.listFiles { f -> f.extension == "json" } ?: return emptyList()
        val out = ArrayList<Tournament>()
        for (f in files) {
            try {
                // Re-slot on load (pure and deterministic; a no-op without a start time) so files saved by an
                // older slotting rule show the current, stage-gated schedule and window checks straight away.
                out.add(Scheduler.scheduleTimes(Json.fromJson(JSONObject(f.readText()))))
            } catch (e: Exception) {
                // Skip unreadable files rather than crashing the app.
            }
        }
        return out.sortedByDescending { it.createdAt }
    }

    fun get(id: String): Tournament? = state.value.firstOrNull { it.id == id }

    fun save(t: Tournament) {
        val list = state.value.toMutableList()
        val idx = list.indexOfFirst { it.id == t.id }
        val prev = if (idx >= 0) list[idx] else null
        if (idx >= 0) list[idx] = t else list.add(0, t)
        state.value = list
        // Optional live hosting: a strict no-op unless this tournament has a room code.
        if (t.roomCode != null) {
            try {
                RemoteSync.instance.onTournamentSaved(prev, t)
            } catch (e: Exception) {
                // Remote sync must never break local saving.
            }
        }
        val json = Json.toJson(t).toString()
        io.execute {
            try {
                val tmp = File(dir, "${t.id}.tmp")
                tmp.writeText(json)
                tmp.renameTo(File(dir, "${t.id}.json"))
            } catch (e: Exception) {
                // Ignore write failures; the in-memory copy is still valid.
            }
        }
    }

    fun mutate(id: String, block: (Tournament) -> Tournament) {
        val cur = get(id) ?: return
        save(block(cur))
    }

    fun updateMatch(tid: String, mid: String, block: (Match) -> Match) {
        mutate(tid) { t ->
            t.copy(matches = t.matches.map { if (it.id == mid) block(it) else it })
        }
    }

    fun delete(id: String) {
        val code = get(id)?.roomCode
        if (code != null) {
            try {
                RemoteSync.instance.stopHosting(code)
            } catch (e: Exception) {
                // Ignore; local delete still proceeds.
            }
        }
        state.value = state.value.filter { it.id != id }
        io.execute { File(dir, "$id.json").delete() }
    }
}
