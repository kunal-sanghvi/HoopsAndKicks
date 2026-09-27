package com.hoopsandkicks.tournament.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hoopsandkicks.tournament.data.DRAW_NOTE
import com.hoopsandkicks.tournament.data.Match
import com.hoopsandkicks.tournament.data.MatchEventType
import com.hoopsandkicks.tournament.data.MatchLog
import com.hoopsandkicks.tournament.data.MatchStatus
import com.hoopsandkicks.tournament.data.ScoreEvent
import com.hoopsandkicks.tournament.data.Sport
import com.hoopsandkicks.tournament.data.Tournament
import com.hoopsandkicks.tournament.data.buildMatchFeed
import com.hoopsandkicks.tournament.data.shootout
import com.hoopsandkicks.tournament.data.shootoutNote

// Previews of the viewers' match feed (hub live card and fixtures timeline). The sample data lives here (not in
// PreviewSamples.kt) so this file can be merged on its own. Nothing here runs in the app.

private const val FeedSampleStart = 1_750_000_000_000L

private val FeedNames = listOf("Jordan", "Maya", "Leo", "Sam", "Ava", "Noah", "Zoe", "Kai", "Mia", "Eli", "Ivy", "Max")

/** [previewActive] with real-looking player names, in [sport]. */
private fun feedCup(sport: Sport = Sport.BASKETBALL): Tournament = previewActive.copy(
    sport = sport, format = sport.defaultFormat(), roomCode = PreviewRoomCode,
    players = previewActive.players.mapIndexed { i, p -> p.copy(name = FeedNames.getOrElse(i) { p.name }) }
)

/**
 * Writes a match the way the organizer's live screen does (Match fields + one MatchLog entry per action), so the
 * previews show exactly what a viewer rebuilds from the log. Team A / B players are picked by roster index.
 */
private class FeedScript(val t: Tournament, var m: Match) {
    private var now = FeedSampleStart
    private val aIds = t.team(m.teamAId)?.playerIds.orEmpty()
    private val bIds = t.team(m.teamBId)?.playerIds.orEmpty()

    private fun log(
        type: MatchEventType, teamId: String? = null, playerId: String? = null, points: Int? = null,
        outId: String? = null, inId: String? = null, voidsSeq: Int? = null, note: String? = null, id: String? = null
    ) {
        now += 45_000L
        m = MatchLog.append(
            m, type, nowMs = now, teamId = teamId, playerId = playerId, points = points, outPlayerId = outId,
            inPlayerId = inId, voidsSeq = voidsSeq, note = note, id = id ?: "ev${MatchLog.nextSeq(m)}"
        )
    }

    private fun player(forA: Boolean, index: Int?): String? = index?.let { (if (forA) aIds else bIds).getOrNull(it) }

    fun start() = apply {
        m = m.copy(status = MatchStatus.LIVE, period = 1, remainingSec = 600)
        log(MatchEventType.MATCH_START)
    }

    fun score(forA: Boolean, playerIndex: Int?, points: Int, clock: String) = apply {
        val teamId = (if (forA) m.teamAId else m.teamBId) ?: return@apply
        val period = if (m.period == 1) "1" else "2"
        val id = "ev${MatchLog.nextSeq(m)}"
        val ev = ScoreEvent(id, teamId, player(forA, playerIndex), points, period, clock, now)
        m = m.copy(
            scoreA = if (forA) m.scoreA + points else m.scoreA,
            scoreB = if (forA) m.scoreB else m.scoreB + points,
            events = m.events + ev
        )
        log(MatchEventType.SCORE, teamId, ev.playerId, points, note = "$period $clock", id = id)
    }

    fun undo() = apply {
        val last = m.events.lastOrNull() ?: return@apply
        val target = m.log.lastOrNull { it.id == last.id }
        val isA = last.teamId == m.teamAId
        m = m.copy(
            scoreA = if (isA) m.scoreA - last.points else m.scoreA,
            scoreB = if (isA) m.scoreB else m.scoreB - last.points,
            events = m.events.dropLast(1)
        )
        log(MatchEventType.VOID, last.teamId, last.playerId, last.points, voidsSeq = target?.seq)
    }

