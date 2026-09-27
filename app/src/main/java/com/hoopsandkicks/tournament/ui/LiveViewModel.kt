package com.hoopsandkicks.tournament.ui

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoopsandkicks.tournament.HoopsApp
import com.hoopsandkicks.tournament.data.DRAW_NOTE
import com.hoopsandkicks.tournament.data.FormatType
import com.hoopsandkicks.tournament.data.GameFormat
import com.hoopsandkicks.tournament.data.Match
import com.hoopsandkicks.tournament.data.MatchEventType
import com.hoopsandkicks.tournament.data.MatchLog
import com.hoopsandkicks.tournament.data.MatchStatus
import com.hoopsandkicks.tournament.data.ScoreEvent
import com.hoopsandkicks.tournament.data.Scheduler
import com.hoopsandkicks.tournament.data.TStatus
import com.hoopsandkicks.tournament.data.Tournament
import com.hoopsandkicks.tournament.data.newId
import com.hoopsandkicks.tournament.data.shootout
import com.hoopsandkicks.tournament.data.shootoutNote
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Everything the match screens read from and call on the live-match state. [LiveViewModel] is the real
 * implementation; previews use a fixed fake (see PreviewSamples.kt), so the screens never need the database.
 */
interface LiveController {
    val clockMs: Long
    val running: Boolean
    val timeUp: Boolean

    /** Scorer sheet state: which side is picking a scorer (true = team A) and for how many points. */
    val sheetForA: Boolean?
    val sheetPoints: Int

    /** Substitution sheet state: which side is making a substitution (true = team A). */
    val subForA: Boolean?

    fun togglePause()
    fun pause()
    fun addBreakMinute()
    fun openSheet(forA: Boolean, points: Int)
    fun closeSheet()
    fun openSub(forA: Boolean)
    fun closeSub()
    fun score(forA: Boolean, playerId: String?, points: Int)
    fun undo()
    fun substitute(forA: Boolean, outPlayerId: String, inPlayerId: String)
    fun endPeriod()
    fun endMatchNow()
    fun startNextPeriod()
    fun shootoutAttempt(playerId: String?, made: Boolean)
    fun canReopen(): Boolean
    fun reopen()
}

/**
 * Owns the running clock and all live-match actions. Match data itself lives in the repository;
 * this class only keeps the ticking clock in memory and writes it back on every important event.
 *
 * Every action also appends a MatchEvent to Match.log (append-only side record used for live sync and
 * replay). New events are pushed to the live room, if any, by the Repository after each save.
 */
class LiveViewModel(private val tid: String, private val mid: String) : ViewModel(), LiveController {
    private val repo = HoopsApp.repo

    override var clockMs by mutableLongStateOf(0L)
        private set
    override var running by mutableStateOf(false)
        private set
    override var timeUp by mutableStateOf(false)
        private set

    /** Scorer sheet state: which side is picking a scorer (true = team A) and for how many points. */
    override var sheetForA by mutableStateOf<Boolean?>(null)
        private set
    override var sheetPoints by mutableIntStateOf(0)
        private set

    /** Substitution sheet state: which side is making a substitution (true = team A). */
    override var subForA by mutableStateOf<Boolean?>(null)
        private set

    private fun tournament(): Tournament? = repo.get(tid)
    private fun match(): Match? = tournament()?.match(mid)

    private fun fmt(t: Tournament, m: Match): GameFormat = t.format.forMatch(m)

    /** Appends one event to this match's log (see MatchLog.append). */
    private fun Match.logged(
        type: MatchEventType,
        teamId: String? = null,
        playerId: String? = null,
        points: Int? = null,
        outPlayerId: String? = null,
        inPlayerId: String? = null,
        voidsSeq: Int? = null,
        note: String? = null,
        clockSec: Int? = null,
        clockRunning: Boolean? = null,
        id: String = newId()
    ): Match = MatchLog.append(
        this, type, System.currentTimeMillis(), teamId, playerId, points,
        outPlayerId, inPlayerId, voidsSeq, note, clockSec, clockRunning, id
    )

