package com.hoopsandkicks.tournament.ui

import com.hoopsandkicks.tournament.data.Algorithm
import com.hoopsandkicks.tournament.data.DRAW_NOTE
import com.hoopsandkicks.tournament.data.Match
import com.hoopsandkicks.tournament.data.MatchStatus
import com.hoopsandkicks.tournament.data.Player
import com.hoopsandkicks.tournament.data.Scheduler
import com.hoopsandkicks.tournament.data.StageType
import com.hoopsandkicks.tournament.data.TStatus
import com.hoopsandkicks.tournament.data.Team
import com.hoopsandkicks.tournament.data.Tournament
import kotlin.random.Random

// Sample tournaments shared by the @Preview functions in the screen files. They are built with the app's own
// Scheduler so they look like real data. Nothing here is used at runtime.

private const val SampleStart = 1_750_000_000_000L

private fun Match.played(a: Int, b: Int): Match = copy(
    status = MatchStatus.FINISHED, scoreA = a, scoreB = b,
    winnerId = when { a > b -> teamAId; b > a -> teamBId; else -> null },
    tieNote = if (a == b) DRAW_NOTE else ""
)

private fun sampleTeams(names: List<String>, players: List<Player> = emptyList()): List<Team> =
    names.mapIndexed { i, n -> Team("t$i", n, i, players.drop(i * 3).take(3).map { it.id }) }

/** 4 teams, round robin, 2 courts: one win, one live match, one draw, the rest scheduled. */
internal val previewActive: Tournament by lazy {
    val positions = listOf("Guard", "Forward", "Center")
    val players = (1..12).map { Player("p$it", "Player $it", positions[it % 3], (it % 5) + 1) }
    val teams = listOf(
        Team("a", "Red Hawks", 0, players.subList(0, 3).map { it.id }),
        Team("b", "Blue Jays", 1, players.subList(3, 6).map { it.id }),
        Team("c", "Green Mambas", 2, players.subList(6, 9).map { it.id }),
        Team("d", "Gold Kings", 3, players.subList(9, 12).map { it.id })
    )
    val base = Tournament(
        id = "preview", name = "City Cup", startAt = SampleStart, endAt = SampleStart + 6 * 3_600_000L, courts = 2,
        playerTarget = 12, teamTarget = 4, players = players, teams = teams,
        algorithm = Algorithm.ROUND_ROBIN, status = TStatus.ACTIVE, draftStep = 5, createdAt = SampleStart
    )
    val scheduled = Scheduler.scheduleTimes(base.copy(matches = Scheduler.generate(base, Random(1))))
    val ms = scheduled.matches.toMutableList()
    ms[0] = ms[0].played(54, 48)
    ms[1] = ms[1].copy(status = MatchStatus.LIVE, scoreA = 12, scoreB = 9, period = 1, remainingSec = 420)
    ms[2] = ms[2].played(40, 40)
    scheduled.copy(matches = ms)
}

/** [previewActive] with nothing live, so the hub shows "Up next". */
internal val previewUpNext: Tournament by lazy {
    previewActive.copy(matches = previewActive.matches.map {
        if (it.status == MatchStatus.LIVE) it.copy(status = MatchStatus.SCHEDULED, scoreA = 0, scoreB = 0) else it
    })
}

/** 8 teams in 2 groups (top 2 advance): group stage finished with one draw, first semi-final played. */
internal val previewGroupKo: Tournament by lazy {
    val names = listOf("Red Hawks", "Blue Jays", "Green Mambas", "Gold Kings", "Night Owls", "Iron Wolves", "Sky Foxes", "Sand Sharks")
    val base = Tournament(
        id = "preview-gko", name = "Spring Cup", startAt = SampleStart, endAt = SampleStart + 10 * 3_600_000L, courts = 2,
        teamTarget = 8, teams = sampleTeams(names), algorithm = Algorithm.GROUP_KO, groups = 2, advance = 2,
        status = TStatus.ACTIVE, draftStep = 5, createdAt = SampleStart
    )
    val drawn = base.copy(matches = Scheduler.generate(base, Random(2)))
    // Lower-numbered teams beat higher-numbered ones; every fifth match is a draw.
    val played = drawn.copy(matches = drawn.matches.mapIndexed { i, m ->
        val a = m.teamAId!!.drop(1).toInt()
        val b = m.teamBId!!.drop(1).toInt()
        when {
            i % 5 == 0 -> m.played(44, 44)
            a < b -> m.played(52, 40)
            else -> m.played(40, 52)
        }
    })
    val withSemis = Scheduler.advance(played, Random(3))
    val firstSemi = withSemis.matches.first { it.stageType == StageType.KNOCKOUT }
    withSemis.copy(matches = withSemis.matches.map { if (it.id == firstSemi.id) it.played(61, 55) else it })
}

