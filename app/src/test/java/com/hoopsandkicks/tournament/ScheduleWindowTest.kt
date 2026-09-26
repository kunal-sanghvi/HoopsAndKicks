package com.hoopsandkicks.tournament

import com.hoopsandkicks.tournament.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The end-time rule: a plan must fit the start-end window when it's made (fits / scheduleOverrunMinutes on the full
 * projection); once matches exist, per-match exceedsWindow is informational and never blocks starting a match.
 */
class ScheduleWindowTest {

    private val start = 1_790_000_000_000L
    private val min = 60_000L

    /** 2 × 10 min halves + 5 min break = 25 min estimated; plus a 5 min buffer = 30 min per slot. */
    private val format = GameFormat(type = FormatType.HALVES, periodMin = 10, breakMin = 5)

    private fun match(n: Int, scheduledAt: Long? = null, isFinal: Boolean = false, bye: Boolean = false) = Match(
        id = "m$n", number = n, stageIndex = 0, stage = "S", stageType = StageType.LEAGUE,
        teamAId = "a$n", teamBId = if (bye) null else "b$n", isFinal = isFinal, bye = bye, scheduledAt = scheduledAt
    )

    private fun tournament(matches: List<Match> = emptyList(), endAt: Long, courts: Int = 1, f: GameFormat = format) = Tournament(
        id = "x", name = "T", startAt = start, endAt = endAt, courts = courts, matchBufferMin = 5,
        format = f, matches = matches, status = TStatus.ACTIVE
    )

    private fun singleElim(teamCount: Int, endAt: Long, courts: Int = 1): Tournament {
        val teams = (1..teamCount).map { Team("t$it", "Team $it", it) }
        val t = Tournament(
            id = "x", name = "T", teams = teams, algorithm = Algorithm.SINGLE_ELIM, status = TStatus.ACTIVE,
            startAt = start, endAt = endAt, courts = courts, matchBufferMin = 5, format = format
        )
        return Scheduler.advance(t.copy(matches = Scheduler.generate(t, Random(1))))
    }

    private fun finishAll(t: Tournament): Tournament = t.copy(matches = t.matches.map {
        if (it.status == MatchStatus.FINISHED) it
        else it.copy(status = MatchStatus.FINISHED, scoreA = 10, scoreB = 5, winnerId = it.teamAId)
    })

    // ---------- fits / scheduleOverrunMinutes ----------

    @Test fun fitsWhenEveryMatchEndsByTheEndTime() {
        // 4 matches, 1 court: starts @0, @30, @60, @90; the last ends @115.
        val ms = (1..4).map { match(it) }
        val fitting = Scheduler.scheduleTimes(tournament(ms, endAt = start + 115 * min))
        assertEquals(0L, Scheduler.scheduleOverrunMinutes(fitting))
        assertTrue(Scheduler.fits(fitting))
        assertTrue(fitting.realMatches().none { it.exceedsWindow(fitting) })
    }

    @Test fun doesNotFitWhenTheLastMatchRunsOver() {
        val ms = (1..4).map { match(it) }
        val t = Scheduler.scheduleTimes(tournament(ms, endAt = start + 100 * min))
        assertEquals(15L, Scheduler.scheduleOverrunMinutes(t))
        assertFalse(Scheduler.fits(t))
        // Exactly the matches reported as over the window are the ones exceeding it.
        assertEquals(listOf("m4"), t.realMatches().filter { it.exceedsWindow(t) }.map { it.id })
        // A second court brings the last end to @55: fits again.
        assertTrue(Scheduler.fits(Scheduler.scheduleTimes(t.copy(courts = 2))))
    }

    @Test fun overrunRoundsUpPartialMinutes() {
        val t = Scheduler.scheduleTimes(tournament(listOf(match(1)), endAt = start + 25 * min - 1))
        assertEquals(1L, Scheduler.scheduleOverrunMinutes(t))
        assertFalse(Scheduler.fits(t))
    }

    @Test fun noEndTimeOrNoStartAlwaysFits() {
        val ms = (1..4).map { match(it) }
        val noEnd = Scheduler.scheduleTimes(tournament(ms, endAt = 0L))
        assertTrue(Scheduler.fits(noEnd))
        val noStart = tournament(ms, endAt = start).copy(startAt = 0L)
        assertTrue(Scheduler.fits(Scheduler.scheduleTimes(noStart)))
    }

    // ---------- exceedsWindow ----------

