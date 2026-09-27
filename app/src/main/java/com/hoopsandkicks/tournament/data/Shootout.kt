package com.hoopsandkicks.tournament.data

/**
 * Tie-breaker for a match level at full time (see [timeUpChoices]). There is no overtime: basketball always goes to a
 * free-throw shootout, football always to a free-kick shootout ([Sport.shootoutAttempts] each, 3 / 5).
 *
 * Teams alternate attempt by attempt, team A first. In knockouts, semi-finals and finals, still level once both teams
 * have taken their allotment means sudden death: one attempt each until one team is ahead after an equal number of
 * attempts. Group / league matches ([Match.awardsPoints]) play a single round only: still level after the allotment
 * ends the match as a draw.
 *
 * Nothing about the shootout is stored outside the append-only [Match.log]: every attempt is a
 * [MatchEventType.SHOOTOUT_ATTEMPT] and an undo is a [MatchEventType.VOID] pointing at it, so the state below is
 * rebuilt from the log the same way for the organizer and for replaying remote viewers.
 */
data class ShootoutAttempt(val seq: Int, val teamId: String, val playerId: String?, val made: Boolean)

data class ShootoutState(
    /** Attempts per team before sudden death. */
    val perTeam: Int,
    val attemptsA: List<ShootoutAttempt>,
    val attemptsB: List<ShootoutAttempt>,
    /** Winning team id once decided, else null. */
    val winnerId: String?,
    /** Group / league match: one round of [perTeam] attempts each and no sudden death (level after it = draw). */
    val singleRound: Boolean = false
) {
    val madeA: Int get() = attemptsA.count { it.made }
    val madeB: Int get() = attemptsB.count { it.made }
    val all: List<ShootoutAttempt> get() = (attemptsA + attemptsB).sortedBy { it.seq }
    val decided: Boolean get() = winnerId != null

    /** Team A shoots first, then teams alternate. */
    val nextIsA: Boolean get() = attemptsA.size <= attemptsB.size

    /** 1-based attempt number for the team shooting next. */
    val nextAttemptNumber: Int get() = (if (nextIsA) attemptsA.size else attemptsB.size) + 1

    /** True once the next attempt is beyond the regular allotment (never in a single-round shootout). */
    val suddenDeath: Boolean get() = !singleRound && nextAttemptNumber > perTeam

    /** Single round only: both teams have taken every attempt and it is still level, so the match is a draw. */
    val endedLevel: Boolean
        get() = singleRound && attemptsA.size >= perTeam && attemptsB.size >= perTeam && madeA == madeB

    /** No more attempts to take: a winner, or a single round that ended level. */
    val over: Boolean get() = decided || endedLevel

    /** How many dot slots to draw per team: the allotment, growing by one per sudden-death round (shown up front). */
    val slots: Int get() {
        if (singleRound) return perTeam
        val round = maxOf(attemptsA.size, attemptsB.size)
        val next = if (winnerId == null && attemptsA.size == attemptsB.size) round + 1 else round
        return maxOf(perTeam, next)
    }
}

/**
 * The shootout for the current tie-break: only attempts after the latest [MatchEventType.TIEBREAK_START] count (a
 * reopened match that is tied again starts a fresh shootout), minus any attempt cancelled by a [MatchEventType.VOID].
 */
fun Match.shootout(sport: Sport): ShootoutState {
    val start = log.lastOrNull { it.type == MatchEventType.TIEBREAK_START }?.seq
    val perTeam = sport.shootoutAttempts
    val singleRound = awardsPoints
    if (start == null) return ShootoutState(perTeam, emptyList(), emptyList(), null, singleRound)
    val voided = log.filter { it.type == MatchEventType.VOID }.mapNotNull { it.voidsSeq }.toSet()
    val attempts = log.asSequence()
        .filter { it.type == MatchEventType.SHOOTOUT_ATTEMPT && it.seq > start && it.seq !in voided && it.teamId != null }
        .sortedBy { it.seq }
        .map { ShootoutAttempt(it.seq, it.teamId!!, it.playerId, (it.points ?: 0) > 0) }
        .toList()
    val a = attempts.filter { it.teamId == teamAId }
    val b = attempts.filter { it.teamId == teamBId }
    return ShootoutState(perTeam, a, b, shootoutWinner(a, b, perTeam), singleRound)
}

/**
 * Players from [roster] who may take a team's next attempt: nobody shoots again until everyone on the roster has
 * shot, so these are the players with the fewest attempts so far. Empty for an empty roster (the team then shoots
 * as a whole). An undone attempt is not counted (see [shootout]), so undo frees its shooter again.
 */
fun ShootoutState.eligibleShooters(teamIsA: Boolean, roster: List<String>): List<String> {
    if (roster.isEmpty()) return emptyList()
    val taken = (if (teamIsA) attemptsA else attemptsB).mapNotNull { it.playerId }.groupingBy { it }.eachCount()
    val fewest = roster.minOf { taken[it] ?: 0 }
    return roster.filter { (taken[it] ?: 0) == fewest }
}

private fun shootoutWinner(a: List<ShootoutAttempt>, b: List<ShootoutAttempt>, perTeam: Int): String? {
    val madeA = a.count { it.made }
    val madeB = b.count { it.made }
    val nA = a.size
    val nB = b.size
    val winA = a.firstOrNull()?.teamId
    val winB = b.firstOrNull()?.teamId
    return if (nA < perTeam || nB < perTeam) {
        // Regular rounds: decided early once one side can no longer be caught.
        when {
            madeA > madeB + (perTeam - nB) -> winA
            madeB > madeA + (perTeam - nA) -> winB
            else -> null
        }
    } else if (nA == nB && madeA != madeB) {
        // Full allotment taken (or a sudden-death round completed) with one side ahead.
        if (madeA > madeB) winA else winB
    } else null
}

/**
 * Which full-time buttons the organizer gets once the last period's clock has run out: [tieBreaker] replaces Pause
 * when the scores are level, and [endGame] is off for a level knockout, which must produce a winner.
 */
data class TimeUpChoices(val tieBreaker: Boolean, val endGame: Boolean)

fun Match.timeUpChoices(): TimeUpChoices = when {
    scoreA != scoreB -> TimeUpChoices(tieBreaker = false, endGame = true)
    awardsPoints -> TimeUpChoices(tieBreaker = true, endGame = true)
    else -> TimeUpChoices(tieBreaker = true, endGame = false)
}

/** Result note for a group / league match still level after its single-round shootout, e.g. "Draw · 2–2 on free throws". */
fun shootoutDrawNote(sport: Sport, madeA: Int, madeB: Int): String =
    "$DRAW_NOTE · $madeA–$madeB on ${sport.shootoutAttemptPlural}"

/** Result note for a match won in the shootout, e.g. "Won 2–1 on free throws · tie-breaker recorded". */
fun shootoutNote(sport: Sport, winnerMade: Int, loserMade: Int): String =
    "Won $winnerMade–$loserMade on ${sport.shootoutAttemptPlural} · tie-breaker recorded"
