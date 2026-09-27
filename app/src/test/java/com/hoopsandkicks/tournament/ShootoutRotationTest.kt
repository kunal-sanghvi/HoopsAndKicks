package com.hoopsandkicks.tournament

import com.hoopsandkicks.tournament.data.Match
import com.hoopsandkicks.tournament.data.MatchEventType
import com.hoopsandkicks.tournament.data.MatchLog
import com.hoopsandkicks.tournament.data.MatchStatus
import com.hoopsandkicks.tournament.data.Sport
import com.hoopsandkicks.tournament.data.StageType
import com.hoopsandkicks.tournament.data.eligibleShooters
import com.hoopsandkicks.tournament.data.shootout
import org.junit.Assert.assertEquals
import org.junit.Test

class ShootoutRotationTest {

    private val roster = listOf("p1", "p2", "p3")

    private fun tiedMatch(): Match = MatchLog.append(
        Match("m", 1, 0, "Final", StageType.KNOCKOUT, teamAId = "a", teamBId = "b", status = MatchStatus.TIEBREAK),
        MatchEventType.TIEBREAK_START
    )

    private fun Match.attempt(team: String, player: String?, made: Boolean = true): Match =
        MatchLog.append(this, MatchEventType.SHOOTOUT_ATTEMPT, teamId = team, playerId = player, points = if (made) 1 else 0)

    private fun Match.eligibleA() = shootout(Sport.BASKETBALL).eligibleShooters(true, roster)

    @Test fun everyoneIsEligibleAtTheStart() {
        assertEquals(roster, tiedMatch().eligibleA())
    }

    @Test fun aPlayerWhoHasShotIsNotEligibleUntilEveryoneHas() {
        val m = tiedMatch().attempt("a", "p1").attempt("b", "p1")
        assertEquals(listOf("p2", "p3"), m.eligibleA())
    }

    @Test fun onlyTheRemainingPlayerIsEligibleWhenTwoHaveShot() {
        val m = tiedMatch().attempt("a", "p1").attempt("b", "p1").attempt("a", "p3").attempt("b", "p2")
        assertEquals(listOf("p2"), m.eligibleA())
    }

    @Test fun everyoneIsEligibleAgainOnceTheWholeRosterHasShot() {
        val m = tiedMatch()
            .attempt("a", "p1").attempt("b", "p1")
            .attempt("a", "p2").attempt("b", "p2")
            .attempt("a", "p3").attempt("b", "p3")
        assertEquals(roster, m.eligibleA())
    }

    @Test fun theSecondRotationAlsoNeedsEveryoneToShotBeforeARepeat() {
        val m = tiedMatch()
            .attempt("a", "p1").attempt("b", "p1")
            .attempt("a", "p2").attempt("b", "p2")
            .attempt("a", "p3").attempt("b", "p3")
            .attempt("a", "p2")
        assertEquals(listOf("p1", "p3"), m.eligibleA())
    }

    @Test fun anUndoneAttemptFreesItsShooterAgain() {
        val withAttempt = tiedMatch().attempt("a", "p1")
        val seq = withAttempt.log.last().seq
        val undone = MatchLog.append(withAttempt, MatchEventType.VOID, teamId = "a", playerId = "p1", points = 1, voidsSeq = seq)
        assertEquals(roster, undone.eligibleA())
    }

    @Test fun teamsAreCountedSeparately() {
        val m = tiedMatch().attempt("a", "p1").attempt("b", "p2")
        assertEquals(listOf("p2", "p3"), m.eligibleA())
        assertEquals(listOf("p1", "p3"), m.shootout(Sport.BASKETBALL).eligibleShooters(false, roster))
    }

    @Test fun aTeamWithNoRosterHasNoNamedShooters() {
        assertEquals(emptyList<String>(), tiedMatch().shootout(Sport.BASKETBALL).eligibleShooters(true, emptyList()))
    }
}
