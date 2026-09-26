package com.hoopsandkicks.tournament.data

/**
 * Clock value to show right now for a clock that was set to [valueAtChange] seconds at
 * [changedAtEpochMs] and has been [running] (or not) since.
 *
 *     displaySeconds = if (running) max(0, valueAtChange - (now - changedAt) / 1000) else valueAtChange
 *
 * Elapsed time is floored at 0 so a viewer whose device clock is slightly behind the host's never
 * sees the clock jump above the value the host set.
 */
fun computeDisplaySeconds(
    valueAtChange: Int,
    changedAtEpochMs: Long,
    running: Boolean,
    nowEpochMs: Long = System.currentTimeMillis()
): Int {
    if (!running) return valueAtChange
    val elapsedSec = (nowEpochMs - changedAtEpochMs).coerceAtLeast(0L) / 1000L
    return maxOf(0L, valueAtChange - elapsedSec).toInt()
}

/**
 * Pure helpers for the append-only [Match.log].
 *
 * The live match screen still mutates Match fields directly (scores, events, clock) exactly as before;
 * every action additionally appends one [MatchEvent] through [append]. [replay] rebuilds match state
 * from the log alone and is used by read-only remote viewers.
 */
object MatchLog {
    fun nextSeq(m: Match): Int = (m.log.maxOfOrNull { it.seq } ?: 0) + 1

    /**
     * Returns [m] with one new event appended (seq = previous max + 1). When [clockRunning] is not null,
     * the match's [Match.clockRunning]/[Match.clockEpochMs] are updated too (epoch = [nowMs]).
     */
    fun append(
        m: Match,
        type: MatchEventType,
        nowMs: Long = System.currentTimeMillis(),
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
    ): Match {
        val ev = MatchEvent(
            id = id, seq = nextSeq(m), type = type, epochMs = nowMs,
            teamId = teamId, playerId = playerId, points = points,
            outPlayerId = outPlayerId, inPlayerId = inPlayerId,
            voidsSeq = voidsSeq, note = note, clockSec = clockSec
        )
        val withLog = m.copy(log = m.log + ev)
        return if (clockRunning == null) withLog else withLog.copy(clockRunning = clockRunning, clockEpochMs = nowMs)
    }

    /**
     * Merges remotely received events into a tournament snapshot. Matches whose merged log contains a
     * MATCH_START are rebuilt from the log; others (not started, or played before logging existed)
     * are taken from the snapshot unchanged.
     */
    fun applyRemote(base: Tournament, remote: Map<String, List<MatchEvent>>): Tournament =
        base.copy(matches = base.matches.map { m ->
            val merged = (m.log + (remote[m.id] ?: emptyList())).distinctBy { it.id }.sortedBy { it.seq }
            if (merged.none { it.type == MatchEventType.MATCH_START }) m else replay(m, merged, base.format)
        })

    /** Rebuilds a match's live state from its log, starting from [base]'s fixed data (teams, stage, lineups). */
    fun replay(base: Match, events: List<MatchEvent>, format: GameFormat): Match {
        val f = format.forMatch(base)
        val ordered = events.sortedBy { it.seq }
        var m = base.copy(
            status = MatchStatus.SCHEDULED, period = 1, scoreA = 0, scoreB = 0,
            remainingSec = f.periodMin * 60, breakRemainingSec = 0,
            events = emptyList(), winnerId = null, tieNote = "",
            clockRunning = false, clockEpochMs = 0L, log = ordered
        )
        for (e in ordered) m = apply(m, e, ordered)
        return m
    }

    /** Sets the clock value carried by [e] (if any) on the right field, plus running/epoch. */
    private fun clock(m: Match, e: MatchEvent, running: Boolean): Match {
        val sec = e.clockSec
        val withValue = when {
            sec == null -> m
            m.status == MatchStatus.BREAK -> m.copy(breakRemainingSec = sec)
            else -> m.copy(remainingSec = sec)
        }
        return withValue.copy(clockRunning = running, clockEpochMs = e.epochMs)
    }