/** 8 teams, single elimination: quarter-finals finished, semi-finals drawn. No points anywhere. */
internal val previewSingleElim: Tournament by lazy {
    val names = listOf("Red Hawks", "Blue Jays", "Green Mambas", "Gold Kings", "Night Owls", "Iron Wolves", "Sky Foxes", "Sand Sharks")
    val base = Tournament(
        id = "preview-ko", name = "Knockout Cup", startAt = SampleStart, endAt = SampleStart + 10 * 3_600_000L, courts = 2,
        teamTarget = 8, teams = sampleTeams(names), algorithm = Algorithm.SINGLE_ELIM,
        status = TStatus.ACTIVE, draftStep = 5, createdAt = SampleStart
    )
    val drawn = base.copy(matches = Scheduler.generate(base, Random(4)))
    val played = drawn.copy(matches = drawn.matches.map { m ->
        val a = m.teamAId!!.drop(1).toInt()
        val b = m.teamBId!!.drop(1).toInt()
        if (a < b) m.played(58, 50) else m.played(50, 58)
    })
    Scheduler.advance(played, Random(5))
}

/** Plays every unfinished group/league/knockout match: the lower-numbered team wins, every fourth match is a draw where allowed. */
private fun Tournament.playOpenMatches(): Tournament = copy(matches = matches.mapIndexed { i, m ->
    if (m.status == MatchStatus.FINISHED || m.bye) m else {
        val a = m.teamAId!!.drop(1).toInt()
        val b = m.teamBId!!.drop(1).toInt()
        when {
            i % 4 == 0 && m.awardsPoints -> m.played(44, 44)
            a < b -> m.played(52, 40)
            else -> m.played(40, 52)
        }
    }
})

private fun roundRobinBase(id: String, teamCount: Int, semis: Boolean, final: Boolean): Tournament {
    val names = listOf("Red Hawks", "Blue Jays", "Green Mambas", "Gold Kings", "Night Owls", "Iron Wolves")
    val base = Tournament(
        id = id, name = "League Cup", startAt = SampleStart, endAt = SampleStart + 10 * 3_600_000L, courts = 2,
        teamTarget = teamCount, teams = sampleTeams(names.take(teamCount)), algorithm = Algorithm.ROUND_ROBIN,
        rrSemis = semis, rrFinal = final, status = TStatus.ACTIVE, draftStep = 5, createdAt = SampleStart
    )
    return base.copy(matches = Scheduler.generate(base, Random(6)))
}

/** 6-team round robin with semi-finals: the round robin is done and the semi-finals are drawn but not played. */
internal val previewRrSemis: Tournament by lazy {
    Scheduler.advance(roundRobinBase("preview-rr-semis", 6, semis = true, final = true).playOpenMatches(), Random(7))
}

/** [previewRrSemis] with both semi-finals played, so the final is drawn (not played yet). */
internal val previewRrSemisFinal: Tournament by lazy {
    Scheduler.advance(previewRrSemis.playOpenMatches(), Random(8))
}

/** 4-team round robin with only a final: the final has been played, so the tournament is complete. */
internal val previewRrFinalOnly: Tournament by lazy {
    val withFinal = Scheduler.advance(roundRobinBase("preview-rr-final", 4, semis = false, final = true).playOpenMatches(), Random(9))
    Scheduler.advance(withFinal.playOpenMatches(), Random(10))
}
