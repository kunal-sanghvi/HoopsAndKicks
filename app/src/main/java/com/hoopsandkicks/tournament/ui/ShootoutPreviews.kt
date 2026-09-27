package com.hoopsandkicks.tournament.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hoopsandkicks.tournament.data.Algorithm
import com.hoopsandkicks.tournament.data.Match
import com.hoopsandkicks.tournament.data.MatchEventType
import com.hoopsandkicks.tournament.data.MatchLog
import com.hoopsandkicks.tournament.data.MatchStatus
import com.hoopsandkicks.tournament.data.Player
import com.hoopsandkicks.tournament.data.Scheduler
import com.hoopsandkicks.tournament.data.Sport
import com.hoopsandkicks.tournament.data.TStatus
import com.hoopsandkicks.tournament.data.Team
import com.hoopsandkicks.tournament.data.Tournament
import com.hoopsandkicks.tournament.data.shootout
import com.hoopsandkicks.tournament.data.shootoutNote
import kotlin.random.Random

// Previews of every tie-breaker state for the organizer ("Admin - ...") and for live viewers ("Viewer - ...").
// The sample data lives here (not in PreviewSamples.kt) so this file can be merged on its own. Nothing here runs in the app.

private const val ShootoutSampleStart = 1_750_000_000_000L

/** 4-team knockout with rosters: both semi-finals drawn, nothing played yet. */
private fun shootoutCup(sport: Sport): Tournament {
    val names = listOf("Maya", "Leo", "Sam", "Ava", "Noah", "Zoe", "Kai", "Mia", "Eli", "Ivy", "Max", "Lia")
    val players = names.mapIndexed { i, n -> Player("sp$i", n) }
    val teams = listOf("Red Hawks", "Blue Jays", "Green Mambas", "Gold Kings").mapIndexed { i, n ->
        Team("st$i", n, i, players.subList(i * 3, i * 3 + 3).map { it.id })
    }
    val base = Tournament(
        id = "preview-shootout-${sport.name}", name = if (sport == Sport.BASKETBALL) "Hoops Cup" else "Kicks Cup",
        startAt = ShootoutSampleStart, endAt = ShootoutSampleStart + 6 * 3_600_000L, courts = 1,
        teamTarget = 4, players = players, teams = teams, sport = sport, format = sport.defaultFormat(),
        algorithm = Algorithm.SINGLE_ELIM, status = TStatus.ACTIVE, draftStep = 5, createdAt = ShootoutSampleStart
    )
    return Scheduler.scheduleTimes(base.copy(matches = Scheduler.generate(base, Random(11))))
}

/**
 * Real match [index] level at [level] each, then a shootout with [shots] (true = made) taken alternately A, B, A...
 * by each roster in turn, logged exactly as the tie-breaker screen does. With [finish] the match ends once decided.
 */
private fun Tournament.withShootout(index: Int, level: Int, shots: List<Boolean>, finish: Boolean = true): Tournament {
    val target = realMatches().getOrNull(index) ?: return this
    var m = target.copy(status = MatchStatus.LIVE, scoreA = level, scoreB = level)
    m = MatchLog.append(m, MatchEventType.MATCH_START, nowMs = ShootoutSampleStart, clockSec = 0, clockRunning = false)
    m = MatchLog.append(m, MatchEventType.TIEBREAK_START, nowMs = ShootoutSampleStart, clockSec = 0, clockRunning = false)
        .copy(status = MatchStatus.TIEBREAK, remainingSec = 0)
    for (made in shots) {
        val so = m.shootout(sport)
        if (so.decided) break
        val teamId = if (so.nextIsA) m.teamAId else m.teamBId
        val roster = team(teamId)?.playerIds.orEmpty()
        val taken = if (so.nextIsA) so.attemptsA.size else so.attemptsB.size
        val shooter = if (roster.isEmpty()) null else roster[taken % roster.size]
        m = MatchLog.append(m, MatchEventType.SHOOTOUT_ATTEMPT, nowMs = ShootoutSampleStart, teamId = teamId, playerId = shooter, points = if (made) 1 else 0)
    }
    val so = m.shootout(sport)
    val winner = so.winnerId
    if (finish && winner != null) {
        val aWon = winner == m.teamAId
        val note = shootoutNote(sport, if (aWon) so.madeA else so.madeB, if (aWon) so.madeB else so.madeA)
        m = MatchLog.append(m, MatchEventType.MATCH_END, nowMs = ShootoutSampleStart, teamId = winner, note = note)
            .copy(status = MatchStatus.FINISHED, winnerId = winner, tieNote = note)
    }
    val done = m
    return copy(matches = matches.map { if (it.id == done.id) done else it })
}