    init {
        val m = match()
        if (m != null) {
            clockMs = (if (m.status == MatchStatus.BREAK) m.breakRemainingSec else m.remainingSec) * 1000L
            timeUp = clockMs == 0L && (m.status == MatchStatus.LIVE || m.status == MatchStatus.BREAK)
            // The local clock always starts paused here; if the saved state says "running" (app was killed
            // mid-period), record that it stopped so the log and remote viewers stay truthful.
            if (m.clockRunning && (m.status == MatchStatus.LIVE || m.status == MatchStatus.BREAK)) {
                persistClock(MatchEventType.PAUSE, "clock stopped (app restarted)")
            }
        }
        viewModelScope.launch {
            var last = SystemClock.elapsedRealtime()
            var ticks = 0
            while (true) {
                delay(100)
                val now = SystemClock.elapsedRealtime()
                val dt = now - last
                last = now
                if (running) {
                    clockMs = (clockMs - dt).coerceAtLeast(0L)
                    ticks += 1
                    if (clockMs == 0L) {
                        running = false
                        persistClock(MatchEventType.PAUSE, "time up")
                        if (isMidwayPeriodEnd()) {
                            // A half (not the last one) just ended: the break starts on its own —
                            // only ending the whole match still needs the organiser to tap something.
                            endPeriod()
                        } else {
                            timeUp = true
                        }
                    } else if (ticks % 50 == 0) {
                        persistClock()
                    }
                }
            }
        }
    }

    private fun clockSeconds(): Int = ((clockMs + 999) / 1000).toInt()

    /**
     * Writes the ticking clock back to the match. clockEpochMs/clockRunning are refreshed together with the
     * value so computeDisplaySeconds stays exact. When [event] is given, a log entry carrying the clock is appended.
     */
    private fun persistClock(event: MatchEventType? = null, note: String? = null) {
        val sec = clockSeconds()
        val isRunning = running
        val now = System.currentTimeMillis()
        repo.updateMatch(tid, mid) { m ->
            val upd = when (m.status) {
                MatchStatus.BREAK -> m.copy(breakRemainingSec = sec, clockRunning = isRunning, clockEpochMs = now)
                MatchStatus.LIVE -> m.copy(remainingSec = sec, clockRunning = isRunning, clockEpochMs = now)
                else -> m
            }
            if (event != null && upd !== m) upd.logged(event, note = note, clockSec = sec, clockRunning = isRunning) else upd
        }
    }

    // ---------- clock ----------

    override fun togglePause() {
        if (running) {
            running = false
            persistClock(MatchEventType.PAUSE)
        } else if (clockMs > 0L) {
            running = true
            timeUp = false
            persistClock(MatchEventType.RESUME)
        }
    }

    override fun pause() {
        if (running) {
            running = false
            persistClock(MatchEventType.PAUSE)
        }
    }

    override fun addBreakMinute() {
        clockMs += 60_000L
        timeUp = false
        persistClock(if (running) MatchEventType.RESUME else MatchEventType.PAUSE, "break +1 min")
    }

    // ---------- scoring ----------

    override fun openSheet(forA: Boolean, points: Int) {
        subForA = null
        sheetForA = forA
        sheetPoints = points
    }

    override fun closeSheet() {
        sheetForA = null
    }

    override fun openSub(forA: Boolean) {
        sheetForA = null
        subForA = forA
    }

    override fun closeSub() {
        subForA = null
    }

    override fun score(forA: Boolean, playerId: String?, points: Int) {
        val t = tournament() ?: return
        val m = t.match(mid) ?: return
        if (m.status != MatchStatus.LIVE) return
        val teamId = (if (forA) m.teamAId else m.teamBId) ?: return
        val f = fmt(t, m)
        // clockMs counts DOWN (time remaining in the period), but a scoring feed should read as time
        // elapsed since the period started — otherwise a basket 45s into a half shows as "04:15" left
        // rather than "00:45" in, which nobody reads at a glance.
        val periodFullMs = f.periodMin * 60_000L
        val ev = ScoreEvent(
            id = newId(), teamId = teamId, playerId = playerId, points = points,
            period = m.periodTag(f.type == FormatType.HALVES),
            clock = formatClock((periodFullMs - clockMs).coerceAtLeast(0L)),
            createdAt = System.currentTimeMillis()
        )
        val sec = clockSeconds()
        val isRunning = running
        repo.updateMatch(tid, mid) {
            it.copy(
                scoreA = if (forA) it.scoreA + points else it.scoreA,
                scoreB = if (forA) it.scoreB else it.scoreB + points,
                events = it.events + ev,
                remainingSec = sec
            ).logged(
                MatchEventType.SCORE, teamId = teamId, playerId = playerId, points = points,
                note = "${ev.period} ${ev.clock}",
                clockSec = sec,
                clockRunning = isRunning,
                id = ev.id // same id as the ScoreEvent, so undo can find the log entry it cancels
            )
        }
        sheetForA = null
    }

