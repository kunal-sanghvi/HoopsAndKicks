package com.hoopsandkicks.tournament.data

import java.util.UUID

fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(12)

enum class Algorithm(val title: String, val blurb: String) {
    GROUP_KO("Group Stage → Semis → Finals", "Teams play inside groups, top teams reach the knockouts."),
    ROUND_ROBIN("Round Robin", "Every team plays every other team once, then the top 2 meet in a final."),
    SINGLE_ELIM("Single Elimination", "Lose once and you are out. Fastest format."),
    DOUBLE_ELIM("Double Elimination", "Two losses to be knocked out. Winners and elimination sides."),
    SWISS("Swiss", "Fixed rounds, paired by current standings.")
}

enum class FormatType { CONTINUOUS, HALVES }
enum class MatchStatus { SCHEDULED, LIVE, BREAK, TIEBREAK, FINISHED }
enum class StageType { GROUP, KNOCKOUT, LEAGUE }
enum class TStatus { DRAFT, ACTIVE, COMPLETED }

enum class Sport(val displayName: String, val emoji: String) {
    BASKETBALL("Basketball", "🏀"),
    FOOTBALL("Football", "⚽");

    val scoreEventSingular: String get() = if (this == BASKETBALL) "basket" else "goal"
    val scoreEventPlural: String get() = if (this == BASKETBALL) "baskets" else "goals"
    val positions: List<String>
        get() = if (this == BASKETBALL) listOf("Guard", "Forward", "Center")
        else listOf("Goalkeeper", "Defender", "Midfielder", "Forward")

    fun defaultFormat(): GameFormat = if (this == BASKETBALL)
        GameFormat(type = FormatType.HALVES, periodMin = 12, breakMin = 5, allow1 = true, allow2 = true, allow3 = true)
    else
        GameFormat(type = FormatType.HALVES, periodMin = 45, breakMin = 15, allow1 = true, allow2 = false, allow3 = false)

    // ----- Tie-breaker: fixed per sport, never chosen by the organizer (see data/Shootout.kt) -----

    /** Attempts each team takes before the shootout goes to sudden death: 3 free throws / 5 free kicks. */
    val shootoutAttempts: Int get() = if (this == BASKETBALL) 3 else 5
    /** e.g. "Free-throw shootout". */
    val shootoutName: String get() = if (this == BASKETBALL) "Free-throw shootout" else "Free-kick shootout"
    /** Lowercase adjective form used in status lines, e.g. "free-throw shootout". */
    val shootoutNameLower: String get() = shootoutName.lowercase()
    /** Plural of one attempt, e.g. "free throws" (as in "Won 2–1 on free throws"). */
    val shootoutAttemptPlural: String get() = if (this == BASKETBALL) "free throws" else "free kicks"
    /** Unit used in the sudden-death footnote: "one shot at a time" / "one free kick at a time". */
    val shootoutAttemptSingular: String get() = if (this == BASKETBALL) "shot" else "free kick"
    /** Label of the success button in the shootout: "Made" / "Scored". */
    val shootoutMadeLabel: String get() = if (this == BASKETBALL) "Made" else "Scored"
}

data class Player(
    val id: String,
    val name: String,
    val position: String = "Guard",
    val skill: Int = 3
)

data class Team(
    val id: String,
    val name: String,
    val color: Int,
    val playerIds: List<String> = emptyList()
)

data class GameFormat(
    val type: FormatType = FormatType.HALVES,
    /** Minutes per half (HALVES) or the whole game (CONTINUOUS). */
    val periodMin: Int = 12,
    val breakMin: Int = 5,
    val allow1: Boolean = true,
    val allow2: Boolean = true,
    val allow3: Boolean = true,
    val finalsDifferent: Boolean = false,
    val finalsPeriodMin: Int = 15
) {
    fun periods(): Int = if (type == FormatType.HALVES) 2 else 1

    /** Estimated regulation length in minutes; ignores shootout tie-breaks, which can't be predicted. */
    fun estimatedDurationMin(): Int = periodMin * periods() + breakMin * (periods() - 1)

    fun forMatch(isFinal: Boolean): GameFormat =
        if (isFinal && finalsDifferent) copy(periodMin = finalsPeriodMin) else this

    fun pointOptions(): List<Int> {
        val l = mutableListOf<Int>()
        if (allow1) l.add(1)
        if (allow2) l.add(2)
        if (allow3) l.add(3)
        return if (l.isEmpty()) listOf(1, 2, 3) else l
    }

    fun summary(): String =
        if (type == FormatType.HALVES) "2 × $periodMin min · $breakMin min break"
        else "$periodMin min continuous"
}