/** Real match [index] won in regulation by team A. */
private fun Tournament.withWin(index: Int, a: Int, b: Int): Tournament {
    val target = realMatches().getOrNull(index) ?: return this
    val won = target.copy(status = MatchStatus.FINISHED, scoreA = a, scoreB = b, winnerId = target.teamAId)
    return copy(matches = matches.map { if (it.id == won.id) won else it })
}

/** Semi-final 1 level 40–40, free-throw shootout under way: A made, B made, A missed; B to shoot, attempt 2 of 3. */
internal val previewShootoutLive: Tournament by lazy {
    shootoutCup(Sport.BASKETBALL).withShootout(0, 40, listOf(true, true, false), finish = false)
}

/** Still level 2–2 after 3 each, A missed the first sudden-death attempt: B to shoot, sudden death. */
internal val previewShootoutSuddenDeath: Tournament by lazy {
    shootoutCup(Sport.BASKETBALL).withShootout(0, 40, listOf(true, true, false, false, true, true, false), finish = false)
}

/** B just scored in sudden death (3–2): decided, the instant before the organizer's app records full time. */
internal val previewShootoutJustDecided: Tournament by lazy {
    shootoutCup(Sport.BASKETBALL).withShootout(0, 40, listOf(true, true, false, false, true, true, false, true), finish = false)
}

/** Semi-final 1 won 2–1 on free throws after 40–40; semi-final 2 won 58–50 in regulation. */
internal val previewShootoutFinished: Tournament by lazy {
    shootoutCup(Sport.BASKETBALL).withShootout(0, 40, listOf(true, false, false, true, true, false)).withWin(1, 58, 50)
}

/** Football: semi-final 1 won 4–3 on free kicks after 1–1. */
internal val previewShootoutFootball: Tournament by lazy {
    shootoutCup(Sport.FOOTBALL).withShootout(0, 1, listOf(true, true, true, false, false, true, true, true, true, false))
}

private fun Tournament.firstReal(): Match = realMatches().first()

/** The drawn 40–40 round-robin match in [previewActive]. */
private fun previewDrawMatch(): Match = previewActive.realMatches().first { it.isDraw }

/** The 54–48 win in [previewActive]. */
private fun previewWinMatch(): Match = previewActive.realMatches().first { it.status == MatchStatus.FINISHED && it.winnerId != null }

@Composable
private fun FullTimePreview(t: Tournament, m: Match) {
    HoopsTheme {
        Screen {
            TopBar("Full time", null)
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FullTimeScoreCard(t, m)
                ShootoutResultCard(t, m)
            }
        }
    }
}

// ─── Viewer: live shootout ──────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE, widthDp = 412, heightDp = 900, name = "Viewer - hub, free-throw shootout live")
@Composable
private fun PreviewViewerHubShootoutLive() {
    HoopsTheme { TournamentContent(previewShootoutLive, {}, {}, {}, { _, _ -> }, readOnly = true) }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE, widthDp = 412, heightDp = 900, name = "Viewer - hub, shootout sudden death")
@Composable
private fun PreviewViewerHubSuddenDeath() {
    HoopsTheme { TournamentContent(previewShootoutSuddenDeath, {}, {}, {}, { _, _ -> }, readOnly = true) }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE, widthDp = 412, heightDp = 480, name = "Viewer - hub card, shootout just decided")
