package com.hoopsandkicks.tournament

import com.hoopsandkicks.tournament.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SchedulerTest {

    private fun tournament(teamCount: Int, algo: Algorithm, groups: Int = 2, advance: Int = 2): Tournament {
        val teams = (1..teamCount).map { Team("t$it", "Team $it", it) }
        return Tournament(
            id = "x", name = "Test", teams = teams, algorithm = algo, groups = groups, advance = advance,
            status = TStatus.ACTIVE
        ).let { it.copy(matches = Scheduler.generate(it, Random(1))) }
    }

    /** Finish every unfinished match: team A wins 10-5. */
    private fun playAll(t0: Tournament): Tournament {
        var t = t0
        var guard = 0
        while (t.status != TStatus.COMPLETED && guard++ < 100) {
            val open = t.matches.filter { it.status != MatchStatus.FINISHED }
            if (open.isEmpty()) break
            t = t.copy(matches = t.matches.map {
                if (it.status != MatchStatus.FINISHED) it.copy(status = MatchStatus.FINISHED, scoreA = 10, scoreB = 5, winnerId = it.teamAId) else it
            })
            t = Scheduler.advance(t, Random(2))
        }
        return t
    }

    @Test fun randomSplitCoversEveryPlayerOnce() {
        val ids = (1..10).map { "p$it" }
        val res = Scheduler.randomSplit(ids, listOf("a", "b", "c"), Random(3))
        assertEquals(ids.toSet(), res.values.flatten().toSet())
        assertEquals(10, res.values.sumOf { it.size })
        assertTrue(res.values.all { it.size in 3..4 })
    }

    @Test fun balancedSplitSpreadsSkill() {
        val players = (1..8).map { Player("p$it", "P$it", "Guard", if (it <= 4) 5 else 1) }
        val res = Scheduler.balancedSplit(players, listOf("a", "b", "c", "d"), Random(3))
        res.values.forEach { team ->
            val skills = team.map { id -> players.first { it.id == id }.skill }
            assertEquals(listOf(1, 5), skills.sorted())
        }
    }

    @Test fun roundRobinPlaysEveryPairOnce() {
        val t = tournament(5, Algorithm.ROUND_ROBIN)
        assertEquals(10, t.matches.size)
        val pairs = t.matches.map { setOf(it.teamAId, it.teamBId) }.toSet()
        assertEquals(10, pairs.size)
    }

    @Test fun roundRobinAddsFinalBetweenTopTwo() {
        val t = tournament(5, Algorithm.ROUND_ROBIN)
        val done = playAll(t)
        assertEquals(TStatus.COMPLETED, done.status)
        assertTrue(done.championId != null)
        // 10 round-robin matches (5 teams) + exactly one extra Final.
        assertEquals(11, done.matches.size)
        assertEquals(1, done.matches.count { it.isFinal })
        val final = done.matches.first { it.isFinal }
        assertEquals("Final", final.label)
        // The tournament's champion is decided by the Final, not by round-robin standings.
        assertEquals(final.winnerId, done.championId)
    }

    @Test fun roundRobinWithTwoTeamsHasNoExtraFinal() {
        val t = tournament(2, Algorithm.ROUND_ROBIN)
        val done = playAll(t)
        assertEquals(TStatus.COMPLETED, done.status)
        // Just the single round-robin match between the two teams; it already is the decider.
        assertEquals(1, done.matches.size)
        assertEquals(0, done.matches.count { it.isFinal })
    }

    @Test fun groupStageThenSemisThenFinal() {
        val t = tournament(6, Algorithm.GROUP_KO, groups = 2, advance = 2)
        assertEquals(6, t.matches.size) // two groups of three -> 3 matches each
        assertTrue(t.matches.all { it.stageType == StageType.GROUP })
        val done = playAll(t)
        assertEquals(TStatus.COMPLETED, done.status)
        assertTrue(done.championId != null)
        assertEquals(2, done.matches.count { it.stage == "Semi-finals" })
        assertEquals(1, done.matches.count { it.isFinal })
    }

    @Test fun singleEliminationHandlesByes() {
        val t = tournament(6, Algorithm.SINGLE_ELIM)
        assertEquals(2, t.matches.count { it.bye })
        val done = playAll(t)
        assertEquals(TStatus.COMPLETED, done.status)
        assertTrue(done.championId != null)
    }

    @Test fun doubleEliminationEndsWithOneChampion() {
        val done = playAll(tournament(5, Algorithm.DOUBLE_ELIM))
        assertEquals(TStatus.COMPLETED, done.status)
        assertTrue(done.championId != null)
    }

    @Test fun swissRunsFixedRounds() {
        val done = playAll(tournament(6, Algorithm.SWISS))
        assertEquals(TStatus.COMPLETED, done.status)
        assertEquals(3, done.matches.maxOf { it.round })
    }

    @Test fun standingsUseHeadToHeadForTwoWayTie() {
        fun m(id: String, a: String, b: String, sa: Int, sb: Int) = Match(
            id, id.toInt(), 0, "S", StageType.LEAGUE, teamAId = a, teamBId = b, scoreA = sa, scoreB = sb,
            status = MatchStatus.FINISHED, winnerId = if (sa > sb) a else b
        )
        val ms = listOf(
            m("1", "a", "b", 10, 12), // b beats a head-to-head
            m("2", "a", "c", 50, 0),  // a has the far better point difference
            m("3", "b", "d", 0, 1),
            m("4", "c", "d", 0, 1)
        )
        // d has 2 wins; a and b have 1 each (level), b won the head-to-head.
        val rows = Standings.compute(listOf("a", "b", "c", "d"), ms)
        assertEquals(listOf("d", "b", "a", "c"), rows.map { it.teamId })
    }

    private fun scheduledTournament(teamCount: Int, courts: Int = 1): Tournament {
        val t = tournament(teamCount, Algorithm.ROUND_ROBIN).copy(startAt = 1_000_000L, courts = courts)
        return Scheduler.scheduleTimes(t)
    }

    @Test fun reorderCourtMovesMatchEarlierAndPinsIt() {
        val t = scheduledTournament(4) // round robin, 6 matches, one court
        val byTime = t.matches.sortedBy { it.scheduledAt }
        val (first, second, third) = byTime.take(3)
        // Swap the 1st and 3rd matches on the court.
        val newOrder = listOf(third.id, second.id, first.id) + byTime.drop(3).map { it.id }
        val reordered = Scheduler.reorderCourt(t, 1, newOrder)

        val nowByTime = reordered.matches.filter { !it.bye }.sortedBy { it.scheduledAt }
        assertEquals(third.id, nowByTime[0].id)
        assertEquals(second.id, nowByTime[1].id)
        assertEquals(first.id, nowByTime[2].id)
        // The three reordered matches are pinned so a later re-slot can't quietly put them back.
        assertTrue(reordered.match(third.id)!!.pinned)
        assertTrue(reordered.match(first.id)!!.pinned)
    }

    @Test fun reorderCourtSurvivesALaterScheduleTimesCall() {
        val t = scheduledTournament(4)
        val byTime = t.matches.sortedBy { it.scheduledAt }
        val (first, second, third) = byTime.take(3)
        val newOrder = listOf(third.id, second.id, first.id)
        val reordered = Scheduler.reorderCourt(t, 1, newOrder)

        // Simulate something else calling scheduleTimes again later (adding a court, extending the end time, …).
        val reslotted = Scheduler.scheduleTimes(reordered.copy(courts = 2))
        val nowByTime = reslotted.matches.filter { !it.bye && it.court == 1 }.sortedBy { it.scheduledAt }
        assertEquals(third.id, nowByTime[0].id)
    }

    @Test fun reorderCourtIgnoresMatchesAlreadyStarted() {
        val t0 = scheduledTournament(4)
        val byTime = t0.matches.sortedBy { it.scheduledAt }
        val started = byTime[0]
        val t = t0.copy(matches = t0.matches.map { if (it.id == started.id) it.copy(status = MatchStatus.LIVE) else it })
        val originalStartedTime = started.scheduledAt

        // Try to move the already-started match to the front anyway; it should be left exactly where it was.
        val newOrder = listOf(started.id) + byTime.drop(1).map { it.id }
        val reordered = Scheduler.reorderCourt(t, 1, newOrder)
        assertEquals(originalStartedTime, reordered.match(started.id)!!.scheduledAt)
        assertTrue(!reordered.match(started.id)!!.pinned)
    }
}