data class ScoreEvent(
    val id: String,
    val teamId: String,
    val playerId: String?,
    val points: Int,
    /** Short period tag like 1H, 2H, G. */
    val period: String,
    /** Remaining clock text, e.g. 08:42. */
    val clock: String,
    val createdAt: Long
)

/** Kinds of entries in a match's append-only event log (see [Match.log] and [MatchLog]). */
enum class MatchEventType {
    MATCH_START, PERIOD_START, PAUSE, RESUME, BREAK_START, BREAK_END, SCORE, SUB, TIEBREAK_START, VOID, MATCH_END,
    /**
     * One shootout attempt (free throw / free kick) after a tied full time: [MatchEvent.teamId] shoots,
     * [MatchEvent.playerId] is the shooter (null = no roster), [MatchEvent.points] is 1 = made/scored, 0 = missed.
     * Never changes the match score; the shootout result is derived from the log by [Match.shootout].
     */
    SHOOTOUT_ATTEMPT,
    /** A finished match was reopened for editing ("Edit scores"). */
    REOPEN
}

/**
 * One entry in a match's append-only log. Nothing is ever removed from the log: an undo appends a
 * [MatchEventType.VOID] whose [voidsSeq] points at the cancelled [MatchEventType.SCORE].
 * [clockSec] is the clock value (seconds) at the moment of the event for clock-related events, so a
 * remote viewer can rebuild the clock with [computeDisplaySeconds] from ([clockSec], [epochMs]).
 */
data class MatchEvent(
    val id: String,
    val seq: Int,
    val type: MatchEventType,
    val epochMs: Long,
    val teamId: String? = null,
    val playerId: String? = null,
    val points: Int? = null,
    val outPlayerId: String? = null,
    val inPlayerId: String? = null,
    val voidsSeq: Int? = null,
    val note: String? = null,
    val clockSec: Int? = null
)

data class Match(
    val id: String,
    val number: Int,
    val stageIndex: Int,
    val stage: String,
    val stageType: StageType,
    val group: String = "",
    val round: Int = 1,
    val label: String = "",
    val teamAId: String? = null,
    val teamBId: String? = null,
    val scoreA: Int = 0,
    val scoreB: Int = 0,
    val status: MatchStatus = MatchStatus.SCHEDULED,
    val isFinal: Boolean = false,
    val bye: Boolean = false,
    val period: Int = 1,
    val remainingSec: Int = 0,
    val breakRemainingSec: Int = 0,
    val lineupA: List<String> = emptyList(),
    val lineupB: List<String> = emptyList(),
    val events: List<ScoreEvent> = emptyList(),
    val winnerId: String? = null,
    val tieNote: String = "",
    /** Append-only record of everything that happened in this match (side channel for sync/replay). */
    val log: List<MatchEvent> = emptyList(),
    /** Whether the clock was running as of [clockEpochMs]; see [computeDisplaySeconds]. */
    val clockRunning: Boolean = false,
    /** Wall-clock time (epoch ms) at which remainingSec/breakRemainingSec was last set. */
    val clockEpochMs: Long = 0L,
    /** Planned start (epoch ms), computed by [Scheduler.scheduleTimes]; null until slotted (always null for byes). */
    val scheduledAt: Long? = null,
    /** 1-based court/pitch number, computed by [Scheduler.scheduleTimes]; null until slotted. */
    val court: Int? = null,
    /**
     * Set when the admin manually moved this match on the Fixtures screen ([Scheduler.reorderCourt]).
     * A pinned match is treated like an already-started one for slotting purposes: [Scheduler.scheduleTimes]
     * never recomputes its [scheduledAt]/[court] on its own, so adding a court, extending the end time, or an
     * earlier match finishing early can't quietly undo a manual reorder. Cleared automatically when the whole
     * schedule is regenerated (that replaces every match, pinned or not).
     */
    val pinned: Boolean = false
) {
    /** Ordering key used to find the latest stage/round frontier. */
    fun frontier(): Int = stageIndex * 1000 + round
}

