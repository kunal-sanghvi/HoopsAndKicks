package com.hoopsandkicks.tournament

import com.hoopsandkicks.tournament.data.*
import com.hoopsandkicks.tournament.ui.GroupShotsDraw
import com.hoopsandkicks.tournament.ui.GroupShotsWin
import com.hoopsandkicks.tournament.ui.previewGroupShootout
import com.hoopsandkicks.tournament.ui.previewMatchIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The full-time "Tie Breaker" choice and the single-round shootout of group / league matches: 3 free throws / 5 free
 * kicks each, no sudden death, level after that = draw. Knockouts keep sudden death.
 */
class TieBreakRuleTest {
    private val t = Tournament(
        id = "t", name = "Cup", sport = Sport.BASKETBALL, winPoints = 3, tiePoints = 1, lossPoints = 0,
        players = listOf(Player("a1", "Maya"), Player("a2", "Leo"), Player("b1", "Sam"), Player("b2", "Ava")),
        teams = listOf(Team("A", "Hawks", 0, listOf("a1", "a2")), Team("B", "Jays", 1, listOf("b1", "b2")))
    )
    private val group = Match(
        id = "m1", number = 1, stageIndex = 0, stage = "Group A", stageType = StageType.GROUP,
        teamAId = "A", teamBId = "B", scoreA = 40, scoreB = 40
    )
    private val knockout = group.copy(stage = "Semi-finals", stageType = StageType.KNOCKOUT)

    private fun ev(m: Match, type: MatchEventType, teamId: String? = null, points: Int? = null, note: String? = null) =
        MatchLog.append(m, type, nowMs = 1L, teamId = teamId, points = points, note = note)

    /** Level full time, then [results] taken alternately A, B, A... as the tie-breaker screen does. */
    private fun play(m: Match, sport: Sport, vararg results: Boolean): Match {
        var r = ev(ev(m, MatchEventType.MATCH_START), MatchEventType.TIEBREAK_START).copy(status = MatchStatus.TIEBREAK)
        results.forEach { made ->
            val s = r.shootout(sport)
            assertFalse("shootout already over", s.over)
            r = ev(r, MatchEventType.SHOOTOUT_ATTEMPT, if (s.nextIsA) "A" else "B", if (made) 1 else 0)
        }
        return r
    }

    /** Full time recorded the way LiveViewModel.shootoutAttempt does once the shootout is over. */
    private fun finished(m: Match, sport: Sport = Sport.BASKETBALL): Match {
        val so = m.shootout(sport)
        val w = so.winnerId
        val note = when (w) {
            null -> shootoutDrawNote(sport, so.madeA, so.madeB)
            "A" -> shootoutNote(sport, so.madeA, so.madeB)
            else -> shootoutNote(sport, so.madeB, so.madeA)
        }
        return ev(m, MatchEventType.MATCH_END, teamId = w, note = note)
            .copy(status = MatchStatus.FINISHED, winnerId = w, tieNote = note)
    }

    // ----- Buttons at full time -----

    @Test fun notLevelOnlyEndsTheGame() {
        assertEquals(TimeUpChoices(tieBreaker = false, endGame = true), group.copy(scoreA = 41).timeUpChoices())
        assertEquals(TimeUpChoices(tieBreaker = false, endGame = true), knockout.copy(scoreB = 41).timeUpChoices())
    }

    @Test fun levelGroupMatchOffersTieBreakerOrADraw() {
        assertEquals(TimeUpChoices(tieBreaker = true, endGame = true), group.timeUpChoices())
        assertEquals(TimeUpChoices(tieBreaker = true, endGame = true), group.copy(stageType = StageType.LEAGUE).timeUpChoices())
    }

    @Test fun levelKnockoutLikeMatchMustGoToTheTieBreaker() {
        assertEquals(TimeUpChoices(tieBreaker = true, endGame = false), knockout.timeUpChoices())
        // The round-robin "Final" is LEAGUE-typed but must still produce a winner.
        assertEquals(TimeUpChoices(tieBreaker = true, endGame = false), group.copy(stageType = StageType.LEAGUE, isFinal = true).timeUpChoices())
    }

    // ----- Shootout state -----

    @Test fun groupShootoutBeforeAnyAttempt() {
        val s = play(group, Sport.BASKETBALL).shootout(Sport.BASKETBALL)
        assertTrue(s.singleRound)
        assertTrue(s.nextIsA)
        assertEquals(1, s.nextAttemptNumber)
        assertEquals(3, s.slots)
        assertFalse(s.suddenDeath)
        assertFalse(s.over)
    }

