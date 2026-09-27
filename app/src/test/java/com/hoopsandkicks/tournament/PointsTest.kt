package com.hoopsandkicks.tournament

import com.hoopsandkicks.tournament.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PointsTest {

    private fun m(id: String, a: String, b: String, sa: Int, sb: Int, type: StageType = StageType.LEAGUE, final: Boolean = false) = Match(
        id, id.toInt(), 0, "S", type, teamAId = a, teamBId = b, scoreA = sa, scoreB = sb, isFinal = final,
        status = MatchStatus.FINISHED, winnerId = if (sa > sb) a else if (sb > sa) b else null
    )

    @Test fun drawCountsAsTieForBothTeamsAndAwardsTiePoints() {
        val rows = Standings.compute(listOf("a", "b"), listOf(m("1", "a", "b", 5, 5)))
        rows.forEach {
            assertEquals(1, it.played)
            assertEquals(1, it.ties)
            assertEquals(0, it.wins)
            assertEquals(0, it.losses)
            assertEquals(1, it.points) // default tie points
        }
    }

    @Test fun customWinTieLossPointsAreUsed() {
        val ms = listOf(
            m("1", "a", "b", 9, 3),  // a wins
            m("2", "a", "c", 4, 4),  // draw
            m("3", "b", "c", 2, 8)   // c wins
        )
        val rows = Standings.compute(listOf("a", "b", "c"), ms, PointsRule(win = 3, tie = 1, loss = 0))
            .associateBy { it.teamId }
        assertEquals(4, rows.getValue("a").points) // win + tie
        assertEquals(0, rows.getValue("b").points) // two losses
        assertEquals(4, rows.getValue("c").points) // tie + win
        val withLossPoints = Standings.compute(listOf("a", "b", "c"), ms, PointsRule(win = 3, tie = 1, loss = 1))
            .associateBy { it.teamId }
        assertEquals(2, withLossPoints.getValue("b").points) // two losses at 1 point each
    }

    @Test fun onlyGroupAndLeagueMatchesAwardPoints() {
        assertTrue(m("1", "a", "b", 1, 0, StageType.GROUP).awardsPoints)
        assertTrue(m("1", "a", "b", 1, 0, StageType.LEAGUE).awardsPoints)
        assertFalse(m("1", "a", "b", 1, 0, StageType.KNOCKOUT).awardsPoints)
        // A round-robin "Final" is a LEAGUE-typed match but still a final.
        assertFalse(m("1", "a", "b", 1, 0, StageType.LEAGUE, final = true).awardsPoints)
    }

    @Test fun overallIgnoresKnockoutsAndCountsOnlyGroupMatches() {
        val teams = (1..4).map { Team("t$it", "Team $it", it) }
        val base = Tournament(
            id = "x", name = "Test", teams = teams, algorithm = Algorithm.GROUP_KO, groups = 2, advance = 2,
            status = TStatus.ACTIVE, winPoints = 3, tiePoints = 1, lossPoints = 0
        )
        var t = base.copy(matches = Scheduler.generate(base, Random(1)))
        var guard = 0
        while (t.status != TStatus.COMPLETED && guard++ < 20) {
            t = t.copy(matches = t.matches.map {
                if (it.status != MatchStatus.FINISHED) it.copy(status = MatchStatus.FINISHED, scoreA = 10, scoreB = 5, winnerId = it.teamAId) else it
            })
            t = Scheduler.advance(t, Random(2))
        }
        assertEquals(TStatus.COMPLETED, t.status)
        val groupMatches = t.matches.count { it.awardsPoints }
        val rows = Leaderboards.overall(t)
        // Every group match gives its winner 3 points and nobody else anything; knockout wins add nothing.
        assertEquals(groupMatches * 3, rows.sumOf { it.total })
        assertEquals(groupMatches * 2, rows.sumOf { it.played })
        assertEquals(groupMatches, rows.sumOf { it.won })
    }

    @Test fun pureKnockoutFormatHasNoPointsColumns() {
        val teams = (1..4).map { Team("t$it", "Team $it", it) }
        val base = Tournament(id = "x", name = "Test", teams = teams, algorithm = Algorithm.SINGLE_ELIM, status = TStatus.ACTIVE)
        val t = base.copy(matches = Scheduler.generate(base, Random(1)))
        assertFalse(t.algorithm.usesPoints)
        assertTrue(Leaderboards.overall(t).all { it.total == 0 && it.played == 0 })
    }

    private fun roundRobin(teamCount: Int, semis: Boolean, final: Boolean): Tournament {
        val teams = (1..teamCount).map { Team("t$it", "Team $it", it) }
        val base = Tournament(
            id = "x", name = "Test", teams = teams, algorithm = Algorithm.ROUND_ROBIN,
            rrSemis = semis, rrFinal = final, status = TStatus.ACTIVE
        )
        return base.copy(matches = Scheduler.generate(base, Random(1)))
    }

    private fun playOpen(t: Tournament): Tournament = t.copy(matches = t.matches.map {
        if (it.status != MatchStatus.FINISHED) it.copy(status = MatchStatus.FINISHED, scoreA = 10, scoreB = 5, winnerId = it.teamAId) else it
    })

    @Test fun roundRobinTeamsOutsideTheSemisAreOutInRoundRobin() {
        val t = Scheduler.advance(playOpen(roundRobin(6, semis = true, final = true)), Random(2))
        val rows = Leaderboards.overall(t)
        val inSemis = t.matches.filter { it.stageType == StageType.KNOCKOUT }.flatMap { listOf(it.teamAId, it.teamBId) }.toSet()
        assertEquals(4, inSemis.size)
        rows.forEach {
            if (it.teamId in inSemis) assertEquals("Still in", it.statusText) else assertEquals("Out in round robin", it.statusText)
        }
    }

    @Test fun roundRobinFinalOnlyMarksTheOthersOutAndTheFinalistsStillIn() {
        val t = Scheduler.advance(playOpen(roundRobin(4, semis = false, final = true)), Random(2))
        val final = t.matches.first { it.isFinal }
        Leaderboards.overall(t).forEach {
            if (it.teamId == final.teamAId || it.teamId == final.teamBId) assertEquals("Still in", it.statusText)
            else assertEquals("Out in round robin", it.statusText)
        }
    }

    @Test fun previewSamplesHaveTheStagesThePreviewsExpect() {
        fun stages(t: Tournament) = t.matches.map { it.stage }.distinct()
        assertEquals(listOf("Round Robin", "Semi-finals"), stages(com.hoopsandkicks.tournament.ui.previewRrSemis))
        assertEquals(listOf("Round Robin", "Semi-finals", "Final"), stages(com.hoopsandkicks.tournament.ui.previewRrSemisFinal))
        val finalOnly = com.hoopsandkicks.tournament.ui.previewRrFinalOnly
        assertEquals(listOf("Round Robin", "Final"), stages(finalOnly))
        assertEquals(TStatus.COMPLETED, finalOnly.status)
        assertEquals(listOf("Group Stage", "Semi-finals"), stages(com.hoopsandkicks.tournament.ui.previewGroupKo))
        assertTrue(com.hoopsandkicks.tournament.ui.previewActive.matches.any { it.isDraw })
        assertEquals(listOf("Quarter-finals", "Semi-finals"), stages(com.hoopsandkicks.tournament.ui.previewSingleElim))
    }
}