data class Tournament(
    val id: String,
    val name: String,
    /** Tournament start, epoch ms. 0L = not set yet (e.g. a fresh draft). */
    val startAt: Long = 0L,
    /** Booked end of the venue, epoch ms; must be > [startAt] when both are set. 0L = not set. */
    val endAt: Long = 0L,
    /** Courts/pitches available at the same time at the venue. */
    val courts: Int = 1,
    /** Changeover minutes between consecutive matches on the same court. */
    val matchBufferMin: Int = 10,
    val venue: String = "",
    val playerTarget: Int = 8,
    val teamTarget: Int = 2,
    val players: List<Player> = emptyList(),
    val teams: List<Team> = emptyList(),
    val sport: Sport = Sport.BASKETBALL,
    val algorithm: Algorithm = Algorithm.GROUP_KO,
    val groups: Int = 2,
    val advance: Int = 2,
    val format: GameFormat = GameFormat(),
    val matches: List<Match> = emptyList(),
    val status: TStatus = TStatus.DRAFT,
    val draftStep: Int = 1,
    val championId: String? = null,
    val createdAt: Long = 0L,
    /** Set while this device hosts the tournament live (see data/remote/RemoteSync.kt). */
    val roomCode: String? = null
) {
    fun team(id: String?): Team? = if (id == null) null else teams.firstOrNull { it.id == id }
    fun player(id: String?): Player? = if (id == null) null else players.firstOrNull { it.id == id }
    fun teamName(id: String?): String = team(id)?.name ?: "TBD"
    fun match(id: String): Match? = matches.firstOrNull { it.id == id }
    fun realMatches(): List<Match> = matches.filter { !it.bye }
    fun anyStarted(): Boolean = matches.any { !it.bye && it.status != MatchStatus.SCHEDULED }
    fun maxGroups(): Int = maxOf(1, teams.size / 2)
}

/**
 * Planned end of this match (epoch ms): [Match.scheduledAt] plus its estimated regulation length
 * ([GameFormat.estimatedDurationMin], finals-aware, no changeover buffer). Null for byes and unslotted matches.
 */
fun Match.estimatedEndAt(t: Tournament): Long? {
    val start = scheduledAt ?: return null
    if (bye) return null
    return start + t.format.forMatch(isFinal).estimatedDurationMin() * 60_000L
}

/**
 * True when this match's tentative slot ends after the tournament's booked end ([Tournament.endAt]).
 * Informational only (the UI shows a "Planned past end time" note): planned times are an estimate and never stop a
 * match from being started. The end time is enforced when a plan is made, via [Scheduler.fits] on
 * [Scheduler.projectedSchedule]. Always false when no end time is set.
 * Computed on the fly from scheduledAt/endAt/format, so it is never stored.
 */
fun Match.exceedsWindow(t: Tournament): Boolean {
    if (t.endAt <= 0L) return false
    val end = estimatedEndAt(t) ?: return false
    return end > t.endAt
}

/**
 * Whether "Start match" may (re)initialise this match: both teams known and not started yet, so starting can never
 * reset a live or finished match's score and events. Deliberately independent of the tentative schedule
 * ([scheduledAt], [exceedsWindow]): planned times are for display only and never block starting a real game.
 */
fun Match.canStart(): Boolean = !bye && status == MatchStatus.SCHEDULED && teamAId != null && teamBId != null