    fun sub(forA: Boolean, outIndex: Int, inIndex: Int) = apply {
        log(MatchEventType.SUB, if (forA) m.teamAId else m.teamBId, outId = player(forA, outIndex), inId = player(forA, inIndex))
    }

    fun halfTime() = apply {
        m = m.copy(status = MatchStatus.BREAK, breakRemainingSec = 240)
        log(MatchEventType.BREAK_START)
    }

    fun secondHalf() = apply {
        m = m.copy(status = MatchStatus.LIVE, period = 2, remainingSec = 480, breakRemainingSec = 0)
        log(MatchEventType.BREAK_END)
        log(MatchEventType.PERIOD_START)
    }

    fun tieBreak() = apply {
        m = m.copy(status = MatchStatus.TIEBREAK, remainingSec = 0)
        log(MatchEventType.TIEBREAK_START)
    }

    /** The next shootout attempt: teams alternate (A first), each roster in turn. */
    fun attempt(made: Boolean) = apply {
        val so = m.shootout(t.sport)
        val forA = so.nextIsA
        val taken = if (forA) so.attemptsA.size else so.attemptsB.size
        val roster = if (forA) aIds else bIds
        val shooter = if (roster.isEmpty()) null else roster[taken % roster.size]
        log(MatchEventType.SHOOTOUT_ATTEMPT, if (forA) m.teamAId else m.teamBId, shooter, if (made) 1 else 0)
    }

    /** Full time the way the live screen records it: leader wins, level = draw, or the decided shootout. */
    fun end() = apply {
        val so = m.shootout(t.sport)
        val shootoutWinner = so.winnerId
        val (winner, note) = when {
            m.scoreA > m.scoreB -> m.teamAId to ""
            m.scoreB > m.scoreA -> m.teamBId to ""
            shootoutWinner != null -> {
                val aWon = shootoutWinner == m.teamAId
                shootoutWinner to shootoutNote(t.sport, if (aWon) so.madeA else so.madeB, if (aWon) so.madeB else so.madeA)
            }
            else -> null to DRAW_NOTE
        }
        m = m.copy(status = MatchStatus.FINISHED, winnerId = winner, tieNote = note)
        log(MatchEventType.MATCH_END, winner, note = note)
    }

    /** The tournament with this match in place of the original. */
    fun tournament(): Tournament = t.copy(matches = t.matches.map { if (it.id == m.id) m else it })
}

/** A script on the live match of [feedCup] (round robin), reset to a fresh 0–0 before kick-off. */
private fun liveScript(sport: Sport = Sport.BASKETBALL): FeedScript {
    val t = feedCup(sport)
    val base = t.matches.first { it.status == MatchStatus.LIVE }
        .copy(status = MatchStatus.SCHEDULED, scoreA = 0, scoreB = 0, events = emptyList(), log = emptyList())
    return FeedScript(t, base)
}

/** Live 3–0 in the first half: Jordan +3 after an undone basket, and two subs. */
private fun sampleLive3to0(): FeedScript = liveScript().start()
    .score(true, 0, 2, "01:10").undo()
    .score(true, 0, 3, "02:40")
    .sub(true, 1, 2)
    .sub(false, 1, 2)

/** A busy live first-then-second half, 8 lines so "Show all" appears. */
private fun sampleLiveBusy(): FeedScript = liveScript().start()
    .score(true, 0, 3, "00:45")
    .score(false, 0, 2, "01:30")
    .sub(true, 1, 2)
    .score(true, 2, 2, "03:05")
    .halfTime().secondHalf()
    .score(false, 1, 3, "00:50")
    .score(true, 0, 1, "02:15")