    /**
     * Undo. During a shootout with attempts recorded it cancels the last attempt (the match stays in the tie-breaker);
     * otherwise it cancels the last regular score, which from the tie-breaker also returns the match to live play.
     */
    override fun undo() {
        val t = tournament() ?: return
        val m = t.match(mid) ?: return
        if (m.status != MatchStatus.LIVE && m.status != MatchStatus.TIEBREAK) return
        if (m.status == MatchStatus.TIEBREAK) {
            val lastAttempt = m.shootout(t.sport).all.lastOrNull()
            if (lastAttempt != null) {
                repo.updateMatch(tid, mid) {
                    it.logged(
                        MatchEventType.VOID, teamId = lastAttempt.teamId, playerId = lastAttempt.playerId,
                        points = if (lastAttempt.made) 1 else 0, voidsSeq = lastAttempt.seq
                    )
                }
                return
            }
        }
        val last = m.events.lastOrNull() ?: return
        if (m.status == MatchStatus.TIEBREAK) {
            clockMs = 0L
            running = false
            timeUp = true
        }
        val wasTieBreak = m.status == MatchStatus.TIEBREAK
        repo.updateMatch(tid, mid) {
            val isA = last.teamId == it.teamAId
            val voided = it.log.lastOrNull { e -> e.type == MatchEventType.SCORE && e.id == last.id }
            it.copy(
                status = MatchStatus.LIVE,
                scoreA = if (isA) (it.scoreA - last.points).coerceAtLeast(0) else it.scoreA,
                scoreB = if (isA) it.scoreB else (it.scoreB - last.points).coerceAtLeast(0),
                events = it.events.dropLast(1)
            ).logged(
                MatchEventType.VOID, teamId = last.teamId, playerId = last.playerId, points = last.points,
                voidsSeq = voided?.seq,
                clockSec = if (wasTieBreak) 0 else null,
                clockRunning = if (wasTieBreak) false else null
            )
        }
    }

    // ---------- substitutions ----------

    /**
     * Swaps [outPlayerId] (on court) for [inPlayerId] (anyone else on the roster). Unlimited, and players may
     * come back on later. The scorer sheet reads the live lineup, so it reflects the change immediately.
     */
    override fun substitute(forA: Boolean, outPlayerId: String, inPlayerId: String) {
        val m = match() ?: return
        if (m.status == MatchStatus.SCHEDULED || m.status == MatchStatus.FINISHED) return
        if (outPlayerId == inPlayerId) return
        val teamId = (if (forA) m.teamAId else m.teamBId) ?: return
        repo.updateMatch(tid, mid) {
            val cur = if (forA) it.lineupA else it.lineupB
            if (outPlayerId !in cur || inPlayerId in cur) {
                it
            } else {
                val next = cur.map { p -> if (p == outPlayerId) inPlayerId else p }
                (if (forA) it.copy(lineupA = next) else it.copy(lineupB = next))
                    .logged(MatchEventType.SUB, teamId = teamId, outPlayerId = outPlayerId, inPlayerId = inPlayerId)
            }
        }
        subForA = null
    }

    // ---------- periods ----------

    /**
     * True when the clock hitting 0 just ended a halves-format period that is *not* the last one —
     * i.e. the moment [endPeriod] would start a break rather than end the match. Used by the ticking
     * clock to start that break automatically instead of waiting for the organiser to tap "End Half".
     */
    private fun isMidwayPeriodEnd(): Boolean {
        val t = tournament() ?: return false
        val m = t.match(mid) ?: return false
        if (m.status != MatchStatus.LIVE) return false
        val f = fmt(t, m)
        return f.type == FormatType.HALVES && m.period < f.periods()
    }

    override fun endPeriod() {
        val t = tournament() ?: return
        val m = t.match(mid) ?: return
        if (m.status != MatchStatus.LIVE) return
        val f = fmt(t, m)
        running = false
        sheetForA = null
        subForA = null
        if (f.type == FormatType.HALVES && m.period < f.periods()) {
            val sec = clockSeconds()
            repo.updateMatch(tid, mid) {
                it.copy(status = MatchStatus.BREAK, remainingSec = sec, breakRemainingSec = f.breakMin * 60)
                    .logged(MatchEventType.BREAK_START, clockSec = f.breakMin * 60, clockRunning = true)
            }
            clockMs = f.breakMin * 60_000L
            timeUp = false
            running = true
        } else {
            endRegulation()
        }
    }

    /** Ends the match now (or goes straight to the shootout tie-breaker if scores are level). */
    override fun endMatchNow() {
        running = false
        sheetForA = null
        subForA = null
        endRegulation()
    }

