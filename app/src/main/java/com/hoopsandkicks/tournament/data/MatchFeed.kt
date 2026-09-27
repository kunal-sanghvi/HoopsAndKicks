package com.hoopsandkicks.tournament.data

/**
 * One line of a match's feed for viewers ("who scored, subs, half-time, full time, the shootout").
 * [title] and [subtitle] hold the exact text shown; the web viewer (docs/) builds the same strings.
 */
sealed class FeedItem {
    abstract val id: String
    abstract val title: String
    open val subtitle: String? get() = null
    /** "<period> <clock>" of a score, e.g. "1 03:12"; null for events without a clock. */
    open val time: String? get() = null
    /** Team the line belongs to (for its colour dot), null for match-wide lines. */
    open val teamId: String? get() = null

    /** A basket or goal, with the running score after it ("Team A–Team B"). */
    data class Score(
        override val id: String,
        override val teamId: String,
        val teamName: String,
        val playerName: String?,
        val points: Int,
        val scoreA: Int,
        val scoreB: Int,
        override val time: String?,
        val football: Boolean
    ) : FeedItem() {
        val runningScore: String get() = "$scoreA–$scoreB"
        override val title: String
            get() = if (football) "Goal · ${playerName ?: teamName}" else "${playerName ?: teamName} +$points"
        override val subtitle: String get() = "$teamName · $runningScore"
    }

    data class Substitution(
        override val id: String,
        override val teamId: String,
        val teamName: String,
        val inName: String,
        val outName: String
    ) : FeedItem() {
        override val title: String get() = "$inName on for $outName"
        override val subtitle: String get() = teamName
    }

    data class HalfTime(override val id: String) : FeedItem() {
        override val title: String get() = "Half-time"
    }

    data class SecondHalf(override val id: String) : FeedItem() {
        override val title: String get() = "Second half"
    }

    /** [result] is "Red Hawks won", "Draw" or, after a shootout, "Won 2–1 on free throws". */
    data class FullTime(override val id: String, val result: String) : FeedItem() {
        override val title: String get() = "Full time"
        override val subtitle: String get() = result
    }

    /** Header of the tie-breaker: "Free-throw shootout" / "Free-kick shootout". */
    data class ShootoutStart(override val id: String, val name: String) : FeedItem() {
        override val title: String get() = name
    }

    data class ShootoutAttempt(
        override val id: String,
        override val teamId: String,
        val teamName: String,
        val playerName: String?,
        val made: Boolean,
        /** [Sport.shootoutMadeLabel]: "Made" / "Scored". */
        val madeLabel: String
    ) : FeedItem() {
        override val title: String
            get() = "${playerName ?: teamName} ${if (made) madeLabel.lowercase() else "missed"}"
        override val subtitle: String get() = teamName
    }
}

/**
 * The match feed, oldest first. Built from the append-only [Match.log] (so scores interleave with subs, half-time and
 * the shootout); when the log has no scores but [Match.events] does (older data, or a viewer whose log hasn't arrived
 * yet), the scores come from [Match.events] instead. Undone scores and attempts never appear, nor does anything
 * superseded by "Edit scores" (an earlier full time) or by an undo out of the tie-breaker (an abandoned shootout).
 * Clock noise (start, pause, resume, reopen) is skipped.
 */
