package com.hoopsandkicks.tournament.data.remote

import com.hoopsandkicks.tournament.HoopsApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Holds the one room this device is currently watching (read-only). Lives outside any screen so the
 * listener survives navigating between the viewer's hub, fixtures and leaderboard. Remote tournaments are
 * never written to the local Repository.
 *
 * The code is also mirrored to [ViewerPrefs] so a viewer who presses back (see [leave]) or fully exits the
 * app (process death) can resume the same room without re-typing the code — [lastCode] is what a Home
 * screen offers as "Resume watching". The saved code is only cleared when the room turns out to be gone
 * ([JoinState.NotFound]) or the viewer deliberately calls [stopWatching].
 */
object ViewerStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private val _state = MutableStateFlow<JoinState?>(null)

    val state: StateFlow<JoinState?> get() = _state

    /** The last room code this device watched, if any — for a Home screen "Resume watching" shortcut. */
    fun lastCode(): String? = HoopsApp.viewerPrefs.load()

    fun join(code: String) {
        job?.cancel()
        _state.value = JoinState.Connecting
        HoopsApp.viewerPrefs.save(code)
        job = scope.launch {
            try {
                RemoteSync.instance.joinRoom(code).collect {
                    _state.value = it
                    // The code was wrong or the organiser has stopped hosting: nothing to resume later.
                    if (it is JoinState.NotFound) HoopsApp.viewerPrefs.clear()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = JoinState.Failed("Could not reach the room.")
            }
        }
    }

    /**
     * Stops listening (e.g. the viewer pressed back) but deliberately keeps the room code saved, so
     * returning to Home still offers to resume this room in one tap instead of asking for the code again.
     */
    fun leave() {
        job?.cancel()
        job = null
        _state.value = null
    }

    /** The viewer explicitly chose to stop watching this room; forget the code entirely. */
    fun stopWatching() {
        leave()
        HoopsApp.viewerPrefs.clear()
    }
}