    @Test fun groupRoundLevelAfterEveryAttemptIsADraw() {
        val s = play(group, Sport.BASKETBALL, *GroupShotsDraw.toBooleanArray()).shootout(Sport.BASKETBALL)
        assertNull(s.winnerId)
        assertTrue(s.endedLevel)
        assertTrue(s.over)
        assertFalse(s.suddenDeath)
        assertEquals(3, s.slots)
        assertEquals(2, s.madeA)
        assertEquals(2, s.madeB)
    }

    @Test fun groupRoundLastAttemptIsNotSuddenDeath() {
        val s = play(group, Sport.BASKETBALL, *GroupShotsDraw.dropLast(1).toBooleanArray()).shootout(Sport.BASKETBALL)
        assertFalse(s.nextIsA)
        assertEquals(3, s.nextAttemptNumber)
        assertFalse(s.suddenDeath)
        assertFalse(s.over)
    }

    @Test fun groupRoundStillDecidesEarly() {
        // A 2/2, B 0/2 with one left each: B can reach at most 1.
        val s = play(group, Sport.BASKETBALL, true, false, true, false).shootout(Sport.BASKETBALL)
        assertEquals("A", s.winnerId)
        assertFalse(s.endedLevel)
    }

    @Test fun groupRoundWinnerAfterAllAttempts() {
        val s = play(group, Sport.BASKETBALL, *GroupShotsWin.toBooleanArray()).shootout(Sport.BASKETBALL)
        assertEquals("A", s.winnerId)
        assertEquals(2, s.madeA)
        assertEquals(1, s.madeB)
    }

    @Test fun footballGroupRoundIsFiveEach() {
        val m = play(group, Sport.FOOTBALL, true, true, true, true, true, true, true, true, false, false)
        val s = m.shootout(Sport.FOOTBALL)
        assertTrue(s.endedLevel)
        assertEquals(5, s.slots)
        assertEquals("Draw · 4–4 on free kicks", finished(m, Sport.FOOTBALL).tieNote)
    }

    @Test fun knockoutKeepsSuddenDeath() {
        val s = play(knockout, Sport.BASKETBALL, *GroupShotsDraw.toBooleanArray()).shootout(Sport.BASKETBALL)
        assertFalse(s.singleRound)
        assertNull(s.winnerId)
        assertFalse(s.endedLevel)
        assertFalse(s.over)
        assertTrue(s.suddenDeath)
        assertEquals(4, s.slots)
    }

    @Test fun rotationStillAppliesInAGroupRound() {
        val m = ev(play(group, Sport.BASKETBALL), MatchEventType.SHOOTOUT_ATTEMPT, "A", 1)
        val first = MatchLog.append(m, MatchEventType.SHOOTOUT_ATTEMPT, teamId = "B", playerId = "b1", points = 1)
        assertEquals(listOf("b2"), first.shootout(Sport.BASKETBALL).eligibleShooters(false, listOf("b1", "b2")))
    }

    // ----- Result text -----

    @Test fun drawNoteWording() {
        assertEquals("Draw · 2–2 on free throws", shootoutDrawNote(Sport.BASKETBALL, 2, 2))
        assertEquals("Draw · 4–4 on free kicks", shootoutDrawNote(Sport.FOOTBALL, 4, 4))
        assertTrue(shootoutDrawNote(Sport.BASKETBALL, 0, 0).startsWith(DRAW_NOTE))
    }

    @Test fun finishedDrawAfterShootoutReadsAsADrawEverywhere() {
        val m = finished(play(group, Sport.BASKETBALL, *GroupShotsDraw.toBooleanArray()))
        assertTrue(m.isDraw)
        assertEquals("Draw · 2–2 on free throws", m.tieNote)
        val r = m.shootoutResult(Sport.BASKETBALL)
        assertEquals(ShootoutResult(null, 2, 2), r)
        assertEquals(2, r?.madeBy("A"))
        assertEquals(2, r?.madeBy("B"))
        assertEquals("Draw · 2–2 on free throws", r?.tallyLine(Sport.BASKETBALL))
        assertEquals("Draw · 2–2 on free throws", m.resultNote(t))
        val fullTime = buildMatchFeed(t, m).last()
        assertEquals("Full time", fullTime.title)
        assertEquals("Draw · 2–2 on free throws", fullTime.subtitle)
    }