    private fun apply(m: Match, e: MatchEvent, all: List<MatchEvent>): Match = when (e.type) {
        MatchEventType.MATCH_START -> clock(m.copy(status = MatchStatus.LIVE, period = 1), e, false)
        MatchEventType.PERIOD_START -> clock(
            // Regulation only (2nd half). Older logs may carry an overtime / sudden-death note here; those periods
            // no longer exist, so the note is ignored (period labels treat anything past regulation as the last one).
            m.copy(status = MatchStatus.LIVE, period = m.period + 1, breakRemainingSec = 0),
            e, (e.clockSec ?: 0) > 0
        )
        MatchEventType.PAUSE -> clock(m, e, false)
        MatchEventType.RESUME -> clock(m, e, true)
        MatchEventType.BREAK_START -> clock(m.copy(status = MatchStatus.BREAK), e, true)
        MatchEventType.BREAK_END -> m.copy(status = MatchStatus.LIVE, breakRemainingSec = 0)
        MatchEventType.SCORE -> {
            val isA = e.teamId == m.teamAId
            val pts = e.points ?: 0
            val note = e.note ?: ""
            val se = ScoreEvent(
                id = e.id, teamId = e.teamId ?: "", playerId = e.playerId, points = pts,
                period = note.substringBefore(' '), clock = note.substringAfter(' ', ""), createdAt = e.epochMs
            )
            val scored = m.copy(
                scoreA = if (isA) m.scoreA + pts else m.scoreA,
                scoreB = if (isA) m.scoreB else m.scoreB + pts,
                events = m.events + se
            )
            if (e.clockSec != null) clock(scored, e, m.clockRunning) else scored
        }
        MatchEventType.VOID -> if (all.any { it.type == MatchEventType.SHOOTOUT_ATTEMPT && it.seq == e.voidsSeq }) {
            // Undoing a shootout attempt: the shootout is derived from the log (Match.shootout), still TIEBREAK.
            m
        } else {
            val target = all.firstOrNull { it.type == MatchEventType.SCORE && it.seq == e.voidsSeq }
            val wasTieBreak = m.status == MatchStatus.TIEBREAK
            var r = m.copy(status = if (wasTieBreak) MatchStatus.LIVE else m.status)
            if (target != null) {
                val isA = target.teamId == r.teamAId
                val pts = target.points ?: 0
                r = r.copy(
                    scoreA = if (isA) (r.scoreA - pts).coerceAtLeast(0) else r.scoreA,
                    scoreB = if (isA) r.scoreB else (r.scoreB - pts).coerceAtLeast(0),
                    events = r.events.filter { it.id != target.id }
                )
            }
            if (e.clockSec != null) clock(r, e, false) else r
        }
        MatchEventType.SUB -> {
            val isA = e.teamId == m.teamAId
            val out = e.outPlayerId
            val inn = e.inPlayerId
            if (out == null || inn == null) m else {
                val cur = if (isA) m.lineupA else m.lineupB
                val next = if (out in cur) cur.map { if (it == out) inn else it } else cur
                val fixed = if (inn in next) next else next + inn
                if (isA) m.copy(lineupA = fixed) else m.copy(lineupB = fixed)
            }
        }
        MatchEventType.TIEBREAK_START -> clock(m.copy(status = MatchStatus.TIEBREAK, remainingSec = 0), e, false)
        // Attempts never touch the score; the shootout state is read from the log via Match.shootout.
        MatchEventType.SHOOTOUT_ATTEMPT -> m
        MatchEventType.MATCH_END -> m.copy(
            status = MatchStatus.FINISHED, winnerId = e.teamId, tieNote = e.note ?: "",
            clockRunning = false, clockEpochMs = e.epochMs
        )
        MatchEventType.REOPEN -> clock(
            m.copy(status = MatchStatus.LIVE, winnerId = null, tieNote = "", remainingSec = 0),
            e, false
        )
    }
}