    @Test fun exceedsWindowComparesEstimatedEndWithEndTime() {
        val t = tournament(endAt = start + 60 * min)
        // Ends exactly at the end time (35 + 25 = 60): allowed.
        assertFalse(match(1, scheduledAt = start + 35 * min).exceedsWindow(t))
        // One minute later: over.
        assertTrue(match(2, scheduledAt = start + 36 * min).exceedsWindow(t))
        // The changeover buffer is not part of the match itself.
        assertEquals(start + 60 * min, match(1, scheduledAt = start + 35 * min).estimatedEndAt(t))
    }

    @Test fun exceedsWindowUsesTheFinalsLength() {
        val f = format.copy(finalsDifferent = true, finalsPeriodMin = 20) // final = 2 × 20 + 5 = 45 min
        val t = tournament(endAt = start + 60 * min, f = f)
        assertFalse(match(1, scheduledAt = start + 20 * min).exceedsWindow(t)) // regular: ends @45
        assertTrue(match(2, scheduledAt = start + 20 * min, isFinal = true).exceedsWindow(t)) // final: ends @65
    }

    @Test fun exceedsWindowIsFalseWithoutASlotOrEndTime() {
        val late = start + 500 * min
        assertFalse(match(1, scheduledAt = late).exceedsWindow(tournament(endAt = 0L)))
        assertFalse(match(2, scheduledAt = null).exceedsWindow(tournament(endAt = start + 10 * min)))
        assertFalse(match(3, scheduledAt = late, bye = true).exceedsWindow(tournament(endAt = start + 10 * min)))
        assertNull(match(3, scheduledAt = late, bye = true).estimatedEndAt(tournament(endAt = start)))
    }

    // ---------- whole-tournament projection and mid-tournament stages ----------

    @Test fun projectionIncludesStagesNotDrawnYet() {
        // 4 teams, 1 court: semis @0 and @30 (end @55), final @60 (ends @85). Only the semis exist so far.
        val t = singleElim(4, endAt = start + 60 * min)
        assertEquals(2, t.realMatches().size)
        assertTrue(Scheduler.fits(t))
        val p = Scheduler.projectedSchedule(t, Random(3))
        assertEquals(3, p.realMatches().size)
        assertEquals(start + 60 * min, p.realMatches().single { it.isFinal }.scheduledAt)
        assertEquals(25L, Scheduler.scheduleOverrunMinutes(p))
        assertFalse(Scheduler.fits(p))
        // The input is untouched: projection is a what-if, never saved.
        assertEquals(2, t.realMatches().size)
        assertTrue(t.realMatches().all { it.status == MatchStatus.SCHEDULED })
        assertTrue(Scheduler.fits(Scheduler.projectedSchedule(t.copy(endAt = start + 85 * min))))
    }

    @Test fun projectionOfAFittingTournamentFits() {
        val teams = (1..6).map { Team("t$it", "Team $it", it) }
        var t = Tournament(
            id = "x", name = "T", teams = teams, algorithm = Algorithm.GROUP_KO, groups = 2, advance = 2,
            status = TStatus.ACTIVE, startAt = start, endAt = start + 115 * min, courts = 4, matchBufferMin = 5, format = format
        )
        t = Scheduler.advance(t.copy(matches = Scheduler.generate(t, Random(1))))
        // Groups @0-@60, semis @60-@90, final @90-@115.
        val p = Scheduler.projectedSchedule(t, Random(2))
        assertEquals(TStatus.COMPLETED, p.status)
        assertTrue(Scheduler.fits(p))
        assertFalse(Scheduler.fits(Scheduler.projectedSchedule(t.copy(endAt = start + 114 * min), Random(2))))
    }

    // ---------- fewer than 2 teams (a draft reopened at the format step after deleting teams) ----------

    @Test fun fewerThanTwoTeamsNeverThrowsForAnyFormat() {
        for (n in 0..1) {
            for (algo in Algorithm.values()) {
                val teams = (1..n).map { Team("t$it", "Team $it", it) }
                val t = Tournament(
                    id = "x", name = "T", teams = teams, algorithm = algo, status = TStatus.ACTIVE,
                    startAt = start, endAt = start + 60 * min, matchBufferMin = 5, format = format
                )
                // Same steps as the wizard's initial schedule + projection.
                val drawn = Scheduler.advance(Scheduler.scheduleTimes(t.copy(matches = Scheduler.generate(t, Random(1)))))
                val p = Scheduler.projectedSchedule(drawn, Random(2))
                assertTrue("$algo with $n teams", p.realMatches().isEmpty())
                assertTrue(Scheduler.fits(p))
                // Single elimination used to index past the end of a size-1 bracket here.
                if (algo == Algorithm.SINGLE_ELIM) assertTrue(drawn.matches.isEmpty())
            }
        }
    }