    @Test fun groupShootoutWinReadsLikeAKnockoutShootoutWin() {
        val m = finished(play(group, Sport.BASKETBALL, *GroupShotsWin.toBooleanArray()))
        assertFalse(m.isDraw)
        assertEquals("Won 2–1 on free throws · tie-breaker recorded", m.tieNote)
        assertEquals("Hawks won 2–1 on free throws", m.resultNote(t))
        assertEquals("Won 2–1 on free throws", buildMatchFeed(t, m).last().subtitle)
    }

    @Test fun viewerRebuildsTheDrawFromTheLog() {
        val host = finished(play(group, Sport.BASKETBALL, *GroupShotsDraw.toBooleanArray()))
        val stripped = t.copy(matches = listOf(host.copy(log = emptyList())))
        val done = MatchLog.applyRemote(stripped, mapOf("m1" to host.log)).matches.single()
        assertEquals(MatchStatus.FINISHED, done.status)
        assertNull(done.winnerId)
        assertTrue(done.isDraw)
        assertEquals("Draw · 2–2 on free throws", done.resultNote(t))
    }

    @Test fun drawNoteFallsBackToItsTallyWithoutALog() {
        val m = group.copy(status = MatchStatus.FINISHED, tieNote = "Draw · 3–3 on free kicks")
        assertEquals(ShootoutResult(null, 3, 3), m.shootoutResult(Sport.FOOTBALL))
    }

    @Test fun plainDrawIsNotAShootoutEvenWithAnOldShootoutInTheLog() {
        // Shootout ended level, then "Edit scores" and the organizer ended it as a plain draw.
        val drawn = finished(play(group, Sport.BASKETBALL, *GroupShotsDraw.toBooleanArray()))
        val again = ev(ev(drawn, MatchEventType.REOPEN), MatchEventType.MATCH_END, note = DRAW_NOTE)
            .copy(status = MatchStatus.FINISHED, winnerId = null, tieNote = DRAW_NOTE)
        assertNull(again.shootoutResult(Sport.BASKETBALL))
        assertEquals("Draw", again.resultNote(t))
        assertEquals("Draw", buildMatchFeed(t, again).last().subtitle)
    }

    // ----- Points -----

    @Test fun groupShootoutWinnerEarnsWinPointsAndADrawEarnsTiePoints() {
        val won = finished(play(group, Sport.BASKETBALL, *GroupShotsWin.toBooleanArray()))
        val drawn = finished(play(group.copy(id = "m2", number = 2), Sport.BASKETBALL, *GroupShotsDraw.toBooleanArray()))
        val rows = Standings.compute(listOf("A", "B"), listOf(won, drawn), t.pointsRule()).associateBy { it.teamId }
        val a = rows.getValue("A")
        val b = rows.getValue("B")
        assertEquals(listOf(1, 0, 1), listOf(a.wins, a.losses, a.ties))
        assertEquals(listOf(0, 1, 1), listOf(b.wins, b.losses, b.ties))
        assertEquals(3 + 1, a.points)
        assertEquals(0 + 1, b.points)
    }

    // ----- Preview samples -----

    @Test fun previewSamplesShowTheGroupShootoutStates() {
        val draw = previewGroupShootout(GroupShotsDraw).second
        assertTrue(draw.isDraw)
        assertEquals("Draw · 2–2 on free throws", draw.tieNote)
        val won = previewGroupShootout(GroupShotsWin).second
        assertEquals(won.teamAId, won.winnerId)
        val (lt, live) = previewGroupShootout(listOf(true, true, true), finish = false)
        assertEquals(MatchStatus.TIEBREAK, lt.match(live.id)?.status)
        assertTrue(live.awardsPoints)

        val (gt, last) = previewMatchIn(MatchStatus.TIEBREAK, level = true, shootoutAttempts = 5, knockout = false)
        val s = last.shootout(gt.sport)
        assertFalse(s.nextIsA)
        assertEquals(3, s.nextAttemptNumber)
        assertEquals(s.madeA - 1, s.madeB)

        assertEquals(TimeUpChoices(true, true), previewMatchIn(MatchStatus.LIVE, level = true, period = 2).second.timeUpChoices())
        assertEquals(TimeUpChoices(true, false), previewMatchIn(MatchStatus.LIVE, level = true, knockout = true, period = 2).second.timeUpChoices())
    }
}
