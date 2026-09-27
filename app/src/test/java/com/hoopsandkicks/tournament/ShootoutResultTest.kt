package com.hoopsandkicks.tournament

import com.hoopsandkicks.tournament.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Result lines for finished matches (shootout, draw, win) and the shootout reaching a remote viewer. */
class ShootoutResultTest {
    private val t = Tournament(
        id = "t", name = "Cup", sport = Sport.BASKETBALL,
        players = listOf(Player("a1", "Maya"), Player("a2", "Leo"), Player("b1", "Sam")),
        teams = listOf(Team("A", "Hawks", 0, listOf("a1", "a2")), Team("B", "Jays", 1, listOf("b1")))
    )
    private val base = Match(
        id = "m1", number = 1, stageIndex = 0, stage = "Semi-finals", stageType = StageType.KNOCKOUT,
        teamAId = "A", teamBId = "B", scoreA = 40, scoreB = 40
    )

    private fun ev(m: Match, type: MatchEventType, teamId: String? = null, playerId: String? = null, points: Int? = null, note: String? = null) =
        MatchLog.append(m, type, nowMs = 1L, teamId = teamId, playerId = playerId, points = points, note = note)

    /** A made, B missed, A missed, B made, A made, B missed: A wins 2–1, then full time is recorded. */
    private fun wonOnFreeThrows(): Match {
        var m = ev(base, MatchEventType.MATCH_START)
        m = ev(m, MatchEventType.TIEBREAK_START)
        listOf("A" to true, "B" to false, "A" to false, "B" to true, "A" to true, "B" to false).forEachIndexed { i, (team, made) ->
            val player = if (team == "A") (if (i % 4 == 0) "a1" else "a2") else "b1"
            m = ev(m, MatchEventType.SHOOTOUT_ATTEMPT, teamId = team, playerId = player, points = if (made) 1 else 0)
        }
        val note = shootoutNote(Sport.BASKETBALL, 2, 1)
        return ev(m, MatchEventType.MATCH_END, teamId = "A", note = note)
            .copy(status = MatchStatus.FINISHED, winnerId = "A", tieNote = note)
    }

    @Test fun shootoutResultComesFromTheLog() {
        val r = wonOnFreeThrows().shootoutResult(Sport.BASKETBALL)
        assertEquals(ShootoutResult("A", 2, 1), r)
        assertEquals(2, r?.madeBy("A"))
        assertEquals(1, r?.madeBy("B"))
        assertEquals("Won 2–1 on free throws", r?.wonLine(Sport.BASKETBALL))
    }

    @Test fun shootoutResultFallsBackToTheNoteWithoutALog() {
        val m = base.copy(status = MatchStatus.FINISHED, winnerId = "B", tieNote = "Won 4–3 on free kicks · tie-breaker recorded")
        assertEquals(ShootoutResult("B", 4, 3), m.shootoutResult(Sport.FOOTBALL))
    }

    @Test fun noShootoutResultForRegulationWinsDrawsOrUnfinishedMatches() {
        assertNull(base.copy(status = MatchStatus.FINISHED, scoreA = 50, winnerId = "A").shootoutResult(Sport.BASKETBALL))
        assertNull(base.copy(status = MatchStatus.FINISHED, winnerId = null, tieNote = DRAW_NOTE).shootoutResult(Sport.BASKETBALL))
        assertNull(base.copy(status = MatchStatus.TIEBREAK).shootoutResult(Sport.BASKETBALL))
    }

    @Test fun resultNoteExplainsEveryLevelScore() {
        assertEquals("Hawks won 2–1 on free throws", wonOnFreeThrows().resultNote(t))
        assertEquals("Draw", base.copy(stageType = StageType.GROUP, status = MatchStatus.FINISHED, tieNote = DRAW_NOTE).resultNote(t))
        assertNull(base.copy(status = MatchStatus.FINISHED, scoreA = 50, winnerId = "A").resultNote(t))
        assertEquals("Old note", base.copy(status = MatchStatus.FINISHED, scoreA = 50, winnerId = "A", tieNote = "Old note").resultNote(t))
        assertNull(base.resultNote(t))
    }

    @Test fun drawPointsFollowThePointsRule() {
        assertEquals("1 point each", t.drawPointsText())
        assertEquals("2 points each", t.copy(tiePoints = 2).drawPointsText())
        assertEquals("0 points each", t.copy(tiePoints = 0).drawPointsText())
    }

    @Test fun shooterNamesListEachAttemptInOrder() {
        val so = wonOnFreeThrows().shootout(Sport.BASKETBALL)
        assertEquals("Maya ✓ · Leo ✗ · Maya ✓", shooterNames(t, so.attemptsA))
        assertEquals("Sam ✗ · Sam ✓ · Sam ✗", shooterNames(t, so.attemptsB))
        val anonymous = listOf(ShootoutAttempt(1, "A", null, true))
        assertNull(shooterNames(t, anonymous))
    }

    /** The room snapshot carries matches with their log stripped; the events arrive separately (RemoteSync.joinRoom). */
    @Test fun viewerRebuildsTheShootoutFromSyncedEvents() {
        val host = wonOnFreeThrows()
        val stripped = t.copy(matches = listOf(host.copy(log = emptyList())))
        val tiebreakOnly = host.log.filter { it.type != MatchEventType.MATCH_END }

        val live = MatchLog.applyRemote(stripped, mapOf("m1" to tiebreakOnly)).matches.single()
        assertEquals(MatchStatus.TIEBREAK, live.status)
        assertEquals(2, live.shootout(Sport.BASKETBALL).madeA)
        assertEquals("A", live.shootout(Sport.BASKETBALL).winnerId)

        val done = MatchLog.applyRemote(stripped, mapOf("m1" to host.log)).matches.single()
        assertEquals(MatchStatus.FINISHED, done.status)
        assertEquals(ShootoutResult("A", 2, 1), done.shootoutResult(Sport.BASKETBALL))
        assertEquals("Hawks won 2–1 on free throws", done.resultNote(t))
    }
}