private fun sampleHalfTime(): FeedScript = liveScript().start()
    .score(true, 0, 3, "00:45")
    .score(false, 0, 2, "04:30")
    .score(true, 1, 2, "09:05")
    .halfTime()

/** Knockout-style level full time and a free-throw shootout in progress (A made, B made, A missed). */
private fun sampleTieBreak(): FeedScript = liveScript().start()
    .score(true, 0, 2, "03:00")
    .score(false, 1, 2, "07:40")
    .halfTime().secondHalf()
    .tieBreak()
    .attempt(true).attempt(true).attempt(false)

private fun sampleFinishedWin(): FeedScript = liveScript().start()
    .score(true, 0, 3, "00:45")
    .sub(false, 0, 2)
    .score(false, 2, 2, "03:30")
    .score(true, 1, 2, "08:10")
    .halfTime().secondHalf()
    .score(false, null, 1, "02:00")
    .score(true, 0, 2, "06:20")
    .end()

private fun sampleFinishedDraw(): FeedScript = liveScript().start()
    .score(true, 0, 2, "02:00")
    .halfTime().secondHalf()
    .score(false, 1, 2, "05:30")
    .end()

private fun sampleFinishedShootout(): FeedScript = liveScript().start()
    .score(true, 0, 2, "04:00")
    .score(false, 0, 2, "09:15")
    .halfTime().secondHalf()
    .tieBreak()
    .attempt(true).attempt(false).attempt(true).attempt(true).attempt(false).attempt(false)
    .end()

private fun sampleFootballLive(): FeedScript = liveScript(Sport.FOOTBALL).start()
    .score(true, 0, 1, "12:30")
    .sub(false, 0, 2)
    .score(false, null, 1, "31:05")
    .halfTime().secondHalf()
    .score(true, 1, 1, "04:40")

private fun sampleFootballFinished(): FeedScript = liveScript(Sport.FOOTBALL).start()
    .score(true, 0, 1, "12:30")
    .halfTime().secondHalf()
    .score(false, 1, 1, "20:10")
    .tieBreak()
    .attempt(true).attempt(true).attempt(true).attempt(false).attempt(false).attempt(true)
    .attempt(true).attempt(true).attempt(true).attempt(false)
    .end()

/** A score with no player picked (team-only), next to one with a scorer. */
private fun sampleNoPlayer(): FeedScript = liveScript().start()
    .score(true, null, 3, "01:20")
    .score(false, 0, 2, "02:10")
    .score(true, null, 1, "03:45")

/** Older data (or a viewer whose log hasn't arrived yet): scores in Match.events, nothing in the log. */
private fun sampleFallback(finished: Boolean): FeedScript {
    val s = liveScript().start().score(true, 0, 3, "01:10").score(false, 1, 2, "03:25").score(true, 2, 2, "06:00")
    if (finished) s.end()
    s.m = s.m.copy(log = emptyList())
    return s
}

private fun sampleEmpty(): FeedScript = liveScript().start()

// ─── Preview frames ─────────────────────────────────────────────────────

/** The hub's navy live card for [s]'s match (UpNextCard, read-only). */
@Composable
private fun LiveCardPreview(s: FeedScript) {
    val t = s.tournament()
    HoopsTheme {
        Column(Modifier.padding(16.dp)) { UpNextCard(t, s.m, true, { _, _ -> }, readOnly = true) }
    }
}

/** The fixtures match cards (read-only) for [scripts], tapped open unless [expanded] is false. */
@Composable
private fun MatchCardsPreview(vararg scripts: FeedScript, expanded: Boolean = true) {
    HoopsTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            scripts.forEach { s -> MatchCard(s.tournament(), s.m, { _, _ -> }, readOnly = true, shootoutExpanded = expanded) }
        }
    }
}

