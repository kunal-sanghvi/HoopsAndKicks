package com.hoopsandkicks.tournament.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hoopsandkicks.tournament.data.Match
import com.hoopsandkicks.tournament.data.MatchEventType
import com.hoopsandkicks.tournament.data.MatchLog
import com.hoopsandkicks.tournament.data.MatchStatus
import com.hoopsandkicks.tournament.data.Sport
import com.hoopsandkicks.tournament.data.Tournament
import com.hoopsandkicks.tournament.data.eligibleShooters
import com.hoopsandkicks.tournament.data.shootout
import com.hoopsandkicks.tournament.data.shootoutDrawNote
import com.hoopsandkicks.tournament.data.shootoutNote

// Previews of the single-round tie-breaker in group / league matches (3 free throws / 5 free kicks each, then a draw
// if still level). The admin match screens' previews for it are at the end of MatchScreens.kt. Nothing here runs in the app.

/** A: made, B: made, A: made, B: missed, A: missed, B: made -> 2–2 after 3 each, a draw. */
internal val GroupShotsDraw = listOf(true, true, true, false, false, true)

/** A: made, B: missed, A: made, B: made, A: missed, B: missed -> A wins 2–1. */
internal val GroupShotsWin = listOf(true, false, true, true, false, false)

/**
 * The live round-robin match of [previewActive], level at full time, then a single-round shootout with [shots]
 * (true = made) taken alternately A, B, A... by the next eligible shooter. With [finish] the match ends the way the
 * tie-breaker screen ends it: a winner ("Won 2–1 on free throws") or, level after every attempt, a draw
 * ("Draw · 2–2 on free throws"). Returns the tournament (as a viewer receives it) and the match.
 */
internal fun previewGroupShootout(
    shots: List<Boolean>,
    finish: Boolean = true,
    sport: Sport = Sport.BASKETBALL
): Pair<Tournament, Match> {
    val (t, start) = previewMatchIn(MatchStatus.TIEBREAK, level = true, knockout = false, sport = sport)
    var m = start
    for (made in shots) {
        val so = m.shootout(sport)
        if (so.over) break
        val teamId = if (so.nextIsA) m.teamAId else m.teamBId
        val shooter = so.eligibleShooters(so.nextIsA, t.team(teamId)?.playerIds.orEmpty()).firstOrNull()
        m = MatchLog.append(m, MatchEventType.SHOOTOUT_ATTEMPT, teamId = teamId, playerId = shooter, points = if (made) 1 else 0)
    }
    val so = m.shootout(sport)
    if (finish && so.over) {
        val winner = so.winnerId
        val note = when (winner) {
            null -> shootoutDrawNote(sport, so.madeA, so.madeB)
            m.teamAId -> shootoutNote(sport, so.madeA, so.madeB)
            else -> shootoutNote(sport, so.madeB, so.madeA)
        }
        m = MatchLog.append(m, MatchEventType.MATCH_END, teamId = winner, note = note)
            .copy(status = MatchStatus.FINISHED, winnerId = winner, tieNote = note)
    }
    val done = m
    return t.copy(roomCode = PreviewRoomCode, matches = t.matches.map { if (it.id == done.id) done else it }) to done
}

// ─── Previews ───────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE, widthDp = 412, heightDp = 900, name = "Viewer - hub, group shootout live (attempt 2 of 3)")
@Composable
private fun PreviewViewerHubGroupShootout() {
    val (t, _) = previewGroupShootout(listOf(true, true, true), finish = false)
    HoopsTheme { TournamentContent(t, {}, {}, {}, { _, _ -> }, readOnly = true) }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE, widthDp = 412, heightDp = 560, name = "Viewer - hub card, group shootout not started")
@Composable
private fun PreviewViewerUpNextGroupShootoutStart() {
    val (t, m) = previewGroupShootout(emptyList(), finish = false)
    HoopsTheme { Column(Modifier.padding(16.dp)) { UpNextCard(t, m, true, { _, _ -> }, readOnly = true) } }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE, widthDp = 412, heightDp = 560, name = "Viewer - hub card, group shootout last attempt")
@Composable
private fun PreviewViewerUpNextGroupShootoutLast() {
    val (t, m) = previewGroupShootout(GroupShotsDraw.dropLast(1), finish = false)
    HoopsTheme { Column(Modifier.padding(16.dp)) { UpNextCard(t, m, true, { _, _ -> }, readOnly = true) } }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE, widthDp = 412, heightDp = 800, name = "Viewer - fixtures, group draw after shootout")
@Composable
private fun PreviewViewerFixturesGroupDraw() {
    val (t, _) = previewGroupShootout(GroupShotsDraw)
    HoopsTheme { FixturesContent(t, onBack = {}, onOpenMatch = { _, _ -> }, readOnly = true, onReorder = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE, widthDp = 412, heightDp = 1000, name = "Viewer - match cards: group shootout live, draw, won")
@Composable
private fun PreviewViewerMatchCardsGroupShootout() {
    val live = previewGroupShootout(listOf(true, true, true), finish = false)
    val draw = previewGroupShootout(GroupShotsDraw)
    val won = previewGroupShootout(GroupShotsWin)
    HoopsTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MatchCard(live.first, live.second, { _, _ -> }, readOnly = true)
            MatchCard(draw.first, draw.second, { _, _ -> }, readOnly = true, shootoutExpanded = true)
            MatchCard(won.first, won.second, { _, _ -> }, readOnly = true)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE, widthDp = 412, heightDp = 600, name = "Viewer - leaderboard result cards, group shootout draw and win")
@Composable
private fun PreviewViewerResultCardsGroupShootout() {
    val draw = previewGroupShootout(GroupShotsDraw)
    val drawFootball = previewGroupShootout(listOf(true, true, false, false, true, true, true, true, false, false), sport = Sport.FOOTBALL)
    val won = previewGroupShootout(GroupShotsWin)
    HoopsTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ResultCard(draw.first, draw.second)
            ResultCard(drawFootball.first, drawFootball.second)
            ResultCard(won.first, won.second)
        }
    }
}