    /**
     * Full time: the leader wins. A level score is a draw in group / league matches; in knockouts, semi-finals and
     * finals it goes straight to the sport's shootout instead (no overtime / extra time).
     */
    private fun endRegulation() {
        val m = match() ?: return
        if (m.scoreA != m.scoreB) {
            val winner = (if (m.scoreA > m.scoreB) m.teamAId else m.teamBId) ?: return
            finish(winner, "")
        } else if (m.awardsPoints) {
            finishDraw()
        } else {
            repo.updateMatch(tid, mid) {
                it.copy(status = MatchStatus.TIEBREAK, remainingSec = 0)
                    .logged(MatchEventType.TIEBREAK_START, clockSec = 0, clockRunning = false)
            }
            clockMs = 0L
            timeUp = false
        }
    }

    override fun startNextPeriod() {
        val t = tournament() ?: return
        val m = t.match(mid) ?: return
        if (m.status != MatchStatus.BREAK) return
        val f = fmt(t, m)
        repo.updateMatch(tid, mid) {
            it.copy(status = MatchStatus.LIVE, period = it.period + 1, remainingSec = f.periodMin * 60, breakRemainingSec = 0)
                .logged(MatchEventType.BREAK_END)
                .logged(MatchEventType.PERIOD_START, clockSec = f.periodMin * 60, clockRunning = true)
        }
        clockMs = f.periodMin * 60_000L
        timeUp = false
        running = true
    }

    // ---------- tie-breaker (shootout) ----------

    /**
     * Records the next shootout attempt: the team due to shoot (teams alternate, A first; see Match.shootout),
     * taken by [playerId] (null when the team has no roster), [made] = made / scored. Once the shootout is decided
     * the match finishes the same way as a decisive full time, with a "Won 2–1 on free throws" style note.
     */
    override fun shootoutAttempt(playerId: String?, made: Boolean) {
        val t = tournament() ?: return
        val m = t.match(mid) ?: return
        if (m.status != MatchStatus.TIEBREAK) return
        val before = m.shootout(t.sport)
        if (before.decided) return
        val teamId = (if (before.nextIsA) m.teamAId else m.teamBId) ?: return
        repo.updateMatch(tid, mid) {
            it.logged(MatchEventType.SHOOTOUT_ATTEMPT, teamId = teamId, playerId = playerId, points = if (made) 1 else 0)
        }
        val after = match()?.shootout(t.sport) ?: return
        val winner = after.winnerId ?: return
        val winnerIsA = winner == m.teamAId
        val note = shootoutNote(
            t.sport,
            if (winnerIsA) after.madeA else after.madeB,
            if (winnerIsA) after.madeB else after.madeA
        )
        finish(winner, note)
    }

    private fun finishDraw() {
        running = false
        timeUp = false
        repo.updateMatch(tid, mid) {
            it.copy(status = MatchStatus.FINISHED, winnerId = null, tieNote = DRAW_NOTE)
                .logged(MatchEventType.MATCH_END, note = DRAW_NOTE, clockRunning = false)
        }
        repo.mutate(tid) { Scheduler.advance(it) }
    }

    private fun finish(winnerId: String, note: String) {
        running = false
        timeUp = false
        repo.updateMatch(tid, mid) {
            it.copy(status = MatchStatus.FINISHED, winnerId = winnerId, tieNote = note)
                .logged(MatchEventType.MATCH_END, teamId = winnerId, note = note, clockRunning = false)
        }
        repo.mutate(tid) { Scheduler.advance(it) }
    }

    // ---------- reopening ----------

    override fun canReopen(): Boolean {
        val t = tournament() ?: return false
        val m = t.match(mid) ?: return false
        if (m.status != MatchStatus.FINISHED) return false
        val maxFrontier = t.matches.filter { !it.bye }.maxOfOrNull { it.frontier() } ?: return false
        return m.frontier() == maxFrontier
    }

    override fun reopen() {
        if (!canReopen()) return
        repo.mutate(tid) { t ->
            t.copy(
                status = if (t.status == TStatus.COMPLETED) TStatus.ACTIVE else t.status,
                championId = null,
                matches = t.matches.map {
                    if (it.id == mid) {
                        it.copy(status = MatchStatus.LIVE, winnerId = null, tieNote = "", remainingSec = 0)
                            .logged(MatchEventType.REOPEN, clockSec = 0, clockRunning = false)
                    } else it
                }
            )
        }
        clockMs = 0L
        running = false
        timeUp = true
    }

    override fun onCleared() {
        val m = match()
        if (m != null && (m.status == MatchStatus.LIVE || m.status == MatchStatus.BREAK)) {
            // Leaving the screen stops the local clock; log it as a pause if it was running.
            val wasRunning = running
            running = false
            persistClock(if (wasRunning) MatchEventType.PAUSE else null)
        }
        super.onCleared()
    }
}