// ─── Viewer: hub live card ──────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE, widthDp = 412, heightDp = 900, name = "Viewer - hub, match feed live 3-0")
@Composable
private fun PreviewViewerHubFeedLive() {
    HoopsTheme { TournamentContent(sampleLive3to0().tournament(), {}, {}, {}, { _, _ -> }, readOnly = true) }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE, widthDp = 412, heightDp = 640, name = "Viewer - live card, feed latest 5")
@Composable
private fun PreviewViewerLiveCardBusy() {
    LiveCardPreview(sampleLiveBusy())
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE, widthDp = 412, heightDp = 640, name = "Viewer - live feed, show all expanded")
@Composable
private fun PreviewViewerLiveFeedShowAll() {
    val s = sampleLiveBusy()
    val t = s.tournament()
    HoopsTheme {
        Column(
            Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Navy).padding(16.dp)
        ) {
            LiveMatchFeedContent(t, buildMatchFeed(t, s.m), s.m.id, showAllInitially = true)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE, widthDp = 412, heightDp = 560, name = "Viewer - live card, half-time")
@Composable
private fun PreviewViewerLiveCardHalfTime() {
    LiveCardPreview(sampleHalfTime())
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE, widthDp = 412, heightDp = 820, name = "Viewer - live card, shootout attempts")
@Composable
private fun PreviewViewerLiveCardShootout() {
    LiveCardPreview(sampleTieBreak())
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE, widthDp = 412, heightDp = 560, name = "Viewer - live card, football")
@Composable
private fun PreviewViewerLiveCardFootball() {
    LiveCardPreview(sampleFootballLive())
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE, widthDp = 412, heightDp = 520, name = "Viewer - live card, score with no player")
@Composable
private fun PreviewViewerLiveCardNoPlayer() {
    LiveCardPreview(sampleNoPlayer())
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE, widthDp = 412, heightDp = 480, name = "Viewer - live card, older data (events, no log)")
@Composable
private fun PreviewViewerLiveCardFallback() {
    LiveCardPreview(sampleFallback(finished = false))
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE, widthDp = 412, heightDp = 360, name = "Viewer - live card, empty feed")
@Composable
private fun PreviewViewerLiveCardEmpty() {
    LiveCardPreview(sampleEmpty())
}

// ─── Viewer: fixtures timeline ──────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE, widthDp = 412, heightDp = 900, name = "Viewer - fixtures, match feed (tap to expand)")
@Composable
private fun PreviewViewerFixturesFeed() {
    HoopsTheme {
        FixturesContent(sampleFinishedWin().tournament(), onBack = {}, onOpenMatch = { _, _ -> }, readOnly = true, onReorder = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE, widthDp = 412, heightDp = 820, name = "Viewer - match card, finished win timeline")
@Composable
private fun PreviewViewerCardWin() {
    MatchCardsPreview(sampleFinishedWin())
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE, widthDp = 412, heightDp = 560, name = "Viewer - match card, finished draw timeline")
@Composable
private fun PreviewViewerCardDraw() {
    MatchCardsPreview(sampleFinishedDraw())
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE, widthDp = 412, heightDp = 900, name = "Viewer - match card, won on free throws timeline")
@Composable
private fun PreviewViewerCardShootout() {
    MatchCardsPreview(sampleFinishedShootout())
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE, widthDp = 412, heightDp = 1000, name = "Viewer - match card, football free kicks timeline")
@Composable
private fun PreviewViewerCardFootball() {
    MatchCardsPreview(sampleFootballFinished())
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE, widthDp = 412, heightDp = 700, name = "Viewer - match cards, live and older data")
@Composable
private fun PreviewViewerCardsLiveAndFallback() {
    MatchCardsPreview(sampleLive3to0(), sampleFallback(finished = true))
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE, widthDp = 412, heightDp = 480, name = "Viewer - match cards collapsed, feed hint and empty")
@Composable
private fun PreviewViewerCardsCollapsed() {
    MatchCardsPreview(sampleFinishedWin(), sampleEmpty(), expanded = false)
}