fun buildMatchFeed(t: Tournament, m: Match): List<FeedItem> {
    if (m.bye) return emptyList()
    val log = m.log.sortedBy { it.seq }
    val voided = log.filter { it.type == MatchEventType.VOID }.mapNotNull { it.voidsSeq }.toSet()
    val kept = log.filter { it.type != MatchEventType.VOID && it.seq !in voided }
    val attemptSeqs = log.filter { it.type == MatchEventType.SHOOTOUT_ATTEMPT }.map { it.seq }.toSet()
    // Only the latest tie-breaker counts, and only if play didn't go back to regulation after it.
    val shootoutSeq = log.lastOrNull { it.type == MatchEventType.TIEBREAK_START }?.seq?.takeIf { start ->
        log.none { e ->
            e.seq > start && (e.type == MatchEventType.REOPEN || e.type == MatchEventType.SCORE ||
                (e.type == MatchEventType.VOID && e.voidsSeq !in attemptSeqs))
        }
    }
    val lastReopen = log.lastOrNull { it.type == MatchEventType.REOPEN }?.seq ?: -1
    val scoresFromEvents = kept.none { it.type == MatchEventType.SCORE } && m.events.isNotEmpty()

    var a = 0
    var b = 0
    fun score(id: String, teamId: String, playerId: String?, points: Int, period: String, clock: String): FeedItem.Score {
        if (teamId == m.teamAId) a += points else b += points
        return FeedItem.Score(
            id, teamId, t.teamName(teamId), t.player(playerId)?.name, points, a, b,
            timeLabel(period, clock), t.sport == Sport.FOOTBALL
        )
    }

    // Each item keeps its timestamp so fallback scores (from Match.events) can be slotted in by time.
    val timed = ArrayList<Pair<Long, FeedItem>>()
    var secondHalfShown = false
    for (e in kept) {
        val item: FeedItem? = when (e.type) {
            MatchEventType.SCORE -> if (scoresFromEvents) null else {
                val note = e.note ?: ""
                score(e.id, e.teamId ?: "", e.playerId, e.points ?: 0, note.substringBefore(' '), note.substringAfter(' ', ""))
            }
            MatchEventType.SUB -> {
                val team = e.teamId
                if (team == null || e.inPlayerId == null || e.outPlayerId == null) null
                else FeedItem.Substitution(e.id, team, t.teamName(team), playerName(t, e.inPlayerId), playerName(t, e.outPlayerId))
            }
            MatchEventType.BREAK_START -> FeedItem.HalfTime(e.id)
            // Older logs used a noted PERIOD_START for overtime, which no longer exists: only the plain 2nd half shows.
            MatchEventType.PERIOD_START -> if (secondHalfShown || e.note != null) null else {
                secondHalfShown = true
                FeedItem.SecondHalf(e.id)
            }
            MatchEventType.TIEBREAK_START -> if (e.seq == shootoutSeq) FeedItem.ShootoutStart(e.id, t.sport.shootoutName) else null
            MatchEventType.SHOOTOUT_ATTEMPT -> {
                val team = e.teamId
                if (shootoutSeq == null || e.seq < shootoutSeq || team == null) null
                else FeedItem.ShootoutAttempt(
                    e.id, team, t.teamName(team), t.player(e.playerId)?.name, (e.points ?: 0) > 0, t.sport.shootoutMadeLabel
                )
            }
            MatchEventType.MATCH_END -> if (e.seq < lastReopen) null else FeedItem.FullTime(e.id, resultLine(t, m, e.teamId))
            else -> null
        }
        if (item != null) timed += e.epochMs to item
    }

    val items = if (scoresFromEvents) {
        val scores = m.events.map { it.createdAt to score(it.id, it.teamId, it.playerId, it.points, it.period, it.clock) }
        (timed + scores).sortedBy { it.first }.map { it.second }
    } else {
        timed.map { it.second }
    }
    // A finished match saved before logging existed has no MATCH_END: still close its feed with the result.
    if (m.status == MatchStatus.FINISHED && items.isNotEmpty() && items.none { it is FeedItem.FullTime }) {
        return items + FeedItem.FullTime("full-time", resultLine(t, m, m.winnerId))
    }
    return items
}

private fun timeLabel(period: String, clock: String): String? =
    listOf(period, clock).filter { it.isNotBlank() }.joinToString(" ").ifEmpty { null }

private fun playerName(t: Tournament, id: String?): String = t.player(id)?.name ?: "Unknown player"

private fun resultLine(t: Tournament, m: Match, winnerId: String?): String =
    if (winnerId == null) DRAW_NOTE
    else m.shootoutResult(t.sport)?.wonLine(t.sport) ?: "${t.teamName(winnerId)} won"
