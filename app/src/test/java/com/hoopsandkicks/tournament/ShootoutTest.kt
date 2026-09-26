package com.hoopsandkicks.tournament

import com.hoopsandkicks.tournament.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixed tie-breaker: 3 free throws (basketball) / 5 free kicks (football) each, alternating, then sudden death. */
class ShootoutTest {
    private val base = Match(
        id = "m1", number = 1, stageIndex = 0, stage = "Semis", stageType = StageType.KNOCKOUT,
        teamAId = "A", teamBId = "B", scoreA = 40, scoreB = 40
    )

    private fun tied(): Match {
        var m = MatchLog.append(base, MatchEventType.MATCH_START, clockSec = 600, clockRunning = false)
        return MatchLog.append(m, MatchEventType.TIEBREAK_START, clockSec = 0, clockRunning = false)
    }

    private fun attempt(m: Match, teamId: String, made: Boolean, player: String? = null) =
        MatchLog.append(m, MatchEventType.SHOOTOUT_ATTEMPT, teamId = teamId, playerId = player, points = if (made) 1 else 0)

    /** Plays [results] alternately A, B, A, B… exactly as the tie-breaker screen does, asserting the turn order. */
    private fun play(sport: Sport, vararg results: Boolean): Match {
        var m = tied()
        results.forEach { made ->
            val s = m.shootout(sport)
            assertFalse("shootout already decided", s.decided)
            m = attempt(m, if (s.nextIsA) "A" else "B", made)
        }
        return m
    }

    @Test fun allotmentIsFixedPerSport() {
        assertEquals(3, Sport.BASKETBALL.shootoutAttempts)
        assertEquals(5, Sport.FOOTBALL.shootoutAttempts)
    }

    @Test fun teamsAlternateStartingWithA() {
        val m = play(Sport.BASKETBALL, true, false, true)
        val s = m.shootout(Sport.BASKETBALL)
        assertEquals(listOf("A", "B", "A"), s.all.map { it.teamId })
        assertFalse(s.nextIsA)
        assertEquals(2, s.nextAttemptNumber)
    }

    @Test fun decidedEarlyWhenOneSideCannotCatchUp() {
        // A 2/2, B 0/2 with one left each: B can reach at most 1.
        val s = play(Sport.BASKETBALL, true, false, true, false).shootout(Sport.BASKETBALL)
        assertEquals("A", s.winnerId)
        assertEquals(2, s.madeA)
        assertEquals(0, s.madeB)
    }

    @Test fun levelAfterAllotmentGoesToSuddenDeath() {
        val m = play(Sport.BASKETBALL, true, true, false, false, true, true)
        val s = m.shootout(Sport.BASKETBALL)
        assertNull(s.winnerId)
        assertTrue(s.suddenDeath)
        assertEquals(4, s.nextAttemptNumber)
        assertEquals(4, s.slots)
        // Sudden death: A misses, B scores -> B wins after an equal number of attempts.
        val b = attempt(m, "A", false)
        assertNull(b.shootout(Sport.BASKETBALL).winnerId)
        assertEquals("B", attempt(b, "B", true).shootout(Sport.BASKETBALL).winnerId)
    }

    @Test fun footballTakesFiveEach() {
        // A 5/5 v B 4/4 is not decided yet (B could still level); B's 5th miss decides it 5–4.
        val m = play(Sport.FOOTBALL, true, true, true, true, true, true, true, true, true)
        assertNull(m.shootout(Sport.FOOTBALL).winnerId)
        assertEquals("A", attempt(m, "B", false).shootout(Sport.FOOTBALL).winnerId)
    }

    @Test fun voidedAttemptIsRemovedAndReplayStaysInTieBreak() {
        var m = attempt(tied(), "A", true, player = "a1")
        m = MatchLog.append(m, MatchEventType.VOID, teamId = "A", voidsSeq = m.log.last().seq)
        val s = m.shootout(Sport.BASKETBALL)
        assertTrue(s.all.isEmpty())
        assertTrue(s.nextIsA)
        val r = MatchLog.replay(base, m.log, GameFormat())
        assertEquals(MatchStatus.TIEBREAK, r.status)
        assertEquals(0, r.scoreA) // replay rebuilds the score from SCORE events only; attempts never add points
    }

    @Test fun reopenedMatchStartsAFreshShootout() {
        var m = play(Sport.BASKETBALL, true, false, true, false)
        m = MatchLog.append(m, MatchEventType.MATCH_END, teamId = "A", note = shootoutNote(Sport.BASKETBALL, 2, 0))
        m = MatchLog.append(m, MatchEventType.REOPEN)
        m = MatchLog.append(m, MatchEventType.TIEBREAK_START)
        assertTrue(m.shootout(Sport.BASKETBALL).all.isEmpty())
    }

    @Test fun resultNoteMatchesDesignCopy() {
        assertEquals("Won 2–1 on free throws · tie-breaker recorded", shootoutNote(Sport.BASKETBALL, 2, 1))
        assertEquals("Won 4–3 on free kicks · tie-breaker recorded", shootoutNote(Sport.FOOTBALL, 4, 3))
    }
}