    // ---------- hub: whole-tournament look-ahead ----------

    @Test fun projectedOverrunCatchesAStageNotDrawnYet() {
        // 4 teams, 1 court, end @60: both semis (@0, @30) fit, so the current-matches check sees nothing,
        // but the final (not drawn yet) would run @60-@85.
        val t = singleElim(4, endAt = start + 60 * min)
        assertTrue(Scheduler.fits(t))
        assertEquals(0L, Scheduler.scheduleOverrunMinutes(t))
        assertEquals(25L, Scheduler.projectedOverrunMinutes(t))
    }

    @Test fun extendingByTheProjectedOverrunKeepsTheNextRoundWithinThePlan() {
        // End @50: semis @0-@25 and @30-@55 run 5 min over; the final would be @60-@85, 35 min over.
        val t = singleElim(4, endAt = start + 50 * min)
        assertEquals(5L, Scheduler.scheduleOverrunMinutes(t))
        assertEquals(35L, Scheduler.projectedOverrunMinutes(t))
        // Extending by the current-matches overrun only (the old hub fix): the final is planned past the end as soon as it's drawn.
        val currentOnly = Scheduler.scheduleTimes(t.copy(endAt = t.endAt + 5 * min))
        assertTrue(Scheduler.fits(currentOnly))
        val drawnLate = Scheduler.advance(finishAll(currentOnly), Random(2))
        assertTrue(drawnLate.realMatches().single { it.isFinal }.exceedsWindow(drawnLate))
        // Extending by the projected overrun (the hub's "Extend end" now): the final fits when it's drawn.
        val lookAhead = Scheduler.scheduleTimes(t.copy(endAt = t.endAt + 35 * min))
        assertEquals(0L, Scheduler.projectedOverrunMinutes(lookAhead))
        val drawn = Scheduler.advance(finishAll(lookAhead), Random(2))
        assertFalse(drawn.realMatches().single { it.isFinal }.exceedsWindow(drawn))
        assertTrue(Scheduler.fits(drawn))
    }

    @Test fun formatChangeOnAnActiveTournamentIsCheckedAgainstTheWholeTournament() {
        // Fits exactly with the current format (final ends @85); 11-min halves make each slot 32 min:
        // semis @0 and @32, final @64-@91.
        val t = singleElim(4, endAt = start + 85 * min)
        assertEquals(0L, Scheduler.projectedOverrunMinutes(t))
        val longer = t.copy(format = format.copy(periodMin = 11))
        assertTrue(Scheduler.fits(Scheduler.scheduleTimes(longer))) // the semis alone still fit
        assertEquals(6L, Scheduler.projectedOverrunMinutes(longer))
    }

    @Test fun advanceStillDrawsAnOverrunningStageAndItCanStillBeStarted() {
        var t = singleElim(4, endAt = start + 60 * min)
        t = Scheduler.advance(finishAll(t), Random(2))
        // The final is drawn (the bracket must exist) and slotted @60, past the @60 end: shown as past the plan,
        // but the plan is only an estimate, so it can still be started.
        val fin = t.realMatches().single { it.isFinal }
        assertEquals(MatchStatus.SCHEDULED, fin.status)
        assertTrue(fin.exceedsWindow(t))
        assertTrue(fin.canStart())
        assertFalse(Scheduler.fits(t))
        // Extending the end time (and re-slotting, as the optional fixes do) brings the plan back inside the window.
        val extended = Scheduler.scheduleTimes(t.copy(endAt = start + 85 * min))
        assertTrue(Scheduler.fits(extended))
        assertFalse(extended.realMatches().single { it.isFinal }.exceedsWindow(extended))
    }

    // ---------- play time: the plan never blocks starting a match ----------

    @Test fun startIsNeverBlockedByTheTentativeSchedule() {
        // m1 planned @90-@115 against a @60 end: well past the plan, still startable.
        val t = tournament(endAt = start + 60 * min)
        val late = match(1, scheduledAt = start + 90 * min)
        assertTrue(late.exceedsWindow(t))
        assertTrue(late.canStart())
        assertTrue(match(2).canStart()) // never slotted (no start time) is fine too
    }

    @Test fun startNeverRestartsALiveOrFinishedMatch() {
        // The only thing that stops "Start match": it has already started, so starting again would wipe its score.
        for (s in MatchStatus.values().filter { it != MatchStatus.SCHEDULED }) {
            assertFalse(s.name, match(1).copy(status = s).canStart())
        }
        assertFalse(match(2, bye = true).canStart())
        assertFalse(match(3).copy(teamBId = null).canStart())
    }
}