@Composable
private fun PreviewViewerUpNextDecided() {
    HoopsTheme {
        Column(Modifier.padding(16.dp)) {
            UpNextCard(previewShootoutJustDecided, previewShootoutJustDecided.firstReal(), true, { _, _ -> }, readOnly = true)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE, widthDp = 412, heightDp = 800, name = "Viewer - fixtures, shootout live")
@Composable
private fun PreviewViewerFixturesShootoutLive() {
    HoopsTheme { FixturesContent(previewShootoutLive, onBack = {}, onOpenMatch = { _, _ -> }, readOnly = true, onReorder = {}) }
}

// ─── Viewer: finished matches ───────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE, widthDp = 412, heightDp = 800, name = "Viewer - fixtures, finished shootout (tap to expand)")
@Composable
private fun PreviewViewerFixturesFinished() {
    HoopsTheme { FixturesContent(previewShootoutFinished, onBack = {}, onOpenMatch = { _, _ -> }, readOnly = true, onReorder = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE, widthDp = 412, heightDp = 900, name = "Viewer - match cards: shootout expanded, draw, win")
@Composable
private fun PreviewViewerMatchCards() {
    HoopsTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MatchCard(previewShootoutFinished, previewShootoutFinished.firstReal(), { _, _ -> }, readOnly = true, shootoutExpanded = true)
            MatchCard(previewShootoutFootball, previewShootoutFootball.firstReal(), { _, _ -> }, readOnly = true, shootoutExpanded = true)
            MatchCard(previewActive, previewDrawMatch(), { _, _ -> }, readOnly = true)
            MatchCard(previewActive, previewWinMatch(), { _, _ -> }, readOnly = true)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE, widthDp = 412, heightDp = 900, name = "Viewer - leaderboard result cards")
@Composable
private fun PreviewViewerResultCards() {
    HoopsTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ResultCard(previewShootoutFinished, previewShootoutFinished.firstReal())
            ResultCard(previewShootoutFootball, previewShootoutFootball.firstReal())
            ResultCard(previewShootoutFinished, previewShootoutFinished.realMatches()[1])
            ResultCard(previewShootoutLive, previewShootoutLive.firstReal())
            ResultCard(previewActive, previewDrawMatch())
        }
    }
}

// ─── Admin: full time and fixtures ──────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE, widthDp = 412, heightDp = 800, name = "Admin - full time, won on free throws")
@Composable
private fun PreviewAdminFullTimeFreeThrows() {
    FullTimePreview(previewShootoutFinished, previewShootoutFinished.firstReal())
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE, widthDp = 412, heightDp = 800, name = "Admin - full time, won on free kicks")
@Composable
private fun PreviewAdminFullTimeFreeKicks() {
    FullTimePreview(previewShootoutFootball, previewShootoutFootball.firstReal())
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE, widthDp = 412, heightDp = 600, name = "Admin - full time, draw")
@Composable
private fun PreviewAdminFullTimeDraw() {
    FullTimePreview(previewActive, previewDrawMatch())
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE, widthDp = 412, heightDp = 600, name = "Admin - full time, regulation win")
@Composable
private fun PreviewAdminFullTimeWin() {
    FullTimePreview(previewActive, previewWinMatch())
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F3EE, widthDp = 412, heightDp = 700, name = "Admin - match cards: shootout, draw, win")
@Composable
private fun PreviewAdminMatchCards() {
    HoopsTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MatchCard(previewShootoutFinished, previewShootoutFinished.firstReal(), { _, _ -> })
            MatchCard(previewShootoutFootball, previewShootoutFootball.firstReal(), { _, _ -> })
            MatchCard(previewActive, previewDrawMatch(), { _, _ -> })
            MatchCard(previewActive, previewWinMatch(), { _, _ -> })
        }
    }
}
